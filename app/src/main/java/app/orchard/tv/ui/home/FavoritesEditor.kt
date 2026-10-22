package app.orchard.tv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.theme.Type

/** TV-only editing: add/remove by confirmation, then use the adjacent buttons to reorder. */
@Composable
fun FavoritesEditor(
    apps: List<AppEntry>, favorites: List<AppEntry>, onToggle: (String) -> Unit,
    onMove: (String, Int) -> Unit, onClose: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val done = remember { FocusRequester() }
    val candidates = remember(apps.map { it.key }) { apps.sortedBy { it.label } }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { (if (candidates.isEmpty()) done else first).requestFocus() }
    }
    Column(Modifier.fillMaxSize().background(Color(0xff10121b)).padding(42.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText("编辑首页", style = Type.Body.copy(fontSize = 25.sp))
                BasicText("确认添加或移除应用；用“前移 / 后移”调整顺序", style = Type.Body.copy(fontSize = 13.sp, color = Color.White.copy(.65f)), modifier = Modifier.padding(top = 8.dp))
            }
            EditorButton("完成", Modifier.width(94.dp).height(43.dp), done, onClick = onClose)
        }
        BasicText("已选择 ${favorites.size} 个应用 · 更改即时保存 · 移除图标不会卸载应用", style = Type.Body.copy(fontSize = 12.sp, color = Color(0xffc3d7ff)), modifier = Modifier.padding(top = 16.dp, bottom = 18.dp))
        if (candidates.isEmpty()) {
            BasicText("暂无可用应用，请稍后再打开", style = Type.Body)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(7.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(candidates, key = { _, app -> app.key }) { index, app ->
                val position = favorites.indexOfFirst { it.key == app.key }
                val selected = position >= 0
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditorButton(
                        if (selected) "✓  ${position + 1}. ${app.label}" else "+  ${app.label}",
                        Modifier.weight(1f).height(48.dp), if (index == 0) first else null,
                        selected = selected, onClick = { onToggle(app.key) },
                    )
                    if (selected) {
                        EditorButton("前移", Modifier.width(78.dp).height(48.dp), subdued = position == 0, onClick = { onMove(app.key, -1) })
                        EditorButton("后移", Modifier.width(78.dp).height(48.dp), subdued = position == favorites.lastIndex, onClick = { onMove(app.key, 1) })
                    } else {
                        Spacer(Modifier.width(168.dp))
                    }
                }
            }
        }
        BasicText("按返回键回到首页", style = Type.Body.copy(fontSize = 12.sp, color = Color.White.copy(.5f)), modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun EditorButton(
    label: String, modifier: Modifier, requester: FocusRequester? = null,
    selected: Boolean = false, subdued: Boolean = false, onClick: () -> Unit,
) {
    Pressable(onClick, modifier, focusRequester = requester, focusedScale = 1.025f) { state ->
        val shape = RoundedCornerShape(10.dp)
        Box(
            Modifier.fillMaxSize()
                .background(Color(if (state.focused) 0xff53546c else if (selected) 0xff29344a else 0xff232631), shape)
                .border(if (state.focused) 1.5.dp else .6.dp, Color.White.copy(if (state.focused) .95f else .16f), shape)
                .padding(horizontal = 15.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(label, style = Type.Body.copy(fontSize = 15.sp, color = Color.White.copy(if (subdued && !state.focused) .4f else 1f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
