package com.example.core.dns

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance in-memory domain blocklist matcher.
 * Efficiently parses and indexes standard StevenBlack hosts files,
 * adblock rules, and provides O(1) exact and hierarchical subdomain matching.
 */
class DomainBlocklistMatcher {

    private val blockedDomains = HashSet<String>(100_000)
    private val allowedDomains = HashSet<String>()
    private val customRules = HashSet<String>()

    // Synchronize access during list updates
    private val lock = Any()

    init {
        // Pre-populate with initial foundational seeds
        loadDefaultSeedRules()
    }

    /**
     * Checks if a domain or any of its parent domain suffixes is blocked.
     * Hierarchy matching ensures that blocking "tracker.com" automatically blocks "ads.us.tracker.com".
     */
    fun isBlocked(rawDomain: String): BlockResult {
        val domain = rawDomain.lowercase().trimEnd('.')

        // 1. Hierarchical Blocklist Suffix Match against HARDCODED Adult Seed / StevenBlack rules
        // Hardcoded adult blocks are IMMUTABLE and can NEVER be bypassed, whitelisted, or removed!
        var current = domain
        while (current.isNotEmpty()) {
            val matched = synchronized(lock) { blockedDomains.contains(current) }
            if (matched) {
                return BlockResult(isBlocked = true, matchedRule = current, reason = "Adult Shield Blocklist")
            }
            val dotIndex = current.indexOf('.')
            if (dotIndex == -1) break
            current = current.substring(dotIndex + 1)
        }

        // 2. Check Custom User Rules (e.g. custom triggers, gambling, distracting domains)
        if (isDomainInSet(domain, customRules)) {
            return BlockResult(isBlocked = true, matchedRule = domain, reason = "Custom User Blocklist")
        }

        // 3. Check Allowlist (Only applicable to non-adult, non-custom domains)
        if (isDomainInSet(domain, allowedDomains)) {
            return BlockResult(isBlocked = false, matchedRule = null, reason = "Explicitly allowed")
        }

        return BlockResult(isBlocked = false, matchedRule = null, reason = "Allowed")
    }

    private fun isDomainInSet(domain: String, set: Set<String>): Boolean {
        var current = domain
        while (current.isNotEmpty()) {
            if (set.contains(current)) return true
            val dotIndex = current.indexOf('.')
            if (dotIndex == -1) break
            current = current.substring(dotIndex + 1)
        }
        return false
    }

    /**
     * Streams and indexes a StevenBlack hosts format file or string.
     * Line-by-line streaming avoids high memory spikes during parsing.
     */
    fun loadFromReader(reader: BufferedReader): Int {
        var count = 0
        val localSet = ArrayList<String>(10_000)

        reader.useLines { lines ->
            for (rawLine in lines) {
                val cleanLine = rawLine.trim()
                if (cleanLine.isEmpty() || cleanLine.startsWith("#")) continue

                // Strip inline comment
                val noComment = cleanLine.substringBefore('#').trim()
                val parts = noComment.split(Regex("\\s+"))

                val candidate = when {
                    parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") -> parts[1]
                    parts.size == 1 && !parts[0].contains(":") -> parts[0]
                    else -> null
                }

                if (candidate != null && isValidDomain(candidate)) {
                    val normalized = candidate.lowercase().trimEnd('.')
                    localSet.add(normalized)
                    count++
                }
            }
        }

        synchronized(lock) {
            blockedDomains.addAll(localSet)
        }

        Log.i(TAG, "Loaded $count rules from StevenBlack hosts. Total rules in memory: ${blockedDomains.size}")
        return count
    }

    fun loadFromInputStream(inputStream: InputStream): Int {
        return loadFromReader(BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)))
    }

    fun addCustomBlockedDomain(domain: String) {
        val clean = domain.lowercase().trimEnd('.')
        if (isValidDomain(clean)) {
            synchronized(lock) {
                customRules.add(clean)
            }
        }
    }

    fun setCustomBlockedDomains(domains: Collection<String>) {
        synchronized(lock) {
            customRules.clear()
            for (d in domains) {
                val clean = d.lowercase().trimEnd('.')
                if (isValidDomain(clean)) {
                    customRules.add(clean)
                }
            }
        }
    }

    fun getCustomBlockedDomains(): Set<String> {
        return synchronized(lock) { customRules.toSet() }
    }

    fun isHardcodedAdultDomain(rawDomain: String): Boolean {
        val domain = rawDomain.lowercase().trimEnd('.')
        var current = domain
        while (current.isNotEmpty()) {
            val matched = synchronized(lock) { blockedDomains.contains(current) }
            if (matched) return true
            val dotIndex = current.indexOf('.')
            if (dotIndex == -1) break
            current = current.substring(dotIndex + 1)
        }
        return false
    }

    fun addAllowedDomain(domain: String) {
        val clean = domain.lowercase().trimEnd('.')
        if (isValidDomain(clean) && !isHardcodedAdultDomain(clean)) {
            allowedDomains.add(clean)
        }
    }

    fun removeCustomRule(domain: String) {
        val clean = domain.lowercase().trimEnd('.')
        synchronized(lock) {
            customRules.remove(clean)
        }
        allowedDomains.remove(clean)
    }

    fun clearBlocklist() {
        synchronized(lock) {
            blockedDomains.clear()
        }
    }

    fun getRuleCount(): Int {
        return synchronized(lock) { blockedDomains.size + customRules.size }
    }

    private fun isValidDomain(domain: String): Boolean {
        if (domain.isEmpty() || domain.length > 253) return false
        if (domain == "localhost" || domain == "localhost.localdomain" || domain == "broadcasthost" || domain == "0.0.0.0") return false
        return domain.contains('.') && !domain.startsWith(".") && !domain.endsWith(".")
    }

    /**
     * Seeds essential telemetries, ad-networks, telemetry scrapers, and malware domains
     * to ensure immediate protection upon launch.
     */
    private fun loadDefaultSeedRules() {
        val seeds = listOf(
            // Telemetry & Spyware Trackers
            "telemetry.sdk.applovin.com",
            "app-measurement.com",
            "firebase-settings.crashlytics.com",
            "telemetry.branch.io",
            "graph.facebook.com",
            "b-graph.facebook.com",
            "pixel.facebook.com",
            "an.facebook.com",
            "events.redditmedia.com",
            "telemetry.sdk.inmobi.com",
            "log.byteoversea.com",
            "mon.musical.ly",

            // Ad Networks & Trackers
            "doubleclick.net",
            "googleads.g.doubleclick.net",
            "pagead2.googlesyndication.com",
            "adservice.google.com",
            "adnxs.com",
            "criteo.com",
            "outbrain.com",
            "taboola.com",
            "popads.net",
            "propellerads.com",
            "adroll.com",
            "scorecardresearch.com",
            "rubiconproject.com",
            "casalemedia.com",
            "moatads.com",
            "chartbeat.net",
            "quantserve.com",
            "adcolony.com",
            "vungle.com",
            "unityads.unity3d.com",
            "chartboost.com",
            "flurry.com",

            // Common Malicious & Phishing Seeds
            "malware-traffic-analysis.net",
            "coinhive.com",
            "coin-hive.com",
            "miner.pr0gramm.com",
            "jsecoin.com",
            "webmine.pro",

            // Adult Content Delivery Networks (CDNs) - Blocking these stops streaming video & images
            "phncdn.com",
            "ypncdn.com",
            "xhcdn.com",
            "rncdn3.com",
            "rncdn4.com",
            "rncdn5.com",
            "bcvcdn.com",
            "trafficjunky.com",
            "trafficjunky.net",
            "exosrv.com",
            "ero-advertising.com",
            "juicyads.com",
            "streamtape.com",
            "doodstream.com",
            "voe.sx",

            // Major Global Adult Tube & Streaming Sites
            "pornhub.com",
            "pornhubpremium.com",
            "xvideos.com",
            "xvideos2.com",
            "xvideos3.com",
            "xnxx.com",
            "xnxx2.com",
            "xhamster.com",
            "xhamster.desi",
            "xhamster2.com",
            "xhamster3.com",
            "redtube.com",
            "youporn.com",
            "tube8.com",
            "brazzers.com",
            "spankbang.com",
            "eporner.com",
            "beeg.com",
            "hqporner.com",
            "daftsex.com",
            "porntrex.com",
            "tnaflix.com",
            "txxx.com",
            "heavy-r.com",
            "motherless.com",
            "empflix.com",
            "nuvid.com",
            "youjizz.com",
            "pornone.com",
            "gotporn.com",
            "porn300.com",
            "vporn.com",
            "sunporno.com",
            "anyporn.com",
            "lobstertube.com",
            "pornhat.com",
            "tubepornclassic.com",
            "porngo.com",
            "thumbzilla.com",
            "tubeplus.me",
            "upornia.com",
            "czechav.com",
            "porn555.com",
            "xfreehd.com",
            "pornbox.com",
            "netporn.com",
            "severeporn.com",

            // Webcam, Live Streaming & Escort Networks
            "chaturbate.com",
            "stripchat.com",
            "bongacams.com",
            "livejasmin.com",
            "cam4.com",
            "camsoda.com",
            "myfreecams.com",
            "flirt4free.com",
            "streamate.com",
            "imlive.com",
            "camwhores.tv",
            "camwhorespy.com",
            "faphouse.com",
            "jerkmate.com",
            "naked.com",
            "slutload.com",
            "babestation.tv",
            "jasmin.com",
            "cams.com",
            "webcamromance.com",

            // Subscription, Fan Sites & Leak Aggregators
            "onlyfans.com",
            "fansly.com",
            "coomer.party",
            "coomer.su",
            "kemono.party",
            "kemono.su",
            "thothub.to",
            "thothub.org",
            "erome.com",
            "fapello.com",
            "simpcity.su",
            "bunkr.is",
            "bunkrr.su",
            "cyberdrop.me",
            "leakgirls.com",
            "nude-gals.com",
            "voyeurweb.com",

            // Hentai, Anime & Comic Adult Portals
            "nhentai.net",
            "hanime.tv",
            "hentaihaven.xxx",
            "hentaihaven.red",
            "tsumino.com",
            "hitomi.la",
            "pururin.io",
            "e-hentai.org",
            "exhentai.org",
            "asmhentai.com",
            "simply-hentai.com",
            "multporn.net",
            "hentai2read.com",
            "rule34.xxx",
            "rule34.paheal.net",
            "e621.net",
            "gelbooru.com",
            "danbooru.donmai.us",
            "yande.re",
            "konachan.com",
            "luscious.net",
            "xbooru.com",

            // Adult Dating, Hookup & Classifieds
            "adultfriendfinder.com",
            "fetlife.com",
            "ashleymadison.com",
            "alt.com",
            "casualx.com",
            "fling.com",
            "passion.com",
            "benaughty.com",
            "milffinder.com",
            "snapsext.com",
            "bedegraine.com",
            "hookup.com",
            "pure.app",
            "skipthegames.com",
            "listcrawler.eu",
            "escortalligator.com",
            "bedpage.com"
        )

        synchronized(lock) {
            blockedDomains.addAll(seeds)
        }
        Log.i(TAG, "Initialized default seed blocklist with ${seeds.size} baseline rules.")
    }

    companion object {
        private const val TAG = "DomainBlocklist"
    }
}

data class BlockResult(
    val isBlocked: Boolean,
    val matchedRule: String?,
    val reason: String
)
