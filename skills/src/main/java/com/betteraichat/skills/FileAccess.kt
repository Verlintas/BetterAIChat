package com.betteraichat.skills

import android.content.Context
import android.os.Build
import android.os.Environment

internal fun isAllowedFilePath(context: Context, path: String): Boolean {
    val normalized = runCatching { java.io.File(path).canonicalPath }.getOrNull() ?: return false
    val roots = buildList {
        runCatching { add(context.cacheDir.canonicalPath) }
        runCatching { add(context.filesDir.canonicalPath) }
        runCatching { context.getExternalFilesDir(null)?.let { add(it.canonicalPath) } }
        if (Build.VERSION.SDK_INT >= 29) {
            add("/storage/emulated/0/Download")
            add("/storage/emulated/0/Documents")
            add("/storage/emulated/0/Pictures")
        } else {
            runCatching {
                add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalPath)
                add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).canonicalPath)
                add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).canonicalPath)
            }
        }
    }
    return roots.any { normalized == it || normalized.startsWith("$it/") }
}
