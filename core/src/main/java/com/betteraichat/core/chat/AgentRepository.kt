package com.betteraichat.core.chat

import com.betteraichat.core.db.AgentDao
import com.betteraichat.core.db.AgentEntity
import com.betteraichat.core.db.AppDatabase
import com.betteraichat.core.model.ProviderConfig
import com.betteraichat.core.model.ProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

fun inferProviderFromKey(key: String): ProviderId = when {
    key.trim().startsWith("sk-ant-") -> ProviderId.ANTHROPIC
    key.trim().startsWith("AIza") -> ProviderId.GEMINI
    else -> ProviderId.OPENAI_COMPAT
}

class AgentRepository(
    private val db: AppDatabase,
    private val crypto: com.betteraichat.core.storage.KeyStoreCrypto
) {

    private val dao: AgentDao = db.agentDao()

    private fun encryptIfPlain(apiKey: String): String {
        if (apiKey.isBlank() || apiKey.startsWith("enc:")) return if (apiKey.startsWith("enc:")) apiKey else ""
        return "enc:" + crypto.encrypt(apiKey)
    }

    private fun decrypt(apiKey: String): String {
        if (apiKey.startsWith("enc:")) {
            return crypto.decrypt(apiKey.removePrefix("enc:"))
        }
        return apiKey
    }

    fun observeAll(): Flow<List<AgentEntity>> = dao.observeAll()

    suspend fun getById(id: Long): AgentEntity? = dao.getById(id)

    suspend fun getDefault(): AgentEntity? = dao.getDefault()

    suspend fun count(): Long = dao.count()

    suspend fun save(agent: AgentEntity): Long {
        val now = System.currentTimeMillis()
        val stored = agent.copy(apiKey = encryptIfPlain(agent.apiKey))
        val id = if (agent.id > 0) {
            dao.update(stored.copy(updatedAt = now))
            agent.id
        } else {
            if (dao.count() == 0L) dao.insert(stored.copy(isDefault = true, createdAt = now, updatedAt = now))
            else dao.insert(stored.copy(createdAt = now, updatedAt = now))
        }
        return id
    }

    suspend fun setDefault(id: Long) {
        dao.clearDefault()
        dao.setDefault(id)
    }

    suspend fun delete(id: Long) {
        val agent = dao.getById(id) ?: return
        dao.deleteById(id)
        if (agent.isDefault) {
            val all = dao.observeAll().first()
            all.firstOrNull()?.let { dao.setDefault(it.id) }
        }
    }

    fun toConfig(agent: AgentEntity): ProviderConfig = ProviderConfig(
        provider = runCatching { ProviderId.valueOf(agent.provider) }.getOrDefault(ProviderId.OPENAI_COMPAT),
        baseUrl = agent.baseUrl,
        apiKey = decrypt(agent.apiKey),
        model = agent.model,
        temperature = agent.temperature,
        maxTokens = agent.maxTokens,
        reasoning = agent.reasoning,
        systemPrompt = agent.systemPrompt
    )
}
