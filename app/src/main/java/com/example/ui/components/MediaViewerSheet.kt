package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.WikiMediaItem
import com.example.ui.theme.MippediaTealAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    media: WikiMediaItem,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Smooth Android back button handling - never closes prematurely by accidental swipe
    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Penampil Berkas Media",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = media.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, media.url)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Bagikan Tautan Berkas"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Bagikan Tautan")
                    }
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(media.descriptionUrl.ifEmpty { media.url }))
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Buka di Browser")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Repositori Tag
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MippediaTealAccent.copy(alpha = 0.15f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = "Commons Media",
                        tint = MippediaTealAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Repositori Bersama: Mippedia Commons",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MippediaTealAccent
                    )
                }
            }

            // High-Resolution Image Display Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 420.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = media.url,
                        contentDescription = media.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Media Title
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Detailed Metadata Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Informasi & Rincian Berkas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    MetadataRow(label = "Nama Berkas", value = media.title)
                    MetadataRow(label = "Repositori", value = "Mippedia Commons")
                    if (media.width > 0 && media.height > 0) {
                        MetadataRow(label = "Dimensi Gambar", value = "${media.width} × ${media.height} piksel")
                    }
                    if (media.sizeBytes > 0) {
                        val sizeKb = media.sizeBytes / 1024
                        val sizeText = if (sizeKb > 1024) "${String.format("%.1f", sizeKb / 1024f)} MB" else "$sizeKb KB"
                        MetadataRow(label = "Ukuran Data", value = sizeText)
                    }
                    if (media.mime.isNotBlank()) {
                        MetadataRow(label = "Format MIME", value = media.mime)
                    }
                    if (media.user.isNotBlank()) {
                        MetadataRow(label = "Pengunggah Berkas", value = media.user)
                    }
                    if (media.timestamp.isNotBlank()) {
                        MetadataRow(label = "Waktu Unggah", value = media.timestamp.take(10))
                    }
                }
            }

            // Action Buttons: Unduh + Salin Tautan + Buka Web
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        try {
                            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                            val cleanName = media.title
                                .replace("File:", "", ignoreCase = true)
                                .replace("Berkas:", "", ignoreCase = true)
                                .replace("/", "_")
                                .replace(" ", "_")
                                .trim()
                            val extension = when {
                                media.url.endsWith(".svg", ignoreCase = true) -> ".svg"
                                media.url.endsWith(".png", ignoreCase = true) -> ".png"
                                media.url.endsWith(".webp", ignoreCase = true) -> ".webp"
                                media.url.endsWith(".gif", ignoreCase = true) -> ".gif"
                                else -> ".jpg"
                            }
                            val fileName = if (cleanName.contains(".")) cleanName else "$cleanName$extension"
                            val request = android.app.DownloadManager.Request(Uri.parse(media.url)).apply {
                                setTitle(fileName)
                                setDescription("Mengunduh gambar dari Mippedia...")
                                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_PICTURES, "Mippedia/$fileName")
                            }
                            downloadManager.enqueue(request)
                            Toast.makeText(context, "Mulai mengunduh: $fileName", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal mengunduh: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Unduh Gambar",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unduh Berkas ke Galeri HP", fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("URL Berkas Mippedia", media.url)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Tautan gambar disalin ke papan klip", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin Tautan", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(media.descriptionUrl.ifEmpty { media.url }))
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Buka Web", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

// Backward compatibility alias
@Composable
fun MediaViewerSheet(
    media: WikiMediaItem,
    onClose: () -> Unit
) {
    MediaViewerScreen(
        media = media,
        onClose = onClose
    )
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}
