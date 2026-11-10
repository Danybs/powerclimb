package com.powerclimb.ui

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.powerclimb.R
import com.powerclimb.data.ClimbPowerState
import com.powerclimb.data.PowerZoneProfile
import io.hammerhead.karooext.models.ViewConfig

object ClimbPowerRemoteViews {

    private val dotIds = intArrayOf(
        R.id.field_dot_1,
        R.id.field_dot_2,
        R.id.field_dot_3,
        R.id.field_dot_4,
        R.id.field_dot_5,
        R.id.field_dot_6,
        R.id.field_dot_7,
        R.id.field_dot_8,
        R.id.field_dot_9,
        R.id.field_dot_10,
    )

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
        // ball-and-dots arrangement.
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
            valueSize(config, valueText, compact),
        )

        views.setTextViewText(R.id.field_ball_value, valueText)
        views.setTextColor(R.id.field_ball_value, if (hasValue) primary else secondary)
        views.setTextViewTextSize(
            R.id.field_ball_value,
            TypedValue.COMPLEX_UNIT_SP,
            ballValueSize(config, compact),
        )

        val visual = mode == ClimbFieldMode.VISUAL
        views.setViewVisibility(R.id.field_value, if (visual) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.field_visual, if (visual) View.VISIBLE else View.GONE)

        views.setInt(
            R.id.field_ring,
            "setColorFilter",
            zoneColor ?: context.getColor(R.color.pc_ring_neutral),
        )

        val zoneCount = zones.zones.size.coerceAtMost(dotIds.size)
        dotIds.forEachIndexed { dotIndex, dotId ->
            if (dotIndex < zoneCount) {
                views.setViewVisibility(dotId, View.VISIBLE)
                views.setInt(dotId, "setColorFilter", PowerZonePalette.colorFor(dotIndex))
                val alpha = when {
                    zone == null -> 0.22f
                    zone.index == dotIndex -> 1f
                    else -> 0.30f
                }
                views.setFloat(dotId, "setAlpha", alpha)
            } else {
                views.setViewVisibility(dotId, View.GONE)
            }
        }

        return views
    }

    private fun valueSize(config: ViewConfig, value: String, compact: Boolean): Float {
        // The host's textSize is the size the native fields use for this slot.
        // Keeping it (instead of an artificial compact cap) makes the value
        // fill the available height like 3S POWER.
        val base = config.textSize
            .takeIf { it > 0 }
            ?.toFloat()
            ?: if (compact) 38f else 64f
        val adjusted = if (value.length >= 4) base * 0.82f else base
        return adjusted.coerceIn(
            minimumValue = if (compact) 24f else 36f,
            maximumValue = if (compact) 80f else 96f,
        )
    }

    private fun ballValueSize(config: ViewConfig, compact: Boolean): Float {
        val base = config.textSize
            .takeIf { it > 0 }
            ?.toFloat()
            ?: if (compact) 38f else 64f
        return (base * 0.56f).coerceIn(
            minimumValue = 20f,
            maximumValue = if (compact) 30f else 42f,
        )
    }
}
