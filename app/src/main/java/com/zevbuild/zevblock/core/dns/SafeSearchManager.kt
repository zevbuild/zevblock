package com.zevbuild.zevblock.core.dns

import android.util.Log

/**
 * SafeSearch VIP IP Rewriter:
 * Intercepts DNS queries targeting major search engines and forces their resolution
 * to strict SafeSearch VIP addresses at the network level.
 */
class SafeSearchManager {

    companion object {
        private const val TAG = "SafeSearchManager"

        // Google SafeSearch VIP
        const val GOOGLE_SAFESEARCH_IPV4 = "216.239.38.120" // forcesafesearch.google.com
        const val GOOGLE_SAFESEARCH_IPV6 = "2001:4860:4806::78"

        // YouTube Restricted Mode VIP
        const val YOUTUBE_RESTRICTED_STRICT_IPV4 = "216.239.38.119" // restrict.youtube.com
        const val YOUTUBE_RESTRICTED_MODERATE_IPV4 = "216.239.38.120"
        const val YOUTUBE_RESTRICTED_IPV6 = "2001:4860:4806::77"

        // Bing Strict SafeSearch VIP
        const val BING_STRICT_IPV4 = "204.79.197.220" // strict.bing.com
        const val BING_STRICT_IPV6 = "2620:1ec:c11::200"

        // DuckDuckGo SafeSearch VIP
        const val DUCKDUCKGO_SAFE_CNAME = "safe.duckduckgo.com"
        const val DUCKDUCKGO_SAFE_IPV4 = "52.142.124.215"

        // Yahoo SafeSearch VIP
        const val YAHOO_SAFE_CNAME = "safe.search.yahoo.com"

        // Brave SafeSearch
        const val BRAVE_SAFE_CNAME = "safe.brave.com"

        // Qwant SafeSearch
        const val QWANT_SAFE_CNAME = "safe.qwant.com"
    }

    var isEnabled: Boolean = true

    /**
     * Checks if the incoming query domain targets a search engine with SafeSearch support.
     * If matched, returns a synthesized DNS response; otherwise returns null.
     */
    fun rewriteDns(query: ParsedDnsQuery): ByteArray? {
        if (!isEnabled) return null

        val domain = query.question.name.lowercase().trimEnd('.')
        val qType = query.question.qType

        // 1. Google SafeSearch VIP
        if (isGoogleSearchDomain(domain)) {
            Log.i(TAG, "SafeSearch Intercept: $domain -> Google VIP ($GOOGLE_SAFESEARCH_IPV4)")
            return when (qType) {
                DnsRecordType.A -> DnsPacketBuilder.buildAResponse(query, GOOGLE_SAFESEARCH_IPV4)
                DnsRecordType.AAAA -> DnsPacketBuilder.buildAaaaResponse(query, GOOGLE_SAFESEARCH_IPV6)
                DnsRecordType.CNAME -> DnsPacketBuilder.buildCnameResponse(query, "forcesafesearch.google.com")
                else -> null
            }
        }

        // 2. YouTube Restricted Mode
        if (isYouTubeDomain(domain)) {
            Log.i(TAG, "SafeSearch Intercept: $domain -> YouTube Restricted VIP ($YOUTUBE_RESTRICTED_STRICT_IPV4)")
            return when (qType) {
                DnsRecordType.A -> DnsPacketBuilder.buildAResponse(query, YOUTUBE_RESTRICTED_STRICT_IPV4)
                DnsRecordType.AAAA -> DnsPacketBuilder.buildAaaaResponse(query, YOUTUBE_RESTRICTED_IPV6)
                DnsRecordType.CNAME -> DnsPacketBuilder.buildCnameResponse(query, "restrict.youtube.com")
                else -> null
            }
        }

        // 3. Bing Strict SafeSearch
        if (isBingDomain(domain)) {
            Log.i(TAG, "SafeSearch Intercept: $domain -> Bing Strict VIP ($BING_STRICT_IPV4)")
            return when (qType) {
                DnsRecordType.A -> DnsPacketBuilder.buildAResponse(query, BING_STRICT_IPV4)
                DnsRecordType.AAAA -> DnsPacketBuilder.buildAaaaResponse(query, BING_STRICT_IPV6)
                DnsRecordType.CNAME -> DnsPacketBuilder.buildCnameResponse(query, "strict.bing.com")
                else -> null
            }
        }

        // 4. DuckDuckGo SafeSearch
        if (domain == "duckduckgo.com" || domain == "www.duckduckgo.com") {
            Log.i(TAG, "SafeSearch Intercept: $domain -> DuckDuckGo Safe ($DUCKDUCKGO_SAFE_CNAME)")
            return when (qType) {
                DnsRecordType.A -> DnsPacketBuilder.buildAResponse(query, DUCKDUCKGO_SAFE_IPV4)
                DnsRecordType.CNAME -> DnsPacketBuilder.buildCnameResponse(query, DUCKDUCKGO_SAFE_CNAME)
                else -> null
            }
        }

        // 5. Yahoo SafeSearch
        if (isYahooSearchDomain(domain)) {
            Log.i(TAG, "SafeSearch Intercept: $domain -> Yahoo SafeSearch ($YAHOO_SAFE_CNAME)")
            return when (qType) {
                DnsRecordType.CNAME -> DnsPacketBuilder.buildCnameResponse(query, YAHOO_SAFE_CNAME)
                DnsRecordType.A -> DnsPacketBuilder.buildCnameResponse(query, YAHOO_SAFE_CNAME)
                else -> null
            }
        }

        // 6. Brave SafeSearch
        if (domain == "search.brave.com") {
            Log.i(TAG, "SafeSearch Intercept: $domain -> Brave Safe ($BRAVE_SAFE_CNAME)")
            return when (qType) {
                DnsRecordType.CNAME, DnsRecordType.A -> DnsPacketBuilder.buildCnameResponse(query, BRAVE_SAFE_CNAME)
                else -> null
            }
        }

        // 7. Qwant SafeSearch
        if (domain == "qwant.com" || domain == "www.qwant.com") {
            Log.i(TAG, "SafeSearch Intercept: $domain -> Qwant Safe ($QWANT_SAFE_CNAME)")
            return when (qType) {
                DnsRecordType.CNAME, DnsRecordType.A -> DnsPacketBuilder.buildCnameResponse(query, QWANT_SAFE_CNAME)
                else -> null
            }
        }

        return null
    }

    /**
     * Determines whether the domain is an active Google search domain (e.g. www.google.com, google.ca, etc.)
     */
    fun isGoogleSearchDomain(domain: String): Boolean {
        if (domain == "google.com" || domain == "www.google.com") return true

        // Fast regex or prefix/suffix check for international Google ccTLDs
        if (domain.startsWith("google.") || domain.startsWith("www.google.")) {
            // Exclude non-search services like googleapis, googletagmanager, googleusercontent
            if (!domain.contains("apis") && !domain.contains("content") && !domain.contains("tag")) {
                return true
            }
        }
        return false
    }

    fun isYouTubeDomain(domain: String): Boolean {
        return domain == "youtube.com" ||
                domain == "www.youtube.com" ||
                domain == "m.youtube.com" ||
                domain == "youtubei.googleapis.com" ||
                domain == "youtube-nocookie.com"
    }

    fun isBingDomain(domain: String): Boolean {
        return domain == "bing.com" ||
                domain == "www.bing.com" ||
                domain == "cn.bing.com"
    }

    fun isYahooSearchDomain(domain: String): Boolean {
        return domain == "search.yahoo.com" ||
                domain == "search.yahoo.co.jp" ||
                domain == "us.search.yahoo.com"
    }
}
