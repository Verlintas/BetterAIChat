package com.betteraichat.ui.chat

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.betteraichat.core.model.ChatRole
import com.betteraichat.core.model.ToolCall
import com.betteraichat.core.model.ToolCallStatus

@Composable
fun MessageItem(msg: UiMessage) {
    if (msg.role == ChatRole.USER) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    msg.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row {
            Text(
                buildString {
                    msg.model?.let { append(it) }
                    msg.mode?.let { m ->
                        if (isNotEmpty()) append(" · ")
                        append(m.displayName)
                    }
                    if (isEmpty()) append("BetterAIChat")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(4.dp))
        if (msg.streaming && msg.content.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("AI 思考中…", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            com.mikepenz.markdown.m3.Markdown(
                msg.content + if (msg.streaming) "▋" else "",
                modifier = Modifier.fillMaxWidth()
            )
        }
        msg.toolCalls.forEach { call ->
            Spacer(Modifier.size(6.dp))
            ToolCallCard(call)
        }
    }
}

@Composable
private fun ToolCallCard(call: ToolCall) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(call.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                StatusBadge(call.status)
            }
            if (call.arguments.isNotBlank() && call.arguments != "{}") {
                Text(
                    call.arguments,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val result = call.result
            if (!result.isNullOrBlank()) {
                Spacer(Modifier.size(4.dp))
                Text(
                    result,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ToolCallStatus) {
    val (text, color) = when (status) {
        ToolCallStatus.PENDING -> "等待" to MaterialTheme.colorScheme.onSurfaceVariant
        ToolCallStatus.RUNNING -> "执行中…" to MaterialTheme.colorScheme.primary
        ToolCallStatus.DONE -> "已完成" to Color(0xFF2E7D32)
        ToolCallStatus.FAILED -> "失败" to MaterialTheme.colorScheme.error
        ToolCallStatus.REJECTED -> "已拒绝" to MaterialTheme.colorScheme.error
        ToolCallStatus.DENIED -> "已禁止" to MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
