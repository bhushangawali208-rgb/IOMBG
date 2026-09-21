package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.CommentItem
import com.example.data.model.CommentReply
import com.example.data.model.EventType
import com.example.data.model.Video
import com.example.di.AppContainer
import com.example.ui.components.IombgVideoPlayerView
import com.example.ui.components.VideoCard
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailScreen(
    video: Video,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenThanksSupport: (Video) -> Unit,
    onOpenBoost: (Video) -> Unit,
    onVideoClick: (Video) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val currentUser by container.authRepository.currentUserState.collectAsState()
    val currentChannel by container.channelRepository.currentChannel.collectAsState()
    val videosFeed by container.videoRepository.videosFeed.collectAsState()
    val commentsMap by container.videoRepository.commentsMap.collectAsState()
    val savedVideoIds by container.videoRepository.savedVideoIds.collectAsState()

    // Current live video state from repository or fallback
    val currentVideo = videosFeed.find { it.videoId == video.videoId } ?: video

    // Check ownership
    val isOwner = remember(currentVideo, currentUser, currentChannel) {
        (currentVideo.ownerUid.isNotBlank() && currentVideo.ownerUid == currentUser?.uid) ||
                (currentChannel != null && currentVideo.channelId == currentChannel?.channelId)
    }

    // Player State
    var isPlaying by remember { mutableStateOf(true) }
    var playbackPosition by remember { mutableFloatStateOf(0.0f) } // 0.0 to 1.0
    var seekTrigger by remember { mutableLongStateOf(0L) }
    var isMuted by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var videoDurationSec by remember { mutableLongStateOf(currentVideo.durationSeconds) }

    // Auto-hide controls timer
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3500)
            showControls = false
        }
    }

    // Engagement State from SocialRepository
    val likedVideoIds by container.socialRepository.likedVideoIds.collectAsState()
    val followedChannels by container.socialRepository.followedChannels.collectAsState()
    val blockedUserIds by container.socialRepository.blockedUserIds.collectAsState()

    val isLiked = likedVideoIds.contains(currentVideo.videoId)
    var isDisliked by remember { mutableStateOf(false) }
    val isSaved = savedVideoIds.contains(currentVideo.videoId)
    val isFollowed = followedChannels.contains(currentVideo.channelId)
    val isSubscribed = isFollowed // alias for backward-compatibility
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    // Dialogs & Sheets
    var showShareDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showEditVideoDialog by remember { mutableStateOf(false) }
    var editingComment by remember { mutableStateOf<CommentItem?>(null) }
    var editCommentText by remember { mutableStateOf("") }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    // Comments State (filtering out blocked users)
    val rawComments = commentsMap[currentVideo.videoId] ?: emptyList()
    val comments = rawComments.filter { !blockedUserIds.contains(it.authorUid) }
    var newCommentText by remember { mutableStateOf("") }
    var replyingToCommentId by remember { mutableStateOf<String?>(null) }
    var replyText by remember { mutableStateOf("") }

    // Track views, watch history progress and recommendation engine telemetry
    LaunchedEffect(currentVideo.videoId) {
        val uid = currentUser?.uid ?: "current_user"
        // Load saved progress for resume playback
        val savedProgressMs = container.watchHistoryRepository.getProgress(uid, "VIDEO", currentVideo.videoId)
        if (savedProgressMs > 2000L && videoDurationSec > 0) {
            val ratio = (savedProgressMs.toFloat() / (videoDurationSec * 1000f)).coerceIn(0f, 0.95f)
            playbackPosition = ratio
        }

        container.videoRepository.observeComments(currentVideo.videoId)
        container.videoRepository.incrementViews(currentVideo.videoId)
        container.recommendationEventRepository.recordEvent(
            userId = uid,
            contentId = currentVideo.videoId,
            contentType = "VIDEO",
            eventType = EventType.VIDEO_OPEN,
            category = currentVideo.category,
            creatorId = currentVideo.channelId,
            tags = currentVideo.tags
        )
        container.recommendationEventRepository.recordEvent(
            userId = uid,
            contentId = currentVideo.videoId,
            contentType = "VIDEO",
            eventType = EventType.PLAY,
            category = currentVideo.category,
            creatorId = currentVideo.channelId,
            tags = currentVideo.tags
        )

        var accumulatedSeconds = 0L
        while (true) {
            delay(10000)
            if (isPlaying) {
                accumulatedSeconds += 10
                val progressMs = (playbackPosition * videoDurationSec * 1000L).toLong()
                container.watchHistoryRepository.recordWatch(
                    userId = uid,
                    contentType = "VIDEO",
                    contentId = currentVideo.videoId,
                    progressMs = if (progressMs > 0) progressMs else accumulatedSeconds * 1000L,
                    durationMs = currentVideo.durationSeconds.toLong() * 1000L,
                    title = currentVideo.title,
                    channelName = currentVideo.channelName,
                    thumbnailUrl = currentVideo.thumbnailUrl,
                    videoUrl = currentVideo.videoUrl
                )
                container.recommendationEventRepository.recordEvent(
                    userId = uid,
                    contentId = currentVideo.videoId,
                    contentType = "VIDEO",
                    eventType = EventType.WATCH_TIME,
                    watchDurationSeconds = accumulatedSeconds,
                    totalDurationSeconds = currentVideo.durationSeconds.toLong(),
                    category = currentVideo.category,
                    creatorId = currentVideo.channelId,
                    tags = currentVideo.tags
                )
                if (accumulatedSeconds >= (currentVideo.durationSeconds * 0.85).toLong()) {
                    container.watchHistoryRepository.recordWatch(
                        userId = uid,
                        contentType = "VIDEO",
                        contentId = currentVideo.videoId,
                        progressMs = currentVideo.durationSeconds.toLong() * 1000L,
                        durationMs = currentVideo.durationSeconds.toLong() * 1000L,
                        title = currentVideo.title,
                        channelName = currentVideo.channelName,
                        thumbnailUrl = currentVideo.thumbnailUrl,
                        videoUrl = currentVideo.videoUrl,
                        forceCloudSync = true
                    )
                    container.recommendationEventRepository.recordEvent(
                        userId = uid,
                        contentId = currentVideo.videoId,
                        contentType = "VIDEO",
                        eventType = EventType.COMPLETION,
                        watchDurationSeconds = accumulatedSeconds,
                        totalDurationSeconds = currentVideo.durationSeconds.toLong(),
                        category = currentVideo.category,
                        creatorId = currentVideo.channelId,
                        tags = currentVideo.tags
                    )
                    break
                }
            }
        }
    }

    DisposableEffect(currentVideo.videoId) {
        onDispose {
            val uid = currentUser?.uid ?: "current_user"
            val progressMs = (playbackPosition * videoDurationSec * 1000L).toLong()
            if (progressMs > 0) {
                container.watchHistoryRepository.recordWatch(
                    userId = uid,
                    contentType = "VIDEO",
                    contentId = currentVideo.videoId,
                    progressMs = progressMs,
                    durationMs = currentVideo.durationSeconds.toLong() * 1000L,
                    title = currentVideo.title,
                    channelName = currentVideo.channelName,
                    thumbnailUrl = currentVideo.thumbnailUrl,
                    videoUrl = currentVideo.videoUrl,
                    forceCloudSync = true
                )
            }
        }
    }

    // Snackbar dismiss
    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            delay(2500)
            snackbarMessage = null
        }
    }

    // ==========================================
    // DIALOGS & OVERLAYS
    // ==========================================

    // Share Dialog
    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("Share Video", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Share \"${currentVideo.title}\" with friends and across platforms:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("https://iombg.tv/watch/${currentVideo.videoId}", fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString("https://iombg.tv/watch/${currentVideo.videoId}"))
                                    snackbarMessage = "Link copied to clipboard!"
                                    showShareDialog = false
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = IombgRed)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Watch ${currentVideo.title} on IOMBG: https://iombg.tv/watch/${currentVideo.videoId}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share via"))
                        container.videoRepository.shareVideo(currentVideo.videoId)
                        showShareDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed)
                ) {
                    Text("Share to Apps")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShareDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Report Dialog
    if (showReportDialog) {
        var reportReason by remember { mutableStateOf("Inappropriate content") }
        val reasons = listOf("Inappropriate content", "Spam or misleading", "Copyright infringement", "Harmful or dangerous acts", "Harassment")

        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Video", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Help keep IOMBG safe. Why are you reporting this video?", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    reasons.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reportReason = reason }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = reportReason == reason,
                                onClick = { reportReason = reason },
                                colors = RadioButtonDefaults.colors(selectedColor = IombgRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(reason, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            container.videoRepository.reportContent(
                                contentId = currentVideo.videoId,
                                contentType = "VIDEO",
                                reason = reportReason,
                                reporterUid = currentUser?.uid ?: "anonymous"
                            )
                            snackbarMessage = "Thank you. Video has been submitted for review."
                            showReportDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed)
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Delete Confirmation Dialog (Owner Only)
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Video?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("Are you sure you want to permanently delete \"${currentVideo.title}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            container.videoRepository.deleteVideo(
                                videoId = currentVideo.videoId,
                                channelId = currentVideo.channelId,
                                currentUid = currentUser?.uid ?: ""
                            )
                            showDeleteConfirmDialog = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_video_button")
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Edit Video Dialog (Owner Only)
    if (showEditVideoDialog) {
        var editTitle by remember { mutableStateOf(currentVideo.title) }
        var editDesc by remember { mutableStateOf(currentVideo.description) }
        var editCategory by remember { mutableStateOf(currentVideo.category) }
        var editVisibility by remember { mutableStateOf(currentVideo.visibility) }
        var editAllowComments by remember { mutableStateOf(currentVideo.allowComments) }

        AlertDialog(
            onDismissRequest = { showEditVideoDialog = false },
            title = { Text("Edit Video Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Allow Comments", fontSize = 12.sp)
                        Switch(
                            checked = editAllowComments,
                            onCheckedChange = { editAllowComments = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IombgRed)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            container.videoRepository.editVideo(
                                videoId = currentVideo.videoId,
                                channelId = currentVideo.channelId,
                                currentUid = currentUser?.uid ?: "",
                                title = editTitle,
                                description = editDesc,
                                category = editCategory,
                                tags = currentVideo.tags,
                                visibility = editVisibility,
                                allowComments = editAllowComments
                            )
                            snackbarMessage = "Video details updated successfully."
                            showEditVideoDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditVideoDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Fullscreen Dialog Viewer
    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                IombgVideoPlayerView(
                    videoUrl = currentVideo.videoUrl,
                    thumbnailUrl = currentVideo.thumbnailUrl,
                    isPlaying = isPlaying,
                    isMuted = isMuted,
                    playbackPosition = playbackPosition,
                    seekTrigger = seekTrigger,
                    onPositionChanged = { playbackPosition = it },
                    onDurationChanged = { videoDurationSec = it },
                    onPlayPauseChange = { isPlaying = it },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Bar Controls: Mute Toggle & Close Fullscreen
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = { isFullscreen = false },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                    }
                }

                // Fullscreen Scrubber & Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(currentVideo.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                        val totalSec = if (videoDurationSec > 0) videoDurationSec else currentVideo.durationSeconds
                        val currentSec = (totalSec * playbackPosition).toLong()
                        Text("${formatDuration(currentSec)} / ${formatDuration(totalSec)}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                    Slider(
                        value = playbackPosition,
                        onValueChange = { playbackPosition = it },
                        onValueChangeFinished = { seekTrigger = System.currentTimeMillis() },
                        colors = SliderDefaults.colors(thumbColor = IombgRed, activeTrackColor = IombgRed)
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentVideo.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("video_detail_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showShareDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    if (isOwner) {
                        IconButton(
                            onClick = { showEditVideoDialog = true },
                            modifier = Modifier.testTag("edit_video_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Video")
                        }
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.testTag("delete_video_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Video", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = { showReportDialog = true }) {
                            Icon(Icons.Default.Flag, contentDescription = "Report")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = {
            snackbarMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                ) {
                    Text(msg)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ==========================================
            // 1. MODERN VIDEO PLAYER COMPONENT
            // ==========================================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showControls = !showControls }
                ) {
                    // Native Android Video & Audio Player
                    IombgVideoPlayerView(
                        videoUrl = currentVideo.videoUrl,
                        thumbnailUrl = currentVideo.thumbnailUrl,
                        isPlaying = isPlaying,
                        isMuted = isMuted,
                        playbackPosition = playbackPosition,
                        seekTrigger = seekTrigger,
                        onPositionChanged = { playbackPosition = it },
                        onDurationChanged = { videoDurationSec = it },
                        onPlayPauseChange = { isPlaying = it },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Dim Overlay when controls are visible
                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        )
                    }

                    // Center Play / Pause / Seek 10s Controls
                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // Rewind 10s
                            IconButton(
                                onClick = {
                                    playbackPosition = (playbackPosition - 0.05f).coerceAtLeast(0f)
                                    seekTrigger = System.currentTimeMillis()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White)
                            }

                            // Play / Pause Toggle
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(IombgRed, CircleShape)
                                    .clickable { isPlaying = !isPlaying }
                                    .testTag("player_play_pause_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            // Forward 10s
                            IconButton(
                                onClick = {
                                    playbackPosition = (playbackPosition + 0.05f).coerceAtMost(1f)
                                    seekTrigger = System.currentTimeMillis()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White)
                            }
                        }
                    }

                    // Top Bar Overlay: Resolution & Mute
                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentVideo.processingStatus == "LOCAL_PREVIEW") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = IombgGold,
                                    modifier = Modifier.testTag("player_local_preview_badge")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("LOCAL PREVIEW • DEVICE FILE", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.6f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Hd, contentDescription = null, tint = IombgRed, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("4K UHD • 60 FPS HDR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Row {
                                IconButton(
                                    onClick = { isMuted = !isMuted },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .testTag("mute_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = if (isMuted) "Unmute" else "Mute",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Bar Overlay: Scrubber, Timestamps & Fullscreen
                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Slider(
                                value = playbackPosition,
                                onValueChange = { playbackPosition = it },
                                onValueChangeFinished = { seekTrigger = System.currentTimeMillis() },
                                colors = SliderDefaults.colors(
                                    thumbColor = IombgRed,
                                    activeTrackColor = IombgRed,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .testTag("video_scrubber_slider")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val totalSec = if (videoDurationSec > 0) videoDurationSec else currentVideo.durationSeconds
                                val currentSec = (totalSec * playbackPosition).toLong()
                                Text(
                                    text = "${formatDuration(currentSec)} / ${formatDuration(totalSec)}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Auto 2160p", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    IconButton(
                                        onClick = { isFullscreen = true },
                                        modifier = Modifier.size(24.dp).testTag("fullscreen_toggle_button")
                                    ) {
                                        Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 2. TITLE & ENGAGEMENT ACTION BAR
            // ==========================================
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = currentVideo.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentVideo.processingStatus == "LOCAL_PREVIEW") {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = IombgGold.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, IombgGold.copy(alpha = 0.6f)),
                                modifier = Modifier.testTag("video_detail_local_preview_chip")
                            ) {
                                Text(
                                    text = "LOCAL PREVIEW",
                                    color = IombgGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        val viewSubtitle = if (currentVideo.processingStatus == "LOCAL_PREVIEW") {
                            "Local Preview • Device File"
                        } else {
                            "${formatCount(currentVideo.viewCount)} views • ${formatTimeAgo(currentVideo.createdAt)}"
                        }

                        Text(
                            text = viewSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = IombgRed.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "#${currentVideo.category}",
                                color = IombgRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Row: Like/Dislike, Thanks Support, Boost, Save, Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Like & Dislike Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isDisliked) isDisliked = false
                                        val nowLiked = container.socialRepository.toggleLikeVideo(currentVideo, currentUser)
                                        container.videoRepository.toggleLikeVideo(currentVideo.videoId, nowLiked)
                                    },
                                    modifier = Modifier.size(36.dp).testTag("video_like_button")
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                        contentDescription = "Like",
                                        tint = if (isLiked) IombgRed else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = formatCount(currentVideo.likeCount + if (isLiked) 1 else 0),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                VerticalDivider(modifier = Modifier.height(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        isDisliked = !isDisliked
                                        if (isDisliked && isLiked) {
                                            val nowLiked = container.socialRepository.toggleLikeVideo(currentVideo, currentUser)
                                            container.videoRepository.toggleLikeVideo(currentVideo.videoId, nowLiked)
                                        }
                                        container.videoRepository.toggleDislikeVideo(currentVideo.videoId, isDisliked)
                                    },
                                    modifier = Modifier.size(36.dp).testTag("video_dislike_button")
                                ) {
                                    Icon(
                                        imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                        contentDescription = "Dislike",
                                        tint = if (isDisliked) IombgRed else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Thanks / Support (70/30 Split)
                        Button(
                            onClick = { onOpenThanksSupport(currentVideo) },
                            colors = ButtonDefaults.buttonColors(containerColor = IombgRed.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("thanks_support_button")
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = "Thanks", tint = IombgRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Thanks", color = IombgRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Boost Button
                        OutlinedButton(
                            onClick = { onOpenBoost(currentVideo) },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("video_boost_button")
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = "Boost", tint = IombgGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Boost", color = IombgGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Save (Watch Later)
                        IconButton(
                            onClick = {
                                val saved = container.videoRepository.toggleSaveVideo(currentVideo.videoId)
                                snackbarMessage = if (saved) "Saved to Watch Later" else "Removed from Watch Later"
                            },
                            modifier = Modifier.size(36.dp).testTag("save_video_button")
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Save",
                                tint = if (isSaved) IombgRed else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Share
                        IconButton(
                            onClick = { showShareDialog = true },
                            modifier = Modifier.size(36.dp).testTag("video_share_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ==========================================
                    // 3. CHANNEL CARD & FOLLOW
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpenChannel(currentVideo.channelId) }
                            ) {
                                AsyncImage(
                                    model = currentVideo.channelAvatarUrl.ifBlank { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200" },
                                    contentDescription = currentVideo.channelName,
                                    modifier = Modifier.size(42.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = currentVideo.channelName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${currentVideo.channelHandle} • 18.5K followers",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!isOwner) {
                                Button(
                                    onClick = {
                                        val nowFollowed = container.socialRepository.toggleFollowChannel(
                                            targetChannelId = currentVideo.channelId,
                                            targetOwnerUid = currentVideo.ownerUid,
                                            targetChannelName = currentVideo.channelName,
                                            follower = currentUser
                                        )
                                        snackbarMessage = if (nowFollowed) "Following ${currentVideo.channelName}!" else "Unfollowed"
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isFollowed) MaterialTheme.colorScheme.surfaceVariant else IombgRed,
                                        contentColor = if (isFollowed) MaterialTheme.colorScheme.onSurface else Color.White
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("subscribe_button")
                                ) {
                                    Text(
                                        text = if (isFollowed) "Following" else "Follow",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = IombgGold.copy(alpha = 0.15f)
                                ) {
                                    Text("Your Channel", color = IombgGold, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Expandable Description Box
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = currentVideo.description,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentVideo.tags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    currentVideo.tags.forEach { tag ->
                                        Text("#$tag", color = IombgRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isDescriptionExpanded) "Show less" else "...more details",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 4. COMMENTS & REPLIES SECTION
            // ==========================================
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Comments (${currentVideo.commentCount.coerceAtLeast(comments.size.toLong())})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (!currentVideo.allowComments) {
                            Text("Comments turned off", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (currentVideo.allowComments) {
                        // Add Comment Input Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = currentUser?.photoUrl?.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" },
                                contentDescription = "Your Avatar",
                                modifier = Modifier.size(34.dp).clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = newCommentText,
                                onValueChange = { newCommentText = it },
                                placeholder = { Text("Add a thoughtful comment...", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f).testTag("add_comment_input"),
                                shape = RoundedCornerShape(20.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (newCommentText.isNotBlank()) {
                                        coroutineScope.launch {
                                            container.videoRepository.addComment(
                                                contentId = currentVideo.videoId,
                                                contentType = "VIDEO",
                                                author = currentUser,
                                                channel = currentChannel,
                                                text = newCommentText
                                            )
                                            newCommentText = ""
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("submit_comment_button")
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Post Comment", tint = IombgRed)
                            }
                        }

                        // Inline Reply Indicator Bar (when user tapped "Reply" on a comment)
                        replyingToCommentId?.let { replyCommentId ->
                            val targetComment = comments.find { it.commentId == replyCommentId }
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Replying to ${targetComment?.authorName ?: "@creator"}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IombgRed)
                                    IconButton(
                                        onClick = { replyingToCommentId = null },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel Reply", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Comments List Items
            items(comments) { comment ->
                CommentRowItem(
                    comment = comment,
                    currentUserUid = currentUser?.uid ?: "",
                    onLike = {
                        container.videoRepository.toggleLikeComment(currentVideo.videoId, comment.commentId, !comment.isLikedByMe)
                    },
                    onReplyClick = {
                        replyingToCommentId = comment.commentId
                    },
                    onSendReply = { replyTxt ->
                        coroutineScope.launch {
                            container.videoRepository.addReply(
                                parentCommentId = comment.commentId,
                                contentId = currentVideo.videoId,
                                author = currentUser,
                                channel = currentChannel,
                                text = replyTxt
                            )
                            replyingToCommentId = null
                        }
                    },
                    onDeleteComment = {
                        coroutineScope.launch {
                            container.videoRepository.deleteComment(
                                commentId = comment.commentId,
                                contentId = currentVideo.videoId,
                                contentType = "VIDEO",
                                currentUid = currentUser?.uid ?: ""
                            )
                        }
                    },
                    onReportComment = {
                        coroutineScope.launch {
                            container.videoRepository.reportContent(
                                contentId = comment.commentId,
                                contentType = "COMMENT",
                                reason = "Inappropriate Comment",
                                reporterUid = currentUser?.uid ?: "anonymous"
                            )
                            snackbarMessage = "Comment reported for moderation."
                        }
                    },
                    isReplying = replyingToCommentId == comment.commentId
                )
            }

            // ==========================================
            // 5. RELATED / UP NEXT VIDEOS
            // ==========================================
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Up Next & Recommended",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            items(videosFeed.filter { it.videoId != currentVideo.videoId }) { relVideo ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    VideoCard(
                        video = relVideo,
                        onClick = { onVideoClick(relVideo) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentRowItem(
    comment: CommentItem,
    currentUserUid: String,
    onLike: () -> Unit,
    onReplyClick: () -> Unit,
    onSendReply: (String) -> Unit,
    onDeleteComment: () -> Unit,
    onReportComment: () -> Unit,
    isReplying: Boolean
) {
    var inlineReplyText by remember { mutableStateOf("") }
    val isOwnComment = comment.authorUid.isNotBlank() && comment.authorUid == currentUserUid

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = comment.authorAvatarUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" },
                contentDescription = comment.authorName,
                modifier = Modifier.size(32.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = comment.authorName,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatTimeAgo(comment.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isOwnComment) {
                        IconButton(
                            onClick = onDeleteComment,
                            modifier = Modifier.size(24.dp).testTag("delete_comment_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        }
                    } else {
                        IconButton(
                            onClick = onReportComment,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Flag, contentDescription = "Report", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = comment.text,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Actions: Like comment, Reply button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onLike() }
                    ) {
                        Icon(
                            imageVector = if (comment.isLikedByMe) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = if (comment.isLikedByMe) IombgRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${comment.likeCount}", fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = "Reply",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = IombgRed,
                        modifier = Modifier.clickable { onReplyClick() }
                    )

                    if (comment.isHeartedByCreator) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            color = IombgRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = IombgRed, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Loved by Creator", color = IombgRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Inline Reply Field
                if (isReplying) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inlineReplyText,
                            onValueChange = { inlineReplyText = it },
                            placeholder = { Text("Write a reply...", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                if (inlineReplyText.isNotBlank()) {
                                    onSendReply(inlineReplyText)
                                    inlineReplyText = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Reply", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Nested Replies List
                if (comment.replies.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp)
                            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        comment.replies.forEach { reply ->
                            Row(verticalAlignment = Alignment.Top) {
                                AsyncImage(
                                    model = reply.authorAvatarUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" },
                                    contentDescription = reply.authorName,
                                    modifier = Modifier.size(24.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(reply.authorName, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(formatTimeAgo(reply.createdAt), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(reply.text, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val hours = diff / (1000 * 60 * 60)
    val days = hours / 24
    return when {
        days > 0 -> "$days days ago"
        hours > 0 -> "$hours hours ago"
        else -> "Just now"
    }
}
