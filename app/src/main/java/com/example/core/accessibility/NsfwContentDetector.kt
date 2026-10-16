package com.example.core.accessibility

import java.net.URLDecoder

/**
 * NsfwContentDetector:
 * Analyzes browser address bar URLs, search queries, and window titles to identify
 * explicit, adult, or bypassing content.
 */
class NsfwContentDetector {

    // Keywords mapped to classifications
    private val nsfwKeywords = listOf(
        // Primary explicit terms
        "porn", "porno", "xxx", "sex", "nude", "naked", "erotic", "nsfw",
        "hentai", "camgirl", "chaturbate", "onlyfans", "redtube", "youporn",
        "xvideos", "xnxx", "xhamster", "brazzers", "playboy", "penthouse",
        "blowjob", "handjob", "masturbat", "orgasm", "gangbang", "fetish",
        "incest", "deepthroat", "boobs", "tits", "pussy", "vagina", "dick",
        "cock", "milf", "ebony", "threesome", "stripchat", "bongacams",
        "camsoda", "myfreecams", "livejasmin", "flirt4free",

        // Leetspeak & bypass variations
        "p0rn", "pr0n", "s3x", "nvd3", "b00bs", "c0ck", "d1ck", "xx",

        // Search bypass keywords
        "unblock porn", "bypass vpn", "porn proxy", "adult vpn", "watch porn free"
    )

    private val explicitTlds = listOf(
        ".xxx", ".adult", ".porn", ".sex", ".sexy", ".cam"
    )

    var isEnabled: Boolean = true

    /**
     * Inspects a candidate string (URL, domain, or search query text).
     */
    fun analyze(candidate: String): NsfwDetectionResult {
        if (!isEnabled || candidate.isBlank()) {
            return NsfwDetectionResult(isNsfw = false, matchedTerm = null, category = "None", original = candidate)
        }

        val decoded = tryDecode(candidate).lowercase().trim()

        // 1. Check Explicit TLDs
        for (tld in explicitTlds) {
            if (decoded.contains(tld)) {
                return NsfwDetectionResult(
                    isNsfw = true,
                    matchedTerm = tld,
                    category = "Explicit TLD",
                    original = candidate
                )
            }
        }

        // 2. Extract and inspect search queries (e.g. ?q=..., &query=...)
        val extractedQuery = extractSearchQuery(decoded)
        val textToInspect = extractedQuery ?: decoded

        // 3. Word boundary and substring matching
        for (keyword in nsfwKeywords) {
            if (containsWordOrPattern(textToInspect, keyword)) {
                return NsfwDetectionResult(
                    isNsfw = true,
                    matchedTerm = keyword,
                    category = if (extractedQuery != null) "Explicit Search Query" else "NSFW Keyword in URL",
                    original = candidate
                )
            }
        }

        return NsfwDetectionResult(isNsfw = false, matchedTerm = null, category = "Clean", original = candidate)
    }

    private fun containsWordOrPattern(text: String, keyword: String): Boolean {
        // Fast direct contains check
        if (!text.contains(keyword)) return false

        // For short 3-letter words like "xxx" or "sex", verify it's not a harmless substring like "essex" or "sussex"
        if (keyword.length <= 4) {
            val regex = Regex("""(?i)(^|[^a-z0-9])$keyword([^a-z0-9]|$)""")
            return regex.containsMatchIn(text)
        }

        return true
    }

    private fun extractSearchQuery(url: String): String? {
        val queryParamTriggers = listOf("?q=", "&q=", "?query=", "&query=", "?search_query=", "&search_query=", "?p=", "&p=")
        for (trigger in queryParamTriggers) {
            val idx = url.indexOf(trigger)
            if (idx != -1) {
                val start = idx + trigger.length
                val end = url.indexOf('&', start).let { if (it == -1) url.length else it }
                val rawValue = url.substring(start, end)
                return tryDecode(rawValue).replace('+', ' ')
            }
        }
        return null
    }

    private fun tryDecode(text: String): String {
        return try {
            URLDecoder.decode(text, "UTF-8")
        } catch (e: Exception) {
            text
        }
    }
}

data class NsfwDetectionResult(
    val isNsfw: Boolean,
    val matchedTerm: String?,
    val category: String,
    val original: String
)
