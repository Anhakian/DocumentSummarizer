package com.example.documentsummarizer.ui.capture

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.documentsummarizer.R

class ScanSummaryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_host)

        if (savedInstanceState == null) {
            val frag = ScanSummaryFragment().apply {
                arguments = intent.extras
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, frag)
                .commit()
        }
    }
}
