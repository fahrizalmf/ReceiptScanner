package com.emoneyreader.app.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.RectF
import com.emoneyreader.app.data.TransactionHistory
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Membuat laporan PDF berisi ringkasan transaksi tol LENGKAP dengan foto struk
 * ukuran penuh (satu struk per baris/kartu) — cocok untuk arsip atau lampiran
 * bukti pengeluaran kerja. Pakai android.graphics.pdf.PdfDocument bawaan
 * Android, tanpa library tambahan.
 */
object PdfReportHelper {

    private const val PAGE_WIDTH = 595  // A4 @ 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 32f

    private val jamFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID"))
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    fun exportToPdf(
        context: Context,
        transactions: List<TransactionHistory>,
        periodLabel: String
    ): File {
        val exportDir = File(context.getExternalFilesDir(null), "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val fileName = "laporan_tol_${periodLabel}_${System.currentTimeMillis()}.pdf"
        val outFile = File(exportDir, fileName)

        val document = PdfDocument()
        val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true; color = Color.BLACK }
        val headerPaint = Paint().apply { textSize = 11f; isFakeBoldText = true; color = Color.BLACK }
        val textPaint = Paint().apply { textSize = 11f; color = Color.BLACK }
        val smallPaint = Paint().apply { textSize = 9f; color = Color.DKGRAY }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        // Header laporan
        canvas.drawText("Laporan Transaksi Tol", MARGIN, y + 16f, titlePaint)
        y += 26f
        canvas.drawText("Periode: $periodLabel", MARGIN, y, textPaint)
        y += 14f
        val total = transactions.sumOf { it.nominal }
        canvas.drawText("Jumlah struk: ${transactions.size}   |   Total: ${currencyFormat.format(total)}", MARGIN, y, headerPaint)
        y += 20f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 16f

        for ((index, t) in transactions.withIndex()) {
            // Estimasi tinggi kartu (foto + teks); kalau tidak cukup ruang, buka halaman baru
            val estimatedCardHeight = 180f
            if (y + estimatedCardHeight > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
            }

            canvas.drawText("${index + 1}. ${t.tollGateName}", MARGIN, y + 12f, headerPaint)
            canvas.drawText(jamFormat.format(Date(t.timestamp)), MARGIN, y + 26f, smallPaint)
            canvas.drawText(currencyFormat.format(t.nominal), PAGE_WIDTH - MARGIN - 100f, y + 12f, headerPaint)
            y += 34f

            val photoPath = t.photoPath
            if (!photoPath.isNullOrBlank() && File(photoPath).exists()) {
                val bitmap = BitmapFactory.decodeFile(photoPath)
                if (bitmap != null) {
                    val maxWidth = 160f
                    val maxHeight = 140f
                    val scale = minOf(maxWidth / bitmap.width, maxHeight / bitmap.height)
                    val drawW = bitmap.width * scale
                    val drawH = bitmap.height * scale
                    val destRect = RectF(MARGIN, y, MARGIN + drawW, y + drawH)
                    canvas.drawBitmap(bitmap, null, destRect, null)
                    y += drawH + 10f
                } else {
                    canvas.drawText("(foto struk tidak dapat dimuat)", MARGIN, y, smallPaint)
                    y += 16f
                }
            } else {
                canvas.drawText("(tidak ada foto struk — input manual)", MARGIN, y, smallPaint)
                y += 16f
            }

            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 16f
        }

        document.finishPage(page)

        FileOutputStream(outFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        return outFile
    }
}
