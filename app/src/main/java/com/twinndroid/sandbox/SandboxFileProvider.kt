package com.twinndroid.sandbox

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Hands APKs across the profile boundary without copying them first. Nothing is reachable unless it
 * was shared here a moment ago: a uri needs both a random token and a grant from the intent carrying it.
 */
class SandboxFileProvider : ContentProvider() {

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = shared[uri.pathSegments.firstOrNull()] ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val file = shared[uri.pathSegments.firstOrNull()] ?: throw FileNotFoundException(uri.toString())
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any>(file.name, file.length()))
        }
    }

    override fun getType(uri: Uri) = "application/octet-stream"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private val shared = ConcurrentHashMap<String, File>()

        fun share(context: Context, file: File): Uri {
            val token = UUID.randomUUID().toString()
            shared[token] = file
            return Uri.Builder().scheme("content").authority("${context.packageName}.sandboxfiles")
                .appendPath(token).appendPath(file.name).build()
        }

        fun forget(uris: List<Uri>) {
            uris.forEach { shared.remove(it.pathSegments.firstOrNull()) }
        }
    }
}
