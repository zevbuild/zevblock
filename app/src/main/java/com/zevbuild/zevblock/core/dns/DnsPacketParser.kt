package com.zevbuild.zevblock.core.dns

import android.util.Log
import java.nio.ByteBuffer

/**
 * High-performance binary parser for raw IPv4/IPv6 packets, UDP headers,
 * and RFC 1035 DNS messages.
 */
object DnsPacketParser {

    private const val TAG = "DnsPacketParser"
    private const val PROTOCOL_UDP = 17

    /**
     * Parses a raw network buffer read from the TUN interface.
     * Supports both IPv4 (version 4) and IPv6 (version 6).
     */
    fun parseIpPacket(buffer: ByteArray, length: Int): IpUdpPacket? {
        if (length < 28) return null // Minimum IPv4 (20) + UDP (8)

        val bufferWrap = ByteBuffer.wrap(buffer, 0, length)
        val firstByte = bufferWrap.get(0).toInt() and 0xFF
        val version = firstByte shr 4

        return when (version) {
            4 -> parseIPv4(buffer, length, bufferWrap)
            6 -> parseIPv6(buffer, length, bufferWrap)
            else -> null
        }
    }

    private fun parseIPv4(buffer: ByteArray, length: Int, bb: ByteBuffer): IpUdpPacket? {
        val firstByte = bb.get(0).toInt() and 0xFF
        val ihl = (firstByte and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null

        val protocol = bb.get(9).toInt() and 0xFF
        if (protocol != PROTOCOL_UDP) return null

        val srcIp = ByteArray(4)
        val dstIp = ByteArray(4)
        System.arraycopy(buffer, 12, srcIp, 0, 4)
        System.arraycopy(buffer, 16, dstIp, 0, 4)

        // UDP Header starts after IPv4 options (at offset 'ihl')
        val srcPort = bb.getShort(ihl).toInt() and 0xFFFF
        val dstPort = bb.getShort(ihl + 2).toInt() and 0xFFFF
        val udpLength = bb.getShort(ihl + 4).toInt() and 0xFFFF

        if (udpLength < 8 || length < ihl + udpLength) return null

        val payloadLength = udpLength - 8
        val payload = ByteArray(payloadLength)
        System.arraycopy(buffer, ihl + 8, payload, 0, payloadLength)

        return IpUdpPacket(
            isIpv6 = false,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            payload = payload
        )
    }

    private fun parseIPv6(buffer: ByteArray, length: Int, bb: ByteBuffer): IpUdpPacket? {
        if (length < 48) return null // IPv6 (40) + UDP (8)

        val nextHeader = bb.get(6).toInt() and 0xFF
        if (nextHeader != PROTOCOL_UDP) return null

        val srcIp = ByteArray(16)
        val dstIp = ByteArray(16)
        System.arraycopy(buffer, 8, srcIp, 0, 16)
        System.arraycopy(buffer, 24, dstIp, 0, 16)

        val srcPort = bb.getShort(40).toInt() and 0xFFFF
        val dstPort = bb.getShort(42).toInt() and 0xFFFF
        val udpLength = bb.getShort(44).toInt() and 0xFFFF

        if (udpLength < 8 || length < 40 + udpLength) return null

        val payloadLength = udpLength - 8
        val payload = ByteArray(payloadLength)
        System.arraycopy(buffer, 48, payload, 0, payloadLength)

        return IpUdpPacket(
            isIpv6 = true,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            payload = payload
        )
    }

    /**
     * Parses an RFC 1035 DNS query payload.
     */
    fun parseDnsQuery(dnsPayload: ByteArray): ParsedDnsQuery? {
        if (dnsPayload.size < 12) return null // DNS header is 12 bytes

        val bb = ByteBuffer.wrap(dnsPayload)
        val id = bb.short.toInt() and 0xFFFF
        val flags = bb.short.toInt() and 0xFFFF

        val isResponse = (flags and 0x8000) != 0
        if (isResponse) return null // Only parse incoming client queries

        val isRecursionDesired = (flags and 0x0100) != 0
        val qdCount = bb.short.toInt() and 0xFFFF
        if (qdCount < 1) return null // Must contain at least one question

        // Parse Question Name (labels)
        var offset = 12
        val domainLabels = StringBuilder()

        while (offset < dnsPayload.size) {
            val labelLen = dnsPayload[offset].toInt() and 0xFF
            offset++

            if (labelLen == 0) break // Root null terminator

            // Handle compression pointer (RFC 1035 4.1.4)
            if ((labelLen and 0xC0) == 0xC0) {
                if (offset >= dnsPayload.size) return null
                val pointerOffset = ((labelLen and 0x3F) shl 8) or (dnsPayload[offset].toInt() and 0xFF)
                offset++
                val pointedDomain = readDomainFromOffset(dnsPayload, pointerOffset)
                if (pointedDomain != null) {
                    if (domainLabels.isNotEmpty()) domainLabels.append(".")
                    domainLabels.append(pointedDomain)
                }
                break
            }

            if (offset + labelLen > dnsPayload.size) return null

            if (domainLabels.isNotEmpty()) {
                domainLabels.append(".")
            }
            domainLabels.append(String(dnsPayload, offset, labelLen, Charsets.US_ASCII))
            offset += labelLen
        }

        if (offset + 4 > dnsPayload.size) return null

        val qType = ((dnsPayload[offset].toInt() and 0xFF) shl 8) or (dnsPayload[offset + 1].toInt() and 0xFF)
        val qClass = ((dnsPayload[offset + 2].toInt() and 0xFF) shl 8) or (dnsPayload[offset + 3].toInt() and 0xFF)

        val cleanDomain = domainLabels.toString().lowercase().trimEnd('.')

        return ParsedDnsQuery(
            id = id,
            isRecursionDesired = isRecursionDesired,
            question = DnsQuestion(
                name = cleanDomain,
                qType = qType,
                qClass = qClass
            ),
            rawQueryBytes = dnsPayload
        )
    }


    /**
     * Validates an upstream DNS response against the query we sent.
     * Invalid responses must never be forwarded to the VPN client.
     */
    fun isValidDnsResponse(queryPayload: ByteArray, responsePayload: ByteArray): Boolean {
        if (!isWellFormedDnsMessage(queryPayload, expectResponse = false)) return false
        if (!isWellFormedDnsMessage(responsePayload, expectResponse = true)) return false

        if (readUInt16(queryPayload, 0) != readUInt16(responsePayload, 0)) return false

        val queryFlags = readUInt16(queryPayload, 2)
        val responseFlags = readUInt16(responsePayload, 2)
        // A truncated response cannot be safely forwarded as a complete DNS answer.
        if ((responseFlags and 0x0200) != 0) return false
        // The response opcode must match the query opcode.
        if (((queryFlags ushr 11) and 0x0F) != ((responseFlags ushr 11) and 0x0F)) return false

        val queryQuestion = readQuestionAt(queryPayload, 12) ?: return false
        val responseQuestion = readQuestionAt(responsePayload, 12) ?: return false
        return queryQuestion.question.name.equals(responseQuestion.question.name, ignoreCase = true) &&
            queryQuestion.question.qType == responseQuestion.question.qType &&
            queryQuestion.question.qClass == responseQuestion.question.qClass
    }

    private data class DecodedDnsName(val name: String, val nextOffset: Int)
    private data class QuestionAtOffset(val question: DnsQuestion, val nextOffset: Int)

    private fun isWellFormedDnsMessage(payload: ByteArray, expectResponse: Boolean): Boolean {
        if (payload.size < 12) return false
        val flags = readUInt16(payload, 2)
        val isResponse = (flags and 0x8000) != 0
        if (isResponse != expectResponse) return false
        if (readUInt16(payload, 4) != 1) return false

        val question = readQuestionAt(payload, 12) ?: return false
        var offset = question.nextOffset

        offset = skipResourceRecords(payload, offset, readUInt16(payload, 6)) ?: return false
        offset = skipResourceRecords(payload, offset, readUInt16(payload, 8)) ?: return false
        offset = skipResourceRecords(payload, offset, readUInt16(payload, 10)) ?: return false
        // Reject trailing bytes instead of allowing malformed packet tails through.
        return offset == payload.size
    }

    private fun readQuestionAt(payload: ByteArray, offset: Int): QuestionAtOffset? {
        val decodedName = decodeDnsName(payload, offset) ?: return null
        if (decodedName.nextOffset + 4 > payload.size) return null
        val qType = readUInt16(payload, decodedName.nextOffset)
        val qClass = readUInt16(payload, decodedName.nextOffset + 2)
        return QuestionAtOffset(
            question = DnsQuestion(decodedName.name, qType, qClass),
            nextOffset = decodedName.nextOffset + 4
        )
    }

    private fun skipResourceRecords(payload: ByteArray, startOffset: Int, count: Int): Int? {
        var offset = startOffset
        repeat(count) {
            val ownerName = decodeDnsName(payload, offset) ?: return null
            val headerOffset = ownerName.nextOffset
            if (headerOffset + 10 > payload.size) return null
            val dataLength = readUInt16(payload, headerOffset + 8)
            val recordEnd = headerOffset + 10 + dataLength
            if (recordEnd < headerOffset || recordEnd > payload.size) return null
            offset = recordEnd
        }
        return offset
    }

    private fun decodeDnsName(payload: ByteArray, startOffset: Int): DecodedDnsName? {
        if (startOffset !in payload.indices) return null
        var offset = startOffset
        var nextOffset = -1
        var jumped = false
        var hops = 0
        var expandedLength = 0
        val labels = ArrayList<String>()
        val visited = HashSet<Int>()

        while (true) {
            if (offset !in payload.indices || !visited.add(offset)) return null
            val labelLength = payload[offset].toInt() and 0xFF
            when {
                labelLength == 0 -> {
                    offset++
                    if (!jumped) nextOffset = offset
                    if (nextOffset < 0) return null
                    break
                }
                (labelLength and 0xC0) == 0xC0 -> {
                    if (offset + 1 >= payload.size) return null
                    val pointer = ((labelLength and 0x3F) shl 8) or
                        (payload[offset + 1].toInt() and 0xFF)
                    // RFC 1035 compression pointers must refer to an earlier occurrence.
                    if (pointer < 12 || pointer >= offset || pointer >= payload.size) return null
                    if (!jumped) {
                        nextOffset = offset + 2
                        jumped = true
                    }
                    offset = pointer
                    hops++
                    if (hops > 16) return null
                }
                (labelLength and 0xC0) != 0 -> return null
                else -> {
                    if (labelLength > 63 || offset + 1 + labelLength > payload.size) return null
                    expandedLength += labelLength + 1
                    if (expandedLength > 255) return null
                    labels.add(String(payload, offset + 1, labelLength, Charsets.US_ASCII))
                    offset += 1 + labelLength
                }
            }
        }
        return DecodedDnsName(labels.joinToString(".").lowercase(), nextOffset)
    }

    private fun readUInt16(payload: ByteArray, offset: Int): Int {
        return ((payload[offset].toInt() and 0xFF) shl 8) or
            (payload[offset + 1].toInt() and 0xFF)
    }

    private fun readDomainFromOffset(buffer: ByteArray, startOffset: Int, maxDepth: Int = 5): String? {
        if (maxDepth <= 0 || startOffset >= buffer.size) return null
        var offset = startOffset
        val sb = StringBuilder()

        while (offset < buffer.size) {
            val len = buffer[offset].toInt() and 0xFF
            offset++
            if (len == 0) break

            if ((len and 0xC0) == 0xC0) {
                if (offset >= buffer.size) break
                val ptr = ((len and 0x3F) shl 8) or (buffer[offset].toInt() and 0xFF)
                val target = readDomainFromOffset(buffer, ptr, maxDepth - 1) ?: break
                if (sb.isNotEmpty()) sb.append(".")
                sb.append(target)
                break
            }

            if (offset + len > buffer.size) break
            if (sb.isNotEmpty()) sb.append(".")
            sb.append(String(buffer, offset, len, Charsets.US_ASCII))
            offset += len
        }

        return sb.toString()
    }
}
