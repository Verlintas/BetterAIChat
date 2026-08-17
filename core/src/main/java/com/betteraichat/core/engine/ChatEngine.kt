package com.betteraichat.core.engine

import com.betteraichat.core.mode.AppMode
import com.betteraichat.core.model.ChatMessage
import com.betteraichat.core.model.ChatRole
import com.betteraichat.core.model.ProviderConfig
import com.betteraichat.core.model.ProviderId
import com.betteraichat.core.model.StreamEvent
import com.betteraichat.core.model.ToolCall
import com.betteraichat.core.model.ToolCallStatus
import com.betteraichat.core.model.ToolSpec
import com.betteraichat.core.provider.ChatProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

interface ToolCatalog {
    fun specsFor(mode: AppMode): List<ToolSpec>
    fun find(name: String): ToolSpec?
}

interface ToolRunner {
    suspend fun run(name: String, arguments: String): String
}

sealed interface EngineEvent {
    data class Delta(val text: String) : EngineEvent
    data class ThinkingDelta(val text: String) : EngineEvent
    data class ToolCallStarted(val call: ToolCall) : EngineEvent
    data class ToolCallFinished(val call: ToolCall) : EngineEvent
    data class AssistantFinished(val message: ChatMessage) : EngineEvent
    data class Usage(val promptTokens: Long, val completionTokens: Long) : EngineEvent
    data class ConfirmRequested(val call: ToolCall) : EngineEvent
    data object Completed : EngineEvent
    data class Failed(val message: String) : EngineEvent
}

class ChatEngine(
    private val providerFactory: (ProviderId) -> ChatProvider,
    private val toolCatalog: ToolCatalog,
    private val toolRunner: ToolRunner
) {

    private val pendingConfirms = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val confirmRequestsFlow = MutableSharedFlow<ToolCall>(extraBufferCapacity = 8)
    val confirmRequests: SharedFlow<ToolCall> = confirmRequestsFlow

    private val confirmTimeoutMs = 300_000L

    fun respond(callId: String, allow: Boolean) {
        pendingConfirms.remove(callId)?.complete(allow)
    }

    fun run(
        messages: List<ChatMessage>,
        config: ProviderConfig,
        mode: AppMode
    ): Flow<EngineEvent> = flow {
        val provider = providerFactory(config.provider)
        val effectiveConfig = config.copy(reasoning = config.reasoning && mode == AppMode.MAX)
        var history = messages
        var toolRounds = 0
        val maxRounds = 8
        while (true) {
            if (toolRounds >= maxRounds) {
                emit(EngineEvent.Failed("工具调用轮次超过 $maxRounds 次，已停止"))
                return@flow
            }
            val tools = toolCatalog.specsFor(mode)
            val sys = ChatMessage(role = ChatRole.SYSTEM, content = systemPromptFor(mode))
            var text = StringBuilder()
            var thinking = StringBuilder()
            var thinkingSignature: String? = null
            var toolCalls = emptyList<ToolCall>()
            try {
                provider.chatStream(listOf(sys) + history, effectiveConfig, tools).collect { ev ->
                    when (ev) {
                        is StreamEvent.Delta -> {
                            text.append(ev.text)
                            emit(EngineEvent.Delta(ev.text))
                        }
                        is StreamEvent.ThinkingDelta -> {
                            thinking.append(ev.text)
                            emit(EngineEvent.ThinkingDelta(ev.text))
                        }
                        is StreamEvent.ToolCallsDone -> toolCalls = ev.calls
                        is StreamEvent.ThinkingSignature -> thinkingSignature = ev.signature
                        is StreamEvent.Usage -> emit(EngineEvent.Usage(ev.promptTokens, ev.completionTokens))
                        is StreamEvent.Error -> throw IllegalStateException(ev.message)
                        StreamEvent.Done -> Unit
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emit(EngineEvent.Failed(e.message ?: e.javaClass.simpleName))
                return@flow
            }
            val assistantMsg = ChatMessage(
                role = ChatRole.ASSISTANT,
                content = text.toString(),
                toolCalls = toolCalls,
                model = config.model,
                mode = mode,
                thinkingText = thinking.toString().ifBlank { null },
                thinkingSignature = thinkingSignature
            )
            emit(EngineEvent.AssistantFinished(assistantMsg))
            if (toolCalls.isEmpty()) break
            toolRounds++
            val finishedCalls = mutableListOf<ToolCall>()
            history = history + assistantMsg.copy(
                toolCalls = toolCalls.map { it.copy(status = ToolCallStatus.PENDING) }
            )
            for (call in toolCalls) {
                val spec = toolCatalog.find(call.name)
                val decision = gate(mode, spec, call)
                val status: ToolCallStatus
                val resultText: String
                when (decision) {
                    is GateResult.Denied -> {
                        status = ToolCallStatus.DENIED
                        resultText = "工具被拒绝执行：${decision.reason}"
                    }
                    is GateResult.NeedsConfirm -> {
                        val deferred = CompletableDeferred<Boolean>()
                        pendingConfirms[call.id] = deferred
                        confirmRequestsFlow.tryEmit(call)
                        val allow = try {
                            withTimeout(confirmTimeoutMs) { deferred.await() }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            false
                        } finally {
                            pendingConfirms.remove(call.id)
                        }
                        if (!allow) {
                            status = ToolCallStatus.REJECTED
                            resultText = "用户拒绝了该工具调用"
                        } else {
                            val (s, r) = executeTool(call, spec) { emit(it) }
                            status = s
                            resultText = r
                        }
                    }
                    is GateResult.Allow -> {
                        val (s, r) = executeTool(call, spec) { emit(it) }
                        status = s
                        resultText = r
                    }
                }
                val finished = call.copy(result = resultText, status = status)
                finishedCalls.add(finished)
                emit(EngineEvent.ToolCallFinished(finished))
                history = history + ChatMessage(
                    role = ChatRole.TOOL,
                    content = resultText,
                    toolCallId = call.id,
                    toolName = call.name
                )
            }
        }
        emit(EngineEvent.Completed)
    }

    private suspend fun executeTool(
        call: ToolCall,
        spec: ToolSpec?,
        emitEvent: suspend (EngineEvent) -> Unit
    ): Pair<ToolCallStatus, String> {
        emitEvent(EngineEvent.ToolCallStarted(call))
        return try {
            val result = toolRunner.run(call.name, call.arguments)
            ToolCallStatus.DONE to result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolCallStatus.FAILED to (e.message ?: "工具执行失败")
        }
    }

    private fun gate(mode: AppMode, spec: ToolSpec?, call: ToolCall): GateResult {
        if (mode == AppMode.CHAT) return GateResult.Denied("Chat 模式不执行设备工具")
        val s = spec ?: return GateResult.Denied("未知工具：${call.name}")
        if (mode == AppMode.PLAN && !s.readOnly) return GateResult.Denied("Plan 模式仅允许只读工具")
        if (mode == AppMode.BUILD) return GateResult.NeedsConfirm
        return GateResult.Allow
    }

    private fun systemPromptFor(mode: AppMode): String = when (mode) {
        AppMode.CHAT -> "你是一个友好的 AI 助手，请用简洁、准确的语言回答用户的问题。你不需要也不允许调用任何工具。"
        AppMode.PLAN -> "你现在处于 Plan（计划）模式。你只能进行分析、制定方案和查看只读信息，绝对禁止执行任何修改设备的操作。你拥有少量只读工具（如查看设备信息）。如果用户请求执行操作，请给出详细的执行计划，并提示需要切换到 Build 或 Max 模式。"
        AppMode.BUILD -> "你现在处于 Build（构建）模式。你可以调用设备工具来帮助用户完成任务，但每个工具执行前系统会请求用户确认，因此请大胆、合理地提出工具调用，明确说明每一步的目的。"
        AppMode.MAX -> "你现在处于 Max（最大）模式。你可以自主、连续地调用设备工具来完成用户任务，无需每次询问用户确认。请提前规划好操作顺序，用最少的步骤完成任务，并在完成后总结结果。"
    }

    private sealed interface GateResult {
        data object Allow : GateResult
        data object NeedsConfirm : GateResult
        data class Denied(val reason: String) : GateResult
    }
}
