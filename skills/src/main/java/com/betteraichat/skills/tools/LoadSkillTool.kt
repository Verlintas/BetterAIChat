package com.betteraichat.skills.tools

import com.betteraichat.core.skills.Skill
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.SkillActionExecutor
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.ToolRegistry
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class LoadSkillTool(
    private val skillProvider: () -> List<Skill>,
    private val registryProvider: () -> ToolRegistry,
    private val actionExecutor: SkillActionExecutor
) : DeviceTool {

    override val name = "load_skill"

    override val description: String
        get() {
            val available = skillProvider()
            if (available.isEmpty()) {
                return "加载一个技能（skill）以执行特定任务。当前没有可用的技能，请告诉用户去设置页导入。"
            }
            val list = available.joinToString("；") { it.name }
            return "加载一个技能（skill）并严格按其指令执行任务。可用技能：$list。技能自带工具会在加载后自动可用。"
        }

    override val readOnly = false

    override val parameters = schemaOf(
        "name" to stringProp("要加载的技能名称，必须是上面列出的可用技能之一"),
        required = listOf("name")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val requested = arguments["name"]?.jsonPrimitive?.content?.trim()
            ?: return "缺少 name 参数"
        val skill = skillProvider().firstOrNull { it.name == requested }
            ?: return "未找到技能「$requested」。可用技能：${
                skillProvider().joinToString("、") { it.name }
            }。如无可用技能，请用户到设置页导入。"
        val allowed = if (skill.allowedTools.isEmpty()) {
            "无限制（可使用全部内置工具）"
        } else {
            skill.allowedTools.joinToString("、")
        }
        if (skill.tools.isNotEmpty()) {
            registryProvider().registerSkillTools(skill.name, skill.tools, actionExecutor)
        }
        return buildString {
            appendLine("技能「${skill.name}」已加载，请严格遵循以下指令执行：")
            appendLine()
            appendLine("【技能指令】")
            appendLine(skill.content)
            appendLine()
            if (skill.tools.isNotEmpty()) {
                appendLine("【本技能自带工具】")
                skill.tools.forEach { t ->
                    appendLine("- ${t.name}：${t.description}")
                }
                appendLine()
            }
            append("【本技能允许使用的内置工具】$allowed")
        }
    }
}
