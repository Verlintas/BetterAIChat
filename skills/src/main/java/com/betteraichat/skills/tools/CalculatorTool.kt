package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class CalculatorTool : DeviceTool {

    override val name = "calculator"
    override val description = "计算数学表达式（安全求值，不执行任意代码）。支持 + - * / % ^ ( ) 和小数，以及函数 sin/cos/tan/asin/acos/atan（角度制）、sqrt/abs/log/ln/floor/ceil/round、常量 pi/e，和百分比（如 200*15% = 30）。示例：(15+7)*3.5/2、sqrt(2)、sin(30)。"
    override val readOnly = true
    override val parameters = schemaOf(
        "expression" to stringProp("数学表达式，如 (12 + 5) * 3、sqrt(2)、sin(30)、200*15%"),
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
                peek() == '^' -> {
                    pos++
                    val right = parseTerm()
                    value = Math.pow(value, right)
                }
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
        val base = when {
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
            peek().isLetter() -> parseIdentifier()
            else -> parseNumber()
        }
        skipSpaces()
        if (peek() == '%' && isPercentContext(pos + 1)) {
            pos++
            return base / 100.0
        }
        return base
    }

    private fun isPercentContext(afterPos: Int): Boolean {
        var i = afterPos
        while (i < input.length && input[i].isWhitespace()) i++
        if (i >= input.length) return true
        return input[i] in listOf(')', '+', '-', '*', '/', '^', '%', ',')
    }

    private fun parseIdentifier(): Double {
        val start = pos
        while (pos < input.length && (input[pos].isLetterOrDigit())) pos++
        val name = input.substring(start, pos).lowercase()
        skipSpaces()
        if (peek() == '(') {
            pos++
            val arg = parseExpression()
            skipSpaces()
            if (peek() != ')') throw IllegalArgumentException("函数 $name 缺少右括号")
            pos++
            return when (name) {
                "sin" -> kotlin.math.sin(Math.toRadians(arg))
                "cos" -> kotlin.math.cos(Math.toRadians(arg))
                "tan" -> kotlin.math.tan(Math.toRadians(arg))
                "asin" -> Math.toDegrees(kotlin.math.asin(arg))
                "acos" -> Math.toDegrees(kotlin.math.acos(arg))
                "atan" -> Math.toDegrees(kotlin.math.atan(arg))
                "sqrt" -> {
                    if (arg < 0) throw IllegalArgumentException("sqrt 参数不能为负数")
                    kotlin.math.sqrt(arg)
                }
                "abs" -> kotlin.math.abs(arg)
                "log" -> {
                    if (arg <= 0) throw IllegalArgumentException("log 参数必须为正数")
                    kotlin.math.log10(arg)
                }
                "ln" -> {
                    if (arg <= 0) throw IllegalArgumentException("ln 参数必须为正数")
                    kotlin.math.ln(arg)
                }
                "floor" -> kotlin.math.floor(arg)
                "ceil" -> kotlin.math.ceil(arg)
                "round" -> kotlin.math.round(arg).toDouble()
                else -> throw IllegalArgumentException("未知函数：$name（可用 sin/cos/tan/sqrt/abs/log/ln/floor/ceil/round）")
            }
        }
        return when (name) {
            "pi" -> Math.PI
            "e" -> Math.E
            else -> throw IllegalArgumentException("未知标识符：$name（常量可用 pi/e，函数需带括号）")
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
