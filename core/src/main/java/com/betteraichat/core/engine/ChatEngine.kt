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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOn
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
        val provider = try {
            providerFactory(config.provider)
        } catch (e: Exception) {
            emit(EngineEvent.Failed("服务初始化失败：${e.message}"))
            return@flow
        }
        val effectiveConfig = config.copy(reasoning = config.reasoning && mode == AppMode.MAX)
        var history = messages.map { m ->
            if (m.role == ChatRole.TOOL) {
                m.copy(content = truncateToolResult(m.content))
            } else m
        }
        var toolRounds = 0
        val maxRounds = 12
        val toolFailures = java.util.concurrent.ConcurrentHashMap<String, Int>()
        while (true) {
            if (toolRounds >= maxRounds) {
                emit(EngineEvent.Failed("工具调用已达 $maxRounds 次上限。请基于目前已获取的信息直接回答用户，不要再调用工具"))
                return@flow
            }
            var text = StringBuilder()
            var thinking = StringBuilder()
            var thinkingSignature: String? = null
            var toolCalls = emptyList<ToolCall>()
            try {
                val tools = toolCatalog.specsFor(mode)
                val custom = config.systemPrompt.trim()
                val sysContent = if (custom.isBlank()) {
                    systemPromptFor(mode)
                } else {
                    "$custom\n\n${systemPromptFor(mode)}"
                }
                val sys = ChatMessage(role = ChatRole.SYSTEM, content = sysContent)
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
            if (mode == AppMode.MAX && toolCalls.size > 1) {
                val decided = toolCalls.map { call ->
                    val spec = toolCatalog.find(call.name)
                    Triple(call, spec, gate(mode, spec, call))
                }
                val executed = coroutineScope {
                    decided.map { (call, spec, decision) ->
                        async {
                            when (decision) {
                                is GateResult.Denied ->
                                    Triple(call, ToolCallStatus.DENIED, "工具被拒绝执行：${decision.reason}")
                                is GateResult.NeedsConfirm ->
                                    Triple(call, ToolCallStatus.REJECTED, "并行执行时无法逐项确认，已跳过（请重新单独发起）")
                                is GateResult.Allow -> {
                                    val (status, result) = runWithCircuitBreaker(call, spec, toolFailures) { }
                                    Triple(call, status, result)
                                }
                            }
                        }
                    }.map { it.await() }
                }
                executed.forEach { (call, status, resultText) ->
                    val finished = call.copy(result = resultText, status = status)
                    emit(EngineEvent.ToolCallFinished(finished))
                    history = history + ChatMessage(
                        role = ChatRole.TOOL,
                        content = truncateToolResult(resultText),
                        toolCallId = call.id,
                        toolName = call.name
                    )
                }
            } else {
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
                        var emitFailed = false
                        var timedOut = false
                        val allow = if (!confirmRequestsFlow.tryEmit(call)) {
                            emitFailed = true
                            pendingConfirms.remove(call.id)
                            false
                        } else {
                            try {
                                withTimeout(confirmTimeoutMs) { deferred.await() }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                timedOut = true
                                false
                            } finally {
                                pendingConfirms.remove(call.id)
                            }
                        }
                        if (!allow) {
                            status = ToolCallStatus.REJECTED
                            resultText = when {
                                emitFailed -> "确认请求未送达，已拒绝该工具调用"
                                timedOut -> "用户未在 5 分钟内确认，已自动跳过该工具调用"
                                else -> "用户拒绝了该工具调用"
                            }
                        } else {
                            val (s, r) = runWithCircuitBreaker(call, spec, toolFailures) { emit(it) }
                            status = s
                            resultText = r
                        }
                    }
                    is GateResult.Allow -> {
                        val (s, r) = runWithCircuitBreaker(call, spec, toolFailures) { emit(it) }
                        status = s
                        resultText = r
                    }
                }
                val finished = call.copy(result = resultText, status = status)
                finishedCalls.add(finished)
                emit(EngineEvent.ToolCallFinished(finished))
                history = history + ChatMessage(
                    role = ChatRole.TOOL,
                    content = truncateToolResult(resultText),
                    toolCallId = call.id,
                    toolName = call.name
                )
            }
            }
        }
        emit(EngineEvent.Completed)
    }.flowOn(Dispatchers.IO)

    private suspend fun runWithCircuitBreaker(
        call: ToolCall,
        spec: ToolSpec?,
        failures: MutableMap<String, Int>,
        emitEvent: suspend (EngineEvent) -> Unit
    ): Pair<ToolCallStatus, String> {
        if ((failures[call.name] ?: 0) >= 3) {
            return ToolCallStatus.DENIED to
                "${call.name} 已连续失败 3 次，请勿再重试：请重新阅读该工具的参数说明后最多修正重试一次；" +
                "若仍失败，请改用其他工具或直接基于已有信息回答用户。"
        }
        val (status, result) = executeTool(call, spec, emitEvent)
        val runnerReportedFailure = result.startsWith("ERROR") ||
            result.startsWith("工具参数") || result.startsWith("工具执行")
        when {
            status == ToolCallStatus.DONE && !runnerReportedFailure -> failures[call.name] = 0
            status == ToolCallStatus.FAILED || runnerReportedFailure ->
                failures[call.name] = (failures[call.name] ?: 0) + 1
            else -> Unit
        }
        return status to result
    }

    private suspend fun executeTool(
        call: ToolCall,
        spec: ToolSpec?,
        emitEvent: suspend (EngineEvent) -> Unit
    ): Pair<ToolCallStatus, String> {
        emitEvent(EngineEvent.ToolCallStarted(call))
        return try {
            val result = withTimeout(60_000) { toolRunner.run(call.name, call.arguments) }
            ToolCallStatus.DONE to result
        } catch (e: CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            ToolCallStatus.FAILED to "工具执行超时（60 秒）"
        } catch (e: Exception) {
            ToolCallStatus.FAILED to (e.message ?: "工具执行失败")
        }
    }

    private fun truncateToolResult(text: String): String {
        if (text.length <= 6000) return text
        return text.take(6000) + "\n…（工具结果过长已截断，共 ${text.length} 字符）"
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
        AppMode.BUILD -> "你现在处于 Build（构建）模式。你可以调用设备工具来帮助用户完成任务，但每个工具执行前系统会请求用户确认，因此请大胆、合理地提出工具调用，明确说明每一步的目的。" +
            " 搜索策略：query 要具体，多角度问题用 query 数组一次搜索；需要网页详情时 read_top≥1 直接拿正文，避免逐条 web_read 浪费轮次。"
        AppMode.MAX -> "你现在处于 Max（最大）模式。你可以自主、连续地调用设备工具来完成用户任务，无需每次询问用户确认。请提前规划好操作顺序，用最少的步骤完成任务，并在完成后总结结果。" +
            " 搜索策略：调用 web_search 时 query 要具体（含时间/地点限定）；需要网页详情时直接让 read_top≥1 一次拿正文，避免逐条 web_read 浪费轮次；结果不足时最多换 1-2 次说法，然后基于已获取信息回答。"
    }

    private sealed interface GateResult {
        data object Allow : GateResult
        data object NeedsConfirm : GateResult
        data class Denied(val reason: String) : GateResult
    }
}
