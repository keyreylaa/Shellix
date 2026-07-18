package com.rk.filemanager

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Dark editor color scheme matching the app's "Soft Dark" terminal palette
 * (see core:main TerminalTheme). Reused colors: terminal bg #211f2d, foreground
 * #d9d6e3 (11.3:1 contrast, WCAG AAA), accent #c4a7e0.
 *
 * Token colors (KEYWORD/COMMENT/LITERAL) are pulled by SimpleHighlightLanguage
 * from this scheme, so syntax highlighting stays readable on the dark bg.
 */
class SoftDarkScheme : EditorColorScheme() {
    override fun applyDefault() {
        super.applyDefault()
        // base surfaces
        setColor(WHOLE_BACKGROUND, 0xff211f2d.toInt())
        setColor(COMPLETION_WND_BACKGROUND, 0xff211f2d.toInt())
        setColor(CURRENT_LINE, 0xff2e2b3d.toInt())
        setColor(BLOCK_LINE, 0xff262433.toInt())
        setColor(SIDE_BLOCK_LINE, 0xff262433.toInt())
        // text
        setColor(TEXT_NORMAL, 0xffd9d6e3.toInt())
        setColor(LINE_NUMBER, 0xff6c6884.toInt())
        setColor(LINE_NUMBER_CURRENT, 0xff6c6884.toInt())
        setColor(LINE_DIVIDER, 0xff38354a.toInt())
        // selection / match
        setColor(SELECTED_TEXT_BACKGROUND, 0x66c4a7e0.toInt())
        setColor(MATCHED_TEXT_BACKGROUND, 0xff2e2b3d.toInt())
        // scrollbar
        setColor(SCROLL_BAR_THUMB, 0xff6c6884.toInt())
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xff9d99b3.toInt())
        setColor(SCROLL_BAR_TRACK, 0x00000000.toInt())
        // syntax tokens
        setColor(KEYWORD, 0xffb298e0.toInt())
        setColor(COMMENT, 0xff6c6884.toInt())
        setColor(LITERAL, 0xff9ed6a3.toInt())
        setColor(OPERATOR, 0xffd9d6e3.toInt())
        setColor(IDENTIFIER_NAME, 0xffd9d6e3.toInt())
        setColor(IDENTIFIER_VAR, 0xffa98fc4.toInt())
        setColor(FUNCTION_NAME, 0xffd9d6e3.toInt())
        setColor(ANNOTATION, 0xffe8cf8f.toInt())
        setColor(NON_PRINTABLE_CHAR, 0xff9d99b3.toInt())
    }
}
