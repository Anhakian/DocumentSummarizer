package com.example.documentsummarizer.ui.library

import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.example.documentsummarizer.R
import com.example.documentsummarizer.data.db.AppDatabase
import com.example.documentsummarizer.data.repository.DocumentRepository
import com.example.documentsummarizer.databinding.FragmentDocumentSummaryBinding
import com.example.documentsummarizer.utils.Log
import com.example.documentsummarizer.utils.PdfExporter
import com.example.documentsummarizer.utils.TextExporter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DocumentSummaryFragment : Fragment(R.layout.fragment_document_summary) {
    private var _binding: FragmentDocumentSummaryBinding? = null
    private val binding get() = _binding!!
    private var imgUri: String? = null

    // Repository for observing saved documents when opened from MainActivity/Library
    private val repo by lazy {
        val ctx = requireContext().applicationContext
        DocumentRepository(AppDatabase.get(ctx).documentDao())
    }

    // When non-null we are actively observing a saved document and should ignore preview updates
    private var observedDocId: Long? = null

    // Create DocsViewModel with factory so it receives a DocumentRepository instance
    private val viewModel: DocsViewModel by viewModels {
        val ctx = requireContext().applicationContext
        val db = AppDatabase.get(ctx)
        val repo = DocumentRepository(db.documentDao())
        DocsViewModelFactory(docsRepo = repo)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d({ "onViewCreated called" })
        _binding = FragmentDocumentSummaryBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        // Wire the Export button in the bottom edit bar
        binding.buttonExportPdf.setOnClickListener {
            val cleanTitle = binding.textViewTitle.text
                .toString()
                .replace("\n", " ")
                .trim()
            val config = PdfExporter.PdfConfig(
                title = cleanTitle,
                body = binding.textViewSummaryFull.text.toString()
            )

            val uri = PdfExporter.export(requireContext(), config)

            if (uri != null) {
                Toast.makeText(requireContext(), "PDF saved!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Failed to save PDF", Toast.LENGTH_LONG).show()
            }
        }

        binding.buttonExportText.setOnClickListener {
            val config = TextExporter.TextConfig(
                title = binding.textViewTitle.text.toString(),
                body = binding.textViewSummaryFull.text.toString()
            )

            val uri = TextExporter.export(requireContext(), config)

            if (uri != null) {
                Toast.makeText(requireContext(), "TXT saved!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Failed to save TXT", Toast.LENGTH_LONG).show()
            }
        }

        // TODO: Implement Edit functionality
        binding.buttonEdit.setOnClickListener {
            Toast.makeText(requireContext(), "Edit not implemented", Toast.LENGTH_SHORT).show()
        }

        // If the fragment was started with intent extras (via DocumentHostActivity), use them
        arguments?.let { args ->
            val docId = when {
                args.containsKey("docId") -> args.getLong("docId", -1L)
                args.containsKey("DOC_ID") -> args.getLong("DOC_ID", -1L)
                else -> -1L
            }
            if (docId != -1L) observedDocId = docId

            // Also accept OCR preview forwarded via args (OCR_TEXT / IMG_URI)
            val ocrText = args.getString("OCR_TEXT")
            val img = args.getString("IMG_URI")
            if (!ocrText.isNullOrBlank()) {
                imgUri = img
                val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                val bullets = prefs.getString("summary_bullets", "6")!!.toInt()
                viewModel.addFromOcr(ocrText, bullets)
            }
        }

        // Observe preview documents (the ViewModel keeps a list where the latest preview is first)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.docs.collect { docs ->
                        // If we're showing a saved document (opened via DOC_ID), ignore preview updates
                        if (observedDocId != null) return@collect

                        val preview = docs.firstOrNull()
                        if (preview == null) {
                            // Clear UI when there's no preview
                            binding.textViewTitle.text = ""
                            binding.textViewSummaryFull.text = ""
                            binding.textViewSourceText.text = ""
                            binding.imageViewThumb.setImageResource(android.R.color.darker_gray)
                        } else {
                            binding.textViewTitle.text = preview.title
                            binding.textViewSummaryFull.text = preview.summary
                            binding.textViewSourceText.text = preview.sourceText

                            // Prefer coverThumb bytes if present
                            val thumbBytes = preview.coverThumb
                            if (thumbBytes != null && thumbBytes.isNotEmpty()) {
                                val bmp = BitmapFactory.decodeByteArray(thumbBytes, 0, thumbBytes.size)
                                binding.imageViewThumb.setImageBitmap(bmp)
                            } else {
                                // Otherwise prefer the imageUri captured from ScannerActivity, then preview.coverUri
                                val useUri = imgUri ?: preview.coverUri
                                if (!useUri.isNullOrBlank()) {
                                    try {
                                        binding.imageViewThumb.setImageURI(Uri.parse(useUri))
                                    } catch (_: Exception) {
                                        binding.imageViewThumb.setImageResource(android.R.color.darker_gray)
                                    }
                                } else {
                                    binding.imageViewThumb.setImageResource(android.R.color.darker_gray)
                                }
                            }
                        }
                    }
                }

                launch {
                    viewModel.loading.collect { isLoading ->
                        binding.progress.isVisible = isLoading
                    }
                }
            }
        }

        // If fragment result with DOC_ID arrives, observe it
        parentFragmentManager.setFragmentResultListener("doc_id_result", viewLifecycleOwner) { _, b ->
            val id = b.getLong("docId", -1L)
            if (id != -1L) {
                observedDocId = id
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.observeWithImages(id).collectLatest { withImages ->
                        bindSavedDocument(withImages?.doc, withImages?.images?.firstOrNull())
                    }
                }
            }
        }

        // If we have an observedDocId (from initial args), start observing it immediately
        observedDocId?.let { id ->
            viewLifecycleOwner.lifecycleScope.launch {
                repo.observeWithImages(id).collectLatest { withImages ->
                    bindSavedDocument(withImages?.doc, withImages?.images?.firstOrNull())
                }
            }
        }
    }

    private fun bindSavedDocument(doc: com.example.documentsummarizer.data.db.DocumentEntity?, imgEntity: com.example.documentsummarizer.data.db.DocumentImageEntity?) {
        if (doc == null) {
            binding.textViewTitle.text = ""
            binding.textViewSummaryFull.text = ""
            binding.textViewSourceText.text = ""
            binding.imageViewThumb.setImageResource(android.R.color.darker_gray)
            return
        }

        binding.textViewTitle.text = doc.title
        binding.textViewSummaryFull.text = doc.summaryText
        binding.textViewSourceText.text = doc.sourceText

        val thumb = imgEntity?.thumbnail
        if (thumb != null && thumb.isNotEmpty()) {
            val bmp = BitmapFactory.decodeByteArray(thumb, 0, thumb.size)
            binding.imageViewThumb.setImageBitmap(bmp)
        } else if (!imgEntity?.imageUri.isNullOrBlank()) {
            try {
                binding.imageViewThumb.setImageURI(Uri.parse(imgEntity?.imageUri))
            } catch (_: Exception) {
                binding.imageViewThumb.setImageResource(android.R.color.darker_gray)
            }
        } else {
            binding.imageViewThumb.setImageResource(android.R.color.darker_gray)
        }
    }

    override fun onDestroyView() {
        _binding = null   // important!
        super.onDestroyView()
    }
}