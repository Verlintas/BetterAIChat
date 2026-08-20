package com.betteraichat.ui.chat

import android.content.Intent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betteraichat.core.catalog.ModelCatalog
import com.betteraichat.core.mode.AppMode
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
    var showMaxConfirm by remember { mutableStateOf(false) }
    var showClearContext by remember { mutableStateOf(false) }
    var showCompressConfirm by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<com.betteraichat.ui.chat.UiMessage?>(null) }
    var editText by remember { mutableStateOf("") }
    val context = LocalContext.current

    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris ->
        if (uris.isNotEmpty()) vm.addPendingImages(uris, context.contentResolver)
    }
    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) vm.addPendingFile(uri, context.contentResolver)
    }

    val totalPromptTokens = state.messages.lastOrNull { it.usageInput > 0 }?.usageInput ?: 0
    val contextWindow = ModelCatalog.entryFor(state.provider, state.model).contextWindow
    val usagePercent = if (contextWindow > 0 && totalPromptTokens > 0) {
        ((totalPromptTokens * 100) / contextWindow).toInt().coerceIn(0, 100)
    } else 0
    val usageText = if (totalPromptTokens > 0) {
        " · ${formatTokens(totalPromptTokens)}（$usagePercent%）"
    } else ""

    val shouldAutoScroll by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 3
        }
    }

    var initialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(state.messages.size) {
        if (!initialScrollDone && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.size - 1)
            initialScrollDone = true
        }
    }

    LaunchedEffect(state.sendTick) {
        if (state.sendTick > 0 && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    LaunchedEffect(state.messages.size, shouldAutoScroll) {
        if (shouldAutoScroll && state.messages.isNotEmpty() && initialScrollDone) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.notification) {
        state.notification?.let {
            snackbarHostState.showSnackbar(it)
            vm.dismissNotification()
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
                    ModeSelector(
                        current = state.mode,
                        onSelect = { target ->
                            if (target == AppMode.MAX && state.mode != AppMode.MAX) {
                                showMaxConfirm = true
                            } else {
                                vm.updateMode(target)
                            }
                        }
                    )
                    ModelSelector(state.provider, state.model, vm::updateModel)
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("分析屏幕") },
                                onClick = {
                                    menuOpen = false
                                    vm.analyzeScreen()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("保存为技能") },
                                onClick = {
                                    menuOpen = false
                                    vm.saveAsSkill()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("压缩上下文") },
                                onClick = {
                                    menuOpen = false
                                    showCompressConfirm = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导出对话") },
                                onClick = {
                                    menuOpen = false
                                    shareConversationInternal(context, vm.buildExportText())
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("清除上下文") },
                                onClick = {
                                    menuOpen = false
                                    showClearContext = true
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            InputBar(
                input = state.input,
                isRunning = state.isRunning,
                mode = state.mode,
                pendingAttachments = state.pendingAttachments,
                attachmentError = state.attachmentError,
                processing = state.processing,
                onInputChange = vm::onInputChange,
                onPickImages = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onPickFile = { filePicker.launch("*/*") },
                onRemoveAttachment = vm::removePendingAttachment,
                onSend = vm::send,
                onStop = vm::stop
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.messages.isEmpty() && state.error == null) {
            WelcomePanel(
                mode = state.mode,
                onPickExample = { vm.onInputChange(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.messages.size, key = { state.messages[it].id }) { idx ->
                    val msg = state.messages[idx]
                    MessageItem(
                        msg = msg,
                        onDelete = vm::deleteMessage,
                        onSpeak = { container.speechPlayer.speak(it) },
                        onToggleStar = vm::toggleStarred,
                        onEdit = { id ->
                            val target = state.messages.firstOrNull { it.id == id }
                            if (target != null) {
                                editingMessage = target
                                editText = target.content
                            }
                        },
                        onOpenLink = { link ->
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
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
    }

    if (showCompressConfirm) {
        AlertDialog(
            onDismissRequest = { showCompressConfirm = false },
            title = { Text("压缩上下文？") },
            text = {
                Text(
                    "将总结之前对话并保留最近一轮，AI 将基于摘要继续。适合长对话节省上下文。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.compressContext()
                    showCompressConfirm = false
                }) { Text("压缩") }
            },
            dismissButton = {
                TextButton(onClick = { showCompressConfirm = false }) { Text("取消") }
            }
        )
    }

    editingMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("编辑消息") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.editAndResend(msg.id, editText)
                    editingMessage = null
                }) { Text("重发") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("取消") }
            }
        )
    }

    if (showClearContext) {
        AlertDialog(
            onDismissRequest = { showClearContext = false },
            title = { Text("清除上下文？") },
            text = {
                Text(
                    "将删除本会话全部消息，AI 将不再记得之前的对话内容（会话本身会保留）。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearContext()
                    showClearContext = false
                }) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearContext = false }) { Text("取消") }
            }
        )
    }

    if (showMaxConfirm) {
        AlertDialog(
            onDismissRequest = { showMaxConfirm = false },
            title = { Text("切换到 Max 模式？") },
            text = {
                Column {
                    Text(
                        "Max 模式下 AI 将自主调用设备工具完成任务，无需每一步确认。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "包括：打开应用、发送通知、调整亮度/音量、截屏、联网搜索。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateMode(AppMode.MAX)
                    showMaxConfirm = false
                }) { Text("切换") }
            },
            dismissButton = {
                TextButton(onClick = { showMaxConfirm = false }) { Text("取消") }
            }
        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomePanel(
    mode: AppMode,
    onPickExample: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val examples = when (mode) {
        AppMode.CHAT -> listOf(
            "你好，介绍一下你自己",
            "用 markdown 写一份番茄工作法说明",
            "给我讲个冷笑话"
        )
        AppMode.PLAN -> listOf(
            "分析一下这台设备的状况并给出优化建议",
            "制定一个本周健身计划"
        )
        AppMode.BUILD -> listOf(
            "打开计算器",
            "把音量调到 30%",
            "发送一条 5 分钟后提醒我喝水的通知"
        )
        AppMode.MAX -> listOf(
            "搜索今天的热点新闻并总结",
            "把亮度调到最高，然后截图给我看",
            "查一下现在的天气，如果下雨就提醒我"
        )
    }
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "AI",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.size(16.dp))
        Text("BetterAIChat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Text(
            "当前模式：${mode.displayName}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.size(4.dp))
        Text(
            mode.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp)
        )
        Spacer(Modifier.size(24.dp))
        Text(
            "试试这样说：",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            examples.forEach { example ->
                AssistChip(
                    onClick = { onPickExample(example) },
                    label = { Text(example, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
        Spacer(Modifier.size(16.dp))
        Text(
            "长按消息可复制/朗读/编辑",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(Modifier.size(24.dp))
        Text(
            "提示词模板：",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            PromptTemplates.forEach { template ->
                AssistChip(
                    onClick = { onPickExample(template.prompt) },
                    label = { Text(template.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
    }
}

private data class PromptTemplate(val label: String, val prompt: String)

private val PromptTemplates = listOf(
    PromptTemplate("翻译", "请将以下内容翻译成英文（保持原意和语气）：\n\n"),
    PromptTemplate("总结", "请用 3 句话总结以下内容的要点：\n\n"),
    PromptTemplate("润色", "请润色以下文字，使其更通顺专业，保持原意：\n\n"),
    PromptTemplate("写作", "请帮我写一篇关于以下主题的文章（300 字左右）：\n\n"),
    PromptTemplate("代码解释", "请解释以下代码的功能和关键逻辑：\n\n"),
    PromptTemplate("头脑风暴", "请针对以下主题给出 10 个创意想法：\n\n")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InputBar(
    input: String,
    isRunning: Boolean,
    mode: AppMode,
    pendingAttachments: List<PendingAttachment>,
    attachmentError: String?,
    processing: Boolean,
    onInputChange: (String) -> Unit,
    onPickImages: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveAttachment: (Long) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    var attachMenu by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (pendingAttachments.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                pendingAttachments.forEach { att ->
                    InputChip(
                        selected = false,
                        onClick = { },
                        label = { Text(att.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            IconButton(onClick = { onRemoveAttachment(att.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = "移除", modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
            }
        }
        attachmentError?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (processing) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "正在解析文档…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box {
                IconButton(onClick = { attachMenu = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加附件")
                }
                DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("选择图片（可多选）") },
                        onClick = {
                            attachMenu = false
                            onPickImages()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("选择文件") },
                        onClick = {
                            attachMenu = false
                            onPickFile()
                        }
                    )
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        when (mode) {
                            AppMode.CHAT -> "输入消息…"
                            AppMode.PLAN -> "输入问题，AI 将进行分析…"
                            AppMode.BUILD -> "输入任务，AI 可操作设备…"
                            AppMode.MAX -> "输入任务，AI 将自主执行…"
                        }
                    )
                },
                maxLines = 6,
                trailingIcon = {
                    if (input.isNotEmpty() && !isRunning) {
                        IconButton(onClick = { onInputChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "清空")
                        }
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { if (isRunning) onStop() else onSend() },
                enabled = !processing && (isRunning || input.isNotBlank() || pendingAttachments.isNotEmpty())
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Filled.Send, contentDescription = "发送")
                }
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
    val container = rememberContainer()
    val serverModels = remember(provider) { container.settings.getCustomModels(provider) }
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
            if (serverModels.isNotEmpty()) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            "服务端模型（检测到的 ${serverModels.size} 个）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { }
                )
                serverModels.forEach { id ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(id)
                                Text("服务端 · 设置页检测", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = {
                            onSelect(id)
                            expanded = false
                        }
                    )
                }
            }
            if (current !in catalogIds && current !in serverModels) {
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

private fun shareConversationInternal(context: android.content.Context, text: String) {
    if (text.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "导出对话"))
}

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
