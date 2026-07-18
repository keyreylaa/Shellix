package com.rk.filemanager

import java.io.File

/**
 * Pure file-system operations for the file manager. All destructive callers
 * (delete, overwrite-on-paste) must confirm with the user in the UI layer first;
 * these functions do the work once confirmed.
 */
object FileOps {

    sealed interface Result {
        data object Ok : Result
        data class Error(val message: String) : Result
    }

    /** True if pasting [sources] into [destDir] would overwrite an existing name. */
    fun collisions(sources: List<File>, destDir: File): List<String> =
        sources.filter { File(destDir, it.name).exists() }.map { it.name }

    /** Recursively copy [src] into [destDir] (keeping the source). */
    fun copyInto(src: File, destDir: File): Result = runCatching {
        val target = File(destDir, src.name)
        if (src.absolutePath == target.absolutePath) {
            return Result.Error("Source and destination are the same")
        }
        if (isSubPath(parent = src, child = destDir)) {
            return Result.Error("Cannot copy a folder into itself")
        }
        copyRecursive(src, target)
        Result.Ok
    }.getOrElse { Result.Error(it.message ?: "Copy failed") }

    /** Move (cut+paste) [src] into [destDir]. Falls back to copy+delete across mounts. */
    fun moveInto(src: File, destDir: File): Result = runCatching {
        val target = File(destDir, src.name)
        if (src.absolutePath == target.absolutePath) {
            return Result.Error("Source and destination are the same")
        }
        if (isSubPath(parent = src, child = destDir)) {
            return Result.Error("Cannot move a folder into itself")
        }
        if (target.exists()) target.deleteRecursively()
        if (!src.renameTo(target)) {
            // Cross-realm (app-private <-> /sdcard FUSE) rename fails; copy then delete.
            copyRecursive(src, target)
            src.deleteRecursively()
        }
        Result.Ok
    }.getOrElse { Result.Error(it.message ?: "Move failed") }

    fun delete(file: File): Result = runCatching {
        if (!file.deleteRecursively()) return Result.Error("Could not delete ${file.name}")
        Result.Ok
    }.getOrElse { Result.Error(it.message ?: "Delete failed") }

    fun rename(file: File, newName: String): Result = runCatching {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return Result.Error("Name cannot be empty")
        if (trimmed.contains('/')) return Result.Error("Name cannot contain '/'")
        val target = File(file.parentFile, trimmed)
        if (target.exists()) return Result.Error("'$trimmed' already exists")
        if (!file.renameTo(target)) return Result.Error("Rename failed")
        Result.Ok
    }.getOrElse { Result.Error(it.message ?: "Rename failed") }

    fun createFolder(parent: File, name: String): Result = runCatching {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.Error("Name cannot be empty")
        if (trimmed.contains('/')) return Result.Error("Name cannot contain '/'")
        val target = File(parent, trimmed)
        if (target.exists()) return Result.Error("'$trimmed' already exists")
        if (!target.mkdir()) return Result.Error("Could not create folder")
        Result.Ok
    }.getOrElse { Result.Error(it.message ?: "Create folder failed") }

    fun createFile(parent: File, name: String): Result = runCatching {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.Error("Name cannot be empty")
        if (trimmed.contains('/')) return Result.Error("Name cannot contain '/'")
        val target = File(parent, trimmed)
        if (target.exists()) return Result.Error("'$trimmed' already exists")
        if (!target.createNewFile()) return Result.Error("Could not create file")
        Result.Ok
    }.getOrElse { Result.Error(it.message ?: "Create file failed") }

    /** True when [child] is [parent] itself or nested under it (prevents self-recursion). */
    fun isSubPath(parent: File, child: File): Boolean {
        val p = parent.absolutePath.removeSuffix("/")
        val c = child.absolutePath.removeSuffix("/")
        return c == p || c.startsWith("$p/")
    }

    private fun copyRecursive(src: File, target: File) {
        if (src.isDirectory) {
            if (!target.exists() && !target.mkdirs()) throw IllegalStateException("mkdir failed: ${target.name}")
            src.listFiles()?.forEach { copyRecursive(it, File(target, it.name)) }
        } else {
            target.parentFile?.let { if (!it.exists()) it.mkdirs() }
            src.copyTo(target, overwrite = true)
        }
    }
}
