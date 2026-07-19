package com.rk.libcommons

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

fun Context.localDir(): File {
    return File(filesDir.parentFile, "local").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun Context.ubuntuDir(): File {
    return localDir().child("ubuntu").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun Context.ubuntuHomeDir(): File {
    val base = ubuntuDir()
    // The active PRoot session runs as the user from /etc/shellix_default_user
    // (see init.sh: `exec su - "$DEFAULT_USER"`), whose home is /home/<user>.
    // The File Manager was previously hardcoding .../ubuntu/root, which only ever
    // holds the root skeleton (.bashrc/.profile) and is NOT where the shell session
    // actually lives. Resolve the real home so the listing matches `ls -la ~`.
    val defaultUserFile = base.child("etc").child("shellix_default_user")
    val home = if (defaultUserFile.exists()) {
        val user = defaultUserFile.readText().trim().takeIf { it.isNotBlank() }
        if (user != null) base.child("home").child(user) else base.child("root")
    } else {
        base.child("root")
    }
    return home.also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun Context.localBinDir(): File {
    return localDir().child("bin").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun Context.localLibDir(): File {
    return localDir().child("lib").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun File.child(fileName: String): File {
    return File(this, fileName)
}

fun File.createFileIfNot(): File {
    if (exists().not()) {
        createNewFile()
    }
    return this
}

fun Context.getFileNameFromUri(uri: Uri): String? {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                return cursor.getString(nameIndex)
            }
        }
    } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
        return File(uri.path!!).name
    }
    return null
}
