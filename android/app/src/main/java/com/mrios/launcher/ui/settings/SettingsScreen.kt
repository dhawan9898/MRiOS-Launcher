package com.mrios.launcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwipeDown
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mrios.launcher.BuildConfigInfo
import com.mrios.launcher.R
import com.mrios.launcher.launcherApp
import com.mrios.launcher.badges.BadgeListenerService
import com.mrios.launcher.data.AppLanguage
import com.mrios.launcher.data.SwipeDownAction
import com.mrios.launcher.data.ThemeMode
import com.mrios.launcher.data.WeatherPlace
import com.mrios.launcher.data.WidgetType
import com.mrios.launcher.system.SystemActions
import com.mrios.launcher.ui.IOSColors
import com.mrios.launcher.ui.LauncherViewModel
import com.mrios.launcher.ui.components.AppIcon
import com.mrios.launcher.ui.components.FrostedBackdrop
import com.mrios.launcher.ui.components.glass
import com.mrios.launcher.ui.overlays.ButtonRole
import com.mrios.launcher.ui.overlays.DialogButton
import com.mrios.launcher.ui.overlays.GlassDialog
import com.mrios.launcher.ui.overlays.SearchField
import com.mrios.launcher.ui.overlays.noRippleClick
import com.mrios.launcher.ui.overlays.popIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsHost(
    val pickWallpaper: () -> Unit,
    val setDefault: () -> Unit,
    val requestLocation: () -> Unit,
    val notificationAccess: () -> Unit,
    val recreate: () -> Unit,
    val unlock: (onUnlocked: () -> Unit) -> Unit,
    val changePin: () -> Unit,
    val openPanels: () -> Unit,
)

private data class PanelAccess(val service: Boolean, val writeSettings: Boolean, val dnd: Boolean)

private enum class Page { MAIN, ICON_PACK, WIDGETS, WEATHER, HIDDEN }

private data class Choice<T>(@StringRes val title: Int, val options: List<Pair<T, String>>, val selected: T, val onSelect: (T) -> Unit)

private val GroupShape = RoundedCornerShape(14.dp)

@Composable
fun SettingsScreen(vm: LauncherViewModel, host: SettingsHost, startOnWeather: Boolean = false, onDismiss: () -> Unit) {
    val s by vm.settings.collectAsState()
    val wallpaper by vm.wallpaper.collectAsState()
    var page by remember { mutableStateOf(if (startOnWeather) Page.WEATHER else Page.MAIN) }
    var choice by remember { mutableStateOf<Choice<*>?>(null) }
    val context = LocalContext.current
    // Re-read system state (default launcher, notification access) whenever we come back.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val isDefault = remember(lifecycleState) { SystemActions.isDefaultLauncher(context) }
    val badgeAccess = remember(lifecycleState) { BadgeListenerService.isEnabled(context) }
    val panelsOn = remember(lifecycleState) { com.mrios.launcher.system.PanelService.isEnabled(context) }
    val writeAccess = remember(lifecycleState) { android.provider.Settings.System.canWrite(context) }
    val dndAccess = remember(lifecycleState) {
        context.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted
    }

    BackHandler { if (choice != null) choice = null else if (page != Page.MAIN) page = Page.MAIN else onDismiss() }

    Box(Modifier.fillMaxSize()) {
        FrostedBackdrop(true, dim = 0.5f, modifier = Modifier.noRippleClick { }, cover = true)
        Column(Modifier.fillMaxSize().statusBarsPadding().popIn(0.97f)) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page != Page.MAIN) Row(Modifier.clickable { page = Page.MAIN }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBackIos, null, tint = IOSColors.Blue, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.settings_title), color = IOSColors.Blue, fontSize = 17.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.done), color = IOSColors.Blue, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onDismiss))
            }
            val title = when (page) {
                Page.MAIN -> R.string.settings_title
                Page.ICON_PACK -> R.string.icon_pack
                Page.WIDGETS -> R.string.home_widgets
                Page.WEATHER -> R.string.weather_location
                Page.HIDDEN -> R.string.manage_hidden
            }
            Text(stringResource(title), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            when (page) {
                Page.MAIN -> MainPage(vm, host, s, wallpaper?.isCustom == true, isDefault, badgeAccess, PanelAccess(panelsOn, writeAccess, dndAccess), { page = it }, { choice = it })
                Page.ICON_PACK -> IconPackPage(vm, s.iconPack)
                Page.WIDGETS -> WidgetsPage(vm, s.homeWidgets)
                Page.WEATHER -> WeatherPage(vm, host, s.weatherPlace) { page = Page.MAIN }
                Page.HIDDEN -> HiddenPage(vm)
            }
        }
        choice?.let { c -> ChoiceDialog(c) { choice = null } }
    }
}

@Composable
private fun <T> ChoiceDialog(c: Choice<T>, onDismiss: () -> Unit) {
    GlassDialog(
        title = stringResource(c.title),
        onDismiss = onDismiss,
        buttons = c.options.map { (value, label) ->
            DialogButton((if (value == c.selected) "✓  " else "") + label, if (value == c.selected) ButtonRole.PRIMARY else ButtonRole.DEFAULT) {
                c.onSelect(value); onDismiss()
            }
        } + DialogButton(stringResource(R.string.cancel), ButtonRole.CANCEL, onDismiss),
    )
}

// ------------------------------------------------------------------ building blocks

private fun LazyListScope.group(header: Int?, rows: List<@Composable () -> Unit>) {
    if (header != null) item {
        Text(
            stringResource(header).uppercase(), color = IOSColors.Secondary, fontSize = 13.sp,
            modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 6.dp),
        )
    }
    item {
        Column(Modifier.padding(top = if (header == null) 12.dp else 0.dp).fillMaxWidth().glass(GroupShape, tint = Color.White.copy(alpha = 0.10f), rim = 0.2f)) {
            rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = IOSColors.Separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                row()
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, color: Color) {
    Box(Modifier.size(30.dp).background(color, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun SRow(
    title: String, icon: ImageVector? = null, color: Color = IOSColors.Blue, value: String? = null,
    subtitle: String? = null, chevron: Boolean = true, titleColor: Color = Color.White, onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { IconBadge(icon, color); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = IOSColors.Secondary, fontSize = 12.sp)
        }
        if (value != null) Text(value, color = IOSColors.Secondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
        if (chevron && onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = IOSColors.Tertiary)
    }
}

@Composable
private fun ToggleRow(title: String, icon: ImageVector, color: Color, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, color); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = IOSColors.Secondary, fontSize = 12.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = IOSColors.Green, checkedThumbColor = Color.White, uncheckedTrackColor = Color.White.copy(alpha = 0.15f), uncheckedBorderColor = Color.Transparent, uncheckedThumbColor = Color.White))
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (checked) Icon(Icons.Filled.Check, null, tint = IOSColors.Blue)
    }
}

private val listPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)

// ------------------------------------------------------------------ pages

@Composable
private fun MainPage(
    vm: LauncherViewModel, host: SettingsHost, s: com.mrios.launcher.data.Settings, customWallpaper: Boolean,
    isDefault: Boolean, badgeAccess: Boolean, access: PanelAccess, go: (Page) -> Unit, choose: (Choice<*>) -> Unit,
) {
    val context = LocalContext.current
    val styleLabels = com.mrios.launcher.data.IconStyle.entries.map { it to stringResource(it.title) }
    val tintLabels = com.mrios.launcher.data.IconTints.mapIndexed { i, (name, _) -> i to stringResource(name) }
    val allowed = stringResource(R.string.access_granted)
    val allow = stringResource(R.string.access_needed)
    val themeLabels = listOf(ThemeMode.GLASS to stringResource(R.string.theme_glass), ThemeMode.TRANSPARENT to stringResource(R.string.theme_transparent))
    val swipeLabels = listOf(SwipeDownAction.SEARCH to stringResource(R.string.swipe_search), SwipeDownAction.NOTIFICATIONS to stringResource(R.string.swipe_notifications))
    val langLabels = listOf(AppLanguage.SYSTEM to stringResource(R.string.lang_system), AppLanguage.ENGLISH to "English", AppLanguage.HINDI to "हिन्दी")
    val unitLabels = listOf(true to stringResource(R.string.celsius), false to stringResource(R.string.fahrenheit))
    val packName = remember(s.iconPack) { s.iconPack?.let { p -> vm.iconPacks().firstOrNull { it.packageName == p }?.label ?: p } }
    val systemIcons = stringResource(R.string.icon_pack_none)
    val myLocation = stringResource(R.string.use_my_location)
    val none = stringResource(R.string.widget_none)
    val widgetNames = s.homeWidgets.map { stringResource(it.title) }.joinToString(", ").ifEmpty { none }

    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = listPadding) {
        group(null, listOf {
            SRow(
                stringResource(if (isDefault) R.string.is_default else R.string.set_default), Icons.Filled.Home, IOSColors.Green,
                chevron = !isDefault, onClick = if (isDefault) null else host.setDefault,
            )
        })
        group(R.string.sec_appearance, buildList {
            add { SRow(stringResource(R.string.theme), Icons.Filled.Contrast, Color(0xFF5E5CE6), themeLabels.first { it.first == s.theme }.second) {
                choose(Choice(R.string.theme, themeLabels, s.theme) { v -> vm.updateSettings { it.copy(theme = v) } })
            } }
            add { SRow(stringResource(R.string.wallpaper), Icons.Filled.Wallpaper, Color(0xFF32ADE6), onClick = host.pickWallpaper) }
            if (customWallpaper) add { SRow(stringResource(R.string.wallpaper_reset), Icons.Filled.Wallpaper, Color(0xFF8E8E93), chevron = false) { vm.resetWallpaper() } }
            add { SRow(stringResource(R.string.icon_style), Icons.Filled.AutoAwesome, Color(0xFF64D2FF), styleLabels.first { it.first == s.iconStyle }.second) {
                choose(Choice(R.string.icon_style, styleLabels, s.iconStyle) { v -> vm.updateSettings { it.copy(iconStyle = v) } })
            } }
            if (s.iconStyle == com.mrios.launcher.data.IconStyle.TINTED) add { SRow(stringResource(R.string.icon_tint), Icons.Filled.Palette, Color(0xFFFF9F0A), tintLabels[s.iconTint].second) {
                choose(Choice(R.string.icon_tint, tintLabels, s.iconTint) { v -> vm.updateSettings { it.copy(iconTint = v) } })
            } }
            add { SRow(stringResource(R.string.icon_pack), Icons.Filled.Interests, IOSColors.Orange, packName ?: systemIcons) { go(Page.ICON_PACK) } }
            add { ToggleRow(stringResource(R.string.show_labels), Icons.Filled.TextFields, Color(0xFF8E8E93), s.showLabels) { v -> vm.updateSettings { it.copy(showLabels = v) } } }
        })
        group(R.string.sec_home, listOf(
            { SRow(stringResource(R.string.grid_columns), Icons.Filled.ViewColumn, IOSColors.Blue, s.columns.toString()) {
                choose(Choice(R.string.grid_columns, (3..6).map { it to it.toString() }, s.columns) { v -> vm.updateSettings { it.copy(columns = v) } })
            } },
            { SRow(stringResource(R.string.grid_rows), Icons.Filled.TableRows, IOSColors.Blue, s.rows.toString()) {
                choose(Choice(R.string.grid_rows, (4..8).map { it to it.toString() }, s.rows) { v -> vm.updateSettings { it.copy(rows = v) } })
            } },
            { SRow(stringResource(R.string.home_widgets), Icons.Filled.Widgets, Color(0xFFAF52DE), widgetNames) { go(Page.WIDGETS) } },
            { ToggleRow(stringResource(R.string.add_new_apps), Icons.Filled.AddToHomeScreen, IOSColors.Blue, s.addNewAppsToHome) { v -> vm.updateSettings { it.copy(addNewAppsToHome = v) } } },
            { SRow(stringResource(R.string.swipe_down), Icons.Filled.SwipeDown, Color(0xFF8E8E93), swipeLabels.first { it.first == s.swipeDown }.second) {
                choose(Choice(R.string.swipe_down, swipeLabels, s.swipeDown) { v -> vm.updateSettings { it.copy(swipeDown = v) } })
            } },
        ))
        group(R.string.sec_widgets, listOf(
            { SRow(stringResource(R.string.weather_location), Icons.Filled.NearMe, IOSColors.Blue, s.weatherPlace?.name?.substringBefore(" · ") ?: myLocation) { go(Page.WEATHER) } },
            { SRow(stringResource(R.string.temp_unit), Icons.Filled.Thermostat, IOSColors.Red, unitLabels.first { it.first == s.celsius }.second) {
                choose(Choice(R.string.temp_unit, unitLabels, s.celsius) { v -> vm.updateSettings { it.copy(celsius = v) } })
            } },
        ))
        group(R.string.sec_panels, listOf(
            { SRow(stringResource(R.string.panels_all_apps), Icons.Filled.Tune, Color(0xFF5E5CE6), if (access.service) allowed else allow,
                subtitle = stringResource(R.string.panels_all_apps_desc), onClick = host.openPanels) },
            { SRow(stringResource(R.string.notif_access), Icons.Filled.Notifications, IOSColors.Red, if (badgeAccess) allowed else allow,
                subtitle = stringResource(R.string.notif_access_desc)) { SystemActions.openNotificationAccess(context) } },
            { SRow(stringResource(R.string.write_settings_access), Icons.Filled.WbSunny, IOSColors.Blue, if (access.writeSettings) allowed else allow) {
                context.launcherApp.controls.openWriteSettings()
            } },
            { SRow(stringResource(R.string.dnd_access), Icons.Filled.DarkMode, Color(0xFF5E5CE6), if (access.dnd) allowed else allow) {
                context.launcherApp.controls.openFocusAccess()
            } },
            { ToggleRow(stringResource(R.string.edge_notifications), Icons.Filled.SwipeDown, Color(0xFF8E8E93), s.edgeNotifications) { v -> vm.updateSettings { it.copy(edgeNotifications = v) } } },
            { ToggleRow(stringResource(R.string.edge_control), Icons.Filled.SwipeDown, Color(0xFF8E8E93), s.edgeControlCenter) { v -> vm.updateSettings { it.copy(edgeControlCenter = v) } } },
        ))
        group(R.string.sec_notifications, listOf {
            ToggleRow(
                stringResource(R.string.badges), Icons.Filled.Notifications, IOSColors.Red, s.badges && badgeAccess,
                subtitle = stringResource(R.string.badges_desc),
            ) { v ->
                vm.updateSettings { it.copy(badges = v) }
                if (v && !badgeAccess) host.notificationAccess()
            }
        })
        group(R.string.sec_privacy, buildList {
            add { SRow(stringResource(R.string.manage_hidden), Icons.Filled.Lock, Color(0xFF8E8E93)) { host.unlock { go(Page.HIDDEN) } } }
            if (s.hiddenPinHash != null) add { SRow(stringResource(R.string.change_pin), Icons.Filled.Password, Color(0xFF8E8E93)) { host.changePin() } }
        })
        group(R.string.sec_language, listOf {
            SRow(stringResource(R.string.sec_language), Icons.Filled.Language, IOSColors.Blue, langLabels.first { it.first == s.language }.second) {
                choose(Choice(R.string.sec_language, langLabels, s.language) { v ->
                    if (v != s.language) { vm.updateSettings { it.copy(language = v) }; host.recreate() }
                })
            }
        })
        group(R.string.about, listOf(
            { SRow(stringResource(R.string.app_name), Icons.Filled.Info, Color(0xFF8E8E93), stringResource(R.string.version, BuildConfigInfo.versionName(LocalContext.current)), chevron = false) },
            { Text(stringResource(R.string.credits), color = IOSColors.Secondary, fontSize = 12.sp, modifier = Modifier.padding(14.dp)) },
        ))
    }
}

@Composable
private fun IconPackPage(vm: LauncherViewModel, current: String?) {
    val context = LocalContext.current
    val packs = remember { vm.iconPacks() }
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = listPadding) {
        group(null, buildList {
            add { CheckRow(stringResource(R.string.icon_pack_none), current == null, { vm.updateSettings { it.copy(iconPack = null) } }) }
            packs.forEach { p -> add { CheckRow(p.label, current == p.packageName, { vm.updateSettings { it.copy(iconPack = p.packageName) } }) } }
        })
        item {
            Text(
                stringResource(R.string.get_icon_packs), color = IOSColors.Blue, fontSize = 15.sp,
                modifier = Modifier.padding(16.dp).clickable { SystemActions.storeSearch(context, "icon pack") },
            )
        }
    }
}

@Composable
private fun WidgetsPage(vm: LauncherViewModel, current: List<WidgetType>) {
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = listPadding) {
        item { Text(stringResource(R.string.home_widgets_desc), color = IOSColors.Secondary, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
        group(null, WidgetType.entries.map { t ->
            @Composable {
                CheckRow(stringResource(t.title), t in current, {
                    vm.updateSettings { s ->
                        val list = if (t in s.homeWidgets) s.homeWidgets - t else (s.homeWidgets + t).takeLast(2)
                        s.copy(homeWidgets = list)
                    }
                })
            }
        })
    }
}

@Composable
private fun WeatherPage(vm: LauncherViewModel, host: SettingsHost, current: WeatherPlace?, back: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<WeatherPlace>()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query) {
        if (query.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        results = vm.searchCity(query)
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        SearchField(query, { query = it }, stringResource(R.string.search_city), Modifier.fillMaxWidth())
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
            group(null, listOf {
                CheckRow(stringResource(R.string.use_my_location), current == null, {
                    vm.updateSettings { it.copy(weatherPlace = null) }
                    host.requestLocation()
                    back()
                }, leading = { Icon(Icons.Filled.NearMe, null, tint = IOSColors.Blue) })
            })
            if (results.isNotEmpty()) group(null, results.map { p ->
                @Composable {
                    CheckRow(p.name, current == p, {
                        scope.launch { vm.updateSettings { it.copy(weatherPlace = p) }; back() }
                    }, leading = { Icon(Icons.Filled.WbSunny, null, tint = IOSColors.Yellow) })
                }
            })
        }
    }
}

@Composable
private fun HiddenPage(vm: LauncherViewModel) {
    val apps by vm.allApps.collectAsState()
    val layout by vm.layout.collectAsState()
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) { if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) } }
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.hidden_desc), color = IOSColors.Secondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        SearchField(query, { query = it }, stringResource(R.string.search_hint), Modifier.fillMaxWidth())
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp)) {
            group(null, shown.map { app ->
                @Composable {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app, 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Switch(app.id in layout.hidden, { vm.setHidden(app, it) }, colors = SwitchDefaults.colors(checkedTrackColor = IOSColors.Green))
                    }
                }
            })
        }
    }
}

