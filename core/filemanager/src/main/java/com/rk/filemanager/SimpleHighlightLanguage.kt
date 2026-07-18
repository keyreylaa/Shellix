package com.rk.filemanager

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import android.os.Bundle

/**
 * A dependency-free [Language] that drives highlighting from [tokenizeLine].
 * See the ponytail note in SyntaxRules.kt for the fidelity ceiling and upgrade
 * path (TextMate/TreeSitter).
 */
class SimpleHighlightLanguage(private val spec: LangSpec) : Language {

    private val analyzer = object : SimpleAnalyzeManager<Unit>() {
        override fun analyze(text: StringBuilder, delegate: Delegate<Unit>): Styles {
            val builder = MappedSpans.Builder()
            var inBlock = false
            val kw = TextStyle.makeStyle(EditorColorScheme.KEYWORD)
            val comment = TextStyle.makeStyle(EditorColorScheme.COMMENT)
            val str = TextStyle.makeStyle(EditorColorScheme.LITERAL)
            val num = TextStyle.makeStyle(EditorColorScheme.LITERAL)
            val normal = TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)

            // Scan lines manually and hand tokenizeLine a zero-copy substring view
            // instead of text.toString().split("\n"). split() allocates a String[]
            // plus a copy per line on every analyze pass (analyze re-runs on each text
            // change), which is the main cost for large files. substring() is a view,
            // so this keeps large-file tokenize cheap (Tugas 6, F1).
            val n = text.length
            var lineIdx = 0
            var start = 0
            while (start <= n) {
                if (delegate.isCancelled) break
                var end = start
                while (end < n && text[end] != '\n') end++
                // every line needs a span starting at column 0
                builder.addIfNeeded(lineIdx, 0, normal)
                val (tokens, next) = tokenizeLine(text.substring(start, end), spec, inBlock)
                inBlock = next
                for (t in tokens) {
                    val style = when (t.type) {
                        TokenType.KEYWORD -> kw
                        TokenType.COMMENT -> comment
                        TokenType.STRING -> str
                        TokenType.NUMBER -> num
                        TokenType.PLAIN -> normal
                    }
                    builder.addIfNeeded(lineIdx, t.start, style)
                    // reset to normal after the token
                    builder.addIfNeeded(lineIdx, t.end, normal)
                }
                lineIdx++
                if (end >= n) break
                start = end + 1
            }
            return Styles(builder.build())
        }
    }

    override fun getAnalyzeManager(): AnalyzeManager = analyzer
    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_STRONG
    override fun requireAutoComplete(
        content: ContentReference, position: CharPosition,
        publisher: CompletionPublisher, extraArguments: Bundle
    ) { /* no completion */ }
    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int = 0
    override fun useTab(): Boolean = false
    override fun getFormatter() = EmptyLanguage.EmptyFormatter.INSTANCE
    override fun getSymbolPairs() = EmptyLanguage.EMPTY_SYMBOL_PAIRS
    override fun getNewlineHandlers(): Array<NewlineHandler>? = null
    override fun destroy() { analyzer.destroy() }
}
