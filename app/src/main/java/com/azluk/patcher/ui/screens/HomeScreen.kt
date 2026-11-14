package com.azluk.patcher.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.azluk.patcher.R
import com.azluk.patcher.core.AppInfo
import com.azluk.patcher.core.PatchStatus
import com.azluk.patcher.ui.theme.*
import com.azluk.patcher.viewmodel.MainViewModel
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun HomeScreen(navController: NavController, vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    Column(
        Modifier
            .fillMaxSize()
            .background(AzlukBg)
            .systemBarsPadding()
    ) {
        // ── Animated header ───────────────────────────────────────────────────
        AzlukHeader(state, vm)

        // ── Content ───────────────────────────────────────────────────────────
        Box(Modifier.weight(1f)) {
            when {
                state.isLoading -> {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement   = Arrangement.Center,
                        horizontalAlignment   = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = AzlukBlue, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Loading apps…", color = AzlukOnSurface, fontSize = 13.sp)
                    }
                }
                state.filteredApps.isEmpty() && !state.isLoading -> {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement   = Arrangement.Center,
                        horizontalAlignment   = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.SearchOff, null, tint = AzlukOnSurface, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No apps found", color = AzlukOnSurface, fontSize = 15.sp)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding      = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { StatsBanner(state.filteredApps); Spacer(Modifier.height(4.dp)) }
                        items(state.filteredApps, key = { it.packageName }) { app ->
                            AppCard(app) { navController.navigate("detail/${app.packageName}") }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }

            if (state.isScanning) {
                Surface(
                    color    = AzlukSurface.copy(0.97f),
                    shape    = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    border   = BorderStroke(1.dp, AzlukBlue.copy(0.2f)),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment          = Alignment.CenterVertically,
                        horizontalArrangement      = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = AzlukBlue, strokeWidth = 2.dp)
                        LinearProgressIndicator(
                            modifier   = Modifier.weight(1f).height(3.dp).clip(CircleShape),
                            color      = AzlukBlue,
                            trackColor = AzlukSurfaceVar
                        )
                        Text("Scanning…", color = AzlukBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AzlukHeader(state: com.azluk.patcher.viewmodel.MainUiState, vm: MainViewModel) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    val rotation by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label         = "spin"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.6f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "pulse"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f   to AzlukSurface2,
                    0.5f to AzlukSurface.copy(0.96f),
                    1f   to AzlukBg
                )
            )
    ) {
        // Faint watermark glow
        Box(
            Modifier
                .size(200.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 60.dp)
                .background(
                    Brush.radialGradient(listOf(AzlukBlue.copy(pulse * 0.08f), Color.Transparent)),
                    CircleShape
                )
        )

        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // ── Top row ────────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo + title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(52.dp).background(
                                Brush.radialGradient(listOf(AzlukBlue.copy(0.25f), Color.Transparent)),
                                CircleShape
                            )
                        )
                        Image(
                            painter            = painterResource(R.drawable.azluk_logo_transparent),
                            contentDescription = "AzlukPatcher",
                            modifier           = Modifier.size(46.dp).rotate(rotation)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "AzlukPatcher",
                            color         = AzlukOnBg,
                            fontSize      = 20.sp,
                            fontWeight    = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Surface(
                                color  = AzlukBlue,
                                shape  = RoundedCornerShape(4.dp)
                            ) {
                                Text("V10", color = Color.White, fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier   = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                            Text("by Azluk", color = AzlukOnSurface, fontSize = 10.sp)
                        }
                    }
                }

                // Refresh button
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Default.Refresh, null, tint = AzlukBlue, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Search bar ─────────────────────────────────────────────────────
            Surface(
                color  = AzlukSurface2,
                shape  = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, AzlukSurfaceVar),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = AzlukOnSurface, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value         = state.query,
                        onValueChange = vm::setQuery,
                        singleLine    = true,
                        textStyle     = TextStyle(color = AzlukOnBg, fontSize = 14.sp),
                        modifier      = Modifier.weight(1f).padding(vertical = 13.dp),
                        decorationBox = @Composable { inner ->
                            if (state.query.isEmpty()) {
                                Text("Search apps, packages…", color = AzlukOnSurface.copy(0.45f), fontSize = 14.sp)
                            }
                            inner()
                        }
                    )
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, null, tint = AzlukOnSurface, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Filter chips ───────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("User", "System", "All").forEachIndexed { i, label ->
                    val sel = state.filter == i
                    Surface(
                        color    = if (sel) AzlukBlue else AzlukSurface2,
                        shape    = RoundedCornerShape(20.dp),
                        border   = if (!sel) BorderStroke(1.dp, AzlukSurfaceVar) else null,
                        modifier = Modifier.clickable { vm.setFilter(i) }
                    ) {
                        Text(
                            label,
                            color      = if (sel) Color.White else AzlukOnSurface,
                            fontSize   = 12.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            modifier   = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.filteredApps.size} apps",
                    color    = AzlukOnSurface,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
private fun StatsBanner(apps: List<AppInfo>) {
    val patchable = apps.count { it.patchStatus == PatchStatus.PATCHABLE }
    val likely    = apps.count { it.patchStatus == PatchStatus.LIKELY }
    val complex   = apps.count { it.patchStatus == PatchStatus.COMPLEX }

    Surface(
        color    = AzlukSurface,
        shape    = RoundedCornerShape(18.dp),
        border   = BorderStroke(1.dp, AzlukSurfaceVar),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(patchable.toString(), "Patchable", AzlukSuccess)
            VerticalDivider(Modifier.height(32.dp), color = AzlukSurfaceVar)
            StatItem(likely.toString(),    "Likely",    AzlukBlue)
            VerticalDivider(Modifier.height(32.dp), color = AzlukSurfaceVar)
            StatItem(complex.toString(),   "Complex",   AzlukWarning)
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = AzlukOnSurface, fontSize = 10.sp)
    }
}

@Composable
fun AppCard(app: AppInfo, onClick: () -> Unit) {
    val statusColor = when (app.patchStatus) {
        PatchStatus.PATCHABLE -> AzlukSuccess
        PatchStatus.LIKELY    -> AzlukBlue
        PatchStatus.COMPLEX   -> AzlukWarning
        PatchStatus.UNKNOWN   -> AzlukOnSurface.copy(0.3f)
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color    = AzlukSurface,
        shape    = RoundedCornerShape(18.dp),
        border   = BorderStroke(1.dp, statusColor.copy(0.18f))
    ) {
        Box {
            // Left accent bar
            Box(
                Modifier.width(4.dp).height(56.dp).align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(statusColor.copy(0.7f))
            )

            Row(
                Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon container
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(50.dp).background(
                            Brush.radialGradient(listOf(statusColor.copy(0.14f), Color.Transparent)),
                            RoundedCornerShape(13.dp)
                        )
                    )
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(AzlukSurface2),
                        contentAlignment = Alignment.Center
                    ) {
                        if (app.icon != null) {
                            Image(
                                painter            = rememberDrawablePainter(app.icon),
                                contentDescription = app.appName,
                                modifier           = Modifier.size(42.dp)
                            )
                        } else {
                            Icon(Icons.Default.Android, null, tint = AzlukOnSurface, modifier = Modifier.size(26.dp))
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(app.appName, color = AzlukOnBg, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, color = AzlukOnSurface, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(app.patchStatus, app.opportunityCount)
                        if (app.isSystemApp) MiniTag("SYS", AzlukWarning)
                        if (app.apkSizeMb > 0) MiniTag("%.0fM".format(app.apkSizeMb), AzlukOnSurface.copy(0.5f))
                    }
                }

                Surface(color = statusColor.copy(0.1f), shape = CircleShape, modifier = Modifier.size(34.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ChevronRight, null, tint = statusColor.copy(0.8f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTag(text: String, color: Color) {
    Surface(color = color.copy(0.1f), shape = RoundedCornerShape(5.dp)) {
        Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
    }
}

@Composable
fun StatusChip(status: PatchStatus, count: Int) {
    val (color, label, icon) = when (status) {
        PatchStatus.PATCHABLE -> Triple(AzlukSuccess, "Patchable${if (count > 0) " ($count)" else ""}", Icons.Default.CheckCircle)
        PatchStatus.LIKELY    -> Triple(AzlukBlue,    "Likely",             Icons.Default.TipsAndUpdates)
        PatchStatus.COMPLEX   -> Triple(AzlukWarning, "Complex",            Icons.Default.Warning)
        PatchStatus.UNKNOWN   -> Triple(AzlukOnSurface.copy(0.4f), "Scanning", Icons.Default.HourglassEmpty)
    }
    Surface(color = color.copy(0.1f), shape = RoundedCornerShape(6.dp)) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(10.dp))
            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun rememberDrawablePainter(drawable: Drawable): androidx.compose.ui.graphics.painter.Painter =
    remember(drawable) {
        object : androidx.compose.ui.graphics.painter.Painter() {
            override val intrinsicSize = Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
            override fun DrawScope.onDraw() {
                drawIntoCanvas { canvas ->
                    drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                    drawable.draw(canvas.nativeCanvas)
                }
            }
        }
    }
