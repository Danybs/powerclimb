package com.powerclimb

import android.content.Context
import com.powerclimb.data.ClimbPowerState
import com.powerclimb.data.PowerZoneProfile
import com.powerclimb.ui.ClimbFieldMode
import com.powerclimb.ui.ClimbPowerRemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ClimbAvgPowerDataType(
    private val powerClimb: PowerClimbExtension,
    private val mode: ClimbFieldMode,
) : DataTypeImpl(PowerClimbExtension.EXTENSION_ID, mode.typeId) {

    override fun startStream(emitter: Emitter<StreamState>) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        scope.launch {
            powerClimb.monitor.state
                .collect { state ->
                    // Keep the stream in the Streaming state even when there is
                    // no active climb. This mirrors the official graphical
                    // sample and avoids an "N/A" overlay on the custom view.
                    val values = state.averageWatts
                        ?.let { mapOf(DataType.Field.SINGLE to it.toDouble()) }
                        ?: emptyMap()
                    emitter.onNext(
                        StreamState.Streaming(
                            DataPoint(
                                dataTypeId = dataTypeId,
                                values = values,
                            ),
                        ),
                    )
                }
        }

        emitter.setCancellable { scope.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        val compact = ClimbPowerRemoteViews.isCompact(config)
        val wide = ClimbPowerRemoteViews.isWide(config)

        if (config.preview) {
            val previewZones = PowerZoneProfile
                .from(powerClimb.monitor.profile.value)
                .takeIf { it.zones.isNotEmpty() }
                ?: PowerZoneProfile.PREVIEW
            val previewState = ClimbPowerState.PREVIEW.copy(
                averageWatts = previewAverage(previewZones),
            )
            emitter.updateView(
                ClimbPowerRemoteViews.build(
                    context = context,
                    state = previewState,
                    zones = previewZones,
                    mode = mode,
                    config = config,
                    compact = compact,
                    wide = wide,
                ),
            )
            emitter.setCancellable { }
            return
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            combine(
                powerClimb.monitor.state,
                powerClimb.monitor.profile,
            ) { state, profile ->
                state to PowerZoneProfile.from(profile)
            }.collect { (state, zones) ->
                emitter.updateView(
                    ClimbPowerRemoteViews.build(
                        context = context,
                        state = state,
                        zones = zones,
                        mode = mode,
                        config = config,
                        compact = compact,
                        wide = wide,
                    ),
                )
            }
        }

        emitter.setCancellable { scope.cancel() }
    }

    private fun previewAverage(zones: PowerZoneProfile): Int {
        val zone = zones.zones.getOrNull(zones.zones.size / 2)
            ?: return ClimbPowerState.PREVIEW.averageWatts ?: 247
        return if (zone.max == Int.MAX_VALUE) {
            zone.min + 50
        } else {
            (zone.min + zone.max) / 2
        }
    }
}
