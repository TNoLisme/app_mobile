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
                PhotoBoothLayoutType.VerticalStrip -> drawVerticalStrip(canvas, bitmaps, shots, paint)
                PhotoBoothLayoutType.Grid2x2 -> drawGrid(canvas, bitmaps, shots, paint)
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

        drawSoftBackdrop(canvas, template, width, height, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 12f
        paint.color = AndroidColor.argb(238, 255, 255, 255)
        canvas.drawRoundRect(RectF(24f, 24f, width - 24f, height - 24f), 42f, 42f, paint)
        paint.strokeWidth = 3f
        paint.color = AndroidColor.argb(156, 255, 255, 255)
        canvas.drawRoundRect(RectF(42f, 42f, width - 42f, height - 42f), 34f, 34f, paint)
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
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.textSize = 62f
        paint.setShadowLayer(3f, 0f, 2f, AndroidColor.argb(42, 11, 58, 110))
        canvas.drawText("EmoGarden", width / 2f, 79f, paint)
        paint.clearShadowLayer()

        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.textSize = 22f
        paint.letterSpacing = 0.16f
        paint.alpha = 225
        canvas.drawText("P H O T O B O O T H", width / 2f, 112f, paint)
        paint.letterSpacing = 0f

        paint.typeface = Typeface.create("serif", Typeface.ITALIC)
        paint.textSize = 23f
        paint.alpha = 205
        canvas.drawText(template.name, width / 2f, 145f, paint)
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
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.textSize = 27f
        canvas.drawText("EmoGarden  ·  $dateText", width / 2f, height - 62f, paint)
    }

    private fun drawVerticalStrip(
        canvas: Canvas,
        bitmaps: List<Bitmap>,
        shots: List<PhotoBoothShot>,
        paint: Paint
    ) {
        val count = bitmaps.size.coerceIn(1, 4)
        val margin = 68f
        val top = 178f
        val footer = 126f
        val gap = 18f
        val labelHeight = 42f
        val availableHeight = canvas.height - top - footer - gap * (count - 1)
        val photoHeight = ((availableHeight - labelHeight * count) / count).coerceAtLeast(260f)
        val photoWidth = canvas.width - margin * 2
        var y = top

        repeat(count) { index ->
            val photoRect = RectF(margin, y, margin + photoWidth, y + photoHeight)
            drawPhotoCell(canvas, bitmaps[index], photoRect, paint)
            drawEmotionIcon(
                canvas = canvas,
                emotionId = shots[index].emotionId,
                centerX = canvas.width / 2f,
                centerY = photoRect.bottom + 22f,
                paint = paint
            )
            y += photoHeight + labelHeight + gap
        }
    }

    private fun drawGrid(
        canvas: Canvas,
        bitmaps: List<Bitmap>,
        shots: List<PhotoBoothShot>,
        paint: Paint
    ) {
        val margin = 68f
        val top = 178f
        val gap = 22f
        val labelHeight = 48f
        val cellWidth = (canvas.width - margin * 2 - gap) / 2f
        val photoHeight = (canvas.height - top - 134f - gap - labelHeight * 2) / 2f

        bitmaps.take(4).forEachIndexed { index, bitmap ->
            val row = index / 2
            val col = index % 2
            val left = margin + col * (cellWidth + gap)
            val topY = top + row * (photoHeight + labelHeight + gap)
            val photoRect = RectF(left, topY, left + cellWidth, topY + photoHeight)
            drawPhotoCell(canvas, bitmap, photoRect, paint)
            drawEmotionIcon(
                canvas = canvas,
                emotionId = shots[index].emotionId,
                centerX = photoRect.centerX(),
                centerY = photoRect.bottom + 24f,
                paint = paint
            )
        }
    }

    private fun drawPhotoCell(canvas: Canvas, bitmap: Bitmap, rect: RectF, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.setShadowLayer(10f, 0f, 5f, AndroidColor.argb(42, 25, 80, 112))
        paint.color = AndroidColor.WHITE
        canvas.drawRoundRect(RectF(rect.left - 8f, rect.top - 8f, rect.right + 8f, rect.bottom + 8f), 28f, 28f, paint)
        paint.clearShadowLayer()

        val path = Path().apply { addRoundRect(rect, 22f, 22f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        val src = centerCropSource(bitmap, rect.width() / rect.height())
        canvas.drawBitmap(bitmap, src, rect, paint)
        canvas.restore()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = AndroidColor.argb(235, 255, 255, 255)
        canvas.drawRoundRect(rect, 22f, 22f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawEmotionIcon(
        canvas: Canvas,
        emotionId: String,
        centerX: Float,
        centerY: Float,
        paint: Paint
    ) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = AndroidColor.argb(220, 255, 255, 255)
        canvas.drawRoundRect(
            RectF(centerX - 28f, centerY - 22f, centerX + 28f, centerY + 22f),
            24f,
            24f,
            paint
        )
        val faceColor = when (emotionId) {
            "angry" -> AndroidColor.rgb(255, 138, 53)
            "fear" -> AndroidColor.rgb(134, 182, 255)
            "disgust" -> AndroidColor.rgb(168, 217, 107)
            "sad" -> AndroidColor.rgb(255, 217, 104)
            "surprise" -> AndroidColor.rgb(255, 209, 91)
            else -> AndroidColor.rgb(255, 214, 77)
        }
        val dark = AndroidColor.rgb(39, 48, 68)
        paint.color = faceColor
        canvas.drawCircle(centerX, centerY, 17f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.6f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = dark
        val leftEye = centerX - 6f
        val rightEye = centerX + 6f
        val eyeY = centerY - 4f
        when (emotionId) {
            "happy" -> {
                canvas.drawArc(RectF(leftEye - 3.5f, eyeY - 2f, leftEye + 3.5f, eyeY + 3f), 200f, 140f, false, paint)
                canvas.drawArc(RectF(rightEye - 3.5f, eyeY - 2f, rightEye + 3.5f, eyeY + 3f), 200f, 140f, false, paint)
                canvas.drawArc(RectF(centerX - 8f, centerY, centerX + 8f, centerY + 10f), 18f, 144f, false, paint)
            }
            "sad" -> {
                canvas.drawCircle(leftEye, eyeY, 1.8f, paint)
                canvas.drawCircle(rightEye, eyeY, 1.8f, paint)
                canvas.drawArc(RectF(centerX - 7f, centerY + 5f, centerX + 7f, centerY + 13f), 200f, 140f, false, paint)
                paint.style = Paint.Style.FILL
                paint.color = AndroidColor.rgb(70, 191, 244)
                canvas.drawOval(RectF(rightEye + 3f, eyeY + 2f, rightEye + 7f, eyeY + 9f), paint)
            }
            "angry" -> {
                canvas.drawLine(leftEye - 4f, eyeY - 4f, leftEye + 3f, eyeY - 1f, paint)
                canvas.drawLine(rightEye + 4f, eyeY - 4f, rightEye - 3f, eyeY - 1f, paint)
                canvas.drawCircle(leftEye, eyeY + 2f, 1.6f, paint)
                canvas.drawCircle(rightEye, eyeY + 2f, 1.6f, paint)
                canvas.drawArc(RectF(centerX - 7f, centerY + 5f, centerX + 7f, centerY + 13f), 200f, 140f, false, paint)
            }
            "fear", "surprise" -> {
                canvas.drawCircle(leftEye, eyeY, 2f, paint)
                canvas.drawCircle(rightEye, eyeY, 2f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawOval(RectF(centerX - 3f, centerY + 3f, centerX + 3f, centerY + 11f), paint)
            }
            "disgust" -> {
                canvas.drawLine(leftEye - 3f, eyeY - 2f, leftEye + 3f, eyeY + 2f, paint)
                canvas.drawLine(rightEye - 3f, eyeY + 2f, rightEye + 3f, eyeY - 2f, paint)
                canvas.drawArc(RectF(centerX - 7f, centerY + 5f, centerX + 7f, centerY + 13f), 200f, 140f, false, paint)
            }
            else -> {
                canvas.drawCircle(leftEye, eyeY, 1.8f, paint)
                canvas.drawCircle(rightEye, eyeY, 1.8f, paint)
                canvas.drawArc(RectF(centerX - 8f, centerY, centerX + 8f, centerY + 10f), 18f, 144f, false, paint)
            }
        }
        paint.style = Paint.Style.FILL
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
            "flower_booth" -> drawFlowerGardenDecor(canvas, width, height, paint)
            "garden_blue" -> drawGardenDecor(canvas, width, height, paint)
            else -> drawMinimalDecor(canvas, width, height, paint)
        }
    }

    private fun drawSoftBackdrop(
        canvas: Canvas,
        template: PhotoBoothFrameTemplate,
        width: Int,
        height: Int,
        paint: Paint
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 24f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = if (template.id == "starry_night") {
            AndroidColor.argb(42, 124, 200, 255)
        } else {
            AndroidColor.argb(70, 255, 255, 255)
        }
        canvas.drawLine(58f, 194f, width - 58f, 194f, paint)
        canvas.drawLine(58f, height - 142f, width - 58f, height - 142f, paint)

        paint.strokeWidth = 4f
        paint.color = if (template.id == "starry_night") {
            AndroidColor.argb(92, 124, 200, 255)
        } else {
            AndroidColor.argb(104, 255, 255, 255)
        }
        canvas.drawLine(74f, 166f, width - 74f, 166f, paint)
        canvas.drawLine(74f, height - 116f, width - 74f, height - 116f, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private fun drawGardenDecor(canvas: Canvas, width: Int, height: Int, paint: Paint) {
        paint.style = Paint.Style.FILL
        val leaf = AndroidColor.rgb(97, 177, 124)
        val flower = AndroidColor.rgb(255, 214, 102)
        val blue = AndroidColor.rgb(124, 200, 255)
        val points = listOf(82f to height - 106f, width - 92f to height - 110f)
        points.forEachIndexed { index, (x, y) ->
            drawLeaf(canvas, x - 18f, y + 5f, leaf, -24f, paint)
            drawLeaf(canvas, x + 18f, y - 10f, leaf, 32f, paint)
            drawDaisy(canvas, x, y, if (index % 2 == 0) flower else blue, 10f, paint)
        }
        drawLeaf(canvas, 52f, 236f, leaf, 28f, paint)
        drawLeaf(canvas, width - 54f, 308f, leaf, -26f, paint)
        drawLeaf(canvas, 48f, height * 0.54f, AndroidColor.rgb(134, 205, 157), 20f, paint)
        drawLeaf(canvas, width - 48f, height * 0.68f, AndroidColor.rgb(134, 205, 157), -18f, paint)
    }

    private fun drawFlowerGardenDecor(canvas: Canvas, width: Int, height: Int, paint: Paint) {
        val leaf = AndroidColor.rgb(91, 171, 115)
        val blue = AndroidColor.rgb(124, 200, 255)
        val yellow = AndroidColor.rgb(255, 214, 102)
        val pink = AndroidColor.rgb(255, 176, 197)
        listOf(
            Triple(78f, height - 106f, blue),
            Triple(130f, height - 88f, yellow),
            Triple(width - 84f, height - 108f, pink),
            Triple(width - 138f, height - 88f, blue)
        ).forEachIndexed { index, (x, y, color) ->
            drawLeaf(canvas, x - 15f, y + 7f, leaf, if (index % 2 == 0) -28f else 28f, paint)
            drawLeaf(canvas, x + 16f, y - 9f, leaf, if (index % 2 == 0) 34f else -34f, paint)
            drawDaisy(canvas, x, y, color, 9f, paint)
        }
        drawDaisy(canvas, 54f, 236f, pink, 7f, paint)
        drawDaisy(canvas, width - 54f, 302f, yellow, 7f, paint)
        drawLeaf(canvas, 45f, height * 0.61f, leaf, 18f, paint)
        drawLeaf(canvas, width - 46f, height * 0.48f, leaf, -18f, paint)
    }

    private fun drawRainbowDecor(canvas: Canvas, width: Int, height: Int, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 11f
        val rect = RectF(width - 202f, 48f, width - 48f, 202f)
        listOf(
            AndroidColor.rgb(255, 124, 124),
            AndroidColor.rgb(255, 214, 102),
            AndroidColor.rgb(93, 201, 129),
            AndroidColor.rgb(112, 190, 255)
        ).forEachIndexed { index, color ->
            paint.color = color
            val inset = index * 13f
            canvas.drawArc(RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset), 200f, 140f, false, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(74f, height - 92f, 21f, paint)
        canvas.drawCircle(102f, height - 92f, 27f, paint)
        canvas.drawCircle(130f, height - 92f, 19f, paint)
        canvas.drawCircle(width - 148f, height - 94f, 18f, paint)
        canvas.drawCircle(width - 122f, height - 94f, 24f, paint)
        canvas.drawCircle(width - 96f, height - 94f, 17f, paint)
        paint.color = AndroidColor.argb(118, 255, 255, 255)
        canvas.drawCircle(54f, 274f, 16f, paint)
        canvas.drawCircle(width - 52f, height * 0.57f, 13f, paint)
    }

    private fun drawMiniFaces(canvas: Canvas, width: Int, height: Int, paint: Paint) {
        val faces = listOf(
            Triple(76f, height - 96f, AndroidColor.rgb(255, 214, 77)),
            Triple(width - 82f, height - 100f, AndroidColor.rgb(134, 182, 255)),
            Triple(48f, 254f, AndroidColor.rgb(255, 138, 53)),
            Triple(width - 48f, 316f, AndroidColor.rgb(168, 217, 107))
        )
        faces.forEach { (x, y, color) ->
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawCircle(x, y, 25f, paint)
            paint.color = AndroidColor.rgb(36, 47, 64)
            canvas.drawCircle(x - 9f, y - 6f, 3f, paint)
            canvas.drawCircle(x + 9f, y - 6f, 3f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawArc(RectF(x - 11f, y, x + 11f, y + 16f), 15f, 150f, false, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawStars(canvas: Canvas, width: Int, height: Int, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = AndroidColor.argb(238, 255, 236, 165)
        canvas.drawCircle(width - 82f, 110f, 30f, paint)
        paint.color = AndroidColor.rgb(23, 42, 66)
        canvas.drawCircle(width - 68f, 98f, 30f, paint)
        paint.color = AndroidColor.rgb(255, 225, 112)
        val stars = listOf(
            70f to 98f,
            110f to 146f,
            width - 142f to 176f,
            54f to height * 0.44f,
            width - 54f to height * 0.61f,
            98f to height - 94f,
            width - 112f to height - 96f
        )
        stars.forEach { (x, y) ->
            canvas.drawCircle(x, y, 5f, paint)
            canvas.drawRect(x - 2f, y - 13f, x + 2f, y + 13f, paint)
            canvas.drawRect(x - 13f, y - 2f, x + 13f, y + 2f, paint)
        }
    }

    private fun drawMinimalDecor(canvas: Canvas, width: Int, height: Int, paint: Paint) {
        paint.style = Paint.Style.FILL
        listOf(
            Triple(68f, height - 94f, AndroidColor.rgb(124, 200, 255)),
            Triple(width - 70f, height - 94f, AndroidColor.rgb(124, 200, 255)),
            Triple(width - 112f, height - 74f, AndroidColor.rgb(255, 226, 138)),
            Triple(112f, height - 74f, AndroidColor.rgb(255, 226, 138)),
            Triple(50f, 246f, AndroidColor.rgb(190, 226, 255)),
            Triple(width - 50f, height * 0.58f, AndroidColor.rgb(190, 226, 255))
        ).forEach { (x, y, color) ->
            paint.color = color
            canvas.drawCircle(x, y, if (y < height - 100f) 9f else 13f, paint)
        }
    }

    private fun drawLeaf(canvas: Canvas, x: Float, y: Float, color: Int, rotation: Float, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.save()
        canvas.rotate(rotation, x, y)
        canvas.drawOval(RectF(x - 18f, y - 9f, x + 18f, y + 9f), paint)
        canvas.restore()
    }

    private fun drawDaisy(canvas: Canvas, x: Float, y: Float, color: Int, petalRadius: Float, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = color
        repeat(5) { petal ->
            val angle = Math.toRadians((petal * 72).toDouble())
            val cx = x + kotlin.math.cos(angle).toFloat() * petalRadius * 1.65f
            val cy = y + kotlin.math.sin(angle).toFloat() * petalRadius * 1.65f
            canvas.drawCircle(cx, cy, petalRadius, paint)
        }
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(x, y, petalRadius * 0.78f, paint)
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
