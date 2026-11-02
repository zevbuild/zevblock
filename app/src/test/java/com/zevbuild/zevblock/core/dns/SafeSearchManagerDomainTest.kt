package com.zevbuild.zevblock.core.dns

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafeSearchManagerDomainTest {
    private val manager = SafeSearchManager()

    @Test
    fun recognizesGoogleSearchDomains() {
        assertTrue(manager.isGoogleSearchDomain("google.com"))
        assertTrue(manager.isGoogleSearchDomain("www.google.com"))
        assertTrue(manager.isGoogleSearchDomain("google.co.uk"))
        assertTrue(manager.isGoogleSearchDomain("www.google.co.uk"))
        assertTrue(manager.isGoogleSearchDomain("google.ca"))
    }

    @Test
    fun rejectsUnrelatedDomainsWithGooglePrefix() {
        assertFalse(manager.isGoogleSearchDomain("google.example.org"))
        assertFalse(manager.isGoogleSearchDomain("google.com.evil.test"))
        assertFalse(manager.isGoogleSearchDomain("www.google.com.evil.test"))
        assertFalse(manager.isGoogleSearchDomain("googleapis.com"))
        assertFalse(manager.isGoogleSearchDomain("notgoogle.com"))
    }

    @Test
    fun acceptsTrailingDotAndMixedCase() {
        assertTrue(manager.isGoogleSearchDomain("WWW.Google.Co.UK."))
    }
}
