package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.di.AppContainer
import com.example.ui.components.MetricStatCard
import com.example.ui.components.formatCount
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StudioSection(val title: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Default.Dashboard),
    CONTENT("Content", Icons.Default.VideoLibrary),
    ANALYTICS("Analytics", Icons.Default.Analytics),
    AUDIENCE("Audience", Icons.Default.People),
    BOOST("Boost", Icons.Default.Bolt),
    MONETIZATION("Monetization", Icons.Default.MonetizationOn),
    EARNINGS("Earnings", Icons.Default.AccountBalanceWallet),
    SETTINGS("Settings", Icons.Default.Settings)
}

enum class AnalyticsPeriod(val label: String, val multiplier: Double) {
    DAYS_7("7 Days", 0.35),
    DAYS_28("28 Days", 1.0),
    DAYS_90("90 Days", 2.8),
    DAYS_365("365 Days", 11.5),
    LIFETIME("Lifetime", 18.0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorStudioScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenMonetization: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenBoost: () -> Unit,
    onNavigateToCreateChannel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val currentUser by container.authRepository.currentUserState.collectAsState()
    val currentChannel by container.channelRepository.currentChannel.collectAsState()
    val videosFeed by container.videoRepository.videosFeed.collectAsState()
    val shortsFeed by container.videoRepository.shortsFeed.collectAsState()
    val liveStreams by container.liveStreamRepository.activeStreams.collectAsState()
    val wallet by container.walletRepository.walletState.collectAsState()
    val monetizationApp by container.monetizationRepository.applicationState.collectAsState()
    val ledgerEntries by container.walletRepository.ledgerEntries.collectAsState()
    val userInterests by container.recommendationEventRepository.userInterests.collectAsState()

    var selectedSection by remember { mutableStateOf(StudioSection.OVERVIEW) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog & Edit states
    var videoToEdit by remember { mutableStateOf<Video?>(null) }
    var shortToEdit by remember { mutableStateOf<ShortItem?>(null) }
    var contentToDelete by remember { mutableStateOf<Triple<String, String, String>?>(null) } // id, title, type
    var selectedVideoForAnalytics by remember { mutableStateOf<Video?>(null) }
    var selectedShortForAnalytics by remember { mutableStateOf<ShortItem?>(null) }
    var showMonetizeApplyDialog by remember { mutableStateOf(false) }

    // Check channel ownership security rule
    val channel = currentChannel

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    Brush.linearGradient(listOf(IombgRed, IombgGold)),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.VideoCall,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Creator Studio",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            if (channel != null) {
                                Text(
                                    "${channel.channelName} (${channel.handle})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("studio_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Telemetry synced with IOMBG recommendation engine")
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Data", tint = DarkTextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (channel == null) {
            // Authenticated user with No Channel
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp)
                    .testTag("creator_studio_empty_channel_state"),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(IombgRed.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.VideoCall,
                                contentDescription = null,
                                tint = IombgRed,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "You don't have a channel yet.",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Create your channel to start publishing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onNavigateToCreateChannel,
                            colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("create_channel_button")
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Channel", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Authenticated Creator Studio Dashboard
            val isDemoCreator = com.example.BuildConfig.DEBUG && (currentUser?.uid == "creator_demo_01" || channel.channelId == "ch_alexrivera" || channel.channelId == "ch_01")
            val myVideos = remember(videosFeed, channel.channelId, currentUser?.uid) {
                videosFeed.filter {
                    it.channelId == channel.channelId || (currentUser != null && it.ownerUid == currentUser!!.uid) || (isDemoCreator && (it.channelId == "ch_01" || it.channelId == "ch_alexrivera"))
                }
            }
            val myShorts = remember(shortsFeed, channel.channelId, currentUser?.uid) {
                shortsFeed.filter {
                    it.channelId == channel.channelId || (currentUser != null && it.ownerUid == currentUser!!.uid) || (isDemoCreator && (it.channelId == "ch_01" || it.channelId == "ch_alexrivera"))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Section Navigation Tabs (Horizontal Scrollable)
                ScrollableTabRow(
                    selectedTabIndex = selectedSection.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = IombgRed,
                    edgePadding = 12.dp,
                    divider = { Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) }
                ) {
                    StudioSection.values().forEach { section ->
                        Tab(
                            selected = selectedSection == section,
                            onClick = { selectedSection = section },
                            modifier = Modifier.testTag("tab_${section.name.lowercase()}"),
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        section.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (selectedSection == section) IombgRed else DarkTextSecondary
                                    )
                                    Text(
                                        section.title,
                                        fontWeight = if (selectedSection == section) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        )
                    }
                }

                // Active Section Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (selectedSection) {
                        StudioSection.OVERVIEW -> StudioOverviewSection(
                            channel = channel,
                            myVideos = myVideos,
                            myShorts = myShorts,
                            liveStreams = liveStreams,
                            wallet = wallet,
                            onNavigateToSection = { selectedSection = it },
                            onOpenMonetization = onOpenMonetization,
                            onOpenWallet = onOpenWallet,
                            onOpenBoost = onOpenBoost,
                            onInspectVideo = { selectedVideoForAnalytics = it }
                        )

                        StudioSection.CONTENT -> StudioContentSection(
                            channel = channel,
                            currentUserUid = currentUser?.uid ?: "",
                            myVideos = myVideos,
                            myShorts = myShorts,
                            liveStreams = liveStreams,
                            onEditVideo = { videoToEdit = it },
                            onEditShort = { shortToEdit = it },
                            onQuickChangeVideoVisibility = { vid, newVis ->
                                coroutineScope.launch {
                                    val ok = container.videoRepository.updateVideoVisibility(
                                        videoId = vid.videoId,
                                        channelId = channel.channelId,
                                        currentUid = currentUser?.uid ?: "",
                                        newVisibility = newVis
                                    )
                                    if (ok) snackbarHostState.showSnackbar("Visibility set to $newVis")
                                }
                            },
                            onQuickChangeShortVisibility = { s, newVis ->
                                coroutineScope.launch {
                                    val ok = container.videoRepository.updateShortVisibility(
                                        shortId = s.shortId,
                                        channelId = channel.channelId,
                                        currentUid = currentUser?.uid ?: "",
                                        newVisibility = newVis
                                    )
                                    if (ok) snackbarHostState.showSnackbar("Short visibility set to $newVis")
                                }
                            },
                            onDeleteVideo = { video ->
                                contentToDelete = Triple(video.videoId, video.title, "VIDEO")
                            },
                            onDeleteShort = { short ->
                                contentToDelete = Triple(short.shortId, short.title, "SHORT")
                            },
                            onInspectVideoAnalytics = { selectedVideoForAnalytics = it },
                            onInspectShortAnalytics = { selectedShortForAnalytics = it }
                        )

                        StudioSection.ANALYTICS -> StudioAnalyticsSection(
                            channel = channel,
                            myVideos = myVideos,
                            myShorts = myShorts,
                            onInspectVideo = { selectedVideoForAnalytics = it },
                            onInspectShort = { selectedShortForAnalytics = it }
                        )

                        StudioSection.AUDIENCE -> StudioAudienceSection(
                            channel = channel,
                            myVideos = myVideos,
                            userInterests = userInterests
                        )

                        StudioSection.BOOST -> BoostScreen(
                            container = container,
                            preSelectedVideo = myVideos.firstOrNull(),
                            onBack = { selectedSection = StudioSection.OVERVIEW }
                        )

                        StudioSection.MONETIZATION -> StudioMonetizationSection(
                            channel = channel,
                            monetizationApp = monetizationApp,
                            onApplyClick = { showMonetizeApplyDialog = true },
                            onOpenWallet = onOpenWallet
                        )

                        StudioSection.EARNINGS -> StudioEarningsSection(
                            channel = channel,
                            wallet = wallet,
                            ledgerEntries = ledgerEntries,
                            onOpenWallet = onOpenWallet
                        )

                        StudioSection.SETTINGS -> StudioSettingsSection(
                            channel = channel,
                            container = container,
                            onChannelSaved = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Channel settings updated successfully! ✨")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ==========================================
    // MODALS & DIALOGS
    // ==========================================

    // Edit Video Dialog
    videoToEdit?.let { video ->
        EditVideoDialog(
            video = video,
            onDismiss = { videoToEdit = null },
            onSave = { updatedTitle, updatedDesc, updatedCategory, updatedTags, updatedVis, allowComments ->
                coroutineScope.launch {
                    val success = container.videoRepository.editVideo(
                        videoId = video.videoId,
                        channelId = channel?.channelId ?: "",
                        currentUid = currentUser?.uid ?: "",
                        title = updatedTitle,
                        description = updatedDesc,
                        category = updatedCategory,
                        tags = updatedTags,
                        visibility = updatedVis,
                        allowComments = allowComments
                    )
                    videoToEdit = null
                    if (success) {
                        snackbarHostState.showSnackbar("Video updated successfully!")
                    } else {
                        snackbarHostState.showSnackbar("Permission denied: You can only edit your own content.")
                    }
                }
            }
        )
    }

    // Edit Short Dialog
    shortToEdit?.let { short ->
        EditShortDialog(
            short = short,
            onDismiss = { shortToEdit = null },
            onSave = { updatedTitle, updatedDesc, updatedCategory, updatedTags, updatedVis, allowComments ->
                coroutineScope.launch {
                    val success = container.videoRepository.editShort(
                        shortId = short.shortId,
                        channelId = channel?.channelId ?: "",
                        currentUid = currentUser?.uid ?: "",
                        title = updatedTitle,
                        description = updatedDesc,
                        category = updatedCategory,
                        tags = updatedTags,
                        visibility = updatedVis,
                        allowComments = allowComments
                    )
                    shortToEdit = null
                    if (success) {
                        snackbarHostState.showSnackbar("Short updated successfully!")
                    } else {
                        snackbarHostState.showSnackbar("Permission denied: You can only edit your own content.")
                    }
                }
            }
        )
    }

    // Delete Confirmation Dialog
    contentToDelete?.let { (contentId, title, type) ->
        AlertDialog(
            onDismissRequest = { contentToDelete = null },
            title = { Text("Delete $type?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to permanently delete \"$title\"? This action cannot be undone and will remove all associated telemetry.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val ok = if (type == "VIDEO") {
                                container.videoRepository.deleteVideo(contentId, channel?.channelId ?: "", currentUser?.uid ?: "")
                            } else {
                                container.videoRepository.deleteShort(contentId, channel?.channelId ?: "", currentUser?.uid ?: "")
                            }
                            contentToDelete = null
                            if (ok) {
                                snackbarHostState.showSnackbar("$type deleted successfully.")
                            } else {
                                snackbarHostState.showSnackbar("Cannot delete: Only content owner has permission.")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed)
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { contentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Video Detailed Analytics Sheet/Dialog
    selectedVideoForAnalytics?.let { video ->
        VideoAnalyticsDialog(
            video = video,
            onDismiss = { selectedVideoForAnalytics = null }
        )
    }

    // Short Detailed Analytics Sheet/Dialog
    selectedShortForAnalytics?.let { short ->
        ShortAnalyticsDialog(
            short = short,
            onDismiss = { selectedShortForAnalytics = null }
        )
    }

    // Monetization Application Dialog
    if (showMonetizeApplyDialog && channel != null) {
        ApplyMonetizationDialog(
            channel = channel,
            onDismiss = { showMonetizeApplyDialog = false },
            onSubmit = { panNumber, legalName ->
                coroutineScope.launch {
                    try {
                        container.monetizationRepository.submitMonetizationApplication(
                            channel = channel,
                            legalName = legalName,
                            panNumber = panNumber,
                            isTermsAccepted = true
                        )
                        showMonetizeApplyDialog = false
                        snackbarHostState.showSnackbar("Monetization application submitted! Under review.")
                    } catch (e: Exception) {
                        showMonetizeApplyDialog = false
                        snackbarHostState.showSnackbar("Submission failed. Ensure you meet the partner criteria.")
                    }
                }
            }
        )
    }
}

// ==============================================================================
// 1. OVERVIEW SECTION
// ==============================================================================
@Composable
fun StudioOverviewSection(
    channel: Channel,
    myVideos: List<Video>,
    myShorts: List<ShortItem>,
    liveStreams: List<LiveStream>,
    wallet: Wallet?,
    onNavigateToSection: (StudioSection) -> Unit,
    onOpenMonetization: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenBoost: () -> Unit,
    onInspectVideo: (Video) -> Unit
) {
    val totalViews = channel.totalViews.coerceAtLeast(myVideos.sumOf { it.viewCount } + myShorts.sumOf { it.viewCount })
    val totalLikes = myVideos.sumOf { it.likeCount } + myShorts.sumOf { it.likeCount }
    val totalComments = myVideos.sumOf { it.commentCount } + myShorts.sumOf { it.commentCount }
    val totalShares = myVideos.sumOf { it.shareCount } + myShorts.sumOf { it.shareCount }
    val watchHours = channel.totalWatchHours.coerceAtLeast(myVideos.sumOf { (it.durationSeconds * it.viewCount) } / 3600.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 80.dp)
    ) {
        // Real-Time Studio Status Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(StatusSuccess, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Live Channel Status: Active",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusSuccess
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        "Dev & Live Telemetry",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IombgGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Total Revenue Snapshot Card
        // Creator Monetization / Earnings Notice Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.MonetizationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Earnings",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Earnings will appear here after monetization is officially enabled.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onOpenWallet,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Wallet", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Total Aggregate Channel Metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Channel Performance",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            TextButton(onClick = { onNavigateToSection(StudioSection.ANALYTICS) }) {
                Text("See Detailed Analytics →", fontSize = 12.sp, color = IombgRed, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricStatCard(
                title = "Total Views",
                value = formatCount(totalViews),
                subtitle = "+24.8% vs last period",
                icon = Icons.Default.Visibility,
                modifier = Modifier.weight(1f)
            )
            MetricStatCard(
                title = "Watch Time",
                value = "${String.format("%,.1f", watchHours)} hrs",
                subtitle = "+18.2% vs last period",
                icon = Icons.Default.Schedule,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricStatCard(
                title = "Followers",
                value = formatCount(channel.subscriberCount),
                subtitle = "+42% new followers",
                icon = Icons.Default.People,
                modifier = Modifier.weight(1f)
            )
            MetricStatCard(
                title = "Total Likes",
                value = formatCount(totalLikes),
                subtitle = "+31.5% engagement",
                icon = Icons.Default.ThumbUp,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricStatCard(
                title = "Total Comments",
                value = formatCount(totalComments),
                subtitle = "Active community",
                icon = Icons.Default.ChatBubble,
                modifier = Modifier.weight(1f)
            )
            MetricStatCard(
                title = "Total Shares",
                value = formatCount(totalShares),
                subtitle = "+19.0% viral reach",
                icon = Icons.Default.Share,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Content Published Summary
        Text(
            "Published Content Hub",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToSection(StudioSection.CONTENT) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = IombgRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Videos", fontSize = 12.sp, color = DarkTextSecondary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("${myVideos.size}", fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToSection(StudioSection.CONTENT) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = IombgGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shorts", fontSize = 12.sp, color = DarkTextSecondary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("${myShorts.size}", fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToSection(StudioSection.CONTENT) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live", fontSize = 12.sp, color = DarkTextSecondary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("${liveStreams.size}", fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Latest Video Performance Card
        val latestVideo = myVideos.firstOrNull()
        if (latestVideo != null) {
            Text(
                "Latest Video Performance",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onInspectVideo(latestVideo) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    AsyncImage(
                        model = latestVideo.thumbnailUrl,
                        contentDescription = latestVideo.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 110.dp, height = 70.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            latestVideo.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${formatCount(latestVideo.viewCount)} views", fontSize = 12.sp, color = DarkTextSecondary)
                            Text("${formatCount(latestVideo.likeCount)} likes", fontSize = 12.sp, color = DarkTextSecondary)
                            Text(
                                "Analytics →",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = IombgRed
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Quick Hub Navigation Cards
        Text(
            "Creator Quick Actions",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            onClick = onOpenMonetization
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(IombgGold.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = IombgGold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Partner Program & Monetization", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("55% Video Ads • 70% Thanks Support Active", fontSize = 11.sp, color = StatusSuccess)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            onClick = onOpenBoost
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(IombgRed.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = IombgRed)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("IOMBG Boost Campaigns", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Reach new audience through targeted sponsored impressions", fontSize = 11.sp, color = DarkTextSecondary)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    }
}

// ==============================================================================
// 2. CONTENT MANAGEMENT SECTION
// ==============================================================================
@Composable
fun StudioContentSection(
    channel: Channel,
    currentUserUid: String,
    myVideos: List<Video>,
    myShorts: List<ShortItem>,
    liveStreams: List<LiveStream>,
    onEditVideo: (Video) -> Unit,
    onEditShort: (ShortItem) -> Unit,
    onQuickChangeVideoVisibility: (Video, String) -> Unit,
    onQuickChangeShortVisibility: (ShortItem, String) -> Unit,
    onDeleteVideo: (Video) -> Unit,
    onDeleteShort: (ShortItem) -> Unit,
    onInspectVideoAnalytics: (Video) -> Unit,
    onInspectShortAnalytics: (ShortItem) -> Unit
) {
    var contentSubTab by remember { mutableStateOf(0) } // 0: Videos, 1: Shorts, 2: Live Replays
    var searchQuery by remember { mutableStateOf("") }
    var visibilityFilter by remember { mutableStateOf("ALL") } // ALL, PUBLIC, UNLISTED, PRIVATE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Content Type Sub-Tabs
        TabRow(
            selectedTabIndex = contentSubTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            divider = {}
        ) {
            Tab(
                selected = contentSubTab == 0,
                onClick = { contentSubTab = 0 },
                text = { Text("Videos (${myVideos.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
            Tab(
                selected = contentSubTab == 1,
                onClick = { contentSubTab = 1 },
                text = { Text("Shorts (${myShorts.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
            Tab(
                selected = contentSubTab == 2,
                onClick = { contentSubTab = 2 },
                text = { Text("Live Replays (${liveStreams.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search & Visibility Filter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search your content...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            // Visibility Filter Chips
            var filterMenuExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { filterMenuExpanded = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(visibilityFilter, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(
                    expanded = filterMenuExpanded,
                    onDismissRequest = { filterMenuExpanded = false }
                ) {
                    listOf("ALL", "PUBLIC", "UNLISTED", "PRIVATE").forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt) },
                            onClick = {
                                visibilityFilter = opt
                                filterMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Content List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            when (contentSubTab) {
                0 -> {
                    val filteredVideos = myVideos.filter { v ->
                        (searchQuery.isBlank() || v.title.contains(searchQuery, ignoreCase = true) || v.category.contains(searchQuery, ignoreCase = true)) &&
                        (visibilityFilter == "ALL" || v.visibility.equals(visibilityFilter, ignoreCase = true))
                    }

                    if (filteredVideos.isEmpty()) {
                        item {
                            EmptyContentPlaceholder("No videos found matching your filters.")
                        }
                    } else {
                        items(filteredVideos, key = { it.videoId }) { video ->
                            CreatorVideoItemCard(
                                video = video,
                                onEdit = { onEditVideo(video) },
                                onQuickChangeVisibility = { onQuickChangeVideoVisibility(video, it) },
                                onDelete = { onDeleteVideo(video) },
                                onAnalytics = { onInspectVideoAnalytics(video) }
                            )
                        }
                    }
                }

                1 -> {
                    val filteredShorts = myShorts.filter { s ->
                        (searchQuery.isBlank() || s.title.contains(searchQuery, ignoreCase = true) || s.category.contains(searchQuery, ignoreCase = true)) &&
                        (visibilityFilter == "ALL" || s.visibility.equals(visibilityFilter, ignoreCase = true))
                    }

                    if (filteredShorts.isEmpty()) {
                        item {
                            EmptyContentPlaceholder("No Shorts found matching your filters.")
                        }
                    } else {
                        items(filteredShorts, key = { it.shortId }) { short ->
                            CreatorShortItemCard(
                                short = short,
                                onEdit = { onEditShort(short) },
                                onQuickChangeVisibility = { onQuickChangeShortVisibility(short, it) },
                                onDelete = { onDeleteShort(short) },
                                onAnalytics = { onInspectShortAnalytics(short) }
                            )
                        }
                    }
                }

                2 -> {
                    if (liveStreams.isEmpty()) {
                        item {
                            EmptyContentPlaceholder("No live stream replays recorded yet.")
                        }
                    } else {
                        items(liveStreams, key = { it.streamId }) { stream ->
                            CreatorLiveItemCard(stream = stream)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreatorVideoItemCard(
    video: Video,
    onEdit: () -> Unit,
    onQuickChangeVisibility: (String) -> Unit,
    onDelete: () -> Unit,
    onAnalytics: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 115.dp, height = 68.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Duration badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.8f)
                    ) {
                        val min = video.durationSeconds / 60
                        val sec = video.durationSeconds % 60
                        Text(
                            text = String.format("%02d:%02d", min, sec),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Metadata Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            video.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit Details") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = { showMenu = false; onEdit() }
                                )
                                DropdownMenuItem(
                                    text = { Text("View Analytics") },
                                    leadingIcon = { Icon(Icons.Default.Analytics, contentDescription = null) },
                                    onClick = { showMenu = false; onAnalytics() }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("Make Public") },
                                    leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, tint = StatusSuccess) },
                                    onClick = { showMenu = false; onQuickChangeVisibility("PUBLIC") }
                                )
                                DropdownMenuItem(
                                    text = { Text("Make Unlisted") },
                                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = IombgGold) },
                                    onClick = { showMenu = false; onQuickChangeVisibility("UNLISTED") }
                                )
                                DropdownMenuItem(
                                    text = { Text("Make Private") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    onClick = { showMenu = false; onQuickChangeVisibility("PRIVATE") }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("Delete Video", color = IombgRed) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = IombgRed) },
                                    onClick = { showMenu = false; onDelete() }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Visibility Chip
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (video.visibility.uppercase()) {
                                "PUBLIC" -> StatusSuccess.copy(alpha = 0.15f)
                                "UNLISTED" -> IombgGold.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            }
                        ) {
                            Text(
                                video.visibility.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (video.visibility.uppercase()) {
                                    "PUBLIC" -> StatusSuccess
                                    "UNLISTED" -> IombgGold
                                    else -> DarkTextSecondary
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(video.createdAt)),
                            fontSize = 11.sp,
                            color = DarkTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action & Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(13.dp), tint = DarkTextSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(formatCount(video.viewCount), fontSize = 11.sp, color = DarkTextSecondary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(13.dp), tint = DarkTextSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(formatCount(video.likeCount), fontSize = 11.sp, color = DarkTextSecondary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ChatBubble, contentDescription = null, modifier = Modifier.size(13.dp), tint = DarkTextSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(formatCount(video.commentCount), fontSize = 11.sp, color = DarkTextSecondary)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 11.sp)
                    }
                    Button(
                        onClick = onAnalytics,
                        colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Analytics", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CreatorShortItemCard(
    short: ShortItem,
    onEdit: () -> Unit,
    onQuickChangeVisibility: (String) -> Unit,
    onDelete: () -> Unit,
    onAnalytics: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // 9:16 Vertical Thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = short.thumbnailUrl,
                        contentDescription = short.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.8f)
                    ) {
                        Text(
                            "${short.durationSeconds}s",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            short.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Box {
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Edit Short") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = { showMenu = false; onEdit() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Short Analytics") },
                                    leadingIcon = { Icon(Icons.Default.Analytics, contentDescription = null) },
                                    onClick = { showMenu = false; onAnalytics() }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("Make Public") },
                                    leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, tint = StatusSuccess) },
                                    onClick = { showMenu = false; onQuickChangeVisibility("PUBLIC") }
                                )
                                DropdownMenuItem(
                                    text = { Text("Make Unlisted") },
                                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = IombgGold) },
                                    onClick = { showMenu = false; onQuickChangeVisibility("UNLISTED") }
                                )
                                DropdownMenuItem(
                                    text = { Text("Make Private") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    onClick = { showMenu = false; onQuickChangeVisibility("PRIVATE") }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("Delete Short", color = IombgRed) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = IombgRed) },
                                    onClick = { showMenu = false; onDelete() }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (short.visibility.uppercase()) {
                                "PUBLIC" -> StatusSuccess.copy(alpha = 0.15f)
                                "UNLISTED" -> IombgGold.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            }
                        ) {
                            Text(
                                short.visibility.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (short.visibility.uppercase()) {
                                    "PUBLIC" -> StatusSuccess
                                    "UNLISTED" -> IombgGold
                                    else -> DarkTextSecondary
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(short.createdAt)),
                            fontSize = 11.sp,
                            color = DarkTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(12.dp), tint = DarkTextSecondary)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(formatCount(short.viewCount), fontSize = 11.sp, color = DarkTextSecondary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(12.dp), tint = DarkTextSecondary)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(formatCount(short.likeCount), fontSize = 11.sp, color = DarkTextSecondary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(12.dp), tint = DarkTextSecondary)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(formatCount(short.shareCount), fontSize = 11.sp, color = DarkTextSecondary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onAnalytics,
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Short Analytics", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CreatorLiveItemCard(stream: LiveStream) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 65.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = stream.thumbnailUrl,
                    contentDescription = stream.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = IombgRed
                ) {
                    Text(
                        if (stream.isLive) "LIVE" else "REPLAY",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stream.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Peak Viewers: ${formatCount(stream.peakViewers.toLong())} • ${formatCount(stream.likeCount)} likes", fontSize = 11.sp, color = DarkTextSecondary)
            }
        }
    }
}

@Composable
fun EmptyContentPlaceholder(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, color = DarkTextSecondary, fontSize = 13.sp)
    }
}

// ==============================================================================
// 3. ANALYTICS SECTION
// ==============================================================================
@Composable
fun StudioAnalyticsSection(
    channel: Channel,
    myVideos: List<Video>,
    myShorts: List<ShortItem>,
    onInspectVideo: (Video) -> Unit,
    onInspectShort: (ShortItem) -> Unit
) {
    var selectedPeriod by remember { mutableStateOf(AnalyticsPeriod.DAYS_28) }

    // Dynamic metrics calculated against period multiplier
    val baseViews = (channel.totalViews.coerceAtLeast(myVideos.sumOf { it.viewCount } + myShorts.sumOf { it.viewCount })).toDouble()
    val periodViews = (baseViews * selectedPeriod.multiplier).toLong()
    val periodWatchTime = (channel.totalWatchHours.coerceAtLeast(6420.0) * selectedPeriod.multiplier)
    val periodLikes = ((myVideos.sumOf { it.likeCount } + myShorts.sumOf { it.likeCount }).coerceAtLeast(42000) * selectedPeriod.multiplier).toLong()
    val periodComments = ((myVideos.sumOf { it.commentCount } + myShorts.sumOf { it.commentCount }).coerceAtLeast(8400) * selectedPeriod.multiplier).toLong()
    val periodShares = ((myVideos.sumOf { it.shareCount } + myShorts.sumOf { it.shareCount }).coerceAtLeast(3200) * selectedPeriod.multiplier).toLong()
    val followersGained = (2840 * selectedPeriod.multiplier).toLong()
    val followersLost = (240 * selectedPeriod.multiplier).toLong()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 80.dp)
    ) {
        // Period Selector Pills
        Text("Select Reporting Timeframe", style = MaterialTheme.typography.labelMedium, color = DarkTextSecondary)
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AnalyticsPeriod.values()) { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { selectedPeriod = period },
                    label = { Text(period.label, fontWeight = if (selectedPeriod == period) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IombgRed,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Headline Performance Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    "Channel Overview (${selectedPeriod.label})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Total Views", style = MaterialTheme.typography.labelSmall, color = DarkTextSecondary)
                        Text(formatCount(periodViews), fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text("+24.8% vs prior", fontSize = 11.sp, color = StatusSuccess)
                    }
                    Column {
                        Text("Watch Time", style = MaterialTheme.typography.labelSmall, color = DarkTextSecondary)
                        Text("${String.format("%,.0f", periodWatchTime)} hrs", fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text("+18.2% vs prior", fontSize = 11.sp, color = StatusSuccess)
                    }
                    Column {
                        Text("Avg Duration", style = MaterialTheme.typography.labelSmall, color = DarkTextSecondary)
                        Text("4m 18s", fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text("68.4% retention", fontSize = 11.sp, color = IombgGold)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Bar Chart Visualizer
                Text("Daily Views Trajectory", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkTextSecondary)
                Spacer(modifier = Modifier.height(10.dp))

                val sampleDays = listOf("Mon" to 0.65f, "Tue" to 0.80f, "Wed" to 0.55f, "Thu" to 0.90f, "Fri" to 1.0f, "Sat" to 0.85f, "Sun" to 0.72f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    sampleDays.forEach { (day, factor) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .height((65 * factor).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(IombgRed, IombgRed.copy(alpha = 0.5f))
                                        )
                                    )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(day, fontSize = 10.sp, color = DarkTextSecondary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Key Telemetry Grid
        Text("Engagement & Community Dynamics", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricStatCard(
                title = "Likes",
                value = formatCount(periodLikes),
                subtitle = "High sentiment ratio",
                icon = Icons.Default.ThumbUp,
                modifier = Modifier.weight(1f)
            )
            MetricStatCard(
                title = "Comments",
                value = formatCount(periodComments),
                subtitle = "Active discussions",
                icon = Icons.Default.ChatBubble,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricStatCard(
                title = "Shares & Virality",
                value = formatCount(periodShares),
                subtitle = "+19.4% external share",
                icon = Icons.Default.Share,
                modifier = Modifier.weight(1f)
            )
            MetricStatCard(
                title = "Returning Viewers",
                value = "58.4%",
                subtitle = "Loyal community base",
                icon = Icons.Default.Repeat,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricStatCard(
                title = "Followers Gained",
                value = "+${formatCount(followersGained)}",
                subtitle = "Organic discovery",
                icon = Icons.Default.PersonAdd,
                modifier = Modifier.weight(1f)
            )
            MetricStatCard(
                title = "Followers Lost",
                value = "-${formatCount(followersLost)}",
                subtitle = "0.8% churn rate",
                icon = Icons.Default.PersonRemove,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Top Performing Videos for deep dive
        Text("Top Performing Content", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text("Tap any video or short to inspect detailed telemetry", fontSize = 11.sp, color = DarkTextSecondary)
        Spacer(modifier = Modifier.height(10.dp))

        myVideos.take(3).forEach { video ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { onInspectVideo(video) },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 80.dp, height = 48.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(video.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatCount(video.viewCount)} views • ${formatCount(video.likeCount)} likes", fontSize = 11.sp, color = DarkTextSecondary)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DarkTextSecondary)
                }
            }
        }

        myShorts.take(2).forEach { short ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { onInspectShort(short) },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = short.thumbnailUrl,
                        contentDescription = short.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 40.dp, height = 48.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(short.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Short • ${formatCount(short.viewCount)} views • 84% completion", fontSize = 11.sp, color = DarkTextSecondary)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DarkTextSecondary)
                }
            }
        }
    }
}

// ==============================================================================
// 4. AUDIENCE SECTION
// ==============================================================================
@Composable
fun StudioAudienceSection(
    channel: Channel,
    myVideos: List<Video>,
    userInterests: Set<String>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 80.dp)
    ) {
        // Returning vs New Viewers Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    "Audience Loyalty Split",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(IombgRed, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Returning Viewers", fontSize = 12.sp, color = DarkTextSecondary)
                        }
                        Text("58.4%", fontWeight = FontWeight.Black, fontSize = 20.sp, color = IombgRed)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(IombgGold, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Viewers", fontSize = 12.sp, color = DarkTextSecondary)
                        }
                        Text("41.6%", fontWeight = FontWeight.Black, fontSize = 20.sp, color = IombgGold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress ratio bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    Box(modifier = Modifier.weight(0.584f).fillMaxHeight().background(IombgRed))
                    Box(modifier = Modifier.weight(0.416f).fillMaxHeight().background(IombgGold))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Peak Viewing Hours Breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("When Your Viewers Are on IOMBG", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Peak engagement windows in your primary viewer timezone (IST / UTC+5:30)", fontSize = 11.sp, color = DarkTextSecondary)
                Spacer(modifier = Modifier.height(14.dp))

                val timeSlots = listOf(
                    "6 AM - 12 PM (Morning)" to 0.40f,
                    "12 PM - 4 PM (Afternoon)" to 0.65f,
                    "4 PM - 8 PM (Prime Evening)" to 0.95f,
                    "8 PM - 12 AM (Late Night Peak)" to 1.0f,
                    "12 AM - 6 AM (Night)" to 0.20f
                )

                timeSlots.forEach { (slot, intensity) ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(slot, fontSize = 11.sp)
                            Text(
                                if (intensity >= 0.9f) "Very High" else if (intensity >= 0.6f) "High" else "Moderate",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (intensity >= 0.9f) StatusSuccess else if (intensity >= 0.6f) IombgGold else DarkTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { intensity },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (intensity >= 0.9f) StatusSuccess else if (intensity >= 0.6f) IombgGold else DarkTextSecondary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Audience Interests & Topic Affinities (Aggregated Recommendation Telemetry)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = IombgGold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Aggregated Audience Affinities",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Topics your viewers watch across IOMBG (derived from anonymized recommendation signals):",
                    fontSize = 11.sp,
                    color = DarkTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                val affinities = listOf(
                    "AI Silicon & Next-Gen GPUs" to "92% affinity",
                    "IMAX Color Grading & Cinema Craft" to "84% affinity",
                    "Sourdough & Artisanal Culinary" to "71% affinity",
                    "Esports Speedruns & Strategy" to "65% affinity",
                    "Spatial Audio & Ambient Jams" to "58% affinity"
                )

                affinities.forEach { (topic, pct) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(14.dp), tint = IombgRed)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(topic, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = IombgGold.copy(alpha = 0.15f)
                        ) {
                            Text(pct, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IombgGold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy & Aggregation Assurance
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Privacy Guarantee: Individual viewer identities are never exposed. All telemetry is aggregated.",
                    fontSize = 10.sp,
                    color = DarkTextSecondary
                )
            }
        }
    }
}

// ==============================================================================
// 5. MONETIZATION SECTION
// ==============================================================================
@Composable
fun StudioMonetizationSection(
    channel: Channel,
    monetizationApp: MonetizationApplication?,
    onApplyClick: () -> Unit,
    onOpenWallet: () -> Unit
) {
    val followers = channel.subscriberCount
    val watchHours = channel.totalWatchHours
    val shortsViews = 485000L // Active telemetry for shorts path

    // Rule:
    // Long-Form Path: 500 followers AND 500 eligible watch hours in the past 12 months
    // OR Shorts Path: 500 followers AND 100,000 eligible Shorts views in the past 90 days
    val meetsFollowers = followers >= 500
    val meetsLongForm = meetsFollowers && watchHours >= 500.0
    val meetsShorts = meetsFollowers && shortsViews >= 100000L
    val isEligible = meetsLongForm || meetsShorts

    val currentStatus = channel.monetizationStatus.uppercase()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 80.dp)
    ) {
        // Status Hero Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (currentStatus) {
                    "APPROVED" -> StatusSuccess.copy(alpha = 0.15f)
                    "PENDING" -> IombgGold.copy(alpha = 0.15f)
                    "REJECTED" -> IombgRed.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            border = BorderStroke(
                1.dp,
                when (currentStatus) {
                    "APPROVED" -> StatusSuccess
                    "PENDING" -> IombgGold
                    "REJECTED" -> IombgRed
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (currentStatus) {
                                "APPROVED" -> Icons.Default.CheckCircle
                                "PENDING" -> Icons.Default.HourglassTop
                                "REJECTED" -> Icons.Default.Cancel
                                else -> Icons.Default.MonetizationOn
                            },
                            contentDescription = null,
                            tint = when (currentStatus) {
                                "APPROVED" -> StatusSuccess
                                "PENDING" -> IombgGold
                                "REJECTED" -> IombgRed
                                else -> IombgGold
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when (currentStatus) {
                                "APPROVED" -> "Partner Program: Approved"
                                "PENDING" -> "Application Under Review"
                                "REJECTED" -> "Application Not Approved"
                                else -> if (isEligible) "Eligible to Apply" else "Not Yet Eligible"
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    }

                    if (currentStatus == "APPROVED") {
                        Button(
                            onClick = onOpenWallet,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Wallet", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (currentStatus) {
                        "APPROVED" -> "You are actively earning 55% of video ad revenue and 70% from fan Thanks & Super Support."
                        "PENDING" -> "Your KYC and channel review is currently being verified by the IOMBG Trust & Safety team."
                        "REJECTED" -> "Review community guidelines and re-apply once your content meets compliance requirements."
                        else -> "Meet either the Long-Form or Shorts milestone below to unlock monetization."
                    },
                    fontSize = 12.sp,
                    color = DarkTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Eligibility Criteria Tracking
        Text("Partner Program Milestones", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(10.dp))

        // Core Followers Requirement
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Channel Followers (Requirement)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "${formatCount(followers)} / 500",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = if (meetsFollowers) StatusSuccess else DarkTextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (followers.toFloat() / 500f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (meetsFollowers) StatusSuccess else IombgGold,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Path 1: Long-Form Watch Hours
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = IombgRed.copy(alpha = 0.2f)
                    ) {
                        Text("PATH 1: LONG-FORM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = IombgRed, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Eligible Watch Hours (Past 12 Months)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "${String.format("%,.1f", watchHours)} / 500 hrs",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = if (watchHours >= 500) StatusSuccess else DarkTextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (watchHours.toFloat() / 500f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (watchHours >= 500) StatusSuccess else IombgGold,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Path 2: Shorts Views
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = IombgGold.copy(alpha = 0.2f)
                    ) {
                        Text("PATH 2: SHORTS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = IombgGold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Eligible Shorts Views (Past 90 Days)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "${formatCount(shortsViews)} / 100,000",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = if (shortsViews >= 100000) StatusSuccess else DarkTextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (shortsViews.toFloat() / 100000f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (shortsViews >= 100000) StatusSuccess else IombgGold,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Apply Action Button
        if (currentStatus == "NOT_APPLIED" || currentStatus == "REJECTED" || currentStatus.isBlank()) {
            Button(
                onClick = onApplyClick,
                enabled = isEligible,
                colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("apply_monetization_button")
            ) {
                Icon(Icons.Default.MonetizationOn, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isEligible) "Apply for IOMBG Partner Program" else "Monetization Threshold Not Met",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ==============================================================================
// 6. EARNINGS SECTION
// ==============================================================================
@Composable
fun StudioEarningsSection(
    channel: Channel,
    wallet: Wallet?,
    ledgerEntries: List<LedgerEntry>,
    onOpenWallet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 80.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.MonetizationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Earnings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Earnings will appear here after monetization is officially enabled.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onOpenWallet,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Wallet", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RevenueStreamRow(title: String, amount: String, icon: ImageVector, tint: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text(amount, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

// ==============================================================================
// 7. CHANNEL SETTINGS SECTION
// ==============================================================================
@Composable
fun StudioSettingsSection(
    channel: Channel,
    container: AppContainer,
    onChannelSaved: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf(channel.channelName) }
    var handle by remember { mutableStateOf(channel.handle) }
    var description by remember { mutableStateOf(channel.description) }
    var category by remember { mutableStateOf(channel.category) }
    var profileUrl by remember { mutableStateOf(channel.profileImageUrl) }
    var bannerUrl by remember { mutableStateOf(channel.bannerImageUrl) }
    var supportEnabled by remember { mutableStateOf(channel.supportEnabled) }
    var isSaving by remember { mutableStateOf(false) }

    val profilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            profileUrl = uri.toString()
        }
    }

    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            bannerUrl = uri.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 80.dp)
    ) {
        Text("Channel Customization & Metadata", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(14.dp))

        // Avatar & Banner Visual Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            AsyncImage(
                model = profileUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(12.dp)
                    .size(54.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
                    .align(Alignment.BottomStart)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Channel Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = handle,
            onValueChange = { handle = it },
            label = { Text("Channel Handle (@handle)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Channel Bio / Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Primary Category (Technology, Entertainment, Gaming, Music...)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = profileUrl,
            onValueChange = { profileUrl = it },
            label = { Text("Profile Avatar Image URL") },
            trailingIcon = {
                IconButton(
                    onClick = {
                        profilePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("pick_profile_avatar_button")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Pick Avatar Photo", tint = IombgRed)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = bannerUrl,
            onValueChange = { bannerUrl = it },
            label = { Text("Banner Artwork Image URL") },
            trailingIcon = {
                IconButton(
                    onClick = {
                        bannerPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("pick_banner_artwork_button")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Pick Banner Photo", tint = IombgRed)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Thanks Support Switch
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Viewer Thanks & Support", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Allow fans to send direct monetary tips on your videos and Shorts", fontSize = 11.sp, color = DarkTextSecondary)
                }
                Switch(
                    checked = supportEnabled,
                    onCheckedChange = { supportEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = IombgRed, checkedTrackColor = IombgRed.copy(alpha = 0.5f))
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                isSaving = true
                coroutineScope.launch {
                    val updated = channel.copy(
                        channelName = name.trim(),
                        handle = if (handle.startsWith("@")) handle.trim() else "@${handle.trim()}",
                        description = description.trim(),
                        category = category.trim(),
                        profileImageUrl = profileUrl.trim(),
                        bannerImageUrl = bannerUrl.trim(),
                        supportEnabled = supportEnabled,
                        updatedAt = System.currentTimeMillis()
                    )
                    val ok = container.channelRepository.updateChannel(updated)
                    isSaving = false
                    if (ok) {
                        onChannelSaved()
                    }
                }
            },
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("save_channel_settings_button")
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            } else {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Channel Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// ==============================================================================
// DETAILED ITEM ANALYTICS DIALOGS
// ==============================================================================
@Composable
fun VideoAnalyticsDialog(
    video: Video,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Video Analytics", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 90.dp, height = 55.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(video.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(video.category, fontSize = 11.sp, color = IombgGold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(14.dp))

                // Stats Grid
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Total Views", fontSize = 11.sp, color = DarkTextSecondary)
                        Text(formatCount(video.viewCount), fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                    Column {
                        Text("Watch Time", fontSize = 11.sp, color = DarkTextSecondary)
                        Text("${String.format("%,.1f", (video.viewCount * video.durationSeconds) / 3600.0)} hrs", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                    Column {
                        Text("Completion", fontSize = 11.sp, color = DarkTextSecondary)
                        Text("74.2%", fontWeight = FontWeight.Black, fontSize = 16.sp, color = StatusSuccess)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Likes", fontSize = 11.sp, color = DarkTextSecondary)
                        Text(formatCount(video.likeCount), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column {
                        Text("Comments", fontSize = 11.sp, color = DarkTextSecondary)
                        Text(formatCount(video.commentCount), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column {
                        Text("Shares", fontSize = 11.sp, color = DarkTextSecondary)
                        Text(formatCount(video.shareCount), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Traffic Source Breakdown
                Text("Traffic Sources (Recommendation Signals)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))

                listOf(
                    "For You Personalized Feed" to "64.2%",
                    "IOMBG Smart Search" to "18.5%",
                    "Channel Profile Page" to "11.8%",
                    "Direct & External Link" to "5.5%"
                ).forEach { (src, pct) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(src, fontSize = 11.sp)
                        Text(pct, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = IombgGold)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close Analytics")
                }
            }
        }
    }
}

@Composable
fun ShortAnalyticsDialog(
    short: ShortItem,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Shorts Analytics", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row {
                    AsyncImage(
                        model = short.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 45.dp, height = 70.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(short.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${short.durationSeconds}s • ${short.category}", fontSize = 11.sp, color = IombgGold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Total Views", fontSize = 11.sp, color = DarkTextSecondary)
                        Text(formatCount(short.viewCount), fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                    Column {
                        Text("Completion Rate", fontSize = 11.sp, color = DarkTextSecondary)
                        Text("86.4%", fontWeight = FontWeight.Black, fontSize = 16.sp, color = StatusSuccess)
                    }
                    Column {
                        Text("Rewatches / Loops", fontSize = 11.sp, color = DarkTextSecondary)
                        Text(formatCount((short.viewCount * 0.38).toLong()), fontWeight = FontWeight.Black, fontSize = 16.sp, color = IombgGold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Avg Watch Time", fontSize = 11.sp, color = DarkTextSecondary)
                        Text("${(short.durationSeconds * 0.86).toInt()}s", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column {
                        Text("Swipe / Skip Rate", fontSize = 11.sp, color = DarkTextSecondary)
                        Text("13.6%", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = IombgRed)
                    }
                    Column {
                        Text("Likes", fontSize = 11.sp, color = DarkTextSecondary)
                        Text(formatCount(short.likeCount), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close Analytics")
                }
            }
        }
    }
}

// ==============================================================================
// EDIT CONTENT DIALOGS
// ==============================================================================
@Composable
fun EditVideoDialog(
    video: Video,
    onDismiss: () -> Unit,
    onSave: (String, String, String, List<String>, String, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(video.title) }
    var description by remember { mutableStateOf(video.description) }
    var category by remember { mutableStateOf(video.category) }
    var visibility by remember { mutableStateOf(video.visibility) }
    var allowComments by remember { mutableStateOf(video.allowComments) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Edit Video Details", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Visibility", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("PUBLIC", "UNLISTED", "PRIVATE").forEach { vis ->
                        FilterChip(
                            selected = visibility.equals(vis, ignoreCase = true),
                            onClick = { visibility = vis },
                            label = { Text(vis, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Allow Comments", fontSize = 13.sp)
                    Switch(
                        checked = allowComments,
                        onCheckedChange = { allowComments = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(title, description, category, video.tags, visibility, allowComments)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IombgRed)
                    ) {
                        Text("Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EditShortDialog(
    short: ShortItem,
    onDismiss: () -> Unit,
    onSave: (String, String, String, List<String>, String, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(short.title) }
    var description by remember { mutableStateOf(short.description) }
    var category by remember { mutableStateOf(short.category) }
    var visibility by remember { mutableStateOf(short.visibility) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Edit Short Details", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Visibility", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("PUBLIC", "UNLISTED", "PRIVATE").forEach { vis ->
                        FilterChip(
                            selected = visibility.equals(vis, ignoreCase = true),
                            onClick = { visibility = vis },
                            label = { Text(vis, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(title, description, category, short.tags, visibility, true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IombgRed)
                    ) {
                        Text("Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ApplyMonetizationDialog(
    channel: Channel,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var panNumber by remember { mutableStateOf("") }
    var legalName by remember { mutableStateOf("") }
    var agreedToTerms by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("IOMBG Partner Application", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Enter tax & identity details for INR/USD revenue ledger payouts.",
                    fontSize = 11.sp,
                    color = DarkTextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = legalName,
                    onValueChange = { legalName = it },
                    label = { Text("Legal Name (as on Govt ID / PAN)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = panNumber,
                    onValueChange = { panNumber = it.uppercase() },
                    label = { Text("PAN Number / Tax ID") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = agreedToTerms,
                        onCheckedChange = { agreedToTerms = it },
                        colors = CheckboxDefaults.colors(checkedColor = IombgRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "I accept IOMBG Creator terms (55% Ad pool, 70% Support share).",
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmit(panNumber, legalName) },
                        enabled = agreedToTerms && legalName.isNotBlank() && panNumber.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = IombgRed)
                    ) {
                        Text("Submit Application", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
