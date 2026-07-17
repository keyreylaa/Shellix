package com.rk.shellix.ui.diagnostics

import android.util.Log

/**
 * Drop-in replacement for [Log] that forwards to both Android Logcat and the
 * in-app [Diagnostics] ring buffer, so messages are visible in the Settings
 * → Diagnostics viewer without pulling Logcat.
 */
object AppLog {
    fun v(tag: String, msg: String) { Log.v(tag, msg); Diagnostics.v(tag, msg) }
    fun d(tag: String, msg: String) { Log.d(tag, msg); Diagnostics.d(tag, msg) }
    fun i(tag: String, msg: String) { Log.i(tag, msg); Diagnostics.i(tag, msg) }
    fun w(tag: String, msg: String) { Log.w(tag, msg); Diagnostics.w(tag, msg) }
    fun e(tag: String, msg: String) { Log.e(tag, msg); Diagnostics.e(tag, msg) }
    fun e(tag: String, msg: String, tr: Throwable) {
        Log.e(tag, msg, tr)
        Diagnostics.e(tag, "$msg\n${Log.getStackTraceString(tr)}")
    }
}
