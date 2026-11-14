package com.azluk.patcher.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.azluk.patcher.R
import com.azluk.patcher.ui.theme.*
import com.azluk.patcher.viewmodel.PerformanceTier
import com.azluk.patcher.viewmodel.SystemProfile
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(profile: SystemProfile, onComplete: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")

    val spinAngle by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label         = "logo_spin"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue  = 0.2f,
        targetValue   = 0.5f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "pulse"
    )
    val ring by infiniteTransition.animateFloat(
        initialValue  = 0.6f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label         = "ring"
    )

    val contentAlpha by animateFloatAsState(1f, tween(600), label = "fade")

    LaunchedEffect(profile.isReady) {
        if (profile.isReady) { delay(900); onComplete() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors  = listOf(AzlukSurface2.copy(0.9f), AzlukBg),
                    radius  = 1400f
                )
            )
            .alpha(contentAlpha),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background rings
        Box(Modifier.size(320.dp)) {
            Box(
                Modifier.fillMaxSize().alpha(0.06f)
                    .border(1.dp, AzlukBlue, CircleShape)
                    .scale(ring)
            )
            Box(
                Modifier.size(240.dp).align(Alignment.Center).alpha(0.08f)
                    .border(1.dp, AzlukCyan, CircleShape)
                    .scale(1.2f - ring * 0.2f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Logo ──────────────────────────────────────────────────────────
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                Box(
                    Modifier.size(130.dp).background(
                        Brush.radialGradient(listOf(AzlukBlue.copy(pulse), Color.Transparent)),
                        CircleShape
                    )
                )
                Image(
                    painter            = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = "AzlukPatcher",
                    modifier           = Modifier
                        .size(104.dp)
                        .rotate(if (!profile.isReady) spinAngle else 0f)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "AzlukPatcher",
                color         = AzlukOnBg,
                fontSize      = 30.sp,
                fontWeight    = FontWeight.Black,
                letterSpacing = (-1.2).sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Surface(
                    color  = AzlukBlue,
                    shape  = RoundedCornerShape(6.dp)
                ) {
                    Text("V10", color = Color.White, fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier   = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Text("by Azluk", color = AzlukOnSurface, fontSize = 12.sp)
            }

            Spacer(Modifier.height(44.dp))

            // ── System check card ─────────────────────────────────────────────
            Surface(
                color    = AzlukSurface,
                shape    = RoundedCornerShape(22.dp),
                border   = BorderStroke(1.dp, AzlukSurfaceVar),
                modifier = Modifier.width(310.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    CheckRow("System",      profile.androidApi > 0,
                        if (profile.androidApi > 0) "Android API ${profile.androidApi}" else "…")
                    CheckRow("CPU",         profile.cpuCores > 0,
                        if (profile.cpuCores > 0) "${profile.cpuCores} cores · ${profile.arch}" else "…")
                    CheckRow("RAM",         profile.ramTotalMb > 0,
                        if (profile.ramTotalMb > 0) "${profile.ramFreeMb}/${profile.ramTotalMb} MB free" else "…")
                    CheckRow("Storage",     profile.storageFreeGb > 0,
                        if (profile.storageFreeGb > 0) "${"%.1f".format(profile.storageFreeGb)} GB free" else "…")
                    CheckRow("Root",        profile.isReady,
                        if (!profile.isReady) "…"
                        else if (profile.hasRoot) "✓ ${profile.rootMethod}" else "Not rooted")
                    CheckRow("Performance", profile.isReady,
                        if (!profile.isReady) "…"
                        else when (profile.performanceTier) {
                            PerformanceTier.HIGH   -> "High — parallel patching"
                            PerformanceTier.MEDIUM -> "Medium"
                            PerformanceTier.LOW    -> "Low — sequential mode"
                        })
                }
            }

            if (profile.warnings.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    profile.warnings.forEach { w ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = AzlukWarning, modifier = Modifier.size(14.dp))
                            Text(w, color = AzlukWarning, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            if (!profile.isReady) {
                LinearProgressIndicator(
                    modifier   = Modifier.width(220.dp).height(3.dp).clip(CircleShape),
                    color      = AzlukBlue,
                    trackColor = AzlukSurfaceVar
                )
                Spacer(Modifier.height(10.dp))
                Text("Initializing engine…", color = AzlukOnSurface, fontSize = 12.sp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = AzlukSuccess, modifier = Modifier.size(16.dp))
                    Text("Ready", color = AzlukSuccess, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, done: Boolean, value: String) {
    val alpha by animateFloatAsState(if (done) 1f else 0.5f, tween(400), label = "row_$label")
    Row(
        Modifier.fillMaxWidth().alpha(alpha),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, color = AzlukOnSurface, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = if (done) AzlukOnBg else AzlukOnSurface,
                fontSize = 12.sp, fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal)
            if (done) {
                Icon(Icons.Default.CheckCircle, null, tint = AzlukSuccess, modifier = Modifier.size(14.dp))
            } else {
                CircularProgressIndicator(Modifier.size(12.dp), color = AzlukBlue, strokeWidth = 1.5.dp)
            }
        }
    }
}
