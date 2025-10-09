package com.example.documentsummarizer.ui.library

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.documentsummarizer.R
import com.example.documentsummarizer.ScannerActivity
import com.example.documentsummarizer.databinding.FragmentDocumentLibraryBinding
import com.example.documentsummarizer.utils.Log

class DocumentLibraryFragment : Fragment(R.layout.fragment_document_library) {
    private var _binding: FragmentDocumentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DocsViewModel by viewModels()
    private val docsAdapter = DocsAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d({ "onViewCreated called" })
        _binding = FragmentDocumentLibraryBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)
        Log.d({ "Binding and layout manager set" })
        binding.recyclerViewDocs.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.recyclerViewDocs.adapter = docsAdapter

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.docs.collect { docs ->
                Log.d({ "docsAdapter.submit called with ${docs.size} docs" })
                docsAdapter.submit(docs)
            }
        }
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.loading.collect { isLoading ->
                Log.d({ "Loading state: $isLoading" })
                binding.progress.isVisible = isLoading
            }
        }

        // Receive OCR text (from ScannerActivity or CaptureFragment)
        parentFragmentManager.setFragmentResultListener("ocr_result", viewLifecycleOwner) { _, b ->
            val text = b.getString("text").orEmpty()
            Log.d({ "Received OCR text: $text" })
            if (text.isNotBlank()) {
                Log.d({ "Calling viewModel.addFromOcr" })
                viewModel.addFromOcr(text)
            }
        }

        binding.buttonScan.setOnClickListener {
            startActivity(Intent(requireContext(), ScannerActivity::class.java))
        }
    }

    override fun onDestroyView() {
        _binding = null   // important!
        super.onDestroyView()
    }
}