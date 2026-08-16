package com.betteraichat.skills

import com.betteraichat.core.engine.ToolCatalog
import com.betteraichat.core.mode.AppMode
import com.betteraichat.core.model.ToolSpec

class ToolRegistry(private val tools: List<DeviceTool>) : ToolCatalog {

    override fun specsFor(mode: AppMode): List<ToolSpec> = when (mode) {
        AppMode.CHAT -> emptyList()
        AppMode.PLAN -> tools.filter { it.readOnly }.map { it.spec() }
        AppMode.BUILD, AppMode.MAX -> tools.map { it.spec() }
    }

    override fun find(name: String): ToolSpec? = tools.firstOrNull { it.name == name }?.spec()

    private fun DeviceTool.spec() = ToolSpec(
        name = name,
        description = description,
        parameters = parameters,
        readOnly = readOnly
    )
}
