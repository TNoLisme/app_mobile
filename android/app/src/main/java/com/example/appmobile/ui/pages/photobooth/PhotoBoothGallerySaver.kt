package com.example.appmobile.ui.pages.photobooth

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PhotoBoothGallerySaver {
    fun save(context: Context, uriString: String) {
        val source = fileFromUri(uriString)
        val fileName = "EmoGarden_Photobooth_${System.currentTimeMillis()}.jpg"
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EmoGarden")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create image entry")
            resolver.openOutputStream(imageUri)?.use { output ->
                FileInputStream(source).use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Cannot open image output")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(imageUri, values, null, null)
        } else {
            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val dir = File(pictures, "EmoGarden").apply { mkdirs() }
            val target = File(dir, fileName)
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
        }
    }

    private fun fileFromUri(uriString: String): File {
        return File(Uri.parse(uriString).path ?: throw IllegalArgumentException("Invalid photo"))
    }
}
