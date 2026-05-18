package com.fastscan.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.fastscan.app.databinding.ActivityGalleryBinding
import com.fastscan.app.databinding.ItemDocBinding
import java.io.File

class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding
    private lateinit var adapter: DocAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = DocAdapter(
            onShare = { doc -> shareDoc(doc) },
            onDelete = { doc -> deleteDoc(doc) }
        )

        binding.rvDocs.layoutManager = LinearLayoutManager(this)
        binding.rvDocs.adapter = adapter

        loadDocuments()
    }

    private fun loadDocuments() {
        val docs = mutableListOf<ScannedDoc>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("Pictures/ScannedDocs%")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val date = cursor.getLong(dateColumn)
                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    docs.add(ScannedDoc(name, date, contentUri))
                }
            }
        } else {
            val directory = File(getExternalFilesDir(null), "ScannedDocs")
            if (directory.exists()) {
                directory.listFiles()?.forEach { file ->
                    docs.add(ScannedDoc(file.name, file.lastModified() / 1000, Uri.fromFile(file)))
                }
            }
        }

        adapter.submitList(docs)
        binding.tvEmpty.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun shareDoc(doc: ScannedDoc) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, doc.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Document"))
    }

    private fun deleteDoc(doc: ScannedDoc) {
        contentResolver.delete(doc.uri, null, null)
        loadDocuments()
    }

    data class ScannedDoc(val name: String, val date: Long, val uri: Uri)

    inner class DocAdapter(
        private val onShare: (ScannedDoc) -> Unit,
        private val onDelete: (ScannedDoc) -> Unit
    ) : RecyclerView.Adapter<DocAdapter.ViewHolder>() {
        private var docs = listOf<ScannedDoc>()

        fun submitList(newDocs: List<ScannedDoc>) {
            docs = newDocs
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDocBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(docs[position])
        }

        override fun getItemCount() = docs.size

        inner class ViewHolder(val binding: ItemDocBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(doc: ScannedDoc) {
                binding.tvFileName.text = doc.name
                binding.tvDate.text = java.text.DateFormat.getDateTimeInstance().format(doc.date * 1000)
                binding.imgThumbnail.load(doc.uri)

                binding.btnMore.setOnClickListener { view ->
                    val popup = PopupMenu(view.context, view)
                    popup.menu.add("Share")
                    popup.menu.add("Delete")
                    popup.setOnMenuItemClickListener { item ->
                        when (item.title) {
                            "Share" -> onShare(doc)
                            "Delete" -> onDelete(doc)
                        }
                        true
                    }
                    popup.show()
                }
            }
        }
    }
}