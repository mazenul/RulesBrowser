# Rules Browser

A small Android browser (Kotlin, system WebView) where privacy behaviour is controlled by your own rules.

## Open and run
1. Open the `RulesBrowser` folder in Android Studio (Koala or newer). Let Gradle sync.
2. Run on a device or emulator (Android 8.0 / API 26+).
3. Tap the gear icon to open **My Rules**.

## Rules included
- JavaScript on/off, pop-up blocking, location blocking (camera/mic always denied)
- Block all cookies, block third-party cookies, disable cache
- Wipe cookies, storage, cache and history when the app closes, plus a "clear now" button
- Force HTTPS, block mixed content
- Block third-party requests (approximate, based on the last two domain labels)
- Your own domain blocklist (subdomains included)
- Send Do Not Track and Global Privacy Control signals
- Custom user agent, home page and search engine

## Adding your own rule
1. Add a preference to `app/src/main/res/xml/rules.xml`.
2. Expose it in `Rules.kt`.
3. Enforce it in `MainActivity.kt` (`applyRules()`, `navigate()` or `shouldInterceptRequest()`).

## Limits
- Built on Android WebView, so it uses the system Chromium engine. There are no extensions or tabs yet.
- Rules apply inside the app only. It can be set as the default browser for http/https links.
- Third-party blocking and the blocklist apply to requests the WebView reports; POST main-frame navigations bypass `shouldOverrideUrlLoading` (an Android limitation).
