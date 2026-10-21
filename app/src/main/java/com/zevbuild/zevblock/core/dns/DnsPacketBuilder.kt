package com.zevbuild.zevblock.core.dns

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Builds synthesized DNS response messages (A, AAAA, CNAME, NXDOMAIN)
 * and wraps them back into raw IPv4/IPv6 UDP network packets.
 */
object DnsPacketBuilder {

    private const val DEFAULT_TTL = 300 // 5 minutes

    /**
     * Builds a synthesized DNS A record response.
     */
    fun buildAResponse(query: ParsedDnsQuery, ipv4Address: String, ttl: Int = DEFAULT_TTL): ByteArray {
        val ipBytes = InetAddress.getByName(ipv4Address).address
        return buildDnsAnswer(
            query = query,
            recordType = DnsRecordType.A,
            ttl = ttl,
            rData = ipBytes
        )
    }

    /**
     * Builds a synthesized DNS AAAA record response.
     */
    fun buildAaaaResponse(query: ParsedDnsQuery, ipv6Address: String, ttl: Int = DEFAULT_TTL): ByteArray {
        val ipBytes = InetAddress.getByName(ipv6Address).address
        return buildDnsAnswer(
            query = query,
            recordType = DnsRecordType.AAAA,
            ttl = ttl,
            rData = ipBytes
        )
    }

    /**
     * Builds a synthesized DNS CNAME record response.
     */
    fun buildCnameResponse(query: ParsedDnsQuery, cnameTarget: String, ttl: Int = DEFAULT_TTL): ByteArray {
        val targetEncoded = encodeDomainName(cnameTarget)
        return buildDnsAnswer(
            query = query,
            recordType = DnsRecordType.CNAME,
            ttl = ttl,
            rData = targetEncoded
        )
    }

    /**
     * Builds a sinkhole response: either 0.0.0.0 (or ::) or NXDOMAIN.
     */
    fun buildSinkholeResponse(
        query: ParsedDnsQuery,
        blockedIp: String = "0.0.0.0",
        isNxDomain: Boolean = false
    ): ByteArray {
        if (isNxDomain) {
            return buildNxDomainResponse(query)
        }

        return if (query.question.qType == DnsRecordType.AAAA) {
            buildAaaaResponse(query, "::", ttl = 60)
        } else {
            buildAResponse(query, blockedIp, ttl = 60)
        }
    }

    /**
     * Builds an NXDOMAIN (RCODE = 3) response.
     */
    fun buildNxDomainResponse(query: ParsedDnsQuery): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. Transaction ID
        out.write((query.id shr 8) and 0xFF)
        out.write(query.id and 0xFF)

        // 2. Flags: QR=1 (Response), AA=1, RA=1, RD copied, RCODE=3 (NXDOMAIN)
        // Binary: 1 0000 1 0 0 | 1 000 0011 -> 0x85 0x83
        val flagRd = if (query.isRecursionDesired) 0x01 else 0x00
        val flagsHigh = 0x81 or (flagRd and 0x01)
        val flagsLow = 0x83 // RA=1, RCODE=3 (NXDOMAIN)
        out.write(flagsHigh)
        out.write(flagsLow)

        // 3. QDCOUNT = 1, ANCOUNT = 0, NSCOUNT = 0, ARCOUNT = 0
        out.write(0x00); out.write(0x01) // QDCOUNT = 1
        out.write(0x00); out.write(0x00) // ANCOUNT = 0
        out.write(0x00); out.write(0x00) // NSCOUNT = 0
        out.write(0x00); out.write(0x00) // ARCOUNT = 0

        // 4. Question Section (copy directly from raw query payload after 12-byte header up to questionEndOffset)
        val questionLen = (query.questionEndOffset - 12).coerceAtLeast(0)
        if (questionLen > 0 && query.rawQueryBytes.size >= query.questionEndOffset) {
            out.write(query.rawQueryBytes, 12, questionLen)
        }

        return out.toByteArray()
    }

    /**
     * Constructs a full DNS answer packet containing 1 Question and 1 Answer Resource Record.
     */
    private fun buildDnsAnswer(
        query: ParsedDnsQuery,
        recordType: Int,
        ttl: Int,
        rData: ByteArray
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. Transaction ID
        out.write((query.id shr 8) and 0xFF)
        out.write(query.id and 0xFF)

        // 2. Flags: QR=1, AA=1, RA=1, RD copied, RCODE=0 (No Error)
        val flagRd = if (query.isRecursionDesired) 0x01 else 0x00
        val flagsHigh = 0x85 or flagRd
        val flagsLow = 0x80 // RA=1, RCODE=0
        out.write(flagsHigh)
        out.write(flagsLow)

        // 3. QDCOUNT = 1, ANCOUNT = 1, NSCOUNT = 0, ARCOUNT = 0
        out.write(0x00); out.write(0x01) // QDCOUNT = 1
        out.write(0x00); out.write(0x01) // ANCOUNT = 1
        out.write(0x00); out.write(0x00) // NSCOUNT = 0
        out.write(0x00); out.write(0x00) // ARCOUNT = 0

        // 4. Question Section (copy from original query)
        val questionBytesLength = (query.questionEndOffset - 12).coerceAtLeast(0)
        if (questionBytesLength > 0 && query.rawQueryBytes.size >= query.questionEndOffset) {
            out.write(query.rawQueryBytes, 12, questionBytesLength)
        }

        // 5. Answer Section
        // Name: Pointer 0xC00C pointing to the question name offset in header
        out.write(0xC0)
        out.write(0x0C)

        // Type (2 bytes)
        out.write((recordType shr 8) and 0xFF)
        out.write(recordType and 0xFF)

        // Class: IN (0x0001)
        out.write(0x00)
        out.write(0x01)

        // TTL (4 bytes)
        out.write((ttl shr 24) and 0xFF)
        out.write((ttl shr 16) and 0xFF)
        out.write((ttl shr 8) and 0xFF)
        out.write(ttl and 0xFF)

        // RDLENGTH (2 bytes)
        out.write((rData.size shr 8) and 0xFF)
        out.write(rData.size and 0xFF)

        // RDATA
        out.write(rData)

        return out.toByteArray()
    }

    /**
     * Encodes a domain name string into RFC 1035 label format.
     */
    private fun encodeDomainName(domain: String): ByteArray {
        val out = ByteArrayOutputStream()
        val parts = domain.trim().trimEnd('.').split('.')
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0) // Root null terminator
        return out.toByteArray()
    }

    /**
     * Wraps a DNS response payload into an IP/UDP packet, reversing source and
     * destination IPs and ports, and computing accurate checksums.
     */
    fun wrapDnsResponseInUdpIp(incomingPacket: IpUdpPacket, dnsResponsePayload: ByteArray): ByteArray {
        return if (incomingPacket.isIpv6) {
            wrapIpv6Udp(incomingPacket, dnsResponsePayload)
        } else {
            wrapIpv4Udp(incomingPacket, dnsResponsePayload)
        }
    }

    private fun wrapIpv4Udp(incomingPacket: IpUdpPacket, payload: ByteArray): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val udpTotalLen = udpHeaderLen + payload.size
        val ipTotalLen = ipHeaderLen + udpTotalLen

        val packet = ByteArray(ipTotalLen)
        val bb = ByteBuffer.wrap(packet)

        // IPv4 Header
        bb.put(0x45.toByte()) // Version 4, IHL 5 (20 bytes)
        bb.put(0x00.toByte()) // ToS
        bb.putShort(ipTotalLen.toShort())
        bb.putShort(0x0000.toShort()) // Identification
        bb.putShort(0x4000.toShort()) // Flags: Don't Fragment
        bb.put(64.toByte()) // TTL
        bb.put(17.toByte()) // Protocol: UDP
        bb.putShort(0.toShort()) // Checksum placeholder

        // Swap IPs: response src = query dst, response dst = query src
        bb.put(incomingPacket.dstIp)
        bb.put(incomingPacket.srcIp)

        // Calculate and insert IPv4 header checksum
        val ipChecksum = calculateInternetChecksum(packet, 0, ipHeaderLen)
        bb.putShort(10, ipChecksum.toShort())

        // UDP Header
        bb.putShort(incomingPacket.dstPort.toShort()) // Source Port (53)
        bb.putShort(incomingPacket.srcPort.toShort()) // Destination Port
        bb.putShort(udpTotalLen.toShort())
        bb.putShort(0.toShort()) // UDP Checksum placeholder

        // Copy Payload
        bb.put(payload)

        // Calculate UDP Checksum (with IPv4 pseudo-header)
        val udpChecksum = calculateUdpIpv4Checksum(
            srcIp = incomingPacket.dstIp,
            dstIp = incomingPacket.srcIp,
            udpPacket = packet,
            udpOffset = ipHeaderLen,
            udpLength = udpTotalLen
        )
        bb.putShort(ipHeaderLen + 6, udpChecksum.toShort())

        return packet
    }

    private fun wrapIpv6Udp(incomingPacket: IpUdpPacket, payload: ByteArray): ByteArray {
        val ipHeaderLen = 40
        val udpHeaderLen = 8
        val udpTotalLen = udpHeaderLen + payload.size
        val ipTotalLen = ipHeaderLen + udpTotalLen

        val packet = ByteArray(ipTotalLen)
        val bb = ByteBuffer.wrap(packet)

        // IPv6 Header (40 bytes)
        bb.putInt(0x60000000) // Version 6, Traffic Class 0, Flow Label 0
        bb.putShort(udpTotalLen.toShort()) // Payload length (UDP length)
        bb.put(17.toByte()) // Next Header: UDP
        bb.put(64.toByte()) // Hop Limit

        // Swap IPv6 Addresses
        bb.put(incomingPacket.dstIp)
        bb.put(incomingPacket.srcIp)

        // UDP Header
        bb.putShort(incomingPacket.dstPort.toShort())
        bb.putShort(incomingPacket.srcPort.toShort())
        bb.putShort(udpTotalLen.toShort())
        bb.putShort(0.toShort()) // Checksum placeholder

        // Payload
        bb.put(payload)

        // Mandatory IPv6 UDP Checksum
        val udpChecksum = calculateUdpIpv6Checksum(
            srcIp = incomingPacket.dstIp,
            dstIp = incomingPacket.srcIp,
            udpPacket = packet,
            udpOffset = ipHeaderLen,
            udpLength = udpTotalLen
        )
        bb.putShort(ipHeaderLen + 6, udpChecksum.toShort())

        return packet
    }

    private fun calculateInternetChecksum(buffer: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            val word = ((buffer[i].toInt() and 0xFF) shl 8) or (buffer[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < offset + length) {
            sum += (buffer[i].toInt() and 0xFF) shl 8
        }
        while (sum > 0xFFFF) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv().toInt() and 0xFFFF
    }

    private fun calculateUdpIpv4Checksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpPacket: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Int {
        var sum = 0L

        // Pseudo Header: Src IP (4) + Dst IP (4) + Zero (1) + Protocol (1) + UDP Length (2)
        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)
        sum += 17 // Protocol UDP
        sum += udpLength

        // UDP Packet data
        var i = udpOffset
        while (i < udpOffset + udpLength - 1) {
            val word = ((udpPacket[i].toInt() and 0xFF) shl 8) or (udpPacket[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < udpOffset + udpLength) {
            sum += (udpPacket[i].toInt() and 0xFF) shl 8
        }

        while (sum > 0xFFFF) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val result = sum.inv().toInt() and 0xFFFF
        return if (result == 0) 0xFFFF else result
    }

    private fun calculateUdpIpv6Checksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpPacket: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Int {
        var sum = 0L

        // Pseudo-header IPv6: Src IP (16 bytes) + Dst IP (16 bytes) + UDP Length (4 bytes) + Next Header (4 bytes)
        for (i in 0 until 16 step 2) {
            sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
        }
        for (i in 0 until 16 step 2) {
            sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
        }
        sum += udpLength shr 16
        sum += udpLength and 0xFFFF
        sum += 17 // Next Header

        // UDP header + payload
        var i = udpOffset
        while (i < udpOffset + udpLength - 1) {
            val word = ((udpPacket[i].toInt() and 0xFF) shl 8) or (udpPacket[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < udpOffset + udpLength) {
            sum += (udpPacket[i].toInt() and 0xFF) shl 8
        }

        while (sum > 0xFFFF) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val result = sum.inv().toInt() and 0xFFFF
        return if (result == 0) 0xFFFF else result
    }
}
