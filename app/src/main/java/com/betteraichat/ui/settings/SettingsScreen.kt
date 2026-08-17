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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.app.NotificationManagerCompat
import com.betteraichat.core.catalog.ModelCatalog
import com.betteraichat.core.mode.AppMode
import com.betteraichat.core.model.ProviderId
import com.betteraichat.ui.rememberContainer
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = rememberContainer()
    val settings = container.settings

    var selectedProvider by remember { mutableStateOf(settings.getDefaultProvider()) }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf(0.7) }
    var maxTokens by remember { mutableStateOf(4096) }
    var reasoning by remember { mutableStateOf(true) }
    var defaultMode by remember { mutableStateOf(settings.getDefaultMode()) }
    var saved by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }

    LaunchedEffect(selectedProvider) {
        apiKey = settings.getApiKey(selectedProvider)
        baseUrl = settings.getBaseUrl(selectedProvider)
        model = settings.getModel(selectedProvider)
        temperature = settings.getTemperature(selectedProvider)
        maxTokens = settings.getMaxTokens(selectedProvider)
        reasoning = settings.getReasoning(selectedProvider)
        saved = false
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val writeSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    val screenshotLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            container.screenshotManagerRef.setProjectionResult(result.resultCode, result.data!!)
        }
    }

    val notificationEnabled = remember(context) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val canWriteSettings = Settings.System.canWrite(context)

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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("服务商")
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

            SectionTitle("${selectedProvider.displayName} 配置")
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

            Text("模型", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
            }
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型 ID（可手动输入任意模型）") },
                singleLine = true
            )

            Text("温度 ${"%.1f".format(temperature)}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = temperature.toFloat(),
                onValueChange = { temperature = it.toDouble() },
                valueRange = 0f..2f
            )

            OutlinedTextField(
                value = maxTokens.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let { maxTokens = it } },
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

            HorizontalDivider()

            SectionTitle("默认模式")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppMode.entries.forEach { m ->
                    FilterChip(
                        selected = defaultMode == m,
                        onClick = { defaultMode = m },
                        label = { Text(m.displayName) }
                    )
                }
            }

            HorizontalDivider()

            SectionTitle("Skills")
            Text(
                "导入 opencode 风格的 SKILL.md（含 name/description frontmatter），AI 可通过 load_skill 工具加载执行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            var skills by remember { mutableStateOf(container.skillRepository.loadAll()) }
            var skillMessage by remember { mutableStateOf<String?>(null) }
            val skillLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    val fileName = context.contentResolver.query(
                        uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
                    )?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else "skill.md"
                    } ?: "skill.md"
                    val content = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: ""
                    if (content.isNotBlank()) {
                        val result = container.skillRepository.import(fileName, content)
                        result.onSuccess { skillMessage = "Skill「${it.name}」导入成功" }
                            .onFailure { skillMessage = "导入失败：${it.message}" }
                        skills = container.skillRepository.loadAll()
                    }
                }
            }
            Button(
                onClick = { skillLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("导入 Skill 文件")
            }
            if (skills.isEmpty()) {
                Text(
                    "暂无技能。可在 Settings 页导入后，于 Build/Max 模式对 AI 说「加载 xx 技能」执行。",
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
                                Text(skill.name, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
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
                                skillMessage = "Skill「${skill.name}」已删除"
                            }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            skillMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("导入失败")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider()

            SectionTitle("权限与工具授权")
            PermissionRow(
                title = "通知",
                status = if (notificationEnabled) "已授权" else "未授权",
                buttonText = "授权",
                onAction = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
            val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            PermissionRow(
                title = "相机（闪光灯/手电筒）",
                status = if (hasCamera) "已授权" else "未授权",
                buttonText = "授权",
                onAction = {
                    notifPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
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
            val hasScreenshot = container.screenshotManagerRef.hasProjection()
            PermissionRow(
                title = "截屏（MediaProjection）",
                status = if (hasScreenshot) "已授权" else "未授权",
                buttonText = "授权",
                onAction = {
                    val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    screenshotLauncher.launch(mpm.createScreenCaptureIntent())
                }
            )

            Button(
                onClick = {
                    settings.setDefaultProvider(selectedProvider)
                    settings.setApiKey(selectedProvider, apiKey)
                    settings.setBaseUrl(selectedProvider, baseUrl)
                    settings.setModel(selectedProvider, model)
                    settings.setTemperature(selectedProvider, temperature)
                    settings.setMaxTokens(selectedProvider, maxTokens)
                    settings.setReasoning(selectedProvider, reasoning)
                    settings.setDefaultMode(defaultMode)
                    saved = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存配置")
            }
            if (saved) {
                Text(
                    "已保存",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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
