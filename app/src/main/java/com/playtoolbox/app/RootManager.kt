package com.playtoolbox.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootManager {
    @Volatile private var granted: Boolean? = null
    fun isCached() = granted
    suspend fun request(): Boolean = withContext(Dispatchers.IO) {
        granted?.let { return@withContext it }
        val ok = try {
            val pb = ProcessBuilder("su", "-c", "id")
            pb.redirectErrorStream(true)
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText()
            val code = p.waitFor()
            code == 0 && out.contains("uid=0")
        } catch (e: Exception) { false }
        granted = ok
        ok
    }
    fun suExists(): Boolean = File("/system/bin/su").exists() || File("/system/xbin/su").exists()
}
