package com.betteraichat.core.skills

import android.content.Context
import java.io.File
import java.util.Locale

data class Skill(
    val name: String,
    val description: String,
    val content: String,
    val allowedTools: List<String> = emptyList(),
    val fileName: String
)

class SkillRepository(private val context: Context) {

    private val dir = File(context.filesDir, "skills")

    fun loadAll(): List<Skill> {
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension.lowercase(Locale.US) == "md" }
            ?.mapNotNull { f -> parse(f)?.copy(fileName = f.name) }
            ?.sortedBy { it.name } ?: emptyList()
    }

    fun loadByName(name: String): Skill? = loadAll().firstOrNull { it.name == name }

    fun import(fileName: String, content: String): Result<Skill> {
        return runCatching {
            val skill = parse(fileName, content)
                ?: throw IllegalArgumentException("无法解析 Skill 文件：缺少 frontmatter（name / description）")
            if (skill.name.isBlank() || skill.name.contains(" ") || skill.name.any { !it.isLetterOrDigit() && it != '_' && it != '-' }) {
                throw IllegalArgumentException("Skill 名称只能包含字母、数字、下划线或连字符")
            }
            if (skill.description.isBlank()) {
                throw IllegalArgumentException("Skill 缺少 description")
            }
            if (loadAll().any { it.name == skill.name }) {
                throw IllegalArgumentException("已存在同名 Skill：${skill.name}")
            }
            if (!dir.exists()) dir.mkdirs()
            val safeName = skill.name + ".md"
            File(dir, safeName).writeText(content.trim() + "\n")
            skill.copy(fileName = safeName)
        }
    }

    fun delete(name: String): Boolean {
        val skill = loadByName(name) ?: return false
        return File(dir, skill.fileName).delete()
    }

    private fun parse(file: File): Skill? = parse(file.name, file.readText())

    private fun parse(fileName: String, content: String): Skill? {
        val trimmed = content.trim()
        if (!trimmed.startsWith("---")) return null
        val end = trimmed.indexOf("\n---", 3)
        if (end < 0) return null
        val frontmatter = trimmed.substring(3, end)
        val body = trimmed.substring(end + 4).trim()

        val fields = mutableMapOf<String, String>()
        var currentKey: String? = null
        val listValues = mutableMapOf<String, MutableList<String>>()
        frontmatter.lineSequence().forEach { line ->
            val listMatch = Regex("^\\s*-\\s+(.+)$").find(line)
            if (listMatch != null) {
                currentKey?.let { listValues.getOrPut(it) { mutableListOf() }.add(listMatch.groupValues[1].trim()) }
                return@forEach
            }
            val pair = line.split(":", limit = 2)
            if (pair.size == 2) {
                val key = pair[0].trim()
                currentKey = key
                fields[key] = pair[1].trim().trim('"', '\'')
            } else if (line.isNotBlank()) {
                currentKey = null
            }
        }

        val name = fields["name"]?.trim()
        if (name.isNullOrBlank()) return null
        val description = fields["description"]?.trim() ?: ""
        val allowedTools = listValues["allowed-tools"] ?: emptyList()
        return Skill(
            name = name,
            description = description,
            content = body,
            allowedTools = allowedTools,
            fileName = fileName
        )
    }
}
