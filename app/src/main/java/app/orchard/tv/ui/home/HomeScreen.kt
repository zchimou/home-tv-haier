package app.orchard.tv.ui.home

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.KeyEvent as NativeKey
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.orchard.tv.*
import app.orchard.tv.R
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.system.SystemProbe
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.control.ControlCenter
import app.orchard.tv.ui.wallpaper.WallpaperSheet
import app.orchard.tv.wallpaper.Wallpapers
import app.orchard.tv.ui.theme.*
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Bake the two atmospheric lights once: one full-screen texture draw, no idle animation. */
private fun homeBackdrop(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bitmap)
    c.drawColor(android.graphics.Color.rgb(5, 7, 16))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = RadialGradient(260f, 160f, 540f,
        intArrayOf(0xff850c24.toInt(), 0xff32081a.toInt(), 0x00050710), floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, 960f, 540f, paint)
    paint.shader = RadialGradient(840f, 510f, 500f,
        intArrayOf(0xff101e38.toInt(), 0x00050710), null, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, 960f, 540f, paint)
    return bitmap.asImageBitmap()
}

@Composable
fun HomeScreen(vm: HomeViewModel, onLaunch: (AppEntry, Rect?) -> Unit, onOpen: (Intent) -> Unit,
    onAppInfo: (String) -> Unit, onUninstall: (String) -> Unit, onStartScreensaver: () -> Unit) {
    val context = LocalContext.current
    val now by rememberNow()
    val all = (vm.apps + vm.hiddenApps).distinctBy { it.key }
    val favorites = vm.favoriteApps
    val requesters = remember(favorites.map { it.key }) { List(favorites.size) { FocusRequester() } }
    val top = remember { List(3) { FocusRequester() } }
    val bottom = remember { List(5) { FocusRequester() } }
    var selected by remember { mutableIntStateOf(0) }
    var initialized by remember { mutableStateOf(false) }
    var allVisible by remember { mutableStateOf(false) }
    var editorVisible by remember { mutableStateOf(false) }
    var zone by remember { mutableIntStateOf(1) }
    var tool by remember { mutableIntStateOf(0) }
    val active = vm.overlay == Overlay.None && !allVisible && !editorVisible
    val windowFocus = LocalWindowInfo.current.isWindowFocused
    fun focus(r: FocusRequester) { runCatching { r.requestFocus() } }
    fun focusHome() { focus(requesters.getOrNull(selected) ?: requesters.firstOrNull() ?: bottom[3]) }
    fun closeEditor() { editorVisible = false; selected = 0; zone = 1; initialized = false }
    LaunchedEffect(vm.loaded, active, windowFocus, vm.homeResetPending, favorites.map { it.key }) {
        if (!vm.loaded || !active || !windowFocus) return@LaunchedEffect
        withFrameNanos { }
        if (!initialized || vm.homeResetPending) { selected = 0; zone = 1; vm.homeResetPending = false; initialized = true }
        selected = selected.coerceIn(0, maxOf(0, favorites.lastIndex))
        when(zone) { 0 -> focus(top[tool.coerceIn(0,2)]); 2 -> focus(bottom[tool.coerceIn(0,4)]); else -> focusHome() }
    }
    LaunchedEffect(vm.homeResetPending) {
        if (vm.homeResetPending) { allVisible = false; editorVisible = false }
    }
    BackHandler(active) { selected = 0; zone = 1; focusHome() }
    val backdrop = remember { homeBackdrop() }
    val density = LocalDensity.current
    val reduced = LocalReducedMotion.current
    val scroll = remember { Animatable(0f) }
    val target = with(density) { (maxOf(0, selected - 3) * 211).dp.toPx() }
    LaunchedEffect(target) {
        if (reduced) scroll.snapTo(target) else scroll.animateTo(target, spring(dampingRatio = 1f, stiffness = 180f))
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.keyCode == NativeKey.KEYCODE_MENU && active) {
            allVisible = true; true
        } else false
    }) {
        Image(Wallpapers.backdrop ?: backdrop, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(Modifier.offset(56.dp, 38.dp)) {
            BasicText(stringResource(when(now.hour) { in 5..11 -> R.string.home_morning; in 12..17 -> R.string.home_afternoon; else -> R.string.home_evening }), style = TextStyle(color = Color(0xffd6cbd0), fontSize = 13.sp, letterSpacing = 1.sp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 5.dp)) {
                BasicText(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = Type.Body.copy(fontSize = 40.sp, fontWeight = FontWeight.Light))
                Box(Modifier.padding(horizontal = 18.dp).width(1.dp).height(27.dp).background(Color.White.copy(.24f)))
                BasicText(dateText(now), style = Type.Body.copy(fontSize = 15.sp, color = Color(0xffd6cbd0)))
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val labels = listOf(R.string.home_inputs, R.string.home_network, R.string.home_settings)
            val icons = listOf(Glyph.Input, Glyph.Wifi, Glyph.Gear)
            labels.forEachIndexed { i, label ->
                HomeTool(stringResource(label), icons[i], top[i], active,
                    Modifier.onFocusChanged { if(it.isFocused) { zone = 0; tool = i } },
                    onDown = { focusHome() },
                    onClick = { when(i) { 0 -> vm.overlay = Overlay.ControlCenter(CcPage.Inputs); 1 -> onOpen(SystemProbe.networkSettingsIntent(context)); else -> onOpen(SystemProbe.settingsIntent(context)) } })
            }
        }
        // Four wide banners, with a glimpse of the fifth. No repeated heading above the row.
        Box(Modifier.offset(y = 292.dp).fillMaxWidth().height(148.dp).clip(RoundedCornerShape(0.dp))) {
            Box(Modifier.offset(39.dp, 8.dp).fillMaxWidth().height(126.dp).background(Color.White.copy(.035f), RoundedCornerShape(22.dp)))
            Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).requiredWidth((maxOf(1, favorites.size) * 211).dp).offset { IntOffset(with(density) { 50.dp.roundToPx() } - scroll.value.roundToInt(), with(density) { 20.dp.roundToPx() }) }, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                favorites.forEachIndexed { i, app ->
                    key(app.key) {
                    HomeCard(app, app.label, i, requesters[i], active,
                        Modifier.onFocusChanged { if(it.isFocused) { selected = i; zone = 1 } },
                        onKey = { e ->
                            if(e.type != KeyEventType.KeyDown) false else when(e.nativeKeyEvent.keyCode) {
                                NativeKey.KEYCODE_DPAD_LEFT -> { focus(requesters[maxOf(0,i-1)]); true }
                                NativeKey.KEYCODE_DPAD_RIGHT -> { focus(requesters[minOf(favorites.lastIndex,i+1)]); true }
                                NativeKey.KEYCODE_DPAD_UP -> { focus(top[0]); true }
                                NativeKey.KEYCODE_DPAD_DOWN -> { focus(bottom[0]); true }
                                else -> false
                            }
                        }, onClick = { onLaunch(app, null) })
                    }
                }
            }
            if (favorites.isEmpty()) {
                Column(Modifier.padding(start = 56.dp, top = 35.dp)) {
                    BasicText("还没有首页应用", style = Type.Body.copy(fontSize = 22.sp))
                    BasicText("在下方“编辑首页”中选择已安装的应用", style = Type.Body.copy(fontSize = 14.sp, color = Color.White.copy(.65f)), modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        Row(Modifier.offset(56.dp, 464.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val labels = listOf(stringResource(R.string.home_cast), stringResource(R.string.home_disk), stringResource(R.string.home_all), "编辑首页", "背景")
            val icons = listOf(Glyph.Cast, Glyph.Disk, Glyph.Grid, Glyph.Move, Glyph.Photo)
            labels.forEachIndexed { i, label ->
                HomeTool(label, icons[i], bottom[i], active,
                    Modifier.onFocusChanged { if(it.isFocused) { zone = 2; tool = i } },
                    onUp = { focusHome() }, onClick = {
                        when(i) {
                            0 -> onOpen(SystemProbe.castIntent(context))
                            1 -> onOpen(SystemProbe.diskIntent(context))
                            2 -> allVisible = true
                            3 -> editorVisible = true
                            else -> vm.overlay = Overlay.Wallpaper
                        }
                    })
            }
        }
        BasicText(if (favorites.isEmpty()) "选择你常用的应用" else "${selected+1} / ${favorites.size}", style = Type.Body.copy(fontSize = 13.sp, color = Color.White.copy(.55f)), modifier = Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = 42.dp))
        ControlCenter(visible = vm.overlay is Overlay.ControlCenter,
            page = (vm.overlay as? Overlay.ControlCenter)?.page ?: CcPage.Main, vm = vm, now = now,
            onOpen = { vm.overlay = Overlay.None; onOpen(it) }, onClose = { vm.overlay = Overlay.None })
        // Keep the home customization deliberately separate from Aerial's layout editor.
        LaunchedEffect(vm.overlay) {
            if (vm.overlay == Overlay.HiddenApps) { vm.overlay = Overlay.None; allVisible = true }
        }
        WallpaperSheet(visible = vm.overlay == Overlay.Wallpaper,
            onDismiss = { vm.overlay = Overlay.None }, onStartScreensaver = onStartScreensaver,
            backgroundOnly = true)
        if(allVisible) {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { withFrameNanos { }; focus(first) }
            BackHandler { allVisible = false }
            Column(Modifier.fillMaxSize().background(Color(0xff10121b)).padding(48.dp)) {
                BasicText(stringResource(R.string.home_all), style = Type.Body.copy(fontSize = 26.sp))
                BasicText(stringResource(R.string.home_back_hint), style = Type.Body.copy(fontSize = 13.sp, color = Color.White.copy(.55f)), modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(4), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(all, key = { _, a -> a.key }) { index, app ->
                        HomeCard(app, app.label, -1, if(index==0) first else null, true, Modifier, onKey = { false }, onClick = { allVisible = false; onLaunch(app,null) })
                    }
                }
            }
        }
        if (editorVisible) {
            FavoritesEditor(
                apps = all.filter { it.source == null }, favorites = favorites,
                onToggle = vm::toggleFavorite, onMove = vm::moveFavorite, onClose = ::closeEditor,
            )
        }
        Screensaver(visible = vm.screensaverVisible, apps = all, now = now, skips = vm.screensaverSkips)
    }
}

@Composable
private fun HomeTool(label: String, glyph: Glyph, requester: FocusRequester, enabled: Boolean,
    modifier: Modifier, onUp: (() -> Unit)? = null, onDown: (() -> Unit)? = null, onClick: () -> Unit) {
    Pressable(onClick, modifier, enabled, requester, focusedScale = 1.05f,
        onKey = { e -> if(e.type != KeyEventType.KeyDown) false else when(e.nativeKeyEvent.keyCode) {
            NativeKey.KEYCODE_DPAD_UP -> if(onUp!=null) { onUp(); true } else false
            NativeKey.KEYCODE_DPAD_DOWN -> if(onDown!=null) { onDown(); true } else false
            else -> false
        } }) { state ->
        Row(Modifier.background(Color.White.copy(if(state.focused) .22f else .065f), RoundedCornerShape(50))
            .border(if(state.focused) 1.5.dp else .7.dp, Color.White.copy(if(state.focused) .95f else .22f), RoundedCornerShape(50))
            .padding(horizontal = 17.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlyphIcon(glyph, Color.White.copy(.9f), 16.dp)
            BasicText(label, style = Type.Body.copy(fontSize = 13.sp, color = Color.White.copy(.9f)))
        }
    }
}

@Composable
private fun HomeCard(app: AppEntry?, label: String, index: Int, requester: FocusRequester?, enabled: Boolean,
    modifier: Modifier, onKey: (KeyEvent) -> Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(app?.packageName) {
        app?.let { runCatching {
            val drawable = context.packageManager.getApplicationIcon(it.packageName)
            val bitmap = Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888)
            drawable.setBounds(0,0,192,192); drawable.draw(AndroidCanvas(bitmap)); bitmap.asImageBitmap()
        }.getOrNull() }
    }
    Pressable(onClick, modifier.width(196.dp).height(106.dp), enabled, requester, focusedScale = 1.055f, onKey = onKey) { state ->
        val shape = RoundedCornerShape(15.dp)
        Box(Modifier.fillMaxSize().drawBehind {
            if(state.focused) {
                // Small local halos only, never an expensive full-screen blur.
                for (n in 9 downTo 1) {
                    val pad = n.dp.toPx()
                    drawRoundRect(Color(0xffbdd7ff).copy(alpha = .012f), Offset(-pad,-pad), androidx.compose.ui.geometry.Size(size.width+pad*2,size.height+pad*2), CornerRadius(15.dp.toPx()+pad))
                }
            }
        }.background(Brush.verticalGradient(listOf(Color(if(state.focused) 0xff4c495b else 0xff302d3d), Color(0xff1c1e2b))), shape)
            .border(if(state.focused) 1.6.dp else .65.dp, Color.White.copy(if(state.focused) .96f else .18f), shape)
            .clip(shape)) {
            if(state.focused) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(14.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xffc2ddff).copy(.3f)))))
            Row(Modifier.fillMaxSize().padding(horizontal=14.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                if(icon!=null) Image(icon, null, Modifier.size(43.dp).clip(RoundedCornerShape(9.dp)))
                else BasicText(if(index==5) "N" else "▶", style=TextStyle(color=Color(0xffed3045),fontSize=34.sp,fontWeight=FontWeight.Bold),modifier=Modifier.width(35.dp))
                Column {
                    BasicText(label, style=Type.Body.copy(fontSize=16.sp,fontWeight=FontWeight.Medium), maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(app==null) BasicText(stringResource(R.string.home_pending),style=Type.Body.copy(fontSize=10.sp,color=Color.White.copy(.5f)),modifier=Modifier.padding(top=6.dp))
                }
            }
        }
    }
}
