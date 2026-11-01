package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.MippediaBlueDark
import com.example.ui.theme.MippediaBluePrimary
import com.example.ui.theme.MippediaTealAccent
import kotlinx.coroutines.delay

data class OnboardingStep(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun OnboardingDialog(
    onComplete: () -> Unit
) {
    var isSplashStage by remember { mutableStateOf(true) }
    var currentStepIndex by remember { mutableIntStateOf(0) }

    // Splash animation values
    val logoScale = remember { Animatable(0.4f) }
    val logoAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, animationSpec = tween(800, easing = FastOutSlowInEasing))
        logoScale.animateTo(1.1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
        logoScale.animateTo(1.0f, animationSpec = tween(300))
        delay(1200)
        logoAlpha.animateTo(0f, animationSpec = tween(500))
        isSplashStage = false
    }

    val steps = listOf(
        OnboardingStep(
            title = "Selamat Datang di Mippedia",
            subtitle = "Ensiklopedia Bebas & Terbuka",
            description = "Mippedia adalah proyek ensiklopedia daring umum multibahasa berbasis MediaWiki yang hadir menyajikan pengetahuan tepercaya, terstruktur, dan bebas untuk semua orang.",
            icon = Icons.Outlined.AutoStories,
            color = MippediaBluePrimary
        ),
        OnboardingStep(
            title = "Dukungan Multibahasa Real-Time",
            subtitle = "Bahasa Indonesia & Bahasa Inggris",
            description = "Jelajahi artikel dalam Bahasa Indonesia dan Bahasa Inggris secara sinkron dengan satu ketukan tombol beralih bahasa.",
            icon = Icons.Outlined.Translate,
            color = MippediaTealAccent
        ),
        OnboardingStep(
            title = "Mippedia Commons",
            subtitle = "Repositori Berkas Media Terpusat",
            description = "Semua gambar dan berkas visual dikelola secara terpusat melalui repositori berkas Mippedia Commons. Di aplikasi ini, berkas media dipisahkan secara cerdas dari artikel biasa untuk pengalaman visual terbaik.",
            icon = Icons.Outlined.PhotoLibrary,
            color = Color(0xFF2E7D32)
        ),
        OnboardingStep(
            title = "Penyuntingan & Kolaborasi",
            subtitle = "Sunting Langsung dari Aplikasi",
            description = "Anda dapat membaca maupun menyunting artikel secara langsung menggunakan editor wikitext terintegrasi, dilengkapi deteksi perlindungan artikel otomatis.",
            icon = Icons.Outlined.EditNote,
            color = Color(0xFFE67E22)
        ),
        OnboardingStep(
            title = "Akses Akun & Tanpa Hambatan",
            subtitle = "Akun Terdaftar & Akun Sementara IP",
            description = "Masuk dengan akun pengguna Mippedia Anda atau langsung membaca dan berkontribusi sebagai Akun Sementara dengan identitas yang aman dan transparan.",
            icon = Icons.Outlined.VerifiedUser,
            color = Color(0xFF6A1B9A)
        )
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(MippediaBlueDark, Color(0xFF071926))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSplashStage) {
                // Moving animated splash with logo fade in & out
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.mippedia_logo),
                        contentDescription = "Logo Mippedia",
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                    )
                    Text(
                        text = "MIPPEDIA",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "Ensiklopedia Bebas Multibahasa",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            } else {
                // Interactive 5-slide Onboarding Cards
                val currentStep = steps[currentStepIndex]

                AnimatedContent(
                    targetState = currentStepIndex,
                    transitionSpec = {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    },
                    label = "OnboardingSlide"
                ) { stepIdx ->
                    val step = steps[stepIdx]

                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .wrapContentHeight()
                            .padding(vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            // Icon Header
                            Surface(
                                shape = CircleShape,
                                color = step.color.copy(alpha = 0.15f),
                                modifier = Modifier.size(76.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = step.icon,
                                        contentDescription = null,
                                        tint = step.color,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }

                            // Texts
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = step.subtitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = step.color,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = step.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )

                            // Step Dots Indicator
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                steps.indices.forEach { index ->
                                    val isSelected = index == stepIdx
                                    Box(
                                        modifier = Modifier
                                            .height(8.dp)
                                            .width(if (isSelected) 24.dp else 8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) step.color else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            )
                                    )
                                }
                            }

                            // Next / Start Button
                            Button(
                                onClick = {
                                    if (stepIdx < steps.size - 1) {
                                        currentStepIndex++
                                    } else {
                                        onComplete()
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (stepIdx < steps.size - 1) "Lanjutkan" else "Mulai Menjelajah Mippedia",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                if (stepIdx < steps.size - 1) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
