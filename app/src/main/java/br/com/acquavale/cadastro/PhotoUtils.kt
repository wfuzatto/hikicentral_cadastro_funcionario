package br.com.acquavale.cadastro

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

object PhotoUtils {
    fun photoBytes(file: File): ByteArray {
        require(file.exists() && file.length() > 0) { "Fotografia vazia. Tire outra foto." }
        val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, info)
        require(info.outWidth > 0 && info.outHeight > 0) { "Arquivo de foto inválido." }
        var sample = 1
        while (info.outWidth / sample > 1200 || info.outHeight / sample > 1200) sample *= 2
        val raw = BitmapFactory.decodeFile(file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalStateException("Não foi possível abrir a fotografia.")
        val orientation = ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val angle = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val rotated = if (angle == 0f) raw else Bitmap.createBitmap(
            raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(angle) }, true
        ).also { raw.recycle() }
        val maxEdge = maxOf(rotated.width, rotated.height)
        val scale = minOf(1f, 960f / maxEdge)
        val target = if (scale < 1f) Bitmap.createScaledBitmap(
            rotated, (rotated.width * scale).toInt().coerceAtLeast(1),
            (rotated.height * scale).toInt().coerceAtLeast(1), true
        ).also { rotated.recycle() } else rotated
        try {
            var quality = 90
            var result: ByteArray
            do {
                val stream = ByteArrayOutputStream()
                target.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                result = stream.toByteArray()
                quality -= 10
            } while (result.size > 195_000 && quality >= 40)
            require(result.size >= 10_000) { "Foto muito pequena para reconhecimento facial. Refaça com boa luz." }
            require(result.size <= 300_000) { "Foto muito grande. Refaça mais próxima do rosto." }
            return result
        } finally {
            target.recycle()
        }
    }

    fun preview(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 600) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
