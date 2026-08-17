package com.betteraichat.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betteraichat.core.catalog.ModelCatalog
import com.betteraichat.core.mode.AppMode
import com.betteraichat.core.model.ToolCall
import com.betteraichat.core.model.ToolCallStatus
import com.betteraichat.ui.rememberContainer
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(conversationId: Long, onBack: () -> Unit) {
    val container = rememberContainer()
    val vm: ChatViewModel = viewModel(
        key = "chat_$conversationId",
        factory = ChatViewModelFactory(conversationId, container)
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val last = state.messages.lastOrNull()

    val totalPromptTokens = state.messages.sumOf { it.usageInput }
    val contextWindow = ModelCatalog.entryFor(state.provider, state.model).contextWindow
    val usagePercent = if (contextWindow > 0 && totalPromptTokens > 0) {
        ((totalPromptTokens * 100) / contextWindow).toInt().coerceIn(0, 100)
    } else 0
    val usageText = if (totalPromptTokens > 0) {
        " · ${formatTokens(totalPromptTokens)}（$usagePercent%）"
    } else ""

    LaunchedEffect(state.messages.size, last?.content?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${state.model} · ${state.mode.displayName}$usageText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    ModeSelector(state.mode, vm::updateMode)
                    ModelSelector(state.provider, state.model, vm::updateModel)
                }
            )
        },
        bottomBar = {
            InputBar(
                input = state.input,
                isRunning = state.isRunning,
                onInputChange = vm::onInputChange,
                onSend = vm::send,
                onStop = vm::stop
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "模式：${state.mode.displayName} — ${state.mode.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(state.messages.size, key = { state.messages[it].id }) { idx ->
                MessageItem(state.messages[idx])
            }
            state.error?.let { error ->
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            "出错了：$error",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }

    state.confirmRequest?.let { req ->
        AlertDialog(
            onDismissRequest = { vm.respondConfirm(false) },
            title = { Text("AI 请求执行设备工具") },
            text = {
                Column {
                    Text("工具：${req.call.name}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(8.dp))
                    Text("参数：", style = MaterialTheme.typography.bodySmall)
                    Text(
                        prettyJson(req.call.arguments),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "当前模式：${req.mode.displayName}。确认执行吗？",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.respondConfirm(true) }) { Text("允许") }
            },
            dismissButton = {
                TextButton(onClick = { vm.respondConfirm(false) }) { Text("拒绝") }
            }
        )
    }
}

@Composable
private fun InputBar(
    input: String,
    isRunning: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("输入消息…") },
            maxLines = 6
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = { if (isRunning) onStop() else onSend() }) {
            if (isRunning) {
                Icon(Icons.Filled.Close, contentDescription = "停止")
            } else {
                Icon(Icons.Filled.Send, contentDescription = "发送")
            }
        }
    }
}

@Composable
private fun ModeSelector(current: AppMode, onSelect: (AppMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(current.displayName)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(mode.displayName, fontWeight = if (mode == current) FontWeight.Bold else FontWeight.Normal)
                            Text(mode.description, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelSelector(
    provider: com.betteraichat.core.model.ProviderId,
    current: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var customInput by remember { mutableStateOf("") }
    val catalogIds = remember(provider) { ModelCatalog.modelsFor(provider).map { it.id } }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(current.take(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ModelCatalog.modelsFor(provider).forEach { entry ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(entry.label)
                            Text(entry.id, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = {
                        onSelect(entry.id)
                        expanded = false
                    }
                )
            }
            if (current !in catalogIds) {
                DropdownMenuItem(
                    text = { Text("$current（自定义）") },
                    onClick = {
                        onSelect(current)
                        expanded = false
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("自定义模型…") },
                onClick = {
                    customInput = current
                    expanded = false
                    showCustom = true
                }
            )
        }
    }
    if (showCustom) {
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text("自定义模型") },
            text = {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it },
                    label = { Text("模型 ID") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customInput.isNotBlank()) onSelect(customInput.trim())
                        showCustom = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showCustom = false }) { Text("取消") }
            }
        )
    }
}

private val prettyJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

private fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1000.0)
    else -> n.toString()
}

private fun prettyJson(input: String): String = runCatching {
    prettyJson.encodeToString(
        kotlinx.serialization.json.JsonElement.serializer(),
        prettyJson.parseToJsonElement(input)
    )
}.getOrDefault(input)
