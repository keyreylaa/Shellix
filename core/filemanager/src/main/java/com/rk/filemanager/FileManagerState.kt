package com.rk.filemanager

import java.io.File

/** Which storage realm the browser is currently showing. */
enum class StorageRealm(val label: String) {
    UBUNTU("Ubuntu Files"),
    PHONE("Phone Storage")
}

/**
 * A single directory entry, precomputed for cheap list rendering.
 */
data class FileEntry(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long
) {
    companion object {
        fun of(file: File) = FileEntry(
            file = file,
            name = file.name,
            isDirectory = file.isDirectory,
            sizeBytes = if (file.isDirectory) 0L else file.length(),
            lastModified = file.lastModified()
        )
    }
}

/**
 * Lists [dir] as sorted [FileEntry]s: directories first, then files, both
 * case-insensitive by name. Returns empty on an unreadable directory rather
 * than throwing, so the UI can show an "empty/denied" state.
 */
fun listDir(dir: File): List<FileEntry> {
    val children = runCatching { dir.listFiles() }.getOrNull() ?: return emptyList()
    return children
        .map { FileEntry.of(it) }
        .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
}

/** Human-readable size (e.g. "12.3 KB"). */
fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var i = 0
    while (value >= 1024 && i < units.lastIndex) { value /= 1024; i++ }
    return String.format("%.1f %s", value, units[i])
}

/**
 * Breadcrumb segments from [root] down to [current], inclusive. Each pair is
 * (display label, the File to navigate to). The first segment shows the realm root.
 */
fun breadcrumbs(root: File, current: File, rootLabel: String): List<Pair<String, File>> {
    val crumbs = mutableListOf<Pair<String, File>>()
    var cursor: File? = current
    while (cursor != null && cursor.absolutePath.startsWith(root.absolutePath)) {
        val label = if (cursor.absolutePath == root.absolutePath) rootLabel else cursor.name
        crumbs.add(0, label to cursor)
        if (cursor.absolutePath == root.absolutePath) break
        cursor = cursor.parentFile
    }
    return crumbs
}
