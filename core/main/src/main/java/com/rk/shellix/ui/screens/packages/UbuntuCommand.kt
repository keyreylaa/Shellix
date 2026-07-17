package com.rk.shellix.ui.screens.packages

import com.rk.shellix.service.SessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random
import com.termux.terminal.TerminalSession

/**
 * Runs a command inside the active Ubuntu PRoot session and returns the
 * captured output. The command is wrapped with two unique marker echoes so
 * the emitted transcript between them can be extracted reliably, avoiding
 * races with manual typing or concurrent commands.
 */
object UbuntuCommand {
    private const val TIMEOUT_MS = 60_000L
    private const val POLL_MS = 150L

    /**
     * Executes [command] (without sudo) as root via the NOPASSWD sudo user.
     *
     * @param sessionBinder the active session binder, or null if no session.
     * @param command the shell command to run (sudo is added automatically).
     * @return success with transcript text between markers, or failure.
     */
    suspend fun run(sessionBinder: SessionService.SessionBinder?, command: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val binder = sessionBinder
                    ?: return@withContext Result.failure(IllegalStateException("No session"))
                val id = binder.getService().currentSession.value.first
                val session = binder.getSession(id)
                    ?: return@withContext Result.failure(IllegalStateException("No active session"))
                val emulator = session.emulator
                    ?: return@withContext Result.failure(IllegalStateException("No emulator"))

                val marker = "MARKER_${Random.nextInt(1_000_000, 9_999_999)}"
                val uidMarker = "UID_${Random.nextInt(1_000_000, 9_999_999)}"

                // Detect whether we are already root so we don't blindly prefix sudo
                // (sudo may be absent on a minimal rootfs). Runs as the session user.
                session.write("echo $uidMarker; id -u; echo $uidMarker\n")
                val uidStart = System.currentTimeMillis()
                var uid = "0"
                while (System.currentTimeMillis() - uidStart < TIMEOUT_MS) {
                    delay(POLL_MS)
                    val now = emulator.getScreen().getTranscriptText()
                    val i1 = now.indexOf(uidMarker)
                    val i2 = now.indexOf(uidMarker, i1 + uidMarker.length)
                    if (i1 >= 0 && i2 > i1) {
                        uid = now.substring(i1 + uidMarker.length, i2)
                            .removePrefix("\r\n").removePrefix("\n")
                            .trim().lineSequence().firstOrNull() ?: "0"
                        break
                    }
                }
                val prefix = if (uid == "0") "" else "sudo "

                val wrapped = "echo $marker; ${prefix}$command; echo $marker\n"

                session.write(wrapped)

                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < TIMEOUT_MS) {
                    delay(POLL_MS)
                    val now = emulator.getScreen().getTranscriptText()
                    val idx1 = now.indexOf(marker)
                    val idx2 = now.indexOf(marker, idx1 + marker.length)
                    if (idx1 >= 0 && idx2 > idx1) {
                        val between = now.substring(idx1 + marker.length, idx2)
                            .removePrefix("\r\n").removePrefix("\n")
                        return@withContext Result.success(between)
                    }
                }
                Result.failure(IllegalStateException("Command timed out: $command"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
