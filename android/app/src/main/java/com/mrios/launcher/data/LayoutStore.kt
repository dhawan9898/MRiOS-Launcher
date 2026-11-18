package com.mrios.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class LaunchStat(val count: Int, val last: Long)

/** Everything the user arranged or customised. Items are referenced by [AppKey.id]. */
data class LayoutData(
    val initialized: Boolean = false,
    val pages: List<List<String>> = listOf(emptyList()),
    val dock: List<String> = emptyList(),
    val known: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    /** "file:<path>" for a picture, or "pack:<package>:<drawable>" for an icon-pack icon. */
    val customIcons: Map<String, String> = emptyMap(),
    val launches: Map<String, LaunchStat> = emptyMap(),
)

sealed interface DropTarget {
    data class Page(val page: Int, val index: Int) : DropTarget
    data class Dock(val index: Int) : DropTarget
}

class LayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("layout", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LayoutData> = _state.asStateFlow()
    val value: LayoutData get() = _state.value

    @Synchronized
    fun update(block: (LayoutData) -> LayoutData) {
        val next = block(_state.value)
        if (next != _state.value) {
            _state.value = next
            save(next)
        }
    }

    // ---------------------------------------------------------------- reconcile

    /**
     * Brings the stored layout in line with the installed apps.
     * [installed] must be sorted the way new users should first see their apps.
     */
    fun reconcile(installed: List<String>, defaultDock: List<String>, grid: Grid, addNewToHome: Boolean) =
        update { d ->
            val installedSet = installed.toSet()
            if (!d.initialized) {
                val dock = defaultDock.filter { it in installedSet }.distinct().take(MAX_DOCK)
                val rest = installed.filter { it !in dock }
                d.copy(
                    initialized = true,
                    dock = dock,
                    pages = pack(listOf(rest), grid),
                    known = installedSet,
                )
            } else {
                val fresh = installed.filter { it !in d.known && it !in d.hidden }
                var pages = d.pages.map { p -> p.filter { it in installedSet } }
                if (addNewToHome && fresh.isNotEmpty()) {
                    pages = pages.dropLast(1) + listOf(pages.lastOrNull().orEmpty() + fresh)
                }
                d.copy(
                    dock = d.dock.filter { it in installedSet },
                    pages = pack(pages, grid),
                    known = d.known.filter { it in installedSet }.toSet() + fresh,
                    hidden = d.hidden.filter { it in installedSet }.toSet(),
                    launches = d.launches.filterKeys { it in installedSet },
                )
            }
        }

    fun repack(grid: Grid) = update { it.copy(pages = pack(it.pages, grid)) }

    // ---------------------------------------------------------------- edits

    fun move(id: String, target: DropTarget, grid: Grid) = update { d ->
        val fromDock = d.dock.indexOf(id)
        val fromPage = d.pages.indexOfFirst { id in it }
        val fromIndex = if (fromPage >= 0) d.pages[fromPage].indexOf(id) else -1
        var dock = d.dock.toMutableList()
        val pages = d.pages.map { it.toMutableList() }.toMutableList()

        when (target) {
            is DropTarget.Dock -> {
                if (fromDock >= 0) {
                    dock.removeAt(fromDock)
                    dock.add(target.index.coerceIn(0, dock.size), id)
                } else if (dock.size < MAX_DOCK) {
                    if (fromPage >= 0) pages[fromPage].remove(id)
                    dock.add(target.index.coerceIn(0, dock.size), id)
                } else {
                    // Dock full: swap with the icon under the finger.
                    val slot = target.index.coerceIn(0, dock.size - 1)
                    val displaced = dock[slot]
                    dock[slot] = id
                    if (fromPage >= 0) pages[fromPage][fromIndex] = displaced
                }
            }
            is DropTarget.Page -> {
                if (fromDock >= 0) dock.removeAt(fromDock)
                if (fromPage >= 0) pages[fromPage].remove(id)
                while (pages.size <= target.page) pages.add(mutableListOf())
                val page = pages[target.page]
                page.add(target.index.coerceIn(0, page.size), id)
            }
        }
        dock = dock.take(MAX_DOCK).toMutableList()
        d.copy(dock = dock, pages = pack(pages, grid))
    }

    fun removeFromHome(id: String, grid: Grid) = update { d ->
        d.copy(dock = d.dock - id, pages = pack(d.pages.map { it - id }, grid))
    }

    fun addToHome(id: String, grid: Grid) = update { d ->
        if (d.dock.contains(id) || d.pages.any { id in it }) d
        else d.copy(pages = pack(d.pages.dropLast(1) + listOf(d.pages.last() + id), grid))
    }

    fun setHidden(id: String, hidden: Boolean, grid: Grid) = update { d ->
        if (hidden) d.copy(
            hidden = d.hidden + id,
            dock = d.dock - id,
            pages = pack(d.pages.map { it - id }, grid),
        ) else d.copy(hidden = d.hidden - id)
    }

    fun rename(id: String, label: String?) = update { d ->
        d.copy(renames = if (label.isNullOrBlank()) d.renames - id else d.renames + (id to label.trim()))
    }

    fun setCustomIcon(id: String, spec: String?) = update { d ->
        d.copy(customIcons = if (spec == null) d.customIcons - id else d.customIcons + (id to spec))
    }

    fun recordLaunch(id: String) = update { d ->
        val s = d.launches[id]
        d.copy(launches = d.launches + (id to LaunchStat((s?.count ?: 0) + 1, System.currentTimeMillis())))
    }

    // ---------------------------------------------------------------- helpers

    companion object {
        const val MAX_DOCK = 4

        /** Re-flows items so no page exceeds its capacity and drops trailing empty pages. */
        fun pack(pages: List<List<String>>, grid: Grid): List<List<String>> {
            val out = mutableListOf<MutableList<String>>()
            var carry = ArrayDeque<String>()
            var i = 0
            while (i < pages.size || carry.isNotEmpty()) {
                val items = ArrayDeque<String>()
                items.addAll(carry)
                if (i < pages.size) items.addAll(pages[i])
                carry = ArrayDeque()
                val cap = grid.capacity(out.size).coerceAtLeast(1)
                val page = mutableListOf<String>()
                while (items.isNotEmpty() && page.size < cap) page.add(items.removeFirst())
                carry.addAll(items)
                out.add(page)
                i++
            }
            // Remove empty pages except the first.
            val cleaned = out.filterIndexed { idx, p -> idx == 0 || p.isNotEmpty() }
            return cleaned.ifEmpty { listOf(emptyList()) }
        }
    }

    private fun load(): LayoutData {
        val raw = prefs.getString("json", null) ?: return LayoutData()
        return runCatching {
            val o = JSONObject(raw)
            LayoutData(
                initialized = o.optBoolean("init"),
                pages = o.getJSONArray("pages").let { a -> (0 until a.length()).map { strings(a.getJSONArray(it)) } },
                dock = strings(o.getJSONArray("dock")),
                known = strings(o.getJSONArray("known")).toSet(),
                hidden = strings(o.getJSONArray("hidden")).toSet(),
                renames = map(o.getJSONObject("renames")),
                customIcons = map(o.getJSONObject("icons")),
                launches = o.getJSONObject("launches").let { l ->
                    l.keys().asSequence().associateWith { k ->
                        val s = l.getJSONArray(k); LaunchStat(s.getInt(0), s.getLong(1))
                    }
                },
            )
        }.getOrDefault(LayoutData())
    }

    private fun save(d: LayoutData) {
        val o = JSONObject()
            .put("init", d.initialized)
            .put("pages", JSONArray(d.pages.map { JSONArray(it) }))
            .put("dock", JSONArray(d.dock))
            .put("known", JSONArray(d.known.toList()))
            .put("hidden", JSONArray(d.hidden.toList()))
            .put("renames", JSONObject(d.renames))
            .put("icons", JSONObject(d.customIcons))
            .put("launches", JSONObject().apply {
                d.launches.forEach { (k, v) -> put(k, JSONArray(listOf(v.count, v.last))) }
            })
        prefs.edit().putString("json", o.toString()).apply()
    }

    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
    private fun map(o: JSONObject) = o.keys().asSequence().associateWith { o.getString(it) }
}
