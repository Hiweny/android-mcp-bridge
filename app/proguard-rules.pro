# Keep MCP tool classes
-keep class com.hiweny.mcpbridge.tools.** { *; }
-keep class com.hiweny.mcpbridge.mcp.** { *; }

# Keep NanoHTTPD
-keep class org.nanohttpd.** { *; }
-keep class fi.iki.elonen.** { *; }

# Keep JSON models
-keepclassmembers,allowobfuscation class * {
  @kotlinx.serialization.Serializable *;
}
