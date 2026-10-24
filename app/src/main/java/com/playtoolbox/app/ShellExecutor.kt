package com.playtoolbox.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExecResult(val ok: Boolean, val output: String, val error: String = "")

object ShellExecutor {
    suspend fun run(cmd: String, needRoot: Boolean): ExecResult = withContext(Dispatchers.IO) {
        try {
            if (needRoot && !RootManager.request()) {
                return@withContext ExecResult(false, "", "拒绝执行：此功能需要 Root 权限。请在 Magisk/KernelSU 中授权本应用。")
            }
            val pb = if (needRoot) ProcessBuilder("su", "-c", cmd) else ProcessBuilder("sh", "-c", cmd)
            pb.redirectErrorStream(true)
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText().trim()
            val code = p.waitFor()
            
            val hasError = out.contains("Exception") || out.contains("Error:") || out.contains("Permission denied") || out.contains("not found") || out.contains("access")
            if (code != 0 || hasError) {
                ExecResult(false, "", out.ifBlank { "执行失败，未知错误" })
            } else {
                ExecResult(true, out.ifBlank { "(执行成功，无输出内容)" }, "")
            }
        } catch (e: Exception) {
            ExecResult(false, "", e.message ?: "执行崩溃")
        }
    }
}
