package com.betteraichat.skills.tools

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SearchContactsTool : DeviceTool {

    override val name = "search_contacts"
    override val description = "按姓名搜索联系人，返回姓名与电话号码（需要通讯录权限）。用于「给张三打电话」「查一下李四的号码」等请求；找到号码后可配合 open_dialer 拨号。"
    override val readOnly = true
    override val parameters = schemaOf(
        "query" to stringProp("姓名关键词（支持部分匹配）"),
        "limit" to intProp("返回条数，默认 5，最多 10")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val appContext = context.appContext
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "ERROR:缺少通讯录权限，请到 设置 → 权限 → 通讯录 中授权后重试"
        }
        val query = arguments["query"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (query.isBlank()) return "query 不能为空：请提供要搜索的姓名关键词"
        val limit = (arguments["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5).coerceIn(1, 10)
        val results = mutableListOf<Pair<String, String>>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        runCatching {
            appContext.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                arrayOf("%$query%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(projection[0])
                val numberIdx = cursor.getColumnIndex(projection[1])
                while (cursor.moveToNext() && results.size < limit) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val number = cursor.getString(numberIdx) ?: continue
                    if (results.none { it.first == name && it.second == number }) {
                        results.add(name to number)
                    }
                }
            }
        }.onFailure { return "查询联系人失败：${it.message}" }
        if (results.isEmpty()) {
            return "没有找到姓名包含「$query」的联系人。可以换个关键词，或让用户直接提供号码。"
        }
        return buildString {
            appendLine("找到 ${results.size} 个匹配「$query」的联系人：")
            results.forEachIndexed { i, (name, number) ->
                appendLine("${i + 1}. $name：$number")
            }
            append("可调用 open_dialer 拨打其中某个号码（需用户确认）。")
        }
    }
}
