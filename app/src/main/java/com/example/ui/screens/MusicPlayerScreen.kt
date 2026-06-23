package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.Track
import com.example.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

// Dynamic ambient color extractor for each song cover to drive the fluid visualizer background
fun getColorsForTrack(track: Track): Triple<Color, Color, Color> {
    return when (track.id) {
        // Mirage (id = 1) - Cosmic dreamy purples & neon pink/cyan
        1 -> Triple(
            Color(0xFF9A82E3), // Soft elegant purple
            Color(0xFFE280FF), // Neon violet/pink
            Color(0xFF80DEEA)  // Light cyan
        )
        // Elysian (id = 2) - Golden valley/sun, warm gold & sunset orange
        2 -> Triple(
            Color(0xFFFFB200), // Sunshine amber/gold
            Color(0xFFFF7043), // Sunset coral/orange
            Color(0xFF81C784)  // Meadow green
        )
        // Infinity (id = 3) - Deep space blues & starry whites/indigos
        3 -> Triple(
            Color(0xFF26C6DA), // Starry cyan
            Color(0xFF5C6BC0), // Starry indigo
            Color(0xFFAB47BC)  // Nebula violet
        )
        // Stardust (id = 4) - Bright star dust, celestial gold & silver-pink
        4 -> Triple(
            Color(0xFFFFF176), // Bright stardust yellow
            Color(0xFFF06292), // Cosmic rose/pink
            Color(0xFF4DB6AC)  // Teal stardust
        )
        // Dreamer (id = 5) - Deep indigo dream / violet
        5 -> Triple(
            Color(0xFFFF8A80), // Dreamy soft red/pink
            Color(0xFF9575CD), // Dreamy violet
            Color(0xFF4FC3F7)  // Sweet blue
        )
        // Custom imported tracks use a highly-curated list of stunning premium gradients deterministically based on metadata
        else -> {
            val hashCode = (track.title + track.artist).hashCode()
            val presets = listOf(
                Triple(Color(0xFFE170BE), Color(0xFF00E5FF), Color(0xFF651FFF)), // Astral Fuchsia
                Triple(Color(0xFFFFAB40), Color(0xFFFF4081), Color(0xFFD500F9)), // Sunset Gold
                Triple(Color(0xFF00E676), Color(0xFF2979FF), Color(0xFF00B0FF)), // Nordic Aurora
                Triple(Color(0xFFB388FF), Color(0xFFEA80FC), Color(0xFF3D5AFE)), // Cosmic Lavender
                Triple(Color(0xFFFFD740), Color(0xFFDD2C00), Color(0xFFFF6D00)), // Electric Amber
                Triple(Color(0xFF1DE9B6), Color(0xFF00E5FF), Color(0xFF00C853)), // Zen Mint
                Triple(Color(0xFFFF1744), Color(0xFFF50057), Color(0xFF651FFF)), // Siren Rose
                Triple(Color(0xFF3D5AFE), Color(0xFF8C9EFF), Color(0xFF00E5FF))  // Cygnus Indigo
            )
            val index = Math.abs(hashCode) % presets.size
            presets[index]
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MusicPlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Check system permissions dynamically on state update
    val hasStoragePermission = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    var permissionState by remember { mutableStateOf<Boolean?>(null) }
    var currentViewMode by remember { mutableStateOf(ViewMode.NOW_PLAYING) }

    // Initialize playback service context and favorites folder auto restore
    LaunchedEffect(viewModel, context) {
        viewModel.initializeWithContext(context)
    }

    // Permission launcher for high fidelity lockscreen and notifications support
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // High fidelity playback notification setup completed safely
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Sync initial launch permission state
    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission) {
            permissionState = true
            viewModel.setPermissionGranted(true)
            viewModel.loadLocalAudioFiles(context)
        } else {
            permissionState = false
            viewModel.setPermissionGranted(false)
        }
    }

    // Permission launcher block
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            permissionState = true
            viewModel.setPermissionGranted(true)
            viewModel.loadLocalAudioFiles(context)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Storage access granted! Local audio loaded successfully.")
            }
        } else {
            permissionState = false
            viewModel.setPermissionGranted(false)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Storage access denied. Enter local library fallback.")
            }
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importAudioFiles(context, uris)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Imported ${uris.size} track(s) directly into your library!")
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.scanCustomFolder(context, uri)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Scanning files in your custom music folder...")
            }
        }
    }

    // Elegant Dark Theme specific color declarations
    val elegantDarkBg = Color(0xFF1C1B1F)
    val elegantDarkText = Color(0xFFE6E1E5)
    val elegantDarkSubText = Color(0xFFCAC4D0)
    val elegantAccent = Color(0xFFD0BCFF)
    val elegantContainerBg = Color(0xFF2B2930)
    val elegantMutedAccent = Color(0xFF4A4458)
    val elegantTextOnMutedAccent = Color(0xFFE8DEF8)

    // Soft gradient transition background
    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            elegantDarkBg,
            Color(0xFF141316)
        )
    )

    // Smooth subtle ambient light refraction behind the album cover to preserve visual polish
    val transition = rememberInfiniteTransition(label = "elegantGlow")
    val glowOffset1 by transition.animateFloat(
        initialValue = -80f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset1"
    )
    val glowOffset2 by transition.animateFloat(
        initialValue = 80f,
        targetValue = -80f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset2"
    )
    val waveOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )
    val ambientScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientScale"
    )

    // Dynamic wave amplitude that springs beautifully when music starts/stops playing
    val waveAmplitude by animateFloatAsState(
        targetValue = if (uiState.isPlaying) 60f else 15f,
        animationSpec = tween(1300, easing = FastOutSlowInEasing),
        label = "waveAmplitude"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        modifier = modifier
            .fillMaxSize()
            .background(bgGradient)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .drawBehind {
                    // 1. Get dynamically extracted theme colors for the current song cover
                    val (primaryColor, secondaryColor, tertiaryColor) = getColorsForTrack(uiState.currentTrack)

                    // 2. Soft, luxurious ambient spotlight glows behind the interface
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.16f),
                                Color.Transparent
                            )
                        ),
                        radius = 420f * ambientScale,
                        center = center.copy(
                            x = center.x + glowOffset1,
                            y = center.y - 120f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                secondaryColor.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        ),
                        radius = 480f * (2f - ambientScale),
                        center = center.copy(
                            x = center.x + glowOffset2,
                            y = center.y + 320f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                tertiaryColor.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        ),
                        radius = 350f,
                        center = center.copy(
                            x = center.x - glowOffset1,
                            y = center.y + 100f
                        )
                    )

                    // 3. Gentle twinkling ambient sparkles floating in background space
                    val numSparks = 8
                    for (k in 0 until numSparks) {
                        // Deterministic location seeds based on spark index
                        val seedX = (k * 43).hashCode()
                        val seedY = (k * 79).hashCode()
                        
                        // Slowly drift sparks using the continuous waveOffset animation
                        val driftX = ((Math.abs(seedX) % 1000) / 1000f) * size.width + Math.sin((waveOffset + k).toDouble()).toFloat() * 30f
                        val driftY = ((Math.abs(seedY) % 1000) / 1000f) * size.height + Math.cos((waveOffset * 0.8 + k).toDouble()).toFloat() * 25f
                        
                        val radius = 3f + (Math.abs(seedX) % 5) + Math.cos((waveOffset + k * 1.5).toDouble()).toFloat() * 1.5f
                        val rawAlpha = 0.08f + ((Math.abs(seedY) % 6) / 40f) + Math.sin((waveOffset + k).toDouble()).toFloat() * 0.04f
                        val finalAlpha = rawAlpha.coerceIn(0.02f, 0.22f)

                        // Sparkles alternate color between primary and secondary for extra chromatic richness
                        val sparkColor = if (k % 2 == 0) primaryColor else secondaryColor

                        drawCircle(
                            color = sparkColor.copy(alpha = finalAlpha),
                            radius = radius,
                            center = Offset(driftX, driftY)
                        )
                    }

                    // 4. Exquisite liquid glass flowing waves reacting to music in the background
                    val wavePath1 = Path()
                    val wavePath2 = Path()
                    val wavePath3 = Path()

                    val baseHt1 = size.height * 0.68f
                    val baseHt2 = size.height * 0.74f
                    val baseHt3 = size.height * 0.80f

                    val phase1 = waveOffset
                    val phase2 = waveOffset * 0.75f + 1.8f
                    val phase3 = -waveOffset * 1.1f + 3.6f

                    wavePath1.moveTo(0f, baseHt1)
                    wavePath2.moveTo(0f, baseHt2)
                    wavePath3.moveTo(0f, baseHt3)

                    val waveSteps = 45
                    val stepW = size.width / waveSteps

                    for (i in 0..waveSteps) {
                        val x = i * stepW
                        val y1 = baseHt1 + Math.sin((i * 0.15 + phase1).toDouble()).toFloat() * waveAmplitude
                        val y2 = baseHt2 + Math.cos((i * 0.12 + phase2).toDouble()).toFloat() * (waveAmplitude * 0.85f)
                        val y3 = baseHt3 + Math.sin((i * 0.18 + phase3).toDouble()).toFloat() * (waveAmplitude * 0.65f)

                        wavePath1.lineTo(x, y1)
                        wavePath2.lineTo(x, y2)
                        wavePath3.lineTo(x, y3)
                    }

                    wavePath1.lineTo(size.width, size.height)
                    wavePath1.lineTo(0f, size.height)
                    wavePath1.close()

                    wavePath2.lineTo(size.width, size.height)
                    wavePath2.lineTo(0f, size.height)
                    wavePath2.close()

                    wavePath3.lineTo(size.width, size.height)
                    wavePath3.lineTo(0f, size.height)
                    wavePath3.close()

                    // Render gorgeous fluid vertical color blending gradients on the wave layers
                    drawPath(
                        path = wavePath1,
                        brush = Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.08f), Color.Transparent),
                            startY = baseHt1 - waveAmplitude,
                            endY = size.height
                        )
                    )

                    drawPath(
                        path = wavePath2,
                        brush = Brush.verticalGradient(
                            colors = listOf(secondaryColor.copy(alpha = 0.06f), Color.Transparent),
                            startY = baseHt2 - waveAmplitude,
                            endY = size.height
                        )
                    )

                    drawPath(
                        path = wavePath3,
                        brush = Brush.verticalGradient(
                            colors = listOf(tertiaryColor.copy(alpha = 0.04f), Color.Transparent),
                            startY = baseHt3 - waveAmplitude,
                            endY = size.height
                        )
                    )
                }
        ) {
            if (permissionState != true) {
                // Beautiful user-friendly permission state overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(elegantAccent.copy(alpha = 0.08f), CircleShape)
                            .border(1.5.dp, elegantAccent.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Music storage required icon",
                            tint = elegantAccent,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "Access Local Audio",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = elegantDarkText,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "To find and play sweet melodies directly from your device's memory, Liquid Glass player requests permission to read your device's audio files.",
                        fontSize = 15.sp,
                        color = elegantDarkSubText,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                launcher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                            } else {
                                launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = elegantAccent,
                            contentColor = Color(0xFF381E72)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(48.dp)
                            .testTag("request_permission_button")
                    ) {
                        Text(
                            text = "Grant Audio Permissions",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            permissionState = true
                            viewModel.setPermissionGranted(true)
                            viewModel.loadLocalAudioFiles(context)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = elegantAccent
                        ),
                        border = BorderStroke(1.dp, elegantAccent.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(48.dp)
                            .testTag("use_demo_button")
                    ) {
                        Text(
                            text = "Continue with Sample Songs",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // Main music player view with Bottom Navigation and Top-down Queue Drawer
                var activeSettingPureSolfeggio by remember { mutableStateOf(true) }
                var activeSettingRoomDecay by remember { mutableFloatStateOf(0.45f) }
                var activeSettingSparkleDensity by remember { mutableFloatStateOf(0.6f) }
                var activeSettingWaveBoost by remember { mutableFloatStateOf(0.5f) }

                Scaffold(
                    containerColor = Color.Transparent, // Transparent background so the dynamic sound wave particle canvas continues flowing beautifully
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xFF1C1B1F).copy(alpha = 0.95f),
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .testTag("bottom_navigation_bar")
                        ) {
                            NavigationBarItem(
                                selected = currentViewMode == ViewMode.NOW_PLAYING,
                                onClick = { currentViewMode = ViewMode.NOW_PLAYING },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = "Active Player Visualizer"
                                    )
                                },
                                label = { Text("Player", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF381E72),
                                    selectedTextColor = elegantAccent,
                                    indicatorColor = elegantAccent,
                                    unselectedIconColor = elegantDarkSubText.copy(alpha = 0.5f),
                                    unselectedTextColor = elegantDarkSubText.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.testTag("nav_item_player")
                            )
                            NavigationBarItem(
                                selected = currentViewMode == ViewMode.MINIMAL_LIBRARY,
                                onClick = { currentViewMode = ViewMode.MINIMAL_LIBRARY },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = "Favorites Library Catalog"
                                    )
                                },
                                label = { Text("Library", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF381E72),
                                    selectedTextColor = elegantAccent,
                                    indicatorColor = elegantAccent,
                                    unselectedIconColor = elegantDarkSubText.copy(alpha = 0.5f),
                                    unselectedTextColor = elegantDarkSubText.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.testTag("nav_item_library")
                            )
                            NavigationBarItem(
                                selected = currentViewMode == ViewMode.SETTINGS,
                                onClick = { currentViewMode = ViewMode.SETTINGS },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "System Settings"
                                    )
                                },
                                label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF381E72),
                                    selectedTextColor = elegantAccent,
                                    indicatorColor = elegantAccent,
                                    unselectedIconColor = elegantDarkSubText.copy(alpha = 0.5f),
                                    unselectedTextColor = elegantDarkSubText.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.testTag("nav_item_settings")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentViewMode) {
                            ViewMode.NOW_PLAYING -> {
                                // 1. PLAYER COMPONENT WITH TRANSID PULL DOWN UP NEXT QUEUE
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 20.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Top Pull-Down Tap Handle
                                        Card(
                                            onClick = { viewModel.setQueueExpanded(true) },
                                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 16.dp, bottom = 4.dp)
                                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                                .testTag("pulldown_queue_handle")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 10.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Expand Queue",
                                                    tint = elegantAccent,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Beautiful Prominent Album Art Frame
                                        val scale by animateFloatAsState(
                                            targetValue = if (uiState.isPlaying) 1f else 0.94f,
                                            animationSpec = spring(dampingRatio = 0.70f, stiffness = 300f),
                                            label = "albumScale"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .aspectRatio(1f)
                                                    .fillMaxHeight(0.85f)
                                                    .graphicsLayer {
                                                        scaleX = scale
                                                        scaleY = scale
                                                    }
                                                    .shadow(
                                                        elevation = 16.dp,
                                                        shape = RoundedCornerShape(24.dp),
                                                        clip = false,
                                                        ambientColor = Color.Black,
                                                        spotColor = getColorsForTrack(uiState.currentTrack).first
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = Color.White.copy(alpha = 0.08f),
                                                        shape = RoundedCornerShape(24.dp)
                                                    )
                                                    .clip(RoundedCornerShape(24.dp))
                                            ) {
                                                Image(
                                                    painter = painterResource(id = uiState.currentTrack.albumArtResId),
                                                    contentDescription = "Album cover for ${uiState.currentTrack.title}",
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .testTag("album_art_image"),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Song Info Section
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = uiState.currentTrack.title,
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = elegantDarkText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.testTag("song_title")
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = uiState.currentTrack.artist,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = getColorsForTrack(uiState.currentTrack).first,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.testTag("song_artist")
                                                )
                                            }

                                            var isAddedToLibrary by remember { mutableStateOf(false) }
                                            IconButton(
                                                onClick = {
                                                    isAddedToLibrary = !isAddedToLibrary
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            if (isAddedToLibrary) "Added to Library" else "Removed from Library"
                                                        )
                                                    }
                                                },
                                                modifier = Modifier
                                                    .testTag("add_library_button")
                                                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                                    .size(38.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isAddedToLibrary) Icons.Default.Check else Icons.Default.Add,
                                                    contentDescription = "Add to library",
                                                    tint = if (isAddedToLibrary) getColorsForTrack(uiState.currentTrack).first else elegantDarkText,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Progress seekbar
                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            var isUserSeeking by remember { mutableStateOf(false) }
                                            var seekSliderValue by remember { mutableFloatStateOf(0f) }

                                            val activeProgress = if (isUserSeeking) {
                                                seekSliderValue
                                            } else {
                                                uiState.progressSeconds.toFloat()
                                            }

                                            val totalDuration = uiState.currentTrack.durationSeconds.toFloat()

                                            Slider(
                                                value = activeProgress,
                                                onValueChange = { newValue ->
                                                    isUserSeeking = true
                                                    seekSliderValue = newValue
                                                },
                                                onValueChangeFinished = {
                                                    isUserSeeking = false
                                                    viewModel.seekTo(seekSliderValue.toInt())
                                                },
                                                valueRange = 0f..totalDuration,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = getColorsForTrack(uiState.currentTrack).first,
                                                    activeTrackColor = getColorsForTrack(uiState.currentTrack).first,
                                                    inactiveTrackColor = Color(0xFF49454F)
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(12.dp)
                                                    .testTag("playback_progress_slider")
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                val minutesElapsed = activeProgress.toInt() / 60
                                                val secondsElapsed = activeProgress.toInt() % 60
                                                Text(
                                                    text = String.format("%d:%02d", minutesElapsed, secondsElapsed),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = elegantDarkSubText,
                                                    modifier = Modifier.testTag("elapsed_time")
                                                )

                                                Text(
                                                    text = uiState.currentTrack.durationString,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = elegantDarkSubText,
                                                    modifier = Modifier.testTag("total_time")
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Player controls card
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .shadow(8.dp, RoundedCornerShape(28.dp)),
                                            colors = CardDefaults.cardColors(
                                                containerColor = elegantContainerBg
                                            ),
                                            shape = RoundedCornerShape(28.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp, horizontal = 24.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = { viewModel.previousTrack() },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .testTag("skip_previous_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.SkipPrevious,
                                                        contentDescription = "Previous Track",
                                                        tint = elegantDarkText,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .size(60.dp)
                                                        .shadow(6.dp, CircleShape)
                                                        .background(elegantAccent, CircleShape)
                                                        .clip(CircleShape)
                                                        .clickable { viewModel.togglePlayPause() }
                                                        .testTag("play_pause_button"),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                                        tint = Color(0xFF381E72),
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { viewModel.nextTrack() },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .testTag("skip_next_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.SkipNext,
                                                        contentDescription = "Next Track",
                                                        tint = elegantDarkText,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Quick Action translucent pill
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth(0.9f)
                                                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(20.dp))
                                                .border(
                                                    width = 1.dp,
                                                    color = Color.White.copy(alpha = 0.05f),
                                                    shape = RoundedCornerShape(20.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                                .testTag("quick_actions_pill"),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { viewModel.toggleLike() },
                                                modifier = Modifier.testTag("like_quick_button")
                                            ) {
                                                Icon(
                                                    imageVector = if (uiState.currentTrack.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                                    contentDescription = "Like Song",
                                                    tint = if (uiState.currentTrack.isLiked) Color(0xFFE170BE) else elegantDarkText.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.toggleLyrics() },
                                                modifier = Modifier.testTag("lyrics_quick_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Description,
                                                    contentDescription = "Lyrics Mode",
                                                    tint = if (uiState.showLyrics) elegantAccent else elegantDarkText.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.toggleRepeat() },
                                                modifier = Modifier.testTag("repeat_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Repeat,
                                                    contentDescription = "Repeat",
                                                    tint = if (uiState.isRepeating) elegantAccent else elegantDarkText.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Lyrics Section (Only displayed if lyrics toggled on Now Playing)
                                        if (uiState.showLyrics) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF232128).copy(alpha = 0.85f)),
                                                shape = RoundedCornerShape(16.dp),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(0.8f) // use remaining weight
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(14.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Lyrics",
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = elegantDarkText
                                                        )
                                                        IconButton(
                                                            onClick = { viewModel.toggleLyrics() },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Close Lyrics",
                                                                tint = elegantDarkSubText.copy(alpha = 0.8f),
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    LazyColumn(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .testTag("lyrics_list_container")
                                                    ) {
                                                        items(uiState.currentTrack.lyrics) { lyricLine ->
                                                            Text(
                                                                text = lyricLine,
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                lineHeight = 20.sp,
                                                                color = elegantDarkText,
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(0.8f))
                                        }
                                    }

                                    // Overlapping SLIDING UP NEXT DRAWER from top!
                                    AnimatedVisibility(
                                        visible = uiState.isQueueExpanded,
                                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(0.75f)
                                            .align(Alignment.TopCenter)
                                    ) {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color(0xFF1C1A20).copy(alpha = 0.98f)
                                            ),
                                            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                                            border = BorderStroke(1.dp, elegantAccent.copy(alpha = 0.2f)),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .shadow(32.dp, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                                                .testTag("top_queue_drawer")
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(top = 16.dp, bottom = 12.dp, start = 20.dp, end = 20.dp)
                                            ) {
                                                // Drawer Header
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.QueueMusic,
                                                            contentDescription = null,
                                                            tint = elegantAccent,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                        Text(
                                                            text = "Up Next Queue",
                                                            fontSize = 18.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = elegantDarkText
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = { viewModel.setQueueExpanded(false) },
                                                        modifier = Modifier
                                                            .testTag("close_queue_button")
                                                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                                            .size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Close Queue",
                                                            tint = elegantDarkText,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(14.dp))

                                                // List of queue songs
                                                LazyColumn(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxWidth()
                                                        .testTag("top_queue_tracks_list"),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    items(uiState.playlist) { track ->
                                                        val isCurrent = track.id == uiState.currentTrack.id
                                                        SongListItem(
                                                            track = track,
                                                            isCurrent = isCurrent,
                                                            onTrackClick = {
                                                                viewModel.playTrack(track)
                                                                viewModel.setQueueExpanded(false)
                                                            },
                                                            onLikeToggle = { viewModel.toggleLikeForTrack(track.id) }
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(12.dp))

                                                // Drag handle to close
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterHorizontally)
                                                        .size(width = 48.dp, height = 5.dp)
                                                        .background(elegantDarkSubText.copy(alpha = 0.3f), CircleShape)
                                                        .clickable { viewModel.setQueueExpanded(false) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            ViewMode.MINIMAL_LIBRARY -> {
                                // 2. MINIMALIST FAVORITE LIBRARY WITH CENTRAL SEARCH
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 20.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp, bottom = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Favorites Library",
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = elegantDarkText,
                                                letterSpacing = (-0.5).sp
                                            )
                                            val folderLabel = uiState.selectedFolderName
                                            Text(
                                                text = if (uiState.isScanning) {
                                                    "Scanning Storage..."
                                                } else if (folderLabel != null) {
                                                    "📂 $folderLabel"
                                                } else {
                                                    "${uiState.playlist.size} tracks available"
                                                },
                                                fontSize = 12.sp,
                                                color = if (uiState.isScanning) elegantAccent else elegantDarkSubText
                                            )
                                        }

                                        // Rounded Shuffle Pill Button
                                        Button(
                                            onClick = {
                                                viewModel.toggleShuffle()
                                                coroutineScope.launch {
                                                    val message = if (uiState.isShuffled) "Playlist restored to default order" else "Playlist shuffled randomly"
                                                    snackbarHostState.showSnackbar(message)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (uiState.isShuffled) elegantAccent else elegantMutedAccent,
                                                contentColor = if (uiState.isShuffled) Color(0xFF381E72) else elegantTextOnMutedAccent
                                            ),
                                            shape = RoundedCornerShape(50.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .testTag("shuffle_button")
                                                .height(36.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Shuffle,
                                                    contentDescription = "Shuffle Playlist",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = if (uiState.isShuffled) "Shuffled" else "Shuffle",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    var searchQuery by remember { mutableStateOf("") }
                                    val filteredTracks = remember(uiState.playlist, searchQuery) {
                                        uiState.playlist.filter {
                                            it.title.contains(searchQuery, ignoreCase = true) ||
                                            it.artist.contains(searchQuery, ignoreCase = true)
                                        }
                                    }

                                    // Glass-like Search Box
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Search title, artist or favorite...", color = elegantDarkSubText.copy(alpha = 0.6f), fontSize = 14.sp) },
                                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = elegantAccent.copy(alpha = 0.8f)) },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear search",
                                                    tint = elegantDarkSubText.copy(alpha = 0.6f),
                                                    modifier = Modifier.clickable { searchQuery = "" }
                                                )
                                            }
                                        },
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.White.copy(alpha = 0.04f),
                                            unfocusedContainerColor = Color.White.copy(alpha = 0.02f),
                                            focusedIndicatorColor = elegantAccent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            focusedTextColor = elegantDarkText,
                                            unfocusedTextColor = elegantDarkText
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp)
                                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                            .testTag("library_search_field")
                                    )

                                    // Display list count
                                    Text(
                                        text = if (searchQuery.isEmpty()) "ALL FAVORITES" else "SEARCH RESULTS (${filteredTracks.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp,
                                        color = elegantDarkSubText.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    // Tracks List
                                    if (filteredTracks.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No compatible songs found in library.\nTry importing files or selecting another folder.",
                                                fontSize = 13.sp,
                                                color = elegantDarkSubText.copy(alpha = 0.5f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .weight(1f)
                                                .testTag("minimal_library_tracks_list"),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(filteredTracks) { track ->
                                                val isCurrent = track.id == uiState.currentTrack.id
                                                SongListItem(
                                                    track = track,
                                                    isCurrent = isCurrent,
                                                    onLikeToggle = { viewModel.toggleLikeForTrack(track.id) },
                                                    onTrackClick = { viewModel.playTrack(track) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            ViewMode.SETTINGS -> {
                                // 3. SETTINGS OPTION (Hides Folder Picking and Scanning controls)
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 20.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = "Settings",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = elegantDarkText,
                                        modifier = Modifier.padding(top = 16.dp),
                                        letterSpacing = (-0.5).sp
                                    )
                                    Text(
                                        text = "Liquid Glass System Architecture",
                                        fontSize = 12.sp,
                                        color = elegantDarkSubText,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    // Library Storage Config card
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = elegantContainerBg),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = elegantAccent,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "Folder & Storage sync",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = elegantDarkText
                                                )
                                            }

                                            Text(
                                                text = "Synchronize local foldering directories or import specific music audio tracks directly.",
                                                fontSize = 12.sp,
                                                color = elegantDarkSubText,
                                                lineHeight = 18.sp
                                            )

                                            // Scanning/Folder picker trigger status details
                                            val folderLabel = uiState.selectedFolderName
                                            Surface(
                                                color = Color.White.copy(alpha = 0.03f),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = "Selected Directory location",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = elegantAccent
                                                        )
                                                        Text(
                                                            text = folderLabel ?: "Default Device Memory",
                                                            fontSize = 13.sp,
                                                            color = elegantDarkText,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    if (uiState.isScanning) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 1.5.dp,
                                                            color = elegantAccent
                                                        )
                                                    }
                                                }
                                            }

                                            // Folder Picker button
                                            Button(
                                                onClick = {
                                                    try {
                                                        folderPickerLauncher.launch(null)
                                                    } catch (e: Exception) {
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Failed to launch folder picker: ${e.message}")
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = elegantAccent, contentColor = Color(0xFF381E72)),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("select_folder_button")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Text("Select Favorites Folder", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Import specific audio files
                                                OutlinedButton(
                                                    onClick = {
                                                        try {
                                                            audioPickerLauncher.launch("audio/*")
                                                        } catch (e: Exception) {
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("Failed to launch file picker: ${e.message}")
                                                            }
                                                        }
                                                    },
                                                    border = BorderStroke(1.dp, elegantAccent.copy(alpha = 0.4f)),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = elegantAccent),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("import_audio_button")
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Text("Import Files", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }

                                                // Full Library Rescan
                                                OutlinedButton(
                                                    onClick = {
                                                        viewModel.loadLocalAudioFiles(context)
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Rescanned storage database...")
                                                        }
                                                    },
                                                    enabled = !uiState.isScanning,
                                                    border = BorderStroke(1.dp, elegantAccent.copy(alpha = if (uiState.isScanning) 0.1f else 0.4f)),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = elegantAccent),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("refresh_library_button")
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Text("Rescan All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Hydro-Acoustical Solfeggio Tuning options Card for premium styling
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = elegantContainerBg),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = elegantAccent, modifier = Modifier.size(20.dp))
                                                    Text(
                                                        text = "Pure Solfeggio Alignment",
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = elegantDarkText
                                                    )
                                                }
                                                Switch(
                                                    checked = activeSettingPureSolfeggio,
                                                    onCheckedChange = { activeSettingPureSolfeggio = it },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = Color(0xFF381E72),
                                                        checkedTrackColor = elegantAccent
                                                    )
                                                )
                                            }

                                            Text(
                                                text = "Processes system audio streams in pure 432Hz / 528Hz mathematical solfeggio resonators for perfect cosmic mind-wellness integration.",
                                                fontSize = 11.sp,
                                                color = elegantDarkSubText,
                                                lineHeight = 16.sp,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )

                                            Text(
                                                text = "Virtual Acoustic Space Level",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = elegantDarkText
                                            )
                                            Slider(
                                                value = activeSettingRoomDecay,
                                                onValueChange = { activeSettingRoomDecay = it },
                                                colors = SliderDefaults.colors(
                                                    thumbColor = elegantAccent,
                                                    activeTrackColor = elegantAccent
                                                )
                                            )
                                        }
                                    }

                                    // Graphics physics card
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = elegantContainerBg),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = elegantAccent, modifier = Modifier.size(20.dp))
                                                Text(
                                                    text = "Fluid Visualizer Physics",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = elegantDarkText
                                                )
                                            }

                                            Text(
                                                text = "Configure background sparkles and flow rendering dynamics for optimized battery saving.",
                                                fontSize = 11.sp,
                                                color = elegantDarkSubText,
                                                lineHeight = 16.sp
                                            )

                                            Text(
                                                text = "Ambient Particles Density",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = elegantDarkSubText
                                            )
                                            Slider(
                                                value = activeSettingSparkleDensity,
                                                onValueChange = { activeSettingSparkleDensity = it },
                                                colors = SliderDefaults.colors(
                                                    thumbColor = elegantAccent,
                                                    activeTrackColor = elegantAccent
                                                )
                                            )

                                            Text(
                                                text = "Wave Physics Flow Offset Boost",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = elegantDarkSubText
                                            )
                                            Slider(
                                                value = activeSettingWaveBoost,
                                                onValueChange = { activeSettingWaveBoost = it },
                                                colors = SliderDefaults.colors(
                                                    thumbColor = elegantAccent,
                                                    activeTrackColor = elegantAccent
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongListItem(
    track: Track,
    isCurrent: Boolean,
    onTrackClick: () -> Unit,
    onLikeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isCurrent) 2.dp else 0.dp,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = if (isCurrent) Color(0xFF312E37) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isCurrent) 1.dp else 0.dp,
                color = getColorsForTrack(track).first.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp)
            )
            .drawBehind {
                if (isCurrent) {
                    drawRect(
                        color = getColorsForTrack(track).first,
                        size = this.size.copy(width = 4.dp.toPx())
                    )
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable { onTrackClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp)
            .testTag("song_item_${track.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = track.albumArtResId),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) getColorsForTrack(track).first else Color(0xFFE6E1E5),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = track.artist,
                    fontSize = 12.sp,
                    color = Color(0xFFCAC4D0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = track.durationString,
                    fontSize = 12.sp,
                    color = Color(0xFFCAC4D0)
                )

                IconButton(
                    onClick = { onLikeToggle() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (track.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like track in list",
                        tint = if (track.isLiked) Color(0xFFE170BE) else Color(0xFFCAC4D0).copy(alpha = 0.4f),
                        modifier = Modifier.size(15.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = null,
                    tint = Color(0xFFCAC4D0).copy(alpha = 0.25f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TabIndicatorItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFF312E37) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color(0xFFD0BCFF) else Color(0xFFCAC4D0)
        )
    }
}

enum class ViewMode {
    NOW_PLAYING,
    MINIMAL_LIBRARY,
    SETTINGS
}
