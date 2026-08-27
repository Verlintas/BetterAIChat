package com.betteraichat.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())

private val CODE_BLOCK_REGEX = Regex("```[^`\\n]*\\n([\\s\\S]*?)```")
private val LINK_REGEX = Regex("\\[([^\\]]*)\\]\\(((?:https?|ftp)://[^\\s)]+)\\)")

private fun extractCodeBlocks(content: String): List<String> =
    CODE_BLOCK_REGEX.findAll(content).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()

private fun extractLinks(content: String): List<String> =
    LINK_REGEX.findAll(content).map { it.groupValues[2].trimEnd(')') }.distinct().toList()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    msg: UiMessage,
    modifier: Modifier = Modifier,
    onDelete: (Long) -> Unit,
    onSpeak: (String) -> Unit = {},
    onEdit: (Long) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    onToggleStar: (Long) -> Unit = {},
    onCopied: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null
) {
    if (msg.role == ChatRole.TOOL) return
    val clipboard = LocalClipboardManager.current
    var showActions by remember { mutableStateOf(false) }
    val copyAction = {
        if (msg.content.isNotBlank()) {
            clipboard.setText(AnnotatedString(msg.content))
            onCopied?.invoke()
        }
        showActions = false
    }
    val onLongPress = { if (msg.id > 0 && !msg.streaming) showActions = true }
    if (msg.role == ChatRole.USER) {
        UserBubble(msg, onLongPress, modifier)
        if (showActions) {
            MessageActionsDialog(
                content = msg.content,
                starred = msg.starred,
                onCopy = copyAction,
                onSpeak = { onSpeak(msg.content) },
                onStar = {
                    showActions = false
                    onToggleStar(msg.id)
                },
                onEdit = {
                    showActions = false
                    onEdit(msg.id)
                },
                onDelete = {
                    showActions = false
                    onDelete(msg.id)
                },
                onDismiss = { showActions = false }
            )
        }
        return
    }
    Row(modifier = modifier.fillMaxWidth()) {
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
            if (msg.content.isNotEmpty() || msg.toolCalls.isEmpty()) {
                Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (msg.streaming && msg.content.isEmpty()) {
                        ThinkingDots()
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
                                stripCodeBlocks(msg.content) + if (msg.streaming) "▋" else "",
                                modifier = Modifier.fillMaxWidth(),
                                typography = markdownTypography(
                                    h1 = androidx.compose.ui.text.TextStyle(
                                        fontSize = 18.sp,
                                        lineHeight = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    h2 = androidx.compose.ui.text.TextStyle(
                                        fontSize = 16.sp,
                                        lineHeight = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    h3 = androidx.compose.ui.text.TextStyle(
                                        fontSize = 15.sp,
                                        lineHeight = 21.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    h4 = androidx.compose.ui.text.TextStyle(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    h5 = androidx.compose.ui.text.TextStyle(
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    h6 = androidx.compose.ui.text.TextStyle(
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    text = androidx.compose.ui.text.TextStyle(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    ),
                                    paragraph = androidx.compose.ui.text.TextStyle(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    ),
                                    bullet = androidx.compose.ui.text.TextStyle(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    ),
                                    ordered = androidx.compose.ui.text.TextStyle(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    ),
                                    list = androidx.compose.ui.text.TextStyle(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    ),
                                    quote = androidx.compose.ui.text.TextStyle(
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
                                    ),
                                    code = androidx.compose.ui.text.TextStyle(
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    ),
                                    inlineCode = androidx.compose.ui.text.TextStyle(
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
                                    )
                                ),
                                components = markdownComponents(
                                    table = { model ->
                                        MarkdownTable(model.content)
                                    }
                                )
                            )
                        }
                    }
                }
            }
            }
            msg.toolCalls.forEachIndexed { index, call ->
                Spacer(Modifier.size(6.dp))
                ToolCallCard(call, stepNumber = index + 1)
            }
            if (msg.streaming && msg.toolCalls.isNotEmpty()) {
                val done = msg.toolCalls.count {
                    it.status == ToolCallStatus.DONE || it.status == ToolCallStatus.FAILED
                }
                val running = msg.toolCalls.count { it.status == ToolCallStatus.RUNNING }
                Spacer(Modifier.size(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Text(
                        "执行步骤：已完成 $done / ${msg.toolCalls.size}${if (running > 0) "，执行中…" else ""}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            val codeBlocks = extractCodeBlocks(msg.content)
            if (codeBlocks.isNotEmpty() && !msg.streaming) {
                codeBlocks.forEach { code ->
                    Spacer(Modifier.size(6.dp))
                    HighlightedCodeCard(
                        code = code,
                        onCopy = { clipboard.setText(AnnotatedString(code)) }
                    )
                }
            }
            val links = extractLinks(msg.content)
            if (links.isNotEmpty() && !msg.streaming) {
                Spacer(Modifier.size(4.dp))
                links.take(3).forEach { link ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { onOpenLink(link) }
                    ) {
                        Text(
                            "打开链接：${link.take(40)}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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
            starred = msg.starred,
            onCopy = copyAction,
            onSpeak = { onSpeak(msg.content) },
            onStar = {
                showActions = false
                onToggleStar(msg.id)
            },
            onEdit = null,
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
    starred: Boolean,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onStar: () -> Unit,
    onEdit: (() -> Unit)?,
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
                TextButton(onClick = onSpeak) { Text("朗读") }
                TextButton(onClick = onStar) { Text(if (starred) "取消收藏" else "收藏") }
                if (onEdit != null) {
                    TextButton(onClick = onEdit) { Text("编辑") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
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
private fun UserBubble(msg: UiMessage, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
                            if (att.isImage && att.dataBase64.isNotBlank()) {
                                val bitmap = remember(att.dataBase64) {
                                    runCatching {
                                        val bytes = android.util.Base64.decode(att.dataBase64, android.util.Base64.DEFAULT)
                                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    }.getOrNull()
                                }
                                if (bitmap != null) {
                                    val w = (bitmap.width * 140f / bitmap.height).toInt().coerceIn(80, 200)
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = att.name,
                                        modifier = Modifier
                                            .fillMaxWidth(0.6f)
                                            .height((140 * bitmap.width / bitmap.height.toFloat()).dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(Modifier.size(4.dp))
                                }
                            } else {
                                Text(
                                    if (att.isImage) "图片：${att.name}" else "文件：${att.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
private fun ToolCallCard(call: ToolCall, stepNumber: Int = 0) {
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
                if (stepNumber > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "第 $stepNumber 步",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
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
                var expanded by remember { mutableStateOf(false) }
                Spacer(Modifier.size(4.dp))
                Text(
                    result,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis
                )
                if (result.length > 180 && !expanded) {
                    TextButton(
                        onClick = { expanded = true },
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text("展开全部（${result.length} 字）", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (expanded) {
                    TextButton(
                        onClick = { expanded = false },
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text("收起", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ToolCallStatus) {
    val (text, target) = when (status) {
        ToolCallStatus.PENDING -> "等待" to MaterialTheme.colorScheme.onSurfaceVariant
        ToolCallStatus.RUNNING -> "执行中…" to MaterialTheme.colorScheme.primary
        ToolCallStatus.DONE -> "已完成" to Color(0xFF2E7D32)
        ToolCallStatus.FAILED -> "失败" to MaterialTheme.colorScheme.error
        ToolCallStatus.REJECTED -> "已拒绝" to MaterialTheme.colorScheme.error
        ToolCallStatus.DENIED -> "已禁止" to MaterialTheme.colorScheme.error
    }
    val color by androidx.compose.animation.animateColorAsState(
        targetValue = target,
        animationSpec = androidx.compose.animation.core.tween(250),
        label = "status"
    )
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


private val CODE_BLOCK_STRIP_REGEX = Regex("```[^`\n]*\n[\\s\\S]*?```")

private fun stripCodeBlocks(content: String): String =
    CODE_BLOCK_STRIP_REGEX.replace(content, "````")

@Composable
private fun HighlightedCodeCard(code: String, onCopy: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "代码",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CDCFE)
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCopy) {
                    Text("复制", color = Color(0xFF9CDCFE))
                }
            }
            Text(
                MiniHighlighter.highlight(code),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 30,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val TABLE_SEPARATOR_REGEX = Regex("^\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*$")

private fun parseMarkdownTable(md: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    md.lines().forEach { line ->
        val trimmed = line.trim()
        if (!trimmed.startsWith("|")) return@forEach
        val cells = trimmed.trim('|').split("|").map { it.trim() }
        if (cells.isEmpty()) return@forEach
        if (cells.all { TABLE_SEPARATOR_REGEX.matches(it) }) return@forEach
        rows.add(cells)
    }
    return rows
}

@Composable
private fun MarkdownTable(md: String, modifier: Modifier = Modifier) {
    val rows = remember(md) { parseMarkdownTable(md) }
    if (rows.isEmpty()) return
    val shape = RoundedCornerShape(10.dp)
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        rows.forEachIndexed { ri, row ->
            if (ri > 0) {
                HorizontalDivider(color = borderColor.copy(alpha = 0.6f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (ri == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        else Color.Transparent
                    )
            ) {
                row.forEachIndexed { ci, cell ->
                    if (ci > 0) {
                        VerticalDivider(color = borderColor.copy(alpha = 0.6f))
                    }
                    Text(
                        cell,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        style = if (ri == 0) {
                            MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 20
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val delays = listOf(0, 180, 360)
    Row(verticalAlignment = Alignment.CenterVertically) {
        delays.forEach { delayMs ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(delayMs)
                ),
                label = "dot"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(7.dp)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text("AI 思考中", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
