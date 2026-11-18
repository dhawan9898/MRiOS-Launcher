# MRiOS Launcher — Android app

The actual launcher described on the landing page: a liquid-glass, iPhone-style home screen for Android 8.0+ (no root).

Kotlin + Jetpack Compose, no third-party dependencies beyond AndroidX.

## Features

| Landing-page promise | Where it lives |
|---|---|
| Liquid-glass interface | `ui/components/Glass.kt` – panels draw a pre-blurred copy of the wallpaper behind themselves (works on every Android version); Android 12+ additionally blurs the live home screen behind menus and folders |
| Smart App Library | `ui/pages/LibraryPage.kt`, `data/Categorizer.kt` – auto categories (Android app metadata + package-name rules), Suggestions, Recently Added, A–Z search |
| Widgets & themes | `ui/widgets/Widgets.kt` – Clock, Weather (Open-Meteo, no API key), Calendar, Batteries; Today view to the left of page 1. Themes: **Glass** (launcher-drawn frosted wallpaper) / **Transparent** (system wallpaper, clear panels) |
| Swipe down to search | `ui/overlays/Search.kt`, `data/Search.kt` – fuzzy matching, calculator, web / Play Store search |
| Hidden folder | Long-press → Hide App. Locked "Hidden" tile in the App Library, unlocked with fingerprint/face/screen lock, or a 4-digit launcher PIN if the phone has none |
| Personalisation | Rename apps, change icons (photo or icon-pack icon), ADW/Nova icon packs, wallpaper picker with preview, grid size, labels |
| English / Hindi | `res/values`, `res/values-hi`; switch in Settings → Language |

Also: iOS-style squircle icons, a glass dock, jiggle edit mode with press-and-drag reordering (including into and out of the dock and across pages), long-press quick actions (app shortcuts), notification badges (optional notification access), work-profile apps, and first-run onboarding.

## Build

Requirements: JDK 17 and the Android SDK (platform 35).

```bash
cd android
echo "sdk.dir=/path/to/Android/Sdk" > local.properties   # or set ANDROID_HOME
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Local release builds are signed with the debug key so they install straight away. For a real release, push a `v*` tag: the GitHub workflow signs the APK with your release key and publishes it (see the root README). To sign locally with your own key, set `MRIOS_KEYSTORE_PATH`, `MRIOS_KEYSTORE_PASSWORD`, `MRIOS_KEY_ALIAS` and `MRIOS_KEY_PASSWORD` before running Gradle.

## Install on a phone

1. Copy the APK to the phone and open it (allow "install unknown apps" for your file manager), or `adb install app-release.apk`.
2. Press Home and choose **MRiOS Launcher**, or use the "Set as Home App" step on the welcome screen.
3. For the full look, tap *Allow* for location (weather), calendar (events) and notification badges on the welcome screen. All of them are optional.

## Using it

- **Swipe left** for the App Library and **swipe right** for the Today view.
- **Swipe down** on the home screen to search. You can change it to open notifications in Settings.
- **Long-press an app** for quick actions, Rename, Change Icon, Hide, Remove, App Info or Delete.
- **Hold and drag** to enter edit mode and move icons. Tap **Done** to finish.
- **Long-press empty space** to edit the home screen, change the wallpaper or open launcher settings.
- The **MRiOS Settings** icon also opens the settings.

## Tested

Tested on Android emulators with debug and release builds:

| | Android 14 (gesture nav) | Android 9 (3-button nav) |
|---|---|---|
| Onboarding, set as home app, default dock | ✅ | ✅ |
| Home grid, widgets, page dots, dock | ✅ | ✅ |
| Hold-and-drag reordering / edit mode | ✅ | ✅ |
| Long-press menu with app shortcuts | ✅ | ✅ |
| App Library, folders, A–Z search | ✅ | ✅ |
| Swipe-down search (fuzzy, calculator) | ✅ | – |
| Weather via device location | ✅ | needs a network location provider* |
| Weather via chosen city | ✅ | ✅ |
| Hidden apps (PIN fallback) | ✅ | – |
| Wallpaper and custom icon from photos | ✅ | – |
| Icon packs (ADW appfilter format) | ✅ | – |
| Notification badges | ✅ | settings screen opens |
| Glass and Transparent themes, English/Hindi | ✅ | – |

\* Android 9–11 apps with approximate location can't use GPS. Phones with Google Play services have a network provider. If a phone has none, the widget offers a city picker.

Not tested: real hardware, Android 8.x, biometric unlock and work profiles.

## Limits (Android, not this app)

- A launcher can't restyle the status bar, notification shade, Control Center or lock screen, or the look of other apps. Those need root or a full system theme.
- App icons are reshaped into squircles. Their artwork stays as each app ships it, so use an iOS-style icon pack from the Play Store for matching icons.
- Not affiliated with Apple. No Apple fonts, icons or artwork are included.
