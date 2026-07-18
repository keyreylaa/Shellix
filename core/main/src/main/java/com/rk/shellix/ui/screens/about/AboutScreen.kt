package com.rk.shellix.ui.screens.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val REPO_URL = "https://github.com/keyreylaa/Shellix"
private const val ISSUES_URL = "https://github.com/keyreylaa/Shellix/issues/new"
private const val WIKI_URL = "https://github.com/keyreylaa/Shellix/wiki"
private const val WIKI_HOME_RAW = "https://raw.githubusercontent.com/wiki/keyreylaa/Shellix/Home.md"

private fun appVersion(context: android.content.Context): String = runCatching {
    val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (android.os.Build.VERSION.SDK_INT >= 28) pkg.longVersionCode
               else @Suppress("DEPRECATION") pkg.versionCode.toLong()
    "${pkg.versionName} (build $code)"
}.getOrDefault("unknown")

@Composable
fun AboutScreen(navController: NavController) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember { appVersion(context) }

    var wikiState by remember { mutableStateOf<WikiState>(WikiState.Loading) }
    LaunchedEffect(Unit) { wikiState = fetchWiki() }

    PreferenceLayout(label = "About", onBack = { navController.popBackStack() }) {
        PreferenceGroup {
            PreferenceTemplate(
                title = { Text("Shellix") },
                description = { Text("A terminal emulator running Ubuntu via PRoot.") }
            )
            PreferenceTemplate(title = { Text("Version") }, description = { Text(version) })
            PreferenceTemplate(title = { Text("Author") }, description = { Text("keyreylaa") })
            PreferenceTemplate(
                title = { Text("License") },
                description = { Text("MIT (bundled Sora Editor is LGPL-2.1)") }
            )
        }

        PreferenceGroup(heading = "Built on") {
            PreferenceTemplate(
                title = { Text("Lineage") },
                description = { Text("Fork of ReTerminal, itself a fork of Termux. Terminal engine: Termux terminal-view / terminal-emulator. PRoot sandboxing.") }
            )
        }

        PreferenceGroup(heading = "Links") {
            PreferenceTemplate(
                modifier = Modifier.clickable { uriHandler.openUri(REPO_URL) },
                title = { Text("GitHub repository") },
                description = { Text(REPO_URL) }
            )
            PreferenceTemplate(
                modifier = Modifier.clickable { uriHandler.openUri(ISSUES_URL) },
                title = { Text("Open an issue") },
                description = { Text("Report a bug or request a feature") }
            )
            PreferenceTemplate(
                modifier = Modifier.clickable { uriHandler.openUri(WIKI_URL) },
                title = { Text("Wiki") },
                description = { Text(WIKI_URL) }
            )
        }

        PreferenceGroup(heading = "From the Wiki") {
            when (val s = wikiState) {
                is WikiState.Loading -> Box(
                    Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                is WikiState.Error -> PreferenceTemplate(
                    title = { Text("Couldn't load Wiki") },
                    description = { Text("Tap 'Wiki' above to open it in your browser.") }
                )
                is WikiState.Loaded -> MarkdownText(
                    markdown = s.markdown,
                    onLinkClick = { uriHandler.openUri(it) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private sealed interface WikiState {
    data object Loading : WikiState
    data object Error : WikiState
    data class Loaded(val markdown: String) : WikiState
}

private suspend fun fetchWiki(): WikiState = withContext(Dispatchers.IO) {
    runCatching {
        val client = OkHttpClient.Builder().build()
        val req = Request.Builder().url(WIKI_HOME_RAW).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext WikiState.Error
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) WikiState.Error else WikiState.Loaded(body)
        }
    }.getOrDefault(WikiState.Error)
}
