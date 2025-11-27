package com.example.documentsummarizer.ui.library

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.documentsummarizer.data.repository.DocumentRepository
import com.example.documentsummarizer.data.repository.SummarizerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DocumentItem(
    val id: String,
    val title: String,
    val sourceText: String,
    val summary: String,
    val createdAt: Long = System.currentTimeMillis(),
    val coverThumb: ByteArray? = null,
    val coverUri: String? = null
)

class DocsViewModel(
    private val summarizer: SummarizerRepository = SummarizerRepository(),
    private val docsRepo: DocumentRepository
) : ViewModel() {

    private val _docs = MutableStateFlow<List<DocumentItem>>(emptyList())
    val docs: StateFlow<List<DocumentItem>> = _docs

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun addFromOcr(ocrText: String, bullets: Int) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val rawSummary = summarizer.summarize(ocrText, bullets)
                val (title, cleanSummary) = extractTitleAndSummary(rawSummary)
                val item = DocumentItem(
                    id = System.currentTimeMillis().toString(),
                    title = title,
                    sourceText = ocrText,
                    summary = cleanSummary
                )
                _docs.value = listOf(item) + _docs.value
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun startSummaryFor(docId: Long, bullets: Int = 5) {
        viewModelScope.launch {
            try {
                // 1) Load from Room
                val doc = withContext(Dispatchers.IO) { docsRepo.getDocument(docId) }
                if (doc == null) {
                    _error.value = "Document not found (id=$docId)"
                    return@launch
                }

                // 2) Summarize using your existing repo
                val raw = withContext(Dispatchers.IO) {
                    summarizer.summarize(doc.sourceText, bullets)
                }

                val (_, summary) = extractTitleAndSummary(raw)

                // 3) Write back to Room as READY
                withContext(Dispatchers.IO) { docsRepo.setSummaryReady(docId, summary) }

            } catch (e: Exception) {
                // Mark ERROR in Room and surface message for UI (e.g., badge, toast)
                withContext(Dispatchers.IO) { docsRepo.setSummaryError(docId, e.message) }
                _error.value = e.message
            }
        }
    }

    fun retrySummary(docId: Long, bullets: Int = 5) = startSummaryFor(docId, bullets)

    fun confirmSave(context: Context, imageUri: String?, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val preview = _docs.value.firstOrNull()
            if (preview == null) {
                _error.value = "Nothing to save."
                return@launch
            }

            try {
                _loading.value = true

                // 1) Load bitmap from the passed IMG_URI (if any)
                val bmp = withContext(Dispatchers.IO) {
                    imageUri?.let { uriStr ->
                        val uri = Uri.parse(uriStr)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    }
                }

                // 2) Save base document (image + OCR text). If no image, you can extend repo to allow text-only.
                val savedId: Long = withContext(Dispatchers.IO) {
                    if (bmp == null) {
                        // If your repo requires a bitmap, you can early-return or handle a text-only save variant.
                        throw IllegalStateException("Image missing for save; no IMG_URI provided.")
                    }
                    docsRepo.saveSinglePage(
                        context = context,
                        title = preview.title.ifBlank { "Untitled" },
                        source = preview.sourceText,
                        bitmap = bmp
                    )
                }

                // 3) Store the AI summary as READY
                withContext(Dispatchers.IO) {
                    docsRepo.setSummaryReady(savedId, preview.summary)
                }

                onSaved(savedId)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    private fun extractTitleAndSummary(raw: String): Pair<String, String> {
        val lines = raw.lineSequence()
            .map { it.trim().replace(Regex("^#{1,6}\\s*"), "") } // remove ###, ##, # headers
            .filter { it.isNotBlank() }
            .toList()

        if (lines.isEmpty()) {
            return "Untitled" to ""
        }

        val title = lines.first().take(80)
        val summaryBody = lines.drop(1).joinToString("\n")

        return title to summaryBody
    }
}
