package com.fastscan.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fastscan.app.databinding.ActivityCropBinding
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.model.AspectRatio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class CropActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCropBinding
    private var currentUri: Uri? = null
    private var originalBitmap: Bitmap? = null
    private var filteredBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriString = intent.getStringExtra("IMAGE_URI")
        if (uriString != null) {
            currentUri = Uri.parse(uriString)
            loadBitmap(currentUri!!)
        }

        binding.btnCropAction.setOnClickListener { startUCrop() }
        binding.btnFilterAction.setOnClickListener { showFilterDialog() }
        binding.btnMagicScan.setOnClickListener { applyMagicScan() }
        binding.btnNext.setOnClickListener {
            saveFilteredImageAndNext()
        }
    }

    private fun loadBitmap(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri)
        originalBitmap = BitmapFactory.decodeStream(inputStream)
        filteredBitmap = originalBitmap
        binding.imgCropPreview.setImageBitmap(filteredBitmap)
    }

    private fun startUCrop() {
        val uri = currentUri ?: return
        val destinationUri = Uri.fromFile(File(cacheDir, "temp_crop.jpg"))
        UCrop.of(uri, destinationUri)
            .withOptions(UCrop.Options().apply {
                setToolbarColor(ContextCompat.getColor(this@CropActivity, R.color.black))
                setStatusBarColor(ContextCompat.getColor(this@CropActivity, R.color.black))
                setActiveControlsWidgetColor(ContextCompat.getColor(this@CropActivity, R.color.blue_accent))
                setToolbarWidgetColor(ContextCompat.getColor(this@CropActivity, R.color.white))
                setFreeStyleCropEnabled(true)
                setAspectRatioOptions(0,
                    AspectRatio("Free", 0f, 0f),
                    AspectRatio("Passport", 144f, 164f),
                    AspectRatio("Aadhaar", 450f, 1000f),
                    AspectRatio("A4", 210f, 297f),
                    AspectRatio("US Letter", 8.5f, 11f),
                    AspectRatio("ID Card", 85.6f, 53.98f),
                    AspectRatio("1:1", 1f, 1f),
                    AspectRatio("3:4", 3f, 4f),
                    AspectRatio("16:9", 16f, 9f)
                )
            })
            .start(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            val resultUri = UCrop.getOutput(data!!)
            resultUri?.let {
                currentUri = it
                loadBitmap(it)
            }
        }
    }

    private fun applyMagicScan() {
        val bitmap = originalBitmap ?: return
        lifecycleScope.launch(Dispatchers.Default) {
            val processed = ScanProcessor.processDocument(bitmap)
            withContext(Dispatchers.Main) {
                filteredBitmap = processed
                binding.imgCropPreview.setImageBitmap(filteredBitmap)
                Toast.makeText(this@CropActivity, "Magic Scan Applied!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFilterDialog() {
        val filters = arrayOf("Original", "Grayscale", "Black & White")
        AlertDialog.Builder(this)
            .setTitle("Select Filter")
            .setItems(filters) { _, which ->
                applyFilter(which)
            }
            .show()
    }

    private fun applyFilter(index: Int) {
        val bitmap = originalBitmap ?: return
        filteredBitmap = when (index) {
            0 -> bitmap
            1 -> toGrayscale(bitmap)
            2 -> toBlackWhite(bitmap)
            else -> bitmap
        }
        binding.imgCropPreview.setImageBitmap(filteredBitmap)
    }

    private fun toGrayscale(bmp: Bitmap): Bitmap {
        val bmpGrayscale = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmpGrayscale)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        return bmpGrayscale
    }

    private fun toBlackWhite(bmp: Bitmap): Bitmap {
        val gray = toGrayscale(bmp)
        val bwBitmap = Bitmap.createBitmap(gray.width, gray.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bwBitmap)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.set(floatArrayOf(
            85f, 85f, 85f, 0f, -128f * 255f,
            85f, 85f, 85f, 0f, -128f * 255f,
            85f, 85f, 85f, 0f, -128f * 255f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(gray, 0f, 0f, paint)
        return bwBitmap
    }

    private fun saveFilteredImageAndNext() {
        val bitmap = filteredBitmap ?: return
        val file = File(cacheDir, "filtered_image.jpg")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }
        val intent = Intent(this, SaveActivity::class.java).apply {
            putExtra("IMAGE_URI", Uri.fromFile(file).toString())
        }
        startActivity(intent)
    }
}