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
    fun recognizesInternationalGoogleDomains() {
        assertTrue(manager.isGoogleSearchDomain("google.co.in"))
        assertTrue(manager.isGoogleSearchDomain("www.google.co.in"))
        assertTrue(manager.isGoogleSearchDomain("google.com.au"))
        assertTrue(manager.isGoogleSearchDomain("www.google.com.br"))
        assertTrue(manager.isGoogleSearchDomain("google.de"))
        assertTrue(manager.isGoogleSearchDomain("google.fr"))
        assertTrue(manager.isGoogleSearchDomain("google.co.jp"))
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
    fun rejectsGoogleServiceSubdomains() {
        assertFalse(manager.isGoogleSearchDomain("apis.google.com"))
        assertFalse(manager.isGoogleSearchDomain("accounts.google.com"))
        assertFalse(manager.isGoogleSearchDomain("drive.google.com"))
        assertFalse(manager.isGoogleSearchDomain("mail.google.com"))
        assertFalse(manager.isGoogleSearchDomain("photos.google.com"))
        assertFalse(manager.isGoogleSearchDomain("docs.google.com"))
    }

    @Test
    fun rejectsMalformedAndEdgeCaseDomains() {
        assertFalse(manager.isGoogleSearchDomain(""))
        assertFalse(manager.isGoogleSearchDomain("."))
        assertFalse(manager.isGoogleSearchDomain("google"))
        assertFalse(manager.isGoogleSearchDomain("www"))
        assertFalse(manager.isGoogleSearchDomain("com"))
        assertFalse(manager.isGoogleSearchDomain("google.net"))
        assertFalse(manager.isGoogleSearchDomain("google.org"))
        assertFalse(manager.isGoogleSearchDomain("google..com"))
    }

    @Test
    fun acceptsTrailingDotAndMixedCase() {
        assertTrue(manager.isGoogleSearchDomain("WWW.Google.Co.UK."))
        assertTrue(manager.isGoogleSearchDomain("WWW.GOOGLE.COM."))
    }
}
