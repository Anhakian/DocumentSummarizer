package com.example.documentsummarizer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.documentsummarizer.databinding.ActivityScannerBinding
import com.example.documentsummarizer.utils.ImageUtils
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

        // Buttons
        binding.buttonCapture.setOnClickListener { captureAndOcr() }
        binding.buttonRetake.setOnClickListener { goPreview() }
        binding.buttonConfirm.setOnClickListener {
            // For demo: accept and go back to preview (you could store lastText in a list here)
            toast("Page confirmed (${lastText.length} chars)")
            goPreview()
        }

        if (hasCameraPermission()) startCamera() else requestPerm.launch(Manifest.permission.CAMERA)
        setState(UiState.PREVIEW)
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val backSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                if (!cameraProvider.hasCamera(backSelector)) {
                    Log.e("Anh", "No BACK camera available on this device/emulator.")
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
                Log.d("Anh", "Back camera bound successfully")
            } catch (e: Exception) {
                Log.e("Anh", "Camera provider setup/bind failed", e)
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
                        val bmp0 = withContext(Dispatchers.IO) { ImageUtils.imageProxyToBitmap(image) }
                        val bmp  = withContext(Dispatchers.Default) { ImageUtils.rotateBitmapIfNeeded(bmp0, rotation) }

                        // 2) Run OCR from the bitmap
                        val text = withContext(Dispatchers.IO) { OcrTextRecognizer.process(bmp) }

                        // 3) Close after all processing
                        image.close()

                        lastBitmap = bmp
                        lastText = text
                        showReview(bmp, text)
                    } catch (t: Throwable) {
                        Log.e("OCR", "Processing failed", t)
                        runCatching { image.close() }
                        toast("Capture Image failed: ${t.message}")
                        setState(UiState.PREVIEW)
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraX", "Capture error", exception)
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scope.cancel()
    }
}