package com.betteraichat.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betteraichat.AppContainer
import com.betteraichat.core.catalog.ModelCatalog
import com.betteraichat.core.mode.AppMode
import com.betteraichat.core.model.ProviderId
import com.betteraichat.core.storage.SettingsRepository
import com.betteraichat.core.storage.ThemeMode
import com.betteraichat.ui.rememberContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SettingsSection(val title: String) {
    PROVIDER("服务商与模型"),
    CONVERSATION("对话"),
    SKILLS("技能"),
    PERMISSIONS("权限"),
    REPEAT_TASKS("定时任务"),
    AUTOMATIONS("自动化"),
    STATS("使用统计")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var section by remember { mutableStateOf<SettingsSection?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(section?.title ?: "设置") },
                navigationIcon = {
                    IconButton(onClick = { if (section == null) onBack() else section = null }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when (section) {
            null -> SettingsMenu(
                modifier = Modifier.fillMaxSize().padding(padding),
                onOpenSection = { section = it }
            )
            SettingsSection.PROVIDER -> ProviderSection(
                modifier = Modifier.fillMaxSize().padding(padding),
                container = container,
                settings = container.settings,
                scope = scope,
                snackbar = snackbar
            )
            SettingsSection.CONVERSATION -> ConversationSection(
                modifier = Modifier.fillMaxSize().padding(padding),
                settings = container.settings,
                scope = scope,
                snackbar = snackbar
            )
            SettingsSection.SKILLS -> SkillsSection(
                modifier = Modifier.fillMaxSize().padding(padding),
                container = container,
                scope = scope,
                snackbar = snackbar
            )
            SettingsSection.PERMISSIONS -> PermissionsSection(
                modifier = Modifier.fillMaxSize().padding(padding),
                container = container,
                scope = scope,
                snackbar = snackbar
            )
            SettingsSection.REPEAT_TASKS -> RepeatTasksSection(
                modifier = Modifier.fillMaxSize().padding(padding),
                container = container,
                scope = scope,
                snackbar = snackbar
            )
            SettingsSection.AUTOMATIONS -> AutomationsSection(
                modifier = Modifier.fillMaxSize().padding(padding),
                container = container,
                scope = scope,
                snackbar = snackbar
            )
            SettingsSection.STATS -> StatsSection(
                modifier = Modifier.fillMaxSize().padding(padding),
                container = container
            )
        }
    }
}

@Composable
private fun SettingsMenu(
    modifier: Modifier,
    onOpenSection: (SettingsSection) -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SettingsMenuItem(
                title = "服务商与模型",
                subtitle = "API Key · Base URL · 模型 · 连接检测",
                icon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.PROVIDER) }
            )
        }
        item {
            SettingsMenuItem(
                title = "对话",
                subtitle = "默认模式 · 外观 · 自动朗读",
                icon = { Icon(Icons.Filled.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.CONVERSATION) }
            )
        }
        item {
            SettingsMenuItem(
                title = "技能",
                subtitle = "导入 · 管理 SKILL.md",
                icon = { Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.SKILLS) }
            )
        }
        item {
            SettingsMenuItem(
                title = "权限",
                subtitle = "Shizuku · 系统权限授权",
                icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.PERMISSIONS) }
            )
        }
        item {
            SettingsMenuItem(
                title = "定时任务",
                subtitle = "查看 · 取消重复提醒",
                icon = { Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.REPEAT_TASKS) }
            )
        }
        item {
            SettingsMenuItem(
                title = "自动化",
                subtitle = "条件触发 · 自动执行工具",
                icon = { Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.AUTOMATIONS) }
            )
        }
        item {
            SettingsMenuItem(
                title = "使用统计",
                subtitle = "会话 · 消息 · Token · 工具调用",
                icon = { Icon(Icons.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.STATS) }
            )
        }
    }
}

@Composable
private fun SettingsMenuItem(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.size(40.dp)
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProviderSection(
    modifier: Modifier,
    container: AppContainer,
    settings: SettingsRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: SnackbarHostState
) {
    var selectedProvider by remember { mutableStateOf(settings.getDefaultProvider()) }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf(0.7) }
    var maxTokens by remember { mutableStateOf("4096") }
    var reasoning by remember { mutableStateOf(true) }
    var showKey by remember { mutableStateOf(false) }
    var configLoaded by remember { mutableStateOf(false) }
    var probing by remember { mutableStateOf(false) }

    LaunchedEffect(selectedProvider) {
        configLoaded = false
        apiKey = settings.getApiKey(selectedProvider)
        baseUrl = settings.getBaseUrl(selectedProvider)
        model = settings.getModel(selectedProvider)
        temperature = settings.getTemperature(selectedProvider)
        maxTokens = settings.getMaxTokens(selectedProvider).toString()
        reasoning = settings.getReasoning(selectedProvider)
        configLoaded = true
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("服务商", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderId.entries.forEach { p ->
                FilterChip(
                    selected = selectedProvider == p,
                    onClick = { selectedProvider = p },
                    label = { Text(p.displayName) }
                )
            }
        }

        HorizontalDivider()

        Text("${selectedProvider.displayName} 配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key（本地加密存储）") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "隐藏" else "显示") }
            }
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Base URL（默认 ${ModelCatalog.defaultBaseUrl(selectedProvider)}）") },
            singleLine = true
        )
        OutlinedButton(
            onClick = {
                if (apiKey.isBlank()) {
                    scope.launch { snackbar.showSnackbar("请先填写 API Key") }
                } else {
                    val providerAtClick = selectedProvider
                    val baseUrlAtClick = baseUrl
                    val apiKeyAtClick = apiKey
                    scope.launch {
                        probing = true
                        val result = ModelProbe.probe(providerAtClick, baseUrlAtClick, apiKeyAtClick)
                        probing = false
                        if (selectedProvider != providerAtClick) return@launch
                        if (result.ok) {
                            if (result.models.isNotEmpty()) {
                                settings.setCustomModels(providerAtClick, result.models)
                                model = result.models.first()
                            }
                        }
                        scope.launch { snackbar.showSnackbar(result.message) }
                    }
                }
            },
            enabled = configLoaded && !probing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (probing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在检测…")
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("测试连接并获取模型")
            }
        }

        HorizontalDivider()

        Text("模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ModelCatalog.modelsFor(selectedProvider).size) { i ->
                val entry = ModelCatalog.modelsFor(selectedProvider)[i]
                FilterChip(
                    selected = model == entry.id,
                    onClick = { model = entry.id },
                    label = { Text(entry.label, maxLines = 1) }
                )
            }
        }
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("模型 ID（可手动输入或点击上方检测）") },
            singleLine = true
        )

        Text("温度 ${"%.1f".format(temperature)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        androidx.compose.material3.Slider(
            value = temperature.toFloat(),
            onValueChange = { temperature = it.toDouble() },
            valueRange = 0f..2f
        )

        OutlinedTextField(
            value = maxTokens,
            onValueChange = { maxTokens = it.filter { c -> c.isDigit() } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Max Tokens") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Max 模式深度推理", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Max 模式下向模型发送高推理强度参数（按模型能力自动适配）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = reasoning, onCheckedChange = { reasoning = it })
        }

        Spacer(Modifier.height(8.dp))
        Button(
            enabled = configLoaded,
            onClick = {
                settings.setDefaultProvider(selectedProvider)
                settings.setApiKey(selectedProvider, apiKey)
                settings.setBaseUrl(selectedProvider, baseUrl)
                settings.setModel(selectedProvider, model)
                settings.setTemperature(selectedProvider, temperature)
                settings.setMaxTokens(selectedProvider, maxTokens.toIntOrNull() ?: 4096)
                settings.setReasoning(selectedProvider, reasoning)
                scope.launch { snackbar.showSnackbar("配置已保存") }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存配置")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConversationSection(
    modifier: Modifier,
    settings: SettingsRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: SnackbarHostState
) {
    var defaultMode by remember { mutableStateOf(settings.getDefaultMode()) }
    var themeMode by remember { mutableStateOf(settings.getThemeMode()) }
    var autoSpeak by remember { mutableStateOf(settings.getAutoSpeak()) }
    var voiceAssistant by remember { mutableStateOf(settings.getVoiceAssistant()) }
    var accentColor by remember { mutableStateOf(settings.getAccentColor()) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("默认模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "新建对话时使用的模式：Chat 纯对话 / Plan 只读分析 / Build 工具需确认 / Max 自主执行",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppMode.entries.forEach { m ->
                FilterChip(
                    selected = defaultMode == m,
                    onClick = {
                        defaultMode = m
                        settings.setDefaultMode(m)
                    },
                    label = { Text(m.displayName) }
                )
            }
        }

        HorizontalDivider()

        Text("外观", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = {
                        themeMode = mode
                        settings.setThemeMode(mode)
                    },
                    label = { Text(mode.displayName) }
                )
            }
        }

        HorizontalDivider()

        Text("强调色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.betteraichat.core.storage.AccentColor.entries.forEach { c ->
                FilterChip(
                    selected = accentColor == c,
                    onClick = {
                        accentColor = c
                        settings.setAccentColor(c)
                    },
                    label = { Text(c.displayName) }
                )
            }
        }

        HorizontalDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("语音助手模式", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "免提对话：AI 回复自动朗读，说完自动开麦，识别到内容自动发送",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = voiceAssistant,
                onCheckedChange = {
                    voiceAssistant = it
                    settings.setVoiceAssistant(it)
                }
            )
        }

        HorizontalDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自动朗读回复", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "AI 回复完成后自动用语音朗读（可配合 Max 模式当语音助手用）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = autoSpeak,
                onCheckedChange = {
                    autoSpeak = it
                    settings.setAutoSpeak(it)
                }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SkillsSection(
    modifier: Modifier,
    container: AppContainer,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: SnackbarHostState
) {
    val context = LocalContext.current
    var skills by remember { mutableStateOf(container.skillRepository.loadAll()) }
    val skillLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val fileName = context.contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else "skill.md" } ?: "skill.md"
            val content = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: ""
            if (content.isNotBlank()) {
                val result = container.skillRepository.import(fileName, content)
                result.onSuccess { scope.launch { snackbar.showSnackbar("Skill「${it.name}」导入成功") } }
                    .onFailure { scope.launch { snackbar.showSnackbar("导入失败：${it.message}") } }
                skills = container.skillRepository.loadAll()
            }
        }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "导入 opencode 风格的 SKILL.md（含 name/description frontmatter），AI 可通过 load_skill 工具加载执行，技能可自带工具。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = { skillLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("导入 Skill 文件")
        }
        if (skills.isEmpty()) {
            Text(
                "暂无技能。导入后可在 Build/Max 模式对 AI 说「加载 xx 技能」执行；也可在对话里用「保存为技能」自动录制。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            skills.forEach { skill ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(skill.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(
                                skill.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        TextButton(onClick = {
                            container.registry.unregisterSkillTools(skill.name)
                            container.skillRepository.delete(skill.name)
                            skills = container.skillRepository.loadAll()
                            scope.launch { snackbar.showSnackbar("Skill「${skill.name}」已删除") }
                        }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionsSection(
    modifier: Modifier,
    container: AppContainer,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: SnackbarHostState
) {
    val context = LocalContext.current
    var notificationEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var canWriteSettings by remember { mutableStateOf(Settings.System.canWrite(context)) }
    var hasScreenshot by remember { mutableStateOf(container.screenshotManagerRef.hasProjection()) }
    var hasCamera by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    fun refresh() {
        notificationEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        canWriteSettings = Settings.System.canWrite(context)
        hasScreenshot = container.screenshotManagerRef.hasProjection()
        hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }
    val writeSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }
    val screenshotLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            container.screenshotManagerRef.setProjectionResult(result.resultCode, result.data!!)
        }
        refresh()
    }

    val shizukuGranted by container.shizukuManager.granted.collectAsStateWithLifecycle()
    val shizukuInstalled = com.betteraichat.skills.tools.ShizukuSupport.isShizukuInstalled(context)
    val shizukuBinder = com.betteraichat.skills.tools.ShizukuSupport.isBinderAlive()
    LaunchedEffect(Unit) { container.shizukuManager.refresh() }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Shizuku（高级权限）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "授权后 AI 可通过 run_shell 工具执行任意 shell 命令（pm/am/dumpsys 等），达到 root 级操作能力。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PermissionRow(
            title = "Shizuku",
            status = when {
                !shizukuInstalled -> "未安装 Shizuku 应用"
                !shizukuBinder -> "Shizuku 未启动"
                !shizukuGranted -> "未授权"
                else -> "已授权，可执行 shell 命令"
            },
            buttonText = when {
                !shizukuInstalled -> "下载"
                !shizukuBinder -> "打开"
                !shizukuGranted -> "授权"
                else -> "已授权"
            },
            onAction = {
                when {
                    !shizukuInstalled -> com.betteraichat.skills.tools.ShizukuSupport.openShizukuDownload(context)
                    !shizukuBinder -> com.betteraichat.skills.tools.ShizukuSupport.openShizukuApp(context)
                    !shizukuGranted -> container.shizukuManager.requestPermission()
                    else -> Unit
                }
            }
        )

        HorizontalDivider()

        Text("系统权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        PermissionRow(
            title = "通知",
            status = if (notificationEnabled) "已授权" else "未授权",
            buttonText = "授权",
            onAction = {
                if (Build.VERSION.SDK_INT >= 33) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scope.launch { snackbar.showSnackbar("Android 12 及以下通知默认已授权") }
                }
            }
        )
        PermissionRow(
            title = "相机（闪光灯/手电筒）",
            status = if (hasCamera) "已授权" else "未授权",
            buttonText = "授权",
            onAction = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
        )
        PermissionRow(
            title = "修改系统设置（亮度等）",
            status = if (canWriteSettings) "已授权" else "未授权",
            buttonText = "授权",
            onAction = {
                writeSettingsLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        )
        PermissionRow(
            title = "截屏（MediaProjection）",
            status = if (hasScreenshot) "已授权" else "未授权",
            buttonText = "授权",
            onAction = {
                val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                if (Build.VERSION.SDK_INT >= 34) {
                    val config = android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()
                    screenshotLauncher.launch(mpm.createScreenCaptureIntent(config))
                } else {
                    screenshotLauncher.launch(mpm.createScreenCaptureIntent())
                }
            }
        )
        Text(
            if (container.screenshotManagerRef.isServiceRunning()) {
                "截屏服务运行中，AI 可随时分析屏幕。完成使用后建议点击下方按钮停止（停止后再次使用需重新授权）。"
            } else {
                "Android 15 授权流程：开始录制 → 选择 BetterAIChat。授权后截屏服务将在后台运行以支持屏幕分析。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (container.screenshotManagerRef.isServiceRunning()) {
            OutlinedButton(
                onClick = { container.screenshotManagerRef.stopProjectionService() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("停止截屏服务")
            }
        }

        HorizontalDivider()

        Text("AI 控制屏幕（无障碍）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "开启后 AI 可通过 ua_type / ua_press / ua_tap / ua_swipe 工具在任意应用里输入文字、点击和滑动，配合屏幕识别即可全自动操作手机。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val accessibilityEnabled = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (true) {
                accessibilityEnabled.value = com.betteraichat.tools.BacAccessibilityService.connected()
                delay(1500)
            }
        }
        PermissionRow(
            title = "无障碍控制",
            status = if (accessibilityEnabled.value) "已开启" else "未开启",
            buttonText = if (accessibilityEnabled.value) "已开启" else "开启",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )
        val usageAccessGranted = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (true) {
                usageAccessGranted.value = checkUsageAccess(context)
                delay(1500)
            }
        }
        PermissionRow(
            title = "使用情况访问（前台应用检测）",
            status = if (usageAccessGranted.value) "已授权" else "未授权",
            buttonText = "授权",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        )
        Text(
            "授权后 AI 可通过 get_foreground_app 知道当前正在使用哪个应用，用于判断任务上下文。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PermissionRow(
            title = "通知使用权（读取通知）",
            status = if (notificationListenerEnabled(context)) "已开启" else "未开启",
            buttonText = "开启",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )
        Text(
            "开启后 AI 可通过 read_notifications 读取最近收到的通知（消息、提醒等），用于感知手机动态。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun checkUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    return runCatching {
        appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
}

private fun notificationListenerEnabled(context: Context): Boolean {
    val flat = android.provider.Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return flat.split(':').any {
        android.content.ComponentName.unflattenFromString(it)?.packageName == context.packageName
    }
}

@Composable
private fun RepeatTasksSection(
    modifier: Modifier,
    container: AppContainer,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: SnackbarHostState
) {
    val tasks by container.db.repeatTaskDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "AI 创建的重复提醒任务（每天/每周/每小时）。可在通知里一键停止，也可在此删除。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (tasks.isEmpty()) {
            Text(
                "暂无定时任务。可在 Build/Max 模式让 AI 设置，例如「每天 9 点提醒我喝水」。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            tasks.forEach { task ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${task.title}：${task.content}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2
                            )
                            Text(
                                "${intervalLabel(task)} · 下次：${formatNext(task.nextTriggerAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            val am = container.appContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                            val pi = android.app.PendingIntent.getBroadcast(
                                container.appContext,
                                task.requestCode,
                                Intent(container.appContext, com.betteraichat.skills.AlarmReceiver::class.java)
                                    .setAction(com.betteraichat.skills.AlarmReceiver.ACTION_REPEAT),
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                            )
                            am.cancel(pi)
                            scope.launch { container.db.repeatTaskDao().deleteByRequestCode(task.requestCode) }
                            scope.launch { snackbar.showSnackbar("定时任务已删除") }
                        }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun intervalLabel(task: com.betteraichat.core.db.RepeatTaskEntity): String = when (task.interval) {
    "daily" -> "每天 ${task.time}"
    "weekly" -> "每周周${task.weekday} ${task.time}"
    else -> "每 ${task.everyHours} 小时"
}

private fun formatNext(ts: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(ts))

@Composable
private fun StatsSection(
    modifier: Modifier,
    container: AppContainer
) {
    val db = container.db
    var conversations by remember { mutableStateOf(0L) }
    var userMessages by remember { mutableStateOf(0L) }
    var assistantMessages by remember { mutableStateOf(0L) }
    var inputTokens by remember { mutableStateOf(0L) }
    var outputTokens by remember { mutableStateOf(0L) }
    var toolCalls by remember { mutableStateOf(0L) }
    var repeatTasks by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val c = db.conversationDao().getAllCount()
            val userM = db.messageDao().countUserMessages()
            val assistantM = db.messageDao().countAssistantMessages()
            val t = db.messageDao().tokenTotals()
            val tc = db.messageDao().countToolCallMessages()
            val rt = db.repeatTaskDao().getCount()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                conversations = c
                userMessages = userM
                assistantMessages = assistantM
                inputTokens = t.totalInput
                outputTokens = t.totalOutput
                toolCalls = tc
                repeatTasks = rt
            }
        }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatRow("会话数", conversations.toString())
        StatRow("用户消息", userMessages.toString())
        StatRow("AI 回复", assistantMessages.toString())
        StatRow("输入 Token 累计", formatCount(inputTokens))
        StatRow("输出 Token 累计", formatCount(outputTokens))
        StatRow("工具调用次数", toolCalls.toString())
        StatRow("定时任务", repeatTasks.toString())
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1000.0)
    else -> n.toString()
}

@Composable
private fun PermissionRow(
    title: String,
    status: String,
    buttonText: String,
    onAction: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == "已授权") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            Button(onClick = onAction) { Text(buttonText) }
        }
    }
}

@Composable
private fun AutomationsSection(
    modifier: Modifier,
    container: AppContainer,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: SnackbarHostState
) {
    val automations by container.db.automationDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "自动化：满足条件时自动执行一系列工具操作，无需人工确认。可让 AI 创建，例如「每天 22:00 开启勿扰并静音」「电量低于 20% 时提醒充电」。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (automations.isEmpty()) {
            Text(
                "暂无自动化。在 Build/Max 模式对 AI 说「创建一个自动化：每天晚上 10 点静音并开启勿扰」。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            automations.forEach { a ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(
                                when (a.triggerType) {
                                    "time" -> "每天 ${a.triggerValue}（${if (a.days == "all") "每天" else a.days}）"
                                    "battery" -> "电量${if (a.triggerValue.startsWith("low")) "低于" else "高于"} ${a.triggerValue.substringAfter(":")}%"
                                    else -> a.triggerType
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = a.enabled,
                            onCheckedChange = { on ->
                                scope.launch {
                                    container.db.automationDao().setEnabled(a.id, on)
                                    val updated = a.copy(enabled = on)
                                    if (on) container.automationScheduler.reschedule(updated)
                                    else container.automationScheduler.cancel(updated)
                                    container.automationScheduler.scheduleAll()
                                }
                            }
                        )
                        TextButton(onClick = {
                            scope.launch {
                                container.db.automationDao().delete(a.id)
                                container.automationScheduler.cancel(a)
                                snackbar.showSnackbar("自动化「${a.name}」已删除")
                            }
                        }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
