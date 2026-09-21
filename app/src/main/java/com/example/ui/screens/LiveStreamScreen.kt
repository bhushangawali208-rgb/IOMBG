package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.EventType
import com.example.data.model.LiveMessage
import com.example.data.model.LiveModerationStatus
import com.example.data.model.LiveStream
import com.example.di.AppContainer
import com.example.ui.components.formatCount
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveStreamScreen(
    stream: LiveStream,
    container: AppContainer,
    onClose: () -> Unit,
    onOpenThanksSupport: (LiveStream) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeStreams by container.liveStreamRepository.activeStreams.collectAsState()
    val liveMessages by container.liveStreamRepository.liveMessages.collectAsState()
    val currentUser by container.authRepository.currentUserState.collectAsState()
    val followingChannels by container.socialRepository.followedChannels.collectAsState()

    // Get current live state for this stream
    val currentStream = activeStreams.firstOrNull { it.streamId == stream.streamId } ?: stream
    val isFollowing = followingChannels.contains(currentStream.channelId)

    // Chat state
    var chatInputText by remember { mutableStateOf("") }
    var chatErrorMessage by remember { mutableStateOf<String?>(null) }
    var slowModeCountdown by remember { mutableIntStateOf(0) }

    // Floating heart reaction state
    var reactionCount by remember { mutableIntStateOf(0) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("Spam or Misleading") }
    var selectedMessageForReport by remember { mutableStateOf<LiveMessage?>(null) }

    // Replay viewing mode
    var isPlayingReplay by remember { mutableStateOf(false) }

    // Log join event on entry and leave event on exit
    val watchStartTime = remember { System.currentTimeMillis() }
    DisposableEffect(currentStream.streamId) {
        val uid = currentUser?.uid ?: "guest_user"
        container.liveStreamRepository.recordJoin(currentStream.streamId, uid)
        onDispose {
            val watchSec = (System.currentTimeMillis() - watchStartTime) / 1000
            container.liveStreamRepository.recordLeave(currentStream.streamId, uid, watchSec)
        }
    }

    // Real-time Firestore chat listener
    LaunchedEffect(currentStream.streamId) {
        container.liveStreamRepository.observeLiveChat(currentStream.streamId)
    }

    // Slow mode countdown timer
    LaunchedEffect(slowModeCountdown) {
        if (slowModeCountdown > 0) {
            delay(1000)
            slowModeCountdown -= 1
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Live Stream Video Canvas
            AsyncImage(
                model = currentStream.thumbnailUrl,
                contentDescription = currentStream.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dark gradient overlay for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Stream Offline / Awaiting Broadcast Overlay
            val isAwaitingBroadcast = currentStream.isLive && (currentStream.status == "AWAITING_BROADCAST" || currentStream.streamUrl.isBlank())
            if (isAwaitingBroadcast && !isPlayingReplay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E1E1E).copy(alpha = 0.9f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SensorsOff,
                                contentDescription = null,
                                tint = IombgGold,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Stream Offline / Awaiting Broadcast",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "The creator has opened this live room, but media broadcast has not started. Chat is active below.",
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Top Header: Live Badge, Viewer Counter, Quality Pill, Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentStream.isLive && !isPlayingReplay) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isAwaitingBroadcast) IombgGold else IombgRed
                        ) {
                            Text(
                                text = if (isAwaitingBroadcast) "AWAITING BROADCAST" else "LIVE 🔴",
                                color = if (isAwaitingBroadcast) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        if (currentStream.viewerCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.6f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${formatCount(currentStream.viewerCount)}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = IombgGold
                        ) {
                            Text(
                                text = "REPLAY 📼",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = if (isAwaitingBroadcast) "Live Ingest Offline" else "Live Broadcast",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("close_live_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Stream Ended Screen (if live ended and not playing replay)
            if (!currentStream.isLive && !isPlayingReplay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.88f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.SensorsOff, contentDescription = null, tint = IombgRed, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("This Live Broadcast Has Ended", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = Color.White))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "The creator has wrapped up this stream session.",
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        if (currentStream.replayAvailable && !currentStream.replayVideoUrl.isNullOrBlank()) {
                            Button(
                                onClick = { isPlayingReplay = true },
                                colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("watch_live_replay_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Watch Full Replay", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        OutlinedButton(
                            onClick = onClose,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("exit_ended_stream_button")
                        ) {
                            Text("Back to Home", color = Color.White)
                        }
                    }
                }
            }

            // Replay mode when no recording is available
            if (isPlayingReplay && currentStream.replayVideoUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.88f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.VideocamOff, contentDescription = null, tint = IombgGold, modifier = Modifier.size(52.dp))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("No recording available.", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "A recorded video replay is not available for this session.",
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { isPlayingReplay = false },
                            colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Back to Stream Details", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Bottom Live Chat & Stream Controls
            if (currentStream.isLive || isPlayingReplay) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(12.dp)
                ) {
                    // Creator Info & Follow Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            AsyncImage(
                                model = currentStream.channelAvatarUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400" },
                                contentDescription = currentStream.channelName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = currentStream.channelName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentStream.title,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Follow Button
                        Button(
                            onClick = {
                                container.socialRepository.toggleFollowChannel(
                                    targetChannelId = currentStream.channelId,
                                    targetOwnerUid = currentStream.ownerUid,
                                    follower = currentUser
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant else IombgRed
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("live_follow_button")
                        ) {
                            Text(
                                text = if (isFollowing) "Following" else "Follow",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Engagement Action Row: Like, Share, Super Support, Report
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Like Button with reaction counter
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.clickable {
                                reactionCount += 1
                                container.liveStreamRepository.recordLike(currentStream.streamId, currentUser?.uid ?: "viewer")
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = "Like", tint = IombgRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${formatCount(currentStream.likeCount)}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Share Button
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("IOMBG Live Stream", "https://iombg.com/live/${currentStream.streamId}")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Live stream link copied!", Toast.LENGTH_SHORT).show()
                                container.liveStreamRepository.recordShare(currentStream.streamId, currentUser?.uid ?: "viewer")
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Super Support / Thanks Button
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = IombgGold,
                            modifier = Modifier.clickable { onOpenThanksSupport(currentStream) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.VolunteerActivism, contentDescription = "Super Support", tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Support 💎", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Report Button
                        IconButton(
                            onClick = { showReportDialog = true },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Flag, contentDescription = "Report", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Chat Window (Reverse LazyColumn)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .padding(8.dp)
                    ) {
                        val streamMessages = liveMessages.filter {
                            it.streamId == currentStream.streamId && it.moderationStatus != LiveModerationStatus.BLOCKED
                        }

                        if (!currentStream.isChatEnabled) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Live chat has been disabled by the creator.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
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
                                            text = "<Message deleted>",
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 11.sp
                                        )
                                    } else if (msg.isSuperSupport) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = IombgGold.copy(alpha = 0.9f),
                                            modifier = Modifier.fillMaxWidth()
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
                                                .clickable { selectedMessageForReport = msg },
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

                    // Chat Error Notice
                    chatErrorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(err, color = IombgGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Chat Input Field
                    if (currentStream.isChatEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = chatInputText,
                                onValueChange = {
                                    chatInputText = it
                                    chatErrorMessage = null
                                },
                                placeholder = {
                                    Text(
                                        text = if (slowModeCountdown > 0) "Slow mode (${slowModeCountdown}s)..." else "Say something in live chat...",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("live_chat_input"),
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color.Black.copy(alpha = 0.6f),
                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.6f)
                                ),
                                singleLine = true,
                                enabled = slowModeCountdown == 0
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    if (chatInputText.isNotBlank()) {
                                        val result = container.liveStreamRepository.sendChatMessage(
                                            streamId = currentStream.streamId,
                                            senderUid = currentUser?.uid ?: "guest_viewer",
                                            senderName = currentUser?.displayName ?: "Viewer",
                                            senderAvatar = currentUser?.photoUrl ?: "",
                                            text = chatInputText.trim()
                                        )

                                        if (result.isSuccess) {
                                            chatInputText = ""
                                            if (currentStream.isSlowModeEnabled) {
                                                slowModeCountdown = currentStream.slowModeIntervalSeconds
                                            }
                                        } else {
                                            chatErrorMessage = result.exceptionOrNull()?.message ?: "Failed to send message"
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .background(IombgRed, CircleShape)
                                    .testTag("send_live_chat_button")
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Stream Report Dialog
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Live Stream", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Help us keep IOMBG safe. Select why you are reporting this live broadcast:")
                    Spacer(modifier = Modifier.height(10.dp))
                    listOf(
                        "Spam, Scam, or Fraudulent content",
                        "Harassment, Bullying, or Hate Speech",
                        "Violent or Dangerous Content",
                        "Copyright or IP Infringement"
                    ).forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reportReason = reason }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = reportReason == reason, onClick = { reportReason = reason })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(reason, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        container.liveStreamRepository.recordReport(
                            streamId = currentStream.streamId,
                            reporterUid = currentUser?.uid ?: "anonymous",
                            reason = reportReason,
                            details = "User reported stream"
                        )
                        showReportDialog = false
                        Toast.makeText(context, "Thank you. Our moderation team has been notified.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed)
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Message Options Bottom Sheet (for Viewer)
    selectedMessageForReport?.let { msg ->
        ModalBottomSheet(
            onDismissRequest = { selectedMessageForReport = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp)) {
                Text("Chat Message: \"${msg.text}\"", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))

                if (msg.senderUid == currentUser?.uid) {
                    ListItem(
                        headlineContent = { Text("Delete My Message", fontWeight = FontWeight.Bold) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = IombgRed) },
                        modifier = Modifier.clickable {
                            container.liveStreamRepository.deleteChatMessage(msg.messageId, currentStream.streamId, currentUser?.uid ?: "")
                            selectedMessageForReport = null
                        }
                    )
                }

                ListItem(
                    headlineContent = { Text("Report Message", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Report inappropriate message to platform moderators") },
                    leadingContent = { Icon(Icons.Default.Flag, contentDescription = null, tint = IombgGold) },
                    modifier = Modifier.clickable {
                        selectedMessageForReport = null
                        Toast.makeText(context, "Message reported for review.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}
