package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.Transaction
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    fun exportToCsv(context: Context, transactions: List<Transaction>): Uri? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        
        val csvHeader = "ID,Tanggal,Tipe,Kategori,Jumlah,Catatan,Status Sinkronisasi\n"
        val csvBody = StringBuilder()
        transactions.forEach { tx ->
            val dateStr = dateFormat.format(Date(tx.date))
            val amountStr = tx.amount.toLong().toString()
            val noteCleaned = tx.note.replace(",", " ").replace("\n", " ")
            val syncStatus = if (tx.isSynced) "Sudah Sinkron" else "Belum Sinkron"
            csvBody.append("${tx.id},$dateStr,${tx.type},${tx.category},$amountStr,$noteCleaned,$syncStatus\n")
        }
        
        val csvContent = csvHeader + csvBody.toString()
        
        return try {
            val fileName = "Cashflow_Report_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { out ->
                out.write(csvContent.toByteArray(Charsets.UTF_8))
            }
            FileProvider.getUriForFile(
                context,
                "com.aistudio.myfeenotes.vjqpax.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportToPdf(context: Context, transactions: List<Transaction>, titleMonth: String): Uri? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = pdfDocument.startPage(pageInfo)
        
        val canvas = page.canvas
        val paint = Paint()
        
        // Header
        paint.color = Color.DKGRAY
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("MyFeeNotes Laporan Cashflow", 40f, 50f, paint)
        
        paint.textSize = 12f
        paint.isFakeBoldText = false
        paint.color = Color.GRAY
        canvas.drawText("Periode: $titleMonth", 40f, 75f, paint)
        canvas.drawText("Dibuat pada: ${SimpleDateFormat("dd MMMM yyyy HH:mm", Locale.getDefault()).format(Date())}", 40f, 95f, paint)
        
        // Draw division line
        paint.color = Color.LTGRAY
        canvas.drawLine(40f, 110f, 555f, 110f, paint)
        
        // Draw stats
        val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val balance = totalIncome - totalExpense
        
        paint.color = Color.BLACK
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("RINGKASAN REKAPITULASI", 40f, 135f, paint)
        
        paint.textSize = 11f
        paint.isFakeBoldText = false
        val prefix = "Rp "
        
        // Helper formatting currency
        fun formatRupiah(amount: Double): String {
            return prefix + String.format(Locale("id", "ID"), "%,.0f", amount)
        }
        
        paint.color = Color.rgb(46, 125, 50) // Green
        canvas.drawText("Total Pemasukan: ${formatRupiah(totalIncome)}", 40f, 160f, paint)
        
        paint.color = Color.rgb(198, 40, 40) // Red
        canvas.drawText("Total Pengeluaran: ${formatRupiah(totalExpense)}", 40f, 180f, paint)
        
        paint.color = if (balance >= 0) Color.rgb(46, 125, 50) else Color.rgb(198, 40, 40)
        paint.isFakeBoldText = true
        canvas.drawText("Saldo Bersih: ${formatRupiah(balance)}", 40f, 200f, paint)
        
        // Table Header
        paint.color = Color.DKGRAY
        canvas.drawLine(40f, 220f, 555f, 220f, paint)
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("Tanggal", 45f, 235f, paint)
        canvas.drawText("Tipe", 140f, 235f, paint)
        canvas.drawText("Kategori", 210f, 235f, paint)
        canvas.drawText("Keterangan", 320f, 235f, paint)
        canvas.drawText("Jumlah", 450f, 235f, paint)
        canvas.drawLine(40f, 245f, 555f, 245f, paint)
        
        // Table Rows
        paint.isFakeBoldText = false
        paint.color = Color.BLACK
        var yPos = 265f
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        
        transactions.take(20).forEach { tx -> // Limit to 20 to fit A4 page nicely
            val dateStr = dateFormat.format(Date(tx.date))
            val amountStr = formatRupiah(tx.amount)
            
            canvas.drawText(dateStr, 45f, yPos, paint)
            
            // Draw type with color indicator
            val oldColor = paint.color
            if (tx.type == "INCOME") {
                paint.color = Color.rgb(46, 125, 50)
                canvas.drawText("PEMASUKAN", 140f, yPos, paint)
            } else {
                paint.color = Color.rgb(198, 40, 40)
                canvas.drawText("PENGELUARAN", 140f, yPos, paint)
            }
            paint.color = oldColor
            
            canvas.drawText(tx.category, 210f, yPos, paint)
            
            val noteText = if (tx.note.length > 20) tx.note.substring(0, 17) + "..." else tx.note
            canvas.drawText(noteText, 320f, yPos, paint)
            
            canvas.drawText(amountStr, 450f, yPos, paint)
            
            yPos += 22f
        }
        
        if (transactions.size > 20) {
            paint.color = Color.GRAY
            paint.textSize = 9f
            canvas.drawText("*Laporan PDF dibatasi hanya menampilkan 20 transaksi terbaru.", 40f, 800f, paint)
        }
        
        pdfDocument.finishPage(page)
        
        return try {
            val fileName = "Cashflow_Report_${System.currentTimeMillis()}.pdf"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            
            FileProvider.getUriForFile(
                context,
                "com.aistudio.myfeenotes.vjqpax.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }
}
