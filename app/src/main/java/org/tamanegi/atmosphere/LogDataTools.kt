@file:JvmName("LogDataTools")

package org.tamanegi.atmosphere

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

fun getExportData(context: Context): String {
    val records = arrayOfNulls<LogData.LogRecord>(LogData.TOTAL_COUNT)
    val logData = LogData.getInstance(context)
    val count = logData.readRecords(records);

    val dateFormat = DateFormat.getDateTimeInstance()
    val data = "DateTime,TimeMillis,Pressure[hPa]\n" +
            records.toList()
                    .subList(0, count)
                    .filterNotNull()
                    .filter { it.value >= 1 }
                    .map { "${dateFormat.format(Date(it.time))},${it.time},${it.value}" }
                    .joinToString("\n")
    return data
}

fun convertAndExportData(context: Context) {
    thread(name = "LogDataTools.convertAndExportData") {
        getExportDataFile(context).writeText(getExportData(context))

        Handler(context.mainLooper).post {
            val intent = Intent(Intent.ACTION_SEND)
                    .setType("text/csv")
                    .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://org.tamanegi.atmosphere.log/data"))

            val chooserIntent = Intent.createChooser(intent, context.getString(R.string.export_title))

            try {
                context.startActivity(chooserIntent)
            }
            catch (e: Exception) {
                // ignore
            }
        }
    }
}

fun getExportDataName(context: Context): String {
    val currentDate = Date()
    val dateSuffix = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(currentDate)
    val filename = "${context.getString(R.string.app_name)}-${dateSuffix}.csv"
    return filename
}

fun getExportDataFile(context: Context): File = context.filesDir.resolve("export.csv")