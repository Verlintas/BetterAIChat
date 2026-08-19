package com.betteraichat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.betteraichat.AppContainer
import com.betteraichat.core.chat.ChatRepository
import com.betteraichat.core.db.MessageEntity
import com.betteraichat.core.engine.ChatEngine
import com.betteraichat.core.engine.EngineEvent
import com.betteraichat.core.mode.AppMode
import com.betteraichat.core.model.ChatMessage
import com.betteraichat.core.model.ChatRole
import com.betteraichat.core.model.ProviderId
import com.betteraichat.core.model.StreamEvent
import com.betteraichat.core.model.ToolCall
import com.betteraichat.core.model.ToolCallStatus
import com.betteraichat.core.storage.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class PendingAttachment(
    val id: Long,
    val name: String,
    val kind: String,
    val mimeType: String,
    val uri: android.net.Uri
)

data class UiMessage(
    val id: Long,
    val role: ChatRole,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val model: String? = null,
    val mode: AppMode? = null,
    val streaming: Boolean = false,
    val usageInput: Long = 0,
    val usageOutput: Long = 0,
    val attachments: List<com.betteraichat.core.model.Attachment> = emptyList(),
    val thinking: String = "",
    val createdAt: Long = 0
)

data class ConfirmRequest(val call: ToolCall, val mode: AppMode)

data class ChatUiState(
    val conversationId: Long = -1,
    val title: String = "新对话",
    val provider: ProviderId = ProviderId.OPENAI_COMPAT,
    val model: String = "",
    val mode: AppMode = AppMode.CHAT,
    val messages: List<UiMessage> = emptyList(),
    val isRunning: Boolean = false,
    val input: String = "",
    val confirmRequest: ConfirmRequest? = null,
    val error: String? = null,
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val attachmentError: String? = null,
    val processing: Boolean = false,
    val sendTick: Int = 0
)

class ChatViewModel(
    private val conversationId: Long,
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val engine: ChatEngine,
    private val providerFactory: (ProviderId) -> com.betteraichat.core.provider.ChatProvider,
    private val appContext: android.content.Context
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()

    private var dbMessages: List<UiMessage> = emptyList()
    private var streaming: UiMessage? = null
    private var currentConversationId: Long = conversationId
    private var runJob: Job? = null
    private var streamTickerJob: Job? = null
    private var pendingAssistantEntity: MessageEntity? = null
    private var roundCounter = 0
    private var streamingRound = -1
    private var pendingRound = -1
    private var sendCancelled = false

    init {
        viewModelScope.launch {
            engine.confirmRequests.collect { call ->
                if (runJob?.isActive == true) {
                    _state.update { it.copy(confirmRequest = ConfirmRequest(call, it.mode)) }
                }
            }
        }
        if (conversationId > 0) {
            loadConversation(conversationId)
        } else {
            val provider = settings.getDefaultProvider()
            _state.update {
                it.copy(
                    provider = provider,
                    model = settings.getModel(provider),
                    mode = settings.getDefaultMode()
                )
            }
        }
    }

    private fun loadConversation(id: Long) {
        viewModelScope.launch {
            val c = repository.getConversation(id)
            if (c == null) {
                currentConversationId = -1
                _state.update {
                    it.copy(
                        conversationId = -1,
                        title = "新对话",
                        provider = settings.getDefaultProvider()
                    )
                }
                val provider = settings.getDefaultProvider()
                _state.update {
                    it.copy(
                        model = settings.getModel(provider),
                        mode = settings.getDefaultMode()
                    )
                }
                return@launch
            }
            val provider = runCatching { ProviderId.valueOf(c.provider) }.getOrDefault(ProviderId.OPENAI_COMPAT)
            val mode = runCatching { AppMode.valueOf(c.mode) }.getOrDefault(AppMode.CHAT)
            _state.update {
                it.copy(
                    conversationId = id,
                    title = c.title,
                    provider = provider,
                    model = c.model,
                    mode = mode
                )
            }
            startObserving(id)
        }
    }

    private fun startObserving(id: Long) {
        viewModelScope.launch {
            repository.observeMessages(id).collect { list ->
                dbMessages = list.map { e ->
                    UiMessage(
                        id = e.id,
                        role = runCatching { ChatRole.valueOf(e.role) }.getOrDefault(ChatRole.USER),
                        content = e.content,
                        toolCalls = e.toolCallsJson?.let {
                            runCatching { json.decodeFromString<List<ToolCall>>(it) }.getOrDefault(emptyList())
                        } ?: emptyList(),
                        model = e.model,
                        mode = e.mode?.let { runCatching { AppMode.valueOf(it) }.getOrNull() },
                        streaming = false,
                        usageInput = e.usageInput,
                        usageOutput = e.usageOutput,
                        attachments = e.attachmentsJson?.let {
                            runCatching { json.decodeFromString<List<com.betteraichat.core.model.Attachment>>(it) }
                                .getOrDefault(emptyList())
                        } ?: emptyList(),
                        createdAt = e.createdAt
                    )
                }
                refresh()
            }
        }
    }

    private fun refresh() {
        _state.update { it.copy(messages = dbMessages + listOfNotNull(streaming)) }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text) }
    }

    private var attachmentCounter = 0L

    fun addPendingImages(uris: List<android.net.Uri>, resolver: android.content.ContentResolver) {
        val items = uris.map { uri ->
            val name = AttachmentProcessor.queryNameFromResolver(resolver, uri)
            attachmentCounter++
            PendingAttachment(attachmentCounter, name, "image", resolver.getType(uri) ?: "image/*", uri)
        }
        _state.update { it.copy(pendingAttachments = it.pendingAttachments + items, attachmentError = null) }
    }

    fun addPendingFile(uri: android.net.Uri, resolver: android.content.ContentResolver) {
        val name = AttachmentProcessor.queryNameFromResolver(resolver, uri)
        attachmentCounter++
        _state.update {
            it.copy(
                pendingAttachments = it.pendingAttachments + PendingAttachment(
                    attachmentCounter, name, "file", resolver.getType(uri) ?: "*/*", uri
                ),
                attachmentError = null
            )
        }
    }

    fun removePendingAttachment(id: Long) {
        _state.update { it.copy(pendingAttachments = it.pendingAttachments.filterNot { a -> a.id == id }) }
    }

    fun send() {
        val text = _state.value.input.trim()
        val pending = _state.value.pendingAttachments
        if ((text.isEmpty() && pending.isEmpty()) || _state.value.isRunning) return
        sendCancelled = false
        _state.update { it.copy(isRunning = true) }
        viewModelScope.launch {
            if (currentConversationId <= 0) {
                val s = _state.value
                val cid = repository.createConversation(s.provider, s.model, s.mode)
                currentConversationId = cid
                repository.updateTitle(cid, text.take(30).ifBlank { "对话" })
                _state.update { it.copy(conversationId = cid, title = text.take(30).ifBlank { "对话" }) }
                startObserving(cid)
            }
            val cid = currentConversationId
            val s = _state.value
            val attachments = processPending(s.pendingAttachments)
            if (sendCancelled) {
                sendCancelled = false
                _state.update { it.copy(isRunning = false, pendingAttachments = emptyList()) }
                return@launch
            }
            if (attachments.isEmpty() && text.isEmpty()) {
                _state.update { it.copy(isRunning = false, pendingAttachments = emptyList()) }
                return@launch
            }
            if (attachments.isNotEmpty() && text.isEmpty()) {
                _state.update { it.copy(attachmentError = null) }
            }
            repository.insertMessage(
                repository.domainToMessage(
                    ChatMessage(
                        role = ChatRole.USER,
                        content = text,
                        model = s.model,
                        mode = s.mode,
                        attachments = attachments
                    ),
                    cid
                )
            )
            _state.update {
                it.copy(input = "", error = null, pendingAttachments = emptyList(), sendTick = it.sendTick + 1)
            }
            runGeneration(cid)
        }
    }

    private suspend fun processPending(pending: List<PendingAttachment>): List<com.betteraichat.core.model.Attachment> {
        if (pending.isEmpty()) return emptyList()
        val context = appContext ?: return emptyList()
        _state.update { it.copy(processing = true) }
        try {
            val result = mutableListOf<com.betteraichat.core.model.Attachment>()
            for (p in pending) {
                val r = if (p.kind == "image") {
                    AttachmentProcessor.imageFromUri(context, p.uri, p.name)
                } else {
                    AttachmentProcessor.docFromUri(context, p.uri, p.name)
                }
                r.onSuccess { result.add(it) }
                    .onFailure { _state.update { st -> st.copy(attachmentError = it.message) } }
            }
            return result
        } finally {
            _state.update { it.copy(processing = false) }
        }
    }

    private fun newStreamingMessage(config: com.betteraichat.core.model.ProviderConfig, s: ChatUiState): UiMessage {
        streamingRound = roundCounter
        return UiMessage(
            id = -1, role = ChatRole.ASSISTANT, content = "",
            model = config.model, mode = s.mode, streaming = true
        )
    }

    private fun runGeneration(cid: Long) {
        val s = _state.value
        val config = settings.configFor(s.provider)
        streaming = newStreamingMessage(config, s)
        refresh()
        var currentJob: Job? = null
        currentJob = viewModelScope.launch {
            streamTickerJob = viewModelScope.launch {
                while (true) {
                    delay(100)
                    if (streaming != null) refresh()
                }
            }
            val history = repository.getHistory(cid).map { repository.messageToDomain(it) }
            try {
                engine.run(history, config, s.mode).collect { ev ->
                    when (ev) {
                        is EngineEvent.Delta -> {
                            if (streaming == null) {
                                streaming = newStreamingMessage(config, s)
                            }
                            streaming = streaming?.copy(content = (streaming?.content ?: "") + ev.text)
                        }
                        is EngineEvent.ThinkingDelta -> {
                            if (streaming == null) {
                                streaming = newStreamingMessage(config, s)
                            }
                            streaming = streaming?.copy(thinking = (streaming?.thinking ?: "") + ev.text)
                        }
                        is EngineEvent.ToolCallStarted -> {
                            if (streaming == null) {
                                streaming = newStreamingMessage(config, s)
                            }
                            streaming = streaming?.copy(
                                toolCalls = upsertCall(
                                    streaming?.toolCalls ?: emptyList(),
                                    ev.call.copy(status = ToolCallStatus.RUNNING)
                                )
                            )
                        }
                        is EngineEvent.ToolCallFinished -> {
                            if (streaming == null) {
                                streaming = newStreamingMessage(config, s)
                            }
                            streaming = streaming?.copy(
                                toolCalls = upsertCall(streaming?.toolCalls ?: emptyList(), ev.call)
                            )
                            persistToolResult(ev.call, cid)
                            refresh()
                        }
                        is EngineEvent.Usage -> {
                            if (streaming == null) {
                                streaming = newStreamingMessage(config, s)
                            }
                            streaming = streaming?.copy(usageInput = ev.promptTokens, usageOutput = ev.completionTokens)
                        }
                        is EngineEvent.AssistantFinished -> {
                            val msg = ev.message
                            val entity = repository.domainToMessage(
                                ChatMessage(
                                    role = ChatRole.ASSISTANT,
                                    content = msg.content,
                                    toolCalls = msg.toolCalls.map { it.copy(status = ToolCallStatus.PENDING) },
                                    model = config.model,
                                    mode = s.mode,
                                    thinkingText = msg.thinkingText,
                                    thinkingSignature = msg.thinkingSignature
                                ),
                                cid
                            ).copy(
                                usageInput = streaming?.usageInput ?: 0,
                                usageOutput = streaming?.usageOutput ?: 0
                            )
                            val id = repository.insertMessage(entity)
                            pendingAssistantEntity = entity.copy(id = id)
                            pendingRound = roundCounter
                            roundCounter++
                            streaming = null
                            refresh()
                        }
                        is EngineEvent.Failed -> {
                            _state.update { it.copy(error = smartError(ev.message)) }
                            persistStreamingPartial(cid)
                            streaming = null
                            refresh()
                        }
                        is EngineEvent.ConfirmRequested -> Unit
                        EngineEvent.Completed -> Unit
                    }
                }
            } finally {
                streamTickerJob?.cancel()
                streamTickerJob = null
                refresh()
                if (runJob === currentJob) {
                    _state.update { it.copy(isRunning = false) }
                }
            }
        }
        runJob = currentJob
    }

    private fun smartError(raw: String): String = when {
        raw.contains("401") -> "API Key 无效或已过期，请到设置页检查（HTTP 401）"
        raw.contains("403") -> "API Key 无权限访问该资源（HTTP 403）"
        raw.contains("404") -> "模型不存在或 Base URL 不正确，请到设置页检测连接（HTTP 404）"
        raw.contains("429") -> "请求过于频繁或额度不足，请稍后重试（HTTP 429）"
        raw.contains("timeout", ignoreCase = true) || raw.contains("timed out", ignoreCase = true) ->
            "网络超时，请检查网络连接后重试"
        raw.contains("failed to connect", ignoreCase = true) || raw.contains("connect timed out", ignoreCase = true) ->
            "无法连接到服务，请检查 Base URL 与网络"
        raw.contains("cancelled", ignoreCase = true) -> "已取消"
        else -> raw
    }

    private fun upsertCall(calls: List<ToolCall>, updated: ToolCall): List<ToolCall> {
        val idx = calls.indexOfFirst { it.id == updated.id }
        return if (idx >= 0) calls.toMutableList().apply { this[idx] = updated } else calls + updated
    }

    private suspend fun persistToolResult(call: ToolCall, cid: Long) {
        repository.insertMessage(
            repository.domainToMessage(
                ChatMessage(
                    role = ChatRole.TOOL,
                    content = call.result ?: "",
                    toolCallId = call.id,
                    toolName = call.name
                ),
                cid
            )
        )
        pendingAssistantEntity = pendingAssistantEntity?.copy(
            toolCallsJson = runCatching {
                json.encodeToString(
                    ListSerializer(ToolCall.serializer()),
                    (pendingAssistantEntity?.toolCallsJson?.let {
                        runCatching { json.decodeFromString<List<ToolCall>>(it) }.getOrDefault(emptyList())
                    } ?: emptyList())
                        .map { c -> if (c.id == call.id) call else c }
                )
            }.getOrNull()
        )
        pendingAssistantEntity?.let { repository.updateMessage(it) }
    }

    private suspend fun persistStreamingPartial(cid: Long) {
        val st = streaming ?: return
        if (st.content.isBlank() && st.toolCalls.isEmpty()) return
        val sameRound = pendingAssistantEntity != null && pendingRound == streamingRound
        if (sameRound) {
            repository.updateMessage(pendingAssistantEntity!!.copy(content = st.content))
        } else {
            repository.insertMessage(
                repository.domainToMessage(
                    ChatMessage(
                        role = ChatRole.ASSISTANT,
                        content = st.content,
                        model = st.model,
                        mode = st.mode,
                        thinkingText = st.thinking.ifBlank { null }
                    ),
                    cid
                )
            )
        }
    }

    fun stop() {
        sendCancelled = true
        runJob?.cancel()
        runJob = null
        streamTickerJob?.cancel()
        streamTickerJob = null
        _state.update { it.copy(confirmRequest = null) }
        viewModelScope.launch {
            persistStreamingPartial(currentConversationId)
            streaming = null
            _state.update { it.copy(isRunning = false) }
            refresh()
        }
    }

    fun clearContext() {
        if (currentConversationId <= 0) return
        viewModelScope.launch {
            runJob?.cancel()
            runJob = null
            streamTickerJob?.cancel()
            streamTickerJob = null
            streaming = null
            pendingAssistantEntity = null
            _state.update { it.copy(confirmRequest = null) }
            repository.clearMessages(currentConversationId)
            _state.update { it.copy(isRunning = false, error = null) }
            refresh()
        }
    }

    fun deleteMessage(messageId: Long) {
        if (messageId <= 0) return
        viewModelScope.launch {
            if (pendingAssistantEntity?.id == messageId) {
                pendingAssistantEntity = null
                pendingRound = -1
            }
            val target = dbMessages.firstOrNull { it.id == messageId }
            if (target != null && target.role == ChatRole.ASSISTANT && target.toolCalls.isNotEmpty()) {
                repository.deleteToolMessages(target.toolCalls.map { it.id })
            }
            repository.deleteMessage(messageId)
            refresh()
        }
    }

    fun compressContext() {
        if (currentConversationId <= 0 || _state.value.isRunning) return
        viewModelScope.launch {
            val cid = currentConversationId
            val history = repository.getHistory(cid)
            if (history.size < 6) {
                _state.update { it.copy(error = "消息太少（少于 6 条），暂不需要压缩") }
                return@launch
            }
            _state.update { it.copy(processing = true) }
            try {
                val keepCount = 2
                val toSummarize = history.dropLast(keepCount)
                val keep = history.takeLast(keepCount)
                val config = settings.configFor(_state.value.provider)
                val summary = summarize(toSummarize, config)
                if (summary.isBlank()) {
                    _state.update { it.copy(processing = false, error = "压缩失败：未获得摘要") }
                    return@launch
                }
                repository.deleteMessagesRange(
                    cid, toSummarize.first().id, toSummarize.last().id
                )
                repository.insertMessage(
                    repository.domainToMessage(
                        ChatMessage(
                            role = ChatRole.USER,
                            content = "以下是此前对话的摘要，请以此为基础继续对话：\n\n$summary",
                            model = _state.value.model,
                            mode = _state.value.mode
                        ),
                        cid
                    )
                )
                _state.update { it.copy(processing = false, error = null) }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(processing = false, error = "压缩失败：${e.message}") }
            }
        }
    }

    private suspend fun summarize(
        history: List<MessageEntity>,
        config: com.betteraichat.core.model.ProviderConfig
    ): String {
        val provider = providerFactory(config.provider)
        val sys = ChatMessage(
            role = ChatRole.SYSTEM,
            content = "请用简洁的中文总结以下对话的关键内容（保留重要事实、用户需求、结论），200 字以内，不要寒暄。"
        )
        val sb = StringBuilder()
        val messages = listOf(sys) + history.map { repository.messageToDomain(it) }
        provider.chatStream(messages, config, emptyList()).collect { ev ->
            when (ev) {
                is StreamEvent.Delta -> sb.append(ev.text)
                is StreamEvent.Error -> throw IllegalStateException(ev.message)
                else -> Unit
            }
        }
        return sb.toString().trim()
    }

    fun editAndResend(messageId: Long, newText: String) {
        val text = newText.trim()
        if (text.isEmpty() || currentConversationId <= 0 || _state.value.isRunning) return
        viewModelScope.launch {
            val cid = currentConversationId
            runJob?.cancel()
            runJob = null
            streamTickerJob?.cancel()
            streamTickerJob = null
            pendingAssistantEntity = null
            _state.update { it.copy(confirmRequest = null) }
            repository.deleteMessagesFrom(cid, messageId)
            repository.insertMessage(
                repository.domainToMessage(
                    ChatMessage(role = ChatRole.USER, content = text, model = _state.value.model, mode = _state.value.mode),
                    cid
                )
            )
            _state.update {
                it.copy(input = "", error = null, sendTick = it.sendTick + 1)
            }
            runGeneration(cid)
        }
    }

    fun buildExportText(): String {
        val sb = StringBuilder()
        sb.appendLine("# ${_state.value.title}")
        sb.appendLine()
        dbMessages.forEach { msg ->
            when (msg.role) {
                ChatRole.USER -> {
                    sb.appendLine("## 用户")
                    if (msg.attachments.isNotEmpty()) {
                        sb.appendLine("（附件：${msg.attachments.joinToString { a -> a.name }}）")
                    }
                    sb.appendLine(msg.content)
                    sb.appendLine()
                }
                ChatRole.ASSISTANT -> {
                    sb.appendLine("## AI")
                    sb.appendLine(msg.content)
                    msg.toolCalls.forEach { call ->
                        sb.appendLine("- 工具 [${call.name}]：${call.result ?: ""}")
                    }
                    sb.appendLine()
                }
                else -> Unit
            }
        }
        return sb.toString()
    }

    fun respondConfirm(allow: Boolean) {
        val req = _state.value.confirmRequest ?: return
        engine.respond(req.call.id, allow)
        _state.update { it.copy(confirmRequest = null) }
    }

    fun updateModel(model: String) {
        _state.update { it.copy(model = model) }
        persistMeta()
    }

    fun updateMode(mode: AppMode) {
        _state.update { it.copy(mode = mode) }
        persistMeta()
    }

    private fun persistMeta() {
        if (currentConversationId <= 0) return
        val s = _state.value
        viewModelScope.launch {
            repository.updateMeta(currentConversationId, s.model, s.mode)
        }
    }
}

class ChatViewModelFactory(
    private val conversationId: Long,
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(
            conversationId,
            container.repository,
            container.settings,
            container.engine,
            container.providerFactory,
            container.appContext
        ) as T
}
