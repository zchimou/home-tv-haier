package app.orchard.tv.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.orchard.tv.system.TvSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class AppEntry(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    /** Launched with CATEGORY_LEANBACK_LAUNCHER when the app has a TV entry, else CATEGORY_LAUNCHER. */
    val leanback: Boolean,
    /** False for apps on the system image, which can be disabled but not uninstalled. */
    val removable: Boolean,
    val art: TileArt,
    /** Set for a TV input pinned to Home (HDMI, tuner...) rather than an app. */
    val source: TvSource? = null,
) {
    val key: String get() = packageName

    fun launchIntent(): Intent = source?.let { Intent(it.intent) } ?: Intent(Intent.ACTION_MAIN)
        .addCategory(if (leanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
        .setComponent(component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

/** A Home tile for a TV input. Its key never collides with a package name. */
fun sourceEntry(source: TvSource): AppEntry = AppEntry(
    packageName = SOURCE_KEY_PREFIX + source.id,
    label = source.label,
    component = ComponentName("input", source.id),
    leanback = false,
    removable = false,
    art = TileArtFactory.forSource(source.kind, source.label, source.deviceName),
    source = source,
)

const val SOURCE_KEY_PREFIX = "input:"

object AppCatalog {
    /** Xiaomi's app store declares only this category, not CATEGORY_LAUNCHER (MiTV-MFTR0). */
    private const val CATEGORY_APPSTORE_LAUNCHER = "android.intent.category.APPSTORE_LAUNCHER"

    /**
     * Firmware apps that the maker's own launcher shows but that declare no launcher category at
     * all, so no query finds them. Verified on Xiaomi MiTV-MFTR0.
     */
    private val UNLISTED_ENTRIES = listOf(
        ComponentName("com.xiaomi.mitv.mediaexplorer", "com.xiaomi.mitv.mediaexplorer.NewScraperMainEntryActivity"), // 高清播放器
        ComponentName("com.xiaomi.smarthome.tv", "com.xiaomi.smarthome.tv.ui.MainActivity"), // 米家
    )

    /**
     * Firmware apps drawn as Aerial's own tiles (see [TileArtFactory.forSystem]). None of them
     * declares a standard app category, so they are listed by package, each checked on its TV.
     */
    private val SYSTEM_TILES = mapOf(
        // Haier 65H5D / LRH670_2G_ALI7H_B
        "com.haier.tv.menu" to SystemTile.Settings,
        "com.huanglong.miracast" to SystemTile.Cast,
        "com.hrtvbic.usb.mm" to SystemTile.Media,
        "com.huanglong.explorer" to SystemTile.Media,
        // Xiaomi MiTV-MFTR0
        "com.xiaomi.mitv.settings" to SystemTile.Settings,
        "com.mitv.gallery" to SystemTile.Photos,
        "com.xiaomi.mitv.calendar" to SystemTile.Calendar,
        "com.xiaomi.tweather" to SystemTile.Weather,
        "com.xiaomi.mitv.smartshare" to SystemTile.Cast,
        "com.xiaomi.mitv.tvmanager" to SystemTile.Security,
        "com.xiaomi.mitv.appstore" to SystemTile.Store,
        "com.xiaomi.mitv.mediaexplorer" to SystemTile.Media,
        // TCL MT5879 (China)
        "com.tcl.settings" to SystemTile.Settings,
        "com.tcl.tvsmartalbum" to SystemTile.Photos,
        "com.tcl.common.weather" to SystemTile.Weather,
        "com.tcl.MultiScreenInteraction_TV" to SystemTile.Cast,
        "com.tcl.guard" to SystemTile.Security,
        "com.tcl.appmarket2" to SystemTile.Store,
        "com.tcl.ui_mediaCenter" to SystemTile.Media,
        "com.tcl.browser" to SystemTile.Browser,
    )

    /**
     * Firmwares that do declare the standard categories. Only for system apps without a TV
     * banner: a banner is artwork made for the TV (Google TV's own apps have them) and wins.
     */
    private val SYSTEM_CATEGORIES = listOf(
        Intent.CATEGORY_APP_GALLERY to SystemTile.Photos,
        Intent.CATEGORY_APP_CALENDAR to SystemTile.Calendar,
        Intent.CATEGORY_APP_BROWSER to SystemTile.Browser,
        Intent.CATEGORY_APP_MARKET to SystemTile.Store,
        CATEGORY_APPSTORE_LAUNCHER to SystemTile.Store,
    )

    private class Cached(val stamp: Long, val width: Int, val label: String, val art: TileArt)
    private val cache = HashMap<String, Cached>()

    /** The apps of the last load, for the phone page. */
    @Volatile var loaded: List<AppEntry> = emptyList(); private set

    /**
     * Every app with a TV entry, plus apps that only have a phone-style entry. China-market firmwares
     * (Xiaomi's settings, gallery, weather...) expose many of their own apps with CATEGORY_LAUNCHER only,
     * and a few with no launcher category at all (see [UNLISTED_ENTRIES]).
     * One tile per package; the TV entry wins.
     */
    suspend fun load(context: Context): List<AppEntry> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val found = LinkedHashMap<String, Pair<ActivityInfo, Boolean>>()
        fun launchable(info: ActivityInfo): Boolean = info.exported && info.enabled &&
            info.applicationInfo.enabled && (info.permission.isNullOrBlank() ||
            pm.checkPermission(info.permission, context.packageName) == PackageManager.PERMISSION_GRANTED)
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
            .filter { launchable(it.activityInfo) }
            .forEach { found.putIfAbsent(it.activityInfo.packageName, it.activityInfo to true) }
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { launchable(it.activityInfo) }
            .forEach { found.putIfAbsent(it.activityInfo.packageName, it.activityInfo to false) }
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_APPSTORE_LAUNCHER), 0)
            .filter { launchable(it.activityInfo) }
            .forEach { found.putIfAbsent(it.activityInfo.packageName, it.activityInfo to false) }
        for (component in UNLISTED_ENTRIES) {
            val info = runCatching { pm.getActivityInfo(component, 0) }.getOrNull() ?: continue
            if (launchable(info)) found.putIfAbsent(info.packageName, info to false)
        }
        found.remove(context.packageName)

        val byCategory = HashMap<String, SystemTile>()
        for ((category, tile) in SYSTEM_CATEGORIES) {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
                .forEach { byCategory.putIfAbsent(it.activityInfo.packageName, tile) }
        }
        pm.resolveActivity(Intent(android.provider.Settings.ACTION_SETTINGS), 0)
            ?.let { byCategory.putIfAbsent(it.activityInfo.packageName, SystemTile.Settings) }

        coroutineScope {
            found.values.map { (info, leanback) ->
                async { runCatching { entry(pm, info, leanback, byCategory) }.getOrNull() }
            }.awaitAll().filterNotNull()
        }.also { loaded = it }
    }

    /**
     * loadIcon and loadBanner pick the density bucket of the screen: a 96 px icon at 320 dpi, 72 px
     * at TCL's 240 dpi, stretched to a 198 px tile it looks soft (Xiaomi MiTV4-ANSM0, issue #17).
     * Ask for the densest bucket the app ships instead; the bitmap layers of adaptive icons too.
     */
    private fun densest(pm: PackageManager, info: ActivityInfo, res: Int): android.graphics.drawable.Drawable? =
        if (res == 0) null else runCatching {
            pm.getResourcesForApplication(info.applicationInfo)
                .getDrawableForDensity(res, android.util.DisplayMetrics.DENSITY_XXXHIGH, null)
        }.getOrNull()

    private fun entry(pm: PackageManager, info: ActivityInfo, leanback: Boolean, byCategory: Map<String, SystemTile>): AppEntry {
        val pkg = info.packageName
        // A picture chosen on the phone page changes the tile as much as an update does.
        val stamp = runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L) + CustomIcons.stamp(pkg)
        val cached = synchronized(cache) { cache[pkg] }?.takeIf { it.stamp == stamp && it.width == TileArtFactory.W }
        val system = (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val label: String
        val art: TileArt
        if (cached != null) {
            label = cached.label
            art = cached.art
        } else {
            label = info.loadLabel(pm).toString().trim().ifEmpty { pkg }
            // Banner first: it is the artwork the app designed for a TV. ActivityInfo falls back
            // to the application's banner by itself.
            // A "banner" that is not wide is the app's square icon declared again (遥遥电视助手 on
            // Xiaomi MiTV-MFTR0); covering the tile with it blows the logo up past the edges.
            val banner = (if (leanback) densest(pm, info, info.bannerResource) ?: runCatching { info.loadBanner(pm) }.getOrNull() else null)
                ?.takeIf { it.intrinsicWidth >= it.intrinsicHeight * 4 / 3 }
            val own = if (system) SYSTEM_TILES[pkg] ?: byCategory[pkg]?.takeIf { banner == null } else null
            val custom = CustomIcons.load(pkg)
            art = when {
                custom != null -> TileArtFactory.fromBanner(android.graphics.drawable.BitmapDrawable(android.content.res.Resources.getSystem(), custom))
                own != null -> TileArtFactory.forSystem(own)
                banner != null -> TileArtFactory.fromBanner(banner)
                else -> TileArtFactory.fromIcon(densest(pm, info, info.iconResource) ?: info.loadIcon(pm))
            }
            synchronized(cache) { cache[pkg] = Cached(stamp, TileArtFactory.W, label, art) }
        }
        val removable = !system
        return AppEntry(pkg, label, ComponentName(pkg, info.name), leanback, removable, art)
    }
}
