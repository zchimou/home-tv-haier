package app.orchard.tv.apps

import android.content.Context
import androidx.core.content.edit

/** Home selection is independent of the complete app list. Removing a tile never removes an app. */
class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("home_favorites", Context.MODE_PRIVATE)
    private var order: List<String>? = prefs.getString("packages", null)
        ?.split(',')?.filter { it.isNotBlank() }?.distinct()

    fun arrange(all: List<AppEntry>): List<AppEntry> {
        val available = all.associateBy { it.key }
        if (order == null) {
            val defaults = listOf(
                listOf("com.ktcp.svideo", "com.ktcp.video"),
                listOf("com.xiaodianshi.tv.yst"),
                listOf("com.netease.cloudmusic.tv"),
                listOf("com.cibn.tv", "com.youku.taitan.tv"),
                listOf("com.gitvdemo.video"),
                listOf("com.hunantv.license"),
            ).mapNotNull { alternatives -> alternatives.firstOrNull { it in available } }
            order = defaults.ifEmpty { all.filter { it.source == null }.take(5).map { it.key } }
            save()
        }
        // Keep missing keys so an app being updated returns in its original position.
        return order.orEmpty().mapNotNull(available::get)
    }

    fun toggle(key: String) {
        val current = order.orEmpty()
        order = if (key in current) current - key else current + key
        save()
    }

    fun move(key: String, delta: Int) {
        val current = order.orEmpty().toMutableList()
        val from = current.indexOf(key)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, current.lastIndex)
        if (from == to) return
        current.removeAt(from)
        current.add(to, key)
        order = current
        save()
    }

    private fun save() = prefs.edit { putString("packages", order.orEmpty().joinToString(",")) }
}
