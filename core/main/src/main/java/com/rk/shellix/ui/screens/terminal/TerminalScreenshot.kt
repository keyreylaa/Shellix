package com.rk.shellix.ui.screens.terminal

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.rk.libcommons.child
import com.rk.libcommons.dpToPx
import com.rk.libcommons.ubuntuDir
import com.rk.settings.Settings
import com.termux.terminal.WcWidth
import com.termux.view.TerminalView
import java.io.File

/**
 * "PC-style" terminal screenshot.
 *
 * Instead of capturing raw pixels, this re-renders the ACTIVE terminal session into a
 * Bitmap styled like a macOS desktop terminal window: a rounded frame, a title bar with
 * the three traffic-light dots, and the live screen text painted with its real ANSI
 * colors + the user's active Terminal Theme (Dracula/Default/Soft Dark/...).
 *
 * Only the currently visible viewport is captured, not the full scrollback buffer — a
 * single image of tens of thousands of lines is not useful.
 *
 * Two output resolutions are supported:
 *  - [Mode.PHONE]  : rendered at the device font size / density (portrait, narrow).
 *  - [Mode.DESKTOP]: re-rendered from the SAME character grid + ANSI colors at a fixed
 *    1440px-wide landscape canvas with a proportionally larger font, so text stays sharp
 *    (no upscaling of a small bitmap). Mirrors a real macOS Terminal screenshot.
 *
 * All disk work (MediaStore insert / staging for share) is the caller's responsibility
 * to run off the main thread; capture + draw is CPU-bound but bounded by the grid size.
 */

private const val SHOTS_DIR = "fm_shots"
private const val AUTHORITY_SUFFIX = ".fileprovider"

/** Output resolution for the screenshot. */
enum class Mode { PHONE, DESKTOP }

object TerminalScreenshot {

    /**
     * Build the macOS-style window title from the ACTIVE PRoot session identity, e.g.
     * `keyreyla@shellix`. The username is read live from the running session's identity
     * file (`/etc/shellix_default_user`) — it differs per user and is never hardcoded.
     * The host portion is the literal `shellix` that the shell prompt itself uses
     * (see init.sh `PS1='\u@shellix'`), NOT the Android device model.
     */
    fun title(context: Context): String {
        val user = readActiveUser(context) ?: "shellix"
        return "$user@shellix"
    }

    /** Resolve the running PRoot username from the live identity file. */
    private fun readActiveUser(context: Context): String? {
        return runCatching {
            val file = context.ubuntuDir().child("etc").child("shellix_default_user")
            if (file.exists()) file.readText().trim().takeIf { it.isNotBlank() } else null
        }.getOrNull()
    }

    /**
     * Render the active session of [terminalView] to a Bitmap, or null if there is no
     * live emulator yet. Only the visible viewport is drawn.
     */
    fun capture(terminalView: TerminalView, context: Context, title: String, mode: Mode = Mode.PHONE): Bitmap? {
        val emulator = terminalView.mEmulator ?: return null
        val rows = emulator.mRows
        val cols = emulator.mColumns
        if (rows <= 0 || cols <= 0) return null

        val scheme = TerminalColorSchemes.byName(Settings.terminal_color_scheme)
        val palette = emulator.mColors.mCurrentColors

        // Resolve terminal background / foreground from the active theme; fall back to
        // the live palette (indices 257/256) when the scheme leaves them unspecified.
        val termBg = scheme.background?.let { Color.parseColor("#$it") }
            ?: palette[257]
        val termFg = scheme.foreground?.let { Color.parseColor("#$it") }
            ?: palette[256]

        val typeface = TerminalUtils.typeface

        // Desktop mode: fixed 1440px content width, font sized so the same column count
        // fits, drawn in absolute px (density-independent target). Phone mode keeps the
        // on-device font size expressed in dp.
        val desktopWidthPx = 1440f
        val fontPx = if (mode == Mode.DESKTOP) {
            // monospace cell width ~= 0.6 * font size -> font = (width/cols) / 0.6
            (desktopWidthPx / cols) / 0.6f
        } else {
            dpToPx(Settings.terminal_font_size.toFloat(), context).toFloat()
        }
        val px = { dp: Float -> if (mode == Mode.DESKTOP) dp else dpToPx(dp, context).toFloat() }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = fontPx
            color = termFg
        }
        val fontWidth = paint.measureText("X")
        val lineHeight = fontPx * 1.2f

        // macOS window metrics.
        val titleBarH = px(34f)
        val padX = px(12f)
        val padY = px(10f)
        val radius = px(12f)

        val contentW = fontWidth * cols
        val contentH = lineHeight * rows
        val windowW = contentW + padX * 2
        val windowH = titleBarH + contentH + padY * 2

        val bitmap = Bitmap.createBitmap(
            windowW.toInt().coerceAtLeast(1),
            windowH.toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)

        // Window frame (slightly lighter than the terminal bg for a desktop feel).
        val frameColor = blend(termBg, Color.WHITE, 0.08f)
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = frameColor }
        canvas.drawRoundRect(0f, 0f, windowW, windowH, radius, radius, framePaint)

        // Title bar separator line.
        val sepPaint = Paint().apply { color = blend(termBg, Color.BLACK, 0.25f) }
        canvas.drawLine(0f, titleBarH, windowW, titleBarH, sepPaint)

        // Traffic-light dots.
        val dotR = px(6f)
        val dotY = titleBarH / 2f
        val dotX0 = padX
        drawDot(canvas, dotX0, dotY, dotR, Color.parseColor("#FF5F56"))
        drawDot(canvas, dotX0 + dotR * 2.6f, dotY, dotR, Color.parseColor("#FFBD2E"))
        drawDot(canvas, dotX0 + dotR * 5.2f, dotY, dotR, Color.parseColor("#27C93F"))

        // Title text.
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = Typeface.create(typeface, Typeface.NORMAL)
            textSize = px(12f)
            color = blend(termFg, termBg, 0.35f)
        }
        val titleX = dotX0 + dotR * 7f
        val titleY = dotY - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(title, titleX, titleY, titlePaint)

        // Terminal content background.
        val bgPaint = Paint().apply { color = termBg }
        canvas.drawRect(padX, titleBarH, padX + contentW, titleBarH + contentH, bgPaint)

        // Text rows. termux indexes the screen buffer by "external row" where the
        // visible viewport is the last `rows` lines: [activeTranscriptRows, +rows).
        // (mTopRow is package-private in TerminalView, so derive the window from the
        // buffer instead — correct for the non-scrolled current view.)
        val buffer = emulator.getScreen()
        val transcriptBase = buffer.activeTranscriptRows
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = fontPx
        }
        val baseline = titleBarH + padY
        for (r in 0 until rows) {
            val externalRow = transcriptBase + r
            val line = runCatching { buffer.allocateFullLineIfNecessary(externalRow) }.getOrNull()
                ?: continue
            val y = baseline + r * lineHeight + lineHeight * 0.82f
            var col = 0
            var charIdx = 0
            val text = line.mText
            while (col < cols && charIdx < text.size) {
                val codePoint = if (Character.isHighSurrogate(text[charIdx]) && charIdx + 1 < text.size) {
                    Character.toCodePoint(text[charIdx], text[charIdx + 1])
                } else {
                    text[charIdx].code
                }
                val charLen = if (Character.isHighSurrogate(text[charIdx])) 2 else 1
                val width = WcWidth.width(codePoint)
                if (width > 0 && codePoint != 0) {
                    val style = line.getStyle(col)
                    drawStyledChar(
                        canvas, textPaint, codePoint,
                        padX + col * fontWidth, y,
                        style, palette, termFg, termBg
                    )
                }
                charIdx += charLen
                col += if (width <= 0) 1 else width
            }
        }
        return bitmap
    }

    private fun drawStyledChar(
        canvas: Canvas,
        base: Paint,
        codePoint: Int,
        x: Float,
        y: Float,
        style: Long,
        palette: IntArray,
        defFg: Int,
        defBg: Int
    ) {
        var fg = resolveColor(TextStyle_decodeForeColor(style), palette, defFg)
        var bg = resolveColor(TextStyle_decodeBackColor(style), palette, defBg)
        val effect = TextStyle_decodeEffect(style)

        val bold = (effect and 0x1) != 0
        val inverse = (effect and 0x10) != 0
        val dim = (effect and 0x100) != 0

        if (inverse) {
            val t = fg; fg = bg; bg = t
        }
        if (dim) {
            fg = blend(fg, bg, 0.34f)
        }

        // Background cell (only if it differs from the terminal bg).
        if (bg != defBg) {
            val bgPaint = Paint().apply { color = bg }
            // Approximate cell width using the base paint's measured 'X' width.
            val cw = base.measureText("X")
            canvas.drawRect(x, y - base.textSize * 0.82f, x + cw, y + base.textSize * 0.18f, bgPaint)
        }

        val p = Paint(base).apply {
            color = fg
            if (bold) isFakeBoldText = true
            if ((effect and 0x2) != 0) textSkewX = -0.25f
            if ((effect and 0x4) != 0) isUnderlineText = true
            if ((effect and 0x40) != 0) isStrikeThruText = true
        }
        canvas.drawText(String(Character.toChars(codePoint)), x, y, p)
    }

    private fun resolveColor(raw: Int, palette: IntArray, fallback: Int): Int {
        return if ((raw and 0xff000000.toInt()) == 0xff000000.toInt()) {
            raw
        } else {
            // Indexed palette entry; bounds-check to avoid crashes on stale indices.
            if (raw in palette.indices) palette[raw] else fallback
        }
    }

    // Thin wrappers around termux TextStyle decode helpers (package com.termux.terminal).
    private fun TextStyle_decodeForeColor(style: Long): Int =
        com.termux.terminal.TextStyle.decodeForeColor(style)
    private fun TextStyle_decodeBackColor(style: Long): Int =
        com.termux.terminal.TextStyle.decodeBackColor(style)
    private fun TextStyle_decodeEffect(style: Long): Int =
        com.termux.terminal.TextStyle.decodeEffect(style)

    private fun drawDot(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(cx, cy, r, paint)
    }

    /** Mix [from] toward [to] by [ratio] (0 = from, 1 = to). */
    private fun blend(from: Int, to: Int, ratio: Float): Int {
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * ratio).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * ratio).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * ratio).toInt()
        return Color.rgb(r, g, b)
    }

    /**
     * Persist [bitmap] to the gallery via MediaStore (Pictures/Shellix/), returning the
     * saved File (a scoped storage copy) or null on failure. Runs on a background thread.
     */
    fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): File? {
        val pngFile = File(context.filesDir, SHOTS_DIR).also { it.mkdirs() }
        val file = File(pngFile, "$displayName.png")
        return runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Shellix")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching file
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            file
        }.getOrNull()
    }

    /** Stage [bitmap] and return a shareable ACTION_SEND chooser intent, or null. */
    fun shareIntent(context: Context, bitmap: Bitmap, displayName: String): Intent? {
        val staged = runCatching {
            val dir = File(context.filesDir, SHOTS_DIR).also { it.mkdirs() }
            val file = File(dir, "$displayName.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file
        }.getOrNull() ?: return null
        val uri = FileProvider.getUriForFile(
            context, context.packageName + AUTHORITY_SUFFIX, staged
        )
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, null
        )
    }
}
