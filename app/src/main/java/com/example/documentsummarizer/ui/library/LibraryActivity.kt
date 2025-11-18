package com.example.documentsummarizer.ui.library

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.documentsummarizer.data.db.AppDatabase
import com.example.documentsummarizer.data.db.DocumentListItem
import com.example.documentsummarizer.data.repository.DocumentRepository
import com.example.documentsummarizer.databinding.ActivityLibraryBinding
import com.example.documentsummarizer.ui.capture.ScannerActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LibraryActivity : ComponentActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private lateinit var adapter: DocsAdapter
    private lateinit var repo: DocumentRepository
    private val searchQuery = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // initialize repository
        repo = DocumentRepository(AppDatabase.get(applicationContext).documentDao())

        // adapter with remove callback and item click
        adapter = DocsAdapter(onRemoveRequested = { docItem, position ->
            handleRemoveRequested(docItem, position)
        }, onItemClick = { docItem ->
            val id = docItem.id.toLongOrNull() ?: return@DocsAdapter
            val intent = Intent(this, DocumentHostActivity::class.java).apply {
                putExtra("DOC_ID", id)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        })

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        // Wire the search view to update the query flow
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery.value = query?.trim()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery.value = newText?.trim()
                return true
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                // Observe searchQuery and switch between full list and search results (debounced)
                searchQuery
                    .debounce(300)
                    .distinctUntilChanged()
                    .flatMapLatest { q ->
                        if (q.isNullOrBlank()) repo.list() else repo.search(q)
                    }
                    .map { rows -> rows.map { it.toDocumentItem() } }
                    .collect { docs ->
                        adapter.submit(docs)
                        binding.emptyView.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
                    }
            }
        }

        binding.buttonScan.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivity(intent)
        }
    }

    private fun handleRemoveRequested(docItem: DocumentItem, position: Int) {
        // optimistic remove from UI
        val removed = adapter.removeAt(position)

        val snack = Snackbar.make(binding.root, "Deleted \"${docItem.title}\"", Snackbar.LENGTH_LONG)
        var undone = false
        snack.setAction("UNDO") {
            undone = true
            // reinsert into the same position
            adapter.insertAt(position, removed)
        }

        snack.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                super.onDismissed(transientBottomBar, event)
                // If not undone and not an action dismissal, commit the delete
                if (event != DISMISS_EVENT_ACTION && !undone) {
                    val id = docItem.id.toLongOrNull() ?: return
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            repo.delete(id)
                        } catch (_: Exception) {
                            // If delete fails, we could re-query the list or reinsert the item on main
                            lifecycleScope.launch {
                                // Simple recovery: reinsert and show message
                                adapter.insertAt(position, removed)
                            }
                        }
                    }
                }
            }
        })

        snack.show()
    }
}

private fun DocumentListItem.toDocumentItem(): DocumentItem {
    // Use title if present; or derive from sourceText first line
    val safeTitle = if (doc.title.isNotBlank()) doc.title
    else doc.sourceText.lineSequence().firstOrNull()?.take(80) ?: "(Untitled)"
    return DocumentItem(
        id = doc.id.toString(),
        title = safeTitle,
        sourceText = doc.sourceText,
        summary = doc.summaryText,
        createdAt = doc.createdAt,
        coverThumb = coverThumb,
        coverUri = coverUri
    )
}