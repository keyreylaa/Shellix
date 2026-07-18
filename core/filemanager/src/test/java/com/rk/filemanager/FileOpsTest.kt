package com.rk.filemanager

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileOpsTest {
    private fun tmp() = Files.createTempDirectory("fmops").toFile()

    @Test
    fun copy_keeps_source_and_duplicates_tree() {
        val root = tmp()
        val src = File(root, "dir").apply { mkdir() }
        File(src, "a.txt").writeText("hello")
        val dest = File(root, "dest").apply { mkdir() }

        assertEquals(FileOps.Result.Ok, FileOps.copyInto(src, dest))
        assertTrue(File(root, "dir/a.txt").exists())            // source kept
        assertEquals("hello", File(dest, "dir/a.txt").readText()) // copied
        root.deleteRecursively()
    }

    @Test
    fun move_removes_source() {
        val root = tmp()
        val src = File(root, "f.txt").apply { writeText("x") }
        val dest = File(root, "d").apply { mkdir() }

        assertEquals(FileOps.Result.Ok, FileOps.moveInto(src, dest))
        assertFalse(src.exists())
        assertTrue(File(dest, "f.txt").exists())
        root.deleteRecursively()
    }

    @Test
    fun collisions_detects_existing_names() {
        val root = tmp()
        val a = File(root, "a.txt").apply { writeText("1") }
        val dest = File(root, "dest").apply { mkdir() }
        File(dest, "a.txt").writeText("old")

        assertEquals(listOf("a.txt"), FileOps.collisions(listOf(a), dest))
        root.deleteRecursively()
    }

    @Test
    fun cannot_copy_folder_into_itself() {
        val root = tmp()
        val dir = File(root, "d").apply { mkdir() }
        val sub = File(dir, "sub").apply { mkdir() }
        val r = FileOps.copyInto(dir, sub)
        assertTrue(r is FileOps.Result.Error)
        root.deleteRecursively()
    }

    @Test
    fun rename_rejects_slash_and_collision() {
        val root = tmp()
        val f = File(root, "f.txt").apply { writeText("x") }
        File(root, "g.txt").writeText("y")
        assertTrue(FileOps.rename(f, "a/b") is FileOps.Result.Error)
        assertTrue(FileOps.rename(f, "g.txt") is FileOps.Result.Error)
        assertEquals(FileOps.Result.Ok, FileOps.rename(f, "renamed.txt"))
        assertTrue(File(root, "renamed.txt").exists())
        root.deleteRecursively()
    }

    @Test
    fun createFolder_and_createFile_guard_duplicates() {
        val root = tmp()
        assertEquals(FileOps.Result.Ok, FileOps.createFolder(root, "newdir"))
        assertTrue(FileOps.createFolder(root, "newdir") is FileOps.Result.Error)
        assertEquals(FileOps.Result.Ok, FileOps.createFile(root, "new.txt"))
        assertTrue(FileOps.createFile(root, "new.txt") is FileOps.Result.Error)
        root.deleteRecursively()
    }
}
