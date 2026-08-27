package com.betteraichat.skills.tools

import android.content.Intent
import android.content.pm.PackageManager
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
                return@withContext "ERROR:Shizuku 未授权。请先安装并启动 Shizuku 应用（https://github.com/RikkaApps/Shizuku），然后在应用设置页点击「授权 Shizuku」后重试。"
            }
            try {
                val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
                    ?: return@withContext "无法连接 Shizuku 服务，请重启 Shizuku 后重试"
                val proc = service.newProcess(arrayOf("sh", "-c", command), null, null)
                    ?: return@withContext "无法创建 shell 进程"
                val ioScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
                try {
                    val outFuture = ioScope.async {
                        boundedRead(android.os.ParcelFileDescriptor.AutoCloseInputStream(proc.inputStream), 1_000_000)
                    }
                    val errFuture = ioScope.async {
                        boundedRead(android.os.ParcelFileDescriptor.AutoCloseInputStream(proc.errorStream), 200_000)
                    }
                    val exited = proc.waitForTimeout(timeout.toLong(), "SECONDS")
                    if (!exited) {
                        runCatching { proc.destroy() }
                        runCatching { proc.inputStream?.close() }
                        runCatching { proc.errorStream?.close() }
                        return@withContext "ERROR:命令超时（${timeout}s），已终止：$command"
                    }
                    val stdout = withTimeoutOrNull(3_000) { outFuture.await() } ?: ""
                    val stderr = withTimeoutOrNull(3_000) { errFuture.await() } ?: ""
                    val code = proc.exitValue()
                    val out = (stdout + if (stderr.isNotBlank()) "\n[stderr]\n$stderr" else "").trim()
                    if (out.length > 8000) {
                        "OK:${out.take(8000)}\n…（输出已截断，共 ${out.length} 字符）"
                    } else if (code != 0) {
                        "ERROR:退出码 $code\n$out"
                    } else if (out.isBlank()) {
                        "OK:执行完成：$command"
                    } else {
                        "OK:$out"
                    }
                } finally {
                    ioScope.cancel()
                    proc.destroy()
                }
            } catch (e: SecurityException) {
                "ERROR:Shizuku 权限被拒绝，请到设置页重新授权"
            } catch (e: Exception) {
                "ERROR:执行失败：${e.message}"
            }
        }
}

private fun boundedRead(input: java.io.InputStream, cap: Int): String {
    return try {
        input.use { stream ->
            val buffer = ByteArray(16 * 1024)
            val out = java.io.ByteArrayOutputStream()
            var total = 0
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                val room = cap - total
                if (room <= 0) break
                val write = minOf(n, room)
                out.write(buffer, 0, write)
                total += write
            }
            out.toString("UTF-8")
        }
    } catch (e: Exception) {
        ""
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

    fun checkPermission(): Int = try {
        if (Shizuku.pingBinder()) Shizuku.checkSelfPermission()
        else android.content.pm.PackageManager.PERMISSION_DENIED
    } catch (e: Exception) {
        android.content.pm.PackageManager.PERMISSION_DENIED
    }

    fun canExecute(): Boolean = isBinderAlive() && checkPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED

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
