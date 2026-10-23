package com.parlor.core.data

import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import android.os.Build
import com.parlor.core.model.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.roundToInt

/** Local image conversion only. Source URIs, filenames, metadata and pixels are never logged. */
object PhotoCodec {
    const val MAX_INPUT_BYTES = 32 * 1024 * 1024
    private const val MAX_SOURCE_PIXELS = 64_000_000L

    /** Called once at process startup, before any picker operation, for copies interrupted by process death. */
    fun clearAbandonedInputs(cache: File) {
        cache.listFiles()?.filter { it.name.startsWith("photo-") && it.extension == "input" }?.forEach { it.delete() }
    }

    fun normalize(input: InputStream, cache: File): PuzzlePhoto {
        val source = File.createTempFile("photo-", ".input", cache)
        try {
            source.outputStream().use { output ->
                val buffer = ByteArray(16_384); var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count; require(total <= MAX_INPUT_BYTES) { "Photo is too large" }
                    output.write(buffer, 0, count)
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            require(bounds.outWidth in 1..16_384 && bounds.outHeight in 1..16_384 &&
                bounds.outWidth.toLong() * bounds.outHeight <= MAX_SOURCE_PIXELS) { "Unsupported photo dimensions" }
            val decoded = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                    require(info.size.width.toLong() * info.size.height <= MAX_SOURCE_PIXELS)
                    val scale = minOf(1f, 2048f / maxOf(info.size.width, info.size.height))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1))
                    decoder.setOnPartialImageListener { false }
                }
            } else {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                    inSampleSize = sample
                }
                val bitmap = requireNotNull(BitmapFactory.decodeFile(source.path, options))
                try {
                    val orientation = ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    val transform = Matrix().apply {
                        when (orientation) {
                            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                            ExifInterface.ORIENTATION_TRANSPOSE -> { setScale(-1f, 1f); postRotate(270f) }
                            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                            ExifInterface.ORIENTATION_TRANSVERSE -> { setScale(-1f, 1f); postRotate(90f) }
                            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                        }
                    }
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true).also {
                        if (it !== bitmap) bitmap.recycle()
                    }
                } catch (failure: Exception) { bitmap.recycle(); throw failure }
            }
            try {
                val crop = minOf(decoded.width, decoded.height)
                val side = minOf(crop, PhotoLimits.SIDE)
                val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
                try {
                    val left = (decoded.width - crop) / 2; val top = (decoded.height - crop) / 2
                    Canvas(square).apply {
                        drawColor(Color.WHITE)
                        drawBitmap(decoded, Rect(left, top, left + crop, top + crop), Rect(0, 0, side, side), Paint(Paint.FILTER_BITMAP_FLAG))
                    }
                    for (quality in listOf(90, 82, 74, 66, 58, 50)) {
                        val output = ByteArrayOutputStream()
                        check(square.compress(Bitmap.CompressFormat.JPEG, quality, output))
                        // Android may emit an ICC APP2 segment even for this fresh sRGB bitmap.
                        val bytes = withoutMetadata(output.toByteArray())
                        if (bytes.size <= PhotoLimits.IMAGE_BYTES) return payload(bytes).also { validate(it) }
                    }
                    error("Photo is too detailed to save")
                } finally { square.recycle() }
            } finally { decoded.recycle() }
        } finally { source.delete() }
    }

    fun payload(bytes: ByteArray): PuzzlePhoto = PuzzlePhoto(digest(bytes), Base64.getEncoder().encodeToString(bytes).chunked(PhotoLimits.CHUNK))

    fun bytes(photo: PuzzlePhoto): ByteArray {
        require(PhotoLimits.validId(photo.id) && photo.jpeg.size in 1..PhotoLimits.MAX_CHUNKS)
        require(photo.jpeg.dropLast(1).all { it.length == PhotoLimits.CHUNK })
        require(photo.jpeg.last().length in 4..PhotoLimits.CHUNK && photo.jpeg.last().length % 4 == 0)
        val encoded = photo.jpeg.joinToString("")
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size in 1..PhotoLimits.IMAGE_BYTES && Base64.getEncoder().encodeToString(bytes) == encoded)
        require(digest(bytes) == photo.id) { "Photo checksum does not match" }
        return bytes
    }

    fun validate(photo: PuzzlePhoto): ByteArray = bytes(photo).also { bytes ->
        jpegStructure(bytes)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outMimeType == "image/jpeg" && bounds.outWidth in 1..PhotoLimits.SIDE && bounds.outWidth == bounds.outHeight)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Photo cannot be decoded" }
        bitmap.recycle()
    }

    private fun withoutMetadata(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(bytes.size)
        output.write(bytes, 0, 2)
        var offset = 2
        while (offset < bytes.size) {
            val start = offset
            require(bytes[offset++].toInt() and 255 == 255 && offset < bytes.size)
            val marker = bytes[offset++].toInt() and 255
            if (marker == 218) { output.write(bytes, start, bytes.size - start); return output.toByteArray() }
            require(offset + 2 <= bytes.size)
            val length = (bytes[offset].toInt() and 255) * 256 + (bytes[offset + 1].toInt() and 255)
            require(length >= 2 && length <= bytes.size - offset)
            offset += length
            if (marker !in 225..239 && marker != 254) output.write(bytes, start, offset - start)
        }
        error("Incomplete encoded photo")
    }

    /** Only the baseline JPEG emitted above: reject metadata, trailing bytes and incomplete segments. */
    private fun jpegStructure(bytes: ByteArray) {
        fun at(i: Int) = bytes[i].toInt() and 255
        require(bytes.size >= 4 && at(0) == 255 && at(1) == 216)
        var offset = 2; var scan = false; var dimensions = false
        while (offset < bytes.size) {
            if (scan && at(offset) != 255) { offset++; continue }
            require(at(offset++) == 255)
            while (offset < bytes.size && at(offset) == 255) offset++
            require(offset < bytes.size)
            val marker = at(offset++)
            if (scan && (marker == 0 || marker in 208..215)) continue
            if (marker == 217) { require(scan && dimensions && offset == bytes.size); return }
            require(!scan && marker in setOf(192, 196, 219, 221, 224, 218)) { "Photo contains unsupported metadata or encoding" }
            require(offset + 2 <= bytes.size)
            val length = at(offset) * 256 + at(offset + 1)
            require(length >= 2 && length <= bytes.size - offset)
            if (marker == 224) {
                require(length == 16 && bytes.copyOfRange(offset + 2, offset + 7).contentEquals(byteArrayOf(74, 70, 73, 70, 0)) &&
                    at(offset + 14) == 0 && at(offset + 15) == 0) { "Unsupported photo metadata" }
            }
            if (marker == 192) { require(!dimensions); dimensions = true }
            if (marker == 218) { require(dimensions); scan = true }
            offset += length
        }
        error("Incomplete photo")
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
