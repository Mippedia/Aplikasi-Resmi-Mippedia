package com.example.model

enum class WikiLanguage(
    val code: String,
    val displayName: String,
    val flagEmoji: String,
    val baseUrl: String
) {
    INDONESIAN("id", "Indonesia", "🇮🇩", "https://id.mippedia.org"),
    ENGLISH("en", "English", "🇬🇧", "https://en.mippedia.org"),
    COMMONS("commons", "Commons (Berkas)", "🖼️", "https://commons.mippedia.org");

    val apiEndpoint: String
        get() = "$baseUrl/api.php"
}

data class WikiArticle(
    val title: String,
    val displayTitle: String = title,
    val shortDescription: String? = null,
    val heroImageUrl: String? = null,
    val pageId: Long = 0,
    val rawWikitext: String = "",
    val parsedHtml: String = "",
    val isProtected: Boolean = false,
    val protectionReason: String = "",
    val sections: List<WikiSection> = emptyList(),
    val infobox: WikiInfobox? = null,
    val socialLinks: List<Pair<String, String>> = emptyList(),
    val categories: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val relatedArticles: List<String> = emptyList(),
    val categoryMembers: List<WikiSearchResult> = emptyList(),
    val redirectedFrom: String? = null,
    val revId: Long = 0,
    val lastModified: String = "",
    val lastEditor: String = "",
    val language: WikiLanguage = WikiLanguage.INDONESIAN
) {
    val cleanCategories: List<String>
        get() = categories.map {
            it.replace('_', ' ')
                .trim()
                .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
        }.filter { it.isNotBlank() }
}

data class WikiSection(
    val id: Int,
    val title: String,
    val level: Int,
    val anchor: String
)

data class WikiInfobox(
    val title: String,
    val imageUrl: String? = null,
    val items: List<Pair<String, String>> = emptyList()
)

data class MonthlyFeature(
    val title: String,
    val summary: String,
    val imageUrl: String? = null,
    val targetArticleTitle: String? = null
)

data class WikiMediaItem(
    val title: String,
    val url: String,
    val thumbUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0,
    val mime: String = "",
    val user: String = "",
    val timestamp: String = "",
    val descriptionUrl: String = ""
)

data class WikiSearchResult(
    val pageId: Long,
    val title: String,
    val snippet: String,
    val ns: Int, // 0 = Artikel, 6 = Berkas, 14 = Kategori
    val timestamp: String = "",
    val wordCount: Int = 0,
    val thumbnailUrl: String? = null,
    val shortDescription: String? = null
)

data class WikiUserInfo(
    val username: String = "Tamu",
    val id: Long = 0,
    val isAnon: Boolean = true,
    val groups: List<String> = emptyList(),
    val unreadCount: Int = 0,
    val email: String = ""
)

data class WikiRecentChange(
    val title: String,
    val ns: Int,
    val revId: Long,
    val oldRevId: Long,
    val timestamp: String,
    val user: String = "",
    val thumbnailUrl: String? = null,
    val snippet: String? = null
)

data class WikiNotification(
    val id: String,
    val title: String,
    val body: String,
    val timestamp: String,
    val isRead: Boolean = false
)

enum class SearchFilter(val label: String, val ns: String) {
    ALL("Semua", "0|6|14"),
    ARTICLES("Artikel", "0"),
    MEDIA("Berkas (Commons)", "6"),
    CATEGORIES("Kategori", "14")
}

data class WikiUserPreferences(
    val username: String = "",
    val email: String = "",
    val realName: String = "",
    val nickname: String = "",
    val registrationDate: String = "",
    val editCount: Int = 0,
    val groups: List<String> = emptyList(),
    val rights: List<String> = emptyList(),
    val languageCode: String = "id",
    val timeZone: String = "Asia/Jakarta"
)

data class UserContributionItem(
    val pageId: Long = 0,
    val revId: Long = 0,
    val title: String = "",
    val timestamp: String = "",
    val comment: String = "",
    val sizeDiff: Int = 0,
    val isMinor: Boolean = false,
    val isNew: Boolean = false
)

