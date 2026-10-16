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
        // Primary explicit terms & acts
        "porn", "porno", "xxx", "sex", "nude", "naked", "erotic", "nsfw",
        "hentai", "camgirl", "chaturbate", "onlyfans", "fansly", "redtube", "youporn",
        "xvideos", "xnxx", "xhamster", "brazzers", "playboy", "penthouse",
        "blowjob", "handjob", "masturbat", "orgasm", "gangbang", "fetish",
        "incest", "deepthroat", "boobs", "tits", "pussy", "vagina", "dick",
        "cock", "milf", "ebony", "threesome", "stripchat", "bongacams",
        "camsoda", "myfreecams", "livejasmin", "flirt4free", "spankbang",
        "eporner", "beeg", "hqporner", "tnaflix", "thumbzilla",
        "creampie", "bukkake", "cumshot", "squirt", "facial", "anal",
        "bdsm", "bondage", "shemale", "transsexual", "ladyboy",
        "rule34", "e621", "gelbooru", "danbooru", "nhentai", "hanime",
        "erome", "fapello", "thothub", "coomer", "kemono",
        "dildo", "vibrator", "fleshlight", "hardcore", "softcore",
        "voyeur", "peeping", "upskirt", "cuckold", "interracial",
        "stepmom", "stepdaughter", "stepsister", "stepbrother",

        // Multilingual & Global Variations (Spanish, Portuguese, French, German)
        "sexo", "puta", "fodendo", "chupada", "erotik", "fetiche", "novinha",
        "safada", "fellation", "salope", "jouissance", "sexe",

        // Subreddit NSFW triggers
        "r/nsfw", "r/gonewild", "r/porn", "r/realgirls", "r/rule34",
        "r/hentai", "r/holdthemoan", "r/breeding", "r/cumsluts", "r/milf",

        // Leetspeak & bypass variations
        "p0rn", "pr0n", "s3x", "nvd3", "b00bs", "c0ck", "d1ck",

        // Search bypass keywords
        "unblock porn", "bypass vpn", "porn proxy", "adult vpn", "watch porn free",
        "free porn", "sex video", "nude pics", "leaked nudes"
    )

    private val explicitTlds = listOf(
        ".xxx", ".adult", ".porn", ".sex", ".sexy", ".cam", ".tube"
    )

    private val explicitPathSegments = listOf(
        "/porn/", "/xxx/", "/hentai/", "/nsfw/", "/adult/", "/erotic/", "/cams/", "/webcam/"
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

        // 2. Check Explicit URL Path Segments
        for (path in explicitPathSegments) {
            if (decoded.contains(path)) {
                return NsfwDetectionResult(
                    isNsfw = true,
                    matchedTerm = path,
                    category = "Explicit URL Path",
                    original = candidate
                )
            }
        }

        // 3. Extract and inspect search queries (e.g. ?q=..., &query=...)
        val extractedQuery = extractSearchQuery(decoded)
        val textToInspect = extractedQuery ?: decoded

        // 4. Word boundary and pattern matching
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
        // Fast direct check
        if (!text.contains(keyword)) return false

        // For words with length <= 5 or short terms (e.g. "anal", "dick", "sex", "xxx", "cum"),
        // enforce word boundaries so harmless words like "analysis", "canal", "essex", "document" are not falsely flagged.
        if (keyword.length <= 5 && !keyword.startsWith("r/")) {
            val regex = Regex("""(?i)(^|[^a-z0-9])${Regex.escape(keyword)}([^a-z0-9]|$)""")
            return regex.containsMatchIn(text)
        }

        return true
    }

    private fun extractSearchQuery(url: String): String? {
        val queryParamTriggers = listOf(
            "?q=", "&q=",
            "?query=", "&query=",
            "?search_query=", "&search_query=",
            "?search=", "&search=",
            "?k=", "&k=",
            "?keyword=", "&keyword=",
            "?p=", "&p=",
            "?term=", "&term=",
            "?w=", "&w=",
            "?keywords=", "&keywords="
        )
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
