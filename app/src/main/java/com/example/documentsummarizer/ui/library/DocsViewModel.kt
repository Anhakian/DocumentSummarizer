package com.example.documentsummarizer.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.documentsummarizer.data.repository.SummarizerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DocumentItem(
    val id: String,
    val title: String,
    val sourceText: String,
    val summary: String,
    val createdAt: Long = System.currentTimeMillis()
)

class DocsViewModel(
    private val summarizer: SummarizerRepository = SummarizerRepository()
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
                val summary = summarizer.summarize(ocrText, bullets)
                val title = summary.lineSequence().firstOrNull()?.take(80) ?: "Untitled"
                val item = DocumentItem(
                    id = System.currentTimeMillis().toString(),
                    title = title,
                    sourceText = ocrText,
                    summary = summary
                )
                _docs.value = listOf(item) + _docs.value
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }
}
