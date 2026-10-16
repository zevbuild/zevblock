package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.admin.DeviceAdminManager
import com.example.core.dns.DnsPacketBuilder
import com.example.core.dns.DnsPacketParser
import com.example.core.dns.DnsRecordType
import com.example.core.dns.DomainBlocklistMatcher
import com.example.core.dns.IpUdpPacket
import com.example.core.dns.SafeSearchManager
import com.example.core.security.TamperLockManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ZevBlock", appName)
    }

    @Test
    fun `test base32 encoding and decoding`() {
        val original = "Hello ShieldGuard OS!".toByteArray(Charsets.UTF_8)
        val encoded = TamperLockManager.Base32.encode(original)
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())

        val decoded = TamperLockManager.Base32.decode(encoded)
        assertEquals(String(original), String(decoded))
    }

    @Test
    fun `test totp generation and verification`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockManager = TamperLockManager(context)

        val secret = lockManager.setupNewPartnerSecret()
        assertNotNull(secret)
        assertEquals(32, secret.length) // 20 bytes -> 32 Base32 characters

        val uri = lockManager.getPartnerTotpUri()
        assertNotNull(uri)
        assertTrue(uri!!.startsWith("otpauth://totp/"))
        assertTrue(uri.contains(secret))

        assertFalse(lockManager.verifyPartnerTotp("000000"))
        assertFalse(lockManager.verifyPartnerTotp("12345"))
    }

    @Test
    fun `test monotonic cooldown timer start and query`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockManager = TamperLockManager(context)

        lockManager.cancelCooldown()
        assertFalse(lockManager.isCooldownActive())

        lockManager.startCooldown(60_000L)
        assertTrue(lockManager.isCooldownActive())

        val remaining = lockManager.getRemainingCooldownMs()
        assertTrue(remaining in 1L..60_000L)

        lockManager.cancelCooldown()
        assertFalse(lockManager.isCooldownActive())
        assertEquals(0L, lockManager.getRemainingCooldownMs())
    }

    @Test
    fun `test device admin manager commands`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adminManager = DeviceAdminManager(context)

        val adbCmd = adminManager.getAdbCommand()
        assertTrue(adbCmd.startsWith("adb shell dpm set-device-owner com.example/"))
        assertTrue(adbCmd.contains("ShieldDeviceAdminReceiver"))

        val status = adminManager.getStatus()
        assertNotNull(status)
        assertEquals(adbCmd, status.adbCommand)
    }

    @Test
    fun `test dns packet parser and builder for a record`() {
        // Construct raw DNS query for "www.google.com" Type A (1), Class IN (1)
        val rawDnsQuery = byteArrayOf(
            0x12, 0x34, // Transaction ID: 0x1234
            0x01, 0x00, // Flags: Standard query, RD=1
            0x00, 0x01, // QDCOUNT = 1
            0x00, 0x00, // ANCOUNT = 0
            0x00, 0x00, // NSCOUNT = 0
            0x00, 0x00, // ARCOUNT = 0
            // QNAME: 3www6google3com0
            0x03, 'w'.code.toByte(), 'w'.code.toByte(), 'w'.code.toByte(),
            0x06, 'g'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'g'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00,
            0x00, 0x01, // QTYPE: A (1)
            0x00, 0x01  // QCLASS: IN (1)
        )

        val parsed = DnsPacketParser.parseDnsQuery(rawDnsQuery)
        assertNotNull(parsed)
        assertEquals(0x1234, parsed!!.id)
        assertTrue(parsed.isRecursionDesired)
        assertEquals("www.google.com", parsed.question.name)
        assertEquals(DnsRecordType.A, parsed.question.qType)

        // Build A response pointing to SafeSearch VIP 216.239.38.120
        val aResponse = DnsPacketBuilder.buildAResponse(parsed, "216.239.38.120")
        assertNotNull(aResponse)
        assertTrue(aResponse.size > rawDnsQuery.size)

        // Verify response flags: QR=1 (0x80)
        val flagByte = aResponse[2].toInt() and 0xFF
        assertTrue((flagByte and 0x80) != 0) // QR bit is set
        // Verify ANCOUNT = 1
        assertEquals(1, aResponse[7].toInt() and 0xFF)
    }

    @Test
    fun `test safesearch vip rewriter`() {
        val safeSearch = SafeSearchManager()
        assertTrue(safeSearch.isGoogleSearchDomain("www.google.com"))
        assertTrue(safeSearch.isGoogleSearchDomain("google.com"))
        assertTrue(safeSearch.isGoogleSearchDomain("google.co.uk"))
        assertFalse(safeSearch.isGoogleSearchDomain("googleapis.com"))

        val query = DnsPacketParser.parseDnsQuery(byteArrayOf(
            0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x06, 'g'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'g'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00,
            0x00, 0x01, 0x00, 0x01
        ))!!

        val response = safeSearch.rewriteDns(query)
        assertNotNull(response)

        // Non-search domain should not be rewritten
        val normalQuery = DnsPacketParser.parseDnsQuery(byteArrayOf(
            0x00, 0x02, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x09, 'w'.code.toByte(), 'i'.code.toByte(), 'k'.code.toByte(), 'i'.code.toByte(), 'p'.code.toByte(), 'e'.code.toByte(), 'd'.code.toByte(), 'i'.code.toByte(), 'a'.code.toByte(),
            0x03, 'o'.code.toByte(), 'r'.code.toByte(), 'g'.code.toByte(),
            0x00,
            0x00, 0x01, 0x00, 0x01
        ))!!
        assertNull(safeSearch.rewriteDns(normalQuery))
    }

    @Test
    fun `test domain blocklist matcher with stevenblack hosts format`() {
        val matcher = DomainBlocklistMatcher()
        matcher.clearBlocklist()

        val sampleHosts = """
            # StevenBlack Unified Hosts File Sample
            127.0.0.1 localhost
            0.0.0.0 0.0.0.0
            
            # Ad networks
            0.0.0.0 doubleclick.net
            0.0.0.0 telemetry.tracker.com # inline comment
            127.0.0.1 malicious-site.org
            
            # Subdomains
            0.0.0.0 ads.example.co.uk
        """.trimIndent()

        val count = matcher.loadFromReader(BufferedReader(StringReader(sampleHosts)))
        assertEquals(4, count)

        // Exact matches
        assertTrue(matcher.isBlocked("doubleclick.net").isBlocked)
        assertTrue(matcher.isBlocked("telemetry.tracker.com").isBlocked)
        assertTrue(matcher.isBlocked("malicious-site.org").isBlocked)

        // Subdomain hierarchical match
        assertTrue(matcher.isBlocked("sub.doubleclick.net").isBlocked)
        assertTrue(matcher.isBlocked("tracker.doubleclick.net").isBlocked)

        // Clean domain
        assertFalse(matcher.isBlocked("clean-example.com").isBlocked)
    }

    @Test
    fun `test raw ip udp packet wrapping and checksum`() {
        val srcIp = byteArrayOf(10, 0, 0, 2)
        val dstIp = byteArrayOf(10, 254.toByte(), 1, 1)
        val fakePayload = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val incoming = IpUdpPacket(
            isIpv6 = false,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = 54321,
            dstPort = 53,
            payload = fakePayload
        )

        val wrapped = DnsPacketBuilder.wrapDnsResponseInUdpIp(incoming, fakePayload)
        assertNotNull(wrapped)
        assertTrue(wrapped.size >= 28 + fakePayload.size)

        // Parse it back to verify swapped IP and Port
        val parsedBack = DnsPacketParser.parseIpPacket(wrapped, wrapped.size)
        assertNotNull(parsedBack)
        assertEquals(53, parsedBack!!.srcPort)
        assertEquals(54321, parsedBack.dstPort)
        assertTrue(parsedBack.srcIp.contentEquals(dstIp))
        assertTrue(parsedBack.dstIp.contentEquals(srcIp))
    }

    @Test
    fun `test nsfw content detector for keywords and search queries`() {
        val detector = com.example.core.accessibility.NsfwContentDetector()

        // Explicit direct domains and keywords
        assertTrue(detector.analyze("https://www.pornhub.com").isNsfw)
        assertTrue(detector.analyze("http://xxx-free-videos.net").isNsfw)
        assertTrue(detector.analyze("leetspeak-p0rn-site.com").isNsfw)

        // Search engine URL query parameter extraction
        val googleSearch = "https://www.google.com/search?q=free+xxx+videos&source=android"
        val searchResult = detector.analyze(googleSearch)
        assertTrue(searchResult.isNsfw)
        assertEquals("xxx", searchResult.matchedTerm)

        // Clean queries (avoid false positives like "essex")
        assertFalse(detector.analyze("https://en.wikipedia.org/wiki/Essex").isNsfw)
        assertFalse(detector.analyze("https://kotlinlang.org/docs/home.html").isNsfw)
        assertFalse(detector.analyze("https://github.com").isNsfw)
    }

    @Test
    fun `test browser url extractor supported packages`() {
        val extractor = com.example.core.accessibility.BrowserUrlExtractor()
        assertTrue(extractor.isSupportedBrowser("com.android.chrome"))
        assertTrue(extractor.isSupportedBrowser("org.mozilla.firefox"))
        assertTrue(extractor.isSupportedBrowser("com.brave.browser"))
        assertTrue(extractor.isSupportedBrowser("com.microsoft.emmx"))
        assertFalse(extractor.isSupportedBrowser("com.android.calculator2"))
    }
}
