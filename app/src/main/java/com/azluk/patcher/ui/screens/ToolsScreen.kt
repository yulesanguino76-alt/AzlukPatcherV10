package com.azluk.patcher.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azluk.patcher.ui.theme.*
import com.azluk.patcher.utils.StorageUtils
import com.azluk.patcher.viewmodel.PerformanceTier
import com.azluk.patcher.viewmodel.SystemCheckViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private data class ChecksumEntry(val name: String, val sizeMb: Float, val sha256: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(vm: SystemCheckViewModel = viewModel()) {
    val ctx     = LocalContext.current
    val scope   = rememberCoroutineScope()
    val profile by vm.profile.collectAsStateWithLifecycle()

    var patchedFiles   by remember { mutableStateOf<List<File>>(emptyList()) }
    var checksums      by remember { mutableStateOf<List<ChecksumEntry>>(emptyList()) }
    var computing      by remember { mutableStateOf(false) }
    var cleanMsg       by remember { mutableStateOf<String?>(null) }

    fun reload() {
        patchedFiles = runCatching { StorageUtils.getPatchedFiles() }.getOrDefault(emptyList())
        checksums    = emptyList()
        cleanMsg     = null
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = AzlukBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tools", color = AzlukOnBg, fontWeight = FontWeight.Bold)
                        Text("Device · Maintenance · Checksums", color = AzlukOnSurface, fontSize = 11.sp)
                    }
                },
                actions = {
                    IconButton(onClick = ::reload) {
                        Icon(Icons.Default.Refresh, null, tint = AzlukBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzlukSurface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Device card ───────────────────────────────────────────────────
            item {
                val tierColor = when (profile.performanceTier) {
                    PerformanceTier.HIGH   -> AzlukSuccess
                    PerformanceTier.MEDIUM -> AzlukBlue
                    PerformanceTier.LOW    -> AzlukWarning
                }

                ToolCard(
                    title   = "Device",
                    icon    = Icons.Default.PhoneAndroid,
                    tint    = AzlukBlue
                ) {
                    // Perf tier banner
                    Surface(
                        color  = tierColor.copy(0.08f),
                        shape  = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Speed, null, tint = tierColor, modifier = Modifier.size(16.dp))
                                Text(
                                    when (profile.performanceTier) {
                                        PerformanceTier.HIGH   -> "High Performance"
                                        PerformanceTier.MEDIUM -> "Medium Performance"
                                        PerformanceTier.LOW    -> "Low Performance"
                                    },
                                    color      = tierColor,
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                when (profile.performanceTier) {
                                    PerformanceTier.HIGH   -> "Parallel patching"
                                    PerformanceTier.MEDIUM -> "Standard mode"
                                    PerformanceTier.LOW    -> "Sequential mode"
                                },
                                color    = tierColor.copy(0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    DevRow(Icons.Default.Memory,     "RAM",     "${profile.ramFreeMb} / ${profile.ramTotalMb} MB free")
                    DevRow(Icons.Default.Storage,    "Storage", "${"%.1f".format(profile.storageFreeGb)} GB free")
                    DevRow(Icons.Default.DeveloperBoard, "CPU", "${profile.cpuCores} cores · ${profile.arch}")
                    DevRow(Icons.Default.Android,    "API",     "Android ${profile.androidApi}")
                    DevRow(
                        Icons.Default.Lock,
                        "Root",
                        if (profile.hasRoot) "✓ ${profile.rootMethod}" else "Not detected",
                        if (profile.hasRoot) AzlukSuccess else AzlukOnSurface
                    )
                    if (profile.isEmulator) {
                        Spacer(Modifier.height(4.dp))
                        Surface(color = AzlukWarning.copy(0.08f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = AzlukWarning, modifier = Modifier.size(13.dp))
                                Text("Emulator detected", color = AzlukWarning, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ── Warnings ──────────────────────────────────────────────────────
            if (profile.warnings.isNotEmpty()) {
                item {
                    ToolCard(title = "Warnings", icon = Icons.Default.Warning, tint = AzlukWarning) {
                        profile.warnings.forEach { w ->
                            Row(
                                Modifier.padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(5.dp).clip(CircleShape).background(AzlukWarning))
                                Text(w, color = AzlukOnSurface, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ── Storage stats ─────────────────────────────────────────────────
            item {
                ToolCard(title = "Output Stats", icon = Icons.Default.FolderSpecial, tint = AzlukCyan) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatBlock("Files",  "${patchedFiles.size}",             AzlukBlue)
                        StatBlock("Total",  StorageUtils.formatSize(StorageUtils.patchedDirSizeBytes()), AzlukCyan)
                        StatBlock("Folder", "AzlukPatcher/", AzlukOnSurface)
                    }
                }
            }

            // ── Maintenance ───────────────────────────────────────────────────
            item {
                ToolCard(title = "Maintenance", icon = Icons.Default.CleaningServices, tint = AzlukWarning) {
                    OutlinedButton(
                        onClick  = {
                            scope.launch {
                                val n = withContext(Dispatchers.IO) { StorageUtils.clearEngineArtifacts(ctx.cacheDir) }
                                cleanMsg = if (n > 0) "Cleared $n artifact(s)" else "Nothing to clear"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        border   = BorderStroke(1.dp, AzlukWarning.copy(0.4f))
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(15.dp), tint = AzlukWarning)
                        Spacer(Modifier.width(6.dp))
                        Text("Clear scan / temp cache", color = AzlukWarning, fontSize = 12.sp)
                    }

                    cleanMsg?.let { msg ->
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = AzlukSuccess, modifier = Modifier.size(14.dp))
                            Text(msg, color = AzlukSuccess, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── SHA-256 checksums ─────────────────────────────────────────────
            item {
                ToolCard(title = "SHA-256 Checksums  (${patchedFiles.size})", icon = Icons.Default.Fingerprint, tint = AzlukBlue) {
                    if (patchedFiles.isEmpty()) {
                        Text("No patched files yet", color = AzlukOnSurface, fontSize = 12.sp)
                    } else {
                        OutlinedButton(
                            onClick  = {
                                if (!computing) {
                                    computing = true
                                    scope.launch {
                                        checksums = withContext(Dispatchers.IO) {
                                            patchedFiles.map { f ->
                                                ChecksumEntry(f.name, f.length() / 1_048_576f, sha256Of(f))
                                            }
                                        }
                                        computing = false
                                    }
                                }
                            },
                            enabled  = !computing,
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(12.dp),
                            border   = BorderStroke(1.dp, AzlukBlue.copy(0.4f))
                        ) {
                            if (computing) {
                                CircularProgressIndicator(Modifier.size(14.dp), color = AzlukBlue, strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Computing…", color = AzlukBlue, fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Security, null, Modifier.size(15.dp), tint = AzlukBlue)
                                Spacer(Modifier.width(6.dp))
                                Text("Compute SHA-256", color = AzlukBlue, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            items(checksums, key = { it.name }) { entry ->
                Surface(
                    color    = AzlukSurface,
                    shape    = RoundedCornerShape(14.dp),
                    border   = BorderStroke(1.dp, AzlukSurfaceVar),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                entry.name,
                                color      = AzlukOnBg,
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                modifier   = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Text("%.1f MB".format(entry.sizeMb), color = AzlukOnSurface, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AzlukSurface2)
                                .clickable {
                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("SHA-256", entry.sha256))
                                    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                entry.sha256,
                                color      = AzlukCyan,
                                fontSize   = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier   = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.ContentCopy, null, tint = AzlukOnSurface, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun ToolCard(
    title:   String,
    icon:    ImageVector,
    tint:    Color = AzlukBlue,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color    = AzlukSurface,
        shape    = RoundedCornerShape(18.dp),
        border   = BorderStroke(1.dp, AzlukSurfaceVar),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.padding(bottom = 12.dp)
            ) {
                Surface(color = tint.copy(0.1f), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
                    }
                }
                Text(title, color = AzlukOnBg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun DevRow(icon: ImageVector, label: String, value: String, valueColor: Color = AzlukOnBg) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AzlukOnSurface, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = AzlukOnSurface, fontSize = 12.sp, modifier = Modifier.width(60.dp))
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatBlock(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = AzlukOnSurface, fontSize = 10.sp)
    }
}

private fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered(64 * 1024).use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) { val r = input.read(buf); if (r <= 0) break; digest.update(buf, 0, r) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
