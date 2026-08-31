package com.betteraichat.core.storage

import android.content.Context
import com.betteraichat.core.catalog.ModelCatalog
import com.betteraichat.core.mode.AppMode
import com.betteraichat.core.model.ProviderConfig
import com.betteraichat.core.model.ProviderId

enum class ThemeMode(val displayName: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

enum class AppLanguage(val displayName: String) {
    SYSTEM("跟随系统"),
    ZH("中文"),
    EN("English")
}

enum class AccentColor(val displayName: String) {
    ORANGE("橙"),
    RED("红"),
    PINK("粉"),
    INDIGO("靛蓝"),
    BLUE("蓝"),
    PURPLE("紫"),
    GREEN("绿"),
    TEAL("青")
}

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val keys = KeyStoreCrypto(context)

    private fun providerKey(provider: ProviderId, suffix: String) = "${provider.name}_$suffix"

    fun getApiKey(provider: ProviderId): String = keys.get(providerKey(provider, "apikey"))

    fun setApiKey(provider: ProviderId, key: String) {
        if (key.isBlank()) {
            keys.remove(providerKey(provider, "apikey"))
            return
        }
        keys.put(providerKey(provider, "apikey"), key)
    }

    fun getBaseUrl(provider: ProviderId): String =
        prefs.getString(providerKey(provider, "baseurl"), ModelCatalog.defaultBaseUrl(provider))!!

    fun setBaseUrl(provider: ProviderId, url: String) {
        prefs.edit().putString(providerKey(provider, "baseurl"), url.ifBlank { ModelCatalog.defaultBaseUrl(provider) }).apply()
    }

    fun getModel(provider: ProviderId): String =
        prefs.getString(providerKey(provider, "model"), ModelCatalog.defaultModel(provider))!!

    fun setModel(provider: ProviderId, model: String) {
        prefs.edit().putString(providerKey(provider, "model"), model.ifBlank { ModelCatalog.defaultModel(provider) }).apply()
    }

    fun getTemperature(provider: ProviderId): Double =
        prefs.getFloat(providerKey(provider, "temp"), ModelCatalog.entryFor(provider, getModel(provider)).temperature.toFloat()).toDouble()

    fun setTemperature(provider: ProviderId, temp: Double) {
        prefs.edit().putFloat(providerKey(provider, "temp"), temp.toFloat()).apply()
    }

    fun getMaxTokens(provider: ProviderId): Int =
        prefs.getInt(providerKey(provider, "maxtokens"), ModelCatalog.entryFor(provider, getModel(provider)).maxTokens)

    fun setMaxTokens(provider: ProviderId, tokens: Int) {
        prefs.edit().putInt(providerKey(provider, "maxtokens"), tokens).apply()
    }

    fun getReasoning(provider: ProviderId): Boolean =
        prefs.getBoolean(providerKey(provider, "reasoning"), true)

    fun setReasoning(provider: ProviderId, enabled: Boolean) {
        prefs.edit().putBoolean(providerKey(provider, "reasoning"), enabled).apply()
    }

    fun getCustomModels(provider: ProviderId): List<String> {
        val raw = prefs.getString(providerKey(provider, "custommodels"), null) ?: return emptyList()
        return runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<List<String>>(raw)
        }.getOrDefault(emptyList())
    }

    fun setCustomModels(provider: ProviderId, models: List<String>) {
        val raw = runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .encodeToString(models)
        }.getOrNull() ?: return
        prefs.edit().putString(providerKey(provider, "custommodels"), raw).apply()
    }

    fun getDefaultMode(): AppMode = runCatching {
        AppMode.valueOf(prefs.getString("default_mode", AppMode.CHAT.name)!!)
    }.getOrDefault(AppMode.CHAT)

    fun setDefaultMode(mode: AppMode) {
        prefs.edit().putString("default_mode", mode.name).apply()
    }

    fun getDefaultProvider(): ProviderId = runCatching {
        ProviderId.valueOf(prefs.getString("default_provider", ProviderId.OPENAI_COMPAT.name)!!)
    }.getOrDefault(ProviderId.OPENAI_COMPAT)

    fun setDefaultProvider(provider: ProviderId) {
        prefs.edit().putString("default_provider", provider.name).apply()
    }

    fun getThemeMode(): ThemeMode = runCatching {
        ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name)!!)
    }.getOrDefault(ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun getAutoSpeak(): Boolean = prefs.getBoolean("auto_speak", false)

    fun setAutoSpeak(enabled: Boolean) {
        prefs.edit().putBoolean("auto_speak", enabled).apply()
    }

    fun getVoiceAssistant(): Boolean = prefs.getBoolean("voice_assistant", false)

    fun setVoiceAssistant(enabled: Boolean) {
        prefs.edit().putBoolean("voice_assistant", enabled).apply()
    }

    fun getLanguage(): AppLanguage = runCatching {
        AppLanguage.valueOf(prefs.getString("language", AppLanguage.SYSTEM.name)!!)
    }.getOrDefault(AppLanguage.SYSTEM)

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString("language", language.name).apply()
    }

    fun getAccentColor(): AccentColor = runCatching {
        AccentColor.valueOf(prefs.getString("accent_color", AccentColor.ORANGE.name)!!)
    }.getOrDefault(AccentColor.ORANGE)

    fun setAccentColor(accent: AccentColor) {
        prefs.edit().putString("accent_color", accent.name).apply()
    }

    fun configFor(provider: ProviderId): ProviderConfig = ProviderConfig(
        provider = provider,
        baseUrl = getBaseUrl(provider),
        apiKey = getApiKey(provider),
        model = getModel(provider),
        temperature = getTemperature(provider),
        maxTokens = getMaxTokens(provider),
        reasoning = getReasoning(provider)
    )
}
