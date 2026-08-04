package com.hiweny.mcpbridge.tools

import android.content.Context
import android.os.Environment
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Resolves [path] to a canonical [File] only if it falls inside one of the allowed
 * directories (external storage or app-specific directories). Returns null otherwise.
 */
private fun resolveSafePath(context: Context, path: String): File? {
    val file = try {
        File(path).canonicalFile
    } catch (e: Exception) {
        return null
    }
    val filePath = file.absolutePath

    val allowedDirs = mutableListOf<File>()
    runCatching { Environment.getExternalStorageDirectory()?.canonicalFile }
        .getOrNull()?.let { allowedDirs.add(it) }
    runCatching { context.getExternalFilesDir(null)?.canonicalFile }
        .getOrNull()?.let { allowedDirs.add(it) }
    runCatching { context.getExternalCacheDir()?.canonicalFile }
        .getOrNull()?.let { allowedDirs.add(it) }
    runCatching { context.filesDir.canonicalFile }
        .getOrNull()?.let { allowedDirs.add(it) }
    runCatching { context.cacheDir.canonicalFile }
        .getOrNull()?.let { allowedDirs.add(it) }
    // Also allow external storage public directories (Downloads, Documents, ...)
    runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.canonicalFile }
        .getOrNull()?.let { allowedDirs.add(it) }
    runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.canonicalFile }
        .getOrNull()?.let { allowedDirs.add(it) }

    val isAllowed = allowedDirs.any { dir ->
        val dirPath = dir.absolutePath
        filePath == dirPath || filePath.startsWith(dirPath + File.separator)
    }
    return if (isAllowed) file else null
}

/**
 * Tool that reads the contents of a text file.
 *
 * Parameters:
 *   - path (required) absolute path to the file
 *
 * Security: only files within external storage or app-specific directories can be read.
 * Maximum file size: 1 MB.
 */
class FileReadTool(private val context: Context) : McpTool {

    override val name: String = "read_file"

    override val description: String =
        "Reads the contents of a text file. Only files within external storage or " +
            "app-specific directories are allowed. Maximum size 1 MB."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("path", JSONObject().apply {
                put("type", "string")
                put("description", "Absolute path to the file to read.")
            })
        })
        put("required", JSONArray().put("path"))
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val path = params.optString("path", "")
            if (path.isEmpty()) {
                return@withContext ToolResult.err("Missing required parameter: path")
            }

            val file = resolveSafePath(context, path)
                ?: return@withContext ToolResult.err(
                    "Access denied: path is outside the allowed directories"
                )

            if (!file.exists()) {
                return@withContext ToolResult.err("File not found: $path")
            }
            if (!file.isFile) {
                return@withContext ToolResult.err("Path is not a file: $path")
            }
            if (file.length() > MAX_FILE_SIZE) {
                return@withContext ToolResult.err(
                    "File too large: ${file.length()} bytes (maximum allowed is $MAX_FILE_SIZE bytes / 1MB)"
                )
            }
            if (!file.canRead()) {
                return@withContext ToolResult.err("File is not readable: $path")
            }

            val content = file.readText(Charsets.UTF_8)
            val result = JSONObject().apply {
                put("path", file.absolutePath)
                put("name", file.name)
                put("size", file.length())
                put("lastModified", file.lastModified())
                put("content", content)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to read file: ${e.message}")
        }
    }

    companion object {
        private const val MAX_FILE_SIZE: Long = 1024L * 1024L // 1 MB
    }
}

/**
 * Tool that lists the contents of a directory.
 *
 * Parameters:
 *   - path (required) absolute path to the directory
 *
 * Security: only directories within external storage or app-specific directories can be listed.
 */
class FileListTool(private val context: Context) : McpTool {

    override val name: String = "list_directory"

    override val description: String =
        "Lists the contents of a directory. Only directories within external storage or " +
            "app-specific directories are allowed."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("path", JSONObject().apply {
                put("type", "string")
                put("description", "Absolute path to the directory to list.")
            })
        })
        put("required", JSONArray().put("path"))
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val path = params.optString("path", "")
            if (path.isEmpty()) {
                return@withContext ToolResult.err("Missing required parameter: path")
            }

            val file = resolveSafePath(context, path)
                ?: return@withContext ToolResult.err(
                    "Access denied: path is outside the allowed directories"
                )

            if (!file.exists()) {
                return@withContext ToolResult.err("Directory not found: $path")
            }
            if (!file.isDirectory) {
                return@withContext ToolResult.err("Path is not a directory: $path")
            }
            if (!file.canRead()) {
                return@withContext ToolResult.err("Directory is not readable: $path")
            }

            val children = file.listFiles()
                ?: return@withContext ToolResult.err("Unable to list directory contents")

            val items = JSONArray()
            children.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .forEach { child ->
                    items.put(JSONObject().apply {
                        put("name", child.name)
                        put("path", child.absolutePath)
                        put("isDirectory", child.isDirectory)
                        put("isFile", child.isFile)
                        put("size", if (child.isFile) child.length() else 0L)
                        put("lastModified", child.lastModified())
                        put("readable", child.canRead())
                        put("writable", child.canWrite())
                        put("hidden", child.isHidden)
                    })
                }

            val result = JSONObject().apply {
                put("path", file.absolutePath)
                put("count", children.size)
                put("items", items)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to list directory: ${e.message}")
        }
    }
}
