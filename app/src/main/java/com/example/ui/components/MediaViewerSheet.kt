package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun MediaViewerSheet(
    media: WikiMediaItem,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MippediaTealAccent.copy(alpha = 0.15f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = "Commons Media",
                            tint = MippediaTealAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Berkas Commons",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MippediaTealAccent
                        )
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            // Media Title
            Text(
                text = media.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Large Image Display
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 340.dp)
            ) {
                AsyncImage(
                    model = media.url,
                    contentDescription = media.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Media Metadata Grid Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Informasi Berkas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    MetadataRow(label = "Repositori Berkas", value = "Mippedia Commons")
                    if (media.width > 0 && media.height > 0) {
                        MetadataRow(label = "Resolusi", value = "${media.width} × ${media.height} piksel")
                    }
                    if (media.sizeBytes > 0) {
                        val sizeKb = media.sizeBytes / 1024
                        val sizeText = if (sizeKb > 1024) "${String.format("%.1f", sizeKb / 1024f)} MB" else "$sizeKb KB"
                        MetadataRow(label = "Ukuran Berkas", value = sizeText)
                    }
                    if (media.mime.isNotBlank()) {
                        MetadataRow(label = "Format MIME", value = media.mime)
                    }
                    if (media.user.isNotBlank()) {
                        MetadataRow(label = "Pengunggah", value = media.user)
                    }
                    if (media.timestamp.isNotBlank()) {
                        MetadataRow(label = "Tanggal Unggah", value = media.timestamp.take(10))
                    }
                }
            }

            // Action Buttons: Instant Download + Open In Browser + Share
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Instant 1-Click Download Button
                Button(
                    onClick = {
                        try {
                            val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
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
                            android.widget.Toast.makeText(context, "Mulai mengunduh: $fileName", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Gagal mengunduh: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Unduh Gambar",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Unduh Berkas", fontWeight = FontWeight.Bold)
                }

                // Open in Web
                FilledTonalButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(media.descriptionUrl.ifEmpty { media.url }))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "Buka Berkas",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Detail")
                }

                // Share
                OutlinedIconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, media.url)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Bagikan Tautan Berkas"))
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Bagikan")
                }
            }
        }
    }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
