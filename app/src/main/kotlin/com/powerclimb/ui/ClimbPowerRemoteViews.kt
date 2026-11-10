package com.powerclimb.ui

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.widget.RemoteViews
import com.powerclimb.R
import com.powerclimb.data.ClimbPowerState
import com.powerclimb.data.PowerZoneProfile
import io.hammerhead.karooext.models.ViewConfig

object ClimbPowerRemoteViews {

    private val zoneBackgrounds = intArrayOf(
        R.drawable.pc_zone_bg_1,
        R.drawable.pc_zone_bg_2,
        R.drawable.pc_zone_bg_3,
        R.drawable.pc_zone_bg_4,
        R.drawable.pc_zone_bg_5,
        R.drawable.pc_zone_bg_6,
        R.drawable.pc_zone_bg_7,
    )

    fun isCompact(config: ViewConfig): Boolean {
        // A single-row field is 20 grid rows high; a 2x2 field is 40. Using
        // the grid height keeps a 478x228 2x1 field on the compact layout
        // instead of the much larger 2x2 presentation.
        return config.gridSize.second < 30 || config.viewSize.first < 280
    }

    fun isWide(config: ViewConfig): Boolean {
        // Total grid width is 60 columns. A 2-column-wide field spans at least
        // 50. The picker preview is 240x128 and also needs the horizontal
        // presentation.
        return config.gridSize.first >= 50 ||
            config.viewSize.first >= config.viewSize.second * 1.5f
    }

    fun build(
        context: Context,
        state: ClimbPowerState,
        zones: PowerZoneProfile,
        mode: ClimbFieldMode,
        config: ViewConfig,
        compact: Boolean,
        wide: Boolean,
    ): RemoteViews {
        val layout = when {
            compact && wide -> R.layout.powerclimb_field_wide
            compact -> R.layout.powerclimb_field_compact
            else -> R.layout.powerclimb_field
        }
        val views = RemoteViews(context.packageName, layout)

        val average = state.averageWatts
        val hasValue = average != null
        val valueText = average?.toString() ?: context.getString(R.string.field_empty)
        val zone = zones.zoneFor(average)
        val onZoneBackground = mode == ClimbFieldMode.ZONE && zone != null

        val themePrimary = context.getColor(R.color.pc_text_primary)
        val themeSecondary = context.getColor(R.color.pc_text_secondary)
        val zoneColor = zone?.let { PowerZonePalette.colorFor(it.index) }
        val primary = if (onZoneBackground) {
            PowerZonePalette.contrastForColor(zoneColor!!)
        } else {
            themePrimary
        }
        val secondary = if (onZoneBackground) {
            PowerZonePalette.withAlpha(primary, 0xCC)
        } else {
            themeSecondary
        }

        if (onZoneBackground) {
            val background = zoneBackgrounds[zone!!.index.mod(zoneBackgrounds.size)]
            views.setInt(R.id.field_root, "setBackgroundResource", background)
        } else {
            views.setInt(R.id.field_root, "setBackgroundColor", Color.TRANSPARENT)
        }

        views.setTextViewText(R.id.field_label, context.getString(R.string.field_label))
        views.setTextColor(R.id.field_label, secondary)
        views.setInt(R.id.field_icon, "setColorFilter", primary)

        views.setTextViewText(R.id.field_value, valueText)
        views.setTextColor(R.id.field_value, if (hasValue) primary else secondary)
        views.setTextViewTextSize(
            R.id.field_value,
            TypedValue.COMPLEX_UNIT_SP,
            valueSize(context, config, valueText, compact),
        )

        return views
    }

    private fun valueSize(
        context: Context,
        config: ViewConfig,
        value: String,
        compact: Boolean,
    ): Float {
        val metrics = context.resources.displayMetrics
        val density = metrics.density.takeIf { it > 0f } ?: 1f
        val scaledDensity = TypedValue
            .applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, metrics)
            .takeIf { it > 0f }
            ?: density

        // Empty values use the width of a typical three-digit value so the
        // placeholder does not suddenly become huge.
        val digits = value.count { it.isDigit() }.takeIf { it > 0 } ?: 3

        return fittedValueSp(
            viewWidthPx = config.viewSize.first,
            viewHeightPx = config.viewSize.second,
            hostTextSize = config.textSize.takeIf { it > 0 }?.toFloat() ?: 64f,
            density = density,
            scaledDensity = scaledDensity,
            digits = digits,
            horizontalPaddingDp = if (compact) 8f else 10f,
            verticalPaddingDp = if (compact) 2f else 3f,
        )
    }

    /**
     * The numeric font (Hammerhead Relative) is monospaced: every digit
     * advances exactly 0.5 em and the ink of a digit is 0.72 em high. Using
     * those metrics lets us fill the slot without clipping, for every field
     * size, instead of relying on one fixed text size.
     */
    internal fun fittedValueSp(
        viewWidthPx: Int,
        viewHeightPx: Int,
        hostTextSize: Float,
        density: Float,
        scaledDensity: Float,
        digits: Int,
        horizontalPaddingDp: Float,
        verticalPaddingDp: Float,
    ): Float {
        val safeDensity = density.takeIf { it > 0f } ?: 1f
        val safeScaledDensity = scaledDensity.takeIf { it > 0f } ?: safeDensity
        val safeDigits = digits.coerceAtLeast(1)

        // Digits advance 0.5 em; letterSpacing is -0.04 em between glyphs.
        val widthEm = 0.5f * safeDigits - 0.04f * (safeDigits - 1)

        val widthPx = (viewWidthPx - 2f * horizontalPaddingDp * safeDensity)
            .coerceAtLeast(1f)
        val heightPx = (viewHeightPx - 2f * verticalPaddingDp * safeDensity)
            .coerceAtLeast(1f)

        val widthLimitSp = widthPx / (widthEm * safeScaledDensity)
        val heightLimitSp = heightPx / (0.78f * safeScaledDensity)

        return minOf(hostTextSize, widthLimitSp, heightLimitSp)
            .coerceIn(18f, 200f)
    }
}
