package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.WikiLanguage
import com.example.model.WikiSearchResult
import com.example.ui.*
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Coil with SVG decoder support
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        enableEdgeToEdge()
        setContent {
            val viewModel: MippediaViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val isDark = when (uiState.themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            MyApplicationTheme(darkTheme = isDark) {
                MippediaMainApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MippediaMainApp(viewModel: MippediaViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedArticles by viewModel.repository.savedArticles.collectAsStateWithLifecycle()
    val searchHistory by viewModel.repository.searchHistory.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Handle Back Press cleanly
    BackHandler(
        enabled = uiState.showSavedArticlesFromAccount || uiState.showSettingsScreen || uiState.showUserProfileScreen || uiState.activeArticle != null || uiState.activeMedia != null || uiState.showEditor
    ) {
        when {
            uiState.showEditor -> viewModel.closeEditor()
            uiState.showSavedArticlesFromAccount -> viewModel.closeSavedArticlesFromAccount()
            uiState.showSettingsScreen -> viewModel.closeSettings()
            uiState.showUserProfileScreen -> viewModel.closeUserProfile()
            uiState.activeMedia != null -> viewModel.closeMediaDetail()
            uiState.activeArticle != null -> viewModel.closeArticle()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.activeArticle != null) {
                        Text(
                            text = uiState.activeArticle!!.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.mippedia_logo),
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                            )
                            Text(
                                text = "Mippedia",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (uiState.activeArticle != null) {
                        IconButton(onClick = { viewModel.closeArticle() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali"
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.activeArticle != null) {
                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, uiState.activeArticle!!.title)
                                putExtra(Intent.EXTRA_TEXT, "${uiState.activeArticle!!.title} - ${uiState.currentLanguage.baseUrl}/wiki/${uiState.activeArticle!!.title}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Artikel"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Bagikan")
                        }
                    } else {
                        LanguageSwitchPill(
                            currentLanguage = uiState.currentLanguage,
                            onToggle = { viewModel.toggleLanguageQuick() },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        IconButton(onClick = { viewModel.openRandomArticle() }) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Acak")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            if (uiState.activeArticle == null && !uiState.showSavedArticlesFromAccount && !uiState.showSettingsScreen && !uiState.showUserProfileScreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.EXPLORE,
                        onClick = { viewModel.selectTab(AppTab.EXPLORE) },
                        icon = {
                            Icon(
                                imageVector = if (uiState.currentTab == AppTab.EXPLORE) Icons.Filled.Explore else Icons.Outlined.Explore,
                                contentDescription = "Jelajahi"
                            )
                        },
                        label = { Text("Jelajahi") }
                    )

                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.SEARCH,
                        onClick = { viewModel.selectTab(AppTab.SEARCH) },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Cari"
                            )
                        },
                        label = { Text("Cari") }
                    )

                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.COMMONS,
                        onClick = { viewModel.selectTab(AppTab.COMMONS) },
                        icon = {
                            Icon(
                                imageVector = if (uiState.currentTab == AppTab.COMMONS) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                                contentDescription = "Commons"
                            )
                        },
                        label = { Text("Commons") }
                    )

                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.ACCOUNT,
                        onClick = { viewModel.selectTab(AppTab.ACCOUNT) },
                        icon = {
                            Icon(
                                imageVector = if (uiState.currentTab == AppTab.ACCOUNT) Icons.Filled.Person else Icons.Outlined.Person,
                                contentDescription = "Akun"
                            )
                        },
                        label = { Text("Akun") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main Content Area
            if (uiState.showSavedArticlesFromAccount) {
                SavedArticlesScreen(
                    savedArticles = savedArticles,
                    onArticleClick = { viewModel.openArticle(it) },
                    onRemoveArticle = { title -> viewModel.removeSavedArticle(title) },
                    onBack = { viewModel.closeSavedArticlesFromAccount() }
                )
            } else if (uiState.showSettingsScreen) {
                SettingsScreen(
                    userPreferences = uiState.userPreferences,
                    isLoadingPrefs = uiState.isPreferencesLoading,
                    fontSizeSp = uiState.readerFontSizeSp,
                    useSerif = uiState.readerUseSerif,
                    fontFamily = uiState.readerFontFamily,
                    themeMode = uiState.themeMode,
                    dataSaver = uiState.dataSaver,
                    onFontSizeChange = { viewModel.setReaderFontSize(it) },
                    onSerifChange = { viewModel.setReaderSerif(it) },
                    onFontFamilyChange = { viewModel.setReaderFontFamily(it) },
                    onThemeModeChange = { viewModel.setThemeMode(it) },
                    onDataSaverChange = { viewModel.setDataSaver(it) },
                    onViewProfile = { username -> viewModel.openUserProfile(username) },
                    onViewContributions = { username -> viewModel.openUserProfile(username) },
                    onLogout = { viewModel.performLogout() },
                    onBack = { viewModel.closeSettings() },
                    onClearCache = { viewModel.clearAppCache() }
                )
            } else if (uiState.showUserProfileScreen) {
                UserProfileScreen(
                    username = uiState.userProfileTargetUsername,
                    userPreferences = uiState.userPreferences,
                    userArticle = uiState.userProfileArticle,
                    contributions = uiState.userContributions,
                    isLoading = uiState.isUserProfileLoading,
                    currentLanguage = uiState.currentLanguage,
                    onArticleClick = { viewModel.openArticle(it) },
                    onBack = { viewModel.closeUserProfile() }
                )
            } else if (uiState.activeArticle != null) {
                ArticleRenderer(
                    article = uiState.activeArticle!!,
                    currentUser = uiState.currentUser,
                    isBookmarked = uiState.isBookmarked,
                    fontSizeSp = uiState.readerFontSizeSp,
                    useSerif = uiState.readerUseSerif,
                    fontFamilyName = uiState.readerFontFamily,
                    onEditClick = { viewModel.startEditingArticle() },
                    onLoginClick = { viewModel.showLogin(true) },
                    onBookmarkClick = { viewModel.toggleBookmarkCurrentArticle() },
                    onTocClick = { viewModel.openToc(true) },
                    onRelatedClick = { viewModel.openRelatedPages() },
                    onImageClick = { imageUrl -> viewModel.openMediaDetail(imageUrl) },
                    onCategoryClick = { catTitle -> viewModel.openArticle(catTitle) },
                    onArticleLinkClick = { linkTitle -> viewModel.openArticle(linkTitle) },
                    onRedlinkClick = { missingTitle -> viewModel.openMissingArticlePrompt(missingTitle) },
                    onUserClick = { username -> viewModel.openUserProfile(username) }
                )
            } else {
                when (uiState.currentTab) {
                    AppTab.EXPLORE -> {
                        ExploreScreen(
                            currentLanguage = uiState.currentLanguage,
                            isLoading = uiState.isLoading,
                            recentChanges = uiState.recentChanges,
                            monthlyArticle = uiState.monthlyArticle,
                            monthlyImage = uiState.monthlyImage,
                            commonsSpotlight = uiState.commonsSpotlight,
                            onArticleClick = { viewModel.openArticle(it) },
                            onMediaClick = { viewModel.openMediaDetail(it) },
                            onRandomClick = { viewModel.openRandomArticle() },
                            onCategoryClick = { viewModel.openArticle(it) },
                            onRefresh = { viewModel.loadExploreData() },
                            onSearchClick = { viewModel.selectTab(AppTab.SEARCH) }
                        )
                    }
                    AppTab.SEARCH -> {
                        SearchScreen(
                            searchQuery = uiState.searchQuery,
                            selectedFilter = uiState.searchFilter,
                            isSearching = uiState.isSearching,
                            searchResults = uiState.searchResults,
                            searchHistory = searchHistory,
                            onQueryChange = { viewModel.updateSearchQuery(it) },
                            onFilterChange = { viewModel.updateSearchFilter(it) },
                            onSearchResultClick = { result ->
                                if (result.ns == 6 || result.title.startsWith("Berkas:", ignoreCase = true) || result.title.startsWith("File:", ignoreCase = true)) {
                                    viewModel.openMediaDetail(result.title)
                                } else {
                                    viewModel.openArticle(result.title)
                                }
                            },
                            onHistoryItemClick = { term ->
                                viewModel.updateSearchQuery(term)
                                viewModel.executeSearch(term)
                            },
                            onClearHistory = { viewModel.repository.clearSearchHistory() },
                            onClearQuery = { viewModel.clearSearch() }
                        )
                    }
                    AppTab.COMMONS -> {
                        CommonsGalleryScreen(
                            gallery = uiState.commonsGallery,
                            isLoading = uiState.isCommonsLoading,
                            onMediaClick = { viewModel.openMediaDetail(it) },
                            onLoadMore = { viewModel.loadCommonsGallery(loadMore = true) }
                        )
                    }
                    AppTab.SAVED -> {
                        SavedArticlesScreen(
                            savedArticles = savedArticles,
                            onArticleClick = { viewModel.openArticle(it) },
                            onRemoveArticle = { title -> viewModel.removeSavedArticle(title) }
                        )
                    }
                    AppTab.ACCOUNT -> {
                        AccountScreen(
                            currentUser = uiState.currentUser,
                            currentLanguage = uiState.currentLanguage,
                            savedArticlesCount = savedArticles.size,
                            onOpenSavedArticles = { viewModel.openSavedArticlesFromAccount() },
                            onSwitchLanguage = { viewModel.toggleLanguageQuick() },
                            onLoginClick = { viewModel.showLogin(true) },
                            onRegisterClick = { viewModel.showRegister(true) },
                            onForgotPasswordClick = { viewModel.showForgotPassword(true) },
                            onNotificationsClick = { viewModel.showNotifications(true) },
                            onSettingsClick = { viewModel.openSettings() },
                            onUserProfileClick = { username -> viewModel.openUserProfile(username) },
                            onLogoutClick = { viewModel.performLogout() },
                            onAboutClick = {
                                viewModel.showInfoModal(
                                    title = "Tentang Mippedia",
                                    content = "Mippedia adalah proyek ensiklopedia daring bebas dan multibahasa yang dikelola dan dikembangkan secara independen oleh Mippedia Community.\n\nSistem Mippedia terdiri dari:\n• Mippedia Indonesia: Edisi Bahasa Indonesia\n• Mippedia English: Edisi Bahasa Inggris\n• Mippedia Commons: Repositori berkas media dan gambar bersama untuk seluruh edisi bahasa.\n\nAplikasi Android ini menghadirkan pengalaman membaca dan berkontribusi yang bersih, cepat, terstruktur, dan modern."
                                )
                            },
                            onPrivacyClick = {
                                viewModel.showInfoModal(
                                    title = "Kebijakan Privasi",
                                    content = "Privasi Anda adalah prioritas kami di Mippedia.\n\n1. Pembacaan Bebas: Anda dapat membaca seluruh konten ensiklopedia tanpa memerlukan akun atau memberikan data pribadi.\n2. Akun Sementara & Anonim: Kontributor yang menyunting tanpa akun akan dicatat alamat IP publiknya untuk menjaga integritas artikel dan mencegah vandalisme.\n3. Akun Terdaftar: Kami hanya menyimpan nama pengguna dan kata sandi yang terenkripsi aman di peladen MediaWiki.\n4. Kuki: Aplikasi menggunakan kuki sesi yang disimpan secara lokal di perangkat Anda untuk menjaga status masuk Anda."
                                )
                            },
                            onDisclaimerClick = {
                                viewModel.showInfoModal(
                                    title = "Penyangkalan Umum (Disclaimer)",
                                    content = "Konten dalam Mippedia dibuat dan disunting secara kolaboratif oleh komunitas terbuka sukarelawan. Walaupun komunitas berupaya memastikan akurasi data, Mippedia tidak memberikan jaminan mutlak atas kebenaran, kelengkapan, atau kesesuaian informasi untuk tujuan hukum, medis, atau profesional tertentu.\n\nSegala bentuk penggunaan informasi merupakan tanggung jawab pribadi pembaca."
                                )
                            }
                        )
                    }
                }
            }

            // Success Notice Toast / Banner
            if (uiState.successNotice != null) {
                SuccessCelebrationBanner(
                    message = uiState.successNotice!!,
                    onDismiss = { viewModel.clearSuccessNotice() },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            // Error Notice Banner
            if (uiState.error != null) {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }
            }

            // Inline action loading overlay
            if (uiState.isActionLoading) {
                ActionLoadingDialog(message = uiState.loadingMessage)
            }
        }
    }

    // Modal BottomSheets
    if (uiState.showEditor) {
        ArticleEditorSheet(
            title = uiState.editorTitle,
            content = uiState.editorContent,
            summary = uiState.editorSummary,
            isMinor = uiState.isMinorEdit,
            isPublishing = uiState.isPublishing,
            currentUser = uiState.currentUser,
            onContentChange = { viewModel.updateEditorContent(it) },
            onSummaryChange = { viewModel.updateEditorSummary(it) },
            onMinorChange = { viewModel.toggleMinorEdit(it) },
            onInsertSyntax = { prefix, suffix -> viewModel.appendEditorSyntax(prefix, suffix) },
            onPublish = { viewModel.publishArticle() },
            onClose = { viewModel.closeEditor() }
        )
    }

    if (uiState.activeMedia != null) {
        MediaViewerSheet(
            media = uiState.activeMedia!!,
            onClose = { viewModel.closeMediaDetail() }
        )
    }

    if (uiState.showLoginSheet) {
        LoginBottomSheet(
            isLoading = uiState.isActionLoading,
            onLogin = { u, p -> viewModel.performLogin(u, p) },
            onGoToRegister = { viewModel.showRegister(true) },
            onGoToForgotPassword = { viewModel.showForgotPassword(true) },
            onClose = { viewModel.showLogin(false) }
        )
    }

    if (uiState.showRegisterSheet) {
        RegisterBottomSheet(
            isLoading = uiState.isActionLoading,
            onRegister = { u, p, email -> viewModel.performRegister(u, p, email) },
            onGoToLogin = { viewModel.showLogin(true) },
            onClose = { viewModel.showRegister(false) }
        )
    }

    // Missing Article / Redlink Prompt Dialog
    if (uiState.missingArticlePrompt != null) {
        val missingTitle = uiState.missingArticlePrompt!!
        AlertDialog(
            onDismissRequest = { viewModel.closeMissingArticlePrompt() },
            icon = {
                Icon(
                    imageVector = Icons.Default.PostAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Halaman Belum Dibuat",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "Halaman \"$missingTitle\" belum tersedia di ensiklopedia Mippedia.\n\nApakah Anda ingin membuka situs web Mippedia untuk membuat dan mengawali artikel baru ini?",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val encoded = java.net.URLEncoder.encode(missingTitle, "UTF-8")
                        val createUrl = "${uiState.currentLanguage.baseUrl}/w/index.php?title=$encoded&action=edit"
                        viewModel.closeMissingArticlePrompt()
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(createUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buat di Website")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.closeMissingArticlePrompt() },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                ) {
                    Text("Tutup")
                }
            }
        )
    }

    if (uiState.showForgotPasswordSheet) {
        ForgotPasswordBottomSheet(
            isLoading = uiState.isActionLoading,
            onReset = { userOrEmail -> viewModel.performResetPassword(userOrEmail) },
            onGoToLogin = { viewModel.showLogin(true) },
            onClose = { viewModel.showForgotPassword(false) }
        )
    }

    if (uiState.showTocSheet && uiState.activeArticle != null) {
        TableOfContentsSheet(
            sections = uiState.activeArticle!!.sections,
            onSectionClick = { /* section clicked */ },
            onClose = { viewModel.openToc(false) }
        )
    }

    if (uiState.showRelatedSheet) {
        RelatedPagesSheet(
            relatedPages = uiState.relatedArticles,
            isLoading = uiState.isRelatedLoading,
            onPageClick = { title -> viewModel.openArticle(title) },
            onClose = { viewModel.closeRelatedPages() }
        )
    }

    if (uiState.showNotificationsSheet) {
        NotificationsSheet(
            notifications = uiState.notifications,
            isLoading = uiState.isNotificationsLoading,
            onClose = { viewModel.showNotifications(false) }
        )
    }

    if (uiState.infoModalTitle != null && uiState.infoModalContent != null) {
        InfoModalDialog(
            title = uiState.infoModalTitle!!,
            content = uiState.infoModalContent!!,
            onClose = { viewModel.closeInfoModal() }
        )
    }

    if (uiState.showOnboarding) {
        OnboardingDialog(
            onComplete = { viewModel.completeOnboarding() }
        )
    }
}
