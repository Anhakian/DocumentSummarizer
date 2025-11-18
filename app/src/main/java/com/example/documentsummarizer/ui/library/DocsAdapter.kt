package com.example.documentsummarizer.ui.library

import android.graphics.BitmapFactory
import androidx.core.net.toUri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.documentsummarizer.databinding.ItemDocumentBinding

class DocsAdapter(
    private val onRemoveRequested: ((DocumentItem, Int) -> Unit)? = null,
    private val onItemClick: ((DocumentItem) -> Unit)? = null
) : RecyclerView.Adapter<DocsAdapter.VH>() {
    private val items = mutableListOf<DocumentItem>()

    fun submit(list: List<DocumentItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    // Remove item from adapter and return it (for undo)
    fun removeAt(position: Int): DocumentItem {
        val removed = items.removeAt(position)
        notifyItemRemoved(position)
        return removed
    }

    fun insertAt(position: Int, item: DocumentItem) {
        items.add(position, item)
        notifyItemInserted(position)
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
            textViewSummary.text = it.summary

            // Bind thumbnail: prefer coverThumb bytes, otherwise use coverUri, otherwise placeholder
            var set = false
            it.coverThumb?.let { bytes ->
                if (bytes.isNotEmpty()) {
                    try {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        imageViewThumb.setImageBitmap(bmp)
                        set = true
                    } catch (_: Exception) { /* ignore decode errors */ }
                }
            }

            if (!set) {
                val uriStr = it.coverUri
                if (!uriStr.isNullOrBlank()) {
                    try {
                        imageViewThumb.setImageURI(uriStr.toUri())
                        set = true
                    } catch (_: Exception) { /* ignore */ }
                }
            }

            if (!set) {
                imageViewThumb.setImageResource(android.R.color.darker_gray)
            }

            // Wire remove button to the callback with safe position check
            buttonRemove.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onRemoveRequested?.invoke(items[pos], pos)
                }
            }

            // Wire item click to callback
            root.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick?.invoke(items[pos])
                }
            }
        }
    }

    override fun getItemCount() = items.size
}