package org.tamanegi.atmosphere

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns

class LogContentProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        return true
    }

    override fun getType(uri: Uri): String? {
        return "text/csv"
    }
    override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String>? {
        return if(mimeTypeFilter == "*/*" || mimeTypeFilter == "text/*" || mimeTypeFilter == "text/csv")
            arrayOf("text/csv")
        else
            null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if(mode != "r") {
            throw SecurityException("only supports read access mode: uri=${uri}, mode=${mode}")
        }

        return ParcelFileDescriptor.open(getExportDataFile(context!!), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val columns = projection?.copyOf() ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val values = columns.map { column ->
            when(column) {
                OpenableColumns.DISPLAY_NAME -> getExportDataName(context!!)
                OpenableColumns.SIZE -> getExportDataFile(context!!).length()
                else -> null as Any?
            }
        }

        val cursor = MatrixCursor(columns)
        cursor.addRow(values)
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
