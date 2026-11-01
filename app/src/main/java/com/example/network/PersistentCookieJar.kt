package com.example.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs: SharedPreferences = context.getSharedPreferences("mippedia_cookies", Context.MODE_PRIVATE)
    private val cookieMap = mutableMapOf<String, MutableList<Cookie>>()

    init {
        loadFromPrefs()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val current = cookieMap.getOrPut(host) { mutableListOf() }
        for (newCookie in cookies) {
            current.removeAll { it.name == newCookie.name }
            current.add(newCookie)
        }
        persistToPrefs()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val list = cookieMap[url.host]?.filter { it.expiresAt > now || it.persistent } ?: emptyList()
        return list
    }

    @Synchronized
    fun clear() {
        cookieMap.clear()
        prefs.edit().clear().apply()
    }

    private fun persistToPrefs() {
        val editor = prefs.edit()
        for ((host, cookies) in cookieMap) {
            val serialized = cookies.joinToString(";;;") { serializeCookie(it) }
            editor.putString("cookies_$host", serialized)
        }
        editor.apply()
    }

    private fun loadFromPrefs() {
        for ((key, value) in prefs.all) {
            if (key.startsWith("cookies_") && value is String) {
                val host = key.removePrefix("cookies_")
                val cookies = value.split(";;;").mapNotNull { deserializeCookie(it) }
                cookieMap[host] = cookies.toMutableList()
            }
        }
    }

    private fun serializeCookie(c: Cookie): String {
        return "${c.name}|${c.value}|${c.domain}|${c.path}|${c.expiresAt}|${c.secure}|${c.httpOnly}"
    }

    private fun deserializeCookie(s: String): Cookie? {
        val parts = s.split("|")
        if (parts.size < 7) return null
        return try {
            val builder = Cookie.Builder()
                .name(parts[0])
                .value(parts[1])
                .domain(parts[2])
                .path(parts[3])
                .expiresAt(parts[4].toLong())
            if (parts[5].toBoolean()) builder.secure()
            if (parts[6].toBoolean()) builder.httpOnly()
            builder.build()
        } catch (_: Exception) {
            null
        }
    }
}
