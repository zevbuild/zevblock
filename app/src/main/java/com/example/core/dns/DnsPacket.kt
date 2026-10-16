package com.example.core.dns

/**
 * Data structures and constants representing parsed IP/UDP and DNS packets.
 */
object DnsRecordType {
    const val A = 1         // IPv4 Address
    const val NS = 2        // Authoritative Name Server
    const val CNAME = 5     // Canonical Name
    const val SOA = 6       // Start of Authority
    const val PTR = 12      // Pointer Record
    const val MX = 15       // Mail Exchange
    const val TXT = 16      // Text Record
    const val AAAA = 28     // IPv6 Address
    const val SRV = 33      // Service Locator
    const val ANY = 255     // Any Record
}

object DnsRCode {
    const val NO_ERROR = 0      // DNS Query completed successfully
    const val FORM_ERR = 1      // DNS Query Format Error
    const val SERV_FAIL = 2     // Server Failure
    const val NX_DOMAIN = 3     // Non-Existent Domain Name
    const val NOT_IMP = 4       // Not Implemented
    const val REFUSED = 5       // Query Refused
}

/**
 * Represents a single DNS Question section (RFC 1035).
 */
data class DnsQuestion(
    val name: String,
    val qType: Int,
    val qClass: Int
)

/**
 * Represents a fully parsed DNS query message.
 */
data class ParsedDnsQuery(
    val id: Int,
    val isRecursionDesired: Boolean,
    val question: DnsQuestion,
    val rawQueryBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ParsedDnsQuery
        return id == other.id && question == other.question
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + question.hashCode()
        return result
    }
}

/**
 * Encapsulates an IPv4 or IPv6 packet with UDP transport details.
 */
data class IpUdpPacket(
    val isIpv6: Boolean,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as IpUdpPacket
        return isIpv6 == other.isIpv6 &&
                srcIp.contentEquals(other.srcIp) &&
                dstIp.contentEquals(other.dstIp) &&
                srcPort == other.srcPort &&
                dstPort == other.dstPort &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = isIpv6.hashCode()
        result = 31 * result + srcIp.contentHashCode()
        result = 31 * result + dstIp.contentHashCode()
        result = 31 * result + srcPort
        result = 31 * result + dstPort
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Log record for DNS traffic monitoring in the UI.
 */
data class DnsLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val domain: String,
    val recordType: String,
    val isBlocked: Boolean,
    val isSafeSearchRewritten: Boolean,
    val reason: String
)
