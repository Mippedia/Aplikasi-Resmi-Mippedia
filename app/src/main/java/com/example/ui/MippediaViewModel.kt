package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import com.example.model.*
import com.example.repository.MippediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppTab(val title: String) {
    EXPLORE("Jelajahi"),
    SEARCH("Cari"),
    COMMONS("Berkas Commons"),
    SAVED("Tersimpan"),
    ACCOUNT("Akun")
}

data class UiState(
    val currentTab: AppTab = AppTab.EXPLORE,
    val currentLanguage: WikiLanguage = WikiLanguage.INDONESIAN,
    val isLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val loadingMessage: String = "",
    val error: String? = null,
    val successNotice: String? = null,

    // Onboarding
    val showOnboarding: Boolean = false,

    // Home / Explore
    val recentChanges: List<WikiRecentChange> = emptyList(),
    val monthlyArticle: MonthlyFeature? = null,
    val monthlyImage: MonthlyFeature? = null,
    val commonsSpotlight: List<WikiMediaItem> = emptyList(),

    // Current Active Article
    val activeArticle: WikiArticle? = null,
    val isBookmarked: Boolean = false,
    val showTocSheet: Boolean = false,
    val showRelatedSheet: Boolean = false,
    val relatedArticles: List<String> = emptyList(),
    val isRelatedLoading: Boolean = false,

    // Wikitext Editor
    val showEditor: Boolean = false,
    val editorTitle: String = "",
    val editorContent: String = "",
    val editorSummary: String = "",
    val isMinorEdit: Boolean = false,
    val isPublishing: Boolean = false,
    val publishSuccessCelebration: Boolean = false,

    // Media Viewer (Berkas:)
    val activeMedia: WikiMediaItem? = null,
    val isMediaLoading: Boolean = false,

    // Search
    val searchQuery: String = "",
    val searchFilter: SearchFilter = SearchFilter.ALL,
    val searchResults: List<WikiSearchResult> = emptyList(),
    val isSearching: Boolean = false,

    // Commons Gallery
    val commonsGallery: List<WikiMediaItem> = emptyList(),
    val commonsContinue: String? = null,
    val isCommonsLoading: Boolean = false,

    // User & Auth
    val currentUser: WikiUserInfo = WikiUserInfo(),
    val showLoginSheet: Boolean = false,
    val showRegisterSheet: Boolean = false,
    val showForgotPasswordSheet: Boolean = false,
    val showNotificationsSheet: Boolean = false,
    val notifications: List<WikiNotification> = emptyList(),
    val isNotificationsLoading: Boolean = false,
    val failedLoginAttempts: Int = 0,

    // Settings & User Profile Screens
    val showSettingsScreen: Boolean = false,
    val userPreferences: WikiUserPreferences? = null,
    val isPreferencesLoading: Boolean = false,
    val showUserProfileScreen: Boolean = false,
    val userProfileTargetUsername: String = "",
    val userProfileArticle: WikiArticle? = null,
    val userContributions: List<UserContributionItem> = emptyList(),
    val isUserProfileLoading: Boolean = false,

    // Redlink / Missing Article Prompt
    val missingArticlePrompt: String? = null,

    // Info Dialogs (Tentang, Kebijakan Privasi, Penyangkalan)
    val infoModalTitle: String? = null,
    val infoModalContent: String? = null,

    // Reader Preferences & Persisted Settings
    val readerFontSizeSp: Float = 16f,
    val readerUseSerif: Boolean = false,
    val readerFontFamily: String = "sans-serif", // "sans-serif", "serif", "monospace", "cursive"
    val themeMode: String = "system", // "system", "light", "dark"
    val dataSaver: Boolean = false,
    val showSavedArticlesFromAccount: Boolean = false
)

class MippediaViewModel(application: Application) : AndroidViewModel(application) {
    val repository = MippediaRepository(application)
    private val appPrefs = application.getSharedPreferences("mippedia_app_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        UiState(
            showOnboarding = !appPrefs.getBoolean("onboarding_complete_v1", false),
            readerFontSizeSp = appPrefs.getFloat("reader_font_size", 16f),
            readerUseSerif = appPrefs.getBoolean("reader_use_serif", false),
            readerFontFamily = appPrefs.getString("reader_font_family", "sans-serif") ?: "sans-serif",
            themeMode = appPrefs.getString("app_theme_mode", "system") ?: "system",
            dataSaver = appPrefs.getBoolean("data_saver", false)
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Collect language from repository
        viewModelScope.launch {
            repository.currentLanguage.collect { lang ->
                _uiState.update {
                    it.copy(
                        currentLanguage = lang,
                        activeArticle = null
                    )
                }
                loadExploreData()
            }
        }

        // Collect user info
        viewModelScope.launch {
            repository.currentUser.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }

        refreshUserInfo()
        loadExploreData()
        loadCommonsSpotlight()
    }

    fun completeOnboarding() {
        appPrefs.edit().putBoolean("onboarding_complete_v1", true).apply()
        _uiState.update { it.copy(showOnboarding = false) }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
        if (tab == AppTab.COMMONS && _uiState.value.commonsGallery.isEmpty()) {
            loadCommonsGallery()
        }
    }

    fun switchLanguage(lang: WikiLanguage) {
        if (lang != _uiState.value.currentLanguage) {
            repository.setLanguage(lang)
        }
    }

    fun toggleLanguageQuick() {
        val next = if (_uiState.value.currentLanguage == WikiLanguage.INDONESIAN) {
            WikiLanguage.ENGLISH
        } else {
            WikiLanguage.INDONESIAN
        }
        switchLanguage(next)
    }

    fun refreshUserInfo() {
        viewModelScope.launch {
            repository.refreshUserInfo()
        }
    }

    fun loadExploreData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val currentLang = _uiState.value.currentLanguage
            val rcRes = repository.apiClient.getRecentChanges(currentLang)
            val monthlyRes = repository.apiClient.getMonthlyFeatures(currentLang)
            val (mArticle, mImage) = monthlyRes.getOrDefault(Pair(null, null))

            _uiState.update {
                it.copy(
                    recentChanges = rcRes.getOrDefault(emptyList()),
                    monthlyArticle = mArticle,
                    monthlyImage = mImage,
                    isLoading = false
                )
            }
        }
    }

    private fun loadCommonsSpotlight() {
        viewModelScope.launch {
            val res = repository.apiClient.getCommonsImages(limit = 8)
            if (res.isSuccess) {
                _uiState.update { it.copy(commonsSpotlight = res.getOrNull()?.first ?: emptyList()) }
            }
        }
    }

    fun loadCommonsGallery(loadMore: Boolean = false) {
        if (_uiState.value.isCommonsLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCommonsLoading = true) }
            val continueToken = if (loadMore) _uiState.value.commonsContinue else null
            val res = repository.apiClient.getCommonsImages(limit = 24, continueKey = continueToken)
            if (res.isSuccess) {
                val (images, nextContinue) = res.getOrDefault(Pair(emptyList(), null))
                _uiState.update {
                    val currentList = if (loadMore) it.commonsGallery + images else images
                    it.copy(
                        commonsGallery = currentList,
                        commonsContinue = nextContinue,
                        isCommonsLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isCommonsLoading = false) }
            }
        }
    }

    fun openArticle(title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.startsWith("Berkas:", ignoreCase = true) || cleanTitle.startsWith("File:", ignoreCase = true)) {
            openMediaDetail(cleanTitle)
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = "Memuat $cleanTitle...",
                    error = null
                )
            }
            val res = repository.apiClient.getArticle(cleanTitle, _uiState.value.currentLanguage)
            if (res.isSuccess) {
                val article = res.getOrNull()
                val isSaved = article != null && (repository.isBookmarked(article.title) || repository.isBookmarked(cleanTitle))
                _uiState.update {
                    it.copy(
                        activeArticle = article,
                        isBookmarked = isSaved,
                        isLoading = false,
                        loadingMessage = ""
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = "",
                        error = res.exceptionOrNull()?.localizedMessage ?: "Gagal memuat artikel"
                    )
                }
            }
        }
    }

    fun closeArticle() {
        _uiState.update { it.copy(activeArticle = null, showTocSheet = false, showRelatedSheet = false) }
    }

    fun toggleBookmarkCurrentArticle() {
        val article = _uiState.value.activeArticle ?: return
        repository.toggleBookmark(article)
        val nowSaved = repository.isBookmarked(article.title)
        _uiState.update { it.copy(isBookmarked = nowSaved) }
        showToast(if (nowSaved) "Artikel disimpan ke Akun > Artikel Tersimpan" else "Artikel dihapus dari simpanan")
    }

    fun openRandomArticle() {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true, loadingMessage = "Mencari artikel acak...") }
            val res = repository.apiClient.getRandomArticle(_uiState.value.currentLanguage)
            _uiState.update { it.copy(isActionLoading = false, loadingMessage = "") }
            if (res.isSuccess) {
                openArticle(res.getOrDefault(if (_uiState.value.currentLanguage == WikiLanguage.ENGLISH) "Main Page" else "Halaman Utama"))
            } else {
                showToast("Gagal memuat artikel acak")
            }
        }
    }

    fun openRelatedPages() {
        val article = _uiState.value.activeArticle ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showRelatedSheet = true, isRelatedLoading = true, relatedArticles = article.relatedArticles) }
            val res = repository.apiClient.getBacklinks(article.title, _uiState.value.currentLanguage)
            _uiState.update {
                val combined = (article.relatedArticles + res.getOrDefault(emptyList())).distinct()
                it.copy(
                    isRelatedLoading = false,
                    relatedArticles = combined
                )
            }
        }
    }

    fun closeRelatedPages() {
        _uiState.update { it.copy(showRelatedSheet = false) }
    }

    fun openToc(show: Boolean) {
        _uiState.update { it.copy(showTocSheet = show) }
    }

    fun openMediaDetail(titleOrItem: Any) {
        if (titleOrItem is WikiMediaItem) {
            _uiState.update { it.copy(activeMedia = titleOrItem) }
            return
        }
        val rawInput = (titleOrItem as? String)?.trim() ?: return
        if (rawInput.isBlank()) return

        // 1. Determine title and direct URL
        var queryTitle: String
        var directUrl: String? = null

        if (rawInput.startsWith("http://", ignoreCase = true) || rawInput.startsWith("https://", ignoreCase = true)) {
            directUrl = rawInput
            val decodedUrl = try { java.net.URLDecoder.decode(rawInput, "UTF-8") } catch (e: Exception) { rawInput }
            val path = android.net.Uri.parse(decodedUrl).path ?: ""
            val filename = if (path.contains("/thumb/")) {
                val segments = path.split("/").filter { it.isNotBlank() }
                val thumbIdx = segments.indexOf("thumb")
                if (thumbIdx >= 0 && segments.size > thumbIdx + 3) {
                    segments[thumbIdx + 3]
                } else {
                    segments.lastOrNull()?.replace(Regex("^\\d+px-"), "") ?: "Berkas Media"
                }
            } else {
                path.substringAfterLast("/").ifEmpty { "Berkas Media" }
            }
            queryTitle = if (filename.startsWith("File:", ignoreCase = true) || filename.startsWith("Berkas:", ignoreCase = true)) {
                filename
            } else {
                "File:$filename"
            }
        } else {
            val decoded = try { java.net.URLDecoder.decode(rawInput, "UTF-8") } catch (e: Exception) { rawInput }
            queryTitle = if (decoded.startsWith("File:", ignoreCase = true) || decoded.startsWith("Berkas:", ignoreCase = true)) {
                decoded
            } else {
                "File:$decoded"
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isMediaLoading = true) }
            val res = repository.apiClient.getImageInfo(queryTitle)
            if (res.isSuccess && res.getOrNull() != null) {
                _uiState.update { it.copy(activeMedia = res.getOrNull(), isMediaLoading = false) }
            } else if (directUrl != null) {
                // Graceful fallback: render the direct URL in MediaViewerSheet so user can still view and download
                val fallbackMedia = WikiMediaItem(
                    title = queryTitle.replace("File:", "").replace("Berkas:", "").replace("_", " "),
                    url = directUrl,
                    thumbUrl = directUrl,
                    width = 0,
                    height = 0,
                    sizeBytes = 0,
                    mime = if (directUrl.endsWith(".svg", ignoreCase = true)) "image/svg+xml" else "image/jpeg",
                    user = "Mippedia Community",
                    timestamp = "",
                    descriptionUrl = directUrl
                )
                _uiState.update { it.copy(activeMedia = fallbackMedia, isMediaLoading = false) }
            } else {
                _uiState.update { it.copy(activeMedia = null, isMediaLoading = false) }
                showToast("Informasi berkas tidak dapat ditemukan di Commons")
            }
        }
    }

    fun closeMediaDetail() {
        _uiState.update { it.copy(activeMedia = null) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            executeSearch(query)
        }
    }

    fun updateSearchFilter(filter: SearchFilter) {
        _uiState.update { it.copy(searchFilter = filter) }
        if (_uiState.value.searchQuery.isNotBlank()) {
            executeSearch(_uiState.value.searchQuery)
        }
    }

    fun executeSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            repository.addSearchHistory(query)
            val res = repository.apiClient.search(query, _uiState.value.currentLanguage, _uiState.value.searchFilter)
            _uiState.update {
                it.copy(
                    searchResults = res.getOrDefault(emptyList()),
                    isSearching = false
                )
            }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
    }

    fun startEditingArticle() {
        val article = _uiState.value.activeArticle ?: return
        if (article.isProtected) {
            showToast("Halaman ini dilindungi (${article.protectionReason})")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true, loadingMessage = "Memuat wikitext artikel...") }
            val rawRes = repository.apiClient.getRawWikitext(article.title, _uiState.value.currentLanguage)
            val rawContent = rawRes.getOrDefault("")
            _uiState.update {
                it.copy(
                    isActionLoading = false,
                    loadingMessage = "",
                    showEditor = true,
                    editorTitle = article.title,
                    editorContent = rawContent,
                    editorSummary = "Disunting melalui aplikasi Android Mippedia",
                    isMinorEdit = false
                )
            }
        }
    }

    fun updateEditorContent(content: String) {
        _uiState.update { it.copy(editorContent = content) }
    }

    fun updateEditorSummary(summary: String) {
        _uiState.update { it.copy(editorSummary = summary) }
    }

    fun toggleMinorEdit(minor: Boolean) {
        _uiState.update { it.copy(isMinorEdit = minor) }
    }

    fun appendEditorSyntax(prefix: String, suffix: String = "") {
        val current = _uiState.value.editorContent
        _uiState.update { it.copy(editorContent = "$current\n$prefix$suffix") }
    }

    fun closeEditor() {
        _uiState.update { it.copy(showEditor = false, isPublishing = false, publishSuccessCelebration = false) }
    }

    fun publishArticle() {
        val title = _uiState.value.editorTitle
        val content = _uiState.value.editorContent
        val summary = _uiState.value.editorSummary
        val isMinor = _uiState.value.isMinorEdit

        viewModelScope.launch {
            _uiState.update { it.copy(isPublishing = true) }
            val res = repository.apiClient.saveArticle(title, content, summary, isMinor, _uiState.value.currentLanguage)
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isPublishing = false,
                        publishSuccessCelebration = true,
                        successNotice = "Suntingan Anda telah berhasil diterbitkan!"
                    )
                }
                delay(1200)
                _uiState.update { it.copy(showEditor = false, publishSuccessCelebration = false) }
                openArticle(title)
                loadExploreData()
            } else {
                _uiState.update {
                    it.copy(
                        isPublishing = false,
                        error = res.exceptionOrNull()?.localizedMessage ?: "Gagal menerbitkan suntingan"
                    )
                }
            }
        }
    }

    fun showLogin(show: Boolean) {
        _uiState.update { it.copy(showLoginSheet = show, showRegisterSheet = false, showForgotPasswordSheet = false) }
    }

    fun showRegister(show: Boolean) {
        _uiState.update { it.copy(showRegisterSheet = show, showLoginSheet = false, showForgotPasswordSheet = false) }
    }

    fun showForgotPassword(show: Boolean) {
        _uiState.update { it.copy(showForgotPasswordSheet = show, showLoginSheet = false, showRegisterSheet = false) }
    }

    fun showNotifications(show: Boolean) {
        _uiState.update { it.copy(showNotificationsSheet = show) }
        if (show) {
            loadNotifications()
        }
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isNotificationsLoading = true) }
            val res = repository.apiClient.getNotifications(_uiState.value.currentLanguage)
            _uiState.update {
                it.copy(
                    isNotificationsLoading = false,
                    notifications = res.getOrDefault(emptyList())
                )
            }
        }
    }

    fun performLogin(user: String, pass: String) {
        if (user.isBlank() || pass.isBlank()) {
            showToast("Nama pengguna dan kata sandi wajib diisi")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true, loadingMessage = "Masuk ke Mippedia...") }
            val res = repository.apiClient.login(user.trim(), pass, _uiState.value.currentLanguage)
            _uiState.update { it.copy(isActionLoading = false, loadingMessage = "") }
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        showLoginSheet = false,
                        failedLoginAttempts = 0,
                        successNotice = "Selamat datang kembali, ${res.getOrNull()}!"
                    )
                }
                repository.refreshUserInfo()
                loadUserPreferences()
            } else {
                val newFailed = _uiState.value.failedLoginAttempts + 1
                _uiState.update {
                    it.copy(
                        failedLoginAttempts = newFailed,
                        error = res.exceptionOrNull()?.localizedMessage ?: "Gagal masuk"
                    )
                }
            }
        }
    }

    fun performRegister(user: String, pass: String, email: String) {
        if (user.isBlank() || pass.isBlank()) {
            showToast("Nama pengguna dan kata sandi wajib diisi")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true, loadingMessage = "Mendaftarkan akun...") }
            val res = repository.apiClient.register(user.trim(), pass, email.trim(), _uiState.value.currentLanguage)
            _uiState.update { it.copy(isActionLoading = false, loadingMessage = "") }
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        showRegisterSheet = false,
                        successNotice = res.getOrNull()
                    )
                }
                repository.refreshUserInfo()
                loadUserPreferences()
            } else {
                _uiState.update {
                    it.copy(error = res.exceptionOrNull()?.localizedMessage ?: "Gagal mendaftar")
                }
            }
        }
    }

    fun performResetPassword(userOrEmail: String) {
        if (userOrEmail.isBlank()) {
            showToast("Masukkan nama pengguna atau email")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true, loadingMessage = "Mengirim instruksi reset...") }
            val res = repository.apiClient.resetPassword(userOrEmail, _uiState.value.currentLanguage)
            _uiState.update { it.copy(isActionLoading = false, loadingMessage = "") }
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        showForgotPasswordSheet = false,
                        successNotice = res.getOrNull()
                    )
                }
            } else {
                _uiState.update {
                    it.copy(error = res.exceptionOrNull()?.localizedMessage ?: "Gagal mereset kata sandi")
                }
            }
        }
    }

    fun performLogout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true, loadingMessage = "Keluar...") }
            repository.apiClient.logout(_uiState.value.currentLanguage)
            _uiState.update {
                it.copy(
                    isActionLoading = false,
                    loadingMessage = "",
                    userPreferences = null,
                    showSettingsScreen = false,
                    showUserProfileScreen = false
                )
            }
            repository.refreshUserInfo()
            showToast("Anda telah keluar dari akun Mippedia")
        }
    }

    // Settings screen handling
    fun openSettings() {
        _uiState.update { it.copy(showSettingsScreen = true) }
        loadUserPreferences()
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettingsScreen = false) }
    }

    fun loadUserPreferences() {
        viewModelScope.launch {
            _uiState.update { it.copy(isPreferencesLoading = true) }
            val res = repository.apiClient.getUserPreferences(_uiState.value.currentLanguage)
            _uiState.update {
                it.copy(
                    isPreferencesLoading = false,
                    userPreferences = res.getOrNull()
                )
            }
        }
    }

    // User Profile screen handling
    fun openUserProfile(username: String) {
        _uiState.update {
            it.copy(
                showUserProfileScreen = true,
                userProfileTargetUsername = username
            )
        }
        loadUserProfile(username)
    }

    fun closeUserProfile() {
        _uiState.update { it.copy(showUserProfileScreen = false) }
    }

    fun loadUserProfile(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUserProfileLoading = true) }
            val artRes = repository.apiClient.getArticle("Pengguna:$username", _uiState.value.currentLanguage)
            val contribsRes = repository.apiClient.getUserContributions(username, _uiState.value.currentLanguage)
            _uiState.update {
                it.copy(
                    isUserProfileLoading = false,
                    userProfileArticle = artRes.getOrNull(),
                    userContributions = contribsRes.getOrDefault(emptyList())
                )
            }
        }
    }

    // Redlink / Missing Article Dialog handling
    fun openMissingArticlePrompt(title: String) {
        _uiState.update { it.copy(missingArticlePrompt = title) }
    }

    fun closeMissingArticlePrompt() {
        _uiState.update { it.copy(missingArticlePrompt = null) }
    }

    // Real Cache Clearing
    fun clearAppCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true, loadingMessage = "Membersihkan cache...") }
            try {
                // Clear OkHttp cache
                repository.apiClient.clearHttpCache()
                // Clear Coil image cache
                val imageLoader = Coil.imageLoader(getApplication())
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
                // Clear internal cache directory
                getApplication<Application>().cacheDir?.deleteRecursively()
                delay(300)
                _uiState.update {
                    it.copy(
                        isActionLoading = false,
                        successNotice = "Cache aplikasi (gambar & halaman) berhasil dibersihkan!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isActionLoading = false, error = "Gagal membersihkan cache: ${e.message}") }
            }
        }
    }

    fun removeSavedArticle(title: String) {
        repository.removeSavedArticle(title)
    }

    fun showInfoModal(title: String, content: String) {
        _uiState.update { it.copy(infoModalTitle = title, infoModalContent = content) }
    }

    fun closeInfoModal() {
        _uiState.update { it.copy(infoModalTitle = null, infoModalContent = null) }
    }

    // Settings & Reader Preferences Persistence
    fun setReaderFontSize(size: Float) {
        val clamped = size.coerceIn(13f, 26f)
        appPrefs.edit().putFloat("reader_font_size", clamped).apply()
        _uiState.update { it.copy(readerFontSizeSp = clamped) }
    }

    fun toggleReaderSerif() {
        setReaderSerif(!_uiState.value.readerUseSerif)
    }

    fun setReaderSerif(useSerif: Boolean) {
        appPrefs.edit().putBoolean("reader_use_serif", useSerif).apply()
        val fam = if (useSerif) "serif" else "sans-serif"
        appPrefs.edit().putString("reader_font_family", fam).apply()
        _uiState.update { it.copy(readerUseSerif = useSerif, readerFontFamily = fam) }
    }

    fun setFontFamily(family: String) {
        appPrefs.edit().putString("reader_font_family", family).apply()
        val isSerif = family.equals("serif", ignoreCase = true)
        appPrefs.edit().putBoolean("reader_use_serif", isSerif).apply()
        _uiState.update { it.copy(readerFontFamily = family, readerUseSerif = isSerif) }
    }

    fun setReaderFontFamily(family: String) {
        setFontFamily(family)
    }

    fun setThemeMode(mode: String) {
        appPrefs.edit().putString("app_theme_mode", mode).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setDataSaver(enabled: Boolean) {
        appPrefs.edit().putBoolean("data_saver", enabled).apply()
        _uiState.update { it.copy(dataSaver = enabled) }
    }


    fun openSavedArticlesFromAccount() {
        _uiState.update { it.copy(showSavedArticlesFromAccount = true) }
    }

    fun closeSavedArticlesFromAccount() {
        _uiState.update { it.copy(showSavedArticlesFromAccount = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccessNotice() {
        _uiState.update { it.copy(successNotice = null) }
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(successNotice = msg) }
    }
}
