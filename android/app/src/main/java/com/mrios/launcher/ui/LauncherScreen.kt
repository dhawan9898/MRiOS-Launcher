package com.mrios.launcher.ui

import android.Manifest
import android.app.WallpaperManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.mrios.launcher.R
import com.mrios.launcher.data.AppEntry
import com.mrios.launcher.data.DropTarget
import com.mrios.launcher.data.SwipeDownAction
import com.mrios.launcher.data.ThemeMode
import com.mrios.launcher.system.HiddenAuth
import com.mrios.launcher.system.SystemActions
import com.mrios.launcher.ui.components.AppIcon
import com.mrios.launcher.ui.components.GlassContext
import com.mrios.launcher.ui.components.LocalGlass
import com.mrios.launcher.ui.components.drawWallpaper
import com.mrios.launcher.ui.overlays.AppMenuOverlay
import com.mrios.launcher.ui.overlays.FolderOverlay
import com.mrios.launcher.ui.overlays.IconPickerSheet
import com.mrios.launcher.ui.overlays.OnboardingActions
import com.mrios.launcher.ui.overlays.OnboardingOverlay
import com.mrios.launcher.ui.overlays.Overlay
import com.mrios.launcher.ui.overlays.PageMenuOverlay
import com.mrios.launcher.ui.overlays.PinDialog
import com.mrios.launcher.ui.overlays.PinMode
import com.mrios.launcher.ui.overlays.RemoveDialog
import com.mrios.launcher.ui.overlays.RenameDialog
import com.mrios.launcher.ui.overlays.SearchOverlay
import com.mrios.launcher.ui.overlays.WallpaperPreviewOverlay
import com.mrios.launcher.ui.overlays.byId
import com.mrios.launcher.ui.pages.DoneButton
import com.mrios.launcher.ui.pages.Dock
import com.mrios.launcher.ui.pages.GridGeom
import com.mrios.launcher.ui.pages.HomePage
import com.mrios.launcher.ui.pages.LibraryPage
import com.mrios.launcher.ui.pages.PageIndicator
import com.mrios.launcher.ui.pages.TileCallbacks
import com.mrios.launcher.ui.pages.TodayPage
import com.mrios.launcher.ui.settings.SettingsHost
import com.mrios.launcher.ui.settings.SettingsScreen
import com.mrios.launcher.ui.widgets.WidgetActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private data class DragInfo(val entry: AppEntry, val pos: Offset, val fromDock: Boolean)

private const val DOCK_ICON_DP = 60

@Composable
fun LauncherScreen(vm: LauncherViewModel, activity: FragmentActivity) {
    val settings by vm.settings.collectAsState()
    val wallpaper by vm.wallpaper.collectAsState()
    val home by vm.home.collectAsState()
    val badges by vm.badges.collectAsState()
    val weather by vm.weather.collectAsState()
    val library by vm.library.collectAsState()
    val visibleApps by vm.visibleApps.collectAsState()
    val hiddenApps by vm.hiddenApps.collectAsState()
    val recents by vm.recents.collectAsState()

    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val overlays = vm.overlays
    fun push(o: Overlay) { overlays.add(o) }
    fun pop() { if (overlays.isNotEmpty()) overlays.removeAt(overlays.lastIndex) }
    fun close(o: Overlay) { overlays.remove(o) }

    var editMode by vm::editMode
    var drag by remember { mutableStateOf<DragInfo?>(null) }
    var dockBounds by remember { mutableStateOf(Rect.Zero) }
    val gridGeoms = remember { HashMap<Int, GridGeom>() }

    val displayedPages = if (drag != null && home.pages.last().isNotEmpty()) home.pages + listOf(emptyList()) else home.pages
    val homeCount = displayedPages.size
    val pageCount = homeCount + 2
    val pagerState = rememberPagerState(initialPage = 1) { pageCount }
    val currentHomeCount by rememberUpdatedState(homeCount)

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dockHeight = (DOCK_ICON_DP + 34).dp
    val homeBottom = navBottom + dockHeight + 52.dp

    // ------------------------------------------------------------ activity results
    var pendingIconFor by remember { mutableStateOf<AppEntry?>(null) }
    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val e = pendingIconFor
        if (uri != null && e != null) vm.setIconFromUri(e, uri)
        pendingIconFor = null
    }
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { vm.decodeWallpaper(uri)?.let { push(Overlay.WallpaperPreview(it)) } }
    }
    fun openWallpaperPicker() = pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshWeather() }
    val requestCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // ------------------------------------------------------------ events
    LaunchedEffect(Unit) {
        vm.homePressed.collect {
            if (overlays.isNotEmpty() || editMode) { overlays.removeAll { it !is Overlay.Onboarding }; editMode = false }
            else if (pagerState.currentPage != 1) pagerState.animateScrollToPage(1)
        }
    }
    LaunchedEffect(Unit) { vm.openSettings.collect { overlays.removeAll { it is Overlay.Settings }; push(Overlay.Settings) } }
    LaunchedEffect(Unit) {
        if (!vm.settings.value.onboardingDone && overlays.none { it is Overlay.Onboarding }) push(Overlay.Onboarding)
    }

    BackHandler(enabled = overlays.isNotEmpty() || editMode || pagerState.currentPage != 1) {
        when {
            overlays.isNotEmpty() -> if (overlays.last() !is Overlay.Onboarding) pop()
            editMode -> editMode = false
            else -> scope.launch { pagerState.animateScrollToPage(1) }
        }
    }

    // ------------------------------------------------------------ actions
    fun launch(entry: AppEntry, b: Rect) {
        val r = if (b.width > 0) android.graphics.Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt()) else null
        overlays.removeAll { it is Overlay.Search || it is Overlay.Folder || it is Overlay.HiddenFolder }
        vm.launch(entry, r, view)
    }

    fun unlockHidden(then: () -> Unit) {
        if (HiddenAuth.systemAuthAvailable(activity)) {
            HiddenAuth.authenticate(activity, context.getString(R.string.hidden_auth_title), context.getString(R.string.hidden_auth_sub)) { ok -> if (ok) then() }
        } else {
            pendingUnlock = then
            push(Overlay.Pin(if (vm.settings.value.hiddenPinHash == null) PinMode.CREATE else PinMode.VERIFY))
        }
    }

    val widgetActions = WidgetActions(
        onWeather = {
            when (weather) {
                com.mrios.launcher.data.WeatherState.NeedsLocation -> requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                com.mrios.launcher.data.WeatherState.LocationUnavailable -> push(Overlay.WeatherSettings)
                else -> vm.refreshWeather()
            }
        },
        onCalendar = {
            if (!com.mrios.launcher.data.CalendarRepository.hasPermission(context)) requestCalendar.launch(Manifest.permission.READ_CALENDAR)
            else SystemActions.openCalendar(context)
        },
        onClock = { SystemActions.openClock(context) },
        onBattery = { SystemActions.start(context, android.content.Intent(android.content.Intent.ACTION_POWER_USAGE_SUMMARY)) },
    )

    fun computeDrop(d: DragInfo): DropTarget? {
        val p = d.pos
        if (dockBounds.contains(p)) {
            val n = home.dock.size
            val slots = if (d.fromDock) n else minOf(n + 1, 4)
            val idx = ((p.x - dockBounds.left) / (dockBounds.width / max(slots, 1))).toInt().coerceIn(0, max(slots - 1, 0))
            return DropTarget.Dock(idx)
        }
        val page = pagerState.currentPage
        if (page !in 1..homeCount) return null
        val g = gridGeoms[page - 1] ?: return DropTarget.Page(page - 1, Int.MAX_VALUE)
        if (p.y < g.rect.top) return DropTarget.Page(page - 1, 0)
        val col = ((p.x - g.rect.left) / g.cellW).toInt().coerceIn(0, g.cols - 1)
        val row = ((p.y - g.rect.top) / g.cellH).toInt().coerceAtLeast(0)
        return DropTarget.Page(page - 1, row * g.cols + col)
    }

    val callbacks = TileCallbacks(
        onTap = { e, b -> launch(e, b) },
        onLongPress = { e, b -> push(Overlay.AppMenu(e, b)) },
        onDragStart = { e, p, fromDock ->
            overlays.removeAll { it is Overlay.AppMenu }
            editMode = true
            drag = DragInfo(e, p, fromDock)
        },
        onDrag = { delta -> drag = drag?.let { it.copy(pos = it.pos + delta) } },
        onDragEnd = {
            val d = drag
            if (d != null) computeDrop(d)?.let { vm.move(d.entry, it) }
            drag = null
        },
        onRemove = { e -> push(Overlay.RemoveConfirm(e)) },
    )

    // Hover at the screen edge while dragging to flip pages.
    val edgePx = with(density) { 28.dp.toPx() }
    val edge = drag?.let { if (it.pos.x < edgePx) -1 else if (it.pos.x > rootSize.width - edgePx) 1 else 0 } ?: 0
    LaunchedEffect(edge) {
        if (edge == 0) return@LaunchedEffect
        while (true) {
            delay(650)
            val target = (pagerState.currentPage + edge).coerceIn(1, currentHomeCount)
            if (target != pagerState.currentPage) pagerState.animateScrollToPage(target)
        }
    }

    val blurHome = overlays.any { it !is Overlay.WallpaperPreview }
    val blurRadius by animateDpAsState(if (blurHome) 22.dp else 0.dp, label = "blur")
    // Transparent theme: the system wallpaper is behind our window, so ask the compositor to blur it (Android 12+).
    LaunchedEffect(blurHome, settings.theme) {
        if (Build.VERSION.SDK_INT >= 31) {
            val w = activity.window
            if (blurHome && settings.theme == ThemeMode.TRANSPARENT) {
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                w.attributes = w.attributes.apply { blurBehindRadius = 90 }
            } else {
                w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
    }

    CompositionLocalProvider(LocalGlass provides GlassContext(settings.theme, wallpaper, rootSize)) {
        Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
            // Wallpaper (Glass theme draws its own copy so panels can frost it; Transparent shows the system one).
            Box(Modifier.fillMaxSize().blur(blurRadius)) {
                val wp = wallpaper
                if (settings.theme == ThemeMode.GLASS && wp != null) {
                    Canvas(Modifier.fillMaxSize()) { drawWallpaper(wp.sharp, rootSize) }
                }
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = pageCount,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(settings.swipeDown, editMode, homeCount) {
                            var total = 0f
                            detectVerticalDragGestures(
                                onDragStart = { total = 0f },
                                onVerticalDrag = { _, dy -> total += dy },
                                onDragEnd = {
                                    val onHome = pagerState.currentPage in 1..homeCount
                                    if (onHome && !editMode && total > 120f) {
                                        if (settings.swipeDown == SwipeDownAction.SEARCH) push(Overlay.Search)
                                        else SystemActions.expandNotifications(context)
                                    }
                                },
                            )
                        },
                ) { page ->
                    when (page) {
                        0 -> TodayPage(weather, widgetActions, statusTop, navBottom, onSearch = { push(Overlay.Search) }, onEdit = { push(Overlay.Settings) })
                        pageCount - 1 -> LibraryPage(
                            groups = library, apps = visibleApps, badges = badges,
                            topPadding = statusTop, bottomPadding = navBottom,
                            onLaunch = ::launch,
                            onMenu = { e, b -> push(Overlay.AppMenu(e, b)) },
                            onOpenGroup = { push(Overlay.Folder(it.id)) },
                            onOpenHidden = { unlockHidden { push(Overlay.HiddenFolder) } },
                        )
                        else -> HomePage(
                            pageIndex = page - 1,
                            items = displayedPages.getOrElse(page - 1) { emptyList() },
                            settings = settings,
                            weather = weather,
                            widgetActions = widgetActions,
                            badges = badges,
                            editMode = editMode,
                            draggingId = drag?.entry?.id,
                            topPadding = statusTop,
                            bottomPadding = homeBottom,
                            callbacks = callbacks,
                            onGeom = { i, g -> gridGeoms[i] = g },
                            onEmptyTap = { if (editMode) editMode = false },
                            onEmptyLongPress = { at -> if (!editMode) push(Overlay.PageMenu(at)) },
                        )
                    }
                }

                // Dock + page indicator fade out on the Today view and App Library.
                val pos = pagerState.currentPage + pagerState.currentPageOffsetFraction
                val dockVisibility = minOf(pos, (pageCount - 1) - pos).coerceIn(0f, 1f)
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navBottom + 8.dp)
                        .graphicsLayer {
                            alpha = dockVisibility
                            translationY = (1f - dockVisibility) * 120f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PageIndicator(homeCount, (pagerState.currentPage - 1).coerceIn(0, homeCount - 1), onClick = { push(Overlay.Search) }, modifier = Modifier.padding(bottom = 12.dp))
                    if (dockVisibility > 0.01f) Dock(
                        apps = home.dock, iconSize = DOCK_ICON_DP.dp, badges = badges,
                        editMode = editMode, draggingId = drag?.entry?.id, callbacks = callbacks,
                        onBounds = { dockBounds = it },
                    )
                }

                AnimatedVisibility(editMode, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) {
                    DoneButton({ editMode = false })
                }
            }

            // Icon following the finger while dragging.
            drag?.let { d ->
                val sizePx = with(density) { 66.dp.toPx() }
                AppIcon(
                    d.entry, 66.dp,
                    Modifier
                        .offset { IntOffset((d.pos.x - sizePx / 2).roundToInt(), (d.pos.y - sizePx / 2).roundToInt()) }
                        .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f; shadowElevation = 24f },
                )
            }

            // ------------------------------------------------------------ overlays
            overlays.toList().forEach { o ->
                key(o) {
                    when (o) {
                        Overlay.Search -> SearchOverlay(visibleApps, recents, badges, ::launch, { e, b -> push(Overlay.AppMenu(e, b)) }) { close(o) }
                        is Overlay.Folder -> library.byId(o.groupId)?.let { g ->
                            FolderOverlay(stringResource(g.title), g.apps, badges, settings.showLabels, null, ::launch, { e, b -> push(Overlay.AppMenu(e, b)) }) { close(o) }
                        } ?: LaunchedEffect(o) { close(o) }
                        Overlay.HiddenFolder -> FolderOverlay(
                            stringResource(R.string.cat_hidden), hiddenApps, badges, settings.showLabels, stringResource(R.string.hidden_empty),
                            ::launch, { e, b -> push(Overlay.AppMenu(e, b, fromHidden = true)) },
                        ) { close(o) }
                        is Overlay.AppMenu -> AppMenuOverlay(
                            vm, o.entry, o.bounds,
                            onHome = o.entry.id in home.onHome,
                            fromHidden = o.fromHidden,
                            onDismiss = { close(o) },
                            onEditHome = { overlays.clear(); editMode = true },
                            onRename = { push(Overlay.Rename(o.entry)) },
                            onChangeIcon = { push(Overlay.IconPicker(o.entry)) },
                            onRemove = { push(Overlay.RemoveConfirm(o.entry)) },
                            onShortcut = { s -> overlays.clear(); vm.startShortcut(s, null) },
                        )
                        is Overlay.PageMenu -> PageMenuOverlay(
                            o.at, onDismiss = { close(o) },
                            onEdit = { editMode = true },
                            onWallpaper = { openWallpaperPicker() },
                            onSettings = { push(Overlay.Settings) },
                        )
                        is Overlay.RemoveConfirm -> RemoveDialog(vm, o.entry) { close(o) }
                        is Overlay.Rename -> RenameDialog(vm, o.entry) { close(o) }
                        is Overlay.IconPicker -> IconPickerSheet(vm, o.entry, onPickPhoto = {
                            pendingIconFor = o.entry
                            close(o)
                            pickIcon.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { close(o) }
                        Overlay.Settings, Overlay.WeatherSettings -> SettingsScreen(
                            vm,
                            startOnWeather = o == Overlay.WeatherSettings,
                            host = SettingsHost(
                                pickWallpaper = { openWallpaperPicker() },
                                setDefault = { SystemActions.requestDefaultLauncher(activity) },
                                requestLocation = { requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                                notificationAccess = { SystemActions.openNotificationAccess(context) },
                                recreate = { activity.recreate() },
                                unlock = { then -> unlockHidden(then) },
                                changePin = { push(Overlay.Pin(PinMode.CREATE)) },
                            ),
                        ) { close(o) }
                        is Overlay.WallpaperPreview -> WallpaperPreviewOverlay(o.bitmap, onApply = { both ->
                            val flags = if (both) WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
                            vm.applyWallpaper(o.bitmap, flags)
                            close(o)
                        }) { close(o) }
                        is Overlay.Pin -> PinDialog(
                            o.mode, settings.hiddenPinHash,
                            onDone = { h -> vm.updateSettings { it.copy(hiddenPinHash = h) } },
                            onSuccess = { pendingUnlock?.invoke(); pendingUnlock = null },
                        ) { close(o) }
                        Overlay.Onboarding -> OnboardingOverlay(
                            OnboardingActions(
                                setDefault = { SystemActions.requestDefaultLauncher(activity) },
                                location = { requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                                calendar = { requestCalendar.launch(Manifest.permission.READ_CALENDAR) },
                                badges = { SystemActions.openNotificationAccess(context) },
                            ),
                        ) {
                            vm.updateSettings { it.copy(onboardingDone = true) }
                            vm.refreshWeather()
                            close(o)
                        }
                    }
                }
            }
        }
    }
}

/** Action to run after the hidden-apps PIN is entered correctly. */
private var pendingUnlock: (() -> Unit)? = null

