package com.example.documentsummarizer.utils

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object OcrTextRecognizer {
    private val client by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun process(bitmap: Bitmap): String {
        val input = InputImage.fromBitmap(bitmap, 0) // rotate the bitmap before calling
        val result = client.process(input).await()
        val sb = StringBuilder()
        result.textBlocks.forEachIndexed { i, block ->
            block.lines.forEach { line -> sb.append(line.text).append('\n') }
            if (i < result.textBlocks.lastIndex) sb.append('\n')
        }
        return sb.toString().trim()
    }
}