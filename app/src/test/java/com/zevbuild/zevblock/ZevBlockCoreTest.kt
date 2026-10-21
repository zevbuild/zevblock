package com.zevbuild.zevblock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zevbuild.zevblock.core.accessibility.NsfwContentDetector
import com.zevbuild.zevblock.core.dns.DnsPacketBuilder
import com.zevbuild.zevblock.core.dns.DnsPacketParser
import com.zevbuild.zevblock.core.dns.DnsQuestion
import com.zevbuild.zevblock.core.dns.DnsRecordType
import com.zevbuild.zevblock.core.dns.DomainBlocklistMatcher
import com.zevbuild.zevblock.core.dns.IpUdpPacket
import com.zevbuild.zevblock.core.dns.ParsedDnsQuery
import com.zevbuild.zevblock.core.dns.SafeSearchManager
import com.zevbuild.zevblock.core.security.TamperLockManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * Unit test suite verifying system-level adult content blocking,
 * DNS packet parsing/synthesis, SafeSearch VIP rewriting,
 * and cryptographic integrity safeguards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZevBlockCoreTest {

    // =========================================================================
    // 1. Adult Content Detection & False-Positive Prevention Tests
    // =========================================================================
    @Test
    fun nsfwDetector_detectsExplicitSearchQueries() {
        val detector = NsfwContentDetector()

        val explicitUrls = listOf(
            "https://www.google.com/search?q=free+porn+videos",
            "https://duckduckgo.com/?q=watch+xxx+movie",
            "https://www.bing.com/search?q=hentai+uncensored",
            "https://search.yahoo.com/search?p=nude+celebrities",
            "https://youtube.com/results?search_query=sexy+girls+naked"
        )

        for (url in explicitUrls) {
            val res = detector.analyze(url)
            assertTrue("Expected URL to be detected as NSFW: $url", res.isNsfw)
        }
    }

    @Test
    fun nsfwDetector_detectsExplicitTldsAndPaths() {
        val detector = NsfwContentDetector()

        assertTrue(detector.analyze("https://bestcams.xxx").isNsfw)
        assertTrue(detector.analyze("https://tube.adult/index.html").isNsfw)
        assertTrue(detector.analyze("https://somedomain.com/porn/video123").isNsfw)
        assertTrue(detector.analyze("https://somedomain.com/hentai/read").isNsfw)
    }

    @Test
    fun nsfwDetector_preventsFalsePositives() {
        val detector = NsfwContentDetector()

        val harmlessUrls = listOf(
            "https://www.google.com/search?q=essex+county+council",
            "https://en.wikipedia.org/wiki/Sussex",
            "https://www.google.com/search?q=data+analysis+tutorial",
            "https://www.google.com/search?q=panama+canal+cruise",
            "https://www.google.com/search?q=historical+documentary",
            "https://www.google.com/search?q=classic+cars+for+sale"
        )

        for (url in harmlessUrls) {
            val res = detector.analyze(url)
            assertFalse("Harmless URL falsely flagged as NSFW: $url (Matched '${res.matchedTerm}')", res.isNsfw)
        }
    }

    // =========================================================================
    // 2. Domain Blocklist Matcher & Adult CDN Tests
    // =========================================================================
    @Test
    fun domainBlocklist_blocksAdultDomainsAndCdns() {
        val matcher = DomainBlocklistMatcher()

        // Major adult sites
        assertTrue(matcher.isBlocked("pornhub.com").isBlocked)
        assertTrue(matcher.isBlocked("xvideos.com").isBlocked)
        assertTrue(matcher.isBlocked("xnxx.com").isBlocked)
        assertTrue(matcher.isBlocked("xhamster.com").isBlocked)
        assertTrue(matcher.isBlocked("chaturbate.com").isBlocked)
        assertTrue(matcher.isBlocked("stripchat.com").isBlocked)
        assertTrue(matcher.isBlocked("onlyfans.com").isBlocked)

        // Adult CDNs (critical for stopping mobile streaming video playback)
        assertTrue(matcher.isBlocked("phncdn.com").isBlocked)
        assertTrue(matcher.isBlocked("ypncdn.com").isBlocked)
        assertTrue(matcher.isBlocked("xhcdn.com").isBlocked)
    }

    @Test
    fun domainBlocklist_hierarchicalSubdomainMatching() {
        val matcher = DomainBlocklistMatcher()

        // Hierarchical match: blocking phncdn.com must automatically block arbitrary deep subdomains
        assertTrue(matcher.isBlocked("ci.phncdn.com").isBlocked)
        assertTrue(matcher.isBlocked("video.edge.phncdn.com").isBlocked)
        assertTrue(matcher.isBlocked("m.xvideos.com").isBlocked)
        assertTrue(matcher.isBlocked("static.xhamster.com").isBlocked)

        // Clean domains remain allowed
        assertFalse(matcher.isBlocked("wikipedia.org").isBlocked)
        assertFalse(matcher.isBlocked("github.com").isBlocked)
    }

    // =========================================================================
    // 3. SafeSearch VIP Rewriter Tests
    // =========================================================================
    @Test
    fun safeSearchManager_rewritesSearchEnginesToStrictVips() {
        val manager = SafeSearchManager()

        // Dummy raw query bytes with dummy 12-byte header + question section
        val dummyQueryBytes = ByteArray(32)
        val dummyQuery = ParsedDnsQuery(
            id = 0x1234,
            isRecursionDesired = true,
            question = DnsQuestion(name = "www.google.com", qType = DnsRecordType.A, qClass = 1),
            rawQueryBytes = dummyQueryBytes
        )

        // Google SafeSearch VIP
        val googleResponse = manager.rewriteDns(dummyQuery)
        assertNotNull("Google query must be intercepted by SafeSearch VIP", googleResponse)

        // YouTube Restricted Mode VIP
        val youtubeQuery = dummyQuery.copy(question = DnsQuestion("youtube.com", DnsRecordType.A, 1))
        val youtubeResponse = manager.rewriteDns(youtubeQuery)
        assertNotNull("YouTube query must be intercepted by Restricted VIP", youtubeResponse)

        // Bing Strict SafeSearch VIP
        val bingQuery = dummyQuery.copy(question = DnsQuestion("bing.com", DnsRecordType.A, 1))
        val bingResponse = manager.rewriteDns(bingQuery)
        assertNotNull("Bing query must be intercepted by Strict VIP", bingResponse)

        // DuckDuckGo SafeSearch
        val ddgQuery = dummyQuery.copy(question = DnsQuestion("duckduckgo.com", DnsRecordType.A, 1))
        val ddgResponse = manager.rewriteDns(ddgQuery)
        assertNotNull("DuckDuckGo query must be intercepted by Safe CNAME/VIP", ddgResponse)

        // Non-search domain should NOT be rewritten
        val cleanQuery = dummyQuery.copy(question = DnsQuestion("example.com", DnsRecordType.A, 1))
        val cleanResponse = manager.rewriteDns(cleanQuery)
        assertEquals(null, cleanResponse)
    }

    // =========================================================================
    // 4. Raw DNS Packet Building & Synthesis Tests
    // =========================================================================
    @Test
    fun dnsPacketParser_calculatesQuestionEndOffsetWithEdns() {
        val baseQuery = createSampleDnsQuery("adultsite.com")
        // Append 11 dummy EDNS option bytes to simulate EDNS0 query packet
        val ednsQueryBytes = baseQuery + byteArrayOf(0x00, 0x00, 0x29, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

        val parsedQuery = DnsPacketParser.parseDnsQuery(ednsQueryBytes)
        assertNotNull("Failed to parse EDNS query", parsedQuery)
        assertEquals(baseQuery.size, parsedQuery!!.questionEndOffset)

        // Verify synthesized response copies only question section (baseQuery.size - 12)
        val sinkholeResponse = DnsPacketBuilder.buildSinkholeResponse(parsedQuery, blockedIp = "0.0.0.0")
        // Answer header (12) + Question section (baseQuery.size - 12) + Answer RR (16)
        val expectedLength = 12 + (baseQuery.size - 12) + 16
        assertEquals(expectedLength, sinkholeResponse.size)
    }

    @Test
    fun dnsPacketBuilder_synthesizesSinkholeResponse() {
        val queryBytes = createSampleDnsQuery("adultsite.com")
        val parsedQuery = DnsPacketParser.parseDnsQuery(queryBytes)
        assertNotNull("Failed to parse sample query", parsedQuery)

        // Synthesize sinkhole response
        val sinkholePacket = DnsPacketBuilder.buildSinkholeResponse(parsedQuery!!, blockedIp = "0.0.0.0")
        assertNotNull(sinkholePacket)
        assertTrue(sinkholePacket.size > 12)

        // Verify DNS Header Flags: QR=1 (Response)
        val flags = ((sinkholePacket[2].toInt() and 0xFF) shl 8) or (sinkholePacket[3].toInt() and 0xFF)
        val isResponse = (flags and 0x8000) != 0
        assertTrue("DNS packet must have Response flag QR=1", isResponse)
    }

    @Test
    fun dnsPacketBuilder_wrapInUdpIpComputesChecksums() {
        val queryBytes = createSampleDnsQuery("test.com")
        val parsedQuery = DnsPacketParser.parseDnsQuery(queryBytes)!!
        val answer = DnsPacketBuilder.buildAResponse(parsedQuery, "1.2.3.4")

        val incomingIpPacket = IpUdpPacket(
            isIpv6 = false,
            srcIp = byteArrayOf(10, 254.toByte(), 1, 2),
            dstIp = byteArrayOf(10, 254.toByte(), 1, 1),
            srcPort = 54321,
            dstPort = 53,
            payload = queryBytes
        )

        val fullPacket = DnsPacketBuilder.wrapDnsResponseInUdpIp(incomingIpPacket, answer)
        assertTrue(fullPacket.size >= 28 + answer.size)

        // IPv4 Version check (0x45)
        assertEquals(0x45.toByte(), fullPacket[0])

        // IP source must now be 10.254.1.1 (swapped)
        assertEquals(10.toByte(), fullPacket[12])
        assertEquals(254.toByte(), fullPacket[13])
        assertEquals(1.toByte(), fullPacket[14])
        assertEquals(1.toByte(), fullPacket[15])
    }

    // =========================================================================
    // 5. Base32 Encoding / Decoding & TOTP Engine Tests
    // =========================================================================
    @Test
    fun base32_encodeDecodeRoundTrip() {
        val sample = "ZEVBLOCK-SECRET-KEY-123456789".toByteArray(Charsets.UTF_8)
        val encoded = TamperLockManager.Base32.encode(sample)
        val decoded = TamperLockManager.Base32.decode(encoded)

        assertEquals(String(sample, Charsets.UTF_8), String(decoded, Charsets.UTF_8))
    }

    // =========================================================================
    // 6. GitHub In-App Auto-Update Version Comparison Tests
    // =========================================================================
    @Test
    fun appUpdateManager_correctlyDetectsNewerVersions() {
        assertTrue(com.zevbuild.zevblock.core.updater.AppUpdateManager.isNewerVersion("1.1", "v1.2-stable"))
        assertTrue(com.zevbuild.zevblock.core.updater.AppUpdateManager.isNewerVersion("1.0", "v1.1"))
        assertTrue(com.zevbuild.zevblock.core.updater.AppUpdateManager.isNewerVersion("1.1", "v2.0"))
        assertTrue(com.zevbuild.zevblock.core.updater.AppUpdateManager.isNewerVersion("1.1.0", "v1.1.1"))

        assertFalse(com.zevbuild.zevblock.core.updater.AppUpdateManager.isNewerVersion("1.2", "v1.2-stable"))
        assertFalse(com.zevbuild.zevblock.core.updater.AppUpdateManager.isNewerVersion("1.2", "v1.1"))
        assertFalse(com.zevbuild.zevblock.core.updater.AppUpdateManager.isNewerVersion("2.0", "v1.9"))
    }

    @Test
    fun appUpdateManager_cleansVersionStringProperly() {
        assertEquals("1.2", com.zevbuild.zevblock.core.updater.AppUpdateManager.cleanVersion("v1.2-stable"))
        assertEquals("2.0", com.zevbuild.zevblock.core.updater.AppUpdateManager.cleanVersion("V2.0"))
        assertEquals("1.3.1", com.zevbuild.zevblock.core.updater.AppUpdateManager.cleanVersion("v1.3.1-beta2"))
    }

    // =========================================================================
    // 7. CrashGuardManager & Telemetry Speed Formatting Tests
    // =========================================================================
    @Test
    fun crashGuardManager_recordsAndRecoversExceptionsSafely() {
        val guard = com.zevbuild.zevblock.core.diagnostics.CrashGuardManager
        guard.clearLogs()
        assertEquals(0, guard.diagnosticLogs.value.size)

        guard.recordHandledException(
            source = "DnsBlockerVpnService.test",
            throwable = IllegalStateException("Simulated socket timeout"),
            autoRecovered = true
        )
        guard.recordServiceRecovery(
            source = "BootCompletedReceiver",
            detail = "Skipped unpermitted background VPN start"
        )

        assertEquals(2, guard.diagnosticLogs.value.size)
        assertEquals(2, guard.autoRecoveryCount.value)
        val report = guard.buildDiagnosticReport()
        assertTrue(report.contains("Simulated socket timeout"))
        assertTrue(report.contains("BootCompletedReceiver"))

        guard.clearLogs()
        assertEquals(0, guard.diagnosticLogs.value.size)
    }

    @Test
    fun dnsBlockerVpnService_formatsSpeedsAndSimulatesTelemetry() {
        val vpn = com.zevbuild.zevblock.vpn.DnsBlockerVpnService
        assertEquals("0 B/s", vpn.formatSpeed(0L))
        assertEquals("512 B/s", vpn.formatSpeed(512L))
        assertTrue(vpn.formatSpeed(2048L).contains("KB/s"))
        assertTrue(vpn.formatBytes(5 * 1024 * 1024L).contains("MB"))

        val beforeCount = vpn.totalQueriesCount.value
        val blockedEntry = vpn.simulateDnsPipelineLookup("pornhub.com")
        val safeEntry = vpn.simulateDnsPipelineLookup("www.google.com")
        val allowedEntry = vpn.simulateDnsPipelineLookup("wikipedia.org")
        assertTrue(blockedEntry.isBlocked)
        assertTrue(safeEntry.isSafeSearchRewritten)
        assertFalse(allowedEntry.isBlocked)
        assertTrue(vpn.totalQueriesCount.value >= beforeCount + 3)
        assertTrue(vpn.recentLogs.value.isNotEmpty())
    }

    // =========================================================================
    // 7. Security Hardening & Regression Tests
    // =========================================================================
    @Test
    fun tamperLockManager_rejectsReplayedTotpCodes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockManager = TamperLockManager(context)
        val secret = lockManager.setupNewPartnerSecret()

        val currentEpochSeconds = System.currentTimeMillis() / 1000L
        val currentStep = currentEpochSeconds / 30L
        val code = lockManager.generateTotpCodeForStep(secret, currentStep)

        // First verification should succeed
        assertTrue("First submission of valid TOTP code must succeed", lockManager.verifyPartnerTotp(code))

        // Second verification with the exact same code / step must be rejected (replay prevention)
        assertFalse("Replaying the same TOTP code must be rejected", lockManager.verifyPartnerTotp(code))
    }

    @Test
    fun tamperLockManager_throttlesRepeatedInvalidAttempts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockManager = TamperLockManager(context)
        val secret = lockManager.setupNewPartnerSecret()

        // Submit 5 invalid attempts
        for (i in 1..5) {
            lockManager.verifyPartnerTotp("000000")
        }

        // Lockout must now be engaged
        assertTrue("Lockout must be engaged after 5 failed attempts", lockManager.getLockoutRemainingMs() > 0)

        // Even with the correct code, verification must be rejected while locked out
        val currentStep = (System.currentTimeMillis() / 1000L) / 30L
        val validCode = lockManager.generateTotpCodeForStep(secret, currentStep)
        assertFalse("Verification must fail while locked out", lockManager.verifyPartnerTotp(validCode))
    }

    @Test
    fun tamperLockManager_failsClosedOnCorruptedState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockManager = TamperLockManager(context)

        lockManager.startCooldown(60_000L)
        assertTrue(lockManager.isCooldownActive())

        // Tamper with the signature in SharedPreferences
        val prefs = context.getSharedPreferences("shieldguard_tamper_lock", Context.MODE_PRIVATE)
        prefs.edit().putString("cooldown_signature", "tampered_bogus_signature").commit()

        // When integrity check fails, it must fail closed and refuse to authorize unlock
        val remaining = lockManager.getRemainingCooldownMs()
        assertEquals("Tampered state must return DEFAULT_COOLDOWN_MS", TamperLockManager.DEFAULT_COOLDOWN_MS, remaining)
        assertFalse("Tampered state must not be marked completed", lockManager.isCooldownFinished())
    }

    @Test
    fun dnsPacketParser_validatesUpstreamDnsResponses() {
        val queryBytes = createSampleDnsQuery("example.com")
        val parsedQuery = DnsPacketParser.parseDnsQuery(queryBytes)
        assertNotNull(parsedQuery)

        // Build valid response
        val validResponse = DnsPacketBuilder.buildSinkholeResponse(parsedQuery!!, "93.184.216.34")
        assertTrue("Valid response matching query must pass validation", DnsPacketParser.validateDnsResponse(validResponse, parsedQuery))

        // 1. Mismatched Transaction ID
        val wrongIdResponse = validResponse.clone()
        wrongIdResponse[0] = 0xFF.toByte()
        wrongIdResponse[1] = 0xFF.toByte()
        assertFalse("Response with mismatched Transaction ID must fail", DnsPacketParser.validateDnsResponse(wrongIdResponse, parsedQuery))

        // 2. Not a response (QR=0)
        val qrZeroResponse = validResponse.clone()
        qrZeroResponse[2] = (qrZeroResponse[2].toInt() and 0x7F).toByte()
        assertFalse("Response with QR=0 must fail", DnsPacketParser.validateDnsResponse(qrZeroResponse, parsedQuery))

        // 3. Question mismatch (response contains different domain)
        val otherQuery = DnsPacketParser.parseDnsQuery(createSampleDnsQuery("otherdomain.org"))!!
        val mismatchedDomainResponse = DnsPacketBuilder.buildSinkholeResponse(otherQuery, "1.2.3.4")
        assertFalse("Response with different question domain must fail", DnsPacketParser.validateDnsResponse(mismatchedDomainResponse, parsedQuery))

        // 4. Truncated packet (< 12 bytes)
        assertFalse("Truncated packet must fail validation", DnsPacketParser.validateDnsResponse(ByteArray(10), parsedQuery))
    }

    @Test
    fun dnsPacketParser_handlesIpv6ExtensionHeadersAndBounds() {
        // Construct IPv6 packet with Hop-by-Hop Options Header (NextHeader = 0)
        val out = ByteArrayOutputStream()
        // IPv6 Header (40 bytes)
        out.write(0x60); out.write(0x00); out.write(0x00); out.write(0x00)
        val payloadLen = 8 + 8 + 12 // Hop-by-Hop (8) + UDP (8) + DNS (12)
        out.write((payloadLen shr 8) and 0xFF); out.write(payloadLen and 0xFF)
        out.write(0x00) // Next Header: Hop-by-Hop Options (0)
        out.write(64)   // Hop Limit: 64
        out.write(ByteArray(16) { 0x20.toByte() })
        out.write(ByteArray(16) { 0x26.toByte() })

        // Hop-by-Hop Header (8 bytes): Next Header = UDP (17), Hdr Ext Len = 0 (means (0+1)*8 = 8 bytes)
        out.write(17) // Next Header: UDP
        out.write(0)  // Hdr Ext Len: 0
        out.write(ByteArray(6)) // Options padding

        // UDP Header (8 bytes)
        val udpLen = 8 + 12
        out.write(0xC0); out.write(0x00)
        out.write(0x00); out.write(0x35)
        out.write((udpLen shr 8) and 0xFF); out.write(udpLen and 0xFF)
        out.write(0x00); out.write(0x00)

        // Dummy DNS Header (12 bytes)
        out.write(ByteArray(12) { 0x01 })

        val packetBytes = out.toByteArray()
        val parsed = DnsPacketParser.parseIpPacket(packetBytes, packetBytes.size)
        assertNotNull("IPv6 packet with Hop-by-Hop extension header must be parsed", parsed)
        assertTrue(parsed!!.isIpv6)
        assertEquals(53, parsed.dstPort)
        assertEquals(49152, parsed.srcPort)
        assertEquals(12, parsed.payload.size)

        // Malformed / truncated IPv6 bounds test: pass truncated length
        val truncatedParsed = DnsPacketParser.parseIpPacket(packetBytes, 30)
        org.junit.Assert.assertNull("Truncated IPv6 packet must return null safely", truncatedParsed)
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================
    private fun createSampleDnsQuery(domain: String): ByteArray {
        val out = ByteArrayOutputStream()
        // ID
        out.write(0x12); out.write(0x34)
        // Flags (standard query, recursion desired: 0x0100)
        out.write(0x01); out.write(0x00)
        // QDCOUNT = 1, ANCOUNT = 0, NSCOUNT = 0, ARCOUNT = 0
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)

        // Question name labels
        for (part in domain.split('.')) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0x00) // Null terminator

        // QTYPE = A (1), QCLASS = IN (1)
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x01)

        return out.toByteArray()
    }
}
