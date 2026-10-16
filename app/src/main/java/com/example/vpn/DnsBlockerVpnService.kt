package com.example.vpn

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
import com.example.MainActivity
import com.example.core.dns.DnsLogEntry
import com.example.core.dns.DnsPacketBuilder
import com.example.core.dns.DnsPacketParser
import com.example.core.dns.DomainBlocklistMatcher
import com.example.core.dns.IpUdpPacket
import com.example.core.dns.SafeSearchManager
import com.example.core.security.TamperLockManager
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
                // Virtual DNS sinkhole endpoint
                .addAddress("10.254.1.1", 32)
                .addAddress("fd00::1", 128)
                // Assign local sinkhole as system DNS server
                .addDnsServer("10.254.1.1")
                .addDnsServer("fd00::1")
                // Route only virtual DNS traffic through TUN to preserve native TCP/UDP speed!
                .addRoute("10.254.1.1", 32)
                .addRoute("fd00::1", 128)

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

                // 3. Step A: SafeSearch VIP Check (Skipped during active temporary bypass)
                if (!isBypassActive) {
                    val safeSearchResponse = safeSearchManager.rewriteDns(dnsQuery)
                    if (safeSearchResponse != null) {
                        val outPacket = DnsPacketBuilder.wrapDnsResponseInUdpIp(ipPacket, safeSearchResponse)
                        synchronized(fos) {
                            fos.write(outPacket)
                        }
                        val safeCount = _safeSearchQueries.incrementAndGet()
                        safeSearchQueriesCount.value = safeCount
                        logDnsEvent(domain, "SafeSearch VIP", isBlocked = false, isSafeSearch = true, reason = "Forced Strict VIP")
                        continue
                    }
                }

                // 4. Step B: Domain Blocklist Check (Skipped during active temporary bypass)
                if (!isBypassActive) {
                    val blockResult = blocklistMatcher.isBlocked(domain)
                    if (blockResult.isBlocked) {
                        val sinkholeResponse = DnsPacketBuilder.buildSinkholeResponse(dnsQuery, blockedIp = "0.0.0.0", isNxDomain = false)
                        val outPacket = DnsPacketBuilder.wrapDnsResponseInUdpIp(ipPacket, sinkholeResponse)
                        synchronized(fos) {
                            fos.write(outPacket)
                        }
                        val blockedCount = _blockedQueries.incrementAndGet()
                        blockedQueriesCount.value = blockedCount
                        logDnsEvent(domain, "Blocked", isBlocked = true, isSafeSearch = false, reason = blockResult.reason)
                        continue
                    }
                }

                // 5. Step C: Forward clean query to Upstream Adult-Filtering DNS Resolver asynchronously
                scope.launch(Dispatchers.IO) {
                    forwardUpstreamDns(ipPacket, dnsQuery.rawQueryBytes, fos)
                }
                logDnsEvent(domain, "Allowed", isBlocked = false, isSafeSearch = false, reason = "Upstream Resolved (Adult-Filtered)")

            } catch (e: Exception) {
                if (scope.isActive) {
                    Log.e(TAG, "Error processing DNS packet in loop", e)
                }
            }
        }
    }

    /**
     * Forwards a query to upstream Adult-Filtering DNS resolvers (Cloudflare 1.1.1.3 / CleanBrowsing 185.228.168.10)
     * using a protected UDP socket that bypasses the local VPN TUN interface.
     */
    private fun forwardUpstreamDns(incomingIpPacket: IpUdpPacket, rawDnsQuery: ByteArray, fos: FileOutputStream) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            protect(socket) // Critical: socket bypasses VPN interface!
            socket.soTimeout = 2500 // 2.5 seconds timeout

            val upstreamIps = listOf(
                UPSTREAM_DNS_PRIMARY,
                UPSTREAM_DNS_SECONDARY,
                UPSTREAM_DNS_CLEANBROWSING
            )

            var dnsAnswerPayload: ByteArray? = null

            for (serverIp in upstreamIps) {
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
                    break // Succeeded
                } catch (e: Exception) {
                    // Try next resolver
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
        const val ACTION_START = "com.example.vpn.action.START"
        const val ACTION_STOP = "com.example.vpn.action.STOP"

        private const val CHANNEL_ID = "shieldguard_vpn_channel"
        private const val NOTIFICATION_ID = 2001
        private const val MAX_RECENT_LOGS = 50

        // Upstream Adult-Filtering Resolvers (Cloudflare Family 1.1.1.3 & CleanBrowsing Adult Filter)
        const val UPSTREAM_DNS_PRIMARY = "1.1.1.3" // Cloudflare Family (Malware + Adult Content Block)
        const val UPSTREAM_DNS_SECONDARY = "1.0.0.3" // Cloudflare Family Secondary
        const val UPSTREAM_DNS_CLEANBROWSING = "185.228.168.10" // CleanBrowsing Adult Filter
        const val UPSTREAM_DNS_ADGUARD_FAMILY = "94.140.14.15" // AdGuard Family Protection

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
