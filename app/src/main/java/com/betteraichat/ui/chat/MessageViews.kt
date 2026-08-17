package com.betteraichat.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betteraichat.core.model.ChatRole
import com.betteraichat.core.model.ToolCall
import com.betteraichat.core.model.ToolCallStatus
import com.mikepenz.markdown.m3.Markdown
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(msg: UiMessage, onDelete: (Long) -> Unit) {
    if (msg.role == ChatRole.TOOL) return
    val clipboard = LocalClipboardManager.current
    var showActions by remember { mutableStateOf(false) }
    val copyAction = {
        if (msg.content.isNotBlank()) {
            clipboard.setText(AnnotatedString(msg.content))
        }
        showActions = false
    }
    val onLongPress = { if (msg.id > 0 && !msg.streaming) showActions = true }
    if (msg.role == ChatRole.USER) {
        UserBubble(msg, onLongPress)
        if (showActions) {
            MessageActionsDialog(
                content = msg.content,
                onCopy = copyAction,
                onDelete = {
                    showActions = false
                    onDelete(msg.id)
                },
                onDismiss = { showActions = false }
            )
        }
        return
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        AiAvatar()
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        msg.model?.let { append(it) }
                        msg.mode?.let { m ->
                            if (isNotEmpty()) append(" · ")
                            append(m.displayName)
                        }
                        if (isEmpty()) append("BetterAIChat")
                        if (msg.createdAt > 0) {
                            append(" · ")
                            append(TIME_FORMAT.format(Date(msg.createdAt)))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(4.dp))
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .combinedClickable(onClick = {}, onLongClick = copyAction)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (msg.streaming && msg.content.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("AI 思考中…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        val blinkAlpha = if (msg.streaming) {
                            val transition = rememberInfiniteTransition(label = "cursor")
                            transition.animateFloat(
                                initialValue = 0.7f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                                label = "cursorAlpha"
                            ).value
                        } else 1f
                        Box(modifier = Modifier.alpha(blinkAlpha)) {
                            Markdown(
                                msg.content + if (msg.streaming) "▋" else "",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            msg.toolCalls.forEach { call ->
                Spacer(Modifier.size(6.dp))
                ToolCallCard(call)
            }
            if (msg.thinking.isNotBlank()) {
                Spacer(Modifier.size(6.dp))
                ThinkingCard(msg.thinking)
            }
        }
    }
    if (showActions) {
        MessageActionsDialog(
            content = msg.content,
            onCopy = copyAction,
            onDelete = {
                showActions = false
                onDelete(msg.id)
            },
            onDismiss = { showActions = false }
        )
    }
}

@Composable
private fun MessageActionsDialog(
    content: String,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("消息操作") },
        text = {
            Text(
                content.take(120) + if (content.length > 120) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onCopy) { Text("复制") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

@Composable
private fun ThinkingCard(thinking: String) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { expanded = !expanded }, onLongClick = { })
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "思考过程",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                Spacer(Modifier.size(6.dp))
                Text(
                    thinking,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Default,
                    maxLines = 200
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(msg: UiMessage, onLongPress: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(horizontalAlignment = Alignment.End) {
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (msg.attachments.isNotEmpty()) {
                        msg.attachments.forEach { att ->
                            Text(
                                if (att.isImage) "图片：${att.name}" else "文件：${att.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.size(4.dp))
                    }
                    Text(
                        msg.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            if (msg.createdAt > 0) {
                Text(
                    TIME_FORMAT.format(Date(msg.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AiAvatar() {
    Box(
        modifier = Modifier
            .size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "AI",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
