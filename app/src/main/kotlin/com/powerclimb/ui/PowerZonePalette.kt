package com.powerclimb.ui

import android.graphics.Color

/**
 * The seven native Karoo power-zone colors.
 *
 * Zone count and boundaries come from the device. If a device ever reports
 * more zones than this palette contains, colors repeat in order; the contrast
 * color is still calculated from the actual color.
 */
object PowerZonePalette {

    private val colors = intArrayOf(
        0xFF60EEB2.toInt(), // Z1
        0xFF00B988.toInt(), // Z2
        0xFFFFF500.toInt(), // Z3
        0xFFFB8C65.toInt(), // Z4
        0xFFFE581F.toInt(), // Z5
        0xFFD60404.toInt(), // Z6
        0xFFB700A2.toInt(), // Z7
    )

    fun colorFor(zoneIndex: Int): Int {
        return colors[zoneIndex.mod(colors.size)]
    }

    fun contrastForColor(color: Int): Int {
        val red = Color.red(color) / 255f
        val green = Color.green(color) / 255f
        val blue = Color.blue(color) / 255f
        val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
        return if (luminance > 0.5f) Color.BLACK else Color.WHITE
    }

    fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
