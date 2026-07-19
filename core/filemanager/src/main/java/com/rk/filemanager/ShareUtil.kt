package com.rk.filemanager

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * "Open with" / share support for the file manager.
 *
 * Files in Ubuntu Files live in app-internal storage, which other apps cannot read
 * directly. Android's FileProvider only serves files under a configured root, and the
 * official guidance is to use a *narrow, scoped* directory rather than `<root-path>` or
 * `.`. So any file we want to share is stream-copied into `filesDir/fm_share/` (a
 * directory declared in file_paths.xml as `<files-path name="fm_share" path="fm_share/"/>`),
 * then exposed through a `content://` URI granted read permission to the target app.
 *
 * Phone Storage files are already under the existing `<external-path path="."/>` root and
 * could be shared in place, but routing them through the same staging dir keeps a single,
 * consistent code path and a single grant/cleanup model.
 */
object ShareUtil {

    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val SHARE_DIR = "fm_share"

    private fun shareStagingDir(context: Context): File =
        File(context.filesDir, SHARE_DIR).also { if (!it.exists()) it.mkdirs() }

    /** Best-effort MIME type from a file name, falling back to octet-stream. */
    fun mimeTypeOf(file: File): String {
        val name = file.name
        val dot = name.lastIndexOf('.')
        if (dot >= 0) {
            val ext = name.substring(dot + 1).lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (mime != null) return mime
        }
        return "application/octet-stream"
    }

    /**
     * Copy [source] into the scoped staging dir and return a content URI for it, or null
     * if the copy fails. The staged copy is named after the source to keep the recipient's
     * view sensible; collisions are avoided by suffixing a counter.
     */
    private fun stageAndGetUri(context: Context, source: File): android.net.Uri? {
        if (!source.isFile) return null
        val staging = shareStagingDir(context)
        var target = File(staging, source.name)
        var n = 1
        while (target.exists()) {
            target = File(staging, "${source.nameWithoutExtension}__$n.${source.extension}")
            n++
        }
        return try {
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            val authority = context.packageName + AUTHORITY_SUFFIX
            FileProvider.getUriForFile(context, authority, target)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Build an intent that opens [file] in an external app ("Open with").
     * Returns null if the file cannot be staged/shared.
     */
    fun openWithIntent(context: Context, file: File): Intent? {
        val uri = stageAndGetUri(context, file) ?: return null
        val mime = mimeTypeOf(file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Build a chooser intent that shares [files] to other apps (ACTION_SEND / SEND_MULTIPLE).
     * Returns null if none of the files could be staged.
     */
    fun shareIntent(context: Context, files: List<File>): Intent? {
        val uris = files.mapNotNull { stageAndGetUri(context, it) }
        if (uris.isEmpty()) return null
        val mime = if (uris.size == 1) mimeTypeOf(files.first()) else "application/octet-stream"
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris.first()) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(
                Intent.EXTRA_STREAM, ArrayList(uris)) }
        }
        intent.type = mime
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, null)
    }

    /** Remove staged copies that are no longer needed (call when convenient). */
    fun clearStaging(context: Context) {
        shareStagingDir(context).listFiles()?.forEach { it.delete() }
    }
}
