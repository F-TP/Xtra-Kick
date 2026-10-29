package com.xtrakick.app.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.google.android.material.color.MaterialColors
import java.util.Locale

interface ActiveTagsAdapter {
    var activeTags: Set<String>
}

object TagFilterHelper {

    private val SEPARATOR_REGEX = Regex("[\\s_\\-]+")

    private fun cleanTag(str: String): String =
        str.trim().lowercase(Locale.ROOT).replace(SEPARATOR_REGEX, "")

    fun isTagActive(tag: String?, activeTags: Collection<String>?): Boolean {
        if (tag.isNullOrBlank() || activeTags.isNullOrEmpty()) return false
        val clean = cleanTag(tag)
        if (clean.isEmpty()) return false
        return activeTags.any { active ->
            val cleanActive = cleanTag(active)
            cleanActive.isNotEmpty() && (
                clean == cleanActive ||
                (cleanActive.length >= 3 && clean.contains(cleanActive)) ||
                (clean.length >= 3 && cleanActive.contains(clean))
            )
        }
    }

    fun applyTagStyle(text: TextView, tag: String, activeTags: Collection<String>?) {
        val context = text.context
        val isHighlighted = isTagActive(tag, activeTags)
        val density = context.resources.displayMetrics.density
        if (isHighlighted) {
            val hPad = (8 * density).toInt()
            val vPad = (2 * density).toInt()
            val cornerRadius = 12 * density
            val primaryColor = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.BLUE)
            val onPrimaryColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setCornerRadius(cornerRadius)
                setColor(primaryColor)
            }
            text.background = bg
            text.setPadding(hPad, vPad, hPad, vPad)
            text.setTextColor(onPrimaryColor)
            text.setTypeface(text.typeface, Typeface.BOLD)
        } else {
            text.background = null
            val padding = (5 * density).toInt()
            text.setPadding(padding, 0, padding, 0)
            val typedArray = context.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.textAppearanceBodyMedium))
            try {
                TextViewCompat.setTextAppearance(text, typedArray.getResourceId(0, 0))
            } finally {
                typedArray.recycle()
            }
            text.setTypeface(null, Typeface.NORMAL)
        }
    }
}
