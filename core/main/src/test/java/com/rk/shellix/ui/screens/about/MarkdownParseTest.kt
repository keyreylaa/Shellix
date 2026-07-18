package com.rk.shellix.ui.screens.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParseTest {
    @Test fun headings_bullets_code_paragraph() {
        val md = """
            # Title
            Some intro line
            that wraps.

            - one
            - two

            ```
            code here
            ```
        """.trimIndent()
        val b = parseMarkdown(md)
        assertTrue(b[0] is MdBlock.Heading && (b[0] as MdBlock.Heading).level == 1)
        assertEquals("Title", (b[0] as MdBlock.Heading).text)
        // wrapped paragraph joined
        assertTrue(b.any { it is MdBlock.Paragraph && (it as MdBlock.Paragraph).text.contains("wraps") })
        assertTrue(b.count { it is MdBlock.Bullet } == 2)
        assertTrue(b.any { it is MdBlock.Code && (it as MdBlock.Code).text.trim() == "code here" })
    }

    @Test fun code_block_is_not_parsed_as_markdown() {
        val md = "```\n# not a heading\n- not a bullet\n```"
        val b = parseMarkdown(md)
        assertEquals(1, b.size)
        assertTrue(b[0] is MdBlock.Code)
    }
}
