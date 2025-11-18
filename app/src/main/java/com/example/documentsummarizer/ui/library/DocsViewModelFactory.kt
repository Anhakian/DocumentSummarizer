package com.example.documentsummarizer.ui.library

import androidx.lifecycle.ViewModel
import com.example.documentsummarizer.data.repository.DocumentRepository
import com.example.documentsummarizer.data.repository.SummarizerRepository

class DocsViewModelFactory(
    private val docsRepo: DocumentRepository,
    private val summarizer: SummarizerRepository = SummarizerRepository()
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DocsViewModel(summarizer, docsRepo) as T
    }
}