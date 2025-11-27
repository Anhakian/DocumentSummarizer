package com.example.documentsummarizer.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object TextExporter {

    data class TextConfig(
        val title: String,
        val body: String
    )

    fun export(context: Context, config: TextConfig): Uri? {
        val safeTitle = sanitize(config.title)
        val fileName = "$safeTitle.txt"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloads(context, fileName, config.body)
        } else {
            saveLegacy(context, fileName, config.body)
        }
    }

    // --- Title sanitization ---
    private fun sanitize(name: String): String =
        name.replace("\n", " ")
            .replace("[^A-Za-z0-9 _-]".toRegex(), "")
            .trim()
            .ifBlank { "Document" }

    // --- Android 10+ Scoped Storage ---
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloads(
        context: Context,
        fileName: String,
        body: String
    ): Uri? {
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/DocumentSummarizer"
            )
        }

        return try {
            val uri = resolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values
            )

            uri?.let {
                resolver.openOutputStream(it)?.use { os ->
                    os.write(body.toByteArray())
                }
            }

            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- Pre-Android 10 fallback (files dir + FileProvider) ---
    private fun saveLegacy(
        context: Context,
        fileName: String,
        body: String
    ): Uri? {
        return try {
            val file = File(context.filesDir, fileName)
            FileOutputStream(file).use { it.write(body.toByteArray()) }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
