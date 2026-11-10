package com.powerclimb.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
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
        views.setTextColor(R.id.field_label, primary)
        views.setInt(R.id.field_icon, "setColorFilter", primary)

        val valueSp = valueSize(context, config, valueText, compact)
        views.setTextViewText(R.id.field_value, valueText)
        views.setTextColor(R.id.field_value, if (hasValue) primary else secondary)
        views.setTextViewTextSize(
            R.id.field_value,
            TypedValue.COMPLEX_UNIT_SP,
            valueSp,
        )
        setValueBottomMargin(views, valueSp, context)

        return views
    }

    /**
     * Native numeric fields sit almost flush with the bottom of the slot. A
     * bottom-gravity [TextView] leaves the font descent as a gap below the
     * digits, so move the value down by that descent (minus the digit's own
     * bottom overshoot). The root padding still provides the final margin.
     */
    private fun setValueBottomMargin(
        views: RemoteViews,
        valueSp: Float,
        context: Context,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val scaledDensity = TypedValue
            .applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, context.resources.displayMetrics)
            .takeIf { it > 0f }
            ?: 1f
        val paint = Paint().apply {
            typeface = Typeface.create("relative", Typeface.NORMAL)
            textSize = valueSp * scaledDensity
        }
        val digitBounds = Rect()
        paint.getTextBounds("0", 0, 1, digitBounds)
        val insetPx = (paint.fontMetrics.descent - digitBounds.bottom).coerceAtLeast(0f)

        views.setViewLayoutMargin(
            R.id.field_value,
            RemoteViews.MARGIN_BOTTOM,
            -insetPx,
            TypedValue.COMPLEX_UNIT_PX,
        )
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
            verticalPaddingDp = if (compact) 3f else 4f,
        )
    }

    /**
     * The numeric font (Hammerhead Relative) is monospaced: every digit
     * advances exactly 0.5 em and the ink of a digit is 0.72 em high. Using
     * those metrics lets us fill the slot without clipping, for every field
     * size, instead of relying on one fixed text size.
     *
     * The value spans the full width of the field, like the native numeric
     * fields; only the vertical padding reduces the available height. This
     * keeps [hostTextSize] as the size for three-digit values instead of
     * shrinking them for padding that belongs to the label row.
     */
    internal fun fittedValueSp(
        viewWidthPx: Int,
        viewHeightPx: Int,
        hostTextSize: Float,
        density: Float,
        scaledDensity: Float,
        digits: Int,
        verticalPaddingDp: Float,
    ): Float {
        val safeDensity = density.takeIf { it > 0f } ?: 1f
        val safeScaledDensity = scaledDensity.takeIf { it > 0f } ?: safeDensity
        val safeDigits = digits.coerceAtLeast(1)

        // Digits advance 0.5 em; letterSpacing is -0.04 em between glyphs.
        val widthEm = 0.5f * safeDigits - 0.04f * (safeDigits - 1)

        val widthPx = viewWidthPx.coerceAtLeast(1)
        val heightPx = (viewHeightPx - 2f * verticalPaddingDp * safeDensity)
            .coerceAtLeast(1f)

        val widthLimitSp = widthPx / (widthEm * safeScaledDensity)
        val heightLimitSp = heightPx / (0.78f * safeScaledDensity)

        return minOf(hostTextSize, widthLimitSp, heightLimitSp)
            .coerceIn(18f, 200f)
    }
}
