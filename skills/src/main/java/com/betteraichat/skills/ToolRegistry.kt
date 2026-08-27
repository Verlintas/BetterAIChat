package com.betteraichat.skills

import com.betteraichat.core.engine.ToolCatalog
import com.betteraichat.core.mode.AppMode
import com.betteraichat.core.model.ToolSpec
import com.betteraichat.core.skills.SkillToolDef

class ToolRegistry(private val builtinTools: List<DeviceTool>) : ToolCatalog {

    private val dynamicTools = mutableMapOf<String, SkillDefinedTool>()
    private val skillOwners = mutableMapOf<String, String>()

    fun registerSkillTools(skillName: String, defs: List<SkillToolDef>, executor: SkillActionExecutor) {
        defs.forEach { def ->
            if (builtinTools.any { it.name == def.name }) return@forEach
            dynamicTools[def.name] = SkillDefinedTool(def, skillName, executor)
            skillOwners[def.name] = skillName
        }
    }

    fun unregisterSkillTools(skillName: String) {
        dynamicTools.entries.removeAll { it.value.skillName == skillName }
        skillOwners.entries.removeAll { it.value == skillName }
    }

    fun findTool(name: String): DeviceTool? =
        builtinTools.firstOrNull { it.name == name } ?: dynamicTools[name]

    override fun specsFor(mode: AppMode): List<ToolSpec> = when (mode) {
        AppMode.CHAT -> emptyList()
        AppMode.PLAN -> builtinTools.filter { it.readOnly }.map { it.spec() }
        AppMode.BUILD, AppMode.MAX ->
            builtinTools.map { it.spec() } + dynamicTools.values.map { it.spec() }
    }

    override fun find(name: String): ToolSpec? = findTool(name)?.spec()

    private fun DeviceTool.spec() = ToolSpec(
        name = name,
        description = description,
        parameters = parameters,
        readOnly = readOnly
    )
}
