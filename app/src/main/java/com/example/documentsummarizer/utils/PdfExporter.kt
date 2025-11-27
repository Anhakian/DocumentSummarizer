package com.example.documentsummarizer.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    data class PdfConfig(
        val title: String,
        val body: String,
        val textSizeTitle: Float = 18f,
        val textSizeBody: Float = 12f,
        val margin: Float = 20f
    )

    private const val PAGE_WIDTH = 595      // A4 width in points
    private const val PAGE_HEIGHT = 842     // A4 height in points

    @RequiresApi(Build.VERSION_CODES.Q)
    fun export(context: Context, config: PdfConfig): Uri? {
        val doc = PdfDocument()

        val paint = Paint().apply {
            isAntiAlias = true
        }

        var pageNumber = 1
        var y: Float

        // Start first page
        var page = startNewPage(doc, pageNumber)
        var canvas = page.canvas
        y = config.margin + 10f

        // Draw title
        paint.textSize = config.textSizeTitle
        canvas.drawText(config.title, config.margin, y, paint)
        y += config.textSizeTitle + 16f

        // Draw summary text with wrapping + pagination
        paint.textSize = config.textSizeBody

        val words = config.body.replace("\n", " \n ").split(" ")
        var currentLine = ""

        fun commitPage() {
            doc.finishPage(page)
            pageNumber++
            page = startNewPage(doc, pageNumber)
            canvas = page.canvas
            y = config.margin
        }

        for (word in words) {
            if (word == "\n") {
                // Force line break
                canvas.drawText(currentLine, config.margin, y, paint)
                y += config.textSizeBody + 6f
                currentLine = ""
                // Page overflow check
                if (y >= PAGE_HEIGHT - config.margin) commitPage()
                continue
            }

            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"

            if (paint.measureText(testLine) > (PAGE_WIDTH - config.margin * 2)) {
                // Draw existing line
                canvas.drawText(currentLine, config.margin, y, paint)
                y += config.textSizeBody + 6f
                currentLine = word

                if (y >= PAGE_HEIGHT - config.margin) commitPage()
            } else {
                currentLine = testLine
            }
        }

        // Draw last line
        if (currentLine.isNotEmpty()) {
            canvas.drawText(currentLine, config.margin, y, paint)
        }

        // Finish last page
        doc.finishPage(page)

        val fileName = "${config.title}_${timestamp()}.pdf"
        val uri = savePdf(context, doc, fileName)

        doc.close()
        return uri
    }

    private fun startNewPage(doc: PdfDocument, pageNumber: Int): PdfDocument.Page {
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        return doc.startPage(info)
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun savePdf(context: Context, pdf: PdfDocument, name: String): Uri? {
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocumentSummarizer")
        }

        return try {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)

            uri?.let {
                resolver.openOutputStream(it)?.use { os ->
                    pdf.writeTo(os)
                }
            }
            uri
        } catch (e: Exception) {
            // fallback to internal private files
            val file = File(context.filesDir, name)
            FileOutputStream(file).use { fos -> pdf.writeTo(fos) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }
}
