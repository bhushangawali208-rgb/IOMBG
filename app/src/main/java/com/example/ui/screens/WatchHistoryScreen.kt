package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.HistorySyncStatus
import com.example.data.model.Video
import com.example.data.model.WatchHistoryItem
import com.example.di.AppContainer
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchHistoryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currentUser by container.authRepository.currentUserState.collectAsState()
    val historyItems by container.watchHistoryRepository.historyItems.collectAsState()
    val syncStatus by container.watchHistoryRepository.syncStatus.collectAsState()
    val isSyncing by container.watchHistoryRepository.isSyncing.collectAsState()
    val syncErrorMessage by container.watchHistoryRepository.syncErrorMessage.collectAsState()

    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "VIDEO", "SHORT"
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filteredItems = remember(historyItems, selectedFilter) {
        when (selectedFilter) {
            "VIDEO" -> historyItems.filter { it.contentType.equals("VIDEO", ignoreCase = true) }
            "SHORT" -> historyItems.filter { it.contentType.equals("SHORT", ignoreCase = true) }
            else -> historyItems
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Watch History",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // Sync status subtitle indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            when {
                                isSyncing -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.5.dp,
                                        color = IombgRed
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Syncing with cloud...", fontSize = 11.sp, color = DarkTextSecondary)
                                }
                                syncStatus == HistorySyncStatus.SYNCED -> {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = "Synced",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Synced to cloud", fontSize = 11.sp, color = Color(0xFF4CAF50))
                                }
                                syncStatus == HistorySyncStatus.SYNC_FAILED -> {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = "Sync error",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync error (tap retry)", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = "Local history",
                                        tint = DarkTextMuted,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Local cache", fontSize = 11.sp, color = DarkTextMuted)
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("watch_history_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (syncStatus == HistorySyncStatus.SYNC_FAILED) {
                        IconButton(
                            onClick = { container.watchHistoryRepository.retrySync() },
                            modifier = Modifier.testTag("watch_history_retry_sync_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry Sync",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (historyItems.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.testTag("watch_history_clear_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = "Clear History",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Error banner with retry if sync failed
            if (syncStatus == HistorySyncStatus.SYNC_FAILED && syncErrorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = syncErrorMessage ?: "Cloud sync failed.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { container.watchHistoryRepository.retrySync() },
                            modifier = Modifier.testTag("watch_history_error_retry_btn")
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Filter chips: All, Videos, Shorts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${historyItems.size})") },
                    modifier = Modifier.testTag("history_filter_all")
                )
                val videoCount = historyItems.count { it.contentType.equals("VIDEO", ignoreCase = true) }
                FilterChip(
                    selected = selectedFilter == "VIDEO",
                    onClick = { selectedFilter = "VIDEO" },
                    label = { Text("Videos ($videoCount)") },
                    modifier = Modifier.testTag("history_filter_videos")
                )
                val shortCount = historyItems.count { it.contentType.equals("SHORT", ignoreCase = true) }
                FilterChip(
                    selected = selectedFilter == "SHORT",
                    onClick = { selectedFilter = "SHORT" },
                    label = { Text("Shorts ($shortCount)") },
                    modifier = Modifier.testTag("history_filter_shorts")
                )
            }

            // History List or Empty State
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = DarkTextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (historyItems.isEmpty()) "No watch history yet" else "No matching items",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (historyItems.isEmpty())
                                "Videos and Shorts you watch will appear here, saved securely and synced across your devices."
                            else "No items found for the selected category filter.",
                            fontSize = 13.sp,
                            color = DarkTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("watch_history_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredItems, key = { it.historyId }) { item ->
                        WatchHistoryRow(
                            item = item,
                            onClick = {
                                if (item.contentType.equals("SHORT", ignoreCase = true)) {
                                    onShortClick(item.contentId)
                                } else {
                                    // Construct or fetch video model to play
                                    val video = container.videoRepository.videosFeed.value.firstOrNull { it.videoId == item.contentId }
                                        ?: Video(
                                            videoId = item.contentId,
                                            channelId = "",
                                            channelName = item.channelName,
                                            title = item.title,
                                            thumbnailUrl = item.thumbnailUrl,
                                            videoUrl = item.videoUrl,
                                            durationSeconds = (item.durationMs / 1000L).coerceAtLeast(1L)
                                        )
                                    onVideoClick(video)
                                }
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    val uid = currentUser?.uid ?: item.userId
                                    container.watchHistoryRepository.deleteItem(uid, item.historyId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Confirmation dialog for Clear History
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Watch History?") },
            text = {
                Text(
                    "This will permanently clear your watch history locally and from your cloud account. This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        coroutineScope.launch {
                            val uid = currentUser?.uid ?: ""
                            container.watchHistoryRepository.clearHistory(uid)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = IombgRed),
                    modifier = Modifier.testTag("confirm_clear_history_btn")
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WatchHistoryRow(
    item: WatchHistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isShort = item.contentType.equals("SHORT", ignoreCase = true)
    val progressRatio = remember(item.progressMs, item.durationMs) {
        if (item.durationMs > 0) {
            (item.progressMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("watch_history_item_${item.historyId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail container with duration & progress
            Box(
                modifier = Modifier
                    .width(if (isShort) 80.dp else 120.dp)
                    .height(if (isShort) 110.dp else 75.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                if (item.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isShort) Icons.Default.Bolt else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Type badge (Shorts pill)
                if (isShort) {
                    Surface(
                        color = IombgRed,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "SHORT",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // Resume progress bar at bottom of thumbnail
                if (progressRatio > 0f) {
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = IombgRed,
                        trackColor = Color.Black.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title.ifBlank { if (isShort) "Short #${item.contentId.take(6)}" else "Video #${item.contentId.take(6)}" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (item.channelName.isNotBlank()) {
                    Text(
                        text = item.channelName,
                        fontSize = 12.sp,
                        color = DarkTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))

                // Watch time and progress indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val formattedDate = remember(item.watchedAt) {
                        if (item.watchedAt > 0) {
                            val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                            sdf.format(Date(item.watchedAt))
                        } else "Recently"
                    }
                    Text(
                        text = formattedDate,
                        fontSize = 11.sp,
                        color = DarkTextMuted
                    )

                    if (item.completed) {
                        Text(
                            text = " • Completed",
                            fontSize = 11.sp,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium
                        )
                    } else if (progressRatio > 0.05f) {
                        val percent = (progressRatio * 100).toInt()
                        Text(
                            text = " • $percent% watched",
                            fontSize = 11.sp,
                            color = DarkTextSecondary
                        )
                    }
                }
            }

            // Remove button
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("delete_history_item_${item.historyId}")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove from history",
                    tint = DarkTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
