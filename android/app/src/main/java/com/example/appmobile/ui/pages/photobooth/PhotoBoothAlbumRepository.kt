package com.example.appmobile.ui.pages.photobooth

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class SavedPhotoBoothItem(
    val id: String,
    val photoUri: String,
    val sourceUri: String,
    val emotionIds: List<String>,
    val frameId: String,
    val layoutType: PhotoBoothLayoutType,
    val createdAt: Long
)

class PhotoBoothAlbumRepository(context: Context) {
    private val albumDir = File(context.filesDir, "photobooth_album").apply { mkdirs() }
    private val indexFile = File(albumDir, "album_index.json")

    @Synchronized
    fun save(
        source: File,
        sourceUri: String,
        emotionIds: List<String>,
        frameId: String,
        layoutType: PhotoBoothLayoutType
    ): SavedPhotoBoothItem {
        load().firstOrNull { item ->
            item.sourceUri == sourceUri && fileFromUri(item.photoUri).exists()
        }?.let { return it }

        val createdAt = System.currentTimeMillis()
        val id = "photobooth_$createdAt"
        val target = File(albumDir, "$id.jpg")
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        val saved = SavedPhotoBoothItem(
            id = id,
            photoUri = Uri.fromFile(target).toString(),
            sourceUri = sourceUri,
            emotionIds = emotionIds,
            frameId = frameId,
            layoutType = layoutType,
            createdAt = createdAt
        )
        persist((load() + saved).distinctBy { it.id })
        return saved
    }

    @Synchronized
    fun load(): List<SavedPhotoBoothItem> {
        val indexed = readIndex().filter { fileFromUri(it.photoUri).exists() }
        val indexedFiles = indexed.map { fileFromUri(it.photoUri).canonicalPath }.toSet()
        val legacyItems = albumDir.listFiles()
            .orEmpty()
            .filter { it.extension.equals("jpg", ignoreCase = true) && it.canonicalPath !in indexedFiles }
            .map { file ->
                SavedPhotoBoothItem(
                    id = file.nameWithoutExtension,
                    photoUri = Uri.fromFile(file).toString(),
                    sourceUri = "",
                    emotionIds = emptyList(),
                    frameId = PhotoBoothCatalog.frames.first().id,
                    layoutType = PhotoBoothLayoutType.VerticalStrip,
                    createdAt = file.lastModified()
                )
            }
        return (indexed + legacyItems).distinctBy { it.id }.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun delete(id: String) {
        val current = load()
        current.firstOrNull { it.id == id }?.let { fileFromUri(it.photoUri).delete() }
        persist(current.filterNot { it.id == id })
    }

    private fun readIndex(): List<SavedPhotoBoothItem> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val json = JSONArray(indexFile.readText(Charsets.UTF_8))
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.getJSONObject(index)
                    val emotions = item.optJSONArray("emotionIds") ?: JSONArray()
                    add(
                        SavedPhotoBoothItem(
                            id = item.getString("id"),
                            photoUri = item.getString("photoUri"),
                            sourceUri = item.optString("sourceUri"),
                            emotionIds = buildList {
                                for (emotionIndex in 0 until emotions.length()) {
                                    add(emotions.getString(emotionIndex))
                                }
                            },
                            frameId = item.optString("frameId", PhotoBoothCatalog.frames.first().id),
                            layoutType = runCatching {
                                PhotoBoothLayoutType.valueOf(item.optString("layoutType"))
                            }.getOrDefault(PhotoBoothLayoutType.VerticalStrip),
                            createdAt = item.optLong("createdAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(items: List<SavedPhotoBoothItem>) {
        val json = JSONArray()
        items.forEach { item ->
            json.put(
                JSONObject()
                    .put("id", item.id)
                    .put("photoUri", item.photoUri)
                    .put("sourceUri", item.sourceUri)
                    .put("emotionIds", JSONArray(item.emotionIds))
                    .put("frameId", item.frameId)
                    .put("layoutType", item.layoutType.name)
                    .put("createdAt", item.createdAt)
            )
        }
        indexFile.writeText(json.toString(), Charsets.UTF_8)
    }

    private fun fileFromUri(uriString: String): File {
        return File(Uri.parse(uriString).path ?: "")
    }
}
