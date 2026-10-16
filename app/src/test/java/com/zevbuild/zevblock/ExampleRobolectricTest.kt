package com.zevbuild.zevblock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zevbuild.zevblock.core.admin.DeviceAdminManager
import com.zevbuild.zevblock.core.dns.DnsPacketBuilder
import com.zevbuild.zevblock.core.dns.DnsPacketParser
import com.zevbuild.zevblock.core.dns.DnsRecordType
import com.zevbuild.zevblock.core.dns.DomainBlocklistMatcher
import com.zevbuild.zevblock.core.dns.IpUdpPacket
import com.zevbuild.zevblock.core.dns.SafeSearchManager
import com.zevbuild.zevblock.core.security.TamperLockManager
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
    fun `test temporary bypass start and cancel`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockManager = TamperLockManager(context)

        lockManager.cancelTemporaryBypass()
        assertFalse(lockManager.isBypassActive())
        assertFalse(TamperLockManager.isBypassActive(context))

        lockManager.startTemporaryBypass(15 * 60 * 1000L)
        assertTrue(lockManager.isBypassActive())
        assertTrue(TamperLockManager.isBypassActive(context))

        val remaining = lockManager.getRemainingBypassMs()
        assertTrue(remaining in 1L..(15 * 60 * 1000L))

        lockManager.cancelTemporaryBypass()
        assertFalse(lockManager.isBypassActive())
        assertFalse(TamperLockManager.isBypassActive(context))
        assertEquals(0L, lockManager.getRemainingBypassMs())
    }

    @Test
    fun `test device admin manager commands`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adminManager = DeviceAdminManager(context)

        val adbCmd = adminManager.getAdbCommand()
        assertTrue(adbCmd.startsWith("adb shell dpm set-device-owner com.zevbuild.zevblock/"))
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
        val detector = com.zevbuild.zevblock.core.accessibility.NsfwContentDetector()

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
        val extractor = com.zevbuild.zevblock.core.accessibility.BrowserUrlExtractor()
        assertTrue(extractor.isSupportedBrowser("com.android.chrome"))
        assertTrue(extractor.isSupportedBrowser("org.mozilla.firefox"))
        assertTrue(extractor.isSupportedBrowser("com.brave.browser"))
        assertTrue(extractor.isSupportedBrowser("com.microsoft.emmx"))
        assertFalse(extractor.isSupportedBrowser("com.android.calculator2"))
    }

    @Test
    fun `test messaging app detection for non-intrusive chat warnings`() {
        // Supported messaging & chat applications
        assertTrue(com.zevbuild.zevblock.accessibility.ShieldAccessibilityService.isMessagingApp("com.whatsapp"))
        assertTrue(com.zevbuild.zevblock.accessibility.ShieldAccessibilityService.isMessagingApp("com.whatsapp.w4b"))
        assertTrue(com.zevbuild.zevblock.accessibility.ShieldAccessibilityService.isMessagingApp("org.telegram.messenger"))
        assertTrue(com.zevbuild.zevblock.accessibility.ShieldAccessibilityService.isMessagingApp("org.thoughtcrime.securesms"))
        assertTrue(com.zevbuild.zevblock.accessibility.ShieldAccessibilityService.isMessagingApp("com.google.android.apps.messaging"))
        assertTrue(com.zevbuild.zevblock.accessibility.ShieldAccessibilityService.isMessagingApp("com.facebook.orca"))
        assertTrue(com.zevbuild.zevblock.accessibility.ShieldAccessibilityService.isMessagingApp("com.discord"))
        assertTrue(com.zevbuild.zevblock.accessibility.ShieldAccessibilityService.isMessagingApp("com.snapchat.android"))

        // Browsers & other apps must NOT be treated as messaging apps
        assertFalse(com.zevbuild.zevblock.accessibility.ShieldAccessibilityService.isMessagingApp("com.android.chrome"))
        assertFalse(com.zevbuild.zevblock.accessibility.ShieldAccessibilityService.isMessagingApp("org.mozilla.firefox"))
        assertFalse(com.zevbuild.zevblock.accessibility.ShieldAccessibilityService.isMessagingApp("com.android.settings"))
        assertFalse(com.zevbuild.zevblock.accessibility.ShieldAccessibilityService.isMessagingApp("com.android.calculator2"))
    }

    @Test
    fun `test zero-bypass guarantee on hardcoded adult blocklists`() {
        val matcher = DomainBlocklistMatcher()

        // Adult domains are blocked by default
        assertTrue(matcher.isBlocked("pornhub.com").isBlocked)
        assertTrue(matcher.isBlocked("xvideos.com").isBlocked)

        // Attempting to whitelist an adult domain must be rejected and fail
        matcher.addAllowedDomain("pornhub.com")
        matcher.addAllowedDomain("xvideos.com")

        // Must STILL be blocked (zero bypass guarantee)
        assertTrue("Built-in adult domain must NEVER be whitelisted", matcher.isBlocked("pornhub.com").isBlocked)
        assertTrue("Built-in adult domain must NEVER be whitelisted", matcher.isBlocked("xvideos.com").isBlocked)
    }

    @Test
    fun `test custom shield rule manager with partner totp and cooldown`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockManager = TamperLockManager(context)
        val secret = lockManager.setupNewPartnerSecret()

        val matcher = DomainBlocklistMatcher()
        val nsfwDetector = com.zevbuild.zevblock.core.accessibility.NsfwContentDetector()
        val ruleManager = com.zevbuild.zevblock.core.rules.CustomShieldRuleManager(context, lockManager, matcher, nsfwDetector)

        // 1. Add Custom Domain
        val domainRes = ruleManager.addDomain("gambling-site.com")
        assertTrue(domainRes is com.zevbuild.zevblock.core.rules.RuleOperationResult.Success)
        assertTrue(ruleManager.customDomains.value.contains("gambling-site.com"))
        assertTrue(matcher.isBlocked("gambling-site.com").isBlocked)

        // Cannot add built-in adult domain as custom (it is already system-protected)
        val adultRes = ruleManager.addDomain("pornhub.com")
        assertTrue(adultRes is com.zevbuild.zevblock.core.rules.RuleOperationResult.ProtectedBySystem)

        // 2. Add Custom Keyword
        val kwRes = ruleManager.addKeyword("badtriggerphrase")
        assertTrue(kwRes is com.zevbuild.zevblock.core.rules.RuleOperationResult.Success)
        assertTrue(ruleManager.customKeywords.value.contains("badtriggerphrase"))
        assertTrue(nsfwDetector.analyze("badtriggerphrase").isNsfw)

        // Cannot add built-in adult keyword as custom (already protected)
        val nsfwRes = ruleManager.addKeyword("porn")
        assertTrue(nsfwRes is com.zevbuild.zevblock.core.rules.RuleOperationResult.ProtectedBySystem)

        // 3. Remove with INVALID TOTP -> Must Fail
        val badTotpRes = ruleManager.removeDomainWithTotp("gambling-site.com", "000000")
        assertTrue(badTotpRes is com.zevbuild.zevblock.core.rules.RuleOperationResult.TotpFailed)
        assertTrue(ruleManager.customDomains.value.contains("gambling-site.com"))

        // 4. Remove with VALID Partner TOTP -> Succeeds immediately
        val currentStep = (System.currentTimeMillis() / 1000L) / 30L
        val validTotp = lockManager.generateTotpCodeForStep(secret, currentStep)
        val goodTotpRes = ruleManager.removeDomainWithTotp("gambling-site.com", validTotp)
        assertTrue(goodTotpRes is com.zevbuild.zevblock.core.rules.RuleOperationResult.Success)
        assertFalse(ruleManager.customDomains.value.contains("gambling-site.com"))

        // 5. Cooldown removal flow
        val cooldownRes = ruleManager.requestCooldownRemoval("badtriggerphrase", com.zevbuild.zevblock.core.rules.RuleType.KEYWORD)
        assertTrue(cooldownRes is com.zevbuild.zevblock.core.rules.RuleOperationResult.CooldownStarted)
        assertEquals(1, ruleManager.pendingRemovals.value.size)
        // Rule remains strictly active during cooldown
        assertTrue(ruleManager.customKeywords.value.contains("badtriggerphrase"))
    }

    @Test
    fun `test protection stats manager aggregation and streak`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockManager = TamperLockManager(context)
        val secret = lockManager.setupNewPartnerSecret()

        val statsManager = com.zevbuild.zevblock.core.stats.ProtectionStatsManager(context, lockManager)

        // Record events
        statsManager.recordDnsQuery(isBlocked = true, isSafeSearch = false)
        statsManager.recordDnsQuery(isBlocked = false, isSafeSearch = true)
        statsManager.recordScreenBlock()
        statsManager.recordIncognitoBlock()
        statsManager.recordChatWarning()
        statsManager.recordSettingsTamper()

        // Check today's totals
        val today = statsManager.todayStat.value
        assertEquals(1L, today.dnsBlocked)
        assertEquals(1L, today.screenBlocked)
        assertEquals(1L, today.incognitoBlocked)
        assertEquals(1L, today.chatWarnings)
        assertEquals(1L, today.settingsBlocked)
        assertEquals(5L, today.totalBlocks)

        // Check 7-day list
        assertEquals(7, statsManager.past7Days.value.size)
        assertTrue(statsManager.streakDays.value >= 1)
        assertTrue(statsManager.allTimeTotalBlocks.value >= 5L)

        // Unauthorized reset without partner TOTP must fail
        assertFalse(statsManager.resetStatsWithPartner("123456"))

        // Authorized reset with partner TOTP succeeds
        val currentStep = (System.currentTimeMillis() / 1000L) / 30L
        val validTotp = lockManager.generateTotpCodeForStep(secret, currentStep)
        assertTrue(statsManager.resetStatsWithPartner(validTotp))
        assertEquals(0L, statsManager.allTimeTotalBlocks.value)
    }

    @Test
    fun `test first launch purpose disclaimer preference`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("zevblock_app_prefs", Context.MODE_PRIVATE)

        // Initially not seen
        assertFalse(prefs.getBoolean("has_seen_purpose_disclaimer", false))

        // User acknowledges dialog
        prefs.edit().putBoolean("has_seen_purpose_disclaimer", true).commit()
        assertTrue(prefs.getBoolean("has_seen_purpose_disclaimer", false))
    }

    @Test
    fun `test app update manager download url validation`() {
        // Valid GitHub release download URLs
        assertTrue(com.zevbuild.zevblock.core.updater.AppUpdateManager.isValidDownloadUrl("https://github.com/zevbuild/zevblock/releases/download/v1.5-stable/ZevBlock-v1.5-stable.apk"))
        assertTrue(com.zevbuild.zevblock.core.updater.AppUpdateManager.isValidDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset-2e65be/12345/app.apk"))

        // Reject insecure HTTP or third-party/malicious domains
        assertFalse(com.zevbuild.zevblock.core.updater.AppUpdateManager.isValidDownloadUrl("http://github.com/zevbuild/zevblock/releases/download/v1.5/app.apk"))
        assertFalse(com.zevbuild.zevblock.core.updater.AppUpdateManager.isValidDownloadUrl("https://malicious-site.com/fake-update.apk"))
        assertFalse(com.zevbuild.zevblock.core.updater.AppUpdateManager.isValidDownloadUrl("javascript:alert(1)"))
        assertFalse(com.zevbuild.zevblock.core.updater.AppUpdateManager.isValidDownloadUrl(""))
    }
}
