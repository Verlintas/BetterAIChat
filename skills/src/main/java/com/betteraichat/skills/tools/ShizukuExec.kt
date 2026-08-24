package com.betteraichat.skills.tools

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

object ShizukuExec {

    suspend fun run(command: String, timeoutSeconds: Int = 15, granted: () -> Boolean): String =
        withContext(Dispatchers.IO) {
            if (!granted()) {
                return@withContext "Shizuku 未授权。请先安装并启动 Shizuku 应用，然后在应用设置页点击「授权 Shizuku」后重试。"
            }
            try {
                val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
                    ?: return@withContext "无法连接 Shizuku 服务，请重启 Shizuku 后重试"
                val proc = service.newProcess(arrayOf("sh", "-c", command), null, null)
                    ?: return@withContext "无法创建 shell 进程"
                val ioScope = CoroutineScope(Dispatchers.IO)
                try {
                    val outFuture = ioScope.async {
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(proc.inputStream)
                            .bufferedReader().readText()
                    }
                    val errFuture = ioScope.async {
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(proc.errorStream)
                            .bufferedReader().readText()
                    }
                    val exited = proc.waitForTimeout(timeoutSeconds.toLong(), "SECONDS")
                    if (!exited) {
                        runCatching { proc.destroy() }
                        runCatching { proc.inputStream?.close() }
                        runCatching { proc.errorStream?.close() }
                        return@withContext "命令超时（${timeoutSeconds}s），已终止"
                    }
                    val stdout = withTimeoutOrNull(3_000) { outFuture.await() } ?: ""
                    val stderr = withTimeoutOrNull(3_000) { errFuture.await() } ?: ""
                    val code = proc.exitValue()
                    val out = (stdout + if (stderr.isNotBlank()) "\n[stderr]\n$stderr" else "").trim()
                    if (out.length > 8000) {
                        "退出码: $code\n${out.take(8000)}\n…（输出已截断，共 ${out.length} 字符）"
                    } else if (out.isBlank()) {
                        "执行完成（退出码 $code）"
                    } else {
                        "退出码: $code\n$out"
                    }
                } finally {
                    ioScope.cancel()
                    runCatching { proc.destroy() }
                    runCatching { proc.inputStream?.close() }
                    runCatching { proc.errorStream?.close() }
                }
            } catch (e: SecurityException) {
                "Shizuku 权限被拒绝，请到设置页重新授权"
            } catch (e: Exception) {
                "执行失败：${e.message}"
            }
        }
}
