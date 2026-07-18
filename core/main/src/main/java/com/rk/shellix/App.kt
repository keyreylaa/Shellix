package com.rk.shellix

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import com.github.anrwatchdog.ANRWatchDog
import com.rk.libcommons.application
import com.rk.resources.Res
import com.rk.shellix.ui.screens.terminal.TerminalUtils
import com.rk.shellix.ui.screens.terminal.TerminalColorSchemes
import com.rk.shellix.ui.screens.terminal.TerminalThemes
import com.rk.settings.Settings
import com.rk.shellix.ui.diagnostics.Diagnostics
import com.rk.update.UpdateManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

class App : Application() {

    companion object {
        fun getTempDir(context: Context): File {
            val tmp = File(context.cacheDir, "tmp")
            if (!tmp.exists()) {
                tmp.mkdir()
            }
            return tmp
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        application = this
        Res.application = this
        TerminalUtils.init(this)

        GlobalScope.launch(Dispatchers.IO) {
            getTempDir(this@App).apply {
                if (exists() && listFiles().isNullOrEmpty().not()) {
                    deleteRecursively()
                }
            }
        }

        ANRWatchDog().start()

        Diagnostics.installCrashHandler(this)
        Diagnostics.lastCrashReport = Diagnostics.loadPersistedCrash(this)

        UpdateManager(this).onUpdate()

        // Materialize the persisted terminal color scheme into colors.properties so the
        // fresh-install default (Soft Dark) is applied without the user opening settings.
        // Reads whatever the user last chose, so explicit picks (incl. "Default") are kept.
        runCatching {
            TerminalThemes.applyScheme(
                this,
                TerminalColorSchemes.byName(Settings.terminal_color_scheme)
            )
        }

        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().apply {
                    detectAll()
                    penaltyLog()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
                            violation.printStackTrace()
                        }
                    }
                }.build()
            )
        }
    }
}
