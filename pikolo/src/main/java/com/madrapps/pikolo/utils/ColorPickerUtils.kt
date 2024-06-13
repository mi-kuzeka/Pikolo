package com.madrapps.pikolo.utils

import android.graphics.Color
import androidx.core.graphics.ColorUtils

object ColorPickerUtils {
    fun getBorderColor(color: Int): Int {
        val colorDarkness = getColorDarkness(color).toFloat()
        return if (colorDarkness >= 0.5) {
            ColorUtils.blendARGB(color, Color.WHITE, colorDarkness)
        } else {
            ColorUtils.blendARGB(color, Color.BLACK, 0.75f - colorDarkness)
        }
    }

    private fun getColorDarkness(color: Int): Double {
        // The formula is from https://en.wikipedia.org/wiki/Luma_%28video%29
        return 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
    }
}