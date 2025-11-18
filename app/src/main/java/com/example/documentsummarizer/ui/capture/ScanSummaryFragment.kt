package com.example.documentsummarizer.ui.capture

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.example.documentsummarizer.R
import com.example.documentsummarizer.databinding.FragmentScanSummaryBinding
import com.example.documentsummarizer.data.db.AppDatabase
import com.example.documentsummarizer.data.repository.DocumentRepository
import com.example.documentsummarizer.ui.library.DocsViewModel
import com.example.documentsummarizer.ui.library.DocsViewModelFactory
import com.example.documentsummarizer.ui.library.LibraryActivity
import com.example.documentsummarizer.utils.Log
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ScanSummaryFragment : Fragment(R.layout.fragment_scan_summary) {
    private var _binding: FragmentScanSummaryBinding? = null
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
        _binding = FragmentScanSummaryBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        // If the fragment was started with intent extras, use them (host activities set fragment.arguments = intent.extras)
        arguments?.let { args ->
            // OCR preview
            val ocrText = args.getString("OCR_TEXT")
            val img = args.getString("IMG_URI")
            if (!ocrText.isNullOrBlank()) {
                imgUri = img
                val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                val bullets = prefs.getString("summary_bullets", "6")!!.toInt()
                viewModel.addFromOcr(ocrText, bullets)
            }
            // If a DOC_ID was provided (host opened a saved doc), observe it instead
            val docId = when {
                args.containsKey("docId") -> args.getLong("docId", -1L)
                args.containsKey("DOC_ID") -> args.getLong("DOC_ID", -1L)
                else -> -1L
            }
            if (docId != -1L) observedDocId = docId
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
                            binding.imageViewThumb.setImageResource(android.R.color.darker_gray)
                        } else {
                            binding.textViewTitle.text = preview.title
                            binding.textViewSummaryFull.text = preview.summary

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

        // Receive OCR text (from ScannerActivity or CaptureFragment)
        parentFragmentManager.setFragmentResultListener("ocr_result", viewLifecycleOwner) { _, b ->
            // Clear any observed saved-document so scanner preview is shown
            observedDocId = null

            val text = b.getString("text").orEmpty()
            imgUri = b.getString("imgUri")
            if (text.isNotBlank()) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                val bullets = prefs.getString("summary_bullets", "6")!!.toInt()
                viewModel.addFromOcr(text, bullets)
            }
        }

        // Receive DOC_ID from MainActivity (when opening an existing saved document)
        parentFragmentManager.setFragmentResultListener("doc_id_result", viewLifecycleOwner) { _, b ->
            val id = b.getLong("docId", -1L)
            if (id != -1L) {
                observedDocId = id
                // Observe saved document with images and bind to UI
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.observeWithImages(id).collectLatest { withImages ->
                        val doc = withImages?.doc
                        if (doc == null) {
                            // Document missing: clear UI
                            binding.textViewTitle.text = ""
                            binding.textViewSummaryFull.text = ""
                            binding.imageViewThumb.setImageResource(android.R.color.darker_gray)
                        } else {
                            binding.textViewTitle.text = doc.title
                            binding.textViewSummaryFull.text = doc.summaryText

                            // Prefer the thumbnail from images if present
                            val imgEntity = withImages.images.firstOrNull()
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
                    }
                }
            }
        }

        binding.buttonRetake.setOnClickListener {
            startActivity(Intent(requireContext(), ScannerActivity::class.java))
        }

        binding.buttonConfirm.setOnClickListener {
            viewModel.confirmSave(
                context = requireContext().applicationContext,
                imageUri = imgUri
            ) { savedId ->
                Log.d({ "Document saved id=$savedId" })
                startActivity(
                    Intent(requireContext(), LibraryActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                )
            }
        }
    }

    override fun onDestroyView() {
        _binding = null   // important!
        super.onDestroyView()
    }
}