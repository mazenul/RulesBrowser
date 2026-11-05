package com.sadit.rulesbrowser

import android.content.Context
import androidx.preference.PreferenceManager

/** Reads the user's rules from SharedPreferences. Every rule lives in res/xml/rules.xml. */
class Rules(context: Context) {
    private val p = PreferenceManager.getDefaultSharedPreferences(context)

    val javascript get() = p.getBoolean("js", true)
    val blockPopups get() = p.getBoolean("block_popups", true)
    val blockGeolocation get() = p.getBoolean("block_geo", true)
    val blockAllCookies get() = p.getBoolean("block_cookies", false)
    val blockThirdPartyCookies get() = p.getBoolean("block_3p_cookies", true)
    val noCache get() = p.getBoolean("no_cache", true)
    val wipeOnExit get() = p.getBoolean("private_mode", true)
    val forceHttps get() = p.getBoolean("force_https", true)
    val blockThirdPartyRequests get() = p.getBoolean("block_3p_requests", false)
    val sendGpc get() = p.getBoolean("send_gpc", true)
    val userAgent: String get() = p.getString("user_agent", "").orEmpty().trim()
    val homepage: String get() = p.getString("homepage", "https://duckduckgo.com").orEmpty().ifBlank { "https://duckduckgo.com" }
    val searchTemplate: String
        get() = p.getString("search_template", "https://duckduckgo.com/?q=%s").orEmpty()
            .let { if (it.contains("%s")) it else "https://duckduckgo.com/?q=%s" }

    private val blockedDomains: List<String>
        get() = p.getString("blocklist", "").orEmpty()
            .split(Regex("[\\s,;]+"))
            .map { it.trim().lowercase().removePrefix("*.").removePrefix(".") }
            .filter { it.isNotEmpty() }

    fun isBlocked(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val h = host.lowercase()
        return blockedDomains.any { h == it || h.endsWith(".$it") }
    }
}
