package com.expensetracker.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.expensetracker.app.data.db.TransactionEntity
import java.io.File
import java.time.format.DateTimeFormatter

/** Exports a set of transactions (already filtered by month/sector by the caller) to CSV and shares it. */
object CsvExporter {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun writeCsv(context: Context, transactions: List<TransactionEntity>, fileLabel: String): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "$fileLabel.csv")

        file.bufferedWriter().use { writer ->
            writer.write("Date,Type,Merchant,Category,Amount,Balance After,Account,Source,Needs Review")
            writer.newLine()
            for (t in transactions) {
                writer.write(
                    listOf(
                        t.transactionDateTime.format(dateFormatter),
                        t.type.name,
                        t.merchant.orEmpty(),
                        t.category.displayName,
                        t.amount?.toPlainString().orEmpty(),
                        t.availableBalance?.toPlainString().orEmpty(),
                        t.accountId.orEmpty(),
                        t.sourceLabel,
                        if (t.needsReview) "Yes" else "No",
                    ).joinToString(",") { csvEscape(it) },
                )
                writer.newLine()
            }
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
