package com.rk.shellix.ui.screens.terminal

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.rk.shellix.ui.components.InputDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.libcommons.child
import com.rk.resources.strings
import com.rk.libcommons.toast
import com.rk.shellix.ui.activities.terminal.MainActivity
import com.rk.shellix.ui.activities.terminal.MainViewModel
import com.rk.shellix.ui.screens.terminal.VoiceInput
import com.termux.terminal.TerminalSession
import com.rk.shellix.ui.components.SetStatusBarTextColor
import com.rk.shellix.ui.screens.settings.SettingsCard
import com.rk.shellix.ui.screens.settings.WorkingMode
import com.rk.shellix.ui.screens.terminal.virtualkeys.VirtualKeysListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    mainActivity: MainActivity,
    navController: NavController,
    mainViewModel: MainViewModel = viewModel(mainActivity),
    terminalViewModel: TerminalViewModel = viewModel(mainActivity)
) {
    val context = LocalContext.current
    val isDarkMode = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val configuration = LocalConfiguration.current
    val drawerWidth = (configuration.screenWidthDp * 0.84).dp
    var showAddDialog by remember { mutableStateOf(false) }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    val view = LocalView.current

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val sessionBinder = mainViewModel.sessionBinder

    var showScreenshotChoice by remember { mutableStateOf(false) }
    var screenshotLoading by remember { mutableStateOf(false) }

    val onScreenshotClick: () -> Unit = clickHandler@ {
        val tv = terminalViewModel.terminalView
        if (tv == null) {
            toast("Terminal not ready")
            return@clickHandler
        }
        showScreenshotChoice = true
    }

    fun doScreenshot(mode: Mode) {
        val tv = terminalViewModel.terminalView ?: run {
            toast("Terminal not ready")
            return
        }
        screenshotLoading = true
        scope.launch(Dispatchers.IO) {
            val title = TerminalScreenshot.title(context)
            val stamp = System.currentTimeMillis()
            val tag = if (mode == Mode.DESKTOP) "-desktop" else ""
            val displayName = "Shellix-$stamp$tag"
            val bitmap = TerminalScreenshot.capture(tv, context, title, mode)
            if (bitmap == null) {
                withContext(Dispatchers.Main) {
                    screenshotLoading = false
                    toast("Nothing to capture")
                }
                return@launch
            }
            val saved = TerminalScreenshot.saveToGallery(context, bitmap, displayName)
            withContext(Dispatchers.Main) {
                screenshotLoading = false
                if (saved != null) {
                    toast("Saved to Pictures/Shellix")
                    TerminalScreenshot.shareIntent(context, saved)?.let { context.startActivity(it) }
                } else {
                    toast("Failed to save screenshot")
                }
            }
        }
    }

    if (showScreenshotChoice) {
        ScreenshotResolutionDialog(
            onDismiss = { showScreenshotChoice = false },
            onChoose = { mode ->
                showScreenshotChoice = false
                doScreenshot(mode)
            }
        )
    }

    LaunchedEffect(Unit) {
        val bgFile = context.filesDir.child("background")
        withContext(Dispatchers.Main) {
            if (bgFile.exists().not()) {
                TerminalUtils.darkText.value = !isDarkMode
                terminalViewModel.bitmapFile = null
            } else if (terminalViewModel.bitmapFile == null) {
                terminalViewModel.bitmapFile = bgFile
            }
        }
    }
    
    // Update virtual keys only when session reference or color actually changes
    var rememberedSession by remember { mutableStateOf<TerminalSession?>(null) }
    var rememberedColor by remember { mutableStateOf(0) }
    SideEffect {
        terminalViewModel.virtualKeysView?.let { vk ->
            val currentSession = terminalViewModel.terminalView?.mTermSession
            val currentColor = TerminalUtils.getViewColor()
            if (currentSession !== rememberedSession) {
                vk.virtualKeysViewClient = currentSession?.let { VirtualKeysListener(it, vk) }
                rememberedSession = currentSession
            }
            if (currentColor != rememberedColor) {
                vk.buttonTextColor = currentColor
                rememberedColor = currentColor
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val isClosed by remember { snapshotFlow { drawerState.isClosed }.distinctUntilChanged() }.collectAsState(initial = drawerState.isClosed)
    val isDarkIcons = if (isClosed) TerminalUtils.darkText.value else !isDarkMode
    SetStatusBarTextColor(isDarkIcons = isDarkIcons)

    if (showAddDialog && sessionBinder != null) {
        AddSessionDialog(
            onDismiss = { showAddDialog = false },
            onCreateSession = { mode ->
                val sessionId = generateUniqueSessionId(sessionBinder.getService().sessionList.keys.toList())
                val terminal = terminalViewModel.terminalView ?: return@AddSessionDialog
                val client = TerminalBackEnd(terminal, mainActivity)
                sessionBinder.createSession(sessionId, client, mode)
                terminalViewModel.changeSession(context, sessionBinder, sessionId)
                showAddDialog = false
            }
        )
    }

    renameTargetId?.let { id ->
        InputDialog(
            title = "Rename session",
            inputLabel = "Name",
            inputValue = renameText,
            onInputValueChange = { renameText = it },
            onConfirm = {
                sessionBinder?.renameSession(id, renameText)
                renameTargetId = null
            },
            onDismiss = { renameTargetId = null }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen || !terminalViewModel.showToolbar,
        drawerContent = {
            val onClearClick = {
                val binder = sessionBinder
                val id = binder?.getService()?.currentSession?.value?.first
                val session = id?.let { binder.getSession(it) }
                session?.emulator?.paste("\u000c")
                Unit
            }

            TerminalDrawer(
                drawerWidth = drawerWidth,
                sessionBinder = sessionBinder,
                navController = navController,
                onAddSession = { showAddDialog = true },
                onClearClick = onClearClick,
                onSessionSelected = { id ->
                    sessionBinder?.let { terminalViewModel.changeSession(context, it, id) }
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
            Box(modifier = Modifier.fillMaxSize()) {
            // Background drawn first => naturally behind the terminal Column (Compose
            // paints in declaration order). No zIndex(-1f): on API <31 a negative zIndex
            // can get clipped and the image disappears entirely.
            key(terminalViewModel.bitmapFile) { BackgroundImage(terminalViewModel) }

            if (screenshotLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Capturing screenshot...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Column {
                if (terminalViewModel.showToolbar) {
                    TerminalTopBar(
                        sessionBinder = sessionBinder,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onAddClick = { showAddDialog = true },
                        onScreenshotClick = onScreenshotClick,
                        onMicClick = {
                            VoiceInput.toggle(
                                activity = mainActivity,
                                onListening = { listening -> terminalViewModel.voiceListening = listening },
                                onResult = { text ->
                                    val binder = sessionBinder
                                    val id = binder?.getService()?.currentSession?.value?.first
                                    val session = id?.let { binder.getSession(it) }
                                    session?.emulator?.paste(text)
                                },
                                onError = { com.rk.libcommons.toast(it) }
                            )
                        },
                        micListening = terminalViewModel.voiceListening,
                        color = TerminalUtils.getComposeColor()
                    )
                }

                val density = LocalDensity.current
                val topPadding = if (terminalViewModel.showToolbar) 0.dp else {
                    with(density) { TopAppBarDefaults.windowInsets.getTop(this).toDp() }
                }

                if (sessionBinder != null) {
                    TerminalViewLayout(
                        viewModel = terminalViewModel,
                        mainActivity = mainActivity,
                        sessionBinder = sessionBinder,
                        modifier = Modifier
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(top = topPadding)
                            .fillMaxSize()
                    )

                    SessionTabBar(
                        sessionBinder = sessionBinder,
                        onSessionSelected = { id ->
                            terminalViewModel.changeSession(context, sessionBinder, id)
                            scope.launch { drawerState.close() }
                        },
                        onCreateSession = { showAddDialog = true },
                        onCloseSession = { id -> sessionBinder.terminateSession(id) },
                        onRenameSession = { id ->
                            renameText = sessionBinder.getService().sessionList[id]?.name ?: ""
                            renameTargetId = id
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundImage(viewModel: TerminalViewModel) {
    val context = LocalContext.current
    val file = viewModel.bitmapFile
    var resolved by remember(file) { mutableStateOf<ImageBitmap?>(null) }

    // For the API <31 blur fallback we need an actual Bitmap. Decode it here with
    // ImageDecoder (handles HEIC/WebP/AVIF on supported OS versions) so every
    // Android-native format works. The on-screen render below uses AsyncImage
    // (Coil) which decodes all formats independently.
    LaunchedEffect(file) {
        if (file == null) { resolved = null; return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            val bmp = try {
                TerminalThemes.decodeBitmap(context, file)
            } catch (e: Exception) { null }
            withContext(Dispatchers.Main) { resolved = bmp }
        }
    }

    file?.let { f ->
        // Modifier.blur() relies on RenderEffect, which only exists on API 31+.
        // On older Android it silently does nothing, so fall back to pre-blurring
        // the Bitmap itself with BlurMaskFilter (all API levels).
        val renderBitmap = if (viewModel.backgroundBlur > 0f && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            resolved?.let { bmp ->
                remember(bmp, viewModel.backgroundBlur) { blurBitmap(bmp, viewModel.backgroundBlur) }
            }
        } else {
            null
        }

        if (renderBitmap != null) {
            Image(
                bitmap = renderBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(viewModel.wallAlpha)
            )
        } else {
            AsyncImage(
                model = f,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(viewModel.wallAlpha)
                    .let {
                        if (viewModel.backgroundBlur > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            it.blur(viewModel.backgroundBlur.dp)
                        } else it
                    }
            )
        }
    }
}

private fun blurBitmap(bitmap: ImageBitmap, radiusDp: Float): ImageBitmap {
    val src = bitmap.asAndroidBitmap()
    val scale = 0.35f // downscale for a cheap, soft blur
    val w = maxOf(1, (src.width * scale).toInt())
    val h = maxOf(1, (src.height * scale).toInt())
    val small = Bitmap.createScaledBitmap(src, w, h, true)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        maskFilter = BlurMaskFilter(radiusDp * scale + 1f, BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawBitmap(small, 0f, 0f, paint)
    small.recycle()
    return out.asImageBitmap()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSessionDialog(onDismiss: () -> Unit, onCreateSession: (Int) -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        PreferenceGroup {
            SettingsCard(
                title = { Text("Ubuntu") },
                description = { Text(stringResource(strings.ubuntu_desc)) },
                onClick = { onCreateSession(WorkingMode.UBUNTU) }
            )
            SettingsCard(
                title = { Text("Android") },
                description = { Text(stringResource(strings.android_desc)) },
                onClick = { onCreateSession(WorkingMode.ANDROID) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenshotResolutionDialog(
    onDismiss: () -> Unit,
    onChoose: (Mode) -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        PreferenceGroup(heading = "Screenshot resolution") {
            SettingsCard(
                title = { Text("Phone resolution") },
                description = { Text("Rendered at the device font size (portrait).") },
                onClick = { onChoose(Mode.PHONE) }
            )
            SettingsCard(
                title = { Text("Desktop (macOS style)") },
                description = { Text("1440px-wide landscape, sharper text.") },
                onClick = { onChoose(Mode.DESKTOP) }
            )
        }
    }
}

private fun generateUniqueSessionId(existingIds: List<String>): String {
    var index = 1
    var newId: String
    do {
        newId = "main$index"
        index++
    } while (newId in existingIds)
    return newId
}

const val VIRTUAL_KEYS = "[" +
    "\n  [\"ESC\", {\"key\": \"/\", \"popup\": \"\\\\\"}, {\"key\": \"-\", \"popup\": \"|\"}, \"HOME\", \"UP\", \"END\", \"PGUP\"]," +
    "\n  [\"TAB\", \"CTRL\", \"ALT\", \"SHIFT\", \"LEFT\", \"DOWN\", \"RIGHT\", \"PGDN\"]" +
    "\n]"
