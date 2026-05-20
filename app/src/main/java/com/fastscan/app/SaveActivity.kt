package com.fastscan.app

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fastscan.app.databinding.ActivitySaveBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaveActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySaveBinding
    private var currentBitmap: Bitmap? = null
    private var finalCompressedData: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySaveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide filter layout as it's now in CropActivity
        binding.layoutFilters.visibility = android.view.View.GONE

        val defaultName = "BankScan_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        binding.etFileName.setText(defaultName)

        // Auto-select text on focus
        binding.etFileName.setSelectAllOnFocus(true)
        binding.etFileName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.etFileName.post {
                    binding.etFileName.selectAll()
                }
            }
        }

        val uriString = intent.getStringExtra("IMAGE_URI")
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            loadBitmap(uri)
        }

        binding.rgCompression.setOnCheckedChangeListener { _, _ ->
            calculateFinalSize()
        }

        binding.btnSave.setOnClickListener {
            val fileName = binding.etFileName.text.toString()
            if (fileName.isEmpty()) {
                Toast.makeText(this, "Please enter a file name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveDocument(fileName)
        }
    }

    private fun loadBitmap(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri)
        currentBitmap = BitmapFactory.decodeStream(inputStream)
        binding.imgPreview.setImageBitmap(currentBitmap)
        calculateFinalSize()
    }

    private fun calculateFinalSize() {
        val bitmap = currentBitmap ?: return
        
        lifecycleScope.launch(Dispatchers.Default) {
            val mode = if (binding.rbPassport.isChecked) Mode.PASSPORT else Mode.AADHAAR
            val result = processImageSmart(bitmap, mode)
            finalCompressedData = result
            
            withContext(Dispatchers.Main) {
                val sizeKB = result.size / 1024
                binding.tvSizeInfo.text = "Estimated Final Size: $sizeKB KB"
            }
        }
    }

    private enum class Mode { PASSPORT, AADHAAR }

    private fun processImageSmart(bitmap: Bitmap, mode: Mode): ByteArray {
        val targetWidth: Int
        val targetHeight: Int
        val targetSizeKB: Int

        when (mode) {
            Mode.PASSPORT -> {
                targetWidth = 144
                targetHeight = 164
                targetSizeKB = 20
            }
            Mode.AADHAAR -> {
                targetWidth = 450
                targetHeight = 1000
                targetSizeKB = 100
            }
        }

        // 1. Smart Resize
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

        // 2. Iterative Smart Compression
        var quality = 100
        var stream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)

        while (stream.toByteArray().size / 1024 >= targetSizeKB && quality > 5) {
            quality -= 2 // Faster steps but still fine-grained
            stream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        }

        return stream.toByteArray()
    }

    private fun saveDocument(fileName: String) {
        val data = finalCompressedData ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val success = writeToFile(data, fileName)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@SaveActivity, "Saved to Pictures/BankReadyScanner", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@SaveActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@SaveActivity, "Failed to save", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SaveActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun writeToFile(data: ByteArray, fileName: String): Boolean {
        val folderName = "BankReadyScanner"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$folderName")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(data)
                }
                true
            } ?: false
        } else {
            val directory = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), folderName)
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, "$fileName.jpg")
            FileOutputStream(file).use { it.write(data) }
            true
        }
    }
}