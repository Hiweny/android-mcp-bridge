package com.hiweny.mcpbridge.ui.screens

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiweny.mcpbridge.ui.components.StatusCard
import com.hiweny.mcpbridge.ui.theme.BlueAccent
import com.hiweny.mcpbridge.ui.theme.StatusRunning
import com.hiweny.mcpbridge.ui.theme.StatusStopped
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    isRunning: Boolean,
    port: Int,
    ipAddress: String,
    toolCount: Int,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onNavigateTools: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionUrl = "http://$ipAddress:$port"
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var uptimeSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            uptimeSeconds = 0L
            while (true) {
                delay(1000)
                uptimeSeconds += 1
            }
        } else {
            uptimeSeconds = 0L
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "MCP Bridge",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // 状态卡片
        StatusCard(
            isRunning = isRunning,
            port = port,
            ipAddress = ipAddress
        )

        // 启动 / 停止按钮
        Crossfade(
            targetState = isRunning,
            animationSpec = tween(300),
            label = "server_button"
        ) { running ->
            if (running) {
                Button(
                    onClick = onStopClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusStopped,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("停止服务", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Button(
                    onClick = onStartClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("启动服务", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // 快速统计
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                icon = Icons.Filled.Build,
                label = "工具数量",
                value = toolCount.toString(),
                accent = BlueAccent,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Filled.Timer,
                label = "运行时长",
                value = formatUptime(uptimeSeconds),
                accent = StatusRunning,
                modifier = Modifier.weight(1f)
            )
        }

        // 连接地址展示与复制
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "连接地址",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = connectionUrl,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(connectionUrl))
                        Toast.makeText(context, "已复制连接地址", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    )
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "复制地址",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("复制", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 导航按钮：工具 & 日志 & 设置
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = onNavigateTools,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("工具", style = MaterialTheme.typography.labelLarge)
            }
            FilledTonalButton(
                onClick = onNavigateLogs,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("日志", style = MaterialTheme.typography.labelLarge)
            }
        }

        FilledTonalButton(
            onClick = onNavigateSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("设置", style = MaterialTheme.typography.labelLarge)
        }

        // 使用说明
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "使用说明",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "1. 启动服务后，在 DeepSeek 网页端安装油猴脚本\n2. 脚本会自动连接到本服务器\n3. AI 调用工具后结果会自动返回\n4. 支持多轮工具调用，无需手动发送",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}
