package com.rk.filemanager

import java.io.File

/**
 * Two-way folder sync engine (Ubuntu Files <-> Phone Storage).
 *
 * Design notes / trade-offs (see research report):
 * - No FileObserver: FUSE-mounted /sdcard does not reliably deliver inotify events,
 *   so sync is poll-based (driven by WorkManager on a periodic schedule).
 * - Conflict policy: LAST-WRITE-WINS. If the same relative path changed on BOTH
 *   sides since the last successful sync and the contents differ, we keep BOTH
 *   copies by writing a `<name>.conflict-<timestamp>` file on each side (no silent
 *   data loss). This is the safe default for v1.
 * - Change detection compares (lastModified, size) against the snapshot taken at
 *   the previous sync. A file that is identical on both sides and unchanged since
 *   last sync is left alone.
 *
 * The engine is deliberately free of Android dependencies so the diff/merge logic
 * can be unit-tested in plain JVM.
 */
object SyncEngine {

    data class FileSig(val modified: Long, val size: Long)

    /** Snapshot of both sides captured at the last successful sync. */
    data class SyncState(
        val ubuntu: Map<String, FileSig> = emptyMap(),
        val phone: Map<String, FileSig> = emptyMap()
    )

    /** Result of [plan]: what should happen to bring both sides in line. */
    sealed interface Action {
        val rel: String
        data class CopyUbuntuToPhone(override val rel: String) : Action
        data class CopyPhoneToUbuntu(override val rel: String) : Action
        data class DeleteUbuntu(override val rel: String) : Action
        data class DeletePhone(override val rel: String) : Action
        data class Conflict(override val rel: String) : Action
    }

    private fun walk(dir: File): Map<String, FileSig> {
        if (!dir.isDirectory) return emptyMap()
        val out = mutableMapOf<String, FileSig>()
        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                val rel = f.relativeTo(dir).path.replace("\\", "/")
                out[rel] = FileSig(f.lastModified(), f.length())
            }
        }
        return out
    }

    /** Compute the set of actions needed to sync [ubuntuDir] and [phoneDir]. */
    fun plan(ubuntuDir: File, phoneDir: File, last: SyncState): List<Action> {
        val uNow = walk(ubuntuDir)
        val pNow = walk(phoneDir)
        val actions = mutableListOf<Action>()
        val allRels = (uNow.keys + pNow.keys).toSet()
        for (rel in allRels) {
            val u = uNow[rel]
            val p = pNow[rel]
            val lu = last.ubuntu[rel]
            val lp = last.phone[rel]
            val changedU = u != lu
            val changedP = p != lp

            when {
                u != null && p != null -> {
                    if (changedU && changedP && u != p) {
                        // Both changed and differ -> conflict, keep both.
                        actions.add(Action.Conflict(rel))
                    } else if (changedU && !changedP) {
                        actions.add(Action.CopyUbuntuToPhone(rel))
                    } else if (changedP && !changedU) {
                        actions.add(Action.CopyPhoneToUbuntu(rel))
                    }
                    // neither changed -> nothing
                }
                u != null && p == null -> {
                    // Exists only on Ubuntu.
                    if (lu != null && lp == null) {
                        // Was deleted on Phone since last sync -> mirror deletion.
                        actions.add(Action.DeleteUbuntu(rel))
                    } else {
                        // New on Ubuntu -> push to Phone.
                        actions.add(Action.CopyUbuntuToPhone(rel))
                    }
                }
                u == null && p != null -> {
                    if (lp != null && lu == null) {
                        actions.add(Action.DeletePhone(rel))
                    } else {
                        actions.add(Action.CopyPhoneToUbuntu(rel))
                    }
                }
            }
        }
        return actions
    }

    /**
     * Apply [actions] between [ubuntuDir] and [phoneDir]. [onConflict] receives the
     * relative path so the caller can choose a timestamped backup name. Returns the
     * new [SyncState] reflecting the post-sync trees (for persistence).
     */
    fun apply(
        actions: List<Action>,
        ubuntuDir: File,
        phoneDir: File,
        onConflict: (rel: String) -> String = { "conflict-${System.currentTimeMillis()}" }
    ): SyncState {
        for (a in actions) {
            val uFile = File(ubuntuDir, a.rel)
            val pFile = File(phoneDir, a.rel)
            when (a) {
                is Action.CopyUbuntuToPhone -> { pFile.parentFile?.mkdirs(); copyOrBackup(uFile, pFile, a.rel, onConflict) }
                is Action.CopyPhoneToUbuntu -> { uFile.parentFile?.mkdirs(); copyOrBackup(pFile, uFile, a.rel, onConflict) }
                is Action.DeleteUbuntu -> uFile.deleteRecursively()
                is Action.DeletePhone -> pFile.deleteRecursively()
                is Action.Conflict -> {
                    val stamp = onConflict(a.rel)
                    val base = a.rel.substringBeforeLast('.', a.rel)
                    val ext = a.rel.substringAfterLast('.', "")
                    val uBackup = File(ubuntuDir, "$base.conflict-$stamp${if (ext.isNotEmpty()) ".$ext" else ""}")
                    val pBackup = File(phoneDir, "$base.conflict-$stamp${if (ext.isNotEmpty()) ".$ext" else ""}")
                    FileOps.copyRecursive(uFile, uBackup, {}, { false }, FileOps.totalBytes(uFile), java.util.concurrent.atomic.AtomicLong(0L))
                    FileOps.copyRecursive(pFile, pBackup, {}, { false }, FileOps.totalBytes(pFile), java.util.concurrent.atomic.AtomicLong(0L))
                }
            }
        }
        return SyncState(walk(ubuntuDir), walk(phoneDir))
    }

    /** Copy [from] to [to]; never overwrite a divergent existing [to] (caller resolves). */
    private fun copyOrBackup(from: File, to: File, rel: String, onConflict: (String) -> String) {
        if (to.exists()) {
            // Same relative path exists on both sides with different content -> keep both.
            val stamp = onConflict(rel)
            val base = rel.substringBeforeLast('.', rel)
            val ext = rel.substringAfterLast('.', "")
            val backup = File(to.parentFile, "$base.conflict-$stamp${if (ext.isNotEmpty()) ".$ext" else ""}")
            FileOps.copyRecursive(from, backup, {}, { false }, FileOps.totalBytes(from), java.util.concurrent.atomic.AtomicLong(0L))
        } else {
            FileOps.copyRecursive(from, to, {}, { false }, FileOps.totalBytes(from), java.util.concurrent.atomic.AtomicLong(0L))
        }
    }
}
