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

    suspend fun run(command: String, timeoutSeconds: Int = 15, granted: () -> Boolean): String =
        withContext(Dispatchers.IO) {
            if (!granted()) {
                return@withContext "ERROR:Shizuku 未授权。请先安装并启动 Shizuku 应用，然后在应用设置页点击「授权 Shizuku」后重试。"
            }
            try {
                val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
                    ?: return@withContext "ERROR:无法连接 Shizuku 服务，请重启 Shizuku 后重试"
                val proc = service.newProcess(arrayOf("sh", "-c", command), null, null)
                    ?: return@withContext "ERROR:无法创建 shell 进程"
                val ioScope = CoroutineScope(Dispatchers.IO)
                try {
                    val outFuture = ioScope.async {
                        boundedRead(android.os.ParcelFileDescriptor.AutoCloseInputStream(proc.inputStream), 1_000_000)
                    }
                    val errFuture = ioScope.async {
                        boundedRead(android.os.ParcelFileDescriptor.AutoCloseInputStream(proc.errorStream), 200_000)
                    }
                    val exited = proc.waitForTimeout(timeoutSeconds.toLong(), "SECONDS")
                    if (!exited) {
                        runCatching { proc.destroy() }
                        runCatching { proc.inputStream?.close() }
                        runCatching { proc.errorStream?.close() }
                        return@withContext "ERROR:命令超时（${timeoutSeconds}s），已终止"
                    }
                    val stdout = withTimeoutOrNull(3_000) { outFuture.await() } ?: ""
                    val stderr = withTimeoutOrNull(3_000) { errFuture.await() } ?: ""
                    val code = proc.exitValue()
                    val out = (stdout + if (stderr.isNotBlank()) "\n[stderr]\n$stderr" else "").trim()
                    if (code != 0) {
                        "ERROR:退出码 $code\n${out.take(8000)}"
                    } else if (out.length > 8000) {
                        "OK:${out.take(8000)}\n…（输出已截断，共 ${out.length} 字符）"
                    } else if (out.isBlank()) {
                        "OK:执行成功"
                    } else {
                        "OK:$out"
                    }
                } finally {
                    ioScope.cancel()
                    runCatching { proc.destroy() }
                    runCatching { proc.inputStream?.close() }
                    runCatching { proc.errorStream?.close() }
                }
            } catch (e: SecurityException) {
                "ERROR:Shizuku 权限被拒绝，请到设置页重新授权"
            } catch (e: Exception) {
                "ERROR:执行失败：${e.message}"
            }
        }
}
