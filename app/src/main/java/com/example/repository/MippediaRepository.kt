package com.example.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.model.*
import com.example.network.MippediaApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class MippediaRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mippedia_prefs", Context.MODE_PRIVATE)
    val apiClient = MippediaApiClient(context)

    private val _currentLanguage = MutableStateFlow(
        if (prefs.getString("selected_lang", "id") == "en") WikiLanguage.ENGLISH else WikiLanguage.INDONESIAN
    )
    val currentLanguage: StateFlow<WikiLanguage> = _currentLanguage.asStateFlow()

    private val _savedArticles = MutableStateFlow<List<WikiSearchResult>>(emptyList())
    val savedArticles: StateFlow<List<WikiSearchResult>> = _savedArticles.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _currentUser = MutableStateFlow(WikiUserInfo())
    val currentUser: StateFlow<WikiUserInfo> = _currentUser.asStateFlow()

    init {
        loadSavedArticles()
        loadSearchHistory()
    }

    fun setLanguage(lang: WikiLanguage) {
        if (lang != WikiLanguage.COMMONS) {
            _currentLanguage.value = lang
            prefs.edit().putString("selected_lang", lang.code).apply()
        }
    }

    suspend fun refreshUserInfo() {
        val res = apiClient.getUserInfo(_currentLanguage.value)
        if (res.isSuccess) {
            _currentUser.value = res.getOrNull() ?: WikiUserInfo()
        }
    }

    fun isBookmarked(title: String): Boolean {
        return _savedArticles.value.any { it.title.equals(title, ignoreCase = true) }
    }

    fun toggleBookmark(article: WikiArticle) {
        val current = _savedArticles.value.toMutableList()
        val index = current.indexOfFirst { it.title.equals(article.title, ignoreCase = true) }
        if (index >= 0) {
            current.removeAt(index)
        } else {
            val snippet = article.parsedHtml.replace(Regex("<[^>]*>"), "").take(160)
            current.add(
                0,
                WikiSearchResult(
                    pageId = article.pageId,
                    title = article.title,
                    snippet = snippet,
                    ns = 0,
                    wordCount = article.parsedHtml.length / 5
                )
            )
        }
        _savedArticles.value = current
        saveSavedArticles()
    }

    fun removeSavedArticle(title: String) {
        val current = _savedArticles.value.toMutableList()
        val index = current.indexOfFirst { it.title.equals(title, ignoreCase = true) }
        if (index >= 0) {
            current.removeAt(index)
            _savedArticles.value = current
            saveSavedArticles()
        }
    }

    fun addSearchHistory(term: String) {
        val trimmed = term.trim()
        if (trimmed.isBlank()) return
        val current = _searchHistory.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        if (current.size > 15) {
            current.removeAt(current.size - 1)
        }
        _searchHistory.value = current
        saveSearchHistory()
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        prefs.edit().remove("search_history").apply()
    }

    private fun loadSavedArticles() {
        val jsonStr = prefs.getString("saved_articles", null) ?: return
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<WikiSearchResult>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    WikiSearchResult(
                        pageId = obj.optLong("pageId"),
                        title = obj.optString("title"),
                        snippet = obj.optString("snippet"),
                        ns = obj.optInt("ns", 0)
                    )
                )
            }
            _savedArticles.value = list
        } catch (_: Exception) {}
    }

    private fun saveSavedArticles() {
        val arr = JSONArray()
        for (item in _savedArticles.value) {
            val obj = JSONObject()
            obj.put("pageId", item.pageId)
            obj.put("title", item.title)
            obj.put("snippet", item.snippet)
            obj.put("ns", item.ns)
            arr.put(obj)
        }
        prefs.edit().putString("saved_articles", arr.toString()).apply()
    }

    private fun loadSearchHistory() {
        val jsonStr = prefs.getString("search_history", null) ?: return
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            _searchHistory.value = list
        } catch (_: Exception) {}
    }

    private fun saveSearchHistory() {
        val arr = JSONArray()
        for (term in _searchHistory.value) {
            arr.put(term)
        }
        prefs.edit().putString("search_history", arr.toString()).apply()
    }
}
