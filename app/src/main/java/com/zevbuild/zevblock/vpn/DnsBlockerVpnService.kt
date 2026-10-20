package com.zevbuild.zevblock.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zevbuild.zevblock.MainActivity
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong

/**
 * DnsBlockerVpnService:
 * High-performance, battery-efficient local DNS sinkhole VpnService.
 * Intercepts DNS queries on virtual address 10.254.1.1 / fd00::1:
 * - Enforces SafeSearch VIP rewriting (e.g. www.google.com -> 216.239.38.120).
 * - Matches domains against in-memory StevenBlack hosts blocklists.
 * - Forwards clean queries to upstream resolvers (1.1.1.1 / 9.9.9.9) using protected sockets.
 */
open class DnsBlockerVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.i(TAG, "DnsBlockerVpnService received action: $action")

        when (action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (_isRunning.value) {
            Log.w(TAG, "VPN is already running.")
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification("Starting DNS Sinkhole..."))

        try {
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                try {
                    builder.setUnderlyingNetworks(null)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set underlying networks: ${e.message}")
                }
            }

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN TUN interface.")
                stopSelf()
                return
            }

            _isRunning.value = true
            updateNotification("Active • Protected against trackers & explicit content")

            serviceJob = scope.launch {
                runPacketLoop(vpnInterface!!)
            }

            Log.i(TAG, "ZevBlock DNS Sinkhole successfully established.")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error establishing VPN", e)
            stopVpn()
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
                if (bytesRead <= 0) continue

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
                    logDnsEvent(domain, "SafeSearch VIP", isBlocked = false, isSafeSearch = true, reason = "Forced Strict VIP")
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
                    logDnsEvent(domain, "Blocked", isBlocked = true, isSafeSearch = false, reason = blockResult.reason)
                    statsManager?.recordDnsQuery(isBlocked = true, isSafeSearch = false)
                    continue
                }

                // 5. Step C: Forward clean query to Upstream Adult-Filtering DNS Resolver asynchronously
                scope.launch(Dispatchers.IO) {
                    forwardUpstreamDns(ipPacket, dnsQuery.rawQueryBytes, fos)
                }
                logDnsEvent(domain, "Allowed", isBlocked = false, isSafeSearch = false, reason = "Upstream Resolved (Adult-Filtered)")
                statsManager?.recordDnsQuery(isBlocked = false, isSafeSearch = false)

            } catch (e: Exception) {
                if (scope.isActive) {
                    Log.e(TAG, "Error processing DNS packet in loop", e)
                }
            }
        }
    }

    /**
     * Forwards a query to upstream Adult-Filtering DNS resolvers
     * (Cloudflare Family 1.1.1.3 / CleanBrowsing / AdGuard Family)
     * using a protected UDP socket that bypasses the local VPN TUN interface.
     */
    private fun forwardUpstreamDns(incomingIpPacket: IpUdpPacket, rawDnsQuery: ByteArray, fos: FileOutputStream) {
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
                    val upstreamAddr = InetAddress.getByName(serverIp)
                    val sendPacket = DatagramPacket(rawDnsQuery, rawDnsQuery.size, upstreamAddr, 53)
                    socket.send(sendPacket)

                    val recvBuffer = ByteArray(4096)
                    val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)
                    socket.receive(recvPacket)

                    val payload = ByteArray(recvPacket.length)
                    System.arraycopy(recvBuffer, 0, payload, 0, recvPacket.length)
                    dnsAnswerPayload = payload
                    break // Succeeded with adult-filtered response
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

    private fun stopVpn() {
        Log.i(TAG, "Stopping ShieldGuard DNS Sinkhole...")
        _isRunning.value = false
        serviceJob?.cancel()
        serviceJob = null

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }

    // =========================================================================
    // NOTIFICATIONS
    // =========================================================================

    private fun buildNotification(statusText: String): Notification {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZevBlock")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ZevBlock VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active DNS sinkhole status and protection counters"
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

        private val _recentLogs = MutableStateFlow<List<DnsLogEntry>>(emptyList())
        val recentLogs: StateFlow<List<DnsLogEntry>> = _recentLogs.asStateFlow()

        val blocklistMatcher = DomainBlocklistMatcher()
        val safeSearchManager = SafeSearchManager()
        var statsManager: com.zevbuild.zevblock.core.stats.ProtectionStatsManager? = null

        fun start(context: Context) {
            val intent = Intent(context, DnsBlockerVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DnsBlockerVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
