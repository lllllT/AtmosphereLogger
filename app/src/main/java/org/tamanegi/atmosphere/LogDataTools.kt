@file:JvmName("LogDataTools")

package org.tamanegi.atmosphere

import android.content.Context
import android.content.Intent
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

fun convertAndExportData(context: Context) {
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

    val currentDate = Date()
    val dateSuffix = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(currentDate)
    val filename = "${context.getString(R.string.app_name)}-${dateSuffix}.csv"

    val intent = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_TEXT, data)
            .putExtra(Intent.EXTRA_SUBJECT, filename)

    val chooserIntent = Intent.createChooser(intent, context.getString(R.string.export_title))
    context.startActivity(chooserIntent)
}