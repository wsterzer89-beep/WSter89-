package de.dokumentenpilot.app

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter

internal object FamilienTestQr {
    fun erstellen(code: String): Bitmap {
        require(FamilienTestEinladungFormat.lesen(code) != null)
        val matrix = QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 512, 512)
        val pixels = IntArray(512 * 512) { i -> if (matrix[i % 512, i / 512]) 0xff000000.toInt() else 0xffffffff.toInt() }
        return Bitmap.createBitmap(pixels, 512, 512, Bitmap.Config.ARGB_8888)
    }

    fun lesen(bitmap: Bitmap): String? {
        if (bitmap.width !in 1..2048 || bitmap.height !in 1..2048) return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return try {
            val code = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(
                RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
            code.takeIf { FamilienTestEinladungFormat.lesen(it) != null }
        } catch (_: com.google.zxing.ReaderException) { null }
    }
}
