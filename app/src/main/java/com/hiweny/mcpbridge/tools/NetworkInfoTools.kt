package com.hiweny.mcpbridge.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.StatFs
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Converts an IPv4 address stored as a packed integer (network byte order) to a dotted string. */
private fun intToIp(addr: Int): String {
    if (addr == 0) return "0.0.0.0"
    return (addr and 0xFF).toString() + "." +
        (addr shr 8 and 0xFF) + "." +
        (addr shr 16 and 0xFF) + "." +
        (addr shr 24 and 0xFF)
}

/** Formats a byte count into a human readable string (e.g. "1.5 MB"). */
private fun humanReadableSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        .coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}

/**
 * Tool that returns the current network connection information.
 *
 * Returns the network type (WiFi / Cellular / Ethernet / None), whether the device has
 * internet connectivity, and the IP address, subnet mask, gateway and DNS servers
 * (obtained from the last DHCP lease when connected over WiFi).
 *
 * No required parameters.
 */
class NetworkInfoTool(private val context: Context) : McpTool {

    override val name: String = "get_network_info"

    override val description: String =
        "Returns the current network connection type (WiFi/Cellular/Ethernet/None), internet " +
            "connectivity, IP address, subnet mask, gateway and DNS servers."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null

            val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val networkType = when {
                caps == null -> "none"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "unknown"
            }

            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager

            val dhcpInfo = try {
                @Suppress("DEPRECATION")
                wifiManager.dhcpInfo
            } catch (e: Exception) {
                null
            }
            val connectionInfo = try {
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo
            } catch (e: Exception) {
                null
            }

            val ipAddress = connectionInfo?.ipAddress?.let { intToIp(it) }
                ?: dhcpInfo?.ipAddress?.let { intToIp(it) }
                ?: "unknown"
            val subnetMask = dhcpInfo?.netmask?.let { intToIp(it) } ?: "unknown"
            val gateway = dhcpInfo?.gateway?.let { intToIp(it) } ?: "unknown"
            val dns1 = dhcpInfo?.dns1?.let { intToIp(it) } ?: "unknown"
            val dns2 = dhcpInfo?.dns2?.let { intToIp(it) } ?: "unknown"
            val dhcpServer = dhcpInfo?.serverAddress?.let { intToIp(it) } ?: "unknown"

            val result = JSONObject().apply {
                put("type", networkType)
                put("hasInternet", hasInternet)
                put("isConnected", caps != null && hasInternet)
                put("ipAddress", ipAddress)
                put("subnetMask", subnetMask)
                put("gateway", gateway)
                put("dns1", dns1)
                put("dns2", dns2)
                put("dhcpServer", dhcpServer)
                put("leaseDuration", dhcpInfo?.leaseDuration ?: -1)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to get network info: ${e.message}")
        }
    }
}

/**
 * Tool that returns the current WiFi connection details.
 *
 * Returns SSID, BSSID, signal strength (RSSI), signal level, frequency, link speed,
 * IP address and supplicant state. Uses WifiManager.
 *
 * No required parameters.
 *
 * Note: On Android 11+ (API 30+) SSID/BSSID may be unavailable without location permissions.
 */
class WifiInfoTool(private val context: Context) : McpTool {

    override val name: String = "get_wifi_info"

    override val description: String =
        "Returns current WiFi connection details: SSID, BSSID, signal strength (RSSI), " +
            "frequency, link speed and IP address."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager

            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            @Suppress("DEPRECATION")
            val rssi = info.rssi
            @Suppress("DEPRECATION")
            val signalLevel = WifiManager.calculateSignalLevel(rssi, 5)

            val result = JSONObject().apply {
                put("ssid", info.ssid ?: "")
                put("bssid", info.bssid ?: "")
                put("rssi", rssi)
                put("signalLevel", signalLevel)
                @Suppress("DEPRECATION")
                put("frequency", info.frequency)
                @Suppress("DEPRECATION")
                put("linkSpeedMbps", info.linkSpeed)
                put("ipAddress", intToIp(info.ipAddress))
                put("macAddress", info.macAddress ?: "")
                put("hiddenSSID", info.hiddenSSID)
                put("networkId", info.networkId)
                put("supplicantState", info.supplicantState?.toString() ?: "unknown")
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to get WiFi info: ${e.message}")
        }
    }
}

/**
 * Tool that executes a ping command against a host and returns the parsed statistics.
 *
 * Parameters:
 *   - host  (required) hostname or IP address to ping
 *   - count (optional, default 4) number of packets to send (1-20)
 *
 * The command is run via Runtime.exec using the array form (no shell). The host is
 * validated to reject whitespace and shell metacharacters. Results include packet
 * loss, transmitted/received counts, RTT min/avg/max/mdev and the raw output.
 */
class PingTool(private val context: Context) : McpTool {

    override val name: String = "ping"

    override val description: String =
        "Executes a ping command against the specified host and returns packet loss and " +
            "round-trip time statistics."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("host", JSONObject().apply {
                put("type", "string")
                put("description", "The hostname or IP address to ping.")
            })
            put("count", JSONObject().apply {
                put("type", "integer")
                put("description", "Number of ping packets to send.")
                put("default", 4)
                put("minimum", 1)
                put("maximum", 20)
            })
        })
        put("required", JSONArray().put("host"))
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val host = params.optString("host", "").trim()
            if (host.isEmpty()) {
                return@withContext ToolResult.err("Missing required parameter: host")
            }
            // Reject hosts containing whitespace or shell metacharacters
            if (host.any { it.isWhitespace() }) {
                return@withContext ToolResult.err("Invalid host: contains whitespace")
            }
            if (FORBIDDEN_HOST_CHARS.any { it in host }) {
                return@withContext ToolResult.err("Invalid host: contains forbidden characters")
            }

            val count = params.optInt("count", 4).coerceIn(1, 20)

            val process = Runtime.getRuntime().exec(
                arrayOf("ping", "-c", count.toString(), host)
            )

            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                lines.forEach { output.appendLine(it) }
            }
            BufferedReader(InputStreamReader(process.errorStream)).useLines { lines ->
                lines.forEach { output.appendLine(it) }
            }

            val timeoutSeconds = count * 2L + 10L
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext ToolResult.err("Ping timed out after $timeoutSeconds seconds")
            }
            val exitCode = try {
                process.exitValue()
            } catch (e: Exception) {
                -1
            }

            val raw = output.toString()

            // Parse: "5 packets transmitted, 4 received, 20% packet loss"
            val statsMatch = Regex("(\\d+) packets transmitted,\\s*(\\d+) received").find(raw)
            val transmitted = statsMatch?.groupValues?.get(1)?.toIntOrNull()
            val received = statsMatch?.groupValues?.get(2)?.toIntOrNull()

            val lossMatch = Regex("(\\d+)% packet loss").find(raw)
            val packetLoss = lossMatch?.groupValues?.get(1)?.toIntOrNull()

            // Parse: "rtt min/avg/max/mdev = 1.234/2.345/3.456/0.789 ms"
            val rttMatch = Regex(
                "(?:rtt|round-trip)\\s+min/avg/max/mdev\\s*=\\s*" +
                    "([\\d.]+)/([\\d.]+)/([\\d.]+)/([\\d.]+)\\s*(\\w+)"
            ).find(raw)
            val rtt = if (rttMatch != null) {
                JSONObject().apply {
                    put("min", rttMatch.groupValues[1].toDoubleOrNull())
                    put("avg", rttMatch.groupValues[2].toDoubleOrNull())
                    put("max", rttMatch.groupValues[3].toDoubleOrNull())
                    put("mdev", rttMatch.groupValues[4].toDoubleOrNull())
                    put("unit", rttMatch.groupValues[5])
                }
            } else null

            val result = JSONObject().apply {
                put("host", host)
                put("count", count)
                put("exitCode", exitCode)
                put("success", exitCode == 0)
                put("transmitted", transmitted)
                put("received", received)
                put("packetLossPercent", packetLoss)
                put("rtt", rtt)
                put("output", raw)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to execute ping: ${e.message}")
        }
    }

    companion object {
        private val FORBIDDEN_HOST_CHARS = charArrayOf(';', '&', '|', '`', '$', '(', ')', '<', '>')
    }
}

/**
 * Tool that returns internal and external storage space information.
 *
 * Returns total, used and available space (in bytes and human-readable form) for the
 * internal data partition and the external (shared) storage when mounted.
 * Uses StatFs.
 *
 * No required parameters.
 */
class StorageInfoTool(private val context: Context) : McpTool {

    override val name: String = "get_storage_info"

    override val description: String =
        "Returns internal and external storage total, used and available space."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val internalPath = Environment.getDataDirectory()
            val internal = statFsInfo(internalPath)

            val external = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                statFsInfo(Environment.getExternalStorageDirectory())
            } else null

            val result = JSONObject().apply {
                put("internal", JSONObject().apply {
                    put("path", internalPath.absolutePath)
                    put("totalBytes", internal.total)
                    put("availableBytes", internal.available)
                    put("usedBytes", internal.used)
                    put("totalHuman", humanReadableSize(internal.total))
                    put("availableHuman", humanReadableSize(internal.available))
                    put("usedHuman", humanReadableSize(internal.used))
                })
                if (external != null) {
                    put("external", JSONObject().apply {
                        put("path", Environment.getExternalStorageDirectory().absolutePath)
                        put("mounted", true)
                        put("totalBytes", external.total)
                        put("availableBytes", external.available)
                        put("usedBytes", external.used)
                        put("totalHuman", humanReadableSize(external.total))
                        put("availableHuman", humanReadableSize(external.available))
                        put("usedHuman", humanReadableSize(external.used))
                    })
                } else {
                    put("external", JSONObject().apply {
                        put("mounted", false)
                    })
                }
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to get storage info: ${e.message}")
        }
    }

    private data class StorageStats(val total: Long, val available: Long, val used: Long)

    private fun statFsInfo(path: File): StorageStats {
        val stat = StatFs(path.absolutePath)
        val total = stat.totalBytes
        val available = stat.availableBytes
        return StorageStats(total, available, total - available)
    }
}
