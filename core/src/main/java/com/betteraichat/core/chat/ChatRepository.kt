package com.betteraichat.core.chat

import com.betteraichat.core.db.AppDatabase
import com.betteraichat.core.db.ConversationEntity
import com.betteraichat.core.db.MessageEntity
import com.betteraichat.core.mode.AppMode
import com.betteraichat.core.model.Attachment
import com.betteraichat.core.model.ChatMessage
import com.betteraichat.core.model.ChatRole
import com.betteraichat.core.model.ProviderId
import com.betteraichat.core.model.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class ChatRepository(private val db: AppDatabase) {

    private val json = Json { ignoreUnknownKeys = true }

    fun observeConversations(): Flow<List<ConversationEntity>> = db.conversationDao().observeAll()

    fun observeConversation(id: Long): Flow<ConversationEntity?> = db.conversationDao().observeById(id)

    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> = db.messageDao().observeForConversation(conversationId)

    suspend fun createConversation(provider: ProviderId, model: String, mode: AppMode): Long =
        db.conversationDao().insert(
            ConversationEntity(
                title = "新对话",
                provider = provider.name,
                model = model,
                mode = mode.name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

    suspend fun getConversation(id: Long): ConversationEntity? = db.conversationDao().getById(id)

    suspend fun deleteConversation(id: Long) {
        db.conversationDao().deleteMessages(id)
        db.conversationDao().deleteById(id)
    }

    suspend fun updateTitle(id: Long, title: String) {
        val c = db.conversationDao().getById(id) ?: return
        db.conversationDao().update(c.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateMeta(id: Long, model: String, mode: AppMode) {
        val c = db.conversationDao().getById(id) ?: return
        db.conversationDao().update(c.copy(model = model, mode = mode.name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun insertMessage(entity: MessageEntity): Long = db.messageDao().insert(entity)

    suspend fun updateMessage(entity: MessageEntity) = db.messageDao().update(entity)

    suspend fun updateMessageContent(id: Long, content: String) = db.messageDao().updateContent(id, content)

    suspend fun getHistory(conversationId: Long): List<MessageEntity> = db.messageDao().getForConversation(conversationId)

    suspend fun clearMessages(conversationId: Long) = db.messageDao().deleteAllForConversation(conversationId)

    fun messageToDomain(e: MessageEntity): ChatMessage = ChatMessage(
        role = runCatching { ChatRole.valueOf(e.role) }.getOrDefault(ChatRole.USER),
        content = e.content,
        toolCalls = e.toolCallsJson?.let {
            runCatching { json.decodeFromString<List<ToolCall>>(it) }.getOrDefault(emptyList())
        } ?: emptyList(),
        toolCallId = e.toolCallId,
        toolName = e.toolName,
        model = e.model,
        mode = e.mode?.let { runCatching { AppMode.valueOf(it) }.getOrNull() },
        attachments = e.attachmentsJson?.let {
            runCatching { json.decodeFromString<List<Attachment>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
    )

    fun domainToMessage(m: ChatMessage, conversationId: Long, status: String = "done"): MessageEntity = MessageEntity(
        conversationId = conversationId,
        role = m.role.name,
        content = m.content,
        toolCallsJson = m.toolCalls.takeIf { it.isNotEmpty() }
            ?.let { json.encodeToString(ListSerializer(ToolCall.serializer()), it) },
        toolCallId = m.toolCallId,
        toolName = m.toolName,
        model = m.model,
        mode = m.mode?.name,
        status = status,
        usageInput = 0,
        usageOutput = 0,
        attachmentsJson = m.attachments.takeIf { it.isNotEmpty() }
            ?.let { json.encodeToString(ListSerializer(Attachment.serializer()), it) },
        createdAt = System.currentTimeMillis()
    )
}
