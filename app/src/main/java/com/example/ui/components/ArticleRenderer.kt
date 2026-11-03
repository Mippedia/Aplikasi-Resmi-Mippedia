package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontStyle
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.model.WikiArticle
import com.example.model.WikiInfobox
import com.example.model.WikiUserInfo
import com.example.ui.theme.MippediaAmber
import com.example.ui.theme.MippediaBluePrimary
import com.example.ui.theme.MippediaTealAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleRenderer(
    article: WikiArticle,
    currentUser: WikiUserInfo = WikiUserInfo(),
    isBookmarked: Boolean,
    fontSizeSp: Float,
    useSerif: Boolean,
    onEditClick: () -> Unit,
    onLoginClick: () -> Unit = {},
    onBookmarkClick: () -> Unit,
    onTocClick: () -> Unit,
    onRelatedClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    onArticleLinkClick: (String) -> Unit,
    onRedlinkClick: (String) -> Unit = {},
    onUserClick: (String) -> Unit = {},
    fontFamilyName: String = "sans-serif",
    onFontSizeChange: (Float) -> Unit = {},
    onFontFamilyChange: (String) -> Unit = {},
    onUseSerifChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    var showFontDialog by remember { mutableStateOf(false) }

    val isCategory = article.title.startsWith("Kategori:", ignoreCase = true) || article.title.startsWith("Category:", ignoreCase = true)
    val isProtectedOrCategory = article.isProtected || isCategory
    var showProtectionDialog by remember { mutableStateOf(false) }
    var protectionDialogMessage by remember { mutableStateOf("") }
    var showAnonEditDialog by remember { mutableStateOf(false) }
    var isHeroImageError by remember(article.heroImageUrl) { mutableStateOf(false) }

    // Protection Information Dialog
    if (showProtectionDialog) {
        AlertDialog(
            onDismissRequest = { showProtectionDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MippediaAmber,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = if (isCategory) "Halaman Kategori Dilindungi" else "Halaman Dilindungi",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = protectionDialogMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showProtectionDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Tutup")
                }
            }
        )
    }

    // Anonymous Edit Confirmation Dialog (Requirement 6)
    if (showAnonEditDialog) {
        AlertDialog(
            onDismissRequest = { showAnonEditDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MippediaAmber,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Anda Belum Masuk Log",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "Anda saat ini menyunting sebagai akun sementara / anonim.\n\nAlamat IP publik Anda (${currentUser.username}) akan dicatat secara permanen dalam riwayat revisi halaman ini dan dapat dilihat oleh publik.\n\nApakah Anda ingin masuk log terlebih dahulu untuk menjaga privasi, atau tetap melanjutkan menyunting?",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAnonEditDialog = false
                        onEditClick()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Lanjutkan Menyunting")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showAnonEditDialog = false
                        onLoginClick()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Masuk Log Dulu")
                }
            }
        )
    }

    // Interactive Font & Typography Settings Modal (Requirement 8)
    if (showFontDialog) {
        ModalBottomSheet(
            onDismissRequest = { showFontDialog = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tampilan Teks & Tipografi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = { showFontDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                // 1. Font Size Stepper & Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ukuran Tulisan Artikel",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${fontSizeSp.toInt()} sp",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                if (fontSizeSp > 13f) {
                                    onFontSizeChange(fontSizeSp - 1f)
                                }
                            },
                            enabled = fontSizeSp > 13f
                        ) {
                            Text("A-", fontWeight = FontWeight.Bold)
                        }

                        Slider(
                            value = fontSizeSp,
                            onValueChange = { onFontSizeChange(it) },
                            valueRange = 13f..26f,
                            steps = 12,
                            modifier = Modifier.weight(1f)
                        )

                        FilledTonalIconButton(
                            onClick = {
                                if (fontSizeSp < 26f) {
                                    onFontSizeChange(fontSizeSp + 1f)
                                }
                            },
                            enabled = fontSizeSp < 26f
                        ) {
                            Text("A+", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 2. Font Family Options
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Jenis Huruf (Font Family)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    val fonts = listOf(
                        Triple("sans-serif", "Sans-Serif", false),
                        Triple("serif", "Serif", true),
                        Triple("monospace", "Monospace", false)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fonts.forEach { (key, label, isSerif) ->
                            val isSelected = (isSerif && useSerif) || (!useSerif && fontFamilyName.equals(key, ignoreCase = true))
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    onFontFamilyChange(key)
                                    onUseSerifChange(isSerif)
                                },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 3. Live Reading Sample
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Mippedia adalah ensiklopedia daring bebas dan multibahasa yang dikelola secara independen oleh Mippedia Community.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = (fontSizeSp - 2).sp,
                            fontFamily = if (useSerif) FontFamily.Serif else if (fontFamilyName == "monospace") FontFamily.Monospace else FontFamily.Default
                        ),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Image at the VERY TOP (Collapses immediately on error so no blank box appears)
        if (!article.heroImageUrl.isNullOrBlank() && !isHeroImageError) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clickable { onImageClick(article.heroImageUrl) }
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(article.heroImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = article.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .padding(8.dp),
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                SmallSpinningLoader()
                            }
                        },
                        onError = {
                            isHeroImageError = true
                        }
                    )
                }
            }
        }

        // 2. Article Title & Short Description
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = article.displayTitle,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (useSerif) FontFamily.Serif else FontFamily.Default
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Redirect badge (Requirement 8)
                if (!article.redirectedFrom.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Directions,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Dialihkan dari ${article.redirectedFrom}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }

                // Short Description right below title: {{SHORTDESC:...}}
                if (!article.shortDescription.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = article.shortDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Real-time last edited metadata with clickable user badge
                if (article.lastEditor.isNotBlank() || article.lastModified.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (article.lastModified.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HistoryEdu,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    val formattedTime = formatIsoTimestamp(article.lastModified)
                                    Text(
                                        text = "Terakhir disunting: $formattedTime",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (article.lastEditor.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Disunting oleh:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier.clickable { onUserClick(article.lastEditor) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = article.lastEditor,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Protection Status is now handled cleanly via lock icon & dialog (no intrusive banner)
            }
        }

        // 3. Action Toolbar (App-like Wikipedia action buttons)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Edit / Protected Lock action (with Anonymous confirmation if not logged in)
                    IconButton(
                        onClick = {
                            if (isProtectedOrCategory) {
                                protectionDialogMessage = if (isCategory) {
                                    "Halaman kategori merupakan indeks sistem ensiklopedia Mippedia dan tidak dapat disunting oleh siapa pun."
                                } else {
                                    "Halaman ini dilindungi (${article.protectionReason.ifEmpty { "Tingkat perlindungan: sysop" }}).\n\nAnda tidak memiliki izin untuk menyunting artikel ini."
                                }
                                showProtectionDialog = true
                            } else if (currentUser.isAnon) {
                                showAnonEditDialog = true
                            } else {
                                onEditClick()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isProtectedOrCategory) Icons.Default.Lock else Icons.Default.Edit,
                            contentDescription = if (isProtectedOrCategory) "Dilindungi" else "Sunting",
                            tint = if (isProtectedOrCategory) MippediaAmber else MaterialTheme.colorScheme.primary
                        )
                    }

                    // Bookmark action (Only visible for logged in users)
                    if (!currentUser.isAnon) {
                        IconButton(onClick = onBookmarkClick) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Simpan",
                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Table of Contents
                    IconButton(onClick = onTocClick) {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = "Daftar Isi",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Related Pages
                    IconButton(onClick = onRelatedClick) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Halaman Terkait",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Font Reader Settings
                    IconButton(onClick = { showFontDialog = !showFontDialog }) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = "Ukuran Tulisan",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 4. Social Media Links Bar (Instagram, TikTok, YouTube, etc.)
        if (article.socialLinks.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Tautan Media Sosial Resmi",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(article.socialLinks) { (platform, url) ->
                            AssistChip(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                },
                                label = { Text(platform, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Native Infobox Card (Clean card with structured items)
        if (article.infobox != null && article.infobox.items.isNotEmpty()) {
            item {
                InfoboxCard(
                    infobox = article.infobox,
                    onImageClick = onImageClick
                )
            }
        }

        // 6. Complete Article Content via High-Fidelity Styled Renderer
        item {
            val styledHtml = remember(article.parsedHtml, isDark, fontSizeSp, useSerif, fontFamilyName) {
                generateStyledArticleHtml(
                    rawHtml = article.parsedHtml,
                    isDark = isDark,
                    fontSizeSp = fontSizeSp,
                    useSerif = useSerif,
                    fontFamilyName = fontFamilyName
                )
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.javaScriptEnabled = false
                            settings.domStorageEnabled = false
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    when {
                                        url.contains("redlink=1") || (url.contains("action=edit") && url.contains("title=")) -> {
                                            val uri = Uri.parse(url)
                                            val targetTitle = uri.getQueryParameter("title")
                                                ?: url.substringAfter("title=").substringBefore("&").replace('+', ' ')
                                            val cleanTitle = java.net.URLDecoder.decode(targetTitle, "UTF-8").replace('_', ' ')
                                            onRedlinkClick(cleanTitle)
                                            return true
                                        }
                                        url.contains("/wiki/") -> {
                                            val rawTitle = url.substringAfter("/wiki/").substringBefore("#").substringBefore("?")
                                            val decodedTitle = try {
                                                java.net.URLDecoder.decode(rawTitle, "UTF-8").replace('_', ' ')
                                            } catch (_: Exception) {
                                                rawTitle.replace('_', ' ')
                                            }
                                            onArticleLinkClick(decodedTitle)
                                            return true
                                        }
                                        url.startsWith("https://commons.mippedia.org/images") || url.endsWith(".jpg") || url.endsWith(".png") || url.endsWith(".webp") || url.endsWith(".svg") -> {
                                            onImageClick(url)
                                            return true
                                        }
                                        else -> {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                ctx.startActivity(intent)
                                            } catch (_: Exception) {}
                                            return true
                                        }
                                    }
                                }
                            }
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL("https://id.mippedia.org", styledHtml, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(14.dp)
                )
            }
        }

        // 6b. Category Members List (For Category Pages: Displays all member pages/articles!)
        if (isCategory) {
            item {
                val cleanCatName = article.title
                    .removePrefix("Kategori:")
                    .removePrefix("kategori:")
                    .removePrefix("Category:")
                    .removePrefix("category:")
                    .trim()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Halaman dalam Kategori \"$cleanCatName\" (${article.categoryMembers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (article.categoryMembers.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Belum ada artikel atau halaman yang terdaftar di dalam kategori ini.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            article.categoryMembers.forEach { member ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    onClick = { onArticleLinkClick(member.title) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (!member.thumbnailUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = member.thumbnailUrl,
                                                contentDescription = member.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                                modifier = Modifier.size(54.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Article,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = member.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (!member.snippet.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = member.snippet,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ArrowForwardIos,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. Clean Categories Section (Underscores replaced with spaces!)
        if (article.cleanCategories.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Kategori Halaman",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(article.cleanCategories) { cat ->
                            SuggestionChip(
                                onClick = { onCategoryClick("Kategori:$cat") },
                                label = { Text(cat, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        }

        // 8. Related Articles Extension
        if (article.relatedArticles.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Halaman Terkait",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    article.relatedArticles.take(4).forEach { relTitle ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            onClick = { onArticleLinkClick(relTitle) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = relTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }

        // 9. Footer License
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Lisensi Konten",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Teks ini tersedia di bawah lisensi Creative Commons Attribution-ShareAlike 4.0 International dari ${article.language.displayName} Mippedia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun InfoboxCard(
    infobox: WikiInfobox,
    onImageClick: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (infobox.title.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = infobox.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                infobox.items.forEachIndexed { index, (key, value) ->
                    val rowBg = if (index % 2 == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color.Transparent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(rowBg)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.42f)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(0.58f)
                        )
                    }
                }
            }
        }
    }
}

// Generate styled HTML that renders all tables, sports stats, paragraphs, and lists cleanly
private fun generateStyledArticleHtml(
    rawHtml: String,
    isDark: Boolean,
    fontSizeSp: Float,
    useSerif: Boolean,
    fontFamilyName: String = "sans-serif"
): String {
    val bgColor = if (isDark) "#181F26" else "#FFFFFF"
    val textColor = if (isDark) "#E2E2E6" else "#1A1C1E"
    val linkColor = if (isDark) "#70B7EB" else "#135D8A"
    val tableBorderColor = if (isDark) "#3F4A54" else "#E0E0E0"
    val tableHeaderBg = if (isDark) "#222B35" else "#F0F4F8"
    val fontFamily = when (fontFamilyName.lowercase()) {
        "serif" -> "Georgia, serif"
        "monospace" -> "'Courier New', Courier, monospace"
        "cursive" -> "'Comic Sans MS', 'Segoe Print', cursive"
        else -> if (useSerif) "Georgia, serif" else "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
    }

    var cleanHtml = rawHtml
        // 1. Remove raw unexpanded template strings if present (e.g. {{Hatnote|...}}, {{Tanpa referensi}}, {{Stub}}, {{Rintisan}})
        .replace(Regex("\\{\\{\\s*(?:Hatnote|Tanpa\\s+referensi|Tanpa\\s+kategori|Tanpa\\s+kutipan|Stub|Rintisan)[^}]*\\}\\}", RegexOption.IGNORE_CASE), "")
        // 2. Remove ambox / metadata tables (Tanpa referensi, Tanpa kategori, Tanpa kutipan, stub, hatnote)
        .replace(Regex("<table[^>]*class=[\"'][^\"']*(?:ambox|asbox|stub|metadata|messagebox|box-Tanpa|hatnote)[^\"']*[\"'][^>]*>.*?</table>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        // 3. Remove hatnote divs, notes, dablinks, shortdesc
        .replace(Regex("<div[^>]*class=[\"'][^\"']*(?:hatnote|dablink|navigation-not-searchable|ambox|stub|asbox)[^\"']*[\"'][^>]*>.*?</div>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("<p[^>]*class=[\"'][^\"']*(?:hatnote|dablink)[^\"']*[\"'][^>]*>.*?</p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("<div[^>]*role=[\"']note[\"'][^>]*>.*?</div>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("<div[^>]*class=[\"'][^\"']*shortdescription[^\"']*[\"'][^>]*>.*?</div>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")

    // Hide infobox in HTML because it is already displayed in native InfoboxCard above!
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            <style>
                body {
                    font-family: $fontFamily;
                    font-size: ${fontSizeSp}px;
                    line-height: 1.6;
                    color: $textColor;
                    background-color: $bgColor;
                    margin: 0;
                    padding: 0;
                    word-wrap: break-word;
                }
                a {
                    color: $linkColor;
                    text-decoration: none;
                    font-weight: 500;
                }
                table.infobox {
                    display: none !important;
                }
                .toc, #toc {
                    display: none !important;
                }
                .mw-editsection {
                    display: none !important;
                }
                h2 {
                    font-size: 1.35em;
                    margin-top: 1.2em;
                    margin-bottom: 0.4em;
                    color: $linkColor;
                    border-bottom: 1px solid $tableBorderColor;
                    padding-bottom: 4px;
                }
                h3 {
                    font-size: 1.15em;
                    margin-top: 1em;
                    margin-bottom: 0.3em;
                    color: $textColor;
                }
                h4 {
                    font-size: 1.05em;
                    margin-top: 0.8em;
                    margin-bottom: 0.2em;
                }
                p {
                    margin-top: 0.4em;
                    margin-bottom: 0.8em;
                }
                /* Clean reading mode: Completely hide Hatnote, Disambiguations, Warning Banners, and Stubs */
                .hatnote,
                .Hatnote,
                div.hatnote,
                div.Hatnote,
                p.hatnote,
                p.Hatnote,
                table.hatnote,
                table.Hatnote,
                [class*="hatnote" i],
                [class*="Hatnote"],
                [class*="hatnote"],
                [id*="hatnote" i],
                [id*="Hatnote"],
                div[role="note"],
                .navigation-not-searchable,
                .dablink,
                .hatnote-container,
                table.ambox,
                table.metadata,
                table.messagebox,
                .mbox-small,
                [class*="ambox" i],
                [class*="asbox" i],
                [class*="stub" i],
                [class*="Stub" i],
                [class*="messagebox" i],
                [class*="Tanpa" i],
                [class*="tanpa" i],
                table.box-Tanpa_referensi,
                table.box-Tanpa_kategori,
                table.box-Tanpa_kutipan,
                .ambox,
                div.metadata,
                .stub,
                .Stub,
                table.stub,
                .asbox,
                table.asbox,
                .stub-box,
                #stub,
                #Stub,
                .metadata.asbox,
                table.imbox,
                table.cmbox,
                table.ombox,
                table.fmbox {
                    display: none !important;
                    visibility: hidden !important;
                    opacity: 0 !important;
                    height: 0 !important;
                    min-height: 0 !important;
                    max-height: 0 !important;
                    margin: 0 !important;
                    padding: 0 !important;
                    border: none !important;
                    overflow: hidden !important;
                }

                /* Responsive Sports & Wiki Tables */
                table:not(.infobox) {
                    display: block;
                    width: 100% !important;
                    overflow-x: auto;
                    border-collapse: collapse;
                    margin: 12px 0;
                    font-size: 0.9em;
                }
                table:not(.infobox) th,
                table:not(.infobox) td {
                    border: 1px solid $tableBorderColor;
                    padding: 8px 10px;
                    text-align: left;
                }
                table:not(.infobox) th {
                    background-color: $tableHeaderBg;
                    font-weight: 600;
                }
                img {
                    max-width: 100% !important;
                    height: auto !important;
                    object-fit: contain !important;
                    border-radius: 8px;
                    margin: 8px 0;
                    display: block;
                }
                ul, ol {
                    padding-left: 20px;
                    margin: 8px 0;
                }
                li {
                    margin-bottom: 4px;
                }
                blockquote {
                    border-left: 3px solid $linkColor;
                    margin: 12px 0;
                    padding: 6px 12px;
                    background-color: $tableHeaderBg;
                    border-radius: 0 8px 8px 0;
                }
            </style>
        </head>
        <body>
            $cleanHtml
        </body>
        </html>
    """.trimIndent()
}

private fun formatIsoTimestamp(rawTimestamp: String): String {
    if (rawTimestamp.isBlank()) return "baru-baru ini"
    return try {
        val clean = rawTimestamp.replace("T", " ").replace("Z", "").take(16)
        if (clean.length >= 10) {
            val datePart = clean.substring(0, 10)
            val timePart = if (clean.length >= 16) clean.substring(11, 16) else ""
            val parts = datePart.split("-")
            if (parts.size == 3) {
                val year = parts[0]
                val month = when (parts[1]) {
                    "01" -> "Jan"
                    "02" -> "Feb"
                    "03" -> "Mar"
                    "04" -> "Apr"
                    "05" -> "Mei"
                    "06" -> "Jun"
                    "07" -> "Jul"
                    "08" -> "Agt"
                    "09" -> "Sep"
                    "10" -> "Okt"
                    "11" -> "Nov"
                    "12" -> "Des"
                    else -> parts[1]
                }
                val day = parts[2].trimStart('0')
                if (timePart.isNotBlank()) "$day $month $year, $timePart WIB" else "$day $month $year"
            } else {
                clean
            }
        } else {
            clean
        }
    } catch (_: Exception) {
        rawTimestamp
    }
}

