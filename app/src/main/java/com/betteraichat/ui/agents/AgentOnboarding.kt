package com.betteraichat.ui.agents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.betteraichat.R
import com.betteraichat.core.catalog.ModelCatalog
import com.betteraichat.core.catalog.ModelEntry
import com.betteraichat.core.model.ProviderId
import com.betteraichat.ui.rememberContainer
import com.betteraichat.ui.settings.ModelProbe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AgentOnboardingDialog(
    isEdit: Boolean = false,
    originalApiKey: String = "",
    initialName: String = "",
    initialProvider: ProviderId = ProviderId.OPENAI_COMPAT,
    initialBaseUrl: String = "",
    initialModel: String = "",
    initialTemperature: Double = 0.7,
    initialMaxTokens: Int = 4096,
    initialReasoning: Boolean = true,
    initialSystemPrompt: String = "",
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(initialName) }
    var provider by remember { mutableStateOf(initialProvider) }
    var baseUrl by remember { mutableStateOf(initialBaseUrl.ifBlank { ModelCatalog.defaultBaseUrl(initialProvider) }) }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(initialModel) }
    var temperature by remember { mutableStateOf(initialTemperature) }
    var maxTokens by remember { mutableStateOf(initialMaxTokens) }
    var reasoning by remember { mutableStateOf(initialReasoning) }
    var useCustomPrompt by remember { mutableStateOf(initialSystemPrompt.isNotBlank()) }
    var systemPrompt by remember { mutableStateOf(initialSystemPrompt) }
    var detectedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var probing by remember { mutableStateOf(false) }
    var probeStatus by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var probeGeneration by remember { mutableIntStateOf(0) }
    var modelFilter by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    val hasKey = apiKey.isNotBlank() || (isEdit && originalApiKey.isNotBlank())
    val credentialsReady = hasKey && baseUrl.isNotBlank()

    fun effectiveKey(): String = apiKey.ifBlank { if (isEdit) originalApiKey else "" }

    fun probe() {
        val key = effectiveKey()
        if (key.isBlank()) {
            probeStatus = context.getString(R.string.agent_probe_key_first)
            return
        }
        if (baseUrl.isBlank()) {
            probeStatus = context.getString(R.string.agent_credentials_required)
            return
        }
        val generation = ++probeGeneration
        probing = true
        probeStatus = context.getString(R.string.agent_detecting)
        scope.launch {
            val result = ModelProbe.probe(context, provider, baseUrl, key)
            if (generation != probeGeneration) return@launch
            probing = false
            if (result.ok) {
                detectedModels = result.models
                if (model.isBlank() && result.models.isNotEmpty()) model = result.models.first()
                probeStatus = if (result.models.isEmpty()) {
                    context.getString(R.string.agent_models_empty)
                } else {
                    result.message
                }
            } else {
                probeStatus = result.message
            }
        }
    }

    LaunchedEffect(step) { scroll.scrollTo(0) }

    LaunchedEffect(step, apiKey, baseUrl, provider) {
        val key = apiKey.ifBlank { if (isEdit) originalApiKey else "" }
        when {
            step == 0 && key.length >= 8 -> {
                detectedModels = emptyList()
                probeStatus = null
                delay(800)
                probe()
            }
            step == 1 && detectedModels.isEmpty() && key.isNotBlank() -> probe()
        }
    }

    fun save() {
        if (!credentialsReady) {
            step = 0
            probeStatus = context.getString(R.string.agent_credentials_required)
            return
        }
        if (model.isBlank()) {
            step = 1
            probeStatus = context.getString(R.string.agent_model_required)
            return
        }
        scope.launch {
            saving = true
            val effectiveKey = if (apiKey.isBlank() && isEdit) originalApiKey else apiKey
            container.agentRepository.save(
                com.betteraichat.core.db.AgentEntity(
                    name = name.ifBlank { context.getString(R.string.agent_untitled) },
                    description = model,
                    provider = provider.name,
                    baseUrl = baseUrl,
                    apiKey = effectiveKey,
                    model = model,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    reasoning = reasoning,
                    systemPrompt = if (useCustomPrompt) systemPrompt else "",
                    isDefault = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            saving = false
            onSaved()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.agent_onboarding_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(scroll)
            ) {
                Text(
                    stringResource(R.string.agent_step, step + 1, 4),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                when (step) {
                    0 -> {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.agent_name)) },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.agent_step1_hint), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { key ->
                                apiKey = key
                                val inferred = com.betteraichat.core.chat.inferProviderFromKey(key)
                                if (inferred != provider) {
                                    provider = inferred
                                    baseUrl = ModelCatalog.defaultBaseUrl(inferred)
                                }
                                if (name.isBlank() && key.length >= 8) name = context.getString(providerNameRes(inferred))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.agent_api_key)) },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                        ) {
                            ProviderId.entries.forEach { p ->
                                FilterChip(
                                    selected = provider == p,
                                    onClick = {
                                        provider = p
                                        baseUrl = ModelCatalog.defaultBaseUrl(p)
                                        if (ModelCatalog.modelsFor(p).none { it.id == model }) model = ""
                                    },
                                    label = { Text(stringResource(providerNameRes(p)), maxLines = 1) }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.agent_quick_providers),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                        ) {
                            QUICK_PROVIDERS.forEach { quick ->
                                FilterChip(
                                    selected = baseUrl.trimEnd('/') == quick.baseUrl.trimEnd('/'),
                                    onClick = {
                                        provider = ProviderId.OPENAI_COMPAT
                                        baseUrl = quick.baseUrl
                                        model = quick.model
                                        if (name.isBlank()) name = quick.label
                                    },
                                    label = { Text(quick.label, maxLines = 1) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.agent_base_url)) },
                            singleLine = true
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = ::probe, enabled = !probing, modifier = Modifier.fillMaxWidth()) {
                            if (probing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.agent_detecting))
                            } else {
                                Text(stringResource(R.string.agent_detect))
                            }
                        }
                        probeStatus?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    1 -> {
                        Text(stringResource(R.string.agent_step2_hint), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (probing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = probeStatus ?: stringResource(
                                    if (hasKey) R.string.agent_detecting else R.string.agent_probe_key_first
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = ::probe, enabled = !probing) {
                                Text(stringResource(R.string.agent_detect))
                            }
                        }
                        if (detectedModels.isNotEmpty()) {
                            Text(stringResource(R.string.agent_detected_models, detectedModels.size), style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(6.dp))
                            if (detectedModels.size > 12) {
                                OutlinedTextField(
                                    value = modelFilter,
                                    onValueChange = { modelFilter = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.agent_filter_models)) },
                                    singleLine = true
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                            val visibleDetected = detectedModels.filter {
                                modelFilter.isBlank() || it.contains(modelFilter.trim(), ignoreCase = true)
                            }
                            visibleDetected.take(50).forEach { m ->
                                FilterChip(
                                    selected = model.equals(m, ignoreCase = true),
                                    onClick = { model = m },
                                    label = { Text(m, maxLines = 1) }
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                        } else if (!probing) {
                            Text(
                                stringResource(R.string.agent_models_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.agent_catalog_models), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        catalogFor(provider, baseUrl).forEach { entry ->
                            FilterChip(
                                selected = model == entry.id,
                                onClick = { model = entry.id },
                                label = { Text(entry.label, maxLines = 1) }
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.agent_model_id)) },
                            singleLine = true
                        )
                    }
                    2 -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.agent_temperature), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    stringResource(R.string.agent_temperature_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("%.1f".format(temperature), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = temperature.toFloat(),
                            onValueChange = { temperature = it.toDouble() },
                            valueRange = 0f..2f
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = maxTokens.toString(),
                            onValueChange = { maxTokens = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.agent_max_tokens)) },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.agent_reasoning), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    stringResource(R.string.agent_reasoning_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            androidx.compose.material3.Switch(checked = reasoning, onCheckedChange = { reasoning = it })
                        }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !useCustomPrompt, onClick = { useCustomPrompt = false })
                            Text(stringResource(R.string.agent_prompt_default))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = useCustomPrompt, onClick = { useCustomPrompt = true })
                            Text(stringResource(R.string.agent_prompt_custom))
                        }
                        if (useCustomPrompt) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = systemPrompt,
                                onValueChange = { systemPrompt = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.agent_prompt_hint)) },
                                minLines = 4
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.agent_prompt_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (step) {
                        0 -> step = 1
                        1 -> step = 2
                        2 -> step = 3
                        else -> save()
                    }
                },
                enabled = !saving && when (step) {
                    0 -> credentialsReady
                    1 -> model.isNotBlank()
                    else -> true
                }
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(
                            if (step < 3) R.string.agent_next else R.string.agent_save
                        )
                    )
                }
            }
        },
        dismissButton = {
            Row {
                if (step > 0) {
                    OutlinedButton(onClick = { step -= 1 }) {
                        Text(stringResource(R.string.agent_back))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

private fun providerNameRes(provider: ProviderId): Int = when (provider) {
    ProviderId.OPENAI_COMPAT -> R.string.provider_openai_compat
    ProviderId.ANTHROPIC -> R.string.provider_anthropic
    ProviderId.GEMINI -> R.string.provider_gemini
}

private data class QuickProvider(val label: String, val baseUrl: String, val model: String)
private val QUICK_PROVIDERS = listOf(
    QuickProvider("DeepSeek", "https://api.deepseek.com/v1", "deepseek-v4-flash"),
    QuickProvider("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3.8-max"),
    QuickProvider("Kimi", "https://api.moonshot.cn/v1", "kimi-k3"),
    QuickProvider("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-5.3"),
    QuickProvider("SiliconFlow", "https://api.siliconflow.cn/v1", "deepseek-v4-pro")
)

private fun catalogFor(provider: ProviderId, baseUrl: String): List<ModelEntry> {
    val all = ModelCatalog.modelsFor(provider)
    val prefix = when {
        baseUrl.contains("deepseek", ignoreCase = true) -> "deepseek-"
        baseUrl.contains("moonshot", ignoreCase = true) || baseUrl.contains("kimi", ignoreCase = true) -> "kimi-"
        baseUrl.contains("dashscope", ignoreCase = true) || baseUrl.contains("aliyun", ignoreCase = true) -> "qwen"
        baseUrl.contains("bigmodel", ignoreCase = true) || baseUrl.contains("zhipu", ignoreCase = true) -> "glm-"
        baseUrl.contains("siliconflow", ignoreCase = true) -> "deepseek-"
        baseUrl.contains("openai.com", ignoreCase = true) -> "gpt-"
        else -> return all
    }
    return all.sortedByDescending { it.id.startsWith(prefix) }
}
