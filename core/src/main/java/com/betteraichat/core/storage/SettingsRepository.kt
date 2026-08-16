package com.betteraichat.core.storage

import android.content.Context
import com.betteraichat.core.catalog.ModelCatalog
import com.betteraichat.core.mode.AppMode
import com.betteraichat.core.model.ProviderConfig
import com.betteraichat.core.model.ProviderId

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val keys = KeyStoreCrypto(context)

    private fun providerKey(provider: ProviderId, suffix: String) = "${provider.name}_$suffix"

    fun getApiKey(provider: ProviderId): String = keys.get(providerKey(provider, "apikey"))

    fun setApiKey(provider: ProviderId, key: String) {
        if (key.isBlank()) {
            prefs.edit().remove(providerKey(provider, "apikey")).apply()
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
