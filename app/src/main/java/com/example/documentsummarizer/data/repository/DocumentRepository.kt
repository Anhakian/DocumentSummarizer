package com.example.documentsummarizer.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.example.documentsummarizer.data.db.*
import com.example.documentsummarizer.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.time.Instant

class DocumentRepository(private val dao: DocumentDao) {

    suspend fun getDocument(id: Long): DocumentEntity? = withContext(Dispatchers.IO) {
        dao.observeDoc(id).firstOrNull()
    }

    // New: observe a document along with its images
    fun observeWithImages(id: Long): Flow<DocumentWithImages?> = dao.observeWithImages(id)

    suspend fun saveSinglePage(
        context: Context,
        title: String,
        source: String,
        bitmap: Bitmap
    ): Long = withContext(Dispatchers.IO) {
        val now = Instant.now().toEpochMilli()
        val id = dao.insert(
            DocumentEntity(
                title = title,
                sourceText = source,
                summaryText = "", // empty for now
                summaryStatus = SummaryStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
        )
        val uri = ImageUtils.saveBitmapToAppStorage(context, bitmap)
        val thumb = ImageUtils.jpegBytes(ImageUtils.makeThumbnail(bitmap, 300), 80)
        dao.clearImages(id)
        dao.insertImages(listOf(DocumentImageEntity(documentId = id, position = 0, imageUri = uri.toString(), thumbnail = thumb)))
        dao.reindexFtsFor(id)
        id
    }

    suspend fun setSummaryReady(id: Long, summary: String) = withContext(Dispatchers.IO) {
        dao.updateSummary(id, summary, SummaryStatus.READY, Instant.now().toEpochMilli())
        dao.reindexFtsFor(id)
    }

    suspend fun setSummaryError(id: Long, message: String? = null) = withContext(Dispatchers.IO) {
        val summary = message?.let { "Summary failed: $it" } ?: ""
        dao.updateSummary(id, summary, SummaryStatus.ERROR, Instant.now().toEpochMilli())
        dao.reindexFtsFor(id)
    }

    fun list() = dao.observeListItems()
    fun search(q: String) = dao.searchListItems(buildFtsQuery(q))

    // Save a document row using an already-saved image file (imageUriStr).
    // This avoids re-saving the bitmap file when the scanner already persisted it.
    suspend fun saveSinglePageFromUri(
        context: android.content.Context,
        title: String,
        source: String,
        imageUriStr: String
    ): Long = withContext(Dispatchers.IO) {
        val now = Instant.now().toEpochMilli()
        val id = dao.insert(
            DocumentEntity(
                title = title,
                sourceText = source,
                summaryText = "",
                summaryStatus = SummaryStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
        )

        // Try to build a thumbnail from the image Uri. We attempt to open the URI
        // via the content resolver; if that fails, insert an empty thumbnail.
        val thumb = runCatching {
            val uri = android.net.Uri.parse(imageUriStr)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                ImageUtils.jpegBytes(ImageUtils.makeThumbnail(bmp, 300), 80)
            }
        }.getOrNull() ?: ByteArray(0)

        dao.clearImages(id)
        dao.insertImages(listOf(DocumentImageEntity(documentId = id, position = 0, imageUri = imageUriStr, thumbnail = thumb)))
        dao.reindexFtsFor(id)
        id
    }

    // Delete a document and its images. This runs on IO dispatcher.
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.clearImages(id)
        dao.deleteById(id)
        // Reindexing for the removed id keeps the FTS table consistent
        dao.reindexFtsFor(id)
    }

    private fun buildFtsQuery(input: String): String =
        input.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" AND ") { "$it*" }.ifEmpty { "*" }
}