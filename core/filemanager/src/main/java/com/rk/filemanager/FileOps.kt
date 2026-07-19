package com.rk.filemanager

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Pure file-system operations for the file manager. All destructive callers
 * (delete, overwrite-on-paste) must confirm with the user in the UI layer first;
 * these functions do the work once confirmed.
 *
 * The two realms (Ubuntu Files = app-private exec mount, Phone Storage = FUSE
 * /sdcard) live on DIFFERENT filesystems. `File.renameTo` silently fails across
 * mount points, so cross-realm copy/move MUST use a byte-stream copy rather than
 * rename. `copyRecursive` below always streams, which is correct in both cases.
 */
object FileOps {

    sealed interface Result {
        data object Ok : Result
        data object Cancelled : Result
        data class Error(val message: String) : Result
    }

    /** Copy/move progress: [done] of [total] bytes (total may be 0 if unknown). */
    data class Progress(val done: Long, val total: Long)

    /** Thrown internally when a copy is cancelled mid-stream. */
    private class CancelledException : RuntimeException()

    /** True if pasting [sources] into [destDir] would overwrite an existing name. */
    fun collisions(sources: List<File>, destDir: File): List<String> =
        sources.filter { File(destDir, it.name).exists() }.map { it.name }

    /**
     * Total size in bytes of everything under [file] (files only). Used to drive
     * the progress bar. Returns 0 for an empty/unreadable tree.
     */
    fun totalBytes(file: File): Long = runCatching {
        if (file.isFile) return@runCatching file.length()
        file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)

    /**
     * Recursively copy [src] into [destDir] (keeping the source), reporting
     * [Progress] and honouring cancellation via [cancelled].
     */
    fun copyInto(
        src: File,
        destDir: File,
        progress: (Progress) -> Unit = {},
        cancelled: () -> Boolean = { false }
    ): Result = runCatching {
        val target = File(destDir, src.name)
        if (src.absolutePath == target.absolutePath) {
            return Result.Error("Source and destination are the same")
        }
        if (isSubPath(parent = src, child = destDir)) {
            return Result.Error("Cannot copy a folder into itself")
        }
        val total = totalBytes(src)
        try {
            copyRecursive(src, target, progress, cancelled, total, AtomicLong(0L))
        } catch (_: CancelledException) {
            target.deleteRecursively() // never leave a half-written tree
            return Result.Cancelled
        }
        if (cancelled()) {
            target.deleteRecursively()
            return Result.Cancelled
        }
        Result.Ok
    }.getOrElse { Result.Error("Copy failed: ${it.message ?: it::class.simpleName}") }

    /**
     * Move (cut+paste) [src] into [destDir]. Stream-copies across filesystems
     * (no renameTo), then removes the source on success. Cancellable; on cancel
     * the partial destination is removed but the source is preserved.
     */
    fun moveInto(
        src: File,
        destDir: File,
        progress: (Progress) -> Unit = {},
        cancelled: () -> Boolean = { false }
    ): Result = runCatching {
        val target = File(destDir, src.name)
        if (src.absolutePath == target.absolutePath) {
            return Result.Error("Source and destination are the same")
        }
        if (isSubPath(parent = src, child = destDir)) {
            return Result.Error("Cannot move a folder into itself")
        }
        if (target.exists()) target.deleteRecursively()
        // Never trust renameTo across the Ubuntu <-> Phone Storage boundary
        // (app-private exec mount vs FUSE /sdcard). Always stream-copy, then
        // remove the source so the move is atomic-ish and works both realms.
        val total = totalBytes(src)
        try {
            copyRecursive(src, target, progress, cancelled, total, AtomicLong(0L))
        } catch (_: CancelledException) {
            target.deleteRecursively() // partial destination removed, source kept
            return Result.Cancelled
        }
        if (cancelled()) {
            target.deleteRecursively()
            return Result.Cancelled
        }
        src.deleteRecursively()
        Result.Ok
    }.getOrElse { Result.Error("Move failed: ${it.message ?: it::class.simpleName}") }

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

    /**
     * Stream-copies [src] into [target], reporting cumulative [Progress] via
     * [progress] and aborting by throwing [CancelledException] when [cancelled]
     * returns true. [acc] tracks bytes copied so far across the whole tree.
     */
    internal fun copyRecursive(
        src: File,
        target: File,
        progress: (Progress) -> Unit,
        cancelled: () -> Boolean,
        total: Long,
        acc: AtomicLong
    ) {
        if (cancelled()) throw CancelledException()
        if (src.isDirectory) {
            if (!target.exists() && !target.mkdirs()) throw IllegalStateException("mkdir failed: ${target.name}")
            val children = src.listFiles()
                ?: throw IllegalStateException("Cannot read directory: ${src.absolutePath} (permission denied?)")
            children.forEach { copyRecursive(it, File(target, it.name), progress, cancelled, total, acc) }
        } else {
            target.parentFile?.let { if (!it.exists()) it.mkdirs() }
            val buffer = ByteArray(8192)
            FileInputStream(src).use { input ->
                FileOutputStream(target).use { output ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (cancelled()) throw CancelledException()
                        output.write(buffer, 0, read)
                        val done = acc.addAndGet(read.toLong())
                        progress(Progress(done, total))
                    }
                }
            }
        }
    }
}
