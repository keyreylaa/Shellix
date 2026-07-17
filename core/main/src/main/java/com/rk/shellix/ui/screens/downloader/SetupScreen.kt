package com.rk.shellix.ui.screens.downloader

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rk.libcommons.*
import com.rk.resources.strings
import com.rk.shellix.ui.activities.terminal.MainActivity
import com.rk.shellix.ui.screens.terminal.Rootfs
import com.rk.shellix.ui.screens.terminal.TerminalScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.system.Os
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.*
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    mainActivity: MainActivity,
    navController: NavHostController
) {
    val context = LocalContext.current
    val installingStr = stringResource(strings.installing)
    val setupFailedStr = stringResource(strings.setup_failed)

    var isSetupComplete by remember { mutableStateOf(Rootfs.isRootfsInstalled(context)) }
    var isExtracted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("") }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryKey) {
        if (isSetupComplete) {
            Rootfs.isInstalled.value = true
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                error = null
                progress = 0f
                statusText = installingStr

                val abis = Build.SUPPORTED_ABIS
                val abi = abis.firstOrNull {
                    it in listOf("arm64-v8a", "armeabi-v7a", "x86_64")
                } ?: throw RuntimeException("Unsupported CPU architectures: ${abis.joinToString()}")

                val arch = when (abi) {
                    "arm64-v8a" -> "arm64"
                    "armeabi-v7a" -> "armhf"
                    "x86_64" -> "amd64"
                    else -> throw RuntimeException("Unsupported ABI: $abi")
                }

                val client = OkHttpClient.Builder().build()
                val outputFile = context.filesDir.child("ubuntu.tar.gz")

                // Copy to $PREFIX/files/ubuntu.tar.gz as well so init-host.sh can
                // re-extract at first boot if needed. filesDir IS $PREFIX/files.
                val tarballName = RootfsSource.tarballNameFor(arch)

                fun download(fromFallback: Boolean) {
                    val url = if (fromFallback) RootfsSource.fallbackUrlFor(arch) else RootfsSource.urlFor(arch)
                    statusText = "Downloading... 0%"
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        throw java.io.IOException("HTTP ${response.code} for $url")
                    }
                    val body = response.body ?: throw java.io.IOException("Empty body for $url")
                    val total = body.contentLength()
                    val sink = ProgressSink(FileOutputStream(outputFile)) { written ->
                        if (total > 0) {
                            val percent = (written * 100 / total).toInt()
                            progress = percent / 100f
                            statusText = "Downloading... $percent%"
                        } else {
                            statusText = "Downloading... ${written / 1024} KB"
                        }
                    }
                    sink.buffer().use { out ->
                        body.source().use { src -> out.writeAll(src) }
                    }
                }

                if (!outputFile.exists() || outputFile.length() == 0L) {
                    runCatching { download(false) }.onFailure { primaryErr ->
                        // Retry with fallback URL on failure.
                        statusText = "Primary download failed, trying fallback..."
                        runCatching { download(true) }.onFailure { fallbackErr ->
                            throw fallbackErr
                        }
                    }
                }

                // Optional SHA256 verification: do not block on failure.
                runCatching {
                    val shaReq = Request.Builder().url(RootfsSource.sha256UrlFor(arch)).build()
                    val shaResp = client.newCall(shaReq).execute()
                    if (shaResp.isSuccessful) {
                        val text = shaResp.body?.string().orEmpty()
                        val line = text.lineSequence()
                            .firstOrNull { it.contains(tarballName) }
                        if (line != null) {
                            val expected = line.trim().split("\\s+".toRegex()).first()
                            val actual = sha256Of(outputFile)
                            if (actual != expected) {
                                outputFile.delete()
                                throw java.io.IOException("SHA256 mismatch for $tarballName (expected $expected, got $actual)")
                            }
                        }
                    }
                }.onFailure { verifyErr ->
                    Log.w("RootfsSource", "SHA256 verify skipped: ${verifyErr.message}")
                }

                statusText = "Extracting..."
                val ubuntuDir = context.ubuntuDir()
                val extractResult = extractTar(outputFile, ubuntuDir, hardDeref = false)
                    .onFailure { firstErr ->
                        Log.w("RootfsSource", "tar extract (pass 1) failed: ${firstErr.message}")
                        extractTar(outputFile, ubuntuDir, hardDeref = true)
                    }

                if (extractResult.isFailure) {
                    val free = runCatching {
                        val stat = android.os.StatFs(ubuntuDir.absolutePath)
                        "${stat.availableBytes / (1024 * 1024)} MB free"
                    }.getOrDefault("unknown free space")
                    val cause = extractResult.exceptionOrNull()
                    throw java.io.IOException(
                        "Failed to extract ${outputFile.name} (${outputFile.length() / (1024 * 1024)} MB).\n" +
                        "Cause: ${cause?.message ?: "unknown"}\n" +
                        "Storage: $free\n" +
                        "Check: archive downloaded fully, device has free space, and file is not corrupted."
                    )
                }

                withContext(Dispatchers.Main) {
                    Rootfs.isInstalled.value = true
                    isExtracted = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e.javaClass.simpleName + ": " + e.message
                    toast(setupFailedStr.format(e.message))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            isExtracted -> {
                SetupWizard(mainActivity = mainActivity, navController = navController)
            }
            !isSetupComplete -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (error != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(0.92f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Setup Failed",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                SelectionContainer {
                                    Text(
                                        error!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(onClick = { retryKey++ }) {
                                        Text("Retry")
                                    }
                                    OutlinedButton(onClick = {
                                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Shellix setup error", error))
                                        toast("Error copied to clipboard")
                                    }) {
                                        Text("Copy error")
                                    }
                                }
                            }
                        }
                    } else {
                        Text(statusText.ifEmpty { installingStr }, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(progress = { progress })
                    }
                }
            }
            else -> {
                TerminalScreen(mainActivity = mainActivity, navController = navController)
            }
        }
    }
}

/** Extracts a .tar.gz into [dest] using a pure-Kotlin (commons-compress) reader.
 *  Returns true on success. The [hardDeref] parameter is kept for signature
 *  compatibility but is no longer needed since hardlinks are emulated by copy. */
@Suppress("UNUSED_PARAMETER")
private fun extractTar(file: File, dest: File, hardDeref: Boolean): Result<Unit> {
    dest.mkdirs()
    val destCanonical = dest.canonicalPath
    val pendingHardlinks = mutableListOf<Pair<TarArchiveEntry, File>>()

    return runCatching {
        GzipCompressorInputStream(FileInputStream(file)).use { gzipIn ->
            TarArchiveInputStream(gzipIn).use { tarIn ->
                var entry = tarIn.nextEntry
                while (entry != null) {
                    val name = entry.name.removePrefix("./")
                    if (name.isEmpty() || name == ".") {
                        entry = tarIn.nextEntry
                        continue
                    }

                    val candidate = File(dest, name)
                    val candidateCanonical = candidate.canonicalPath
                    val isInside = candidateCanonical == destCanonical ||
                            candidateCanonical.startsWith(destCanonical + File.separator)
                    if (!isInside) {
                        Log.w("RootfsSource", "Skipping path traversal entry: ${entry.name}")
                        entry = tarIn.nextEntry
                        continue
                    }

                    when {
                        entry.isDirectory -> {
                            candidate.mkdirs()
                        }
                        entry.isSymbolicLink -> {
                            createSymlink(entry.linkName, candidate.absolutePath)
                        }
                        entry.isLink -> {
                            // Hardlink: resolve the link target which should already be
                            // extracted; if not, defer to a second pass.
                            val target = File(dest, entry.linkName)
                            if (target.exists()) {
                                copyRegular(tarIn, candidate, entry)
                            } else {
                                pendingHardlinks.add(entry to candidate)
                            }
                        }
                        else -> {
                            copyRegular(tarIn, candidate, entry)
                        }
                    }
                    entry = tarIn.nextEntry
                }
            }
        }

        // Second pass for hardlinks whose target was extracted after them.
        for ((entry, candidate) in pendingHardlinks) {
            val target = File(dest, entry.linkName)
            if (target.exists()) {
                copyFileBytes(target, candidate)
            } else {
                Log.w("RootfsSource", "Hardlink target missing, creating empty file: ${entry.name} -> ${entry.linkName}")
                candidate.parentFile?.mkdirs()
                candidate.createNewFile()
            }
        }
    }.onFailure { e ->
        Log.e("RootfsSource", "extractTar failed for ${file.absolutePath} -> ${dest.absolutePath}", e)
    }
}

/** Copies the current tar entry's bytes into [candidate]. */
private fun copyRegular(tarIn: TarArchiveInputStream, candidate: File, entry: TarArchiveEntry) {
    candidate.parentFile?.mkdirs()
    FileOutputStream(candidate).use { out ->
        val buffer = ByteArray(8192)
        var read: Int
        while (tarIn.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
        }
    }
    try {
        val mode = entry.mode
        candidate.setExecutable((mode and 0b001) != 0 || (mode and 0b010) != 0)
    } catch (_: Exception) {
        // best-effort
    }
}

/** Copies an already-extracted file's bytes into [candidate] (hardlink emulation). */
private fun copyFileBytes(source: File, candidate: File) {
    candidate.parentFile?.mkdirs()
    source.inputStream().use { input ->
        FileOutputStream(candidate).use { out ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                out.write(buffer, 0, read)
            }
        }
    }
    try {
        candidate.setExecutable(source.canExecute())
    } catch (_: Exception) {
        // best-effort
    }
}

/** Creates a symlink, preferring android.system.Os, falling back to `ln -s`. */
private fun createSymlink(linkName: String?, targetPath: String) {
    if (linkName == null) return
    try {
        File(targetPath).parentFile?.mkdirs()
        Os.symlink(linkName, targetPath)
    } catch (_: Exception) {
        try {
            Runtime.getRuntime().exec(arrayOf("ln", "-s", linkName, targetPath))
        } catch (e: Exception) {
            Log.w("RootfsSource", "Failed to create symlink $targetPath -> $linkName: ${e.message}")
        }
    }
}

/** A sink that reports bytes written. */
private class ProgressSink(
    private val delegate: java.io.OutputStream,
    private val onProgress: (Long) -> Unit
) : Sink {
    private var written = 0L
    private val buffered = delegate.sink().buffer()

    override fun write(source: Buffer, byteCount: Long) {
        buffered.write(source, byteCount)
        written += byteCount
        onProgress(written)
    }

    override fun flush() = buffered.flush()
    override fun timeout() = buffered.timeout()
    override fun close() = buffered.close()
}

private fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(8192)
        var read: Int
        while (input.read(buf).also { read = it } != -1) {
            digest.update(buf, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
