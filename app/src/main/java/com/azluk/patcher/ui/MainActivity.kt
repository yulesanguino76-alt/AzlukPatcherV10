package com.azluk.patcher.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.azluk.patcher.ui.screens.*
import com.azluk.patcher.ui.theme.*
import com.azluk.patcher.viewmodel.SystemCheckViewModel

class MainActivity : ComponentActivity() {

    private val permReady = mutableStateOf(false)
    private val sysVm: SystemCheckViewModel by viewModels()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permReady.value = hasPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        permReady.value = hasPermission()
        sysVm.run()
        setContent {
            AzlukTheme {
                val ready by permReady
                if (ready) AzlukApp(sysVm) else PermissionScreen { requestPerm() }
            }
        }
        if (!hasPermission()) requestPerm()
    }

    override fun onResume() { super.onResume(); permReady.value = hasPermission() }

    private fun hasPermission() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun requestPerm() {
        if (hasPermission()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            }.onFailure {
                runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        } else {
            permLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }
}

sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    object Home    : BottomNavItem("home",    Icons.Default.Apps,       "Apps")
    object Patched : BottomNavItem("patched", Icons.Default.FolderOpen, "Files")
    object Packs   : BottomNavItem("packs",   Icons.Default.GridView,   "Packs")
    object Tools   : BottomNavItem("tools",   Icons.Default.Build,      "Tools")
}

@Composable
fun AzlukApp(sysVm: SystemCheckViewModel) {
    val profile by sysVm.profile.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    var splashDone by remember { mutableStateOf(false) }

    if (!splashDone) {
        SplashScreen(profile = profile, onComplete = { splashDone = true })
        return
    }

    val navItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Patched,
        BottomNavItem.Packs,
        BottomNavItem.Tools
    )

    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show bottom nav only on root screens
    val showBottomBar = currentRoute in navItems.map { it.route }

    Scaffold(
        containerColor = AzlukBg,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter   = slideInVertically { it } + fadeIn(),
                exit    = slideOutVertically { it } + fadeOut()
            ) {
                AzlukBottomNav(navItems, currentRoute) { route ->
                    nav.navigate(route) {
                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState    = true
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController      = nav,
            startDestination   = "home",
            modifier           = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp),
            enterTransition    = { slideInHorizontally { it / 4 } + fadeIn(tween(220)) },
            exitTransition     = { slideOutHorizontally { -it / 4 } + fadeOut(tween(180)) },
            popEnterTransition = { slideInHorizontally { -it / 4 } + fadeIn(tween(220)) },
            popExitTransition  = { slideOutHorizontally { it / 4 } + fadeOut(tween(180)) }
        ) {
            composable("home")    { HomeScreen(nav) }
            composable("patched") { PatchedFilesScreen(nav) }
            composable("tools")   { ToolsScreen(sysVm) }
            composable("packs")   { PatchPacksScreen(nav) }
            composable(
                "detail/{pkg}",
                arguments = listOf(navArgument("pkg") { type = NavType.StringType })
            ) { back ->
                val pkg = back.arguments?.getString("pkg") ?: return@composable
                DetailScreen(pkg = pkg, navController = nav)
            }
            composable(
                "patch/{pkg}",
                arguments = listOf(navArgument("pkg") { type = NavType.StringType })
            ) { back ->
                val pkg = back.arguments?.getString("pkg") ?: return@composable
                PatchScreen(pkg = pkg, navController = nav)
            }
            composable(
                "patchlog/{pkg}",
                arguments = listOf(navArgument("pkg") { type = NavType.StringType })
            ) { back ->
                val pkg = back.arguments?.getString("pkg") ?: return@composable
                PatchLogScreen(pkg = pkg, navController = nav)
            }
        }
    }
}

@Composable
fun AzlukBottomNav(
    items:        List<BottomNavItem>,
    currentRoute: String?,
    onNavigate:   (String) -> Unit
) {
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color.Transparent, AzlukBg.copy(0.97f), AzlukBg)
    )

    Box(
        Modifier
            .fillMaxWidth()
            .background(gradientBrush)
            .navigationBarsPadding()
    ) {
        Surface(
            color  = AzlukSurface.copy(0.95f),
            shape  = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val selected = currentRoute == item.route
                    val scale by animateFloatAsState(
                        targetValue   = if (selected) 1.05f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label         = "nav_scale_${item.label}"
                    )
                    val tint by animateColorAsState(
                        targetValue   = if (selected) AzlukBlue else AzlukOnSurface,
                        animationSpec = tween(200),
                        label         = "nav_tint_${item.label}"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .then(Modifier),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick  = { onNavigate(item.route) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (selected) {
                                    Surface(
                                        color  = AzlukBlue.copy(0.15f),
                                        shape  = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(item.icon, null,
                                                tint     = tint,
                                                modifier = Modifier.size(20.dp))
                                        }
                                    }
                                } else {
                                    Icon(item.icon, null,
                                        tint     = tint,
                                        modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    item.label,
                                    color      = tint,
                                    fontSize   = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AzlukBg, AzlukSurface2, AzlukBg)))
            .systemBarsPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color  = AzlukBlue.copy(0.1f),
                shape  = RoundedCornerShape(28.dp),
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.FolderOff, null,
                        tint     = AzlukBlue,
                        modifier = Modifier.size(48.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Storage Access Required",
                style      = MaterialTheme.typography.headlineSmall,
                color      = AzlukOnBg,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("AzlukPatcher needs full storage access to read and patch APK files.",
                color = AzlukOnSurface,
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onRequest,
                shape   = RoundedCornerShape(16.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = AzlukBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Security, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Grant Access", fontWeight = FontWeight.Bold)
            }
        }
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        val msg    = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        ctx.sendBroadcast(Intent("com.azluk.patcher.INSTALL_RESULT_LOCAL").apply {
            setPackage(ctx.packageName)
            putExtra(PackageInstaller.EXTRA_STATUS, status)
            putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, msg)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    ?.let { putExtra(Intent.EXTRA_INTENT, it) }
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    ?.let { putExtra(Intent.EXTRA_INTENT, it) }
            }
        })
    }
}
