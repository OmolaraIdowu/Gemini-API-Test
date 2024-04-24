package com.swancodes.geminiapitest

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.swancodes.geminiapitest.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

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
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        binding.apply {

            imageButton.setOnClickListener {
                openGalleryForImageSelection()
            }

            scanButton.setOnClickListener {
                lifecycleScope.launch {
                    viewModel.uiState.collect { updateUI(it) }
                }
            }
            retakeScanButton.setOnClickListener {
                openGalleryForImageSelection()
            }
            doneButton.setOnClickListener {
                finish()
            }
        }
    }

    private fun updateUI(state: UiState) = with(binding) {
        when (state) {
            is UiState.Initial -> {
                progressBar.gone()
                doneButton.gone()
                retakeScanButton.gone()
            }

            is UiState.Loading -> {
                progressBar.show()
                scanButton.gone()
                scanResultTextView.text = getString(R.string.loading)
                retakeScanButton.gone()
                doneButton.gone()
            }

            is UiState.Success -> {
                progressBar.gone()
                scanResultTextView.text = state.outputText
                scanButton.gone()
                retakeScanButton.show()
                doneButton.show()
            }

            is UiState.Error -> {
                progressBar.gone()
                scanResultTextView.text = state.errorMessage
                scanButton.gone()
                retakeScanButton.show()
                doneButton.show()
                Toast.makeText(this@MainActivity, state.errorMessage, Toast.LENGTH_SHORT).show()

            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_SELECT_IMAGE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val exif = ExifInterface(inputStream!!)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
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
                    binding.imageButton.gone()
                    binding.retakeScanButton.gone()
                    binding.doneButton.gone()
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error loading image. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_SELECT_IMAGE = 100
    }

    private fun openGalleryForImageSelection() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE_SELECT_IMAGE)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}