package com.betteraichat.core.provider

import com.betteraichat.core.model.ChatMessage
import com.betteraichat.core.model.ProviderConfig
import com.betteraichat.core.model.StreamEvent
import com.betteraichat.core.model.ToolSpec
import kotlinx.coroutines.flow.Flow

interface ChatProvider {
    fun chatStream(
        messages: List<ChatMessage>,
        config: ProviderConfig,
        tools: List<ToolSpec>
    ): Flow<StreamEvent>
}
