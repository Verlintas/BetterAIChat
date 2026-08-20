package com.betteraichat.ui.settings

import android.Manifest
import android.app.NotificationManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.betteraichat.core.catalog.ModelCatalog
import com.betteraichat.core.mode.AppMode
import com.betteraichat.core.model.ProviderId
import com.betteraichat.ui.rememberContainer
import kotlinx.coroutines.launch

private enum class SettingsTab(val title: String) {
    CONNECTION("连接"),
    SMART("智能"),
    PERMISSIONS("权限")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = rememberContainer()
    val settings = container.settings
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    var selectedProvider by remember { mutableStateOf(settings.getDefaultProvider()) }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf(0.7) }
    var maxTokens by remember { mutableStateOf("4096") }
    var reasoning by remember { mutableStateOf(true) }
    var defaultMode by remember { mutableStateOf(settings.getDefaultMode()) }
    var showKey by remember { mutableStateOf(false) }
    var configLoaded by remember { mutableStateOf(false) }
    var probing by remember { mutableStateOf(false) }

    var notificationEnabled by remember { mutableStateOf(
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    ) }
    var canWriteSettings by remember { mutableStateOf(Settings.System.canWrite(context)) }
    var hasScreenshot by remember { mutableStateOf(container.screenshotManagerRef.hasProjection()) }

    var skills by remember { mutableStateOf(container.skillRepository.loadAll()) }

    fun refreshPermissionStates() {
        notificationEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        canWriteSettings = Settings.System.canWrite(context)
        hasScreenshot = container.screenshotManagerRef.hasProjection()
    }

    fun loadProviderConfig(provider: ProviderId) {
        configLoaded = false
        apiKey = settings.getApiKey(provider)
        baseUrl = settings.getBaseUrl(provider)
        model = settings.getModel(provider)
        temperature = settings.getTemperature(provider)
        maxTokens = settings.getMaxTokens(provider).toString()
        reasoning = settings.getReasoning(provider)
        configLoaded = true
    }

    LaunchedEffect(selectedProvider) {
        loadProviderConfig(selectedProvider)
    }

    fun saveConfig() {
        settings.setDefaultProvider(selectedProvider)
        settings.setApiKey(selectedProvider, apiKey)
        settings.setBaseUrl(selectedProvider, baseUrl)
        settings.setModel(selectedProvider, model)
        settings.setTemperature(selectedProvider, temperature)
        settings.setMaxTokens(selectedProvider, maxTokens.toIntOrNull() ?: 4096)
        settings.setReasoning(selectedProvider, reasoning)
        settings.setDefaultMode(defaultMode)
        scope.launch { snackbar.showSnackbar("配置已保存") }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissionStates() }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissionStates() }
    val writeSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshPermissionStates() }
    val screenshotLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            container.screenshotManagerRef.setProjectionResult(result.resultCode, result.data!!)
        }
        refreshPermissionStates()
    }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                SettingsTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.title) }
                    )
                }
            }
            when (SettingsTab.entries[selectedTab]) {
                SettingsTab.CONNECTION -> ConnectionTab(
                    selectedProvider = selectedProvider,
                    onSelectProvider = { selectedProvider = it },
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    showKey = showKey,
                    onToggleShowKey = { showKey = !showKey },
                    baseUrl = baseUrl,
                    onBaseUrlChange = { baseUrl = it },
                    model = model,
                    onModelChange = { model = it },
                    temperature = temperature,
                    onTemperatureChange = { temperature = it },
                    maxTokens = maxTokens,
                    onMaxTokensChange = { maxTokens = it },
                    reasoning = reasoning,
                    onReasoningChange = { reasoning = it },
                    configLoaded = configLoaded,
                    probing = probing,
                    onProbe = {
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
                                    scope.launch { snackbar.showSnackbar(result.message) }
                                } else {
                                    scope.launch { snackbar.showSnackbar(result.message) }
                                }
                            }
                        }
                    },
                    onSave = ::saveConfig
                )
                SettingsTab.SMART -> SmartTab(
                    defaultMode = defaultMode,
                    onDefaultModeChange = { defaultMode = it },
                    onSave = ::saveConfig,
                    themeMode = container.settings.getThemeMode(),
                    onThemeModeChange = { mode ->
                        container.settings.setThemeMode(mode)
                        scope.launch { snackbar.showSnackbar("外观已切换") }
                    },
                    autoSpeak = container.settings.getAutoSpeak(),
                    onAutoSpeakChange = { enabled ->
                        container.settings.setAutoSpeak(enabled)
                        scope.launch { snackbar.showSnackbar(if (enabled) "AI 回复将自动朗读" else "已关闭自动朗读") }
                    },
                    skills = skills,
                    onImportSkill = { skillLauncher.launch(arrayOf("*/*")) },
                    onDeleteSkill = { skill ->
                        container.registry.unregisterSkillTools(skill.name)
                        container.skillRepository.delete(skill.name)
                        skills = container.skillRepository.loadAll()
                        scope.launch { snackbar.showSnackbar("Skill「${skill.name}」已删除") }
                    }
                )
                SettingsTab.PERMISSIONS -> PermissionTab(
                    notificationEnabled = notificationEnabled,
                    canWriteSettings = canWriteSettings,
                    hasScreenshot = hasScreenshot,
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            scope.launch { snackbar.showSnackbar("Android 12 及以下通知默认已授权") }
                        }
                    },
                    onRequestCamera = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onRequestWriteSettings = {
                        writeSettingsLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    },
                    onRequestScreenshot = {
                        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        screenshotLauncher.launch(mpm.createScreenCaptureIntent())
                    },
                    onRefresh = ::refreshPermissionStates
                )
            }
        }
    }
}

@Composable
private fun ConnectionTab(
    selectedProvider: ProviderId,
    onSelectProvider: (ProviderId) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    showKey: Boolean,
    onToggleShowKey: () -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    temperature: Double,
    onTemperatureChange: (Double) -> Unit,
    maxTokens: String,
    onMaxTokensChange: (String) -> Unit,
    reasoning: Boolean,
    onReasoningChange: (Boolean) -> Unit,
    configLoaded: Boolean,
    probing: Boolean,
    onProbe: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("服务商")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderId.entries.forEach { p ->
                FilterChip(
                    selected = selectedProvider == p,
                    onClick = { onSelectProvider(p) },
                    label = { Text(p.displayName) }
                )
            }
        }

        HorizontalDivider()

        SectionTitle("${selectedProvider.displayName} 配置")
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key（本地加密存储）") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = onToggleShowKey) { Text(if (showKey) "隐藏" else "显示") }
            }
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Base URL（默认 ${ModelCatalog.defaultBaseUrl(selectedProvider)}）") },
            singleLine = true
        )
        OutlinedButton(
            onClick = onProbe,
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

        Text("模型", style = MaterialTheme.typography.labelLarge)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ModelCatalog.modelsFor(selectedProvider).size) { i ->
                val entry = ModelCatalog.modelsFor(selectedProvider)[i]
                FilterChip(
                    selected = model == entry.id,
                    onClick = { onModelChange(entry.id) },
                    label = { Text(entry.label, maxLines = 1) }
                )
            }
        }
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("模型 ID（可手动输入或点击上方检测）") },
            singleLine = true
        )

        Text("温度 ${"%.1f".format(temperature)}", style = MaterialTheme.typography.labelLarge)
        androidx.compose.material3.Slider(
            value = temperature.toFloat(),
            onValueChange = { onTemperatureChange(it.toDouble()) },
            valueRange = 0f..2f
        )

        OutlinedTextField(
            value = maxTokens,
            onValueChange = onMaxTokensChange,
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
            Switch(checked = reasoning, onCheckedChange = onReasoningChange)
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, enabled = configLoaded, modifier = Modifier.fillMaxWidth()) {
            Text("保存配置")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SmartTab(
    defaultMode: AppMode,
    onDefaultModeChange: (AppMode) -> Unit,
    onSave: () -> Unit,
    themeMode: com.betteraichat.core.storage.ThemeMode,
    onThemeModeChange: (com.betteraichat.core.storage.ThemeMode) -> Unit,
    autoSpeak: Boolean,
    onAutoSpeakChange: (Boolean) -> Unit,
    skills: List<com.betteraichat.core.skills.Skill>,
    onImportSkill: () -> Unit,
    onDeleteSkill: (com.betteraichat.core.skills.Skill) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("外观")
        Text(
            "切换应用的黑白（深色/浅色）模式",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.betteraichat.core.storage.ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    label = { Text(mode.displayName) }
                )
            }
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
            Switch(checked = autoSpeak, onCheckedChange = onAutoSpeakChange)
        }

        HorizontalDivider()

        SectionTitle("默认模式")
        Text(
            "新建对话时使用的模式：Chat 纯对话 / Plan 只读分析 / Build 工具需确认 / Max 自主执行",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppMode.entries.forEach { m ->
                FilterChip(
                    selected = defaultMode == m,
                    onClick = { onDefaultModeChange(m) },
                    label = { Text(m.displayName) }
                )
            }
        }
        OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("保存默认模式")
        }

        HorizontalDivider()

        SectionTitle("Skills")
        Text(
            "导入 opencode 风格的 SKILL.md（含 name/description frontmatter），AI 可通过 load_skill 工具加载执行，技能可自带工具。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onImportSkill, modifier = Modifier.fillMaxWidth()) {
            Text("导入 Skill 文件")
        }
        if (skills.isEmpty()) {
            Text(
                "暂无技能。导入后可在 Build/Max 模式对 AI 说「加载 xx 技能」执行。",
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
                        TextButton(onClick = { onDeleteSkill(skill) }) {
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
private fun PermissionTab(
    notificationEnabled: Boolean,
    canWriteSettings: Boolean,
    hasScreenshot: Boolean,
    onRequestNotification: () -> Unit,
    onRequestCamera: () -> Unit,
    onRequestWriteSettings: () -> Unit,
    onRequestScreenshot: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val container = rememberContainer()
    val shizukuGranted by container.shizukuManager.granted.collectAsStateWithLifecycle()
    val shizukuInstalled = com.betteraichat.skills.tools.ShizukuSupport.isShizukuInstalled(context)
    val shizukuBinder = com.betteraichat.skills.tools.ShizukuSupport.isBinderAlive()
    LaunchedEffect(Unit) { container.shizukuManager.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Shizuku（高级权限）")
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

        SectionTitle("系统权限")
        val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        PermissionRow(
            title = "通知",
            status = if (notificationEnabled) "已授权" else "未授权",
            buttonText = "授权",
            onAction = onRequestNotification
        )
        PermissionRow(
            title = "相机（闪光灯/手电筒）",
            status = if (hasCamera) "已授权" else "未授权",
            buttonText = "授权",
            onAction = onRequestCamera
        )
        PermissionRow(
            title = "修改系统设置（亮度等）",
            status = if (canWriteSettings) "已授权" else "未授权",
            buttonText = "授权",
            onAction = onRequestWriteSettings
        )
        PermissionRow(
            title = "截屏（MediaProjection）",
            status = if (hasScreenshot) "已授权" else "未授权",
            buttonText = "授权",
            onAction = onRequestScreenshot
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
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
