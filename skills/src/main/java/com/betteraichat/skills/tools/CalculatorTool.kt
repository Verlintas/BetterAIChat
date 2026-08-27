package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class CalculatorTool : DeviceTool {

    override val name = "calculator"
    override val description = "计算数学表达式（安全求值，不执行任意代码）。支持 + - * / % ^ ( ) 和小数，如 (15 + 7) * 3.5 / 2。用于精确计算或单位换算。"
    override val readOnly = true
    override val parameters = schemaOf(
        "expression" to stringProp("数学表达式，如 (12 + 5) * 3"),
        required = listOf("expression")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val expr = arguments["expression"]?.jsonPrimitive?.content?.trim()
            ?: return "expression 参数无效"
        if (expr.isEmpty()) return "expression 不能为空"
        if (expr.length > 500) return "ERROR:表达式过长（最多 500 字符）"
        return try {
            Parser(expr).parse()
        } catch (e: Throwable) {
            "ERROR:表达式无效：${e.message}"
        }
    }
}

private class Parser(private val input: String) {

    private var pos = 0

    fun parse(): String {
        skipSpaces()
        val value = parseExpression()
        skipSpaces()
        if (pos < input.length) throw IllegalArgumentException("多余字符：${input.substring(pos)}")
        val text = format(value)
        return if (text.endsWith(".0")) text.dropLast(2) else text
    }

    private fun format(v: Double): String = if (v == v.toLong().toDouble() && kotlin.math.abs(v) < 1e15) {
        v.toLong().toString()
    } else {
        "%.10f".format(v).trimEnd('0').trimEnd('.')
    }

    private fun parseExpression(): Double {
        var value = parseTerm()
        while (true) {
            skipSpaces()
            when {
                peek() == '+' -> { pos++; value += parseTerm() }
                peek() == '-' -> { pos++; value -= parseTerm() }
                else -> return value
            }
        }
    }

    private fun parseTerm(): Double {
        var value = parseFactor()
        while (true) {
            skipSpaces()
            when {
                peek() == '*' -> { pos++; value *= parseFactor() }
                peek() == '/' -> {
                    pos++
                    val d = parseFactor()
                    if (d == 0.0) throw IllegalArgumentException("除以零")
                    value /= d
                }
                peek() == '%' -> {
                    pos++
                    value %= parseFactor()
                }
                else -> return value
            }
        }
    }

    private fun parseFactor(): Double {
        skipSpaces()
        return when {
            peek() == '-' -> { pos++; -parseFactor() }
            peek() == '+' -> { pos++; parseFactor() }
            peek() == '(' -> {
                pos++
                val v = parseExpression()
                skipSpaces()
                if (peek() != ')') throw IllegalArgumentException("缺少右括号")
                pos++
                v
            }
            peek() == '^' -> {
                pos++
                val base = parseFactor()
                val exp = parseFactor()
                Math.pow(base, exp)
            }
            else -> parseNumber()
        }
    }

    private fun parseNumber(): Double {
        val start = pos
        var dot = false
        while (pos < input.length) {
            val c = input[pos]
            if (c.isDigit()) {
                pos++
            } else if (c == '.' && !dot) {
                dot = true
                pos++
            } else {
                break
            }
        }
        if (start == pos) throw IllegalArgumentException("位置 ${pos} 处的字符无效")
        return input.substring(start, pos).toDouble()
    }

    private fun peek(): Char = if (pos < input.length) input[pos] else '\u0000'

    private fun skipSpaces() {
        while (pos < input.length && input[pos].isWhitespace()) pos++
    }
}
