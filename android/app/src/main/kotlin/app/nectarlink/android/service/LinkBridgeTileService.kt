// SPDX-License-Identifier: GPL-3.0-or-later
package app.nectarlink.android.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.nectarlink.android.NectarlinkApplication
import app.nectarlink.android.R
import app.nectarlink.android.core.Core
import app.nectarlink.android.core.CoreState
import app.nectarlink.android.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android Quick Settings Tile for LinkBridge.
 *
 * Displays paired PC connection status (Active / Inactive) and opens
 * the LinkBridge Remote Command / Control interface when tapped.
 */
class LinkBridgeTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var updatesJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val app = applicationContext as? NectarlinkApplication ?: return

        updatesJob = scope?.launch {
            app.core.state.collectLatest { coreState ->
                updateTileState(coreState)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        updatesJob?.cancel()
        scope?.cancel()
        scope = null
    }

    private fun updateTileState(state: CoreState) {
        val tile = qsTile ?: return
        val connectedDevice = state.devices.firstOrNull { it.online }

        if (connectedDevice != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.linkbridge_tile_label)
            tile.subtitle = connectedDevice.name
        } else if (state.devices.isNotEmpty()) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.linkbridge_tile_label)
            tile.subtitle = getString(R.string.linkbridge_tile_offline)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.linkbridge_tile_label)
            tile.subtitle = getString(R.string.linkbridge_tile_no_devices)
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val app = applicationContext as? NectarlinkApplication
        val onlineDevice = app?.core?.state?.value?.devices?.firstOrNull { it.online }
        val targetPcId = onlineDevice?.id ?: app?.core?.state?.value?.devices?.firstOrNull()?.id

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (targetPcId != null) {
                putExtra("remote_pc", targetPcId)
                putExtra("remote_mode", "touchpad")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
