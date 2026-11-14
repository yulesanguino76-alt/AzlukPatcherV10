package com.azluk.patcher.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.azluk.patcher.core.PatchType
import com.azluk.patcher.ui.theme.*
import com.azluk.patcher.viewmodel.PatchViewModel
import org.json.JSONArray
import org.json.JSONObject

private data class PatchPack(
    val id:          String,
    val name:        String,
    val subtitle:    String,
    val icon:        String,
    val category:    String,
    val description: String,
    val types:       Set<PatchType>
)

private val ALL_PACKS = listOf(
    PatchPack(
        id          = "lucky_core",
        name        = "Lucky Core",
        subtitle    = "Classic bypass combo",
        icon        = "verified_user",
        category    = "bypass",
        description = "License bypass + IAP bypass + Signature spoof. The classic combo for paid apps.",
        types       = setOf(PatchType.LICENSE_BYPASS, PatchType.IAP_BYPASS, PatchType.SIGNATURE_BYPASS)
    ),
    PatchPack(
        id          = "ads_nuke",
        name        = "Ads Nuke",
        subtitle    = "Kills all ad SDKs",
        icon        = "block",
        category    = "ads",
        description = "Stubs AdMob, Facebook Audience, Unity, AppLovin, IronSource, MoPub, Vungle, InMobi, Chartboost. Also blocks 79 ad domains.",
        types       = setOf(PatchType.REMOVE_ADS, PatchType.BLOCK_AD_DOMAINS, PatchType.DISABLE_ANALYTICS, PatchType.REMOVE_TELEMETRY)
    ),
    PatchPack(
        id          = "ssl_unpin",
        name        = "SSL Unpin",
        subtitle    = "Full TLS bypass",
        icon        = "lock_open",
        category    = "security",
        description = "OkHttp CertificatePinner + TrustManager + HostnameVerifier. Full MITM unlock for traffic analysis.",
        types       = setOf(PatchType.SSL_BYPASS)
    ),
    PatchPack(
        id          = "stealth",
        name        = "Stealth Mode",
        subtitle    = "No detection survives",
        icon        = "shield",
        category    = "security",
        description = "Root + SafetyNet/Integrity + Anti-Frida + Anti-Emulator bypass. Full detection shield.",
        types       = setOf(PatchType.ROOT_BYPASS, PatchType.SAFETYNET_BYPASS, PatchType.FRIDA_BYPASS, PatchType.EMULATOR_BYPASS)
    ),
    PatchPack(
        id          = "dev_unlock",
        name        = "Dev Unlock",
        subtitle    = "Full debug access",
        icon        = "code",
        category    = "dev",
        description = "Force debuggable + Disable FLAG_SECURE + Export all components + Allow backup. Complete dev access.",
        types       = setOf(PatchType.FORCE_DEBUGGABLE, PatchType.DISABLE_FLAG_SECURE, PatchType.EXPORT_ALL_COMPONENTS, PatchType.ALLOW_BACKUP)
    ),
    PatchPack(
        id          = "full_patch",
        name        = "Full Patch",
        subtitle    = "Everything enabled",
        icon        = "auto_fix_high",
        category    = "all",
        description = "Every patch type enabled. License + IAP + Ads + SSL + Root + Integrity + Anti-Debug + Dev tools.",
        types       = PatchType.values().toSet()
    )
)

private fun packIcon(icon: String): ImageVector = when (icon) {
    "verified_user" -> Icons.Default.VerifiedUser
    "lock_open"     -> Icons.Default.LockOpen
    "shield"        -> Icons.Default.Shield
    "block"         -> Icons.Default.Block
    "code"          -> Icons.Default.Code
    "auto_fix_high" -> Icons.Default.AutoFixHigh
    else            -> Icons.Default.Extension
}

private fun packGradient(cat: String) = when (cat) {
    "bypass"   -> listOf(AzlukBlue, AzlukBlueDim)
    "ads"      -> listOf(AzlukWarning, AzlukWarningDim)
    "security" -> listOf(AzlukError, AzlukErrorDim)
    "dev"      -> listOf(AzlukCyan, AzlukCyanDim)
    "all"      -> listOf(AzlukPurple, AzlukPurpleDim)
    else       -> listOf(AzlukOnSurface, AzlukSurfaceVar)
}

private fun packFg(cat: String) = when (cat) {
    "bypass"   -> AzlukBlue
    "ads"      -> AzlukWarning
    "security" -> AzlukError
    "dev"      -> AzlukCyan
    "all"      -> AzlukPurple
    else       -> AzlukOnSurface
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchPacksScreen(navController: NavController) {
    val ctx = LocalContext.current

    Scaffold(
        containerColor = AzlukBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Patch Packs", color = AzlukOnBg, fontWeight = FontWeight.Bold)
                        Text("Preset combinations for common use cases", color = AzlukOnSurface, fontSize = 11.sp)
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
            items(ALL_PACKS, key = { it.id }) { pack ->
                PackCard(pack) { selectedTypes ->
                    // Store selected types in ViewModel then navigate to home to pick app
                    // For now navigate back — user picks app then patch screen auto-selects
                    navController.navigate("home")
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun PackCard(pack: PatchPack, onApply: (Set<PatchType>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val fg = packFg(pack.category)
    val scale by animateFloatAsState(if (expanded) 1.01f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "scale")

    Surface(
        color    = AzlukSurface,
        shape    = RoundedCornerShape(20.dp),
        border   = BorderStroke(1.dp, fg.copy(if (expanded) 0.35f else 0.15f)),
        modifier = Modifier.fillMaxWidth().scale(scale)
    ) {
        Column {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gradient icon box
                Box(
                    Modifier
                        .size(54.dp)
                        .background(
                            Brush.linearGradient(packGradient(pack.category)),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(packIcon(pack.icon), null, tint = Color.White, modifier = Modifier.size(28.dp))
                }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Text(pack.name, color = AzlukOnBg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(pack.subtitle, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    // Type count chip
                    Surface(color = fg.copy(0.1f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "${pack.types.size} patches",
                            color    = fg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint     = AzlukOnSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expanded detail
            if (expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                    HorizontalDivider(color = AzlukSurfaceVar, thickness = 0.5.dp)
                    Spacer(Modifier.height(12.dp))

                    Text(pack.description, color = AzlukOnSurface, fontSize = 12.sp, lineHeight = 18.sp)

                    Spacer(Modifier.height(10.dp))

                    // Type chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement   = Arrangement.spacedBy(6.dp)
                    ) {
                        pack.types.take(8).forEach { t ->
                            Surface(color = fg.copy(0.08f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    t.displayName,
                                    color    = fg,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (pack.types.size > 8) {
                            Surface(color = AzlukSurfaceVar, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "+${pack.types.size - 8} more",
                                    color    = AzlukOnSurface,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick  = { onApply(pack.types) },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = fg)
                    ) {
                        Icon(Icons.Default.FlashOn, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Apply Pack — Go to Apps", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
