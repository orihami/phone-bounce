package jp.haruto.phonebounce

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** クイック設定のタイル: タップで、ロゴの一時停止／再開。 */
class PauseTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val p = AppSettings.prefs(this)
        p.edit().putBoolean(AppSettings.PAUSED, !p.getBoolean(AppSettings.PAUSED, false)).apply()
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val paused = AppSettings.prefs(this).getBoolean(AppSettings.PAUSED, false)
        tile.state = if (paused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = if (paused) "停止中" else "動いています"
        tile.updateTile()
    }
}
