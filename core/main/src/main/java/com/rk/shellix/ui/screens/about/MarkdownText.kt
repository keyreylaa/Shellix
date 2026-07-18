package com.rk.shellix.ui.screens.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal Markdown renderer (no external dependency). Supports headings, bold,
 * inline code, bullet lists, fenced code blocks, links, and paragraphs — enough
 * to display a GitHub Wiki page cleanly. Not a full CommonMark implementation.
 */
@Composable
fun MarkdownText(
    markdown: String,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Column(modifier = modifier) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Heading -> Text(
                    text = block.text,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                is MdBlock.Code -> Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = block.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                is MdBlock.Bullet -> LinkableText(
                    inlineAnnotated("• " + block.text),
                    onLinkClick,
                    Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                )
                is MdBlock.Paragraph -> LinkableText(
                    inlineAnnotated(block.text),
                    onLinkClick,
                    Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LinkableText(
    annotated: AnnotatedString,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.onSurface
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(color = color),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { onLinkClick(it.item) }
        }
    )
}

// ── Pure parser (unit-testable, no Android/Compose types) ──────────

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
}

fun parseMarkdown(md: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = md.replace("\r\n", "\n").split("\n")
    var i = 0
    val para = StringBuilder()
    fun flushPara() {
        if (para.isNotBlank()) out.add(MdBlock.Paragraph(para.toString().trim()))
        para.setLength(0)
    }
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                flushPara()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.appendLine(lines[i]); i++
                }
                out.add(MdBlock.Code(code.toString().trimEnd('\n')))
            }
            line.startsWith("#") -> {
                flushPara()
                val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
                out.add(MdBlock.Heading(level, line.drop(level).trim()))
            }
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                flushPara()
                out.add(MdBlock.Bullet(line.trimStart().drop(2).trim()))
            }
            line.isBlank() -> flushPara()
            else -> { if (para.isNotEmpty()) para.append(' '); para.append(line.trim()) }
        }
        i++
    }
    flushPara()
    return out
}

/** Render inline **bold**, `code`, and [text](url) links into an AnnotatedString. */
fun inlineAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        // link [label](url)
        if (text[i] == '[') {
            val close = text.indexOf(']', i)
            val open = if (close >= 0 && close + 1 < text.length && text[close + 1] == '(') close + 1 else -1
            val end = if (open >= 0) text.indexOf(')', open) else -1
            if (close > i && open >= 0 && end > open) {
                val label = text.substring(i + 1, close)
                val url = text.substring(open + 1, end)
                pushStringAnnotation("URL", url)
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(label) }
                pop()
                i = end + 1
                continue
            }
        }
        // bold **text**
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > i) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                i = end + 2
                continue
            }
        }
        // inline code `text`
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(text.substring(i + 1, end)) }
                i = end + 1
                continue
            }
        }
        append(text[i]); i++
    }
}
