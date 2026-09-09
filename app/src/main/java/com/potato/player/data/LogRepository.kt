package com.potato.player.data

import android.content.Context
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for logcat capture.
 *
 * Verbose ON  -> logcat -d -t 1000 *:V
 * Verbose OFF -> logcat -d -t 500  *:W
 *
 * Lines containing any of the SENSITIVE_PATTERNS keywords are stripped
 * before the file is written, so passwords, tokens, etc. never leave the
 * device inside a bug report.
 */
@Singleton
class LogRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {

    companion object {
        private val SENSITIVE_PATTERNS = listOf(
            "password", "token", "auth", "cookie", "key", "secret", "email", "phone"
        )
    }

    /**
     * Captures logcat output, filters sensitive lines, and writes the result
     * to context.cacheDir/potato_debug_logs.txt.
     *
     * @param verboseLogging when true, captures up to 1 000 lines at *:V;
     *                       when false, captures up to 500 lines at *:W.
     * @return the [File] on success.
     * @throws Exception if the logcat process or file write fails.
     */
    suspend fun dumpLogs(verboseLogging: Boolean): File {
        val (lineCount, level) = if (verboseLogging) "1000" to "*:V" else "500" to "*:W"

        val process = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-t", lineCount, level)
        )
        val output = process.inputStream.bufferedReader().readText()

        val filtered = output.lineSequence()
            // Keep only lines relevant to this app
            .filter { line ->
                line.contains("com.potato.player") || line.contains("potato")
            }
            // Strip any line that might contain sensitive data
            .filterNot { line ->
                val lower = line.lowercase()
                SENSITIVE_PATTERNS.any { lower.contains(it) }
            }
            .joinToString("\n")

        val file = File(context.cacheDir, "potato_debug_logs.txt")
        file.writeText(filtered)
        return file
    }
}
