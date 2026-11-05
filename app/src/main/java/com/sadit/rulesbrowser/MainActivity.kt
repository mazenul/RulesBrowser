package com.sadit.rulesbrowser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var rules: Rules

    /** Host of the page being viewed; read from the WebView IO thread, so volatile. */
    @Volatile
    private var pageHost: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rules = Rules(this)
        web = findViewById(R.id.web)
        urlBar = findViewById(R.id.urlBar)
        progress = findViewById(R.id.progress)

        web.webViewClient = RulesClient()
        web.webChromeClient = RulesChromeClient()
        applyRules()

        urlBar.setOnEditorActionListener { v, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                load(v.text.toString())
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(v.windowToken, 0)
                web.requestFocus()
                true
            } else false
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { if (web.canGoBack()) web.goBack() }
        findViewById<Button>(R.id.btnForward).setOnClickListener { if (web.canGoForward()) web.goForward() }
        findViewById<Button>(R.id.btnReload).setOnClickListener { web.reload() }
        findViewById<Button>(R.id.btnHome).setOnClickListener { load(rules.homepage) }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        load(intent?.data?.toString() ?: rules.homepage)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.toString()?.let { load(it) }
    }

    override fun onResume() {
        super.onResume()
        // Pick up any rule changes made in Settings.
        applyRules()
    }

    override fun onDestroy() {
        if (rules.wipeOnExit && isFinishing) wipeAll()
        web.destroy()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- rules

    @SuppressLint("SetJavaScriptEnabled")
    private fun applyRules() {
        val s = web.settings
        s.javaScriptEnabled = rules.javascript
        s.domStorageEnabled = rules.javascript
        s.cacheMode = if (rules.noCache) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setGeolocationEnabled(!rules.blockGeolocation)
        s.javaScriptCanOpenWindowsAutomatically = !rules.blockPopups
        s.setSupportMultipleWindows(false)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.userAgentString = rules.userAgent.ifBlank { WebSettings.getDefaultUserAgent(this) }

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(!rules.blockAllCookies)
        cm.setAcceptThirdPartyCookies(web, !rules.blockThirdPartyCookies && !rules.blockAllCookies)
    }

    private fun wipeAll() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        web.clearCache(true)
        web.clearHistory()
        web.clearFormData()
    }

    private fun requestHeaders(): Map<String, String> =
        if (rules.sendGpc) mapOf("DNT" to "1", "Sec-GPC" to "1") else emptyMap()

    // ----------------------------------------------------------- navigation

    private fun load(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains('.') && !t.contains(' ') -> "https://$t"
            else -> rules.searchTemplate.replace("%s", Uri.encode(t))
        }
        navigate(url)
    }

    /** Single choke point for main-frame navigation: scheme, HTTPS upgrade and blocklist rules. */
    private fun navigate(url: String) {
        var uri = Uri.parse(url)
        if (uri.scheme == "http" && rules.forceHttps) {
            uri = uri.buildUpon().scheme("https").build()
        }
        if (uri.scheme != "http" && uri.scheme != "https") return
        if (rules.isBlocked(uri.host)) {
            showBlocked(uri.host.orEmpty())
            return
        }
        web.loadUrl(uri.toString(), requestHeaders())
    }

    private fun showBlocked(host: String) {
        val safe = host.replace("<", "").replace(">", "").replace("&", "")
        val html = "<html><body style='font-family:sans-serif;padding:32px'>" +
            "<h2>Blocked by your rules</h2><p>$safe is on your blocklist.</p></body></html>"
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        urlBar.setText(host)
    }

    private fun emptyResponse() = WebResourceResponse(
        "text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0))
    )

    /** Approximate "site" (registrable domain) for the third-party rule. */
    private fun site(host: String): String {
        val parts = host.lowercase().split('.')
        if (parts.size <= 2) return host.lowercase()
        val secondLevel = setOf("co", "com", "org", "net", "gov", "edu", "ac")
        val take = if (parts.last().length == 2 && parts[parts.size - 2] in secondLevel) 3 else 2
        return parts.takeLast(take).joinToString(".")
    }

    // -------------------------------------------------------------- clients

    private inner class RulesClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame || request.method != "GET") return false
            navigate(request.url.toString())
            return true
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val host = request.url.host ?: return null
            if (rules.isBlocked(host)) return emptyResponse()
            if (rules.blockThirdPartyRequests && !request.isForMainFrame) {
                val page = pageHost
                if (page.isNotEmpty() && site(host) != site(page)) return emptyResponse()
            }
            return null
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            pageHost = Uri.parse(url).host.orEmpty()
            urlBar.setText(url)
            progress.visibility = View.VISIBLE
            if (rules.sendGpc && rules.javascript) {
                view.evaluateJavascript(
                    "try{Object.defineProperty(navigator,'globalPrivacyController',{value:true});" +
                        "Object.defineProperty(navigator,'doNotTrack',{value:'1'});}catch(e){}",
                    null
                )
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            progress.visibility = View.INVISIBLE
        }
    }

    private inner class RulesChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress.progress = newProgress
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String, callback: GeolocationPermissions.Callback
        ) {
            callback.invoke(origin, !rules.blockGeolocation, false)
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            // Camera, microphone and other site permissions are always denied.
            request.deny()
        }
    }
}
