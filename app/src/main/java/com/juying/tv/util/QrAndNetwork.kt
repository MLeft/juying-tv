package com.juying.tv.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface

fun generateQrBitmap(text: String, size: Int = 512): Bitmap? {
    return runCatching {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    }.getOrNull()
}

fun localIpv4Address(): String? {
    val candidates = mutableListOf<Pair<String, Inet4Address>>()
    runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { nic ->
            if (!nic.isUp || nic.isLoopback) return@forEach
            nic.inetAddresses.toList().filterIsInstance<Inet4Address>().forEach { address ->
                if (!address.isLoopbackAddress && address.isSiteLocalAddress) {
                    candidates += nic.name to address
                }
            }
        }
    }
    candidates.sortBy { (name, _) ->
        when {
            name.startsWith("wlan") -> 0
            name.startsWith("eth") -> 1
            name.startsWith("en") -> 2
            else -> 3
        }
    }
    return candidates.firstOrNull()?.second?.hostAddress
}
