package com.swancodes.geminiapitest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.swancodes.geminiapitest.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
    }

    private fun setupUI() {
        binding.apply {
            scanButton.isEnabled = false
            scanButton.setOnClickListener {
                lifecycleScope.launch {
                    viewModel.uiState.collect { state ->
                        updateUI(state)
                    }
                }
            }
            retakeScanButton.setOnClickListener {
                viewModel.onRetakeScan()
            }
            doneButton.setOnClickListener {
                finish()
            }
            imageButton.setOnClickListener {
                showImageSelectionDialog()
            }
        }
    }

    private fun updateUI(state: UiState) = with(binding) {
        when (state) {
            is UiState.Initial -> {
                scanResultTextView.text = getString(R.string.scan_result)
                progressBar.gone()
                doneButton.gone()
                scanButton.show()
                retakeScanButton.gone()
                imageButton.show()
                binding.selectedImage.setImageDrawable(null)
                binding.scanButton.isEnabled = false
            }

            is UiState.Loading -> {
                scanResultTextView.text = getString(R.string.analyzing)
                progressBar.show()
                scanButton.gone()
                retakeScanButton.gone()
                doneButton.gone()
            }

            is UiState.Success -> {
                scanResultTextView.text = state.outputText
                retakeScanButton.show()
                doneButton.show()
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
                Toast.makeText(baseContext, "Permission request denied", Toast.LENGTH_SHORT).show()
                showPermissionDeniedDialog()
            } else {
                // Permission granted, open the camera
                openCamera()
            }
        }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            startActivityForResult(intent, REQUEST_CODE_IMAGE_CAPTURE)
        } catch (e: IOException) {
            // display error message to the user
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGalleryForImageSelection() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE_SELECT_IMAGE)
    }

    private fun showImageSelectionDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("Choose an option")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> {
                    // Request camera permissions
                    if (allPermissionsGranted()) {
                        openCamera()
                    } else {
                        requestPermissions()
                    }
                }

                1 -> openGalleryForImageSelection()
            }
        }
        builder.show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app requires camera permission to take photos. Please grant the permission in settings.")
            .setPositiveButton("Go to Settings") { dialog, _ ->
                // Redirect the user to app settings to grant permission
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SELECT_IMAGE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                handleSelectedImage(uri)
            }
        } else if (requestCode == REQUEST_CODE_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as Bitmap
            handleCapturedImage(imageBitmap)
        }
    }

    // Handle selected image from gallery
    private fun handleSelectedImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val exif = ExifInterface(inputStream!!)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            val rotatedBitmap = rotateBitmap(bitmap, rotationDegrees)
            viewModel.sendPrompt(rotatedBitmap, getString(R.string.prompt_placeholder))
            binding.selectedImage.setImageBitmap(rotatedBitmap)
            binding.scanButton.isEnabled = true
            binding.imageButton.gone()
            binding.retakeScanButton.gone()
            binding.doneButton.gone()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(
                this,
                "Error loading image. Please try again.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Handle captured image from camera
    private fun handleCapturedImage(imageBitmap: Bitmap) {
        viewModel.sendPrompt(imageBitmap, getString(R.string.prompt_placeholder))
        binding.selectedImage.setImageBitmap(imageBitmap)
        binding.scanButton.isEnabled = true
        binding.imageButton.gone()
        binding.retakeScanButton.gone()
        binding.doneButton.gone()
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
        private const val REQUEST_CODE_SELECT_IMAGE = 100
        private const val REQUEST_CODE_IMAGE_CAPTURE = 200
    }
}