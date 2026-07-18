package com.rk.shellix.ui.diagnostics

/**
 * Lightweight, allocation-free holder for runtime performance telemetry. Updated
 * once per second by the frame-drop counter in MainActivity and by the session
 * service; surfaced read-only in the Diagnostics "Performance" section.
 */
object PerfStats {
    @Volatile var lastJankPercent: Int = 0
        private set
    @Volatile var lastFrameCount: Int = 0
        private set
    @Volatile var activeSessions: Int = 0
    @Volatile var backgroundRenderPaused: Boolean = false

    fun update(jankPercent: Int, frames: Int) {
        lastJankPercent = jankPercent
        lastFrameCount = frames
    }
}
