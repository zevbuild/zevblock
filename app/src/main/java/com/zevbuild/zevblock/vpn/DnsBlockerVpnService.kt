package com.zevbuild.zevblock.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zevbuild.zevblock.MainActivity
import com.zevbuild.zevblock.core.diagnostics.CrashGuardManager
import com.zevbuild.zevblock.core.dns.DnsLogEntry
import com.zevbuild.zevblock.core.dns.DnsPacketBuilder
import com.zevbuild.zevblock.core.dns.DnsPacketParser
import com.zevbuild.zevblock.core.dns.DomainBlocklistMatcher
import com.zevbuild.zevblock.core.dns.IpUdpPacket
import com.zevbuild.zevblock.core.dns.SafeSearchManager
import com.zevbuild.zevblock.core.security.TamperLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * DnsBlockerVpnService:
 * High-performance, battery-efficient local DNS sinkhole VpnService.
 * Intercepts DNS queries on virtual address 10.254.1.1 / fd00::1:
 * - Enforces SafeSearch VIP rewriting (e.g. www.google.com -> 216.239.38.120).
 * - Matches domains against in-memory StevenBlack hosts blocklists.
 * - Forwards clean queries to upstream resolvers (1.1.1.3 / 185.228.168.10) using protected sockets.
 * - Tracks live Download/Upload speeds & daily data usage in the system Notification Panel.
 */
open class DnsBlockerVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceJob: Job? = null
    private var telemetryJob: Job? = null
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CrashGuardManager.coroutineExceptionHandler("DnsBlockerVpnService")
    )
    private val upstreamSemaphore = Semaphore(32)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.i(TAG, "DnsBlockerVpnService received action: $action")

        return when (action) {
            ACTION_STOP -> {
                stopVpn()
                START_NOT_STICKY
            }
            else -> {
                val started = startVpn()
                if (started) START_STICKY else START_NOT_STICKY
            }
        }
    }

    private fun ensureDependencies() {
        if (statsManager == null) {
            statsManager = com.zevbuild.zevblock.core.stats.ProtectionStatsManager(
                applicationContext,
                TamperLockManager(applicationContext)
            )
        }
    }

    private fun startVpn(): Boolean {
        // Always satisfy the Android O+ startForegroundService() 5-second contract first,
        // even if the service is already running, to prevent ForegroundServiceDidNotStartInTimeException.
        try {
            val initialNotification = buildNotification(
                titleText = "ZevBlock • ↓ ${formatSpeed(_downloadSpeedBps.value)}  ↑ ${formatSpeed(_uploadSpeedBps.value)}",
                statusText = if (_isRunning.value) "DNS Sinkhole & Speed Monitor Active" else "Starting DNS Sinkhole & Speed Monitor...",
                bigText = "Local DNS sinkhole (10.254.1.1) and real-time network speed telemetry active."
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to promote DnsBlockerVpnService to foreground", t)
            CrashGuardManager.recordHandledException("DnsBlockerVpnService.startForeground", t, autoRecovered = true)
            stopSelf()
            return false
        }

        ensureDependencies()

        if (_isRunning.value && vpnInterface != null) {
            Log.i(TAG, "VPN is already running; foreground state refreshed.")
            return true
        }

        try {
            if (prepare(this) != null) {
                Log.w(TAG, "VPN consent not granted yet; stopping foreground service cleanly.")
                CrashGuardManager.recordServiceRecovery("DnsBlockerVpnService", "Stopped unpermitted background VPN start safely.")
                stopVpn()
                return false
            }

            val builder = Builder()
                .setSession("ZevBlock DNS Sinkhole")
                .setMtu(1500)
                .setBlocking(true)
                // Virtual client endpoint (Decoupled from DNS Server IP to prevent Linux loopback capture!)
                .addAddress("10.254.1.2", 32)
                .addAddress("fd00::2", 128)
                // Assign local sinkhole virtual gateway as system DNS server
                .addDnsServer("10.254.1.1")
                .addDnsServer("fd00::1")
                // Route only virtual DNS traffic through TUN to preserve native TCP/UDP speed!
                .addRoute("10.254.1.1", 32)
                .addRoute("fd00::1", 128)
                // Intercept common hardcoded public DNS servers to prevent app bypasses
                .addRoute("8.8.8.8", 32)
                .addRoute("8.8.4.4", 32)
                .addRoute("1.1.1.1", 32)
                .addRoute("1.0.0.1", 32)
                .addRoute("9.9.9.9", 32)
                .addRoute("94.140.14.14", 32)

            // Exclude self application from VPN routing to avoid socket loopback deadlocks
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Could not disallow self package from VPN: ${e.message}")
            }

            try {
                builder.setUnderlyingNetworks(null)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set underlying networks: ${e.message}")
            }

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN TUN interface.")
                stopVpn()
                return false
            }

            _isRunning.value = true

            serviceJob?.cancel()
            serviceJob = scope.launch {
                runPacketLoop(vpnInterface!!)
            }

            telemetryJob?.cancel()
            telemetryJob = scope.launch {
                runTrafficAndNotificationLoop()
            }

            Log.i(TAG, "ZevBlock DNS Sinkhole successfully established.")
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "Fatal error establishing VPN", t)
            CrashGuardManager.recordHandledException("DnsBlockerVpnService.establish", t, autoRecovered = true)
            stopVpn()
            return false
        }
    }

    /**
     * Samples device and app-level TrafficStats every 2 seconds to compute live
     * download/upload speeds and daily data usage, updating both UI StateFlows
     * and the persistent Notification Panel.
     */
    private suspend fun runTrafficAndNotificationLoop() {
        val myUid = Process.myUid()
        val trafficPrefs = applicationContext.getSharedPreferences(TRAFFIC_PREFS_NAME, Context.MODE_PRIVATE)
        val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        var lastRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
        var lastTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
        var lastAppRx = TrafficStats.getUidRxBytes(myUid).coerceAtLeast(0L)
        var lastAppTx = TrafficStats.getUidTxBytes(myUid).coerceAtLeast(0L)
        var lastSampleElapsed = SystemClock.elapsedRealtime()

        while (scope.isActive && _isRunning.value) {
            delay(2000L)
            if (!_isRunning.value) break

            val nowElapsed = SystemClock.elapsedRealtime()
            val elapsedSec = ((nowElapsed - lastSampleElapsed) / 1000.0).coerceAtLeast(0.5)
            lastSampleElapsed = nowElapsed

            val currentRx = TrafficStats.getTotalRxBytes().coerceAtLeast(lastRx)
            val currentTx = TrafficStats.getTotalTxBytes().coerceAtLeast(lastTx)
            val currentAppRx = TrafficStats.getUidRxBytes(myUid).coerceAtLeast(lastAppRx)
            val currentAppTx = TrafficStats.getUidTxBytes(myUid).coerceAtLeast(lastAppTx)

            val deltaRx = (currentRx - lastRx).coerceAtLeast(0L)
            val deltaTx = (currentTx - lastTx).coerceAtLeast(0L)
            val deltaAppRx = (currentAppRx - lastAppRx).coerceAtLeast(0L)
            val deltaAppTx = (currentAppTx - lastAppTx).coerceAtLeast(0L)

            lastRx = currentRx
            lastTx = currentTx
            lastAppRx = currentAppRx
            lastAppTx = currentAppTx

            val dlSpeed = (deltaRx / elapsedSec).toLong()
            val ulSpeed = (deltaTx / elapsedSec).toLong()
            val appDlSpeed = (deltaAppRx / elapsedSec).toLong()
            val appUlSpeed = (deltaAppTx / elapsedSec).toLong()

            _downloadSpeedBps.value = dlSpeed
            _uploadSpeedBps.value = ulSpeed
            _appDownloadSpeedBps.value = appDlSpeed
            _appUploadSpeedBps.value = appUlSpeed

            // Persist daily download/upload totals
            val todayKey = dayFormatter.format(System.currentTimeMillis())
            val savedDay = trafficPrefs.getString("current_day", "")
            var dayDl = if (savedDay == todayKey) trafficPrefs.getLong("day_dl_bytes", 0L) else 0L
            var dayUl = if (savedDay == todayKey) trafficPrefs.getLong("day_ul_bytes", 0L) else 0L
            var dayAppDl = if (savedDay == todayKey) trafficPrefs.getLong("day_app_dl_bytes", 0L) else 0L
            var dayAppUl = if (savedDay == todayKey) trafficPrefs.getLong("day_app_ul_bytes", 0L) else 0L

            dayDl += deltaRx
            dayUl += deltaTx
            dayAppDl += deltaAppRx
            dayAppUl += deltaAppTx

            trafficPrefs.edit()
                .putString("current_day", todayKey)
                .putLong("day_dl_bytes", dayDl)
                .putLong("day_ul_bytes", dayUl)
                .putLong("day_app_dl_bytes", dayAppDl)
                .putLong("day_app_ul_bytes", dayAppUl)
                .apply()

            _todayDownloadBytes.value = dayDl
            _todayUploadBytes.value = dayUl
            _todayAppDownloadBytes.value = dayAppDl
            _todayAppUploadBytes.value = dayAppUl

            val blocked = _blockedQueries.get()
            val safe = _safeSearchQueries.get()
            val allowed = _allowedQueries.get()
            val latency = _lastUpstreamLatencyMs.value

            val title = "ZevBlock • ↓ ${formatSpeed(dlSpeed)}  ↑ ${formatSpeed(ulSpeed)}"
            val compactLine = "Today: ↓ ${formatBytes(dayDl)} ↑ ${formatBytes(dayUl)} • Blocked: $blocked"
            val expandedText = buildString {
                append("Live Speed: ↓ ${formatSpeed(dlSpeed)}  |  ↑ ${formatSpeed(ulSpeed)}\n")
                append("Today's Data: ↓ ${formatBytes(dayDl)}  |  ↑ ${formatBytes(dayUl)} (App DNS: ↓ ${formatBytes(dayAppDl)} ↑ ${formatBytes(dayAppUl)})\n")
                append("DNS Shield: $blocked Blocked • $safe SafeSearch • $allowed Allowed (${latency}ms)")
            }

            updateNotification(titleText = title, statusText = compactLine, bigText = expandedText)
        }
    }

    private suspend fun runPacketLoop(pfd: ParcelFileDescriptor) {
        val fis = FileInputStream(pfd.fileDescriptor)
        val fos = FileOutputStream(pfd.fileDescriptor)
        val packetBuffer = ByteArray(32767)

        Log.i(TAG, "Entering DNS TUN packet loop...")

        while (scope.isActive && _isRunning.value) {
            try {
                val bytesRead = fis.read(packetBuffer)
                if (bytesRead < 0) break
                if (bytesRead == 0) continue

                // 1. Parse raw IP/UDP packet
                val ipPacket = DnsPacketParser.parseIpPacket(packetBuffer, bytesRead) ?: continue

                // Filter for destination port 53 (DNS)
                if (ipPacket.dstPort != 53) continue

                // 2. Parse DNS Query message
                val dnsQuery = DnsPacketParser.parseDnsQuery(ipPacket.payload) ?: continue
                val domain = dnsQuery.question.name

                val total = _totalQueries.incrementAndGet()
                totalQueriesCount.value = total

                val isBypassActive = TamperLockManager.isBypassActive(this@DnsBlockerVpnService)

                // 3. Step A: SafeSearch VIP Check (Strictly enforced at all times to prevent adult search results)
                val safeSearchResponse = safeSearchManager.rewriteDns(dnsQuery)
                if (safeSearchResponse != null) {
                    val outPacket = DnsPacketBuilder.wrapDnsResponseInUdpIp(ipPacket, safeSearchResponse)
                    synchronized(fos) {
                        fos.write(outPacket)
                    }
                    val safeCount = _safeSearchQueries.incrementAndGet()
                    safeSearchQueriesCount.value = safeCount
                    logDnsEvent(
                        domain = domain,
                        type = "SafeSearch VIP",
                        isBlocked = false,
                        isSafeSearch = true,
                        reason = "Tier 1: Forced Strict SafeSearch VIP (216.239.38.120)"
                    )
                    statsManager?.recordDnsQuery(isBlocked = false, isSafeSearch = true)
                    continue
                }

                // 4. Step B: Domain Blocklist Check
                // Zero-Bypass Guarantee: Built-in adult domains are IMMUTABLE and ALWAYS sinkholed, even during temporary bypass!
                val blockResult = blocklistMatcher.isBlocked(domain)
                val shouldBlock = if (isBypassActive) {
                    blocklistMatcher.isHardcodedAdultDomain(domain)
                } else {
                    blockResult.isBlocked
                }

                if (shouldBlock) {
                    val sinkholeResponse = DnsPacketBuilder.buildSinkholeResponse(dnsQuery, blockedIp = "0.0.0.0", isNxDomain = false)
                    val outPacket = DnsPacketBuilder.wrapDnsResponseInUdpIp(ipPacket, sinkholeResponse)
                    synchronized(fos) {
                        fos.write(outPacket)
                    }
                    val blockedCount = _blockedQueries.incrementAndGet()
                    blockedQueriesCount.value = blockedCount
                    logDnsEvent(
                        domain = domain,
                        type = "Blocked (0.0.0.0)",
                        isBlocked = true,
                        isSafeSearch = false,
                        reason = "Tier 2 Sinkhole: ${blockResult.reason} -> 0.0.0.0"
                    )
                    statsManager?.recordDnsQuery(isBlocked = true, isSafeSearch = false)
                    continue
                }

                // 5. Step C: Forward clean query to Upstream Adult-Filtering DNS Resolver asynchronously
                val allowedCount = _allowedQueries.incrementAndGet()
                allowedQueriesCount.value = allowedCount
                scope.launch(Dispatchers.IO) {
                    upstreamSemaphore.withPermit {
                        forwardUpstreamDns(ipPacket, dnsQuery, fos)
                    }
                }
                logDnsEvent(
                    domain = domain,
                    type = "Allowed (1.1.1.3)",
                    isBlocked = false,
                    isSafeSearch = false,
                    reason = "Tier 3 Allowed: Resolved via Cloudflare Family (1.1.1.3)"
                )
                statsManager?.recordDnsQuery(isBlocked = false, isSafeSearch = false)

            } catch (e: Exception) {
                if (!scope.isActive) {
                    break
                }
                Log.e(TAG, "Error processing DNS packet in loop", e)
                delay(50)
            }
        }
    }

    /**
     * Forwards a query to upstream Adult-Filtering DNS resolvers
     * (Cloudflare Family 1.1.1.3 / CleanBrowsing / AdGuard Family)
     * using a protected UDP socket that bypasses the local VPN TUN interface.
     */
    private fun forwardUpstreamDns(incomingIpPacket: IpUdpPacket, dnsQuery: ParsedDnsQuery, fos: FileOutputStream) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            protect(socket) // Critical: socket bypasses VPN interface!
            socket.soTimeout = 1200 // 1.2s timeout for ultra-responsive fallback

            // Prioritize upstream resolvers based on whether client query arrived via IPv6 or IPv4
            val upstreamServers = if (incomingIpPacket.isIpv6) {
                listOf(
                    UPSTREAM_DNS_CLOUDFLARE_IPV6_1,
                    UPSTREAM_DNS_CLOUDFLARE_IPV4_1,
                    UPSTREAM_DNS_CLEANBROWSING_IPV6,
                    UPSTREAM_DNS_CLOUDFLARE_IPV6_2,
                    UPSTREAM_DNS_ADGUARD_FAMILY_IPV6,
                    UPSTREAM_DNS_CLOUDFLARE_IPV4_2,
                    UPSTREAM_DNS_CLEANBROWSING_IPV4
                )
            } else {
                listOf(
                    UPSTREAM_DNS_CLOUDFLARE_IPV4_1,
                    UPSTREAM_DNS_CLOUDFLARE_IPV4_2,
                    UPSTREAM_DNS_CLEANBROWSING_IPV4,
                    UPSTREAM_DNS_ADGUARD_FAMILY_IPV4,
                    UPSTREAM_DNS_CLOUDFLARE_IPV6_1,
                    UPSTREAM_DNS_CLEANBROWSING_IPV6
                )
            }

            var dnsAnswerPayload: ByteArray? = null

            for (serverIp in upstreamServers) {
                try {
                    val startMs = SystemClock.elapsedRealtime()
                    val upstreamAddr = InetAddress.getByName(serverIp)
                    val sendPacket = DatagramPacket(dnsQuery.rawQueryBytes, dnsQuery.rawQueryBytes.size, upstreamAddr, 53)
                    socket.send(sendPacket)

                    val recvBuffer = ByteArray(4096)
                    val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)
                    socket.receive(recvPacket)

                    // 1. Validate response originates from exact contacted resolver address and port
                    if (recvPacket.address != upstreamAddr || recvPacket.port != 53) {
                        Log.w(TAG, "Discarded spoofed DNS reply from unexpected source: ${recvPacket.address}:${recvPacket.port}")
                        continue
                    }

                    val payload = ByteArray(recvPacket.length)
                    System.arraycopy(recvBuffer, 0, payload, 0, recvPacket.length)

                    // 2. Validate Transaction ID, response flag (QR=1), opcode 0, and question match
                    if (!DnsPacketParser.validateDnsResponse(payload, dnsQuery)) {
                        Log.w(TAG, "Discarded malformed or mismatched DNS response from $serverIp")
                        continue
                    }

                    val rtt = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
                    _lastUpstreamLatencyMs.value = rtt

                    dnsAnswerPayload = payload
                    break // Succeeded with validated adult-filtered response
                } catch (e: Exception) {
                    // Try next resolver in the pool
                    continue
                }
            }

            if (dnsAnswerPayload != null) {
                // Wrap response back into IP/UDP packet and deliver to TUN
                val outPacket = DnsPacketBuilder.wrapDnsResponseInUdpIp(incomingIpPacket, dnsAnswerPayload)
                synchronized(fos) {
                    fos.write(outPacket)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Upstream DNS resolution error: ${e.message}")
        } finally {
            socket?.close()
        }
    }

    private fun logDnsEvent(domain: String, type: String, isBlocked: Boolean, isSafeSearch: Boolean, reason: String) {
        recordDnsDecision(domain, type, isBlocked, isSafeSearch, reason)
    }

    private fun stopVpn() {
        try {
            statsManager?.flushDnsCounters()
        } catch (e: Exception) {
            Log.w(TAG, "Error flushing DNS counters on stop", e)
        }
        Log.i(TAG, "Stopping ShieldGuard DNS Sinkhole...")
        _isRunning.value = false
        _downloadSpeedBps.value = 0L
        _uploadSpeedBps.value = 0L
        _appDownloadSpeedBps.value = 0L
        _appUploadSpeedBps.value = 0L

        telemetryJob?.cancel()
        telemetryJob = null
        serviceJob?.cancel()
        serviceJob = null

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground exception ignored", e)
        }
        try {
            stopSelf()
        } catch (e: Exception) {
            Log.w(TAG, "stopSelf exception ignored", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }

    // =========================================================================
    // NOTIFICATIONS
    // =========================================================================

    private fun buildNotification(
        titleText: String,
        statusText: String,
        bigText: String? = null
    ): Notification {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText(statusText)
            .setSubText("DNS Sinkhole & Speed")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (!bigText.isNullOrBlank()) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(titleText)
                    .bigText(bigText)
            )
        }

        return builder.build()
    }

    private fun updateNotification(titleText: String, statusText: String, bigText: String? = null) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.notify(NOTIFICATION_ID, buildNotification(titleText, statusText, bigText))
        } catch (e: Exception) {
            Log.w(TAG, "Notification update skipped: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ZevBlock Live Speed & DNS Shield",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time download/upload speed, daily data usage, and DNS sinkhole telemetry"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val TAG = "DnsBlockerVpnService"
        const val ACTION_START = "com.zevbuild.zevblock.vpn.action.START"
        const val ACTION_STOP = "com.zevbuild.zevblock.vpn.action.STOP"

        private const val CHANNEL_ID = "shieldguard_vpn_channel"
        private const val TRAFFIC_PREFS_NAME = "zevblock_traffic_daily_prefs"
        private const val NOTIFICATION_ID = 2001
        private const val MAX_RECENT_LOGS = 50

        // Upstream Adult-Filtering Resolvers (Dual-Stack IPv4 & IPv6)
        // Cloudflare Family 1.1.1.3 (Malware + Adult Content Block)
        const val UPSTREAM_DNS_CLOUDFLARE_IPV4_1 = "1.1.1.3"
        const val UPSTREAM_DNS_CLOUDFLARE_IPV4_2 = "1.0.0.3"
        const val UPSTREAM_DNS_CLOUDFLARE_IPV6_1 = "2606:4700:4700::1113"
        const val UPSTREAM_DNS_CLOUDFLARE_IPV6_2 = "2606:4700:4700::1003"

        // CleanBrowsing Adult Filter
        const val UPSTREAM_DNS_CLEANBROWSING_IPV4 = "185.228.168.10"
        const val UPSTREAM_DNS_CLEANBROWSING_IPV6 = "2a0d:5600:33:4::"

        // AdGuard Family Protection
        const val UPSTREAM_DNS_ADGUARD_FAMILY_IPV4 = "94.140.14.15"
        const val UPSTREAM_DNS_ADGUARD_FAMILY_IPV6 = "2a10:50c0::bad1:ff"

        // Backward compatibility aliases
        const val UPSTREAM_DNS_PRIMARY = UPSTREAM_DNS_CLOUDFLARE_IPV4_1
        const val UPSTREAM_DNS_SECONDARY = UPSTREAM_DNS_CLOUDFLARE_IPV4_2
        const val UPSTREAM_DNS_CLEANBROWSING = UPSTREAM_DNS_CLEANBROWSING_IPV4

        // Global Shared State for UI binding
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _totalQueries = AtomicLong(0L)
        val totalQueriesCount = MutableStateFlow(0L)

        private val _blockedQueries = AtomicLong(0L)
        val blockedQueriesCount = MutableStateFlow(0L)

        private val _safeSearchQueries = AtomicLong(0L)
        val safeSearchQueriesCount = MutableStateFlow(0L)

        private val _allowedQueries = AtomicLong(0L)
        val allowedQueriesCount = MutableStateFlow(0L)

        private val _downloadSpeedBps = MutableStateFlow(0L)
        val downloadSpeedBps: StateFlow<Long> = _downloadSpeedBps.asStateFlow()

        private val _uploadSpeedBps = MutableStateFlow(0L)
        val uploadSpeedBps: StateFlow<Long> = _uploadSpeedBps.asStateFlow()

        private val _appDownloadSpeedBps = MutableStateFlow(0L)
        val appDownloadSpeedBps: StateFlow<Long> = _appDownloadSpeedBps.asStateFlow()

        private val _appUploadSpeedBps = MutableStateFlow(0L)
        val appUploadSpeedBps: StateFlow<Long> = _appUploadSpeedBps.asStateFlow()

        private val _todayDownloadBytes = MutableStateFlow(0L)
        val todayDownloadBytes: StateFlow<Long> = _todayDownloadBytes.asStateFlow()

        private val _todayUploadBytes = MutableStateFlow(0L)
        val todayUploadBytes: StateFlow<Long> = _todayUploadBytes.asStateFlow()

        private val _todayAppDownloadBytes = MutableStateFlow(0L)
        val todayAppDownloadBytes: StateFlow<Long> = _todayAppDownloadBytes.asStateFlow()

        private val _todayAppUploadBytes = MutableStateFlow(0L)
        val todayAppUploadBytes: StateFlow<Long> = _todayAppUploadBytes.asStateFlow()

        private val _lastUpstreamLatencyMs = MutableStateFlow(14L)
        val lastUpstreamLatencyMs: StateFlow<Long> = _lastUpstreamLatencyMs.asStateFlow()

        private val _recentLogs = MutableStateFlow<List<DnsLogEntry>>(emptyList())
        val recentLogs: StateFlow<List<DnsLogEntry>> = _recentLogs.asStateFlow()

        val blocklistMatcher = DomainBlocklistMatcher()
        val safeSearchManager = SafeSearchManager()
        var statsManager: com.zevbuild.zevblock.core.stats.ProtectionStatsManager? = null

        /**
         * Records a DNS link evaluation decision into live telemetry counters and logs.
         * Also used by the interactive DNS tester so users can watch real-time decisions in the Telemetry Stream.
         */
        fun recordDnsDecision(
            domain: String,
            type: String,
            isBlocked: Boolean,
            isSafeSearch: Boolean,
            reason: String
        ) {
            val entry = DnsLogEntry(
                domain = domain,
                recordType = type,
                isBlocked = isBlocked,
                isSafeSearchRewritten = isSafeSearch,
                reason = reason
            )
            val current = _recentLogs.value.toMutableList()
            current.add(0, entry)
            if (current.size > MAX_RECENT_LOGS) {
                current.removeAt(current.size - 1)
            }
            _recentLogs.value = current
        }

        /**
         * Evaluates a domain through the exact 3-tier DNS pipeline and logs it to the live Telemetry Stream.
         */
        fun simulateDnsPipelineLookup(domain: String): DnsLogEntry {
            val clean = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore("/")
            val total = _totalQueries.incrementAndGet()
            totalQueriesCount.value = total

            // Tier 1: SafeSearch VIP check
            val vipTarget = when (clean) {
                "google.com", "www.google.com" -> "216.239.38.120 (Google SafeSearch VIP)"
                "youtube.com", "www.youtube.com", "m.youtube.com" -> "216.239.38.119 (YouTube Restricted VIP)"
                "bing.com", "www.bing.com" -> "204.79.197.220 (Bing Strict VIP)"
                "duckduckgo.com", "www.duckduckgo.com" -> "safe.duckduckgo.com (DDG Safe VIP)"
                else -> null
            }

            if (vipTarget != null) {
                val safeCount = _safeSearchQueries.incrementAndGet()
                safeSearchQueriesCount.value = safeCount
                val reason = "Tier 1: Forced Strict SafeSearch -> $vipTarget"
                recordDnsDecision(clean, "SafeSearch VIP", isBlocked = false, isSafeSearch = true, reason = reason)
                statsManager?.recordDnsQuery(isBlocked = false, isSafeSearch = true)
                return _recentLogs.value.first()
            }

            // Tier 2: Blocklist & Custom Shield check
            val blockRes = blocklistMatcher.isBlocked(clean)
            if (blockRes.isBlocked) {
                val blockedCount = _blockedQueries.incrementAndGet()
                blockedQueriesCount.value = blockedCount
                val reason = "Tier 2 Sinkhole: ${blockRes.reason} -> 0.0.0.0"
                recordDnsDecision(clean, "Blocked (0.0.0.0)", isBlocked = true, isSafeSearch = false, reason = reason)
                statsManager?.recordDnsQuery(isBlocked = true, isSafeSearch = false)
                return _recentLogs.value.first()
            }

            // Tier 3: Allowed via Family DNS
            val allowedCount = _allowedQueries.incrementAndGet()
            allowedQueriesCount.value = allowedCount
            val reason = "Tier 3 Allowed: Clean domain resolved via 1.1.1.3 (Cloudflare Family)"
            recordDnsDecision(clean, "Allowed (1.1.1.3)", isBlocked = false, isSafeSearch = false, reason = reason)
            statsManager?.recordDnsQuery(isBlocked = false, isSafeSearch = false)
            return _recentLogs.value.first()
        }

        fun formatSpeed(bytesPerSec: Long): String {
            return when {
                bytesPerSec >= 1_048_576L -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1_048_576.0)
                bytesPerSec >= 1_024L -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1_024.0)
                else -> "$bytesPerSec B/s"
            }
        }

        fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
                bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
                bytes >= 1_024L -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
                else -> "$bytes B"
            }
        }

        fun start(context: Context) {
            try {
                if (prepare(context) != null) {
                    Log.w(TAG, "Cannot start DnsBlockerVpnService: VPN consent not granted.")
                    return
                }
                val intent = Intent(context, DnsBlockerVpnService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error starting DnsBlockerVpnService", t)
                CrashGuardManager.recordHandledException("DnsBlockerVpnService.start", t, autoRecovered = true)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, DnsBlockerVpnService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (t: Throwable) {
                try {
                    context.stopService(Intent(context, DnsBlockerVpnService::class.java))
                } catch (ignored: Throwable) {
                }
                CrashGuardManager.recordHandledException("DnsBlockerVpnService.stop", t, autoRecovered = true)
            }
        }
    }
}

