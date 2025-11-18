package com.example.documentsummarizer.ui.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.documentsummarizer.R
import com.example.documentsummarizer.databinding.ActivityScannerBinding
import com.example.documentsummarizer.ui.library.LibraryActivity
import com.example.documentsummarizer.ui.settings.SettingsActivity
import com.example.documentsummarizer.utils.ImageUtils
import com.example.documentsummarizer.utils.Log
import com.example.documentsummarizer.utils.OcrTextRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private enum class UiState { PREVIEW, PROCESSING, REVIEW }
    private var currentState = UiState.PREVIEW

    // Hold last capture preview + text for review
    private var lastBitmap: Bitmap? = null
    private var lastText: String = ""

    private val requestPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startCamera() else toast("Camera permission is required") }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener {
            startActivity(
                Intent(this, LibraryActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }

        // Buttons
        binding.buttonCapture.setOnClickListener { captureAndOcr() }
        binding.buttonRetake.setOnClickListener { goPreview() }
        binding.buttonConfirm.setOnClickListener {
            Log.d({ "User confirmed OCR text, returning to MainActivity" })
            val bmp = lastBitmap
            val text = lastText
            if (bmp == null || text.isBlank()) {
                toast("No text detected.")
                return@setOnClickListener
            }

            // Return OCR text to the parent so the UI/fragment can show AI summary
            binding.progressBarCapture.visibility = View.VISIBLE

            scope.launch {
                try {
                    // Persist the bitmap file now and get a content URI string so other UIs
                    // (summary fragment) can show the image without requiring a DB row yet.
                    val imgUriStr = withContext(Dispatchers.IO) {
                        ImageUtils.saveBitmapFile(this@ScannerActivity, bmp)
                    }

                    // Send OCR_TEXT and IMG_URI to the ScanSummaryActivity so it can show preview & allow save
                    val intent = Intent(this@ScannerActivity, ScanSummaryActivity::class.java).apply {
                        putExtra("OCR_TEXT", text)
                        putExtra("IMG_URI", imgUriStr)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent)
                    finish()
                } catch (t: Throwable) {
                    Log.d { t.message.toString() }
                    toast("Save failed: ${t.message}")
                } finally {
                    binding.progressBarCapture.visibility = View.GONE
                }
            }
        }

        if (hasCameraPermission()) startCamera() else requestPerm.launch(Manifest.permission.CAMERA)
        setState(UiState.PREVIEW)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scope.cancel()
    }

    // Helper functions
    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.Companion.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val backSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                if (!cameraProvider.hasCamera(backSelector)) {
                    Log.e({"No BACK camera available on this device/emulator."})
                    toast("No back camera available")
                    return@addListener
                }

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, backSelector, preview, imageCapture)
                Log.d({ "Back camera bound successfully" })
            } catch (e: Exception) {
                Log.e({ "Camera provider setup/bind failed" })
                toast("Camera error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndOcr() {
        val capture = imageCapture ?: return
        setState(UiState.PROCESSING)

        capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                scope.launch {
                    try {
                        val rotation = image.imageInfo.rotationDegrees

                        // 1) Build bitmap from ImageProxy safely
                        val bmp0 =
                            withContext(Dispatchers.IO) { ImageUtils.imageProxyToBitmap(image) }
                        val bmp  = withContext(Dispatchers.Default) {
                            ImageUtils.rotateBitmapIfNeeded(
                                bmp0,
                                rotation
                            )
                        }

                        // 2) Run OCR from the bitmap
                        val text = withContext(Dispatchers.IO) { OcrTextRecognizer.process(bmp) }

                        // 3) Close after all processing
                        image.close()

                        lastBitmap = bmp
                        lastText = text
                        showReview(bmp, text)
                    } catch (t: Throwable) {
                        Log.e( { "Processing failed" })
                        runCatching { image.close() }
                        toast("Capture Image failed: ${t.message}")
                        setState(UiState.PREVIEW)
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e( {"Capture error $exception" })
                toast("Capture error: ${exception.message}")
                setState(UiState.PREVIEW)
            }
        })
    }


    private fun showReview(bmp: Bitmap?, text: String) {
        binding.imgThumb.setImageBitmap(bmp)
        binding.tvOcr.text = text.ifBlank { "(No text detected)" }
        setState(UiState.REVIEW)
    }

    private fun goPreview() {
        lastBitmap = null
        lastText = ""
        binding.imgThumb.setImageDrawable(null)
        binding.tvOcr.text = ""
        setState(UiState.PREVIEW)
    }

    private fun setState(state: UiState) {
        currentState = state
        when (state) {
            UiState.PREVIEW -> {
                binding.previewContainer.visibility = View.VISIBLE
                binding.reviewContainer.visibility = View.GONE
                binding.progressBarCapture.visibility = View.GONE
                binding.buttonCapture.isEnabled = true
            }
            UiState.PROCESSING -> {
                binding.previewContainer.visibility = View.VISIBLE
                binding.reviewContainer.visibility = View.GONE
                binding.progressBarCapture.visibility = View.VISIBLE
                binding.buttonCapture.isEnabled = false
            }
            UiState.REVIEW -> {
                binding.previewContainer.visibility = View.GONE
                binding.reviewContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_scanner, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            Log.d({ "Navigating to SettingsActivity" })
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}