package com.swancodes.geminiapitest

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.swancodes.geminiapitest.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUI(state)
            }
        }

        setupUI()
    }

    private fun setupUI() {
        binding.apply {
            binding.scanButton.setOnClickListener {
                takePhotoAndScan()
            }
            retakeScanButton.setOnClickListener {
                startCamera()
                viewModel.onRetakeScan()
            }
            doneButton.setOnClickListener {
                finish()
            }
        }

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateUI(state: UiState) = with(binding) {
        when (state) {
            is UiState.Initial -> {
                scanResultTextView.text = getString(R.string.scan_result)
                previewView.show()
                scanButton.show()
                progressBar.gone()
                doneButton.gone()
                capturedImageView.gone()
                retakeScanButton.gone()
            }

            is UiState.Captured -> {
                capturedImageView.setImageURI(state.capturedImage)
                scanResultTextView.text = getString(R.string.processing)
                previewView.gone()
                progressBar.gone()
                scanButton.gone()
                retakeScanButton.show()
                doneButton.show()
                capturedImageView.show()
            }

            is UiState.Loading -> {
                scanResultTextView.text = getString(R.string.processing)
                progressBar.show()
                scanButton.gone()
                retakeScanButton.gone()
                doneButton.gone()
            }


            is UiState.Success -> {
                scanResultTextView.text = state.outputText
                retakeScanButton.show()
                doneButton.show()
                capturedImageView.show()
                progressBar.gone()
                scanButton.gone()
            }

            is UiState.Error -> {
                scanResultTextView.text = state.errorMessage
                retakeScanButton.show()
                doneButton.show()
                progressBar.gone()
                scanButton.gone()
                Toast.makeText(this@MainActivity, state.errorMessage, Toast.LENGTH_SHORT).show()

            }
        }
    }

    companion object {
        private const val TAG = "YOOOOOOO"
        const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            arrayOf(
                Manifest.permission.CAMERA,
            )
        //private const val REQUEST_CODE_PERMISSIONS = 10
    }

    private fun takePhotoAndScan() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create output file to hold the image
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val photoFile = File(application.filesDir, "$name.jpg")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(photoFile)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                    val imagePath = savedUri.path ?: ""
                    viewModel.onImageTaken(imagePath, getString(R.string.prompt_placeholder))
                    //val msg = "Photo capture succeeded: ${output.savedUri}"
                    val msg = "Photo taken successfully"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Photo capture succeeded: $savedUri")

                    // Update UI to show captured image
                    binding.capturedImageView.setImageURI(savedUri)
                    binding.scanResultTextView.text = getString(R.string.processing)
                }
            }
        )
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startCamera()
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}