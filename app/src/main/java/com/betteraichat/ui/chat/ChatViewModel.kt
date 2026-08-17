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
import com.betteraichat.core.model.ToolCall
import com.betteraichat.core.model.ToolCallStatus
import com.betteraichat.core.storage.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

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
    val error: String? = null
)

class ChatViewModel(
    private val conversationId: Long,
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val engine: ChatEngine
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()

    private var dbMessages: List<UiMessage> = emptyList()
    private var streaming: UiMessage? = null
    private var currentConversationId: Long = conversationId
    private var runJob: Job? = null
    private var pendingAssistantEntity: MessageEntity? = null

    init {
        viewModelScope.launch {
            engine.confirmRequests.collect { call ->
                _state.update { it.copy(confirmRequest = ConfirmRequest(call, it.mode)) }
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
            val c = repository.getConversation(id) ?: return@launch
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

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.isRunning) return
        viewModelScope.launch {
            if (currentConversationId <= 0) {
                val s = _state.value
                val cid = repository.createConversation(s.provider, s.model, s.mode)
                currentConversationId = cid
                repository.updateTitle(cid, text.take(30))
                _state.update { it.copy(conversationId = cid, title = text.take(30)) }
                startObserving(cid)
            }
            val cid = currentConversationId
            val s = _state.value
            repository.insertMessage(
                repository.domainToMessage(
                    ChatMessage(role = ChatRole.USER, content = text, model = s.model, mode = s.mode),
                    cid
                )
            )
            _state.update { it.copy(input = "", isRunning = true, error = null) }
            runGeneration(cid)
        }
    }

    private fun runGeneration(cid: Long) {
        val s = _state.value
        val config = settings.configFor(s.provider)
        streaming = UiMessage(
            id = -1, role = ChatRole.ASSISTANT, content = "",
            model = config.model, mode = s.mode, streaming = true
        )
        refresh()
        runJob = viewModelScope.launch {
            val history = repository.getHistory(cid).map { repository.messageToDomain(it) }
            try {
                engine.run(history, config, s.mode).collect { ev ->
                    when (ev) {
                        is EngineEvent.Delta -> {
                            streaming = streaming?.copy(content = (streaming?.content ?: "") + ev.text)
                            refresh()
                        }
                        is EngineEvent.ToolCallStarted -> {
                            streaming = streaming?.copy(
                                toolCalls = upsertCall(
                                    streaming?.toolCalls ?: emptyList(),
                                    ev.call.copy(status = ToolCallStatus.RUNNING)
                                )
                            )
                            refresh()
                        }
                        is EngineEvent.ToolCallFinished -> {
                            streaming = streaming?.copy(
                                toolCalls = upsertCall(streaming?.toolCalls ?: emptyList(), ev.call)
                            )
                            persistToolResult(ev.call, cid)
                            refresh()
                        }
                        is EngineEvent.Usage -> {
                            streaming = streaming?.copy(usageInput = ev.promptTokens, usageOutput = ev.completionTokens)
                        }
                        is EngineEvent.AssistantFinished -> {
                            val content = ev.content
                            val entity = repository.domainToMessage(
                                ChatMessage(
                                    role = ChatRole.ASSISTANT,
                                    content = content,
                                    toolCalls = ev.toolCalls.map { it.copy(status = ToolCallStatus.PENDING) },
                                    model = config.model,
                                    mode = s.mode
                                ),
                                cid
                            ).copy(
                                usageInput = streaming?.usageInput ?: 0,
                                usageOutput = streaming?.usageOutput ?: 0
                            )
                            val id = repository.insertMessage(entity)
                            pendingAssistantEntity = entity.copy(id = id)
                            streaming = null
                            refresh()
                        }
                        is EngineEvent.Failed -> {
                            _state.update { it.copy(error = ev.message) }
                        }
                        is EngineEvent.ConfirmRequested -> Unit
                        EngineEvent.Completed -> Unit
                    }
                }
            } finally {
                _state.update { it.copy(isRunning = false) }
            }
        }
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

    fun stop() {
        runJob?.cancel()
        runJob = null
        viewModelScope.launch {
            val st = streaming
            if (st != null && st.content.isNotBlank() && currentConversationId > 0) {
                if (pendingAssistantEntity != null) {
                    repository.updateMessage(pendingAssistantEntity!!.copy(content = st.content))
                } else {
                    repository.insertMessage(
                        repository.domainToMessage(
                            ChatMessage(role = ChatRole.ASSISTANT, content = st.content, model = st.model, mode = st.mode),
                            currentConversationId
                        )
                    )
                }
            }
            streaming = null
            _state.update { it.copy(isRunning = false) }
            refresh()
        }
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
        ChatViewModel(conversationId, container.repository, container.settings, container.engine) as T
}
