package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.di.AppContainer
import com.example.ui.components.formatCount
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorLiveStudioScreen(
    stream: LiveStream,
    container: AppContainer,
    onFinishLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val streamingService = container.liveStreamRepository.streamingService
    val streamHealth by streamingService.streamHealthState.collectAsState()
    val viewerPresence by streamingService.viewerPresenceState.collectAsState()
    val activeStreams by container.liveStreamRepository.activeStreams.collectAsState()
    val liveMessages by container.liveStreamRepository.liveMessages.collectAsState()
    val currentUser by container.authRepository.currentUserState.collectAsState()

    val currentStream = activeStreams.firstOrNull { it.streamId == stream.streamId } ?: stream

    // Duration timer state
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = currentStream.startedAt
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - start) / 1000
            delay(1000)
        }
    }

    // Camera & Mic toggles
    var isFrontCam by remember { mutableStateOf(streamingService.isFrontCamera()) }
    var isMuted by remember { mutableStateOf(streamingService.isMicrophoneMuted()) }

    // Moderation sheet state
    var selectedMessageForModeration by remember { mutableStateOf<LiveMessage?>(null) }
    var showEndConfirmDialog by remember { mutableStateOf(false) }
    var isEndingStream by remember { mutableStateOf(false) }
    var liveAnalyticsSummary by remember { mutableStateOf<LiveAnalytics?>(null) }

    // Pulsing animation for Live indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val liveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveAlpha"
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Live Camera Viewport Canvas
            AsyncImage(
                model = currentStream.thumbnailUrl,
                contentDescription = "Broadcasting Viewport",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.85f)
            )

            // Dark subtle gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Top Status Bar: LIVE Badge, Duration, Viewers, Stream Health, End Live
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LIVE indicator + Duration
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = IombgRed.copy(alpha = liveAlpha)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = formatDuration(elapsedSeconds),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Active Viewers (Server-authoritative presence)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.6f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${formatCount(viewerPresence.activeViewerCount)}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // End Stream Button
                    Button(
                        onClick = { showEndConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("end_live_stream_button")
                    ) {
                        Text("End Live", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Stream Health & Telemetry HUD
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(if (streamHealth.health == StreamHealth.EXCELLENT) StatusSuccess else IombgGold, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Health: ${streamHealth.health.name} • ${streamHealth.currentBitrateKbps} kbps • ${streamHealth.currentFps} FPS",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = streamHealth.networkCondition,
                            color = IombgGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Right Vertical Camera / Mic Controls
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = {
                        isFrontCam = streamingService.switchCamera()
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .testTag("studio_switch_camera_button")
                ) {
                    Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera", tint = Color.White)
                }

                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        streamingService.toggleMicrophone(isMuted)
                    },
                    modifier = Modifier
                        .background(if (isMuted) IombgRed else Color.Black.copy(alpha = 0.65f), CircleShape)
                        .testTag("studio_toggle_mic_button")
                ) {
                    Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = "Toggle Mic", tint = Color.White)
                }

                // Chat Slow Mode Quick Toggle
                IconButton(
                    onClick = {
                        val newSlowMode = !currentStream.isSlowModeEnabled
                        container.liveStreamRepository.setSlowMode(currentStream.streamId, newSlowMode, 5)
                    },
                    modifier = Modifier
                        .background(if (currentStream.isSlowModeEnabled) IombgGold else Color.Black.copy(alpha = 0.65f), CircleShape)
                        .testTag("studio_toggle_slow_mode")
                ) {
                    Icon(
                        Icons.Default.HourglassTop,
                        contentDescription = "Slow Mode",
                        tint = if (currentStream.isSlowModeEnabled) Color.Black else Color.White
                    )
                }
            }

            // Bottom Live Chat & Moderation HUD
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(14.dp)
            ) {
                // Stream Title & Live Engagement Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentStream.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "Tap any message to moderate • ${currentStream.category}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = IombgRed, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${formatCount(currentStream.likeCount)}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Chat Messages Window (Reverse LazyColumn)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .padding(8.dp)
                ) {
                    val streamMessages = liveMessages.filter {
                        it.streamId == currentStream.streamId && it.moderationStatus != LiveModerationStatus.BLOCKED
                    }

                    if (streamMessages.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Waiting for viewers to join and chat...", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            reverseLayout = true,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(streamMessages.reversed()) { msg ->
                                if (msg.moderationStatus == LiveModerationStatus.DELETED_BY_CREATOR || msg.moderationStatus == LiveModerationStatus.DELETED_BY_USER) {
                                    Text(
                                        text = "<Message deleted by moderator>",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 11.sp,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else if (msg.isSuperSupport) {
                                    // Super Support Highlight Card
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = IombgGold.copy(alpha = 0.9f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedMessageForModeration = msg }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.VolunteerActivism, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text("${msg.senderName} sent Super Support! 💎", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.Black)
                                                Text(msg.text, fontSize = 11.sp, color = Color.Black)
                                            }
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { selectedMessageForModeration = msg }
                                            .padding(vertical = 2.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "${msg.senderName}: ",
                                            color = if (msg.isCreator) IombgGold else IombgRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = msg.text,
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Creator Chat Bar with Fast Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (currentStream.isChatEnabled) "🟢 Chat is LIVE (Slow Mode: ${if (currentStream.isSlowModeEnabled) "5s" else "Off"})" else "🔴 Chat is Disabled",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedButton(
                        onClick = {
                            val newChatState = !currentStream.isChatEnabled
                            container.liveStreamRepository.setChatEnabled(currentStream.streamId, newChatState)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("toggle_chat_enabled_button")
                    ) {
                        Text(if (currentStream.isChatEnabled) "Disable Chat" else "Enable Chat", fontSize = 10.sp, color = Color.White)
                    }
                }
            }
        }
    }

    // Moderation Bottom Sheet
    selectedMessageForModeration?.let { msg ->
        ModalBottomSheet(
            onDismissRequest = { selectedMessageForModeration = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = IombgRed)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Chat Moderation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Selected User: ${msg.senderName}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("\"${msg.text}\"", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action 1: Delete Message
                ListItem(
                    headlineContent = { Text("Delete This Message", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Removes message from live broadcast for all viewers") },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = IombgRed) },
                    modifier = Modifier.clickable {
                        container.liveStreamRepository.deleteChatMessage(
                            messageId = msg.messageId,
                            streamId = currentStream.streamId,
                            performedByUid = currentUser?.uid ?: "creator",
                            isCreatorAction = true
                        )
                        selectedMessageForModeration = null
                    }
                )

                // Action 2: Temporary Mute (60s)
                ListItem(
                    headlineContent = { Text("Mute User for 60 Seconds", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("User cannot send messages in this stream for 1 minute") },
                    leadingContent = { Icon(Icons.Default.VolumeOff, contentDescription = null, tint = IombgGold) },
                    modifier = Modifier.clickable {
                        container.liveStreamRepository.muteUser(
                            streamId = currentStream.streamId,
                            targetUid = msg.senderUid,
                            durationSeconds = 60,
                            performedByUid = currentUser?.uid ?: "creator"
                        )
                        selectedMessageForModeration = null
                    }
                )

                // Action 3: Block User from Live Chat
                ListItem(
                    headlineContent = { Text("Block User from Stream Chat", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Permanently blocks user from sending messages in this broadcast") },
                    leadingContent = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        container.liveStreamRepository.blockUserFromChat(
                            streamId = currentStream.streamId,
                            targetUid = msg.senderUid,
                            performedByUid = currentUser?.uid ?: "creator"
                        )
                        selectedMessageForModeration = null
                    }
                )
            }
        }
    }

    // Confirmation Dialog for Ending Stream
    if (showEndConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEndConfirmDialog = false },
            title = { Text("End Live Broadcast?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to stop broadcasting? Your replay and live analytics will be automatically processed and saved.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEndConfirmDialog = false
                        isEndingStream = true
                        coroutineScope.launch {
                            val summary = container.liveStreamRepository.endLiveStream(currentStream.streamId)
                            isEndingStream = false
                            liveAnalyticsSummary = summary
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                    modifier = Modifier.testTag("confirm_end_live_button")
                ) {
                    Text("End Broadcast Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirmDialog = false }) {
                    Text("Continue Streaming")
                }
            }
        )
    }

    // Live Stream Summary & Analytics Popup Screen
    liveAnalyticsSummary?.let { summary ->
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(IombgRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SensorsOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Live Broadcast Ended", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black))
                    Text(summary.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)

                    Spacer(modifier = Modifier.height(20.dp))

                    // Analytics Metric Grid
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Broadcast Performance Summary", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                AnalyticsMetricCard(title = "Peak Viewers", value = "${formatCount(summary.peakViewers)}", modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                AnalyticsMetricCard(title = "Avg Viewers", value = "${formatCount(summary.averageViewers)}", modifier = Modifier.weight(1f))
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                AnalyticsMetricCard(title = "Total Views", value = "${formatCount(summary.totalViews)}", modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                AnalyticsMetricCard(title = "Duration", value = formatDuration(summary.streamDurationSeconds), modifier = Modifier.weight(1f))
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                AnalyticsMetricCard(title = "Total Likes", value = "${formatCount(summary.likes)}", modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                AnalyticsMetricCard(title = "Chat Messages", value = "${formatCount(summary.chatMessages)}", modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StatusSuccess.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Replay saved & published to your Creator Studio library.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StatusSuccess)
                        }
                    }
                }

                Button(
                    onClick = onFinishLive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("done_live_summary_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Return to Studio", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun AnalyticsMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}
