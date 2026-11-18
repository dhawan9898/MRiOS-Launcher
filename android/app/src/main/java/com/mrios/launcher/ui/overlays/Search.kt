package com.mrios.launcher.ui.overlays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrios.launcher.R
import com.mrios.launcher.data.AppEntry
import com.mrios.launcher.data.Calculator
import com.mrios.launcher.data.FuzzySearch
import com.mrios.launcher.system.SystemActions
import com.mrios.launcher.ui.IOSColors
import com.mrios.launcher.ui.components.AppTile
import com.mrios.launcher.ui.components.FrostedBackdrop
import com.mrios.launcher.ui.components.glass

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onSubmit: () -> Unit = {},
) {
    Row(
        modifier.height(44.dp).glass(RoundedCornerShape(14.dp), tint = Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = IOSColors.Secondary, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            if (value.isEmpty()) Text(hint, color = IOSColors.Secondary, fontSize = 17.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                cursorBrush = SolidColor(IOSColors.Blue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().let { m -> focusRequester?.let { m.focusRequester(it) } ?: m },
            )
        }
        if (value.isNotEmpty()) Icon(
            Icons.Filled.Cancel, null, tint = IOSColors.Secondary,
            modifier = Modifier.size(18.dp).clickable { onValueChange("") },
        )
    }
}

@Composable
private fun ResultCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), tint = Color.White.copy(alpha = 0.12f))) { content() }
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = IOSColors.Blue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
private fun AppRow(apps: List<AppEntry>, badges: Map<String, Int>, onLaunch: (AppEntry, Rect) -> Unit, onMenu: (AppEntry, Rect) -> Unit) {
    apps.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            row.forEach { app ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    AppTile(app, 56.dp, true, badges[app.packageName] ?: 0, onTap = { onLaunch(app, it) }, onLongPress = { onMenu(app, it) })
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Spotlight-style search: fuzzy app search, calculator, web & store search. */
@Composable
fun SearchOverlay(
    apps: List<AppEntry>,
    suggestions: List<AppEntry>,
    badges: Map<String, Int>,
    onLaunch: (AppEntry, Rect) -> Unit,
    onMenu: (AppEntry, Rect) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val results = remember(q, apps) { if (q.isBlank()) emptyList() else FuzzySearch.filter(q, apps) { it.label } }
    val calc = remember(q) { Calculator.evaluate(q) }

    Box(Modifier.fillMaxSize()) {
        FrostedBackdrop(true, dim = 0.3f, modifier = Modifier.noRippleClick(onDismiss))
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp).popIn(0.96f)) {
            Row(Modifier.padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchField(q, { q = it }, stringResource(R.string.search_hint), Modifier.weight(1f), focus) {
                    results.firstOrNull()?.let { onLaunch(it, Rect.Zero) }
                }
                Text(
                    stringResource(R.string.cancel), color = IOSColors.Blue, fontSize = 17.sp,
                    modifier = Modifier.padding(start = 12.dp).clickable { keyboard?.hide(); onDismiss() },
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                if (q.isBlank()) {
                    if (suggestions.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.search_suggestions)) }
                        item { ResultCard { AppRow(suggestions.take(8), badges, onLaunch, onMenu) } }
                    }
                } else {
                    if (calc != null) item {
                        ResultCard {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Calculate, null, tint = IOSColors.Orange, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(q, color = IOSColors.Secondary, fontSize = 14.sp)
                                    Text("= $calc", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    if (results.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.search_top_hit)) }
                        item { ResultCard { AppRow(results.take(1), badges, onLaunch, onMenu) } }
                        if (results.size > 1) {
                            item { SectionHeader(stringResource(R.string.search_apps)) }
                            item { ResultCard { AppRow(results.drop(1).take(12), badges, onLaunch, onMenu) } }
                        }
                    } else if (calc == null) item {
                        Text(stringResource(R.string.search_no_results), color = IOSColors.Secondary, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
                    }
                    item {
                        ResultCard {
                            ActionRow(Icons.Outlined.Public, stringResource(R.string.search_web, q)) { SystemActions.webSearch(context, q); onDismiss() }
                            HorizontalDivider(color = IOSColors.Separator, thickness = 0.5.dp)
                            ActionRow(Icons.Outlined.ShoppingBag, stringResource(R.string.search_store)) { SystemActions.storeSearch(context, q); onDismiss() }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
