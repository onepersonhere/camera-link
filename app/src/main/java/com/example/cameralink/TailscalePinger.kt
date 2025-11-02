package com.example.cameralink

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Utility class to detect and ping Tailscale network connections
 */
object TailscalePinger {
    private const val TAG = "TailscalePinger"
    private const val PING_TIMEOUT_MS = 2000

    // Tailscale uses 100.64.0.0/10 CGNAT range
    // This means IPs from 100.64.0.0 to 100.127.255.255

    // Manually configured Tailscale peer IPs/hostnames to ping
    // Add your Tailscale device IPs or MagicDNS names here
    private val configuredTailscaleIps = mutableSetOf<String>(
        // Default Tailscale MagicDNS hostnames
        "erics-macbook-pro-2",
        "iphone-14-pro-max",
        "laptop-l2vhnlt6",
        "whs-macbook-pro-1",
        "matt.tail08eb66.ts.net",
        "oppo-cph2697.tail08eb66.ts.net"
    )

    /**
     * Add a Tailscale IP or MagicDNS hostname to the list of targets to ping
     */
    fun addTailscaleIp(ip: String) {
        val trimmed = ip.trim()
        if (trimmed.isNotEmpty()) {
            // Accept both IPs and hostnames
            if (isTailscaleIp(trimmed) || isValidHostname(trimmed)) {
                configuredTailscaleIps.add(trimmed)
                Log.i(TAG, "Added Tailscale target to ping list: $trimmed")
            } else {
                Log.w(TAG, "Target $trimmed is not a valid Tailscale IP (100.64.0.0 - 100.127.255.255) or hostname")
            }
        }
    }

    /**
     * Remove a Tailscale IP/hostname from the ping list
     */
    fun removeTailscaleIp(ip: String) {
        configuredTailscaleIps.remove(ip)
        Log.i(TAG, "Removed Tailscale target from ping list: $ip")
    }

    /**
     * Get all configured Tailscale IPs/hostnames
     */
    fun getConfiguredIps(): Set<String> = configuredTailscaleIps.toSet()

    /**
     * Clear all configured IPs/hostnames
     */
    fun clearConfiguredIps() {
        configuredTailscaleIps.clear()
        Log.i(TAG, "Cleared all configured Tailscale targets")
    }

    /**
     * Check if a string is a valid hostname
     */
    private fun isValidHostname(hostname: String): Boolean {
        // Simple hostname validation
        return hostname.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]$")) ||
               hostname.matches(Regex("^[a-zA-Z0-9]+$"))
    }

    /**
     * Check if an IP is in the Tailscale CGNAT range (100.64.0.0/10)
     */
    private fun isTailscaleIp(ip: String): Boolean {
        try {
            val parts = ip.split(".")
            if (parts.size != 4) return false

            val first = parts[0].toIntOrNull() ?: return false
            val second = parts[1].toIntOrNull() ?: return false

            // Tailscale uses 100.64.0.0/10
            // First octet must be 100
            // Second octet must be 64-127 (binary: 01xxxxxx)
            return first == 100 && second in 64..127
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Get all Tailscale IP addresses from network interfaces (IPs assigned to THIS device)
     */
    fun getLocalTailscaleIps(): List<String> {
        val tailscaleIps = mutableListOf<String>()
        val allIps = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            Log.d(TAG, "Scanning network interfaces for Tailscale IPs...")

            for (intf in interfaces) {
                val intfName = intf.name?.lowercase() ?: ""
                val isTailscaleInterface = intfName.contains("tailscale") ||
                                          intfName.startsWith("ts") ||
                                          intfName.contains("utun") // iOS/Android VPN tunnel

                if (isTailscaleInterface) {
                    Log.d(TAG, "Found Tailscale interface: ${intf.name}")
                }

                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    val hostAddress = addr.hostAddress
                    if (hostAddress != null &&
                        !addr.isLoopbackAddress &&
                        hostAddress.indexOf(':') < 0) { // IPv4 only

                        allIps.add(hostAddress)
                        Log.d(TAG, "Found IP: $hostAddress on ${intf.name}")

                        if (isTailscaleIp(hostAddress)) {
                            tailscaleIps.add(hostAddress)
                            Log.i(TAG, "✅ Found LOCAL Tailscale IP: $hostAddress on ${intf.name}")
                        }
                    }
                }
            }

            Log.i(TAG, "Total IPs found: ${allIps.size}, Tailscale IPs: ${tailscaleIps.size}")

            // For testing: if no Tailscale IPs found, return all non-loopback IPs
            if (tailscaleIps.isEmpty() && allIps.isNotEmpty()) {
                Log.w(TAG, "⚠️ No LOCAL Tailscale IPs found. Using all available IPs for testing: $allIps")
                return allIps
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting Tailscale IPs", e)
        }
        return tailscaleIps
    }

    /**
     * Get all Tailscale IPs/hostnames to ping (local + configured remote peers)
     */
    fun getTailscaleIps(): List<String> {
        val localIps = getLocalTailscaleIps()
        val allTargets = (localIps + configuredTailscaleIps).distinct()

        Log.i(TAG, "Total Tailscale targets to ping: ${allTargets.size} (${localIps.size} local + ${configuredTailscaleIps.size} configured)")

        return allTargets
    }

    /**
     * Ping a single IP address or hostname
     * @return true if the ping was successful, false otherwise
     */
    suspend fun pingIp(target: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Attempting to ping: $target")
            val inetAddress = InetAddress.getByName(target)
            Log.d(TAG, "Resolved $target to ${inetAddress.hostAddress}")

            val reachable = inetAddress.isReachable(PING_TIMEOUT_MS)
            if (reachable) {
                Log.d(TAG, "✅ Successfully pinged $target (${inetAddress.hostAddress})")
            } else {
                Log.w(TAG, "❌ Failed to ping $target (${inetAddress.hostAddress})")
            }
            reachable
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error pinging $target: ${e.message}", e)
            false
        }
    }

    /**
     * Ping all Tailscale connections (IPs and hostnames)
     * @return Map of target (IP/hostname) to ping success status
     */
    suspend fun pingAllTailscaleConnections(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val targets = getTailscaleIps()

        if (targets.isEmpty()) {
            Log.i(TAG, "No Tailscale connections found")
            return@withContext emptyMap()
        }

        Log.i(TAG, "Pinging ${targets.size} Tailscale target(s)")

        val results = mutableMapOf<String, Boolean>()
        for (target in targets) {
            val success = pingIp(target)
            results[target] = success
        }

        val successCount = results.values.count { it }
        Log.i(TAG, "Ping complete: $successCount/${targets.size} successful")

        results
    }
}

