package com.azluk.patcher.ui.screens

import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.azluk.patcher.core.InstallState
import com.azluk.patcher.ui.theme.*
import com.azluk.patcher.utils.StorageUtils
import com.azluk.patcher.viewmodel.PatchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchedFilesScreen(
    navController: NavController,
    vm: PatchViewModel = viewModel()
) {
    val ctx   = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    var files      by remember { mutableStateOf<List<File>>(emptyList()) }
    var activeFile by remember { mutableStateOf<String?>(null) }
    var showLog    by remember { mutableStateOf(false) }
    val installLog = remember { mutableStateListOf<String>() }
    val logState   = rememberLazyListState()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    fun log(line: String) = mainHandler.post { installLog.add(line) }

    fun refresh() {
        files = runCatching { StorageUtils.getPatchedFiles() }.getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val r = runCatching { StorageUtils.getPatchedFiles() }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) { files = r }
        }
    }

    LaunchedEffect(installLog.size) {
        if (installLog.isNotEmpty()) logState.animateScrollToItem(installLog.size - 1)
    }

    DisposableEffect(Unit) {
        val filter   = IntentFilter("com.azluk.patcher.INSTALL_RESULT_LOCAL")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
                val msg    = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        installLog.add("⏳ Waiting for user confirmation…")
                        val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        else @Suppress("DEPRECATION") i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        confirm?.let { ctx.startActivity(it) }
                    }
                    else -> {
                        vm.onInstallResult(status, msg)
                        installLog.add(
                            if (status == PackageInstaller.STATUS_SUCCESS)
                                "✅ Installation complete" else "❌ Install failed: ${msg ?: "code $status"}"
                        )
                        activeFile = null
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else ctx.registerReceiver(receiver, filter)
        onDispose { ctx.unregisterReceiver(receiver) }
    }

    fun isContainer(f: File) = f.extension.lowercase() in setOf("xapk", "apkm", "apks")

    fun doInstallApk(file: File) {
        activeFile = file.absolutePath
        showLog    = true
        installLog.clear()
        log("📦 ${file.name}  (${StorageUtils.formatSize(file.length())})")
        log("Opening PackageInstaller session…")
        try {
            val pi        = ctx.packageManager.packageInstaller
            val params    = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = pi.createSession(params)
            val session   = pi.openSession(sessionId)
            log("Session #$sessionId created")
            FileInputStream(file).use { fis ->
                session.openWrite("base.apk", 0, file.length()).use { os ->
                    fis.copyTo(os, 65536); session.fsync(os)
                }
            }
            log("APK written — committing…")
            val ri = Intent("com.azluk.patcher.INSTALL_RESULT").apply { setPackage(ctx.packageName) }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
            session.commit(PendingIntent.getBroadcast(ctx, sessionId, ri, flags).intentSender)
            session.close()
            log("Waiting for installer…")
        } catch (e: Exception) {
            log("❌ ${e.message}")
            activeFile = null
            vm.onInstallResult(-1, e.message)
        }
    }

    fun doInstallContainer(file: File) {
        activeFile = file.absolutePath
        showLog    = true
        installLog.clear()
        log("📦 ${file.name}  (${StorageUtils.formatSize(file.length())})")
        log("Mode: multi-APK session (base + splits)")

        Thread {
            var session: PackageInstaller.Session? = null
            try {
                val pi     = ctx.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val sid    = pi.createSession(params)
                val s      = pi.openSession(sid).also { session = it }
                log("Session #$sid created")

                var apkCount = 0
                ZipInputStream(BufferedInputStream(FileInputStream(file), 256 * 1024)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (!entry.isDirectory && entry.name.endsWith(".apk", true)) {
                            s.openWrite("split_$apkCount.apk", 0, if (entry.size > 0) entry.size else -1L)
                                .use { os -> zip.copyTo(os, 65536); s.fsync(os) }
                            log("  ✓ ${entry.name}")
                            apkCount++
                        }
                        zip.closeEntry()
                    }
                }

                if (apkCount == 0) throw IllegalStateException("No APK files in container")
                log("$apkCount APK(s) staged — committing…")

                val ri = Intent("com.azluk.patcher.INSTALL_RESULT").apply { setPackage(ctx.packageName) }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                else PendingIntent.FLAG_UPDATE_CURRENT
                s.commit(PendingIntent.getBroadcast(ctx, sid, ri, flags).intentSender)
                s.close(); session = null
                log("Waiting for installer…")
            } catch (e: Exception) {
                try { session?.abandon() } catch (_: Exception) {}
                log("❌ ${e.message}")
                mainHandler.post { activeFile = null; vm.onInstallResult(-1, e.message) }
            }
        }.start()
    }

    fun doInstall(file: File) {
        if (isContainer(file)) doInstallContainer(file) else doInstallApk(file)
    }

    Scaffold(
        containerColor = AzlukBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Patched Files", color = AzlukOnBg, fontWeight = FontWeight.Bold)
                        Text(
                            "${files.size} file${if (files.size != 1) "s" else ""} · ${StorageUtils.formatSize(StorageUtils.patchedDirSizeBytes())}",
                            color    = AzlukOnSurface,
                            fontSize = 11.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = ::refresh) {
                        Icon(Icons.Default.Refresh, null, tint = AzlukBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzlukSurface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Install log panel ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = showLog,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color  = AzlukSurface,
                    border = BorderStroke(1.dp, AzlukSurfaceVar)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (activeFile != null) {
                                    CircularProgressIndicator(Modifier.size(13.dp), color = AzlukBlue, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Terminal, null, tint = AzlukOnSurface, modifier = Modifier.size(14.dp))
                                }
                                Text("Install Log", color = AzlukOnBg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                if (installLog.size > 0) {
                                    Surface(color = AzlukSurfaceVar, shape = CircleShape) {
                                        Text(
                                            "${installLog.size}",
                                            color    = AzlukOnSurface,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { showLog = false }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, null, tint = AzlukOnSurface, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Box(Modifier.heightIn(max = 150.dp)) {
                            LazyColumn(state = logState) {
                                items(installLog) { line ->
                                    Text(
                                        line,
                                        color      = when {
                                            line.startsWith("✅") -> AzlukSuccess
                                            line.startsWith("❌") -> AzlukError
                                            line.startsWith("⏳") -> AzlukWarning
                                            line.startsWith("  ✓") -> AzlukCyan
                                            else                 -> AzlukOnSurface
                                        },
                                        fontSize   = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }

                        // AI suggestion inline
                        val ai = state.aiDiagnosis
                        AnimatedVisibility(ai.loading || ai.suggestion.isNotEmpty() || ai.error.isNotEmpty()) {
                            Column(Modifier.padding(top = 10.dp)) {
                                HorizontalDivider(color = AzlukSurfaceVar, thickness = 0.5.dp)
                                Spacer(Modifier.height(10.dp))
                                when {
                                    ai.loading -> Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(Modifier.size(12.dp), color = AzlukBlue, strokeWidth = 1.5.dp)
                                        Text("Analyzing error…", color = AzlukOnSurface, fontSize = 11.sp)
                                    }
                                    ai.suggestion.isNotEmpty() -> Surface(
                                        color = AzlukBlue.copy(0.07f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Lightbulb, null, tint = AzlukBlue, modifier = Modifier.size(14.dp))
                                            Text(ai.suggestion, color = AzlukOnBg, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                                            TextButton(onClick = { vm.dismissAi() }, contentPadding = PaddingValues(0.dp)) {
                                                Text("OK", fontSize = 11.sp, color = AzlukOnSurface)
                                            }
                                        }
                                    }
                                    ai.error.isNotEmpty() -> Text(ai.error, color = AzlukError, fontSize = 11.sp)
                                }
                            }
                        }

                        // Install result actions
                        when (val ist = state.installState) {
                            is InstallState.Success -> {
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = AzlukSuccess, modifier = Modifier.size(16.dp))
                                    Text("Installed successfully!", color = AzlukSuccess, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                            is InstallState.Failure -> if (ist.canRetry && activeFile != null) {
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick  = { activeFile?.let { doInstall(File(it)) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape    = RoundedCornerShape(12.dp),
                                    colors   = ButtonDefaults.buttonColors(containerColor = AzlukBlue)
                                ) {
                                    Icon(Icons.Default.Refresh, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Retry Install", fontSize = 12.sp)
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            // ── File list ─────────────────────────────────────────────────────
            if (files.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = AzlukSurfaceVar, shape = RoundedCornerShape(24.dp), modifier = Modifier.size(80.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.FolderOff, null, tint = AzlukOnSurface, modifier = Modifier.size(36.dp))
                            }
                        }
                        Text("No patched files", color = AzlukOnBg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Patch an app to see output files here", color = AzlukOnSurface, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(files, key = { it.absolutePath }) { file ->
                        val isInstalling = activeFile == file.absolutePath
                        val guessedPkg   = file.nameWithoutExtension.removeSuffix("_azluk")
                        val isInstalled  = remember(guessedPkg) {
                            runCatching { ctx.packageManager.getPackageInfo(guessedPkg, 0); true }.getOrDefault(false)
                        }

                        FileCard(
                            file         = file,
                            date         = fmt.format(Date(file.lastModified())),
                            isInstalling = isInstalling,
                            isInstalled  = isInstalled,
                            isContainer  = isContainer(file),
                            onInstall    = { doInstall(file) },
                            onReinstall  = {
                                ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$guessedPkg"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                Toast.makeText(ctx, "Uninstall first, then tap Install", Toast.LENGTH_LONG).show()
                            },
                            onDelete     = { file.delete(); refresh() }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FileCard(
    file:         File,
    date:         String,
    isInstalling: Boolean,
    isInstalled:  Boolean,
    isContainer:  Boolean,
    onInstall:    () -> Unit,
    onReinstall:  () -> Unit,
    onDelete:     () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        if (isInstalling) AzlukBlue.copy(0.5f) else AzlukSurfaceVar,
        label = "border"
    )

    Surface(
        color    = AzlukSurface,
        shape    = RoundedCornerShape(18.dp),
        border   = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                // Icon
                Surface(
                    color  = if (isInstalling) AzlukBlue.copy(0.18f) else AzlukSurface2,
                    shape  = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isInstalling) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = AzlukBlue, strokeWidth = 2.5.dp)
                        } else {
                            Icon(
                                if (isContainer) Icons.Default.FolderZip else Icons.Default.Android,
                                null,
                                tint     = if (isInstalled) AzlukSuccess else AzlukBlue,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        file.name,
                        color      = AzlukOnBg,
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${StorageUtils.formatSize(file.length())} · $date",
                            color    = AzlukOnSurface,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        when {
                            isInstalling -> Surface(color = AzlukBlue.copy(0.1f), shape = RoundedCornerShape(5.dp)) {
                                Text("Installing…", color = AzlukBlue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            isContainer -> Surface(color = AzlukWarning.copy(0.1f), shape = RoundedCornerShape(5.dp)) {
                                Text("SPLITS", color = AzlukWarning, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            isInstalled -> Surface(color = AzlukSuccess.copy(0.1f), shape = RoundedCornerShape(5.dp)) {
                                Text("INSTALLED", color = AzlukSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Surface(color = AzlukSurfaceVar, shape = RoundedCornerShape(5.dp)) {
                            Text(file.extension.uppercase(), color = AzlukOnSurface, fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }

                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = AzlukOnSurface, modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    HorizontalDivider(color = AzlukSurfaceVar, thickness = 0.5.dp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick  = { expanded = false; onInstall() },
                            enabled  = !isInstalling,
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = AzlukBlue)
                        ) {
                            Icon(Icons.Default.InstallMobile, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Install", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        if (isInstalled && !isContainer) {
                            OutlinedButton(
                                onClick  = { expanded = false; onReinstall() },
                                enabled  = !isInstalling,
                                modifier = Modifier.weight(1.2f),
                                shape    = RoundedCornerShape(12.dp),
                                border   = BorderStroke(1.dp, AzlukWarning.copy(0.5f))
                            ) {
                                Icon(Icons.Default.SwapHoriz, null, Modifier.size(14.dp), tint = AzlukWarning)
                                Spacer(Modifier.width(4.dp))
                                Text("Reinstall", fontSize = 12.sp, color = AzlukWarning, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        IconButton(
                            onClick  = onDelete,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AzlukError.copy(0.08f))
                        ) {
                            Icon(Icons.Default.DeleteOutline, null, tint = AzlukError, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
