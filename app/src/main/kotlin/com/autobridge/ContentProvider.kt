package com.autobridge

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.lang.UnsupportedOperationException

class ContentProvider : ContentProvider() {
    companion object {
        fun createChooserIntent(title: String, path: String) =
                Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                                .setType("*/*")
                                .putExtra(
                                        Intent.EXTRA_STREAM,
                                        Uri.Builder()
                                                .scheme(ContentResolver.SCHEME_CONTENT)
                                                .authority(this.javaClass.`package`.name)
                                                .path(path)
                                                .build()
                                ),
                        title
                )
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.createPipe()[0].apply {
                ParcelFileDescriptor.AutoCloseOutputStream(this).write(
                        when (uri.path) {
                            "/log" -> byteArrayOf(0) // this@ContentProvider.context.applicationContext.to<Application>().logEntries
                            "/configuration" -> File(this@ContentProvider.context.filesDir, uri.path).readBytes()
                            else -> throw IllegalArgumentException()
                        })
            }

    override fun query(uri: Uri?, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                addRow(arrayOf("", ""))
            }

    override fun insert(uri: Uri?, values: ContentValues?): Uri = throw UnsupportedOperationException()

    override fun update(uri: Uri?, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    override fun delete(uri: Uri?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    override fun getType(uri: Uri?): String = throw UnsupportedOperationException()
}