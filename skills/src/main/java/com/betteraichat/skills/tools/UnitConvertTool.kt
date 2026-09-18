package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.numberProp
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class UnitConvertTool : DeviceTool {

    override val name = "unit_convert"
    override val description = "单位换算：长度(m/km/cm/mm/mile/ft/inch)、重量(kg/g/lb/oz)、温度(c/f/k)、面积(m2/km2/ha/亩)、速度(m/s/km/h/mph)、数据(b/kb/mb/gb/tb)、时间(s/min/h/day)。例如 100 km/h 转 m/s。"
    override val readOnly = true
    override val parameters = schemaOf(
        "value" to numberProp("要换算的数值"),
        "from" to stringProp("源单位，如 km/h、c、kg、mile"),
        "to" to stringProp("目标单位，如 m/s、f、lb、km")
    )

    private val groups: Map<String, Map<String, Double>> = mapOf(
        "length" to mapOf(
            "mm" to 0.001, "cm" to 0.01, "m" to 1.0, "km" to 1000.0,
            "inch" to 0.0254, "ft" to 0.3048, "mile" to 1609.344, "nmi" to 1852.0
        ),
        "weight" to mapOf(
            "mg" to 0.000001, "g" to 0.001, "kg" to 1.0, "t" to 1000.0,
            "oz" to 0.028349523, "lb" to 0.45359237, "斤" to 0.5
        ),
        "area" to mapOf(
            "m2" to 1.0, "km2" to 1_000_000.0, "ha" to 10_000.0,
            "亩" to 666.6667, "ft2" to 0.092903, "acre" to 4046.86
        ),
        "speed" to mapOf(
            "m/s" to 1.0, "km/h" to 1000.0 / 3600, "mph" to 0.44704,
            "knot" to 1852.0 / 3600, "ft/s" to 0.3048
        ),
        "data" to mapOf(
            "b" to 1.0, "kb" to 1024.0, "mb" to 1024.0 * 1024, "gb" to 1024.0 * 1024 * 1024,
            "tb" to 1024.0 * 1024 * 1024 * 1024
        ),
        "time" to mapOf(
            "ms" to 0.001, "s" to 1.0, "min" to 60.0, "h" to 3600.0, "day" to 86400.0
        ),
        "temperature" to mapOf("c" to 1.0, "f" to 1.0, "k" to 1.0)
    )

    private val aliases = mapOf(
        "米" to "m", "千米" to "km", "公里" to "km", "厘米" to "cm", "毫米" to "mm",
        "英里" to "mile", "英尺" to "ft", "英寸" to "inch",
        "克" to "g", "千克" to "kg", "公斤" to "kg", "吨" to "t", "磅" to "lb", "盎司" to "oz",
        "平方米" to "m2", "平方公里" to "km2", "公顷" to "ha",
        "公里/小时" to "km/h", "米/秒" to "m/s",
        "摄氏度" to "c", "华氏度" to "f", "开尔文" to "k",
        "秒" to "s", "分钟" to "min", "小时" to "h", "天" to "day",
        "字节" to "b", "千字节" to "kb", "兆字节" to "mb", "吉字节" to "gb"
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val value = arguments["value"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: return "value 参数无效：请提供数值"
        val fromRaw = arguments["from"]?.jsonPrimitive?.content?.trim().orEmpty()
        val toRaw = arguments["to"]?.jsonPrimitive?.content?.trim().orEmpty()
        val from = normalize(fromRaw)
        val to = normalize(toRaw)
        if (from.isBlank() || to.isBlank()) return "from/to 参数不能为空"
        val group = groups.entries.firstOrNull { from in it.value.keys && to in it.value.keys }
            ?: return "不支持的单位组合：$from → $to（请使用说明中的单位，注意单位大小写，如 km/h、m/s、c、f、kg、mile）"
        val result = when (group.key) {
            "temperature" -> convertTemperature(value, from, to)
            else -> value * (group.value[from]!! / group.value[to]!!)
        }
        val formatted = if (result == result.toLong().toDouble() && kotlin.math.abs(result) < 1e15) {
            result.toLong().toString()
        } else {
            "%.6f".format(result).trimEnd('0').trimEnd('.')
        }
        val valueText = if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }
        return "$valueText $fromRaw = $formatted $toRaw"
    }

    private fun normalize(unit: String): String = aliases[unit] ?: unit.lowercase()

    private fun convertTemperature(value: Double, from: String, to: String): Double {
        val celsius = when (from) {
            "c" -> value
            "f" -> (value - 32) * 5 / 9
            "k" -> value - 273.15
            else -> value
        }
        return when (to) {
            "c" -> celsius
            "f" -> celsius * 9 / 5 + 32
            "k" -> celsius + 273.15
            else -> celsius
        }
    }
}
