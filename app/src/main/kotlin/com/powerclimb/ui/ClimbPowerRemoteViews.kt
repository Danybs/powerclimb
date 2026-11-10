package com.powerclimb.ui

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.powerclimb.R
import com.powerclimb.data.ClimbPhase
import com.powerclimb.data.ClimbPowerState
import io.hammerhead.karooext.models.ViewConfig
import java.util.Locale

object ClimbPowerRemoteViews {

    fun isCompact(config: ViewConfig): Boolean {
        return config.viewSize.first < 280 || config.viewSize.second < 220
    }

    fun build(
        context: Context,
        state: ClimbPowerState,
        compact: Boolean,
    ): RemoteViews {
        val active = state.phase == ClimbPhase.ACTIVE
        val finished = state.phase == ClimbPhase.FINISHED
        val paused = active && state.ridePaused

        val valueText = when {
            active -> state.averageWatts?.toString() ?: "···"
            finished -> state.averageWatts?.toString() ?: "—"
            else -> "—"
        }
        val showUnit = (active || finished) && state.averageWatts != null

        val layout = if (compact) {
            R.layout.powerclimb_field_compact
        } else {
            R.layout.powerclimb_field
        }
        val views = RemoteViews(context.packageName, layout)

        views.setTextViewText(R.id.field_value, valueText)
        views.setTextViewTextSize(
            R.id.field_value,
            TypedValue.COMPLEX_UNIT_SP,
            valueSize(valueText.length, compact),
        )
        views.setTextColor(
            R.id.field_value,
            context.getColor(
                when {
                    active -> R.color.pc_white
                    finished -> R.color.pc_finished
                    else -> R.color.pc_muted
                },
            ),
        )
        views.setViewVisibility(
            R.id.field_unit,
            if (showUnit) View.VISIBLE else View.GONE,
        )

        views.setTextColor(
            R.id.field_label,
            context.getColor(
                when {
                    paused -> R.color.pc_pause
                    active -> R.color.pc_accent
                    finished -> R.color.pc_finished
                    else -> R.color.pc_dim
                },
            ),
        )

        val badge = state.climbNumber
        if (active && badge != null && badge > 0) {
            views.setTextViewText(R.id.field_badge, "#$badge")
            views.setTextColor(
                R.id.field_badge,
                context.getColor(if (paused) R.color.pc_pause else R.color.pc_accent),
            )
            views.setViewVisibility(R.id.field_badge, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.field_badge, View.GONE)
        }

        val subText = when {
            paused -> "${context.getString(R.string.field_paused)} · ${formatDuration(state.elapsedSeconds)}"
            active && state.averageWatts == null && state.elapsedSeconds == 0 -> {
                context.getString(R.string.field_taking_data)
            }

            active && state.currentWatts != null -> {
                "${context.getString(R.string.field_actual, state.currentWatts)} · " +
                    formatDuration(state.elapsedSeconds)
            }

            active -> {
                "${context.getString(R.string.field_no_power)} · " +
                    formatDuration(state.elapsedSeconds)
            }

            finished -> {
                "${context.getString(R.string.field_finished)} · " +
                    formatDuration(state.elapsedSeconds)
            }

            else -> context.getString(R.string.field_no_climb)
        }

        views.setTextViewText(R.id.field_sub, subText)
        views.setTextColor(
            R.id.field_sub,
            context.getColor(
                when {
                    paused -> R.color.pc_pause
                    active -> R.color.pc_muted
                    finished -> R.color.pc_finished
                    else -> R.color.pc_dim
                },
            ),
        )

        return views
    }

    private fun valueSize(length: Int, compact: Boolean): Float {
        return when {
            compact && length >= 4 -> 30f
            compact -> 38f
            length >= 4 -> 48f
            else -> 64f
        }
    }

    private fun formatDuration(seconds: Int): String {
        val total = seconds.coerceAtLeast(0)
        return if (total >= 3_600) {
            String.format(
                Locale.US,
                "%d:%02d:%02d",
                total / 3_600,
                (total % 3_600) / 60,
                total % 60,
            )
        } else {
            String.format(Locale.US, "%d:%02d", total / 60, total % 60)
        }
    }
}
