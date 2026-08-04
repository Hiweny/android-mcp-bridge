package com.hiweny.mcpbridge.tools

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Maps an Android [Sensor] type constant to a human-readable name.
 */
private fun sensorTypeName(type: Int): String = when (type) {
    Sensor.TYPE_ACCELEROMETER -> "accelerometer"
    Sensor.TYPE_GYROSCOPE -> "gyroscope"
    Sensor.TYPE_LIGHT -> "light"
    Sensor.TYPE_PRESSURE -> "pressure"
    Sensor.TYPE_PROXIMITY -> "proximity"
    Sensor.TYPE_GRAVITY -> "gravity"
    Sensor.TYPE_LINEAR_ACCELERATION -> "linear_acceleration"
    Sensor.TYPE_ROTATION_VECTOR -> "rotation_vector"
    Sensor.TYPE_GAME_ROTATION_VECTOR -> "game_rotation_vector"
    Sensor.TYPE_MAGNETIC_FIELD -> "magnetic_field"
    Sensor.TYPE_AMBIENT_TEMPERATURE -> "ambient_temperature"
    Sensor.TYPE_RELATIVE_HUMIDITY -> "relative_humidity"
    else -> "type_$type"
}

/**
 * Tool that lists all available sensors on the device.
 *
 * Uses SensorManager.getSensorList(Sensor.TYPE_ALL). Returns each sensor's name, type,
 * vendor, version, maximum range, resolution, power and minimum delay.
 */
class ListSensorsTool(private val context: Context) : McpTool {

    override val name: String = "list_sensors"

    override val description: String =
        "Lists all available sensors on the device with their metadata " +
            "(type, vendor, version, range, resolution, power, min delay)."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensors = sm.getSensorList(Sensor.TYPE_ALL)

            val arr = JSONArray()
            for (s in sensors) {
                arr.put(JSONObject().apply {
                    put("name", s.name)
                    put("type", s.type)
                    put("typeName", sensorTypeName(s.type))
                    put("vendor", s.vendor)
                    put("version", s.version)
                    put("maximumRange", s.maximumRange)
                    put("resolution", s.resolution)
                    put("power", s.power)
                    put("minDelay", s.minDelay)
                })
            }

            val result = JSONObject().apply {
                put("count", sensors.size)
                put("sensors", arr)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to list sensors: ${e.message}")
        }
    }
}

/**
 * Tool that reads a single sample from a sensor.
 *
 * Parameters:
 *   - sensor_type (optional, default 1 / TYPE_ACCELEROMETER) the Android sensor type.
 *
 * Supported types: 1=accelerometer, 4=gyroscope, 5=light, 6=pressure, 8=proximity,
 * 9=gravity, 10=linear_acceleration, 11=rotation_vector, 15=game_rotation_vector.
 *
 * Registers a one-shot listener, waits for the first data callback, then unregisters.
 * Times out after 3 seconds.
 */
class ReadSensorTool(private val context: Context) : McpTool {

    override val name: String = "read_sensor"

    override val description: String =
        "Reads a single sample from a sensor. Defaults to the accelerometer (type 1). " +
            "Supported types: 1=accelerometer, 4=gyroscope, 5=light, 6=pressure, 8=proximity, " +
            "9=gravity, 10=linear_acceleration, 11=rotation_vector, 15=game_rotation_vector. " +
            "Times out after 3 seconds."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("sensor_type", JSONObject().apply {
                put("type", "integer")
                put("description", "Android Sensor type integer. Default 1 (accelerometer).")
                put("default", Sensor.TYPE_ACCELEROMETER)
            })
        })
        put("required", JSONArray())
    }

    private val supportedTypes = setOf(1, 4, 5, 6, 8, 9, 10, 11, 15)

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val sensorType = params.optInt("sensor_type", Sensor.TYPE_ACCELEROMETER)
            if (sensorType !in supportedTypes) {
                return@withContext ToolResult.err(
                    "Unsupported sensor_type: $sensorType. Supported types: " +
                        "1=accelerometer, 4=gyroscope, 5=light, 6=pressure, 8=proximity, " +
                        "9=gravity, 10=linear_acceleration, 11=rotation_vector, 15=game_rotation_vector"
                )
            }

            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sm.getDefaultSensor(sensorType)
                ?: return@withContext ToolResult.err(
                    "No sensor available for type $sensorType on this device"
                )

            val values: List<Float> = try {
                withTimeout(TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        val listener = object : SensorEventListener {
                            override fun onSensorChanged(event: SensorEvent) {
                                sm.unregisterListener(this)
                                if (cont.isActive) {
                                    cont.resume(event.values.toList())
                                }
                            }

                            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {
                                // Not used for one-shot reading.
                            }
                        }
                        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                        cont.invokeOnCancellation { sm.unregisterListener(listener) }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                return@withContext ToolResult.err(
                    "Sensor read timed out after ${TIMEOUT_MS}ms for type $sensorType (${sensorTypeName(sensorType)})"
                )
            }

            val valuesJson = JSONArray()
            values.forEach { valuesJson.put(it) }

            val result = JSONObject().apply {
                put("sensorType", sensorType)
                put("typeName", sensorTypeName(sensorType))
                put("sensorName", sensor.name)
                put("values", valuesJson)
                put("valueCount", values.size)
                put("timestampMs", System.currentTimeMillis())
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to read sensor: ${e.message}")
        }
    }

    companion object {
        private const val TIMEOUT_MS = 3000L
    }
}
