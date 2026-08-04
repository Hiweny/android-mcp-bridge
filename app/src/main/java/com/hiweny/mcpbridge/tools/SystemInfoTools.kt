package com.hiweny.mcpbridge.tools

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.NetworkInterface
import kotlin.math.sqrt

/* ======================================================================================
 *  Connectivity
 * ====================================================================================== */

/**
 * Tool that returns the current network connection details.
 *
 * No required parameters.
 */
class ConnectivityInfoTool(private val context: Context) : McpTool {

    override val name: String = "get_connectivity"

    override val description: String =
        "Returns current network connection details: online status, network type " +
            "(WiFi/Cellular/Ethernet/None), carrier name, IPv4/IPv6 addresses and roaming status."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(activeNetwork)

            val isOnline = caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            val type = when {
                caps == null -> "None"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Other"
            }

            val (operator, roaming) = getTelephonyInfo(caps)

            val result = JSONObject().apply {
                put("online", isOnline)
                put("validated", isValidated)
                put("networkType", type)
                put("carrier", operator)
                put("isRoaming", roaming ?: JSONObject.NULL)
                put("ipv4", getLocalIpAddress(ipv4 = true))
                put("ipv6", getLocalIpAddress(ipv4 = false))
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to get connectivity info: ${e.message}")
        }
    }

    private fun getTelephonyInfo(caps: NetworkCapabilities?): Pair<String, Boolean?> {
        var operator = ""
        var roaming: Boolean? = null

        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null) {
                operator = tm.networkOperatorName ?: ""
                if (operator.isEmpty()) operator = tm.simOperatorName ?: ""
            }
        } catch (e: Exception) {
            // Carrier info may be unavailable on some devices.
        }

        try {
            roaming = if (caps != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            } else {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                tm?.isNetworkRoaming
            }
        } catch (e: Exception) {
            roaming = null
        }

        return Pair(operator, roaming)
    }

    private fun getLocalIpAddress(ipv4: Boolean): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return ""
            val addresses = StringBuilder()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr.isLoopbackAddress) continue
                    val host = addr.hostAddress ?: continue
                    val isV6 = host.contains(':')
                    if (ipv4 && !isV6) {
                        if (addresses.isNotEmpty()) addresses.append(", ")
                        addresses.append(host)
                    } else if (!ipv4 && isV6) {
                        // Strip the zone index (e.g. fe80::1%wlan0 -> fe80::1)
                        val cleaned = host.substringBefore('%')
                        if (addresses.isNotEmpty()) addresses.append(", ")
                        addresses.append(cleaned)
                    }
                }
            }
            addresses.toString()
        } catch (e: Exception) {
            ""
        }
    }
}

/* ======================================================================================
 *  Screen
 * ====================================================================================== */

/**
 * Tool that returns screen information.
 *
 * No required parameters.
 */
class ScreenInfoTool(private val context: Context) : McpTool {

    override val name: String = "get_screen_info"

    override val description: String =
        "Returns screen information: resolution, density (DPI and ratio), refresh rate, " +
            "orientation (portrait/landscape) and diagonal screen size in inches."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)

            val widthPx = metrics.widthPixels
            val heightPx = metrics.heightPixels
            val dpi = metrics.densityDpi
            val density = metrics.density
            val refreshRate = display.refreshRate
            val rotation = display.rotation

            val orientation = if (widthPx >= heightPx) "landscape" else "portrait"
            val rotationName = when (rotation) {
                Surface.ROTATION_0 -> "0"
                Surface.ROTATION_90 -> "90"
                Surface.ROTATION_180 -> "180"
                Surface.ROTATION_270 -> "270"
                else -> rotation.toString()
            }

            // Physical diagonal size in inches, derived from the physical DPI.
            val xdpi = if (metrics.xdpi > 0) metrics.xdpi.toDouble() else dpi.toDouble()
            val ydpi = if (metrics.ydpi > 0) metrics.ydpi.toDouble() else dpi.toDouble()
            val widthInches = widthPx / xdpi
            val heightInches = heightPx / ydpi
            val diagonalInches = sqrt(widthInches * widthInches + heightInches * heightInches)
            val diagonalRounded = Math.round(diagonalInches * 100.0) / 100.0

            val result = JSONObject().apply {
                put("resolution", "${widthPx}x${heightPx}")
                put("widthPixels", widthPx)
                put("heightPixels", heightPx)
                put("densityDpi", dpi)
                put("density", density.toDouble())
                put("xdpi", metrics.xdpi.toDouble())
                put("ydpi", metrics.ydpi.toDouble())
                put("refreshRate", refreshRate.toDouble())
                put("orientation", orientation)
                put("rotationDegrees", rotationName)
                put("screenSizeInches", diagonalRounded)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to get screen info: ${e.message}")
        }
    }
}

/* ======================================================================================
 *  Memory / Storage
 * ====================================================================================== */

/**
 * Tool that returns memory and internal storage information.
 *
 * No required parameters.
 */
class MemoryInfoTool(private val context: Context) : McpTool {

    override val name: String = "get_memory_info"

    override val description: String =
        "Returns memory information: total/available RAM, low-memory threshold, low-memory " +
            "flag, and total/available internal storage."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            val statFs = StatFs(Environment.getDataDirectory().path)
            val totalStorage = statFs.totalBytes
            val availableStorage = statFs.availableBytes

            val result = JSONObject().apply {
                put("totalMemoryBytes", memInfo.totalMem)
                put("availableMemoryBytes", memInfo.availMem)
                put("thresholdBytes", memInfo.threshold)
                put("lowMemory", memInfo.lowMemory)
                put("totalMemoryMb", memInfo.totalMem / MB)
                put("availableMemoryMb", memInfo.availMem / MB)
                put("thresholdMb", memInfo.threshold / MB)

                put("totalStorageBytes", totalStorage)
                put("availableStorageBytes", availableStorage)
                put("totalStorageMb", totalStorage / MB)
                put("availableStorageMb", availableStorage / MB)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to get memory info: ${e.message}")
        }
    }

    private companion object {
        const val MB = 1024L * 1024L
    }
}

/* ======================================================================================
 *  Package / App info
 * ====================================================================================== */

/**
 * Tool that returns information about this application.
 *
 * No required parameters.
 */
class PackageInfoTool(private val context: Context) : McpTool {

    override val name: String = "get_package_info"

    override val description: String =
        "Returns information about this application: version name/code, package name, " +
            "install/update times, APK path, UID and requested permissions."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val packageInfo =
                pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)

            val versionName = packageInfo.versionName ?: ""
            @Suppress("DEPRECATION")
            val versionCode: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }

            val permissions = JSONArray()
            packageInfo.requestedPermissions?.forEach { perm ->
                permissions.put(perm)
            }

            val appInfo = packageInfo.applicationInfo
            val result = JSONObject().apply {
                put("packageName", packageInfo.packageName)
                put("versionName", versionName)
                put("versionCode", versionCode)
                put("firstInstallTime", packageInfo.firstInstallTime)
                put("installTime", packageInfo.firstInstallTime)
                put("lastUpdateTime", packageInfo.lastUpdateTime)
                put("apkPath", appInfo?.sourceDir ?: "")
                put("uid", appInfo?.uid ?: -1)
                put("requestedPermissions", permissions)
                put("permissionsCount", permissions.length())
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to get package info: ${e.message}")
        }
    }
}
