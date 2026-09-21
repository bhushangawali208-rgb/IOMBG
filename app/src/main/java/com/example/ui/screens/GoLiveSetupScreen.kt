package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.Channel
import com.example.data.model.LiveStream
import com.example.di.AppContainer
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoLiveSetupScreen(
    container: AppContainer,
    onLiveStarted: (LiveStream) -> Unit,
    onCreateChannelClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentChannel by container.channelRepository.currentChannel.collectAsState()
    val currentUser by container.authRepository.currentUserState.collectAsState()

    // Form fields
    var liveTitle by remember { mutableStateOf("") }
    var liveDescription by remember { mutableStateOf("") }
    var liveCategory by remember { mutableStateOf("Gaming") }
    var liveVisibility by remember { mutableStateOf("PUBLIC") } // PUBLIC, UNLISTED, PRIVATE
    var isChatEnabled by remember { mutableStateOf(true) }
    var allowReplay by remember { mutableStateOf(true) }
    var liveThumbnailUrl by remember { mutableStateOf("https://images.unsplash.com/photo-1542751371-adc38448a05e?w=800") }
    var tagInput by remember { mutableStateOf("") }
    var tagList by remember { mutableStateOf(listOf("Gaming", "Live", "Interactive", "4K")) }

    // Hardware & Device Preview State
    var isFrontCamera by remember { mutableStateOf(true) }
    var isMicMuted by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasMicPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var isStartingBroadcast by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Permission Launchers
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasMicPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: hasMicPermission
    }

    val thumbnailPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            liveThumbnailUrl = uri.toString()
        }
    }

    val categories = listOf("Gaming", "Technology", "Entertainment", "Music", "Education", "Food", "Science", "Sports", "Lifestyle")
    val presetThumbnails = listOf(
        "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=800" to "Esports Arena",
        "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800" to "Deep Tech Lab",
        "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800" to "Studio Acoustic",
        "https://images.unsplash.com/photo-1513104890138-7c749659a591?w=800" to "Live Kitchen",
        "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800" to "Cyber AI"
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = IombgRed
                    ) {
                        Text(
                            text = "LIVE SETUP 🔴",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Box(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Channel Guard Check
            if (currentChannel == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IombgRed.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(IombgRed.copy(alpha = 0.15f), Color.Transparent)))
                            .padding(20.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Sensors, contentDescription = null, tint = IombgRed, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Channel Required to Go Live", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "You must create or claim your creator channel before broadcasting live to viewers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onCreateChannelClick,
                                colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("create_channel_live_prompt_button")
                            ) {
                                Text("Create Creator Channel", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Architecture Banner: Development & Sandbox Live Pipeline
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, IombgGold.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = IombgGold, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("LIVE BROADCAST SETUP", fontWeight = FontWeight.Black, fontSize = 11.sp, color = IombgGold)
                        Text(
                            "Live streaming architecture ready for media ingest server. Broadcast will await connection from ingest stream.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Camera & Microphone Live Preview Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("1. Broadcast Camera & Mic Preview", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        // Camera Preview Visualizer
                        AsyncImage(
                            model = liveThumbnailUrl,
                            contentDescription = "Camera View",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Camera & Mic Controls Overlay
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { isFrontCamera = !isFrontCamera },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    .testTag("switch_camera_button")
                            ) {
                                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera", tint = Color.White)
                            }

                            IconButton(
                                onClick = { isMicMuted = !isMicMuted },
                                modifier = Modifier
                                    .background(if (isMicMuted) IombgRed else Color.Black.copy(alpha = 0.65f), CircleShape)
                                    .testTag("toggle_mic_button")
                            ) {
                                Icon(if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = "Toggle Mic", tint = Color.White)
                            }
                        }

                        // Bottom Status Bar
                        Surface(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (isMicMuted) IombgRed else StatusSuccess, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isFrontCamera) "Front Camera (1080p60)" else "Rear Ultra-Wide (4K60)",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isMicMuted) "• Mic Muted" else "• Mic Live",
                                    color = if (isMicMuted) IombgRed else StatusSuccess,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Permission Indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (hasCameraPermission && hasMicPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasCameraPermission && hasMicPermission) StatusSuccess else IombgGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (hasCameraPermission && hasMicPermission) "Camera & Audio Access Granted" else "Permissions Required",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!hasCameraPermission || !hasMicPermission) {
                            OutlinedButton(
                                onClick = {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("grant_permissions_button")
                            ) {
                                Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Stream Details & Metadata
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("2. Stream Title & Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = liveTitle,
                        onValueChange = { if (it.length <= 100) liveTitle = it },
                        label = { Text("Stream Title (Required)") },
                        placeholder = { Text("🔴 24-Hour Speedrun Marathon, Q&A & Viewer Matches") },
                        supportingText = { Text("${liveTitle.length}/100") },
                        modifier = Modifier.fillMaxWidth().testTag("live_title_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = liveDescription,
                        onValueChange = { liveDescription = it },
                        label = { Text("Description & Viewer Instructions") },
                        placeholder = { Text("Tell viewers what's happening, add social links and chat rules...") },
                        modifier = Modifier.fillMaxWidth().height(100.dp).testTag("live_desc_input"),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Stream Category", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = liveCategory == cat,
                                onClick = { liveCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tags
                    Text("Tags & Search Keywords", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = tagInput,
                            onValueChange = { tagInput = it },
                            placeholder = { Text("Add tag (e.g. Esports)", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f).testTag("live_tag_input"),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (tagInput.isNotBlank() && !tagList.contains(tagInput.trim())) {
                                    tagList = tagList + tagInput.trim()
                                    tagInput = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.testTag("add_live_tag_button")
                        ) {
                            Text("Add", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(tagList) { tag ->
                            InputChip(
                                selected = true,
                                onClick = { tagList = tagList.filter { it != tag } },
                                label = { Text("#$tag", fontSize = 11.sp) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Custom Thumbnail Selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("3. Stream Thumbnail", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        IconButton(
                            onClick = {
                                thumbnailPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("upload_live_thumbnail_button")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Pick Image", tint = IombgRed)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presetThumbnails) { (url, label) ->
                            val isSelected = liveThumbnailUrl == url
                            Column(
                                modifier = Modifier
                                    .width(110.dp)
                                    .clickable { liveThumbnailUrl = url },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) IombgRed else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(16.dp)
                                                .background(IombgRed, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(label, fontSize = 10.sp, maxLines = 1, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Live Interaction & Visibility Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("4. Broadcast & Chat Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Visibility selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("PUBLIC" to "Public", "UNLISTED" to "Unlisted", "PRIVATE" to "Private").forEach { (vis, label) ->
                            FilterChip(
                                selected = liveVisibility == vis,
                                onClick = { liveVisibility = vis },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    // Enable Live Chat Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Live Chat", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Allow viewers to send real-time chat messages and Super Support tips", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isChatEnabled,
                            onCheckedChange = { isChatEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IombgRed),
                            modifier = Modifier.testTag("enable_live_chat_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Allow Replay Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Save Broadcast Replay", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Automatically save and publish full replay video in your Creator Studio", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = allowReplay,
                            onCheckedChange = { allowReplay = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IombgRed),
                            modifier = Modifier.testTag("allow_replay_switch")
                        )
                    }
                }
            }

            // Error display
            errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Start Live Broadcast Button
            Button(
                onClick = {
                    if (liveTitle.isBlank()) {
                        liveTitle = "🔴 Live Interactive Broadcast"
                    }
                    val channel = currentChannel ?: Channel(
                        channelId = "ch_demo",
                        channelName = currentUser?.displayName ?: "Creator Studio",
                        handle = "@${currentUser?.displayName?.lowercase()?.replace(" ", "") ?: "creator"}",
                        profileImageUrl = currentUser?.photoUrl ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400",
                        ownerUid = currentUser?.uid ?: "user_default"
                    )

                    isStartingBroadcast = true
                    coroutineScope.launch {
                        try {
                            val stream = container.liveStreamRepository.startLiveStream(
                                channel = channel,
                                title = liveTitle,
                                description = liveDescription,
                                category = liveCategory,
                                thumbnailUrl = liveThumbnailUrl,
                                visibility = liveVisibility,
                                isChatEnabled = isChatEnabled,
                                allowReplay = allowReplay,
                                tags = tagList
                            )
                            isStartingBroadcast = false
                            onLiveStarted(stream)
                        } catch (e: Exception) {
                            isStartingBroadcast = false
                            errorMessage = e.message ?: "Failed to start live broadcast"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("start_live_broadcast_button"),
                colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                shape = RoundedCornerShape(12.dp),
                enabled = !isStartingBroadcast
            ) {
                if (isStartingBroadcast) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Starting Broadcast...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                } else {
                    Icon(Icons.Default.Sensors, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Live Broadcast 🔴", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
