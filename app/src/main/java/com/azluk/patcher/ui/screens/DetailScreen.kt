package com.azluk.patcher.ui.screens

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.azluk.patcher.core.*
import com.azluk.patcher.engine.ApkEngine
import com.azluk.patcher.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private data class AppDetail(
    val name:        String,
    val pkg:         String,
    val version:     String,
    val versionCode: Long,
    val apkSize:     Long,
    val installDate: Long,
    val updateDate:  Long,
    val isSystem:    Boolean,
    val isDebug:     Boolean,
    val dataDir:     String,
    val nativeDir:   String,
    val targetSdk:   Int,
    val minSdk:      Int,
    val permissions: List<String>,
    val activities:  List<String>,
    val services:    List<String>,
    val receivers:   List<String>,
    val icon:        Drawable?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(pkg: String, navController: NavController) {
    val ctx = LocalContext.current
    val pm  = ctx.packageManager

    val detail = remember(pkg) {
        try {
            val ai = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
            val pi = pm.getPackageInfo(pkg,
                PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES  or
                PackageManager.GET_SERVICES    or
                PackageManager.GET_RECEIVERS)
            AppDetail(
                name        = pm.getApplicationLabel(ai).toString(),
                pkg         = pkg,
                version     = pi.versionName ?: "?",
                versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode
                              else @Suppress("DEPRECATION") pi.versionCode.toLong(),
                apkSize     = File(ai.sourceDir).length(),
                installDate = pi.firstInstallTime,
                updateDate  = pi.lastUpdateTime,
                isSystem    = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                isDebug     = (ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                dataDir     = ai.dataDir ?: "N/A",
                nativeDir   = ai.nativeLibraryDir ?: "N/A",
                targetSdk   = ai.targetSdkVersion,
                minSdk      = if (android.os.Build.VERSION.SDK_INT >= 24) ai.minSdkVersion else 0,
                permissions = pi.requestedPermissions?.toList() ?: emptyList(),
                activities  = pi.activities?.map  { it.name.substringAfterLast(".") } ?: emptyList(),
                services    = pi.services?.map    { it.name.substringAfterLast(".") } ?: emptyList(),
                receivers   = pi.receivers?.map   { it.name.substringAfterLast(".") } ?: emptyList(),
                icon        = try { pm.getApplicationIcon(ai) } catch (_: Exception) { null }
            )
        } catch (_: Exception) { null }
    }

    if (detail == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("App not found", color = AzlukError)
        }
        return
    }

    // Scan state
    var scanLoading by remember { mutableStateOf(true) }
    var scanError   by remember { mutableStateOf<String?>(null) }
    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }

    LaunchedEffect(pkg) {
        val engine = ApkEngine(ctx)
        val result = withContext(Dispatchers.IO) { runCatching { engine.scan(pkg) } }
        result.fold(
            onSuccess = { scanResults = it; scanLoading = false },
            onFailure = { scanError = it.message; scanLoading = false }
        )
    }

    var activeTab by remember { mutableStateOf(0) }
    val fmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Scaffold(
        containerColor = AzlukBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(detail.name, color = AzlukOnBg, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AzlukOnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzlukSurface)
            )
        },
        bottomBar = {
            Surface(color = AzlukSurface, tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick  = {
                            runCatching { ctx.startActivity(pm.getLaunchIntentForPackage(pkg)!!) }
                        },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(14.dp),
                        border   = BorderStroke(1.dp, AzlukSurfaceVar)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp), tint = AzlukSuccess)
                        Spacer(Modifier.width(4.dp))
                        Text("Launch", color = AzlukSuccess, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick  = {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
                        },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(14.dp),
                        border   = BorderStroke(1.dp, AzlukSurfaceVar)
                    ) {
                        Icon(Icons.Default.Settings, null, Modifier.size(16.dp), tint = AzlukOnSurface)
                        Spacer(Modifier.width(4.dp))
                        Text("Settings", color = AzlukOnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick  = { navController.navigate("patch/$pkg") },
                        modifier = Modifier.weight(1.4f),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = AzlukBlue)
                    ) {
                        Icon(Icons.Default.Build, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Patch", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── App header ────────────────────────────────────────────────────
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(AzlukSurface, AzlukBg))
                ).padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)).background(AzlukSurface2),
                        contentAlignment = Alignment.Center
                    ) {
                        if (detail.icon != null) {
                            Image(
                                painter            = rememberDrawablePainter(detail.icon),
                                contentDescription = null,
                                modifier           = Modifier.size(60.dp)
                            )
                        } else {
                            Icon(Icons.Default.Android, null, Modifier.size(38.dp), tint = AzlukBlue)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(detail.name, color = AzlukOnBg, fontWeight = FontWeight.Bold,
                            fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(detail.pkg, color = AzlukOnSurface, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            InfoChip("v${detail.version}", AzlukBlue)
                            InfoChip(fmtSize(detail.apkSize), AzlukOnSurface)
                            InfoChip("API ${detail.targetSdk}", AzlukCyan)
                            if (detail.isSystem) InfoChip("SYS", AzlukWarning)
                            if (detail.isDebug)  InfoChip("DEBUG", AzlukSuccess)
                        }
                    }
                }
            }

            // ── Tabs ──────────────────────────────────────────────────────────
            val tabs = listOf("Info", "Patches", "Perms", "Components")
            TabRow(
                selectedTabIndex = activeTab,
                containerColor   = AzlukSurface,
                contentColor     = AzlukBlue,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(positions[activeTab]),
                        color = AzlukBlue, height = 2.dp
                    )
                }
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = activeTab == i,
                        onClick  = { activeTab = i },
                        text = {
                            Text(title, fontSize = 13.sp,
                                color = if (activeTab == i) AzlukBlue else AzlukOnSurface)
                        }
                    )
                }
            }

            when (activeTab) {
                0 -> InfoTab(detail, fmt)
                1 -> PatchesTab(scanLoading, scanError, scanResults)
                2 -> PermsTab(detail.permissions)
                3 -> ComponentsTab(detail)
            }
        }
    }
}

@Composable
private fun InfoTab(d: AppDetail, fmt: SimpleDateFormat) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InfoSection("Package") {
            InfoRow("Package",    d.pkg)
            InfoRow("Version",    "${d.version} (${d.versionCode})")
            InfoRow("Size",       fmtSize(d.apkSize))
            InfoRow("Target SDK", "API ${d.targetSdk}")
            InfoRow("Min SDK",    "API ${d.minSdk}")
            InfoRow("Type",       if (d.isSystem) "System" else "User")
            InfoRow("Debuggable", if (d.isDebug) "Yes" else "No")
        }
        InfoSection("Dates") {
            InfoRow("Installed", fmt.format(Date(d.installDate)))
            InfoRow("Updated",   fmt.format(Date(d.updateDate)))
        }
        InfoSection("Paths") {
            InfoRow("Data",       d.dataDir)
            InfoRow("Native",     d.nativeDir)
        }
    }
}

@Composable
private fun PatchesTab(loading: Boolean, error: String?, results: List<ScanResult>) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(32.dp), color = AzlukBlue, strokeWidth = 2.5.dp)
                Text("Scanning DEX…", color = AzlukOnSurface, fontSize = 12.sp)
            }
        }
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ErrorOutline, null, Modifier.size(32.dp), tint = AzlukError)
                Text(error, color = AzlukError, fontSize = 12.sp)
            }
        }
        else -> {
            val detected = results.mapNotNull { r ->
                runCatching { PatchType.valueOf(r.patchType) }.getOrNull()
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Summary
                Surface(
                    color  = if (detected.isEmpty()) AzlukSurface else AzlukSuccess.copy(0.07f),
                    shape  = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (detected.isEmpty()) AzlukSurfaceVar else AzlukSuccess.copy(0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            if (detected.isEmpty()) Icons.Default.SearchOff else Icons.Default.CheckCircle,
                            null, Modifier.size(18.dp),
                            tint = if (detected.isEmpty()) AzlukOnSurface else AzlukSuccess
                        )
                        Text(
                            if (detected.isEmpty()) "No patches auto-detected"
                            else "${detected.size} patches available",
                            color = if (detected.isEmpty()) AzlukOnSurface else AzlukSuccess,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                        )
                    }
                }

                results.forEach { r ->
                    val type = runCatching { PatchType.valueOf(r.patchType) }.getOrNull()
                    val color = when (type?.category) {
                        "bypass"   -> AzlukBlue
                        "ads"      -> AzlukWarning
                        "security" -> AzlukError
                        "dev"      -> AzlukCyan
                        else       -> AzlukSuccess
                    }
                    Surface(
                        color  = AzlukSurface,
                        shape  = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, color.copy(0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                            Column(Modifier.weight(1f)) {
                                Text(type?.displayName ?: r.patchType, color = AzlukOnBg,
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                r.desc?.let { Text(it, color = AzlukOnSurface, fontSize = 10.sp, maxLines = 1) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermsTab(perms: List<String>) {
    if (perms.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No permissions declared", color = AzlukOnSurface)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("${perms.size} permissions", color = AzlukOnSurface, fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp))
        perms.forEach { perm ->
            val danger = perm.contains("LOCATION") || perm.contains("CAMERA") ||
                         perm.contains("READ_") || perm.contains("WRITE_") ||
                         perm.contains("CONTACTS") || perm.contains("PHONE") ||
                         perm.contains("SMS") || perm.contains("STORAGE")
            Surface(
                color  = if (danger) AzlukWarning.copy(0.05f) else AzlukSurface,
                shape  = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (danger) AzlukWarning.copy(0.2f) else AzlukSurfaceVar)
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (danger) AzlukWarning else AzlukOnSurface.copy(0.3f)))
                    Spacer(Modifier.width(10.dp))
                    Text(perm.removePrefix("android.permission."), color = if (danger) AzlukWarning else AzlukOnSurface, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ComponentsTab(d: AppDetail) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ComponentGroup("Activities (${d.activities.size})", Icons.Default.Window,     d.activities, AzlukBlue)
        ComponentGroup("Services (${d.services.size})",    Icons.Default.Dns,         d.services,   AzlukSuccess)
        ComponentGroup("Receivers (${d.receivers.size})",  Icons.Default.Notifications, d.receivers, AzlukWarning)
    }
}

@Composable
private fun ComponentGroup(title: String, icon: ImageVector, items: List<String>, color: Color) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(13.dp), tint = color)
            Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        items.forEach { name ->
            Surface(color = AzlukSurface, shape = RoundedCornerShape(9.dp)) {
                Text(name, color = AzlukOnSurface, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = AzlukSurface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = AzlukBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AzlukOnSurface, fontSize = 12.sp)
        Text(value, color = AzlukOnBg, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp))
    }
}

@Composable
private fun InfoChip(text: String, color: Color) {
    Surface(color = color.copy(0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}


private fun fmtSize(b: Long) = when {
    b < 1024          -> "$b B"
    b < 1024 * 1024   -> "%.1f KB".format(b / 1024f)
    b < 1024L*1024*1024 -> "%.1f MB".format(b / (1024f * 1024))
    else              -> "%.2f GB".format(b / (1024f * 1024 * 1024))
}
