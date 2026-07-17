package com.rk.shellix.ui.screens.downloader

import android.os.Build
import androidx.compose.foundation.layout.*
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
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.Okio
import okio.Sink
import java.io.File
import java.io.FileOutputStream
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
                    Okio.buffer(sink).use { out ->
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
                val extractOk = runCatching {
                    extractTar(outputFile, ubuntuDir, hardDeref = false)
                }.getOrElse { firstErr ->
                    Log.w("RootfsSource", "tar extract failed, retrying with --hard-dereference: ${firstErr.message}")
                    extractTar(outputFile, ubuntuDir, hardDeref = true)
                }

                if (!extractOk) {
                    throw java.io.IOException("Failed to extract ${outputFile.name}")
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
                        Text("Setup Failed: $error", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { retryKey++ }) {
                            Text("Retry")
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

/** Extracts a .tar.gz into [dest]. Returns true on success. */
private fun extractTar(file: File, dest: File, hardDeref: Boolean): Boolean {
    val cmd = mutableListOf("tar", "-xzf", file.absolutePath, "-C", dest.absolutePath)
    if (hardDeref) cmd.add("--hard-dereference")
    return runCatching {
        val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() == 0
    }.getOrDefault(false)
}

/** A sink that reports bytes written. */
private class ProgressSink(
    private val delegate: java.io.OutputStream,
    private val onProgress: (Long) -> Unit
) : Sink {
    private var written = 0L
    private val buffered = Okio.buffer(Okio.sink(delegate))

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
