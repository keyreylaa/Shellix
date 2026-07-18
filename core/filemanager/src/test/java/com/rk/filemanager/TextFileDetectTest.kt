package com.rk.filemanager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFileDetectTest {
    @Test fun plain_text_is_text() {
        assertTrue(TextFileDetect.isProbablyText("hello world\n\ttab line\r\n".toByteArray()))
    }
    @Test fun nul_byte_is_binary() {
        assertFalse(TextFileDetect.isProbablyText(byteArrayOf(72, 105, 0, 33)))
    }
    @Test fun many_control_bytes_is_binary() {
        val b = ByteArray(100) { 0x01 } // all SOH control bytes
        assertFalse(TextFileDetect.isProbablyText(b))
    }
    @Test fun empty_is_text() {
        assertTrue(TextFileDetect.isProbablyText(ByteArray(0), 0))
    }
}
