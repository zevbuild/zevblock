package com.zevbuild.zevblock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zevbuild.zevblock.accessibility.ShieldAccessibilityService
import com.zevbuild.zevblock.core.dns.SafeSearchManager
import com.zevbuild.zevblock.core.prefs.ZevBlockPreferences
import com.zevbuild.zevblock.core.security.TamperLockManager
import com.zevbuild.zevblock.ui.qr.QrCodeGenerator
import com.zevbuild.zevblock.vpn.DnsBlockerVpnService
import com.zevbuild.zevblock.vpn.DomainCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiUxRedesignTest {

    @Test
    fun zevBlockPreferences_persistsAndAppliesServiceTogglesWithoutUrlStorage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ZevBlockPreferences(context)

        prefs.setThemeMode(ZevBlockPreferences.THEME_LIGHT)
        prefs.setNotificationMode(ZevBlockPreferences.NOTIF_SPEED)
        prefs.setDevLabEnabled(true)
        prefs.setIncognitoBlockEnabled(false)
        prefs.setChatWarningEnabled(false)
        prefs.setSafeSearchEnabled(false)

        assertEquals(ZevBlockPreferences.THEME_LIGHT, prefs.themeMode.value)
        assertEquals(ZevBlockPreferences.NOTIF_SPEED, ZevBlockPreferences.getNotificationMode(context))
        assertTrue(prefs.devLabEnabled.value)
        assertFalse(ShieldAccessibilityService.isIncognitoBlockEnabled)
        assertFalse(ShieldAccessibilityService.isChatWarningEnabled)
        assertFalse(DnsBlockerVpnService.safeSearchManager.isEnabled)

        // Reset back cleanly and verify applyFromStorage restores
        prefs.setIncognitoBlockEnabled(true)
        prefs.setChatWarningEnabled(true)
        prefs.setSafeSearchEnabled(true)
        ZevBlockPreferences.applyFromStorage(context)
        assertTrue(ShieldAccessibilityService.isIncognitoBlockEnabled)
        assertTrue(ShieldAccessibilityService.isChatWarningEnabled)
        assertTrue(DnsBlockerVpnService.safeSearchManager.isEnabled)
    }

    @Test
    fun tamperLockManager_tracksPairingConfirmationAndCooldownConsumption() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockManager = TamperLockManager(context)
        lockManager.clearPartnerSecret()
        assertFalse(lockManager.isPairingConfirmed())

        // Newly generated key waits for partner's first verification before hiding in vault
        val secret = lockManager.setupNewPartnerSecret()
        assertFalse(lockManager.isPairingConfirmed())

        val step = (System.currentTimeMillis() / 1000L) / 30L
        val validCode = lockManager.generateTotpCodeForStep(secret, step)
        assertTrue(lockManager.verifyPartnerTotp(validCode))
        assertTrue(lockManager.isPairingConfirmed())

        // Cooldown consumption fails closed when cooldown is still running or inactive
        lockManager.startCooldown(60_000L)
        lockManager.setCooldownPurpose(TamperLockManager.COOLDOWN_PURPOSE_UNPAIR)
        assertEquals(TamperLockManager.COOLDOWN_PURPOSE_UNPAIR, lockManager.getCooldownPurpose())
        assertFalse(lockManager.consumeCompletedCooldown())
        lockManager.cancelCooldown()
    }

    @Test
    fun qrCodeGenerator_buildsIsoCompliantFinderTimingAndDarkModuleMatrix() {
        val uri = "otpauth://totp/ZevBlock:Partner?secret=JBSWY3DPEHPK3PXP&issuer=ZevBlock&algorithm=SHA1&digits=6&period=30"
        val matrix = QrCodeGenerator.encodeToMatrix(uri)
        val size = matrix.size

        // Standard QR sizes follow 4*V + 17 (V >= 1 -> size >= 21, and uri > 53 bytes -> V >= 4 -> >= 33)
        assertTrue("QR size must follow 4*V+17 formula and accommodate URI: $size", size >= 33 && (size - 17) % 4 == 0)
        assertEquals(size, matrix[0].size)

        // Verify Top-Left 7x7 finder corners & center 3x3
        for (r in 0..6) {
            assertTrue(matrix[r][0])
            assertTrue(matrix[r][6])
        }
        assertTrue(matrix[3][3])
        assertFalse(matrix[1][1])

        // Verify Top-Right & Bottom-Left finder outer borders
        assertTrue(matrix[0][size - 7])
        assertTrue(matrix[size - 7][0])

        // Mandatory dark module at (size - 8, 8)
        assertTrue(matrix[size - 8][8])

        // Timing pattern alternating along row 6
        assertTrue(matrix[6][8])
        assertFalse(matrix[6][9])
        assertTrue(matrix[6][10])
    }

    @Test
    fun safeSearchManager_describeMatchCoversAllSupportedEngines() {
        val manager = SafeSearchManager()
        assertNotNull(manager.describeMatch("www.google.co.in"))
        assertNotNull(manager.describeMatch("m.youtube.com"))
        assertNotNull(manager.describeMatch("www.bing.com"))
        assertNotNull(manager.describeMatch("duckduckgo.com"))
        assertNotNull(manager.describeMatch("search.yahoo.com"))
        assertNotNull(manager.describeMatch("search.brave.com"))
        assertNotNull(manager.describeMatch("www.qwant.com"))

        assertNull(manager.describeMatch("apis.google.com"))
        assertNull(manager.describeMatch("wikipedia.org"))
    }

    @Test
    fun dnsBlockerVpnService_inspectionAndSessionLogPauseWorkWithoutSideEffects() {
        DnsBlockerVpnService.clearSessionLogs()
        DnsBlockerVpnService.setSessionLogPaused(false)
        DnsBlockerVpnService.safeSearchManager.isEnabled = true

        val beforeTotal = DnsBlockerVpnService.totalQueriesCount.value

        // Read-only lookup inspects accurately across categories without inflating live counters
        val googleRes = DnsBlockerVpnService.inspectDomainLookup("https://www.google.co.in/search?q=test")
        assertEquals(DomainCategory.SAFESEARCH_VIP, googleRes.category)
        assertFalse(googleRes.isEditable)

        val adultRes = DnsBlockerVpnService.inspectDomainLookup("pornhub.com")
        assertEquals(DomainCategory.ADULT_SHIELD, adultRes.category)
        assertFalse(adultRes.isEditable)

        val invalidRes = DnsBlockerVpnService.inspectDomainLookup("notadomain")
        assertEquals(DomainCategory.INVALID, invalidRes.category)

        assertEquals(beforeTotal, DnsBlockerVpnService.totalQueriesCount.value)

        // Pausing session RAM log suppresses recording into recentLogs
        DnsBlockerVpnService.setSessionLogPaused(true)
        DnsBlockerVpnService.simulateDnsPipelineLookup("wikipedia.org")
        assertTrue(DnsBlockerVpnService.recentLogs.value.isEmpty())

        // Resuming records properly and Clear empties logs
        DnsBlockerVpnService.setSessionLogPaused(false)
        DnsBlockerVpnService.simulateDnsPipelineLookup("www.google.co.in")
        assertEquals(1, DnsBlockerVpnService.recentLogs.value.size)
        assertTrue(DnsBlockerVpnService.recentLogs.value.first().isSafeSearchRewritten)
        DnsBlockerVpnService.clearSessionLogs()
        assertTrue(DnsBlockerVpnService.recentLogs.value.isEmpty())
    }
}
