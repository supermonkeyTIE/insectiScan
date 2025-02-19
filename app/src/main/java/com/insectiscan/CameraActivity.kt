package com.insectiscan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Locale

class CameraActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null
    private lateinit var viewFinder: PreviewView
    private lateinit var imageAnalyzer: ImageAnalysis

    // Lazy-initialized output directory
    private val outputDirectory by lazy {
        getOutputDirectoryPath()
    }

    // Method to get output directory path
    private fun getOutputDirectoryPath(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        viewFinder = findViewById(R.id.viewFinder)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        findViewById<MaterialButton>(R.id.camera_capture_button).setOnClickListener {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Create output file
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()

                    // Optional: Analyze the captured image
                    val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                    analyzeImage(bitmap)

                    setResult(RESULT_OK, Intent().apply {
                        data = savedUri
                    })
                    finish()
                }

                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(baseContext, "Photo capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            // Setup image analysis
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), InsectDetectionAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)
            } catch(exc: Exception) {
                Toast.makeText(this, "Camera failed to start", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // ML Kit and TensorFlow Lite Integration
    inner class InsectDetectionAnalyzer : ImageAnalysis.Analyzer {
        private val mlKitLabeler = ImageLabeling.getClient(
            ImageLabelerOptions.DEFAULT_OPTIONS
        )

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = imageProxy.image

            image?.let {
                val inputImage = InputImage.fromMediaImage(
                    image,
                    rotationDegrees
                )

                // ML Kit Image Labeling
                mlKitLabeler.process(inputImage)
                    .addOnSuccessListener { labels ->
                        labels.forEach { label ->
                            Log.d("ImageLabeling",
                                "Label: ${label.text}, Confidence: ${label.confidence}"
                            )
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ImageLabeling", "Detection failed", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
        }
    }

    // Analyze image with TensorFlow Lite
    private fun analyzeImage(bitmap: Bitmap) {
        try {
            // Load TensorFlow Lite model
            val tflite = Interpreter(loadModelFile())

            // Preprocess the bitmap
            val inputBuffer = preprocessImage(bitmap)

            // Prepare output buffer
            val outputBuffer = ByteBuffer.allocateDirect(4 * 1 * 10)
            outputBuffer.order(ByteOrder.nativeOrder())

            // Run inference
            tflite.run(inputBuffer, outputBuffer)

            // Process and display results
            val results = processResults(outputBuffer)
            displayDetectionResults(results)
        } catch (e: Exception) {
            Log.e("TFLite", "Error processing image", e)
        }
    }

    // Load TensorFlow Lite model file
    private fun loadModelFile(): ByteBuffer {
        val modelFile = File(filesDir, "insect_detection_model.tflite")
        // Note: You'll need to copy your .tflite model to this location during app initialization
        return FileInputStream(modelFile).channel.map(
            FileChannel.MapMode.READ_ONLY,
            0,
            modelFile.length()
        )
    }

    // Preprocess image for TensorFlow Lite model
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        // Implement image preprocessing based on your specific model requirements
        // This is a placeholder and needs to be customized
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val inputBuffer = ByteBuffer.allocateDirect(224 * 224 * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        // Convert bitmap to byte buffer
        // Implement normalization and pixel conversion based on your model
        return inputBuffer
    }

    // Process TensorFlow Lite model results
    private fun processResults(outputBuffer: ByteBuffer): List<DetectionResult> {
        // Parse output based on your model's output format
        // This is a placeholder and needs to be customized
        return listOf(
            DetectionResult("Placeholder Insect", 0.75f)
        )
    }

    // Display detection results
    private fun displayDetectionResults(results: List<DetectionResult>) {
        // Implement UI to show detection results
        results.forEach { result ->
            Toast.makeText(
                this,
                "Detected: ${result.label} (${result.confidence * 100}% confidence)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Data class to represent detection results
    data class DetectionResult(
        val label: String,
        val confidence: Float
    )

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}