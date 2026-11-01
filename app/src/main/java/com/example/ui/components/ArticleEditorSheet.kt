package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.WikiUserInfo
import com.example.ui.theme.MippediaAmber
import com.example.ui.theme.MippediaBluePrimary
import com.example.ui.theme.MippediaGreenSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleEditorSheet(
    title: String,
    content: String,
    summary: String,
    isMinor: Boolean,
    isPublishing: Boolean,
    currentUser: WikiUserInfo,
    onContentChange: (String) -> Unit,
    onSummaryChange: (String) -> Unit,
    onMinorChange: (Boolean) -> Unit,
    onInsertSyntax: (String, String) -> Unit,
    onPublish: () -> Unit,
    onClose: () -> Unit
) {
    var isPreviewMode by remember { mutableStateOf(false) }
    val syntaxScroll = rememberScrollState()

    val sheetScrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = { if (!isPublishing) onClose() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(sheetScrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sunting Artikel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { isPreviewMode = !isPreviewMode }) {
                        Text(if (isPreviewMode) "Kode Sumber" else "Pratinjau")
                    }
                    IconButton(onClick = onClose, enabled = !isPublishing) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }
            }

            // Anon User Notice
            if (currentUser.isAnon) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MippediaAmber.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Akun Sementara",
                            tint = MippediaAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Anda menyunting sebagai Akun Sementara / Anonim (${currentUser.username}). Perubahan Anda akan dicatat dalam riwayat halaman.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Quick Formatting Toolbar
            if (!isPreviewMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(syntaxScroll),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { onInsertSyntax("'''", "'''") },
                        label = { Text("Tebal (B)", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onInsertSyntax("''", "''") },
                        label = { Text("Miring (I)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onInsertSyntax("== ", " ==") },
                        label = { Text("Judul ==", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onInsertSyntax("[[", "]]") },
                        label = { Text("Tautan [[]]", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onInsertSyntax("[[Berkas:", "|jmpl|keterangan]]") },
                        label = { Text("Berkas Media", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onInsertSyntax("{{", "}}") },
                        label = { Text("Templat {{}}", fontSize = 11.sp) }
                    )
                }
            }

            // Editor Input vs Preview (Ensuring minimum height so soft keyboard never shrinks it to a slit)
            // Editor Input vs Preview (Ensuring minimum height so soft keyboard never shrinks it to a slit)
            if (isPreviewMode) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 460.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Pratinjau Artikel",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 1.dp
                        )
                        WikitextPreviewView(content)
                    }
                }
            } else {
                OutlinedTextField(
                    value = content,
                    onValueChange = onContentChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 460.dp),
                    placeholder = { Text("Tulis atau ubah isi wikitext artikel di sini...") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Edit Summary & Minor Checkbox
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = summary,
                    onValueChange = onSummaryChange,
                    label = { Text("Ringkasan Suntingan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isMinor,
                        onCheckedChange = onMinorChange
                    )
                    Text(
                        text = "Tandai sebagai suntingan kecil",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Publish Button with animated spinner
            Button(
                onClick = onPublish,
                enabled = !isPublishing && content.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isPublishing) {
                    SmallSpinningLoader(color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Menerbitkan Perubahan...")
                } else {
                    Icon(imageVector = Icons.Default.Publish, contentDescription = "Terbitkan")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Terbitkan Perubahan")
                }
            }
        }
    }
}

private sealed class PreviewItem {
    data class Heading(val text: String, val level: Int) : PreviewItem()
    data class Bullet(val text: String) : PreviewItem()
    data class Paragraph(val text: String) : PreviewItem()
}

private fun parsePreviewItems(content: String): List<PreviewItem> {
    val cleanedText = cleanWikitextForPreview(content)
    if (cleanedText.isBlank() || cleanedText == "(Belum ada teks untuk dipratinjau)") {
        return emptyList()
    }

    val items = mutableListOf<PreviewItem>()
    val lines = cleanedText.lines()
    val paragraphBuffer = StringBuilder()

    fun flushParagraph() {
        if (paragraphBuffer.isNotBlank()) {
            items.add(PreviewItem.Paragraph(paragraphBuffer.toString().trim()))
            paragraphBuffer.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            flushParagraph()
            continue
        }

        val headingMatch = Regex("^(=+)\\s*([^=]+?)\\s*\\1$").find(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            val level = headingMatch.groupValues[1].length
            val title = headingMatch.groupValues[2].trim()
            items.add(PreviewItem.Heading(title, level))
        } else if (trimmed.startsWith("• ") || trimmed.startsWith("* ")) {
            flushParagraph()
            val bulletText = trimmed.removePrefix("• ").removePrefix("* ").trim()
            items.add(PreviewItem.Bullet(bulletText))
        } else {
            if (paragraphBuffer.isNotEmpty()) {
                paragraphBuffer.append(" ")
            }
            paragraphBuffer.append(trimmed)
        }
    }
    flushParagraph()
    return items
}

/**
 * Preview renderer that neatly formats wikitext sections, headings,
 * paragraphs, and bullet points without icons or emojis on subheadings.
 */
@Composable
private fun WikitextPreviewView(content: String) {
    val items = remember(content) { parsePreviewItems(content) }

    if (items.isEmpty()) {
        Text(
            text = "(Belum ada teks untuk dipratinjau)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            when (item) {
                is PreviewItem.Heading -> {
                    Text(
                        text = item.text,
                        style = when (item.level) {
                            2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            3 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        },
                        color = if (item.level <= 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                }
                is PreviewItem.Bullet -> {
                    Row(
                        modifier = Modifier.padding(start = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is PreviewItem.Paragraph -> {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Cleans wikitext for preview so users do not see raw template tags
 * like {{SHORTDESC:...}}, {{Hatnote|...}}, {{Infobox...}}, or <ref> tags.
 * Pure text output with NO icons or emojis on headings.
 */
fun cleanWikitextForPreview(rawWikitext: String): String {
    if (rawWikitext.isBlank()) return "(Belum ada teks untuk dipratinjau)"

    var text = rawWikitext

    // 1. Remove references <ref...>...</ref> and self-closing <ref.../>
    text = text.replace(Regex("<ref[^>]*>.*?</ref>", RegexOption.DOT_MATCHES_ALL), "")
    text = text.replace(Regex("<ref[^/>]*/\\s*>"), "")

    // 2. Strip all nested and unnested templates (Hatnote, Infobox, Tanpa referensi, Tanpa Kategori, Stub, etc.)
    var prev = ""
    var iters = 0
    while (text != prev && iters < 8) {
        prev = text
        text = text.replace(Regex("\\{\\{[^{}]*\\}\\}", RegexOption.DOT_MATCHES_ALL), "")
        iters++
    }

    // 3. Remove leftover infobox rows if any unclosed template left pipes
    text = text.lines()
        .filterNot { line ->
            val trimmed = line.trim()
            trimmed.startsWith("|") || trimmed.startsWith("}}") || trimmed.startsWith("{{")
        }
        .joinToString("\n")

    // 4. Clean wiki links [[Target|Label]] -> Label and [[Link]] -> Link
    text = text.replace(Regex("\\[\\[(?:Berkas|File):[^\\]]+\\]\\]", RegexOption.IGNORE_CASE), "")
    text = text.replace(Regex("\\[\\[(?:[^|\\]]+\\|)?([^\\]]+)\\]\\]"), "$1")

    // 5. External links [http... Label] -> Label or remove bare url
    text = text.replace(Regex("\\[https?://[^\\s\\]]+\\s+([^\\]]+)\\]"), "$1")
    text = text.replace(Regex("\\[https?://[^\\s\\]]+\\]"), "")

    // 6. Bold and Italics
    text = text.replace(Regex("'''+(.*?)'''+"), "$1")
    text = text.replace(Regex("''+(.*?)''+"), "$1")

    // 7. Bullet points * -> •
    text = text.replace(Regex("(?m)^\\*\\s*"), "• ")

    // 8. Clean multiple blank lines
    text = text.replace(Regex("\\n{3,}"), "\n\n").trim()

    return if (text.isBlank()) "(Teks artikel bersih)" else text
}
