package com.hiweny.mcpbridge.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool that returns the device's last known location.
 *
 * Uses [LocationManager] to fetch the last known fix for the requested provider.
 * Requires the ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION runtime permission.
 *
 * Parameters:
 *  - provider (string, default "network", enum: gps/network/passive)
 *  - high_accuracy (boolean, default false) — when true, prefer the GPS provider
 */
class GetLocationTool(private val context: Context) : McpTool {

    override val name: String = "get_location"

    override val description: String =
        "Returns the device's last known location (latitude, longitude, accuracy, altitude, " +
            "speed, bearing and timestamp) using the specified location provider."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("provider", JSONObject().apply {
                put("type", "string")
                put("description", "The location provider to query.")
                put("default", "network")
                put("enum", JSONArray().apply {
                    put("gps")
                    put("network")
                    put("passive")
                })
            })
            put("high_accuracy", JSONObject().apply {
                put("type", "boolean")
                put("description", "If true, prefer the GPS provider for higher accuracy.")
                put("default", false)
            })
        })
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            // --- Permission check -----------------------------------------------------------
            val hasFine = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) {
                return@withContext ToolResult.err(
                    "Location permission is required. Please grant ACCESS_FINE_LOCATION or " +
                        "ACCESS_COARSE_LOCATION to use this tool."
                )
            }

            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // --- Resolve the effective provider ---------------------------------------------
            val requestedProvider = params.optString("provider", "network")
            val highAccuracy = params.optBoolean("high_accuracy", false)

            val preferredProvider = when {
                highAccuracy -> LocationManager.GPS_PROVIDER
                "gps".equals(requestedProvider, ignoreCase = true) -> LocationManager.GPS_PROVIDER
                "passive".equals(requestedProvider, ignoreCase = true) ->
                    LocationManager.PASSIVE_PROVIDER
                else -> LocationManager.NETWORK_PROVIDER
            }

            val enabledProviders = locationManager.getProviders(true)
            val provider = if (enabledProviders.contains(preferredProvider)) {
                preferredProvider
            } else {
                // Fall back to any enabled provider.
                enabledProviders.firstOrNull() ?: return@withContext ToolResult.err(
                    "No enabled location providers are available. Please enable location " +
                        "services (GPS or network) on the device and try again."
                )
            }

            // --- Fetch the last known location ---------------------------------------------
            @Suppress("MissingPermission")
            val location: Location? = locationManager.getLastKnownLocation(provider)

            if (location == null) {
                val empty = JSONObject().apply {
                    put("success", false)
                    put("provider", provider)
                    put("message", "No last known location is available for provider '$provider'. " +
                        "The device may not have a recent location fix. Open a maps app or enable " +
                        "the requested provider, then try again.")
                }
                return@withContext ToolResult.ok(empty.toString())
            }

            val result = JSONObject().apply {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("provider", location.provider ?: provider)
                put("timestamp", location.time)
                put("accuracy", if (location.hasAccuracy()) location.accuracy.toDouble() else JSONObject.NULL)
                put("altitude", if (location.hasAltitude()) location.altitude else JSONObject.NULL)
                put("speed", if (location.hasSpeed()) location.speed.toDouble() else JSONObject.NULL)
                put("bearing", if (location.hasBearing()) location.bearing.toDouble() else JSONObject.NULL)
                // hasVerticalAccuracy()/verticalAccuracyMeters are API 26+ (== minSdk).
                put("verticalAccuracy",
                    if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters.toDouble()
                    else JSONObject.NULL)
            }
            ToolResult.ok(result.toString())
        } catch (e: SecurityException) {
            ToolResult.err("Location permission denied: ${e.message}")
        } catch (e: Exception) {
            ToolResult.err("Failed to get location: ${e.message}")
        }
    }
}
