package com.betteraichat.skills.tools

import android.content.Intent
import android.content.pm.PackageManager
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

class RunShellTool(private val isShizukuGranted: () -> Boolean) : DeviceTool {

    override val name = "run_shell"
    override val description = "以 shell 权限执行命令（需要 Shizuku 授权，设置页可授权）。可执行任何 shell 命令，如 pm、am、dumpsys、文件操作等。这是高权限操作，请谨慎使用。"
    override val readOnly = false
    override val parameters = schemaOf(
        "command" to stringProp("要执行的 shell 命令，如 ls /sdcard 或 dumpsys battery"),
        "timeout_seconds" to intProp("超时秒数，默认 15，最大 60"),
        required = listOf("command")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String =
        withContext(Dispatchers.IO) {
            val command = arguments["command"]?.jsonPrimitive?.content?.trim()
                ?: return@withContext "缺少 command 参数"
            if (command.isBlank()) return@withContext "command 不能为空"
            val timeout = (arguments["timeout_seconds"]?.jsonPrimitive?.content?.toIntOrNull() ?: 15)
                .coerceIn(1, 60)
            if (!isShizukuGranted()) {
                return@withContext "Shizuku 未授权。请先安装并启动 Shizuku 应用（https://github.com/RikkaApps/Shizuku），然后在应用设置页点击「授权 Shizuku」后重试。"
            }
            try {
                val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
                    ?: return@withContext "无法连接 Shizuku 服务，请重启 Shizuku 后重试"
                val proc = service.newProcess(arrayOf("sh", "-c", command), null, null)
                    ?: return@withContext "无法创建 shell 进程"
                val outFuture = kotlinx.coroutines.coroutineScope {
                    async(Dispatchers.IO) {
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(proc.inputStream)
                            .bufferedReader().readText()
                    }
                }
                val errFuture = kotlinx.coroutines.coroutineScope {
                    async(Dispatchers.IO) {
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(proc.errorStream)
                            .bufferedReader().readText()
                    }
                }
                val exited = proc.waitForTimeout(timeout.toLong(), "SECONDS")
                if (!exited) {
                    proc.destroy()
                    return@withContext "命令超时（${timeout}s），已终止：$command"
                }
                val code = proc.exitValue()
                val stdout = outFuture.await()
                val stderr = errFuture.await()
                val out = (stdout + if (stderr.isNotBlank()) "\n[stderr]\n$stderr" else "").trim()
                if (out.length > 8000) {
                    return@withContext buildString {
                        appendLine("退出码: $code")
                        append(out.take(8000))
                        append("\n…（输出已截断，共 ${out.length} 字符）")
                    }
                }
                if (out.isBlank()) {
                    "执行完成（退出码 $code）：$command"
                } else {
                    "退出码: $code\n$out"
                }
            } catch (e: SecurityException) {
                "Shizuku 权限被拒绝，请到设置页重新授权"
            } catch (e: Exception) {
                "执行失败：${e.message}"
            }
        }
}

object ShizukuSupport {

    fun isShizukuInstalled(context: android.content.Context): Boolean {
        val pm = context.packageManager
        return pm.getPackageInfoCompat("moe.shizuku.privileged.api") ||
            pm.getPackageInfoCompat("moe.shizuku.privileged.api.controller")
    }

    private fun PackageManager.getPackageInfoCompat(pkg: String): Boolean =
        try {
            getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    fun isBinderAlive(): Boolean = Shizuku.pingBinder()

    fun checkPermission(): Int = Shizuku.checkSelfPermission()

    fun canExecute(): Boolean = isBinderAlive() && checkPermission() == PackageManager.PERMISSION_GRANTED

    fun openShizukuApp(context: android.content.Context) {
        val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            ?: context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api.controller")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun openShizukuDownload(context: android.content.Context) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("https://github.com/RikkaApps/Shizuku/releases")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
