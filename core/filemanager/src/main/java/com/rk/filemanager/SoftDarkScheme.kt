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
        setColor(WHOLE_BACKGROUND, 0xff211f2d)
        setColor(COMPLETION_WND_BACKGROUND, 0xff211f2d)
        setColor(CURRENT_LINE, 0xff2e2b3d)
        setColor(BLOCK_LINE, 0xff262433)
        setColor(SIDE_BLOCK_LINE, 0xff262433)
        // text
        setColor(TEXT_NORMAL, 0xffd9d6e3)
        setColor(LINE_NUMBER, 0xff6c6884)
        setColor(LINE_NUMBER_CURRENT, 0xff6c6884)
        setColor(LINE_DIVIDER, 0xff38354a)
        // selection / match
        setColor(SELECTED_TEXT_BACKGROUND, 0x66c4a7e0)
        setColor(MATCHED_TEXT_BACKGROUND, 0xff2e2b3d)
        // scrollbar
        setColor(SCROLL_BAR_THUMB, 0xff6c6884)
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xff9d99b3)
        setColor(SCROLL_BAR_TRACK, 0x00000000)
        // syntax tokens
        setColor(KEYWORD, 0xffb298e0)
        setColor(COMMENT, 0xff6c6884)
        setColor(LITERAL, 0xff9ed6a3)
        setColor(OPERATOR, 0xffd9d6e3)
        setColor(IDENTIFIER_NAME, 0xffd9d6e3)
        setColor(IDENTIFIER_VAR, 0xffa98fc4)
        setColor(FUNCTION_NAME, 0xffd9d6e3)
        setColor(ANNOTATION, 0xffe8cf8f)
        setColor(NON_PRINTABLE_CHAR, 0xff9d99b3)
    }
}
