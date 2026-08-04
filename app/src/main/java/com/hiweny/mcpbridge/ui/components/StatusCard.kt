package com.hiweny.mcpbridge.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiweny.mcpbridge.ui.theme.BlueAccent
import com.hiweny.mcpbridge.ui.theme.BlueAccentLight
import com.hiweny.mcpbridge.ui.theme.NavyBlue
import com.hiweny.mcpbridge.ui.theme.NavyDark
import com.hiweny.mcpbridge.ui.theme.NavyLight
import com.hiweny.mcpbridge.ui.theme.StatusRunning
import com.hiweny.mcpbridge.ui.theme.StatusStopped

/**
 * 高级感的服务器状态卡片。
 *
 * 运行时使用深蓝渐变背景，并带绿色脉冲指示点；
 * 停止时使用静态暗色背景，红色指示点。
 */
@Composable
fun StatusCard(
    isRunning: Boolean,
    port: Int,
    ipAddress: String,
    modifier: Modifier = Modifier
) {
    val connectionUrl = "http://$ipAddress:$port"

    // 运行中的渐变背景
    val runningGradient = Brush.linearGradient(
        colors = listOf(NavyLight, NavyBlue, NavyDark)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isRunning) runningGradient else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = if (isRunning) BlueAccent.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIndicatorDot(isRunning = isRunning)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (isRunning) "服务运行中" else "服务已停止",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isRunning) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (isRunning) Icons.Filled.Wifi else Icons.Filled.PowerSettingsNew,
                    contentDescription = null,
                    tint = if (isRunning) StatusRunning else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Lan,
                    contentDescription = null,
                    tint = if (isRunning) BlueAccentLight else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "连接地址",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isRunning) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = connectionUrl,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isRunning) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatusMetaItem(
                    label = "端口",
                    value = port.toString(),
                    isRunning = isRunning
                )
                StatusMetaItem(
                    label = "IP 地址",
                    value = ipAddress,
                    isRunning = isRunning
                )
            }
        }
    }
}

@Composable
private fun StatusMetaItem(
    label: String,
    value: String,
    isRunning: Boolean
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isRunning) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isRunning) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 状态指示点：运行时绿色脉冲动画，停止时红色静态。
 */
@Composable
private fun StatusIndicatorDot(isRunning: Boolean) {
    val transition = rememberInfiniteTransition(label = "status_dot")
    val pulseScale by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
        if (isRunning) {
            // 外圈扩散光晕
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .scale(pulseScale)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(StatusRunning.copy(alpha = 0.5f))
            )
        }
        // 中心实心点
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isRunning) StatusRunning else StatusStopped)
        )
    }
}
