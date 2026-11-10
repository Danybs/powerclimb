package com.powerclimb

import android.content.Context
import com.powerclimb.data.ClimbPowerState
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
import kotlinx.coroutines.launch

class ClimbAvgPowerDataType(
    private val powerClimb: PowerClimbExtension,
) : DataTypeImpl(PowerClimbExtension.EXTENSION_ID, TYPE_ID) {

    companion object {
        const val TYPE_ID = "climb-avg-power"
    }

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

        if (config.preview) {
            emitter.updateView(
                ClimbPowerRemoteViews.build(
                    context = context,
                    state = ClimbPowerState.PREVIEW,
                    compact = compact,
                ),
            )
            emitter.setCancellable { }
            return
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            powerClimb.monitor.state
                .collect { state ->
                    emitter.updateView(
                        ClimbPowerRemoteViews.build(
                            context = context,
                            state = state,
                            compact = compact,
                        ),
                    )
                }
        }

        emitter.setCancellable { scope.cancel() }
    }
}
