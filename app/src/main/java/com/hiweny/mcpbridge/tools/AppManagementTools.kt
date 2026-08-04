package com.hiweny.mcpbridge.tools

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool that lists installed applications.
 *
 * Parameters:
 *   - keyword (optional) case-insensitive filter matched against package name and app label.
 *
 * Uses PackageManager.getInstalledApplications. Returns package name, app name,
 * version name/code and whether the app is a system app.
 */
class ListAppsTool(private val context: Context) : McpTool {

    override val name: String = "list_apps"

    override val description: String =
        "Lists installed applications. Returns package name, app name, version and " +
            "whether it is a system app. Supports optional keyword filtering."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("keyword", JSONObject().apply {
                put("type", "string")
                put("description", "Optional case-insensitive filter matched against package name or app name.")
            })
        })
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val keyword = params.optString("keyword", "").trim()
            val apps = pm.getInstalledApplications(0)

            val arr = JSONArray()
            var matched = 0
            for (info in apps) {
                val label = try {
                    info.loadLabel(pm).toString()
                } catch (e: Exception) {
                    info.packageName
                }

                if (keyword.isNotEmpty()) {
                    val key = keyword.lowercase()
                    if (!info.packageName.lowercase().contains(key) &&
                        !label.lowercase().contains(key)
                    ) {
                        continue
                    }
                }

                val (versionName, versionCode) = try {
                    val pkg = pm.getPackageInfo(info.packageName, 0)
                    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pkg.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pkg.versionCode.toLong()
                    }
                    Pair(pkg.versionName ?: "", code)
                } catch (e: Exception) {
                    Pair("", 0L)
                }

                arr.put(JSONObject().apply {
                    put("packageName", info.packageName)
                    put("appName", label)
                    put("versionName", versionName)
                    put("versionCode", versionCode)
                    put("isSystemApp", info.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                })
                matched++
            }

            val result = JSONObject().apply {
                put("count", matched)
                put("keyword", keyword)
                put("apps", arr)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to list apps: ${e.message}")
        }
    }
}

/**
 * Tool that lists running app processes.
 *
 * Uses ActivityManager.getRunningAppProcesses. Returns process name, PID, importance
 * level and memory usage (PSS in KB/bytes).
 *
 * Note: since Android 5.0 (API 21), getRunningAppProcesses only returns the caller's own
 * processes (and a few visible ones) for privacy reasons.
 */
class ListRunningAppsTool(private val context: Context) : McpTool {

    override val name: String = "list_running_apps"

    override val description: String =
        "Lists running app processes. Returns package name, PID, importance level and " +
            "memory usage (PSS). Note: since Android 5.0 this only returns the caller's own " +
            "processes for privacy reasons."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processes = am.runningAppProcesses ?: emptyList()

            val arr = JSONArray()
            for (proc in processes) {
                val pssKb = if (proc.pid > 0) {
                    try {
                        val mem = am.getProcessMemoryInfo(intArrayOf(proc.pid))
                        mem?.firstOrNull()?.totalPss ?: -1
                    } catch (e: Exception) {
                        -1
                    }
                } else {
                    -1
                }

                arr.put(JSONObject().apply {
                    put("processName", proc.processName)
                    put("pid", proc.pid)
                    put("importance", proc.importance)
                    put("importanceLevel", importanceToString(proc.importance))
                    put("pssKb", pssKb)
                    put("pssBytes", if (pssKb >= 0) pssKb * 1024L else -1L)
                })
            }

            val result = JSONObject().apply {
                put("count", processes.size)
                put("processes", arr)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to list running apps: ${e.message}")
        }
    }

    private fun importanceToString(importance: Int): String = when (importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "foreground_service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> "top_sleeping"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "perceptible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE -> "cant_save_state"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHE -> "cached"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "gone"
        else -> "unknown"
    }
}

/**
 * Tool that launches an application by its package name.
 *
 * Parameters:
 *   - package_name (required) package name of the application to launch.
 *
 * Uses PackageManager.getLaunchIntentForPackage.
 */
class OpenAppTool(private val context: Context) : McpTool {

    override val name: String = "open_app"

    override val description: String =
        "Launches an application by its package name using its default launcher intent."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("package_name", JSONObject().apply {
                put("type", "string")
                put("description", "Package name of the application to launch.")
            })
        })
        put("required", JSONArray().put("package_name"))
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val packageName = params.optString("package_name", "").trim()
            if (packageName.isEmpty()) {
                return@withContext ToolResult.err("Missing required parameter: package_name")
            }

            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
                ?: return@withContext ToolResult.err(
                    "No launchable activity found for package: $packageName"
                )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            val result = JSONObject().apply {
                put("success", true)
                put("packageName", packageName)
                put("message", "Launched $packageName")
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to open app: ${e.message}")
        }
    }
}

/**
 * Tool that returns detailed information about an application.
 *
 * Parameters:
 *   - package_name (required) package name of the application.
 *
 * Returns package name, version name/code, install/update time, requested permissions,
 * whether it is a system app, UID, dataDir and sourceDir.
 */
class GetAppInfoTool(private val context: Context) : McpTool {

    override val name: String = "get_app_info"

    override val description: String =
        "Returns detailed information about an application: version, install/update time, " +
            "permissions, system flag, UID, data dir and source dir."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("package_name", JSONObject().apply {
                put("type", "string")
                put("description", "Package name of the application.")
            })
        })
        put("required", JSONArray().put("package_name"))
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val packageName = params.optString("package_name", "").trim()
            if (packageName.isEmpty()) {
                return@withContext ToolResult.err("Missing required parameter: package_name")
            }

            val pm = context.packageManager
            val pkgInfo = try {
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            } catch (e: PackageManager.NameNotFoundException) {
                return@withContext ToolResult.err("Package not found: $packageName")
            }

            val appInfo = pkgInfo.applicationInfo
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }

            val permissions = JSONArray()
            pkgInfo.requestedPermissions?.forEach { permissions.put(it) }

            val result = JSONObject().apply {
                put("packageName", pkgInfo.packageName)
                put("versionName", pkgInfo.versionName ?: "")
                put("versionCode", versionCode)
                put("firstInstallTime", pkgInfo.firstInstallTime)
                put("lastUpdateTime", pkgInfo.lastUpdateTime)
                put("isSystemApp", appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                put("uid", appInfo.uid)
                put("dataDir", appInfo.dataDir ?: "")
                put("sourceDir", appInfo.sourceDir ?: "")
                put("permissions", permissions)
                put("permissionCount", permissions.length())
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to get app info: ${e.message}")
        }
    }
}

/**
 * Tool that kills the background processes of an application.
 *
 * Parameters:
 *   - package_name (required) package name of the application.
 *
 * Uses ActivityManager.killBackgroundProcesses. Requires the
 * KILL_BACKGROUND_PROCESSES permission; returns a helpful error if it is not granted.
 */
class KillAppTool(private val context: Context) : McpTool {

    override val name: String = "kill_app"

    override val description: String =
        "Force stops / kills background processes of an application. Requires the " +
            "KILL_BACKGROUND_PROCESSES permission."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("package_name", JSONObject().apply {
                put("type", "string")
                put("description", "Package name of the application whose background processes should be killed.")
            })
        })
        put("required", JSONArray().put("package_name"))
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val packageName = params.optString("package_name", "").trim()
            if (packageName.isEmpty()) {
                return@withContext ToolResult.err("Missing required parameter: package_name")
            }

            val granted = context.checkSelfPermission(
                android.Manifest.permission.KILL_BACKGROUND_PROCESSES
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                return@withContext ToolResult.err(
                    "Permission denied: KILL_BACKGROUND_PROCESSES is not granted. " +
                        "Add <uses-permission android:name=\"android.permission.KILL_BACKGROUND_PROCESSES\"/> " +
                        "to the AndroidManifest to use this tool."
                )
            }

            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)

            val result = JSONObject().apply {
                put("success", true)
                put("packageName", packageName)
                put("message", "Requested to kill background processes of $packageName")
            }
            ToolResult.ok(result.toString())
        } catch (e: SecurityException) {
            ToolResult.err("Permission denied: ${e.message}")
        } catch (e: Exception) {
            ToolResult.err("Failed to kill app: ${e.message}")
        }
    }
}
