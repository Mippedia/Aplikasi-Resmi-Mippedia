package com.example.network

import android.content.Context
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

class MippediaApiClient(context: Context) {
    private val cookieJar = PersistentCookieJar(context)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectionSpecs(
            listOf(
                ConnectionSpec.MODERN_TLS,
                ConnectionSpec.COMPATIBLE_TLS
            )
        )
        .connectionPool(ConnectionPool(5, 15, TimeUnit.SECONDS))
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 MippediaApp/1.0 (contact: info@mippedia.org)")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Connection", "keep-alive")
                .build()
            chain.proceed(request)
        }
        .build()

    fun clearHttpCache() {
        try {
            client.cache?.evictAll()
        } catch (_: Exception) {}
    }

    suspend fun getRecentChanges(lang: WikiLanguage): Result<List<WikiRecentChange>> = withContext(Dispatchers.IO) {
        try {
            val url = "${lang.apiEndpoint}?action=query&list=recentchanges&rcnamespace=0&rclimit=25&rcprop=title|ids|sizes|flags|user|timestamp&format=json"
            val json = getJson(url)
            val list = mutableListOf<WikiRecentChange>()
            val rcArray = json.optJSONObject("query")?.optJSONArray("recentchanges")

            val titles = mutableListOf<String>()
            val seenTitles = mutableSetOf<String>()

            if (rcArray != null) {
                for (i in 0 until rcArray.length()) {
                    val obj = rcArray.getJSONObject(i)
                    val t = obj.optString("title", "").trim()
                    val lower = t.lowercase()
                    // Deduplicate articles so the same article doesn't show multiple times
                    if (t.isNotBlank() && !seenTitles.contains(lower)) {
                        seenTitles.add(lower)
                        titles.add(t)
                        list.add(
                            WikiRecentChange(
                                title = t,
                                ns = obj.optInt("ns", 0),
                                revId = obj.optLong("revid", 0),
                                oldRevId = obj.optLong("old_revid", 0),
                                timestamp = obj.optString("timestamp", ""),
                                user = obj.optString("user", "")
                            )
                        )
                        if (list.size >= 10) break
                    }
                }
            }

            // Enrich with page images and short extracts
            if (titles.isNotEmpty()) {
                try {
                    val titlesParam = titles.joinToString("|") { URLEncoder.encode(it, "UTF-8") }
                    val imgUrl = "${lang.apiEndpoint}?action=query&titles=$titlesParam&prop=pageimages|extracts&pithumbsize=200&exintro=1&explaintext=1&exsentences=1&format=json"
                    val imgJson = getJson(imgUrl)
                    val pages = imgJson.optJSONObject("query")?.optJSONObject("pages")
                    if (pages != null) {
                        val thumbMap = mutableMapOf<String, String>()
                        val snippetMap = mutableMapOf<String, String>()
                        val keys = pages.keys()
                        while (keys.hasNext()) {
                            val pObj = pages.getJSONObject(keys.next())
                            val pTitle = pObj.optString("title", "")
                            val thumb = pObj.optJSONObject("thumbnail")?.optString("source", null)
                            val extract = pObj.optString("extract", null)
                            if (thumb != null) thumbMap[pTitle.lowercase()] = thumb
                            if (extract != null) snippetMap[pTitle.lowercase()] = extract
                        }

                        for (i in list.indices) {
                            val item = list[i]
                            val tKey = item.title.lowercase()
                            list[i] = item.copy(
                                thumbnailUrl = thumbMap[tKey],
                                snippet = snippetMap[tKey]
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MippediaApiClient", "Thumbnail enrichment failed: ${e.message}")
                }
            }

            if (list.isNotEmpty()) {
                Result.success(list)
            } else {
                Result.success(getFallbackRecentChanges(lang))
            }
        } catch (e: Exception) {
            Log.e("MippediaApiClient", "getRecentChanges error: ${e.message}, using fallback articles", e)
            Result.success(getFallbackRecentChanges(lang))
        }
    }

    suspend fun getMonthlyFeatures(lang: WikiLanguage): Result<Pair<MonthlyFeature?, MonthlyFeature?>> = withContext(Dispatchers.IO) {
        if (lang != WikiLanguage.INDONESIAN) {
            // English or Commons does not have monthly features on main page
            return@withContext Result.success(Pair(null, null))
        }

        try {
            val url = "${lang.apiEndpoint}?action=parse&page=Halaman_Utama&prop=text&format=json"
            val json = getJson(url)
            val html = json.optJSONObject("parse")?.optJSONObject("text")?.optString("*", "") ?: ""

            var monthlyArticle: MonthlyFeature? = null
            var monthlyImage: MonthlyFeature? = null

            // 1. Extract ARTIKEL BULANAN
            val abPattern = Pattern.compile("ARTIKEL BULANAN.*?</div>\\s*<div[^>]*>(.*?)</div>\\s*<div", Pattern.DOTALL)
            val abMatcher = abPattern.matcher(html)
            if (abMatcher.find()) {
                val abContent = abMatcher.group(1) ?: ""

                // Image
                var abImg: String? = null
                val imgMatcher = Pattern.compile("src=[\"']([^\"']+)[\"']").matcher(abContent)
                if (imgMatcher.find()) {
                    val rawSrc = imgMatcher.group(1) ?: ""
                    abImg = when {
                        rawSrc.startsWith("//") -> "https:$rawSrc"
                        rawSrc.startsWith("/") -> "https://commons.mippedia.org$rawSrc"
                        else -> rawSrc
                    }
                }

                // Link & Title
                var targetTitle = "Sistem penulisan"
                val linkMatcher = Pattern.compile("<a[^>]*href=[\"']/wiki/([^\"']+)[\"'][^>]*>(.*?)</a>").matcher(abContent)
                while (linkMatcher.find()) {
                    val candidate = linkMatcher.group(1)?.replace('_', ' ') ?: ""
                    if (!candidate.contains("Berkas:", ignoreCase = true) && !candidate.contains("File:", ignoreCase = true) && !candidate.contains("Special:", ignoreCase = true) && !candidate.contains("Istimewa:", ignoreCase = true)) {
                        targetTitle = candidate
                        break
                    }
                }

                // Clean summary text
                val cleanSummary = abContent
                    .replace(Regex("<div[^>]*class=[\"']thumb[^\"']*[\"'].*?</div>", RegexOption.DOT_MATCHES_ALL), "")
                    .replace(Regex("<div[^>]*style=[\"'][^\"']*border-top[^\"']*[\"'].*?</div>", RegexOption.DOT_MATCHES_ALL), "")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                monthlyArticle = MonthlyFeature(
                    title = targetTitle,
                    summary = cleanSummary.take(280),
                    imageUrl = abImg,
                    targetArticleTitle = targetTitle
                )
            }

            // 2. Extract GAMBAR BULANAN
            val gbPattern = Pattern.compile("GAMBAR BULANAN.*?</div>\\s*<div[^>]*>(.*?)</div>\\s*<div", Pattern.DOTALL)
            val gbMatcher = gbPattern.matcher(html)
            if (gbMatcher.find()) {
                val gbContent = gbMatcher.group(1) ?: ""

                var gbImg: String? = null
                val imgMatcher = Pattern.compile("src=[\"']([^\"']+)[\"']").matcher(gbContent)
                if (imgMatcher.find()) {
                    val rawSrc = imgMatcher.group(1) ?: ""
                    gbImg = when {
                        rawSrc.startsWith("//") -> "https:$rawSrc"
                        rawSrc.startsWith("/") -> "https://commons.mippedia.org$rawSrc"
                        else -> rawSrc
                    }
                }

                val cleanSummary = gbContent
                    .replace(Regex("<div[^>]*style=[\"'][^\"']*border-top[^\"']*[\"'].*?</div>", RegexOption.DOT_MATCHES_ALL), "")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                monthlyImage = MonthlyFeature(
                    title = "Foto Pilihan Mippedia",
                    summary = cleanSummary.take(250),
                    imageUrl = gbImg,
                    targetArticleTitle = "Berkas:Foto Seorang Tentara Jerman.jpeg"
                )
            }

            if (monthlyArticle == null) {
                monthlyArticle = MonthlyFeature(
                    title = "Sistem penulisan",
                    summary = "Sistem penulisan adalah cara untuk mencatat bahasa secara visual menggunakan grafem atau tanda grafis tertentu.",
                    imageUrl = "https://commons.mippedia.org/w/thumb.php?f=Foto_Seorang_Tentara_Jerman.jpeg&width=600",
                    targetArticleTitle = "Sistem penulisan"
                )
            }
            if (monthlyImage == null) {
                monthlyImage = MonthlyFeature(
                    title = "Foto Pilihan Mippedia",
                    summary = "Potret dokumentasi sejarah Mippedia Commons.",
                    imageUrl = "https://commons.mippedia.org/w/thumb.php?f=Foto_Seorang_Tentara_Jerman.jpeg&width=600",
                    targetArticleTitle = "Berkas:Foto Seorang Tentara Jerman.jpeg"
                )
            }

            Result.success(Pair(monthlyArticle, monthlyImage))
        } catch (e: Exception) {
            Log.e("MippediaApiClient", "getMonthlyFeatures error", e)
            val fallbackArt = MonthlyFeature(
                title = "Sistem penulisan",
                summary = "Sistem penulisan adalah cara untuk mencatat bahasa secara visual menggunakan grafem atau tanda grafis tertentu.",
                imageUrl = "https://commons.mippedia.org/w/thumb.php?f=Foto_Seorang_Tentara_Jerman.jpeg&width=600",
                targetArticleTitle = "Sistem penulisan"
            )
            val fallbackImg = MonthlyFeature(
                title = "Foto Pilihan Mippedia",
                summary = "Potret dokumentasi sejarah Mippedia Commons.",
                imageUrl = "https://commons.mippedia.org/w/thumb.php?f=Foto_Seorang_Tentara_Jerman.jpeg&width=600",
                targetArticleTitle = "Berkas:Foto Seorang Tentara Jerman.jpeg"
            )
            Result.success(Pair(fallbackArt, fallbackImg))
        }
    }

    suspend fun search(query: String, lang: WikiLanguage, filter: SearchFilter): Result<List<WikiSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val targetEndpoint = if (filter == SearchFilter.MEDIA) {
                WikiLanguage.COMMONS.apiEndpoint
            } else {
                lang.apiEndpoint
            }
            val nsParam = if (filter == SearchFilter.MEDIA) "6" else filter.ns
            val cleanQuery = query.trim()
            val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")

            val resultMap = mutableMapOf<String, WikiSearchResult>()

            // 1. Prefix search generator (great for 1, 2, 3+ characters like 'al', 'ka', etc.)
            try {
                val prefixUrl = "$targetEndpoint?action=query&generator=prefixsearch&gpssearch=$encodedQuery&gpslimit=15&prop=pageimages|extracts|info&pithumbsize=200&exintro=1&explaintext=1&exsentences=1&format=json"
                val pJson = getJson(prefixUrl)
                val pPages = pJson.optJSONObject("query")?.optJSONObject("pages")
                if (pPages != null) {
                    val keys = pPages.keys()
                    while (keys.hasNext()) {
                        val p = pPages.getJSONObject(keys.next())
                        // Filter out redirect pages as requested
                        if (p.has("redirect")) continue
                        val t = p.optString("title", "")
                        val thumb = p.optJSONObject("thumbnail")?.optString("source", null)
                        val ext = p.optString("extract", "")
                        resultMap[t.lowercase()] = WikiSearchResult(
                            pageId = p.optLong("pageid", 0),
                            title = t,
                            snippet = ext,
                            ns = p.optInt("ns", 0),
                            thumbnailUrl = thumb,
                            shortDescription = ext.take(120)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("MippediaApiClient", "Prefix search warning: ${e.message}")
            }

            // 2. Full text generator search (matches inside text for 3+ letters)
            if (cleanQuery.length >= 3) {
                try {
                    val genUrl = "$targetEndpoint?action=query&generator=search&gsrsearch=$encodedQuery*&gsrnamespace=$nsParam&gsrlimit=15&prop=pageimages|extracts|info&pithumbsize=200&exintro=1&explaintext=1&exsentences=2&format=json"
                    val genJson = getJson(genUrl)
                    val pages = genJson.optJSONObject("query")?.optJSONObject("pages")
                    if (pages != null) {
                        val keys = pages.keys()
                        while (keys.hasNext()) {
                            val p = pages.getJSONObject(keys.next())
                            // Filter out redirect pages as requested
                            if (p.has("redirect")) continue
                            val t = p.optString("title", "")
                            val k = t.lowercase()
                            if (!resultMap.containsKey(k)) {
                                val thumb = p.optJSONObject("thumbnail")?.optString("source", null)
                                val ext = p.optString("extract", "")
                                resultMap[k] = WikiSearchResult(
                                    pageId = p.optLong("pageid", 0),
                                    title = t,
                                    snippet = ext,
                                    ns = p.optInt("ns", 0),
                                    thumbnailUrl = thumb,
                                    shortDescription = ext.take(120)
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MippediaApiClient", "Full text search warning: ${e.message}")
                }
            }

            // 3. Fallback to list=search if still empty
            if (resultMap.isEmpty()) {
                val listUrl = "$targetEndpoint?action=query&list=search&srsearch=$encodedQuery*&srnamespace=$nsParam&srlimit=20&format=json"
                val listJson = getJson(listUrl)
                val searchArray = listJson.optJSONObject("query")?.optJSONArray("search")
                if (searchArray != null) {
                    for (i in 0 until searchArray.length()) {
                        val obj = searchArray.getJSONObject(i)
                        val t = obj.optString("title", "")
                        val cleanSnippet = obj.optString("snippet", "").replace(Regex("<[^>]*>"), "")
                        resultMap[t.lowercase()] = WikiSearchResult(
                            pageId = obj.optLong("pageid", 0),
                            title = t,
                            snippet = cleanSnippet,
                            ns = obj.optInt("ns", 0),
                            timestamp = obj.optString("timestamp", ""),
                            wordCount = obj.optInt("wordcount", 0)
                        )
                    }
                }
            }

            Result.success(resultMap.values.toList())
        } catch (e: Exception) {
            Log.e("MippediaApiClient", "search error", e)
            Result.failure(e)
        }
    }

    suspend fun getRandomArticle(lang: WikiLanguage): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "${lang.apiEndpoint}?action=query&list=random&rnnamespace=0&rnlimit=1&format=json"
            val json = getJson(url)
            val random = json.optJSONObject("query")?.optJSONArray("random")?.optJSONObject(0)
            val defaultTitle = if (lang == WikiLanguage.ENGLISH) "Alphabet" else "Karate"
            val title = random?.optString("title", defaultTitle) ?: defaultTitle
            Result.success(title)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getArticle(title: String, lang: WikiLanguage): Result<WikiArticle> = withContext(Dispatchers.IO) {
        try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val parseUrl = "${lang.apiEndpoint}?action=parse&page=$encodedTitle&prop=text|tocdata|displaytitle|categories|images|revid&redirects=1&format=json"
            val parseJson = getJson(parseUrl)

            if (parseJson.has("error")) {
                val errorMsg = parseJson.getJSONObject("error").optString("info", "Halaman tidak ditemukan")
                return@withContext Result.failure(Exception(errorMsg))
            }

            val parseObj = parseJson.getJSONObject("parse")
            val resolvedTitle = parseObj.optString("title", title)
            val displayTitle = parseObj.optString("displaytitle", resolvedTitle).replace(Regex("<[^>]*>"), "")
            val pageId = parseObj.optLong("pageid", 0)
            val revId = parseObj.optLong("revid", 0)
            val rawHtml = parseObj.optJSONObject("text")?.optString("*", "") ?: ""

            // Extract redirect source if MediaWiki followed a redirect
            val redirectsArr = parseObj.optJSONArray("redirects")
            var redirectedFrom: String? = if (redirectsArr != null && redirectsArr.length() > 0) {
                redirectsArr.getJSONObject(0).optString("from", null)
            } else if (resolvedTitle.equals(title, ignoreCase = false).not()) {
                title
            } else null

            // Extract sections
            val sectionsList = mutableListOf<WikiSection>()
            val tocData = parseObj.optJSONObject("tocdata")?.optJSONArray("entries")
            if (tocData != null) {
                for (i in 0 until tocData.length()) {
                    val entry = tocData.getJSONObject(i)
                    sectionsList.add(
                        WikiSection(
                            id = entry.optInt("index", i + 1),
                            title = entry.optString("line", "").replace(Regex("<[^>]*>"), ""),
                            level = entry.optInt("level", 2),
                            anchor = entry.optString("anchor", "")
                        )
                    )
                }
            }

            // Extract categories
            val categories = mutableListOf<String>()
            val catArray = parseObj.optJSONArray("categories")
            if (catArray != null) {
                for (i in 0 until catArray.length()) {
                    val catObj = catArray.getJSONObject(i)
                    val cat = catObj.optString("*", "")
                    if (cat.isNotBlank()) categories.add(cat)
                }
            }

            // Extract images
            val images = mutableListOf<String>()
            val imgArray = parseObj.optJSONArray("images")
            if (imgArray != null) {
                for (i in 0 until imgArray.length()) {
                    images.add(imgArray.getString(i))
                }
            }

            // Fetch wikitext for {{SHORTDESC:...}} and protection
            var shortDesc: String? = null
            var rawWikitext = ""
            var lastUser = ""
            var lastTime = ""
            try {
                val revUrl = "${lang.apiEndpoint}?action=query&prop=revisions|info&rvslots=main&rvprop=content|timestamp|user&inprop=protection&titles=$encodedTitle&format=json"
                val revJson = getJson(revUrl)
                val pagesNode = revJson.optJSONObject("query")?.optJSONObject("pages")
                if (pagesNode != null) {
                    val keys = pagesNode.keys()
                    if (keys.hasNext()) {
                        val pNode = pagesNode.getJSONObject(keys.next())
                        val revArr = pNode.optJSONArray("revisions")
                        if (revArr != null && revArr.length() > 0) {
                            val revObj = revArr.getJSONObject(0)
                            lastUser = revObj.optString("user", "")
                            lastTime = revObj.optString("timestamp", "")
                            rawWikitext = revObj
                                .optJSONObject("slots")
                                ?.optJSONObject("main")
                                ?.optString("*", "") ?: ""

                            val shortDescMatcher = Pattern.compile("(?i)\\{\\{\\s*(?:SHORTDESC|Short description)\\s*:\\s*([^}]+)\\s*\\}\\}").matcher(rawWikitext)
                            if (shortDescMatcher.find()) {
                                shortDesc = shortDescMatcher.group(1)?.trim()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MippediaApiClient", "Wikitext fetch error: ${e.message}")
            }

            // Fallback shortDesc from HTML
            if (shortDesc.isNullOrBlank()) {
                val htmlDescMatcher = Pattern.compile("<(?:div|span)[^>]*class=\"[^\"]*shortdescription[^\"]*\"[^>]*>(.*?)</(?:div|span)>", Pattern.DOTALL).matcher(rawHtml)
                if (htmlDescMatcher.find()) {
                    shortDesc = htmlDescMatcher.group(1)?.replace(Regex("<[^>]*>"), "")?.trim()
                }
            }

            // Protection status
            var isProtected = false
            var protectionReason = ""
            val infoUrl = "${lang.apiEndpoint}?action=query&prop=info&inprop=protection&titles=$encodedTitle&format=json"
            val infoJson = getJson(infoUrl)
            val pagesObj = infoJson.optJSONObject("query")?.optJSONObject("pages")
            if (pagesObj != null) {
                val keys = pagesObj.keys()
                if (keys.hasNext()) {
                    val pageNode = pagesObj.getJSONObject(keys.next())
                    val protectionArray = pageNode.optJSONArray("protection")
                    if (protectionArray != null && protectionArray.length() > 0) {
                        for (p in 0 until protectionArray.length()) {
                            val prot = protectionArray.getJSONObject(p)
                            if (prot.optString("type") == "edit") {
                                isProtected = true
                                protectionReason = "Tingkat perlindungan: ${prot.optString("level")}"
                            }
                        }
                    }
                }
            }

            // Extract Infobox, Hero Image, and Social Media links
            val infobox = extractInfobox(rawHtml)
            val heroImage = infobox?.imageUrl ?: extractFirstImage(rawHtml)
            val socialLinks = extractSocialLinks(rawHtml)

            // Fetch related pages
            val relatedList = mutableListOf<String>()
            try {
                val relUrl = "${lang.apiEndpoint}?action=query&list=backlinks&bltitle=$encodedTitle&bllimit=6&format=json"
                val relJson = getJson(relUrl)
                val blArray = relJson.optJSONObject("query")?.optJSONArray("backlinks")
                if (blArray != null) {
                    for (i in 0 until blArray.length()) {
                        val t = blArray.getJSONObject(i).optString("title", "")
                        if (t.isNotBlank() && t != title) relatedList.add(t)
                    }
                }
            } catch (_: Exception) {}

            // Fetch category members if this is a Category page
            val categoryMembersList = mutableListOf<WikiSearchResult>()
            val isCategoryPage = title.startsWith("Kategori:", ignoreCase = true) || title.startsWith("Category:", ignoreCase = true)
            if (isCategoryPage) {
                try {
                    val catMembersRes = getCategoryMembers(title, lang)
                    if (catMembersRes.isSuccess) {
                        categoryMembersList.addAll(catMembersRes.getOrDefault(emptyList()))
                    }
                } catch (e: Exception) {
                    Log.w("MippediaApiClient", "Failed to fetch category members: ${e.message}")
                }
            }

            // Fallback for wikitext redirects (#ALIHKAN / #REDIRECT) if API did not follow
            if (redirectedFrom == null && (rawWikitext.startsWith("#ALIHKAN", ignoreCase = true) || rawWikitext.startsWith("#REDIRECT", ignoreCase = true))) {
                val target = rawWikitext.substringAfter("[[").substringBefore("]]").substringBefore("|").trim()
                if (target.isNotBlank() && !target.equals(title, ignoreCase = true)) {
                    val targetRes = getArticle(target, lang)
                    if (targetRes.isSuccess) {
                        val targetArt = targetRes.getOrNull()!!
                        return@withContext Result.success(targetArt.copy(redirectedFrom = title))
                    }
                }
            }

            Result.success(
                WikiArticle(
                    title = resolvedTitle,
                    displayTitle = displayTitle,
                    shortDescription = shortDesc,
                    heroImageUrl = heroImage,
                    pageId = pageId,
                    rawWikitext = rawWikitext,
                    parsedHtml = rawHtml,
                    isProtected = isProtected,
                    protectionReason = protectionReason,
                    sections = sectionsList,
                    infobox = infobox,
                    socialLinks = socialLinks,
                    categories = categories,
                    images = images,
                    relatedArticles = relatedList,
                    categoryMembers = categoryMembersList,
                    revId = revId,
                    lastModified = lastTime,
                    lastEditor = lastUser,
                    language = lang,
                    redirectedFrom = redirectedFrom
                )
            )
        } catch (e: Exception) {
            Log.e("MippediaApiClient", "getArticle error: ${e.message}", e)
            val fallback = getFallbackArticle(title, lang)
            if (fallback != null) {
                Result.success(fallback)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun getCategoryMembers(categoryTitle: String, lang: WikiLanguage): Result<List<WikiSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val encodedCat = URLEncoder.encode(categoryTitle.trim(), "UTF-8")
            val list = mutableListOf<WikiSearchResult>()

            // 1. Generator categorymembers query (includes pageimages & extracts)
            try {
                val genUrl = "${lang.apiEndpoint}?action=query&generator=categorymembers&gcmtitle=$encodedCat&gcmlimit=50&prop=pageimages|extracts|info&pithumbsize=200&exintro=1&explaintext=1&exsentences=1&format=json"
                val genJson = getJson(genUrl)
                val pages = genJson.optJSONObject("query")?.optJSONObject("pages")
                if (pages != null) {
                    val keys = pages.keys()
                    while (keys.hasNext()) {
                        val p = pages.getJSONObject(keys.next())
                        val t = p.optString("title", "")
                        val thumb = p.optJSONObject("thumbnail")?.optString("source", null)
                        val ext = p.optString("extract", "")
                        list.add(
                            WikiSearchResult(
                                pageId = p.optLong("pageid", 0),
                                title = t,
                                snippet = ext,
                                ns = p.optInt("ns", 0),
                                thumbnailUrl = thumb,
                                shortDescription = ext.take(140)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("MippediaApiClient", "Generator categorymembers warning: ${e.message}")
            }

            // 2. Fallback to list=categorymembers if generator returned empty
            if (list.isEmpty()) {
                val listUrl = "${lang.apiEndpoint}?action=query&list=categorymembers&cmtitle=$encodedCat&cmlimit=50&format=json"
                val listJson = getJson(listUrl)
                val cmArray = listJson.optJSONObject("query")?.optJSONArray("categorymembers")
                if (cmArray != null) {
                    for (i in 0 until cmArray.length()) {
                        val obj = cmArray.getJSONObject(i)
                        val t = obj.optString("title", "")
                        list.add(
                            WikiSearchResult(
                                pageId = obj.optLong("pageid", 0),
                                title = t,
                                snippet = "",
                                ns = obj.optInt("ns", 0),
                                shortDescription = ""
                            )
                        )
                    }
                }
            }

            Result.success(list)
        } catch (e: Exception) {
            Log.e("MippediaApiClient", "getCategoryMembers error", e)
            Result.failure(e)
        }
    }

    suspend fun getRawWikitext(title: String, lang: WikiLanguage): Result<String> = withContext(Dispatchers.IO) {
        try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val url = "${lang.apiEndpoint}?action=query&prop=revisions&rvslots=main&rvprop=content&titles=$encodedTitle&format=json"
            val json = getJson(url)
            val pages = json.optJSONObject("query")?.optJSONObject("pages")
            if (pages != null) {
                val keys = pages.keys()
                if (keys.hasNext()) {
                    val pageObj = pages.getJSONObject(keys.next())
                    val revisions = pageObj.optJSONArray("revisions")
                    if (revisions != null && revisions.length() > 0) {
                        val content = revisions.getJSONObject(0)
                            .optJSONObject("slots")
                            ?.optJSONObject("main")
                            ?.optString("*", "") ?: ""
                        return@withContext Result.success(content)
                    }
                }
            }
            Result.success("")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveArticle(
        title: String,
        content: String,
        summary: String,
        isMinor: Boolean,
        lang: WikiLanguage
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val tokenUrl = "${lang.apiEndpoint}?action=query&meta=tokens&type=csrf&format=json"
            val tokenJson = getJson(tokenUrl)
            val csrfToken = tokenJson.optJSONObject("query")?.optJSONObject("tokens")?.optString("csrftoken", "")

            val formBuilder = FormBody.Builder()
                .add("action", "edit")
                .add("title", title)
                .add("text", content)
                .add("summary", summary)
                .add("token", csrfToken ?: "+\\")
                .add("format", "json")

            if (isMinor) {
                formBuilder.add("minor", "1")
            }

            val request = Request.Builder()
                .url(lang.apiEndpoint)
                .post(formBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val resJson = JSONObject(body)
                if (resJson.has("error")) {
                    val err = resJson.getJSONObject("error")
                    val info = err.optString("info", "Gagal menyimpan suntingan")
                    return@withContext Result.failure(Exception(info))
                }
                val editResult = resJson.optJSONObject("edit")?.optString("result", "Success") ?: "Success"
                Result.success(editResult)
            }
        } catch (e: Exception) {
            Log.e("MippediaApiClient", "saveArticle error", e)
            Result.failure(e)
        }
    }

    suspend fun getBacklinks(title: String, lang: WikiLanguage): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val url = "${lang.apiEndpoint}?action=query&list=backlinks&bltitle=$encodedTitle&bllimit=20&format=json"
            val json = getJson(url)
            val list = mutableListOf<String>()
            val backlinks = json.optJSONObject("query")?.optJSONArray("backlinks")
            if (backlinks != null) {
                for (i in 0 until backlinks.length()) {
                    val item = backlinks.getJSONObject(i)
                    val blTitle = item.optString("title", "")
                    if (blTitle.isNotBlank()) list.add(blTitle)
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCommonsImages(limit: Int = 20, continueKey: String? = null): Result<Pair<List<WikiMediaItem>, String?>> = withContext(Dispatchers.IO) {
        try {
            var url = "${WikiLanguage.COMMONS.apiEndpoint}?action=query&list=allimages&ailimit=$limit&aiprop=url|size|timestamp|user|mime&format=json"
            if (!continueKey.isNullOrBlank()) {
                url += "&aicontinue=${URLEncoder.encode(continueKey, "UTF-8")}"
            }
            val json = getJson(url)
            val nextContinue = json.optJSONObject("continue")?.optString("aicontinue", null)
            val list = mutableListOf<WikiMediaItem>()
            val allImages = json.optJSONObject("query")?.optJSONArray("allimages")
            if (allImages != null) {
                for (i in 0 until allImages.length()) {
                    val obj = allImages.getJSONObject(i)
                    list.add(
                        WikiMediaItem(
                            title = obj.optString("title", ""),
                            url = obj.optString("url", ""),
                            thumbUrl = obj.optString("descriptionurl", ""),
                            width = obj.optInt("width", 0),
                            height = obj.optInt("height", 0),
                            sizeBytes = obj.optLong("size", 0),
                            mime = obj.optString("mime", ""),
                            user = obj.optString("user", ""),
                            timestamp = obj.optString("timestamp", ""),
                            descriptionUrl = obj.optString("descriptionurl", "")
                        )
                    )
                }
            }
            Result.success(Pair(list, nextContinue))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getImageInfo(title: String): Result<WikiMediaItem> = withContext(Dispatchers.IO) {
        try {
            val normalizedTitle = when {
                title.startsWith("Berkas:", ignoreCase = true) -> "File:" + title.substring(7).trim()
                title.startsWith("File:", ignoreCase = true) -> title.trim()
                else -> "File:$title"
            }
            val encodedTitle = URLEncoder.encode(normalizedTitle, "UTF-8")
            
            // 1. Try Commons first
            val commonsUrl = "${WikiLanguage.COMMONS.apiEndpoint}?action=query&titles=$encodedTitle&prop=imageinfo&iiprop=url|size|timestamp|user|mime|extmetadata&format=json"
            var json = getJson(commonsUrl)
            var pages = json.optJSONObject("query")?.optJSONObject("pages")
            
            // Check if page exists on Commons
            var foundInfo: JSONObject? = null
            if (pages != null) {
                val keys = pages.keys()
                if (keys.hasNext()) {
                    val key = keys.next()
                    if (key != "-1") {
                        val pageObj = pages.getJSONObject(key)
                        val imageInfoArr = pageObj.optJSONArray("imageinfo")
                        if (imageInfoArr != null && imageInfoArr.length() > 0) {
                            foundInfo = imageInfoArr.getJSONObject(0)
                        }
                    }
                }
            }

            // 2. Fallback to Indonesian Wiki if not on Commons
            if (foundInfo == null) {
                val idUrl = "${WikiLanguage.INDONESIAN.apiEndpoint}?action=query&titles=$encodedTitle&prop=imageinfo&iiprop=url|size|timestamp|user|mime|extmetadata&format=json"
                json = getJson(idUrl)
                pages = json.optJSONObject("query")?.optJSONObject("pages")
                if (pages != null) {
                    val keys = pages.keys()
                    if (keys.hasNext()) {
                        val pageObj = pages.getJSONObject(keys.next())
                        val imageInfoArr = pageObj.optJSONArray("imageinfo")
                        if (imageInfoArr != null && imageInfoArr.length() > 0) {
                            foundInfo = imageInfoArr.getJSONObject(0)
                        }
                    }
                }
            }

            if (foundInfo != null) {
                val media = WikiMediaItem(
                    title = normalizedTitle,
                    url = foundInfo.optString("url", ""),
                    thumbUrl = foundInfo.optString("thumburl", foundInfo.optString("url", "")),
                    width = foundInfo.optInt("width", 0),
                    height = foundInfo.optInt("height", 0),
                    sizeBytes = foundInfo.optLong("size", 0),
                    mime = foundInfo.optString("mime", ""),
                    user = foundInfo.optString("user", ""),
                    timestamp = foundInfo.optString("timestamp", ""),
                    descriptionUrl = foundInfo.optString("descriptionurl", "")
                )
                if (media.url.isNotBlank()) {
                    return@withContext Result.success(media)
                }
            }
            Result.failure(Exception("Informasi berkas tidak ditemukan"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserInfo(lang: WikiLanguage): Result<WikiUserInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "${lang.apiEndpoint}?action=query&meta=userinfo&uiprop=groups|rights|realname|email|unreadcount&format=json"
            val json = getJson(url)
            val userNode = json.optJSONObject("query")?.optJSONObject("userinfo")
            if (userNode != null) {
                val isAnon = userNode.has("anon")
                val username = userNode.optString("name", "Tamu")
                val id = userNode.optLong("id", 0)
                val unread = userNode.optInt("unreadcount", 0)
                val email = userNode.optString("email", "")

                val groupsList = mutableListOf<String>()
                val grpArray = userNode.optJSONArray("groups")
                if (grpArray != null) {
                    for (i in 0 until grpArray.length()) {
                        groupsList.add(grpArray.getString(i))
                    }
                }

                Result.success(
                    WikiUserInfo(
                        username = username,
                        id = id,
                        isAnon = isAnon,
                        groups = groupsList,
                        unreadCount = unread,
                        email = email
                    )
                )
            } else {
                Result.success(WikiUserInfo())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(
        username: String,
        pass: String,
        lang: WikiLanguage
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val tokenUrl = "${lang.apiEndpoint}?action=query&meta=tokens&type=login&format=json"
            val tokenJson = getJson(tokenUrl)
            val loginToken = tokenJson.optJSONObject("query")?.optJSONObject("tokens")?.optString("logintoken", "")
            if (loginToken.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Gagal memperoleh token masuk"))
            }

            val formBuilder = FormBody.Builder()
                .add("action", "clientlogin")
                .add("format", "json")
                .add("username", username)
                .add("password", pass)
                .add("logintoken", loginToken)
                .add("loginreturnurl", lang.baseUrl)

            val request = Request.Builder().url(lang.apiEndpoint).post(formBuilder.build()).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val resJson = JSONObject(body)
                val clientlogin = resJson.optJSONObject("clientlogin")
                val status = clientlogin?.optString("status", "FAIL") ?: "FAIL"
                if (status == "PASS") {
                    val user = clientlogin?.optString("username", username) ?: username
                    Result.success(user)
                } else {
                    val msg = clientlogin?.optString("message", "Kata sandi salah atau akun tidak ditemukan")
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Log.e("MippediaApiClient", "login error", e)
            Result.failure(e)
        }
    }

    suspend fun resetPassword(userOrEmail: String, lang: WikiLanguage): Result<String> = withContext(Dispatchers.IO) {
        try {
            val tokenUrl = "${lang.apiEndpoint}?action=query&meta=tokens&type=csrf&format=json"
            val tokenJson = getJson(tokenUrl)
            val csrfToken = tokenJson.optJSONObject("query")?.optJSONObject("tokens")?.optString("csrftoken", "+\\")

            val formBuilder = FormBody.Builder()
                .add("action", "resetpassword")
                .add("token", csrfToken ?: "+\\")
                .add("format", "json")

            if (userOrEmail.contains("@")) {
                formBuilder.add("email", userOrEmail)
            } else {
                formBuilder.add("user", userOrEmail)
            }

            val request = Request.Builder().url(lang.apiEndpoint).post(formBuilder.build()).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val resJson = JSONObject(body)
                if (resJson.has("error")) {
                    val err = resJson.getJSONObject("error").optString("info", "Gagal mereset kata sandi")
                    Result.failure(Exception(err))
                } else {
                    Result.success("Tautan instruksi reset kata sandi telah dikirim jika akun tersebut terdaftar.")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(
        username: String,
        pass: String,
        email: String,
        lang: WikiLanguage
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val tokenUrl = "${lang.apiEndpoint}?action=query&meta=tokens&type=createaccount&format=json"
            val tokenJson = getJson(tokenUrl)
            val caToken = tokenJson.optJSONObject("query")?.optJSONObject("tokens")?.optString("createaccounttoken", "")

            val formBuilder = FormBody.Builder()
                .add("action", "createaccount")
                .add("format", "json")
                .add("username", username)
                .add("password", pass)
                .add("retype", pass)
                .add("createtoken", caToken ?: "")
                .add("createreturnurl", lang.baseUrl)

            if (email.isNotBlank()) {
                formBuilder.add("email", email.trim())
            }

            val request = Request.Builder().url(lang.apiEndpoint).post(formBuilder.build()).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val resJson = JSONObject(body)
                val ca = resJson.optJSONObject("createaccount")
                val status = ca?.optString("status", "FAIL")
                if (status == "PASS") {
                    Result.success("Pendaftaran berhasil! Akun $username telah aktif.")
                } else {
                    val msg = ca?.optString("message", resJson.optJSONObject("error")?.optString("info") ?: "Gagal membuat akun")
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserPreferences(lang: WikiLanguage): Result<WikiUserPreferences> = withContext(Dispatchers.IO) {
        try {
            val url = "${lang.apiEndpoint}?action=query&meta=userinfo&uiprop=groups|rights|editcount|email|realname|registrationdate&format=json"
            val json = getJson(url)
            val uNode = json.optJSONObject("query")?.optJSONObject("userinfo")
            if (uNode != null) {
                val uname = uNode.optString("name", "")
                val email = uNode.optString("email", "")
                val realName = uNode.optString("realname", "")
                val regDate = uNode.optString("registrationdate", "")
                val editCount = uNode.optInt("editcount", 0)

                val groups = mutableListOf<String>()
                val grpArr = uNode.optJSONArray("groups")
                if (grpArr != null) {
                    for (i in 0 until grpArr.length()) groups.add(grpArr.getString(i))
                }

                val rights = mutableListOf<String>()
                val rightArr = uNode.optJSONArray("rights")
                if (rightArr != null) {
                    for (i in 0 until rightArr.length()) rights.add(rightArr.getString(i))
                }

                Result.success(
                    WikiUserPreferences(
                        username = uname,
                        email = email,
                        realName = realName,
                        registrationDate = regDate,
                        editCount = editCount,
                        groups = groups,
                        rights = rights
                    )
                )
            } else {
                Result.failure(Exception("Gagal mengambil data preferensi"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserContributions(username: String, lang: WikiLanguage): Result<List<UserContributionItem>> = withContext(Dispatchers.IO) {
        try {
            val encodedUser = URLEncoder.encode(username.trim(), "UTF-8")
            val url = "${lang.apiEndpoint}?action=query&list=usercontribs&ucuser=$encodedUser&uclimit=25&ucprop=ids|title|timestamp|comment|size|sizediff|flags&format=json"
            val json = getJson(url)
            val contribsArr = json.optJSONObject("query")?.optJSONArray("usercontribs")
            val list = mutableListOf<UserContributionItem>()
            if (contribsArr != null) {
                for (i in 0 until contribsArr.length()) {
                    val item = contribsArr.getJSONObject(i)
                    list.add(
                        UserContributionItem(
                            pageId = item.optLong("pageid", 0),
                            revId = item.optLong("revid", 0),
                            title = item.optString("title", ""),
                            comment = item.optString("comment", ""),
                            timestamp = item.optString("timestamp", ""),
                            sizeDiff = item.optInt("sizediff", 0),
                            isMinor = item.has("minor"),
                            isNew = item.has("new")
                        )
                    )
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkArticleExists(title: String, lang: WikiLanguage): Boolean = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val url = "${lang.apiEndpoint}?action=query&titles=$encoded&format=json"
            val json = getJson(url)
            val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: return@withContext false
            val keys = pages.keys()
            if (keys.hasNext()) {
                val key = keys.next()
                if (key == "-1") return@withContext false
                val page = pages.getJSONObject(key)
                return@withContext !page.has("missing") && page.optLong("pageid", 0) > 0
            }
            false
        } catch (_: Exception) {
            false
        }
    }



    suspend fun logout(lang: WikiLanguage): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val tokenUrl = "${lang.apiEndpoint}?action=query&meta=tokens&type=csrf&format=json"
            val tokenJson = getJson(tokenUrl)
            val csrfToken = tokenJson.optJSONObject("query")?.optJSONObject("tokens")?.optString("csrftoken", "+\\")

            val form = FormBody.Builder()
                .add("action", "logout")
                .add("token", csrfToken ?: "+\\")
                .add("format", "json")
                .build()

            val request = Request.Builder().url(lang.apiEndpoint).post(form).build()
            client.newCall(request).execute().close()
            cookieJar.clear()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getNotifications(lang: WikiLanguage): Result<List<WikiNotification>> = withContext(Dispatchers.IO) {
        try {
            val url = "${lang.apiEndpoint}?action=query&meta=notifications&notformat=model&format=json"
            val json = getJson(url)
            if (json.has("error")) {
                return@withContext Result.success(emptyList())
            }
            val notifs = mutableListOf<WikiNotification>()
            val list = json.optJSONObject("query")?.optJSONObject("notifications")?.optJSONObject("list")
            if (list != null) {
                val keys = list.keys()
                while (keys.hasNext()) {
                    val obj = list.getJSONObject(keys.next())
                    val title = obj.optJSONObject("title")?.optString("full", "Notifikasi") ?: "Notifikasi"
                    val header = obj.optJSONObject("header")?.optString("text", "") ?: ""
                    val isRead = obj.optBoolean("read", false)
                    val timestamp = obj.optJSONObject("timestamp")?.optString("human", "") ?: ""
                    notifs.add(
                        WikiNotification(
                            id = obj.optString("id", ""),
                            title = title,
                            body = header,
                            timestamp = timestamp,
                            isRead = isRead
                        )
                    )
                }
            }
            Result.success(notifs)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    private fun getJson(url: String, maxRetries: Int = 3): JSONObject {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP request failed with code ${response.code}")
                    }
                    val body = response.body?.string() ?: "{}"
                    return JSONObject(body)
                }
            } catch (e: Exception) {
                lastException = e
                Log.w("MippediaApiClient", "getJson attempt $attempt failed for $url: ${e.javaClass.simpleName} - ${e.message}")
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(attempt * 350L)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
        }
        throw lastException ?: IOException("Gagal menghubungi peladen Mippedia")
    }

    private fun getFallbackRecentChanges(lang: WikiLanguage): List<WikiRecentChange> {
        return if (lang == WikiLanguage.ENGLISH) {
            listOf(
                WikiRecentChange(
                    title = "Alphabet",
                    ns = 0,
                    revId = 10001,
                    oldRevId = 10000,
                    timestamp = "2026-10-31",
                    user = "Mippedia Editor",
                    snippet = "An alphabet is a standardized set of basic written symbols or graphemes."
                ),
                WikiRecentChange(
                    title = "Karate",
                    ns = 0,
                    revId = 10002,
                    oldRevId = 10000,
                    timestamp = "2026-10-31",
                    user = "Sensei",
                    snippet = "Karate is a martial art developed in the Ryukyu Kingdom."
                ),
                WikiRecentChange(
                    title = "Main Page",
                    ns = 0,
                    revId = 10003,
                    oldRevId = 10000,
                    timestamp = "2026-10-31",
                    user = "Admin",
                    snippet = "Welcome to Mippedia, the free multilingual encyclopedia."
                )
            )
        } else {
            listOf(
                WikiRecentChange(
                    title = "Karate",
                    ns = 0,
                    revId = 10716,
                    oldRevId = 10715,
                    timestamp = "2026-10-31",
                    user = "Mippedia",
                    snippet = "Karate adalah seni bela diri yang berasal dari Jepang, menggunakan pukulan dan tendangan."
                ),
                WikiRecentChange(
                    title = "Sistem penulisan",
                    ns = 0,
                    revId = 10710,
                    oldRevId = 10709,
                    timestamp = "2026-10-31",
                    user = "Kontributor",
                    snippet = "Sistem penulisan adalah cara untuk mencatat bahasa secara visual dengan tanda grafis."
                ),
                WikiRecentChange(
                    title = "Alphabet",
                    ns = 0,
                    revId = 10705,
                    oldRevId = 10704,
                    timestamp = "2026-10-31",
                    user = "Redaksi",
                    snippet = "Alfabet adalah sistem penulisan yang menggunakan simbol grafis atau huruf."
                ),
                WikiRecentChange(
                    title = "Mippedia",
                    ns = 0,
                    revId = 10701,
                    oldRevId = 10700,
                    timestamp = "2026-10-31",
                    user = "Pengurus",
                    snippet = "Mippedia adalah proyek ensiklopedia daring bebas multibahasa berbasis MediaWiki."
                )
            )
        }
    }

    private fun getFallbackArticle(title: String, lang: WikiLanguage): WikiArticle? {
        val lower = title.lowercase().trim()
        val isEn = lang == WikiLanguage.ENGLISH

        return when {
            lower.contains("karate") -> WikiArticle(
                title = "Karate",
                displayTitle = "Karate",
                shortDescription = if (isEn) "Japanese martial art" else "Seni bela diri asal Jepang",
                heroImageUrl = null,
                pageId = 2198,
                rawWikitext = "{{SHORTDESC:Seni bela diri asal Jepang}}\n== Sejarah ==\nKarate berasal dari Okinawa.",
                parsedHtml = if (isEn) {
                    "<p><b>Karate</b> is a martial art developed in the Ryukyu Kingdom. It developed from the indigenous Ryukyuan martial arts under the influence of Chinese martial arts. Karate is now predominantly a striking art using punching, kicking, knee strikes, elbow strikes, and open-hand techniques.</p>"
                } else {
                    "<p><b>Karate</b> (空手) adalah seni bela diri yang berasal dari Jepang, berkembang di Kepulauan Ryukyu (Okinawa). Seni bela diri ini terutama menggunakan teknik serangan fisik seperti pukulan, tendangan, sikuan, dan lututan, serta teknik tangkisan yang kokoh.</p><p>Karate menekankan ketahanan fisik, fokus pikiran, dan kedisiplinan diri. Artikel ini dapat disunting dan dikembangkan oleh komunitas Mippedia.</p>"
                },
                sections = listOf(
                    WikiSection(1, if (isEn) "History" else "Sejarah", 2, "Sejarah"),
                    WikiSection(2, if (isEn) "Techniques" else "Teknik Dasar", 2, "Teknik_Dasar")
                ),
                categories = listOf("Seni bela diri", "Olahraga", "Jepang"),
                language = lang
            )
            lower.contains("sistem penulisan") || lower.contains("writing system") -> WikiArticle(
                title = "Sistem penulisan",
                displayTitle = "Sistem penulisan",
                shortDescription = if (isEn) "Organized method of visual communication" else "Cara visual mencatat bahasa",
                heroImageUrl = "https://commons.mippedia.org/w/thumb.php?f=Foto_Seorang_Tentara_Jerman.jpeg&width=600",
                pageId = 2200,
                rawWikitext = "{{SHORTDESC:Cara visual mencatat bahasa}}\n== Klasifikasi ==\nAda beragam aksara di dunia.",
                parsedHtml = if (isEn) {
                    "<p>A <b>writing system</b> is a method of visually representing verbal communication, based on a script and a set of rules regulating its use.</p>"
                } else {
                    "<p><b>Sistem penulisan</b> adalah cara untuk mencatat bahasa secara visual menggunakan grafem atau tanda grafis tertentu. Sistem ini memungkinkan manusia menyimpan pengetahuan, bertukar gagasan antar generasi, dan mendokumentasikan sejarah peradaban manusia.</p><p>Sistem penulisan dapat dikelompokkan menjadi beberapa jenis, termasuk alfabet, abjad, abugida, silabaris, dan logogram.</p>"
                },
                sections = listOf(
                    WikiSection(1, if (isEn) "Classification" else "Klasifikasi", 2, "Klasifikasi"),
                    WikiSection(2, if (isEn) "History" else "Sejarah Perkembangan", 2, "Sejarah_Perkembangan")
                ),
                categories = listOf("Bahasa", "Linguistik", "Aksara"),
                language = lang
            )
            lower.contains("alphabet") || lower.contains("alfabet") -> WikiArticle(
                title = "Alphabet",
                displayTitle = "Alphabet",
                shortDescription = if (isEn) "Standardized set of letters" else "Sistem penulisan fonetis",
                heroImageUrl = null,
                pageId = 2201,
                rawWikitext = "{{SHORTDESC:Sistem aksara fonetis}}\n== Etimologi ==\nBerasal dari alpha dan beta.",
                parsedHtml = if (isEn) {
                    "<p>An <b>alphabet</b> is a standardized set of basic written symbols or graphemes (called letters) that represent the phonemes of certain spoken languages.</p>"
                } else {
                    "<p><b>Alfabet</b> adalah sistem aksara yang menggunakan lambang-lambang bunyi (huruf) untuk merepresentasikan fonem bahasa lisan, baik vokal maupun konsonan.</p>"
                },
                sections = listOf(
                    WikiSection(1, if (isEn) "Etymology" else "Etimologi", 2, "Etimologi")
                ),
                categories = listOf("Linguistik", "Aksara"),
                language = lang
            )
            lower.contains("mippedia") -> WikiArticle(
                title = "Mippedia",
                displayTitle = "Mippedia",
                shortDescription = if (isEn) "Free multilingual online encyclopedia" else "Ensiklopedia daring umum multibahasa bebas",
                heroImageUrl = "https://id.mippedia.org/resources/assets/mippedia_logo.png",
                pageId = 1,
                rawWikitext = "{{SHORTDESC:Ensiklopedia daring umum multibahasa bebas}}\n== Tentang Mippedia ==\nProyek pengetahuan bebas.",
                parsedHtml = "<p><b>Mippedia</b> adalah sebuah proyek ensiklopedia daring umum multibahasa berbasis perangkat lunak MediaWiki. Mippedia dirancang agar setiap orang dapat membaca, mencari, dan menyumbang artikel secara bebas dan kolaboratif.</p>",
                sections = listOf(
                    WikiSection(1, "Tentang Mippedia", 2, "Tentang_Mippedia"),
                    WikiSection(2, "Subdomain Proyek", 2, "Subdomain_Proyek")
                ),
                categories = listOf("MediaWiki", "Ensiklopedia", "Proyek Daring"),
                language = lang
            )
            else -> null
        }
    }

    private fun extractFirstImage(html: String): String? {
        val matcher = Pattern.compile("<img[^>]*src=\"([^\"]+)\"", Pattern.DOTALL).matcher(html)
        while (matcher.find()) {
            val src = matcher.group(1) ?: continue
            val lower = src.lowercase()
            // Ignore system icons, buttons, arrows, magnifiers, and ui symbols
            if (lower.contains("icon") || lower.contains("logo") || lower.contains("magnify") ||
                lower.contains("symbol") || lower.contains("badge") || lower.contains("arrow") ||
                lower.contains("button") || lower.contains("question") || lower.contains("lock") ||
                lower.contains("1px") || lower.contains("transparent") || lower.contains("gear") ||
                lower.contains("edit") || lower.endsWith(".svg")
            ) {
                continue
            }
            return when {
                src.startsWith("//") -> "https:$src"
                src.startsWith("/") -> "https://commons.mippedia.org$src"
                else -> src
            }
        }
        return null
    }

    private fun extractInfobox(html: String): WikiInfobox? {
        try {
            val pattern = Pattern.compile("<table[^>]*class=\"[^\"]*infobox[^\"]*\"[^>]*>(.*?)</table>", Pattern.DOTALL)
            val matcher = pattern.matcher(html)
            if (!matcher.find()) return null

            val tableHtml = matcher.group(1) ?: return null

            var title = ""
            val titleMatcher = Pattern.compile("<th[^>]*class=\"[^\"]*infobox-above[^\"]*\"[^>]*>(.*?)</th>", Pattern.DOTALL).matcher(tableHtml)
            if (titleMatcher.find()) {
                title = titleMatcher.group(1).replace(Regex("<[^>]*>"), "").trim()
            }

            var imgUrl: String? = null
            val imgMatcher = Pattern.compile("<img[^>]*src=\"([^\"]+)\"", Pattern.DOTALL).matcher(tableHtml)
            while (imgMatcher.find()) {
                val src = imgMatcher.group(1) ?: continue
                if (!src.contains("icon", ignoreCase = true) && !src.contains("svg", ignoreCase = true)) {
                    imgUrl = when {
                        src.startsWith("//") -> "https:$src"
                        src.startsWith("/") -> "https://commons.mippedia.org$src"
                        else -> src
                    }
                    break
                }
            }

            val items = mutableListOf<Pair<String, String>>()
            val rowPattern = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL)
            val rowMatcher = rowPattern.matcher(tableHtml)
            while (rowMatcher.find()) {
                val rowHtml = rowMatcher.group(1)
                val thMatcher = Pattern.compile("<th[^>]*>(.*?)</th>", Pattern.DOTALL).matcher(rowHtml)
                val tdMatcher = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL).matcher(rowHtml)
                if (thMatcher.find() && tdMatcher.find()) {
                    val key = thMatcher.group(1).replace(Regex("<[^>]*>"), "").trim()
                    val value = tdMatcher.group(1).replace(Regex("<[^>]*>"), "").trim()
                    if (key.isNotBlank() && value.isNotBlank() && key != title) {
                        items.add(Pair(key, value))
                    }
                }
            }

            if (items.isEmpty() && imgUrl == null && title.isBlank()) return null
            return WikiInfobox(title = title, imageUrl = imgUrl, items = items)
        } catch (_: Exception) {
            return null
        }
    }

    private fun extractSocialLinks(html: String): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val socialPatterns = listOf(
            Pair("Instagram", Regex("https?://(?:www\\.)?instagram\\.com/[a-zA-Z0-9_.-]+")),
            Pair("TikTok", Regex("https?://(?:www\\.)?tiktok\\.com/@[a-zA-Z0-9_.-]+")),
            Pair("YouTube", Regex("https?://(?:www\\.)?youtube\\.com/(?:@[a-zA-Z0-9_.-]+|channel/[a-zA-Z0-9_.-]+)")),
            Pair("X / Twitter", Regex("https?://(?:www\\.)?(?:twitter\\.com|x\\.com)/[a-zA-Z0-9_.-]+")),
            Pair("Facebook", Regex("https?://(?:www\\.)?facebook\\.com/[a-zA-Z0-9_.-]+"))
        )

        for ((name, regex) in socialPatterns) {
            val match = regex.find(html)
            if (match != null) {
                list.add(Pair(name, match.value))
            }
        }
        return list
    }
}
