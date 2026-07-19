package com.rk.filemanager

import android.content.Context
import java.io.File

/**
 * Soft-delete (trash) support for the file manager.
 *
 * Deletion is never immediate: items are moved into an app-internal trash directory
 * (`filesDir/fm_trash`) together with a `.meta` sidecar recording the original absolute
 * path. This keeps delete reversible (Undo) and survives an app kill — the file is simply
 * already in the trash, not lost.
 *
 * The trash lives in app-internal storage on purpose: it is always writable without any
 * extra permission (unlike Phone Storage / FUSE, whose write access is user-gated via
 * MANAGE_EXTERNAL_STORAGE). Restoration moves the item back to its recorded absolute path;
 * if that location is no longer writable the caller is told so it can surface an error.
 */
object TrashUtil {

    private const val TRASH_DIR = "fm_trash"
    private const val META_SUFFIX = ".meta"

    private fun trashDir(context: Context): File =
        File(context.filesDir, TRASH_DIR).also { if (!it.exists()) it.mkdirs() }

    /**
     * Move [file] into the trash. Returns the trash entry File on success, or null on failure.
     * A sidecar `<entry>.meta` stores the original absolute path for restoration.
     */
    fun trash(context: Context, file: File): File? {
        if (!file.exists()) return null
        val base = file.name
        var entry = File(trashDir(context), base)
        var n = 1
        while (entry.exists()) {
            entry = File(trashDir(context), "${file.nameWithoutExtension}__$n${if (file.extension.isNotEmpty()) ".${file.extension}" else ""}")
            n++
        }
        val meta = File(trashDir(context), entry.name + META_SUFFIX)
        return try {
            if (!file.renameTo(entry)) {
                // Cross-mount move can fail; fall back to stream copy + delete.
                FileOps.copyRecursive(file, entry)
                file.deleteRecursively()
            }
            meta.writeText(file.absolutePath)
            entry
        } catch (_: Exception) {
            entry.deleteRecursively()
            meta.delete()
            null
        }
    }

    /** Original absolute path recorded for a trashed [entry], or null if missing. */
    fun originalPath(entry: File): String? {
        val meta = File(entry.parentFile, entry.name + META_SUFFIX)
        return if (meta.exists()) meta.readText().trim().takeIf { it.isNotBlank() } else null
    }

    /**
     * Restore a trashed [entry] to its original path. Returns true on success.
     * Fails (false) if the destination is not writable (e.g. Phone Storage without
     * MANAGE_EXTERNAL_STORAGE).
     */
    fun restore(context: Context, entry: File): Boolean {
        val destPath = originalPath(entry) ?: return false
        val dest = File(destPath)
        dest.parentFile?.let { if (!it.exists()) it.mkdirs() }
        return try {
            val ok = if (entry.renameTo(dest)) true else {
                FileOps.copyRecursive(entry, dest).let { it is FileOps.Result.Ok }.also {
                    if (it) entry.deleteRecursively()
                }
            }
            if (ok) File(entry.parentFile, entry.name + META_SUFFIX).delete()
            ok
        } catch (_: Exception) {
            false
        }
    }

    /** Permanently purge a trashed [entry] (used after the undo window expires). */
    fun purge(entry: File) {
        entry.deleteRecursively()
        File(entry.parentFile, entry.name + META_SUFFIX).delete()
    }
}
