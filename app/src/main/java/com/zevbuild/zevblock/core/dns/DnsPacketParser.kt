package com.zevbuild.zevblock.core.dns

import android.util.Log
import java.nio.ByteBuffer

/**
 * High-performance binary parser for raw IPv4/IPv6 packets, UDP headers,
 * and RFC 1035 DNS messages.
 * Includes RFC 8200 IPv6 extension header bounds validation and DNS response verification.
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

        val totalLength = bb.getShort(2).toInt() and 0xFFFF
        if (totalLength < ihl + 8 || totalLength > length) return null

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

        if (udpLength < 8 || length < ihl + udpLength || totalLength < ihl + udpLength) return null

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
        if (length < 48) return null // IPv6 base header (40) + UDP (8)

        val payloadLengthField = bb.getShort(4).toInt() and 0xFFFF
        if (40 + payloadLengthField > length) return null

        var nextHeader = bb.get(6).toInt() and 0xFF
        var offset = 40

        // Parse through known IPv6 extension headers (RFC 8200) to find upper-layer protocol
        var extensionDepth = 0
        while (offset + 8 <= length && extensionDepth < 8) {
            extensionDepth++
            when (nextHeader) {
                PROTOCOL_UDP -> {
                    // Reached UDP header
                    if (length < offset + 8) return null
                    val srcPort = bb.getShort(offset).toInt() and 0xFFFF
                    val dstPort = bb.getShort(offset + 2).toInt() and 0xFFFF
                    val udpLength = bb.getShort(offset + 4).toInt() and 0xFFFF

                    if (udpLength < 8 || length < offset + udpLength) return null

                    val payloadLength = udpLength - 8
                    val payload = ByteArray(payloadLength)
                    System.arraycopy(buffer, offset + 8, payload, 0, payloadLength)

                    val srcIp = ByteArray(16)
                    val dstIp = ByteArray(16)
                    System.arraycopy(buffer, 8, srcIp, 0, 16)
                    System.arraycopy(buffer, 24, dstIp, 0, 16)

                    return IpUdpPacket(
                        isIpv6 = true,
                        srcIp = srcIp,
                        dstIp = dstIp,
                        srcPort = srcPort,
                        dstPort = dstPort,
                        payload = payload
                    )
                }
                0, 43, 60 -> {
                    // Hop-by-Hop (0), Routing (43), Destination Options (60)
                    if (offset + 2 > length) return null
                    nextHeader = bb.get(offset).toInt() and 0xFF
                    val extLen = (bb.get(offset + 1).toInt() and 0xFF) * 8 + 8
                    if (extLen <= 0 || offset + extLen > length) return null
                    offset += extLen
                }
                44 -> {
                    // Fragment Header: Fixed 8 octets
                    if (offset + 8 > length) return null
                    nextHeader = bb.get(offset).toInt() and 0xFF
                    val fragOffsetAndFlags = bb.getShort(offset + 2).toInt() and 0xFFFF
                    val fragOffset = fragOffsetAndFlags shr 3
                    if (fragOffset != 0) {
                        // Non-initial fragment; cannot parse UDP header directly
                        return null
                    }
                    offset += 8
                }
                51 -> {
                    // Authentication Header (AH): length in 4-octet units minus 2
                    if (offset + 2 > length) return null
                    nextHeader = bb.get(offset).toInt() and 0xFF
                    val payloadLen = bb.get(offset + 1).toInt() and 0xFF
                    val extLen = (payloadLen + 2) * 4
                    if (extLen <= 0 || offset + extLen > length) return null
                    offset += extLen
                }
                else -> {
                    // Unsupported extension header or non-UDP protocol
                    return null
                }
            }
        }
        return null
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
        val questionEnd = offset + 4

        return ParsedDnsQuery(
            id = id,
            isRecursionDesired = isRecursionDesired,
            question = DnsQuestion(
                name = cleanDomain,
                qType = qType,
                qClass = qClass
            ),
            rawQueryBytes = dnsPayload,
            questionEndOffset = questionEnd
        )
    }

    /**
     * Validates that an upstream DNS response strictly matches the original query.
     * Prevents DNS spoofing, cache poisoning, and malformed packet injection.
     */
    fun validateDnsResponse(responseBytes: ByteArray, expectedQuery: ParsedDnsQuery): Boolean {
        if (responseBytes.size < 12) return false

        // 1. Transaction ID match
        val id = ((responseBytes[0].toInt() and 0xFF) shl 8) or (responseBytes[1].toInt() and 0xFF)
        if (id != expectedQuery.id) {
            Log.w(TAG, "DNS validation rejected: Transaction ID mismatch (got $id, expected ${expectedQuery.id})")
            return false
        }

        // 2. Flags: QR bit must be 1 (Response), Opcode must be 0 (Standard Query)
        val flags = ((responseBytes[2].toInt() and 0xFF) shl 8) or (responseBytes[3].toInt() and 0xFF)
        val isResponse = (flags and 0x8000) != 0
        if (!isResponse) {
            Log.w(TAG, "DNS validation rejected: packet is not a DNS response (QR=0)")
            return false
        }

        val opcode = (flags shr 11) and 0x0F
        if (opcode != 0) {
            Log.w(TAG, "DNS validation rejected: unexpected opcode $opcode")
            return false
        }

        // 3. QDCOUNT must be at least 1
        val qdCount = ((responseBytes[4].toInt() and 0xFF) shl 8) or (responseBytes[5].toInt() and 0xFF)
        if (qdCount < 1) {
            Log.w(TAG, "DNS validation rejected: QDCOUNT < 1 ($qdCount)")
            return false
        }

        // 4. Verify question in response matches original query name and type
        var offset = 12
        val respDomain = StringBuilder()

        while (offset < responseBytes.size) {
            val labelLen = responseBytes[offset].toInt() and 0xFF
            offset++
            if (labelLen == 0) break

            if ((labelLen and 0xC0) == 0xC0) {
                if (offset >= responseBytes.size) return false
                val ptr = ((labelLen and 0x3F) shl 8) or (responseBytes[offset].toInt() and 0xFF)
                offset++
                val pointed = readDomainFromOffset(responseBytes, ptr) ?: return false
                if (respDomain.isNotEmpty()) respDomain.append(".")
                respDomain.append(pointed)
                break
            }

            if (offset + labelLen > responseBytes.size) return false
            if (respDomain.isNotEmpty()) respDomain.append(".")
            respDomain.append(String(responseBytes, offset, labelLen, Charsets.US_ASCII))
            offset += labelLen
        }

        if (offset + 4 > responseBytes.size) return false
        val respQType = ((responseBytes[offset].toInt() and 0xFF) shl 8) or (responseBytes[offset + 1].toInt() and 0xFF)
        val respQClass = ((responseBytes[offset + 2].toInt() and 0xFF) shl 8) or (responseBytes[offset + 3].toInt() and 0xFF)

        val cleanRespDomain = respDomain.toString().lowercase().trimEnd('.')
        if (cleanRespDomain != expectedQuery.question.name.lowercase().trimEnd('.')) {
            Log.w(TAG, "DNS validation rejected: Question name mismatch (got '$cleanRespDomain', expected '${expectedQuery.question.name}')")
            return false
        }

        if (respQType != expectedQuery.question.qType) {
            Log.w(TAG, "DNS validation rejected: Question type mismatch (got $respQType, expected ${expectedQuery.question.qType})")
            return false
        }

        if (respQClass != expectedQuery.question.qClass) {
            Log.w(TAG, "DNS validation rejected: Question class mismatch (got $respQClass, expected ${expectedQuery.question.qClass})")
            return false
        }

        return true
    }

    private fun readDomainFromOffset(buffer: ByteArray, startOffset: Int, maxDepth: Int = 5): String? {
        if (maxDepth <= 0 || startOffset < 0 || startOffset >= buffer.size) return null
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
