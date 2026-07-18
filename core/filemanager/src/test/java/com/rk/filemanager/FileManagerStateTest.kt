package com.rk.filemanager

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileManagerStateTest {

    @Test
    fun formatSize_scales_units() {
        assertEquals("512 B", formatSize(512))
        assertEquals("1.0 KB", formatSize(1024))
        assertEquals("1.5 KB", formatSize(1536))
        assertEquals("1.0 MB", formatSize(1024L * 1024))
    }

    @Test
    fun listDir_dirs_first_then_alpha() {
        val root = Files.createTempDirectory("fm").toFile()
        File(root, "b.txt").writeText("x")
        File(root, "A.txt").writeText("x")
        File(root, "zdir").mkdir()
        File(root, "Adir").mkdir()

        val names = listDir(root).map { it.name }
        // directories first (case-insensitive alpha), then files (case-insensitive alpha)
        assertEquals(listOf("Adir", "zdir", "A.txt", "b.txt"), names)
        root.deleteRecursively()
    }

    @Test
    fun breadcrumbs_from_root_to_current() {
        val root = Files.createTempDirectory("fmroot").toFile()
        val sub = File(root, "a/b").apply { mkdirs() }

        val crumbs = breadcrumbs(root, sub, "Ubuntu Files")
        assertEquals(listOf("Ubuntu Files", "a", "b"), crumbs.map { it.first })
        assertEquals(root.absolutePath, crumbs.first().second.absolutePath)
        assertEquals(sub.absolutePath, crumbs.last().second.absolutePath)
        root.deleteRecursively()
    }

    @Test
    fun breadcrumbs_at_root_is_single_segment() {
        val root = Files.createTempDirectory("fmroot2").toFile()
        val crumbs = breadcrumbs(root, root, "Phone Storage")
        assertEquals(listOf("Phone Storage"), crumbs.map { it.first })
        root.deleteRecursively()
    }
}
