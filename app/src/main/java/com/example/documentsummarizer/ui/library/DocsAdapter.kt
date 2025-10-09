package com.example.documentsummarizer.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.documentsummarizer.databinding.ItemDocumentBinding

class DocsAdapter : RecyclerView.Adapter<DocsAdapter.VH>() {
    private val items = mutableListOf<DocumentItem>()

    fun submit(list: List<DocumentItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged() // swap to DiffUtil later
    }

    class VH(val binding: ItemDocumentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        with(holder.binding) {
            textViewTitle.text = it.title
            textViewSourceSnippet.text = "Source (OCR): ${it.sourceText.take(300)}"
            textViewSummary.text = it.summary
        }
    }

    override fun getItemCount() = items.size
}