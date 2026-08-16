package com.betteraichat.core.mode

enum class AppMode(val displayName: String, val description: String) {
    CHAT("Chat", "纯对话，不调用任何设备工具"),
    PLAN("Plan", "只读分析，禁止执行类工具"),
    BUILD("Build", "默认模式，每个工具执行前需确认"),
    MAX("Max", "自主执行多步工具调用，深度推理")
}
