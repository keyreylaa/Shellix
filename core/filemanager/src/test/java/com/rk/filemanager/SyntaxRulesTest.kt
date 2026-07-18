package com.rk.filemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxRulesTest {

    private fun types(line: String, spec: LangSpec, inBlock: Boolean = false) =
        tokenizeLine(line, spec, inBlock).first.map { it.type }

    @Test fun kotlin_keyword_string_comment_number() {
        val spec = LangSpec.forFile("Main.kt")
        val (tokens, _) = tokenizeLine("val x = 42 // note", spec, false)
        val kinds = tokens.map { it.type }
        assertTrue(TokenType.KEYWORD in kinds)   // val
        assertTrue(TokenType.NUMBER in kinds)    // 42
        assertTrue(TokenType.COMMENT in kinds)   // // note
    }

    @Test fun python_hash_comment_and_string() {
        val spec = LangSpec.forFile("a.py")
        val (tokens, _) = tokenizeLine("def f(): return \"hi\"  # c", spec, false)
        val kinds = tokens.map { it.type }
        assertTrue(TokenType.KEYWORD in kinds)   // def / return
        assertTrue(TokenType.STRING in kinds)    // "hi"
        assertTrue(TokenType.COMMENT in kinds)   // # c
    }

    @Test fun block_comment_spans_multiple_lines() {
        val spec = LangSpec.forFile("a.c")
        val (_, open1) = tokenizeLine("/* start", spec, false)
        assertTrue(open1)                        // still inside block comment
        val (t2, open2) = tokenizeLine("still comment */ int x;", spec, true)
        assertFalse(open2)                       // block closed on this line
        assertEquals(TokenType.COMMENT, t2.first().type)
    }

    @Test fun unknown_extension_is_plain() {
        val spec = LangSpec.forFile("notes.unknownext")
        val kinds = types("class val def 123", spec)
        // no keywords for plain, but numbers still highlight
        assertFalse(TokenType.KEYWORD in kinds)
        assertTrue(TokenType.NUMBER in kinds)
    }

    @Test fun broad_language_coverage_resolves() {
        // sanity: a spread of extensions each map to a non-crashing spec
        for (name in listOf("a.rs","a.go","a.php","a.rb","a.swift","a.sql","a.css","a.lua","a.dart","a.toml","a.yaml","a.json")) {
            val spec = LangSpec.forFile(name)
            tokenizeLine("x = 1", spec, false) // must not throw
        }
        assertTrue(true)
    }
}
