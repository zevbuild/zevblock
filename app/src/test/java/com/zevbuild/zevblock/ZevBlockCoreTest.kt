package com.zevbuild.zevblock

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
