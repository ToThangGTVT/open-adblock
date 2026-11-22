package com.utc.color.blockads.core.tunnel

import com.utc.color.blockads.core.common.R as L10n
import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.utc.color.blockads.core.common.TunnelStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile that turns ad blocking on and off without opening the app. */
class BlockAdsTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null
    private val controller by lazy { TunnelController(this) }

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch { controller.state.collect { render(it.status) } }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val status = controller.state.value.status
        if (status in Protecting) {
            controller.stop()
            return
        }
        // Consent dialogs need an Activity, so hand those cases over to the app.
        if (controller.prepare() != null || needsLocalNetworkPermission()) {
            openApp()
            return
        }
        render(TunnelStatus.Connecting)
        controller.start()
        if (controller.state.value.status == TunnelStatus.Error) openApp()
    }

    private fun needsLocalNetworkPermission() =
        Build.VERSION.SDK_INT >= 37 &&
            checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) !=
                PackageManager.PERMISSION_GRANTED

    private fun openApp() {
        val launch =
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                action = ActionStartProtection
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            } ?: return
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(launch)
        }
    }

    private fun render(status: TunnelStatus) {
        val tile = qsTile ?: return
        tile.state = if (status in Protecting) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(L10n.string.qs_tile_label)
        tile.subtitle =
            getString(
                when (status) {
                    TunnelStatus.Connected -> L10n.string.qs_tile_on
                    TunnelStatus.Connecting,
                    TunnelStatus.Reconnecting -> L10n.string.qs_tile_connecting
                    else -> L10n.string.qs_tile_off
                }
            )
        tile.updateTile()
    }

    companion object {
        /** Launch action asking the app to turn protection on, showing any consent it needs. */
        const val ActionStartProtection = "com.utc.color.blockads.START_PROTECTION"
        private val Protecting =
            setOf(TunnelStatus.Connecting, TunnelStatus.Connected, TunnelStatus.Reconnecting)
    }
}
