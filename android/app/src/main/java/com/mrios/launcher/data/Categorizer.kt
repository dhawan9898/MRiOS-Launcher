package com.mrios.launcher.data

import android.content.pm.ApplicationInfo
import android.os.Build

/** Sorts apps into App Library categories using Android's metadata, then package-name hints. */
object Categorizer {

    private val keywordRules: List<Pair<AppCategory, List<String>>> = listOf(
        AppCategory.GAMES to listOf("game", "games", "pubg", "freefire", "dts.freefire", "supercell", "king.", "roblox", "mojang", "ludo", "chess", "puzzle", "rovio", "miniclip", "gameloft", "bgmi", "krafton"),
        AppCategory.SOCIAL to listOf("whatsapp", "telegram", "instagram", "facebook", "katana", "orca", "snapchat", "twitter", "x.android", "threads", "barcelona", "discord", "signal", "messenger", "linkedin", "reddit", "sharechat", "moj", "josh", "truecaller", "messaging", "mms", "sms", "dialer", "contacts", "skype", "viber", "wechat", "line.android", "pinterest", "tumblr", "quora", "koo", "teams", "zoom"),
        AppCategory.ENTERTAINMENT to listOf("youtube", "netflix", "spotify", "music", "hotstar", "primevideo", "avod", "jiocinema", "mxtech", "mx.player", "videoplayer", "video", "player", "gaana", "wynk", "jiosaavn", "saavn", "sonyliv", "zee5", "voot", "podcast", "audible", "twitch", "tiktok", "vlc", "radio", "soundcloud", "crunchyroll"),
        AppCategory.SHOPPING to listOf("amazon.mShop", "shopping", "flipkart", "myntra", "meesho", "ajio", "nykaa", "snapdeal", "swiggy", "zomato", "blinkit", "grofers", "zepto", "bigbasket", "dominos", "ebay", "aliexpress", "shein", "dunzo", "instamart", "jiomart", "tatacliq", "food"),
        AppCategory.PRODUCTIVITY to listOf("docs", "sheets", "slides", "office", "word", "excel", "powerpoint", "drive", "dropbox", "onedrive", "notion", "evernote", "keep", "todo", "tasks", "calendar", "gmail", "email", "mail", "outlook", "slack", "trello", "pdf", "scanner", "notes", "paytm", "phonepe", "paisa", "bank", "upi", "bhim", "wallet", "finance", "money", "zerodha", "kite", "groww", "upstox", "cred", "sbi", "hdfc", "icici", "axis", "kotak", "pay"),
        AppCategory.CREATIVITY to listOf("camera", "photos", "gallery", "photo", "picsart", "snapseed", "lightroom", "canva", "capcut", "inshot", "kinemaster", "vsco", "editor", "remini", "lensa", "design", "draw", "paint", "sketch"),
        AppCategory.INFO to listOf("news", "inshorts", "dailyhunt", "kindle", "books", "reader", "wikipedia", "medium", "magazine", "translate", "dictionary", "learn", "duolingo", "byjus", "unacademy", "classroom", "education", "khan", "coursera", "udemy", "weather"),
        AppCategory.TRAVEL to listOf("maps", "uber", "olacabs", "rapido", "irctc", "makemytrip", "goibibo", "cleartrip", "yatra", "booking", "airbnb", "ixigo", "redbus", "travel", "trip", "indigo", "airindia", "metro", "railway", "navigation"),
        AppCategory.HEALTH to listOf("fit", "health", "workout", "yoga", "step", "pedometer", "cult", "healthify", "practo", "pharmeasy", "1mg", "netmeds", "meditation", "calm", "headspace", "samsung.android.app.shealth", "strava", "sleep"),
        AppCategory.UTILITIES to listOf("settings", "calculator", "clock", "deskclock", "files", "filemanager", "documentsui", "browser", "chrome", "firefox", "opera", "brave", "edge", "vending", "playstore", "store", "launcher", "security", "cleaner", "battery", "vpn", "authenticator", "compass", "flashlight", "torch", "recorder", "soundrecorder", "backup", "remote", "bluetooth", "wifi", "keyboard", "inputmethod", "tools", "utility", "google.android.googlequicksearchbox", "assistant", "lens"),
    )

    fun categorize(info: ApplicationInfo, packageName: String, label: String): AppCategory {
        fromSystemCategory(info)?.let { return it }
        val hay = (packageName + " " + label).lowercase()
        for ((cat, words) in keywordRules) {
            if (words.any { hay.contains(it.lowercase()) }) return cat
        }
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) return AppCategory.UTILITIES
        @Suppress("DEPRECATION")
        if (info.flags and ApplicationInfo.FLAG_IS_GAME != 0) return AppCategory.GAMES
        return AppCategory.OTHER
    }

    private fun fromSystemCategory(info: ApplicationInfo): AppCategory? = when (info.category) {
        ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
        ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> AppCategory.ENTERTAINMENT
        ApplicationInfo.CATEGORY_IMAGE -> AppCategory.CREATIVITY
        ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
        ApplicationInfo.CATEGORY_NEWS -> AppCategory.INFO
        ApplicationInfo.CATEGORY_MAPS -> AppCategory.TRAVEL
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
        else -> if (Build.VERSION.SDK_INT >= 31 && info.category == ApplicationInfo.CATEGORY_ACCESSIBILITY) AppCategory.UTILITIES else null
    }
}
