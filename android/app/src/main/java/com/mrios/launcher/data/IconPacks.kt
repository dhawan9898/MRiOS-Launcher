package com.mrios.launcher.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPackInfo(val packageName: String, val label: String)

/** Reads ADW/Nova-compatible icon packs (appfilter.xml). */
class IconPacks(private val context: Context) {
    private val pm = context.packageManager
    private val packs = HashMap<String, Pack?>()

    private class Pack(val res: Resources, val pkg: String, val byComponent: Map<String, String>, val byPackage: Map<String, String>)

    fun installed(): List<IconPackInfo> {
        val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme", "com.teslacoilsw.launcher.THEME")
        return actions.flatMap { a -> runCatching { pm.queryIntentActivities(Intent(a), PackageManager.GET_META_DATA) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.packageName }
            .distinct()
            .map { pkg -> IconPackInfo(pkg, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)) }
            .sortedBy { it.label.lowercase() }
    }

    fun iconFor(packName: String, component: ComponentName): Drawable? {
        val pack = pack(packName) ?: return null
        val name = pack.byComponent["${component.packageName}/${component.className}"]
            ?: pack.byPackage[component.packageName] ?: return null
        return drawable(pack, name)
    }

    fun allIconNames(packName: String): List<String> {
        val pack = pack(packName) ?: return emptyList()
        return pack.byComponent.values.distinct().sorted()
    }

    fun drawable(packName: String, name: String): Drawable? = pack(packName)?.let { drawable(it, name) }

    fun clear() = synchronized(packs) { packs.clear() }

    @SuppressLint("DiscouragedApi")
    private fun drawable(pack: Pack, name: String): Drawable? = runCatching {
        val id = pack.res.getIdentifier(name, "drawable", pack.pkg)
        if (id == 0) null else ResourcesCompat.getDrawable(pack.res, id, null)
    }.getOrNull()

    private fun pack(pkg: String): Pack? = synchronized(packs) {
        packs.getOrPut(pkg) { runCatching { parse(pkg) }.getOrNull() }
    }

    @SuppressLint("DiscouragedApi")
    private fun parse(pkg: String): Pack {
        val res = pm.getResourcesForApplication(pkg)
        val parser: XmlPullParser = res.getIdentifier("appfilter", "xml", pkg).let { id ->
            if (id != 0) res.getXml(id)
            else XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(res.assets.open("appfilter.xml"), "UTF-8")
            }
        }
        val byComponent = HashMap<String, String>()
        val byPackage = HashMap<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (comp != null && drawable != null && comp.startsWith("ComponentInfo{")) {
                    val inner = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                    val slash = inner.indexOf('/')
                    if (slash > 0) {
                        val p = inner.substring(0, slash)
                        var c = inner.substring(slash + 1)
                        if (c.startsWith(".")) c = p + c
                        byComponent["$p/$c"] = drawable
                        byPackage.putIfAbsent(p, drawable)
                    }
                }
            }
            event = parser.next()
        }
        return Pack(res, pkg, byComponent, byPackage)
    }
}
