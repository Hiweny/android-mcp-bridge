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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

/**
 * Resolves [path] to a canonical [File] only if it falls inside one of the allowed
 * directories (external storage, app-specific directories and public Downloads/Documents).
 * Returns null otherwise. This is a file-private copy of the check used in [FileTool.kt]
 * so that this file remains self-contained.
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
    runCatching {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.canonicalFile
    }.getOrNull()?.let { allowedDirs.add(it) }
    runCatching {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.canonicalFile
    }.getOrNull()?.let { allowedDirs.add(it) }

    val isAllowed = allowedDirs.any { dir ->
        val dirPath = dir.absolutePath
        filePath == dirPath || filePath.startsWith(dirPath + File.separator)
    }
    return if (isAllowed) file else null
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
 * Tool that writes text content to a file.
 *
 * Parameters:
 *   - path    (required) absolute path to the file
 *   - content (required) text content to write (overwrites existing content)
 *
 * Security: only paths within external storage or app-specific directories are allowed.
 * Parent directories are created automatically. Maximum content size: 1 MB.
 */
class FileWriteTool(private val context: Context) : McpTool {

    override val name: String = "write_file"

    override val description: String =
        "Writes text content to a file, overwriting any existing content. Parent directories " +
            "are created automatically. Only paths within external storage or app-specific " +
            "directories are allowed. Maximum size 1 MB."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("path", JSONObject().apply {
                put("type", "string")
                put("description", "Absolute path to the file to write.")
            })
            put("content", JSONObject().apply {
                put("type", "string")
                put("description", "Text content to write to the file. Overwrites existing content.")
            })
        })
        put("required", JSONArray().apply {
            put("path")
            put("content")
        })
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            if (!params.has("path")) {
                return@withContext ToolResult.err("Missing required parameter: path")
            }
            if (!params.has("content")) {
                return@withContext ToolResult.err("Missing required parameter: content")
            }

            val path = params.optString("path", "")
            if (path.isEmpty()) {
                return@withContext ToolResult.err("Parameter 'path' must not be empty")
            }
            val content = params.optString("content", "")

            val contentBytes = content.toByteArray(Charsets.UTF_8)
            if (contentBytes.size > MAX_CONTENT_BYTES) {
                return@withContext ToolResult.err(
                    "Content too large: ${contentBytes.size} bytes " +
                        "(maximum allowed is $MAX_CONTENT_BYTES bytes / 1MB)"
                )
            }

            val file = resolveSafePath(context, path)
                ?: return@withContext ToolResult.err(
                    "Access denied: path is outside the allowed directories"
                )

            // Auto-create parent directories
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    return@withContext ToolResult.err(
                        "Failed to create parent directories: ${parent.absolutePath}"
                    )
                }
            }

            file.writeText(content, Charsets.UTF_8)

            val result = JSONObject().apply {
                put("path", file.absolutePath)
                put("size", file.length())
                put("sizeHuman", humanReadableSize(file.length()))
                put("bytesWritten", contentBytes.size)
                put("lastModified", file.lastModified())
                put("written", true)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to write file: ${e.message}")
        }
    }

    companion object {
        private const val MAX_CONTENT_BYTES = 1024 * 1024 // 1 MB
    }
}

/**
 * Tool that deletes a file or an empty directory.
 *
 * Parameters:
 *   - path (required) absolute path to the file or empty directory
 *
 * Security: only paths within external storage or app-specific directories are allowed.
 * Non-empty directories are not deleted (use recursive deletion externally if needed).
 */
class FileDeleteTool(private val context: Context) : McpTool {

    override val name: String = "delete_file"

    override val description: String =
        "Deletes a file or an empty directory. Only paths within external storage or " +
            "app-specific directories are allowed. Non-empty directories will not be deleted."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("path", JSONObject().apply {
                put("type", "string")
                put("description", "Absolute path to the file or empty directory to delete.")
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
                return@withContext ToolResult.err("Path not found: $path")
            }

            if (file.isDirectory) {
                val children = file.list()
                if (children != null && children.isNotEmpty()) {
                    return@withContext ToolResult.err(
                        "Directory is not empty (contains ${children.size} entries): ${file.absolutePath}"
                    )
                }
            }

            val deleted = file.delete()
            if (!deleted) {
                return@withContext ToolResult.err("Failed to delete: ${file.absolutePath}")
            }

            val result = JSONObject().apply {
                put("path", file.absolutePath)
                put("wasDirectory", file.isDirectory)
                put("deleted", true)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to delete file: ${e.message}")
        }
    }
}

/**
 * Tool that copies a file from source to destination.
 *
 * Parameters:
 *   - source      (required) absolute path to the source file
 *   - destination (required) absolute path to the destination file
 *
 * Security: both paths must be within allowed directories.
 * Parent directories of the destination are created automatically.
 */
class FileCopyTool(private val context: Context) : McpTool {

    override val name: String = "copy_file"

    override val description: String =
        "Copies a file from source to destination. Parent directories for the destination are " +
            "created automatically. Only paths within external storage or app-specific " +
            "directories are allowed."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("source", JSONObject().apply {
                put("type", "string")
                put("description", "Absolute path to the source file.")
            })
            put("destination", JSONObject().apply {
                put("type", "string")
                put("description", "Absolute path to the destination file.")
            })
        })
        put("required", JSONArray().apply {
            put("source")
            put("destination")
        })
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val sourcePath = params.optString("source", "")
            val destPath = params.optString("destination", "")
            if (sourcePath.isEmpty()) {
                return@withContext ToolResult.err("Missing required parameter: source")
            }
            if (destPath.isEmpty()) {
                return@withContext ToolResult.err("Missing required parameter: destination")
            }

            val source = resolveSafePath(context, sourcePath)
                ?: return@withContext ToolResult.err(
                    "Access denied: source path is outside the allowed directories"
                )
            val destination = resolveSafePath(context, destPath)
                ?: return@withContext ToolResult.err(
                    "Access denied: destination path is outside the allowed directories"
                )

            if (!source.exists()) {
                return@withContext ToolResult.err("Source file not found: $sourcePath")
            }
            if (!source.isFile) {
                return@withContext ToolResult.err("Source path is not a file: $sourcePath")
            }
            if (!source.canRead()) {
                return@withContext ToolResult.err("Source file is not readable: $sourcePath")
            }
            if (destination.isDirectory) {
                return@withContext ToolResult.err("Destination is an existing directory: $destPath")
            }

            // Auto-create parent directories for destination
            val parent = destination.parentFile
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    return@withContext ToolResult.err(
                        "Failed to create parent directories: ${parent.absolutePath}"
                    )
                }
            }

            FileInputStream(source).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }

            val result = JSONObject().apply {
                put("source", source.absolutePath)
                put("destination", destination.absolutePath)
                put("size", destination.length())
                put("sizeHuman", humanReadableSize(destination.length()))
                put("copied", true)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to copy file: ${e.message}")
        }
    }
}

/**
 * Tool that searches for files whose names contain a query string.
 *
 * Parameters:
 *   - directory (required) absolute path to the directory to search in
 *   - query     (required) substring to match file/directory names (case-insensitive)
 *   - recursive (optional, default false) whether to search subdirectories
 *
 * Security: only directories within allowed directories can be searched.
 * Results are capped to 200 entries.
 */
class FileSearchTool(private val context: Context) : McpTool {

    override val name: String = "search_files"

    override val description: String =
        "Searches for files and directories whose names contain the query string " +
            "(case-insensitive) within a directory. Supports recursive search. Only paths " +
            "within external storage or app-specific directories are allowed."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("directory", JSONObject().apply {
                put("type", "string")
                put("description", "Absolute path to the directory to search in.")
            })
            put("query", JSONObject().apply {
                put("type", "string")
                put("description", "Substring to match file/directory names against (case-insensitive).")
            })
            put("recursive", JSONObject().apply {
                put("type", "boolean")
                put("description", "Whether to search recursively into subdirectories.")
                put("default", false)
            })
        })
        put("required", JSONArray().apply {
            put("directory")
            put("query")
        })
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val directoryPath = params.optString("directory", "")
            if (directoryPath.isEmpty()) {
                return@withContext ToolResult.err("Missing required parameter: directory")
            }
            val query = params.optString("query", "")
            if (query.isEmpty()) {
                return@withContext ToolResult.err("Missing required parameter: query")
            }
            val recursive = params.optBoolean("recursive", false)

            val dir = resolveSafePath(context, directoryPath)
                ?: return@withContext ToolResult.err(
                    "Access denied: directory path is outside the allowed directories"
                )

            if (!dir.exists()) {
                return@withContext ToolResult.err("Directory not found: $directoryPath")
            }
            if (!dir.isDirectory) {
                return@withContext ToolResult.err("Path is not a directory: $directoryPath")
            }
            if (!dir.canRead()) {
                return@withContext ToolResult.err("Directory is not readable: $directoryPath")
            }

            val queryLower = query.lowercase()
            val matches = JSONArray()
            var count = 0
            var truncated = false

            val walk = dir.walkTopDown().let { if (recursive) it else it.maxDepth(1) }
            for (file in walk) {
                if (file == dir) continue // skip the root itself
                if (file.name.lowercase().contains(queryLower)) {
                    if (count >= MAX_RESULTS) {
                        truncated = true
                        break
                    }
                    matches.put(JSONObject().apply {
                        put("name", file.name)
                        put("path", file.absolutePath)
                        put("isDirectory", file.isDirectory)
                        put("isFile", file.isFile)
                        put("size", if (file.isFile) file.length() else 0L)
                        put("sizeHuman", if (file.isFile) humanReadableSize(file.length()) else "0 B")
                        put("lastModified", file.lastModified())
                    })
                    count++
                }
            }

            val result = JSONObject().apply {
                put("directory", dir.absolutePath)
                put("query", query)
                put("recursive", recursive)
                put("count", count)
                put("matches", matches)
                put("truncated", truncated)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to search files: ${e.message}")
        }
    }

    companion object {
        private const val MAX_RESULTS = 200
    }
}

/**
 * Tool that returns detailed information about a file or directory.
 *
 * Parameters:
 *   - path (required) absolute path to the file or directory
 *
 * Security: only paths within allowed directories can be inspected.
 */
class FileInfoTool(private val context: Context) : McpTool {

    override val name: String = "get_file_info"

    override val description: String =
        "Returns detailed information about a file or directory: type, size, dates, " +
            "permissions and extension. Only paths within external storage or app-specific " +
            "directories are allowed."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("path", JSONObject().apply {
                put("type", "string")
                put("description", "Absolute path to the file or directory.")
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
                return@withContext ToolResult.err("Path not found: $path")
            }

            val result = JSONObject().apply {
                put("name", file.name)
                put("path", file.absolutePath)
                put("canonicalPath", file.canonicalPath)
                put("parent", file.parent ?: "")
                put("isFile", file.isFile)
                put("isDirectory", file.isDirectory)
                put("isHidden", file.isHidden)
                put("size", file.length())
                put("sizeHuman", if (file.isFile) humanReadableSize(file.length()) else "0 B")
                put("lastModified", file.lastModified())
                put("readable", file.canRead())
                put("writable", file.canWrite())
                put("executable", file.canExecute())
                put("extension", file.extension)
                put("nameWithoutExtension", file.nameWithoutExtension)
                if (file.isDirectory) {
                    val children = file.listFiles()
                    put("childCount", children?.size ?: 0)
                    put("freeSpace", file.freeSpace)
                    put("freeSpaceHuman", humanReadableSize(file.freeSpace))
                    put("totalSpace", file.totalSpace)
                    put("totalSpaceHuman", humanReadableSize(file.totalSpace))
                    put("usableSpace", file.usableSpace)
                    put("usableSpaceHuman", humanReadableSize(file.usableSpace))
                }
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to get file info: ${e.message}")
        }
    }
}
