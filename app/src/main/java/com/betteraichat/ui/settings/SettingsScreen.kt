package com.betteraichat.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betteraichat.AppContainer
import com.betteraichat.core.catalog.ModelCatalog
import com.betteraichat.core.mode.AppMode
import com.betteraichat.core.model.ProviderId
import com.betteraichat.core.storage.SettingsRepository
import com.betteraichat.R
import com.betteraichat.core.storage.ThemeMode
import com.betteraichat.ui.rememberContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.launch

private enum class SettingsSection(@androidx.annotation.StringRes val titleRes: Int) {
    PROVIDER(R.string.settings_provider),
    CONVERSATION(R.string.settings_conversation),
    SKILLS(R.string.settings_skills),
    PERMISSIONS(R.string.settings_permissions),
    REPEAT_TASKS(R.string.settings_tasks),
    AUTOMATIONS(R.string.settings_automations),
    STATS(R.string.settings_stats),
    MEMORIES(R.string.settings_memories),
    DEVELOPER(R.string.settings_developer)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var section by remember { mutableStateOf<SettingsSection?>(null) }

    BackHandler(enabled = section != null) {
        section = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(section?.let { stringResource(it.titleRes) } ?: stringResource(R.string.settings_settings)) },
                navigationIcon = {
                    IconButton(onClick = { if (section == null) onBack() else section = null }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        AnimatedContent(
            targetState = section,
            transitionSpec = {
                (fadeIn(tween(160)) + slideInHorizontally { it / 8 })
                    .togetherWith(fadeOut(tween(120)) + slideOutHorizontally { -it / 8 })
            },
            label = "settings-section"
        ) { target ->
        when (target) {
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
                container = container,
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
            SettingsSection.MEMORIES -> MemoriesSection(
                modifier = Modifier.fillMaxSize().padding(padding),
                container = container,
                scope = scope,
                snackbar = snackbar
            )
            SettingsSection.DEVELOPER -> DeveloperSection(
                modifier = Modifier.fillMaxSize().padding(padding),
                scope = scope,
                snackbar = snackbar
            )
        }
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
                title = stringResource(com.betteraichat.R.string.settings_provider),
                subtitle = stringResource(com.betteraichat.R.string.settings_provider_sub),
                icon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.PROVIDER) }
            )
        }
        item {
            SettingsMenuItem(
                title = stringResource(com.betteraichat.R.string.settings_conversation),
                subtitle = stringResource(com.betteraichat.R.string.settings_conversation_sub),
                icon = { Icon(Icons.Filled.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.CONVERSATION) }
            )
        }
        item {
            SettingsMenuItem(
                title = stringResource(com.betteraichat.R.string.settings_skills),
                subtitle = stringResource(com.betteraichat.R.string.settings_skills_sub),
                icon = { Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.SKILLS) }
            )
        }
        item {
            SettingsMenuItem(
                title = stringResource(com.betteraichat.R.string.settings_permissions),
                subtitle = stringResource(com.betteraichat.R.string.settings_permissions_sub),
                icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.PERMISSIONS) }
            )
        }
        item {
            SettingsMenuItem(
                title = stringResource(com.betteraichat.R.string.settings_tasks),
                subtitle = stringResource(com.betteraichat.R.string.settings_tasks_sub),
                icon = { Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.REPEAT_TASKS) }
            )
        }
        item {
            SettingsMenuItem(
                title = stringResource(com.betteraichat.R.string.settings_automations),
                subtitle = stringResource(com.betteraichat.R.string.settings_automations_sub),
                icon = { Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.AUTOMATIONS) }
            )
        }
        item {
            SettingsMenuItem(
                title = stringResource(com.betteraichat.R.string.settings_stats),
                subtitle = stringResource(com.betteraichat.R.string.settings_stats_sub),
                icon = { Icon(Icons.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.STATS) }
            )
        }
        item {
            SettingsMenuItem(
                title = stringResource(com.betteraichat.R.string.settings_memories),
                subtitle = stringResource(com.betteraichat.R.string.settings_memories_sub),
                icon = { Icon(Icons.Filled.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.MEMORIES) }
            )
        }
        item {
            SettingsMenuItem(
                title = stringResource(com.betteraichat.R.string.settings_developer),
                subtitle = stringResource(com.betteraichat.R.string.settings_developer_sub),
                icon = { Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpenSection(SettingsSection.DEVELOPER) }
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
        Text(stringResource(R.string.settings_provider_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

        Text(stringResource(R.string.settings_provider_config, selectedProvider.displayName), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_api_key)) },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showKey = !showKey }) { Text(stringResource(if (showKey) R.string.settings_key_hide else R.string.settings_key_show)) }
            }
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_base_url, ModelCatalog.defaultBaseUrl(selectedProvider))) },
            singleLine = true
        )
        OutlinedButton(
            onClick = {
                if (apiKey.isBlank()) {
                    scope.launch { snackbar.showSnackbar(container.appContext.getString(R.string.settings_key_first)) }
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
                Text(stringResource(R.string.settings_detecting))
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_detect_models))
            }
        }

        HorizontalDivider()

        Text(stringResource(R.string.settings_model), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            label = { Text(stringResource(R.string.settings_model_id_hint)) },
            singleLine = true
        )

        Text(stringResource(R.string.settings_temperature, "%.1f".format(temperature)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                Text(stringResource(R.string.settings_reasoning), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_reasoning_sub),
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
                scope.launch { snackbar.showSnackbar(container.appContext.getString(R.string.settings_save_success)) }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_save))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConversationSection(
    modifier: Modifier,
    container: AppContainer,
    settings: SettingsRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: SnackbarHostState
) {
    val context = LocalContext.current
    var defaultMode by remember { mutableStateOf(settings.getDefaultMode()) }
    var themeMode by remember { mutableStateOf(settings.getThemeMode()) }
    var language by remember { mutableStateOf(settings.getLanguage()) }
    var autoSpeak by remember { mutableStateOf(settings.getAutoSpeak()) }
    var voiceAssistant by remember { mutableStateOf(settings.getVoiceAssistant()) }
    var accentColor by remember { mutableStateOf(settings.getAccentColor()) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.settings_default_mode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.settings_default_mode_sub),
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

        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.betteraichat.core.storage.AppLanguage.entries.forEach { lang ->
                FilterChip(
                    selected = language == lang,
                    onClick = {
                        language = lang
                        settings.setLanguage(lang)
                        context.startActivity(
                            Intent(context, com.betteraichat.MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        )
                    },
                    label = { Text(lang.displayName) }
                )
            }
        }

        HorizontalDivider()

        Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = {
                        themeMode = mode
                        settings.setThemeMode(mode)
                        container.bumpTheme()
                    },
                    label = { Text(mode.displayName) }
                )
            }
        }

        HorizontalDivider()

        Text(stringResource(R.string.settings_accent), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            com.betteraichat.core.storage.AccentColor.entries.forEach { c ->
                FilterChip(
                    selected = accentColor == c,
                    onClick = {
                        accentColor = c
                        settings.setAccentColor(c)
                        container.bumpTheme()
                    },
                    label = { Text(c.displayName) }
                )
            }
        }

        HorizontalDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_voice_assistant), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_voice_assistant_sub),
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
                Text(stringResource(R.string.settings_auto_speak), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_auto_speak_sub),
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
    var skills by remember {
        mutableStateOf<List<com.betteraichat.core.skills.Skill>>(emptyList())
    }
    LaunchedEffect(Unit) {
        skills = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            container.skillRepository.loadAll()
        }
    }
    val reloadSkills: () -> Unit = {
        scope.launch {
            skills = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                container.skillRepository.loadAll()
            }
        }
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
                result.onSuccess { scope.launch { snackbar.showSnackbar(context.getString(R.string.settings_skill_imported, it.name)) } }
                    .onFailure { scope.launch { snackbar.showSnackbar(context.getString(R.string.settings_skill_import_failed, it.message ?: "")) } }
                reloadSkills()
            }
        }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.settings_skills_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = { skillLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_import_skill))
        }
        if (skills.isEmpty()) {
            Text(
                stringResource(R.string.settings_skills_empty),
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
                            reloadSkills()
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.settings_skill_deleted, skill.name)) }
                        }) {
                            Text(stringResource(com.betteraichat.R.string.memories_delete), color = MaterialTheme.colorScheme.error)
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
        Text(stringResource(R.string.settings_shizuku), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.settings_shizuku_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PermissionRow(
            title = "Shizuku",
            status = when {
                !shizukuInstalled -> context.getString(R.string.shizuku_status_not_installed)
                !shizukuBinder -> context.getString(R.string.shizuku_status_not_started)
                !shizukuGranted -> context.getString(R.string.shizuku_status_unauthorized)
                else -> context.getString(R.string.shizuku_status_ready)
            },
            buttonText = when {
                !shizukuInstalled -> context.getString(R.string.shizuku_action_download)
                !shizukuBinder -> context.getString(R.string.shizuku_action_open)
                !shizukuGranted -> context.getString(R.string.shizuku_action_grant)
                else -> context.getString(R.string.shizuku_action_granted)
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

        Text(stringResource(R.string.settings_system_permissions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        PermissionRow(
            title = context.getString(R.string.perm_notification),
            status = if (notificationEnabled) context.getString(R.string.perm_granted) else context.getString(R.string.perm_denied),
            buttonText = context.getString(R.string.perm_action_grant),
            onAction = {
                if (Build.VERSION.SDK_INT >= 33) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.perm_notification_legacy)) }
                }
            }
        )
        PermissionRow(
            title = context.getString(R.string.perm_camera),
            status = if (hasCamera) context.getString(R.string.perm_granted) else context.getString(R.string.perm_denied),
            buttonText = context.getString(R.string.perm_action_grant),
            onAction = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
        )
        PermissionRow(
            title = context.getString(R.string.perm_write_settings),
            status = if (canWriteSettings) context.getString(R.string.perm_granted) else context.getString(R.string.perm_denied),
            buttonText = context.getString(R.string.perm_action_grant),
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
            title = context.getString(R.string.perm_screenshot),
            status = if (hasScreenshot) context.getString(R.string.perm_granted) else context.getString(R.string.perm_denied),
            buttonText = context.getString(R.string.perm_action_grant),
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
                context.getString(R.string.perm_screenshot_running)
            } else {
                context.getString(R.string.perm_screenshot_idle)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (container.screenshotManagerRef.isServiceRunning()) {
            OutlinedButton(
                onClick = { container.screenshotManagerRef.stopProjectionService() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.perm_stop_screenshot))
            }
        }

        HorizontalDivider()

        Text(stringResource(R.string.settings_accessibility), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.settings_accessibility_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        val accessibilityEnabled = remember { mutableStateOf(false) }
        val usageAccessGranted = remember { mutableStateOf(false) }
        fun pollAccessibility() {
            accessibilityEnabled.value = com.betteraichat.tools.BacAccessibilityService.connected()
            usageAccessGranted.value = checkUsageAccess(context)
        }
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) pollAccessibility()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(Unit) {
            while (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                pollAccessibility()
                delay(1500)
            }
        }
        PermissionRow(
            title = context.getString(R.string.perm_accessibility),
            status = if (accessibilityEnabled.value) context.getString(R.string.perm_on) else context.getString(R.string.perm_off),
            buttonText = if (accessibilityEnabled.value) context.getString(R.string.perm_on) else context.getString(R.string.perm_action_enable),
            onAction = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )
        PermissionRow(
            title = context.getString(R.string.perm_usage),
            status = if (usageAccessGranted.value) context.getString(R.string.perm_granted) else context.getString(R.string.perm_denied),
            buttonText = context.getString(R.string.perm_action_grant),
            onAction = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        )
        Text(
            stringResource(R.string.perm_usage_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PermissionRow(
            title = context.getString(R.string.perm_notif_listener),
            status = if (notificationListenerEnabled(context)) context.getString(R.string.perm_on) else context.getString(R.string.perm_off),
            buttonText = context.getString(R.string.perm_action_enable),
            onAction = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )
        Text(
            stringResource(R.string.perm_notif_listener_sub),
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
            stringResource(R.string.tasks_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (tasks.isEmpty()) {
            Text(
                stringResource(R.string.tasks_empty),
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
                                stringResource(R.string.tasks_next, intervalLabel(task), formatNext(task.nextTriggerAt)),
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
                            scope.launch { snackbar.showSnackbar(container.appContext.getString(R.string.tasks_deleted)) }
                        }) {
                            Text(stringResource(com.betteraichat.R.string.memories_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun intervalLabel(task: com.betteraichat.core.db.RepeatTaskEntity): String = when (task.interval) {
    "daily" -> stringResource(R.string.tasks_daily, task.time)
    "weekly" -> stringResource(R.string.tasks_weekly, task.weekday, task.time)
    else -> stringResource(R.string.tasks_hourly, task.everyHours)
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
        StatRow(stringResource(R.string.stats_conversations), conversations.toString())
        StatRow(stringResource(R.string.stats_user_msgs), userMessages.toString())
        StatRow(stringResource(R.string.stats_ai_replies), assistantMessages.toString())
        StatRow(stringResource(R.string.stats_input_tokens), formatCount(inputTokens))
        StatRow(stringResource(R.string.stats_output_tokens), formatCount(outputTokens))
        StatRow(stringResource(R.string.stats_tool_calls), toolCalls.toString())
        StatRow(stringResource(R.string.stats_tasks), repeatTasks.toString())
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
                    color = if (status == stringResource(R.string.perm_granted) ||
                        status == stringResource(R.string.shizuku_action_granted)
                    ) MaterialTheme.colorScheme.primary
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
            stringResource(R.string.automations_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (automations.isEmpty()) {
            Text(
                stringResource(R.string.automations_empty),
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
                                    "time" -> container.appContext.getString(R.string.automation_time, a.triggerValue,
                                        if (a.days == "all") container.appContext.getString(R.string.every_day) else a.days)
                                    "battery" -> container.appContext.getString(R.string.automation_battery,
                                        if (a.triggerValue.startsWith("low")) container.appContext.getString(R.string.below) else container.appContext.getString(R.string.above),
                                        a.triggerValue.substringAfter(":"))
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
                                }
                            }
                        )
                        TextButton(onClick = {
                            scope.launch {
                                container.db.automationDao().delete(a.id)
                                container.automationScheduler.cancel(a)
                                snackbar.showSnackbar(container.appContext.getString(R.string.automation_deleted, a.name))
                            }
                        }) {
                            Text(stringResource(com.betteraichat.R.string.memories_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DeveloperSection(
    modifier: Modifier,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: SnackbarHostState
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
    fun copyEmail(email: String) {
        clipboard.setText(AnnotatedString(email))
        scope.launch { snackbar.showSnackbar(context.getString(R.string.dev_email_copied, email)) }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("BetterAIChat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.dev_about_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.dev_version, appVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()

        Text(stringResource(com.betteraichat.R.string.dev_repo_info), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        var devInfo by remember { mutableStateOf<DeveloperInfo?>(null) }
        var devLoading by remember { mutableStateOf(true) }
        var devRetryTick by remember { mutableStateOf(0) }
        LaunchedEffect(devRetryTick) {
            devLoading = true
            devInfo = withContext(kotlinx.coroutines.Dispatchers.IO) { fetchDeveloperInfo() }
            devLoading = false
        }
        if (devLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(com.betteraichat.R.string.dev_loading), style = MaterialTheme.typography.bodySmall)
            }
        } else if (devInfo == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(com.betteraichat.R.string.dev_failed), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = { devRetryTick = devRetryTick + 1 }) {
                    Text(stringResource(com.betteraichat.R.string.retry))
                }
            }
        } else {
            val info = devInfo!!
            var devVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { devVisible = true }
            val breath by rememberInfiniteTransition(label = "avatar").animateFloat(
                initialValue = 1f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "avatarScale"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        )
                    )
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = devVisible,
                    enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 6 }
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color(0xFF0B0B0F),
                        onClick = { openUrl("https://github.com/${info.login}") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            val avatar = info.avatarBytes?.let { bytes ->
                                remember(bytes) {
                                    runCatching {
                                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    }.getOrNull()
                                }
                            }
                            val haloTransition = rememberInfiniteTransition(label = "halo")
                            val rotOuter by haloTransition.animateFloat(
                                0f, 360f,
                                infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
                                label = "rotOuter"
                            )
                            val rotMid by haloTransition.animateFloat(
                                0f, 360f,
                                infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
                                label = "rotMid"
                            )
                            val rotInner by haloTransition.animateFloat(
                                0f, 360f,
                                infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart),
                                label = "rotInner"
                            )
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .scale(breath)
                                    .drawBehind {
                                        val r = size.minDimension / 2
                                        val stroke = 3.dp.toPx()
                                        val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                                        fun haloRing(
                                            radius: Float,
                                            color: Color,
                                            startAngle: Float,
                                            ccw: Boolean = false
                                        ) {
                                            val base = if (ccw) -startAngle else startAngle
                                            drawArc(
                                                brush = Brush.sweepGradient(
                                                    center = center,
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        color,
                                                        color,
                                                        Color.Transparent
                                                    )
                                                ),
                                                startAngle = base,
                                                sweepAngle = 220f,
                                                useCenter = false,
                                                topLeft = androidx.compose.ui.geometry.Offset(
                                                    center.x - radius, center.y - radius
                                                ),
                                                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = stroke,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                            )
                                        }
                                        haloRing(r, Color(0xFF90CAF9), rotOuter)                        // 外圈浅蓝（顺时针）
                                        haloRing(r - 6.dp.toPx(), Color(0xFFF48FB1), rotMid, ccw = true)  // 中圈粉（逆时针）
                                        haloRing(r - 12.dp.toPx(), Color.White, rotInner)                // 内圈白（顺时针）
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatar != null) {
                                    Image(
                                        bitmap = avatar.asImageBitmap(),
                                        contentDescription = stringResource(R.string.dev_avatar),
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(50))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(
                                            info.login.take(1).uppercase(),
                                            modifier = Modifier.align(Alignment.Center),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    info.name.ifBlank { info.login },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "@${info.login}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF82B1FF)
                                )
                                if (info.bio.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    TypewriterText(
                                        info.bio,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB8B8C2),
                                        maxLines = 3
                                    )
                                }
                                if (info.location.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "📍 ${info.location}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF8A8A96)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = devVisible,
                enter = fadeIn(tween(500, delayMillis = 150)) + slideInVertically(tween(500, delayMillis = 150)) { it / 6 }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("${info.followers}", stringResource(com.betteraichat.R.string.dev_followers))
                    StatChip("${info.following}", stringResource(com.betteraichat.R.string.dev_following))
                    StatChip("${info.publicRepos}", stringResource(com.betteraichat.R.string.dev_repos))
                }
            }
            Spacer(Modifier.height(4.dp))
            androidx.compose.animation.AnimatedVisibility(
                visible = devVisible,
                enter = fadeIn(tween(500, delayMillis = 300)) + slideInVertically(tween(500, delayMillis = 300)) { it / 6 }
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { openUrl("https://github.com/Verlintas/BetterAIChat/releases/latest") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(com.betteraichat.R.string.dev_latest_release, info.latestVersion),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${info.latestTitle} · ${info.latestDate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
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
        }

        HorizontalDivider()

        Text(stringResource(R.string.dev_project), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            onClick = { openUrl("https://github.com/Verlintas/BetterAIChat") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dev_github_repo), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "github.com/Verlintas/BetterAIChat",
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
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            onClick = { openUrl("https://github.com/Verlintas/BetterAIChat/releases") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dev_releases), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.dev_releases_sub),
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

        HorizontalDivider()

        Text(stringResource(R.string.dev_about), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.dev_stack),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        Text(stringResource(R.string.dev_contact), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            onClick = { openUrl("https://x.com/Verlintas") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("X（Twitter）", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "x.com/Verlintas",
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

        HorizontalDivider()

        Text(stringResource(R.string.dev_email), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.dev_email_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        EmailRow("ulv777777@gmail.com", context.getString(R.string.dev_primary)) { copyEmail(it) }
        EmailRow("12321666@163.com", context.getString(R.string.dev_secondary)) { copyEmail(it) }
        EmailRow("orcakkk@gmail.com", context.getString(R.string.dev_secondary)) { copyEmail(it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EmailRow(
    email: String,
    label: String,
    onCopy: (String) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = { onCopy(email) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(email, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.copy),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private data class DeveloperInfo(
    val name: String,
    val login: String,
    val bio: String,
    val location: String,
    val followers: String,
    val following: String,
    val publicRepos: String,
    val createdAt: String,
    val avatarBytes: ByteArray?,
    val latestVersion: String,
    val latestTitle: String,
    val latestDate: String
)

@Composable
private fun StatChip(value: String, label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            AnimatedNumber(value, style = MaterialTheme.typography.labelLarge)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AnimatedNumber(value: String, style: androidx.compose.ui.text.TextStyle) {
    val suffix = if (value.endsWith("k")) "k" else ""
    val target = value.removeSuffix("k").toDoubleOrNull() ?: 0.0
    val animated = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(value) {
        animated.animateTo(
            target.toFloat(),
            animationSpec = androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        )
    }
    Text(
        if (suffix == "k") String.format(java.util.Locale.US, "%.1f", animated.value) + "k"
        else animated.value.toInt().toString(),
        style = style,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

private fun fetchDeveloperInfo(): DeveloperInfo? = runCatching {
    val userJson = fetchJson("https://api.github.com/users/Verlintas")
    val releaseJson = fetchJson("https://api.github.com/repos/Verlintas/BetterAIChat/releases/latest")
    val user = kotlinx.serialization.json.Json.parseToJsonElement(userJson).jsonObject
    val release = kotlinx.serialization.json.Json.parseToJsonElement(releaseJson).jsonObject
    fun fmt(n: Long): String = when {
        n >= 1000 -> "%.1fk".format(n / 1000.0)
        else -> n.toString()
    }
    val avatarUrl = user["avatar_url"]?.jsonPrimitive?.content
    val avatarBytes = avatarUrl?.let { url ->
        runCatching {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "BetterAIChat")
            try {
                if (conn.responseCode != 200) null
                else conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
    val rawName = runCatching { user["name"]?.jsonPrimitive?.content }.getOrNull()
        ?.takeIf { it.isNotBlank() && it != "null" } ?: ""
    val rawBio = runCatching { user["bio"]?.jsonPrimitive?.content }.getOrNull().orEmpty()
    DeveloperInfo(
        name = rawName,
        login = user["login"]?.jsonPrimitive?.content ?: "Verlintas",
        bio = android.text.Html.fromHtml(rawBio, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim(),
        location = user["location"]?.jsonPrimitive?.content ?: "",
        followers = fmt(user["followers"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0),
        following = fmt(user["following"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0),
        publicRepos = fmt(user["public_repos"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0),
        createdAt = user["created_at"]?.jsonPrimitive?.content?.substringBefore("T") ?: "",
        avatarBytes = avatarBytes,
        latestVersion = release["tag_name"]?.jsonPrimitive?.content ?: "unknown",
        latestTitle = release["name"]?.jsonPrimitive?.content ?: "",
        latestDate = release["published_at"]?.jsonPrimitive?.content?.substringBefore("T") ?: ""
    )
}.getOrNull()

private fun fetchJson(url: String): String {
    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = 12_000
    conn.readTimeout = 12_000
    conn.setRequestProperty("Accept", "application/vnd.github+json")
    conn.setRequestProperty("User-Agent", "BetterAIChat")
    try {
        if (conn.responseCode != 200) throw RuntimeException("HTTP ${conn.responseCode}")
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

@Composable
private fun TypewriterText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    maxLines: Int
) {
    var shown by remember(text) { mutableStateOf(0) }
    LaunchedEffect(text) {
        shown = 0
        val step = maxOf(1, text.length / 60)
        while (shown < text.length) {
            shown = minOf(text.length, shown + step)
            delay(12)
        }
    }
    Text(
        text.take(shown),
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun MemoriesSection(
    modifier: Modifier,
    container: AppContainer,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: SnackbarHostState
) {
    val context = LocalContext.current
    var memories by remember { mutableStateOf(emptyList<com.betteraichat.core.db.MemoryEntity>()) }
    var snapshots by remember { mutableStateOf(emptyList<com.betteraichat.core.db.MemoryEntity>()) }
    LaunchedEffect(Unit) {
        val all = runCatching { container.db.memoryDao().observeAll() }.getOrDefault(emptyList())
        memories = all.filter { it.type == "memory" }
        snapshots = all.filter { it.type == "snapshot" }
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(com.betteraichat.R.string.memories_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (memories.isEmpty()) {
            Text(
                stringResource(com.betteraichat.R.string.memories_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                stringResource(com.betteraichat.R.string.memories_count, memories.size),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            memories.forEach { m ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            m.content,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = {
                            scope.launch {
                                container.db.memoryDao().delete(m.id)
                                memories = memories.filter { it.id != m.id }
                                snackbar.showSnackbar(context.getString(com.betteraichat.R.string.memories_deleted))
                            }
                        }) {
                            Text(stringResource(com.betteraichat.R.string.memories_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Text(stringResource(com.betteraichat.R.string.memories_snapshots_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (snapshots.isEmpty()) {
            Text(
                stringResource(com.betteraichat.R.string.memories_snapshots_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            snapshots.forEach { snap ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(com.betteraichat.R.string.memories_snapshot_item, snap.content.length),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = {
                            scope.launch {
                                container.db.memoryDao().delete(snap.id)
                                snapshots = snapshots.filter { it.id != snap.id }
                                snackbar.showSnackbar(context.getString(com.betteraichat.R.string.memories_snapshot_deleted))
                            }
                        }) {
                            Text(stringResource(com.betteraichat.R.string.memories_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
