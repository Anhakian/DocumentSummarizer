package com.example.documentsummarizer

import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.appcompat.app.AppCompatActivity
import com.example.documentsummarizer.databinding.ActivityMainBinding
import com.example.documentsummarizer.ui.library.DocumentLibraryFragment
import com.example.documentsummarizer.utils.Log

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (supportFragmentManager.findFragmentById(R.id.navigation) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.navigation, DocumentLibraryFragment())
                .commitNow() // commitNow so we can deliver results immediately
            Log.d { "MainActivity: DocumentLibraryFragment attached" }
        }

        deliverOcrFromIntent(intent)
    }

    // If ScannerActivity used FLAG_ACTIVITY_CLEAR_TOP, onCreate is NOT called again.
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)  // so later intent.getStringExtra() works too
            deliverOcrFromIntent(intent)
        }
    }

    private fun deliverOcrFromIntent(intent: Intent) {
        val text = intent.getStringExtra("OCR_TEXT")
        if (!text.isNullOrBlank()) {
            Log.d { "MainActivity: delivering OCR_TEXT len=${text.length}" }
            supportFragmentManager.setFragmentResult("ocr_result", bundleOf("text" to text))
        } else {
            Log.d { "MainActivity: no OCR_TEXT in intent" }
        }
    }
}
