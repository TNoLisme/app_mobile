package com.example.appmobile.ui.pages.photobooth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object PhotoBoothComposer {
    fun compose(
        context: Context,
        shots: List<PhotoBoothShot>,
        frameTemplate: PhotoBoothFrameTemplate,
        layoutType: PhotoBoothLayoutType,
        createdDateText: String,
        outputDir: File
    ): String {
        require(shots.isNotEmpty()) { "No shots to compose." }
        outputDir.mkdirs()

        val outputSize = when (layoutType) {
            PhotoBoothLayoutType.VerticalStrip -> Pair(1080, 1920)
            PhotoBoothLayoutType.Grid2x2 -> Pair(1600, 1600)
        }
        val output = Bitmap.createBitmap(outputSize.first, outputSize.second, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        drawBackground(canvas, frameTemplate, output.width, output.height, paint)
        drawDecorations(canvas, frameTemplate, output.width, output.height, paint)
        drawHeader(canvas, frameTemplate, output.width, paint)

        val bitmaps = shots.map { shot -> decodePhoto(context, shot.photoUri) }
        try {
            when (layoutType) {
                PhotoBoothLayoutType.VerticalStrip -> drawVerticalStrip(canvas, bitmaps, shots, frameTemplate, paint)
                PhotoBoothLayoutType.Grid2x2 -> drawGrid(canvas, bitmaps, shots, frameTemplate, paint)
            }
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }

        drawFooter(canvas, frameTemplate, output.width, output.height, createdDateText, paint)

        val file = File(outputDir, "EmoGarden_Photobooth_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { output.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        output.recycle()
        return Uri.fromFile(file).toString()
    }

    private fun drawBackground(
        canvas: Canvas,
        template: PhotoBoothFrameTemplate,
        width: Int,
        height: Int,
        paint: Paint
    ) {
        val start = template.primaryColor.toArgb()
        val end = template.secondaryColor.toArgb()
        paint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            start,
            end,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 22f
        paint.color = AndroidColor.WHITE
        canvas.drawRoundRect(RectF(42f, 42f, width - 42f, height - 42f), 42f, 42f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawHeader(
        canvas: Canvas,
        template: PhotoBoothFrameTemplate,
        width: Int,
        paint: Paint
    ) {
        paint.shader = null
        paint.color = template.textColor.toArgb()
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 56f
        canvas.drawText("Photobooth cảm xúc", width / 2f, 96f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 28f
        paint.alpha = 210
        canvas.drawText("EmoGarden", width / 2f, 136f, paint)
        paint.alpha = 255
    }

    private fun drawFooter(
        canvas: Canvas,
        template: PhotoBoothFrameTemplate,
        width: Int,
        height: Int,
        dateText: String,
        paint: Paint
    ) {
        paint.color = template.textColor.toArgb()
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 34f
        canvas.drawText("EmoGarden · $dateText", width / 2f, height - 76f, paint)
    }

    private fun drawVerticalStrip(
        canvas: Canvas,
        bitmaps: List<Bitmap>,
        shots: List<PhotoBoothShot>,
        template: PhotoBoothFrameTemplate,
        paint: Paint
    ) {
        val count = bitmaps.size.coerceIn(1, 4)
        val margin = 86f
        val top = 180f
        val footer = 150f
        val gap = 28f
        val labelHeight = 50f
        val availableHeight = canvas.height - top - footer - gap * (count - 1)
        val photoHeight = ((availableHeight - labelHeight * count) / count).coerceAtLeast(260f)
        val photoWidth = canvas.width - margin * 2
        var y = top

        repeat(count) { index ->
            val photoRect = RectF(margin, y, margin + photoWidth, y + photoHeight)
            drawPhotoCell(canvas, bitmaps[index], photoRect, paint)
            drawEmotionLabel(
                canvas = canvas,
                text = PhotoBoothCatalog.emotionName(shots[index].emotionId),
                centerX = canvas.width / 2f,
                baselineY = photoRect.bottom + 36f,
                template = template,
                paint = paint
            )
            y += photoHeight + labelHeight + gap
        }
    }

    private fun drawGrid(
        canvas: Canvas,
        bitmaps: List<Bitmap>,
        shots: List<PhotoBoothShot>,
        template: PhotoBoothFrameTemplate,
        paint: Paint
    ) {
        val margin = 84f
        val top = 190f
        val gap = 30f
        val labelHeight = 58f
        val cellWidth = (canvas.width - margin * 2 - gap) / 2f
        val photoHeight = (canvas.height - top - 170f - gap - labelHeight * 2) / 2f

        bitmaps.take(4).forEachIndexed { index, bitmap ->
            val row = index / 2
            val col = index % 2
            val left = margin + col * (cellWidth + gap)
            val topY = top + row * (photoHeight + labelHeight + gap)
            val photoRect = RectF(left, topY, left + cellWidth, topY + photoHeight)
            drawPhotoCell(canvas, bitmap, photoRect, paint)
            drawEmotionLabel(
                canvas = canvas,
                text = PhotoBoothCatalog.emotionName(shots[index].emotionId),
                centerX = photoRect.centerX(),
                baselineY = photoRect.bottom + 40f,
                template = template,
                paint = paint
            )
        }
    }

    private fun drawPhotoCell(canvas: Canvas, bitmap: Bitmap, rect: RectF, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = AndroidColor.WHITE
        canvas.drawRoundRect(RectF(rect.left - 12f, rect.top - 12f, rect.right + 12f, rect.bottom + 12f), 32f, 32f, paint)

        val path = Path().apply { addRoundRect(rect, 26f, 26f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        val src = centerCropSource(bitmap, rect.width() / rect.height())
        canvas.drawBitmap(bitmap, src, rect, paint)
        canvas.restore()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = AndroidColor.WHITE
        canvas.drawRoundRect(rect, 26f, 26f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawEmotionLabel(
        canvas: Canvas,
        text: String,
        centerX: Float,
        baselineY: Float,
        template: PhotoBoothFrameTemplate,
        paint: Paint
    ) {
        paint.shader = null
        paint.color = template.textColor.toArgb()
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 34f
        canvas.drawText(text, centerX, baselineY, paint)
    }

    private fun drawDecorations(
        canvas: Canvas,
        template: PhotoBoothFrameTemplate,
        width: Int,
        height: Int,
        paint: Paint
    ) {
        when (template.id) {
            "starry_night" -> drawStars(canvas, width, height, paint)
            "emotion_stickers" -> drawMiniFaces(canvas, width, height, paint)
            "rainbow_feelings" -> drawRainbowDecor(canvas, width, height, paint)
            "flower_booth", "garden_blue" -> drawGardenDecor(canvas, width, height, paint)
            else -> drawMinimalDecor(canvas, width, height, paint)
        }
    }

    private fun drawGardenDecor(canvas: Canvas, width: Int, height: Int, paint: Paint) {
        paint.style = Paint.Style.FILL
        val leaf = AndroidColor.rgb(97, 177, 124)
        val flower = AndroidColor.rgb(255, 214, 102)
        val blue = AndroidColor.rgb(124, 200, 255)
        val points = listOf(
            86f to 172f,
            width - 112f to 182f,
            86f to height - 180f,
            width - 120f to height - 190f
        )
        points.forEachIndexed { index, (x, y) ->
            paint.color = leaf
            canvas.drawOval(RectF(x - 38f, y - 12f, x + 18f, y + 24f), paint)
            canvas.drawOval(RectF(x + 4f, y - 34f, x + 50f, y + 6f), paint)
            paint.color = if (index % 2 == 0) flower else blue
            repeat(5) { petal ->
                val angle = Math.toRadians((petal * 72).toDouble())
                val cx = x + kotlin.math.cos(angle).toFloat() * 24f
                val cy = y + kotlin.math.sin(angle).toFloat() * 24f
                canvas.drawCircle(cx, cy, 14f, paint)
            }
            paint.color = AndroidColor.WHITE
            canvas.drawCircle(x, y, 12f, paint)
        }
    }

    private fun drawRainbowDecor(canvas: Canvas, width: Int, height: Int, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 16f
        val rect = RectF(width - 250f, 110f, width - 72f, 288f)
        listOf(
            AndroidColor.rgb(255, 124, 124),
            AndroidColor.rgb(255, 214, 102),
            AndroidColor.rgb(93, 201, 129),
            AndroidColor.rgb(112, 190, 255)
        ).forEachIndexed { index, color ->
            paint.color = color
            val inset = index * 20f
            canvas.drawArc(RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset), 200f, 140f, false, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(92f, 136f, 28f, paint)
        canvas.drawCircle(128f, 136f, 34f, paint)
        canvas.drawCircle(164f, 136f, 25f, paint)
        canvas.drawCircle(width - 170f, height - 140f, 30f, paint)
        canvas.drawCircle(width - 130f, height - 142f, 38f, paint)
    }

    private fun drawMiniFaces(canvas: Canvas, width: Int, height: Int, paint: Paint) {
        val faces = listOf(
            Triple(90f, 150f, AndroidColor.rgb(255, 214, 77)),
            Triple(width - 94f, 150f, AndroidColor.rgb(255, 138, 53)),
            Triple(90f, height - 154f, AndroidColor.rgb(134, 182, 255)),
            Triple(width - 96f, height - 154f, AndroidColor.rgb(168, 217, 107))
        )
        faces.forEach { (x, y, color) ->
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawCircle(x, y, 34f, paint)
            paint.color = AndroidColor.rgb(36, 47, 64)
            canvas.drawCircle(x - 12f, y - 8f, 4f, paint)
            canvas.drawCircle(x + 12f, y - 8f, 4f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawArc(RectF(x - 15f, y, x + 15f, y + 22f), 15f, 150f, false, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawStars(canvas: Canvas, width: Int, height: Int, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = AndroidColor.rgb(255, 225, 112)
        val stars = listOf(90f to 150f, width - 90f to 250f, 150f to height - 170f, width - 170f to height - 120f, 90f to height / 2f)
        stars.forEach { (x, y) ->
            canvas.drawCircle(x, y, 8f, paint)
            canvas.drawRect(x - 2f, y - 18f, x + 2f, y + 18f, paint)
            canvas.drawRect(x - 18f, y - 2f, x + 18f, y + 2f, paint)
        }
    }

    private fun drawMinimalDecor(canvas: Canvas, width: Int, height: Int, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = AndroidColor.rgb(124, 200, 255)
        canvas.drawCircle(92f, 150f, 20f, paint)
        canvas.drawCircle(width - 92f, height - 150f, 24f, paint)
        paint.color = AndroidColor.rgb(255, 226, 138)
        canvas.drawCircle(width - 116f, 150f, 15f, paint)
        canvas.drawCircle(124f, height - 150f, 14f, paint)
    }

    private fun decodePhoto(context: Context, uriString: String): Bitmap {
        val bytes = readBytes(context, Uri.parse(uriString))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, 1200, 1200)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IllegalStateException("Cannot decode photo")
        return applyExifRotation(decoded, bytes)
    }

    private fun readBytes(context: Context, uri: Uri): ByteArray {
        if (uri.scheme == "file") {
            val path = uri.path ?: throw IllegalArgumentException("Invalid file uri")
            return File(path).readBytes()
        }
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Invalid photo uri")
    }

    private fun applyExifRotation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    private fun centerCropSource(bitmap: Bitmap, targetRatio: Float): Rect {
        val sourceRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        return if (sourceRatio > targetRatio) {
            val newWidth = (bitmap.height * targetRatio).toInt()
            val left = (bitmap.width - newWidth) / 2
            Rect(left, 0, left + newWidth, bitmap.height)
        } else {
            val newHeight = (bitmap.width / targetRatio).toInt()
            val top = (bitmap.height - newHeight) / 2
            Rect(0, top, bitmap.width, top + min(newHeight, bitmap.height))
        }
    }
}
