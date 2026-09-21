package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ShortItem
import com.example.data.model.Video
import com.example.di.AppContainer
import com.example.ui.components.ShortsRow
import com.example.ui.components.VideoCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    homeViewModel: HomeViewModel,
    onVideoClick: (Video) -> Unit,
    onShortClick: (ShortItem) -> Unit,
    onNotificationClick: () -> Unit,
    onSearchClick: () -> Unit,
    onLiveClick: () -> Unit,
    onPremiumClick: () -> Unit,
    onMessagesClick: () -> Unit = {},
    onAdminClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videos by homeViewModel.rankedVideos.collectAsState()
    val shorts by homeViewModel.shorts.collectAsState()
    val selectedTab by homeViewModel.selectedTab.collectAsState()
    val selectedCategory by homeViewModel.selectedCategory.collectAsState()
    val searchQuery by homeViewModel.searchQuery.collectAsState()
    val isFeedLoading: Boolean by homeViewModel.isFeedLoading.collectAsState()
    val feedError: String? by homeViewModel.feedError.collectAsState()
    val isLoadingMoreVideos: Boolean by homeViewModel.isLoadingMoreVideos.collectAsState()
    val hasMoreVideos: Boolean by homeViewModel.hasMoreVideos.collectAsState()
    val videoPaginationError: String? by homeViewModel.videoPaginationError.collectAsState()
    val currentUser by container.authRepository.currentUserState.collectAsState()
    val unreadCount by container.socialRepository.unreadNotificationsCount.collectAsState()
    val unreadMessagesCount by container.chatRepository.unreadTotalCount.collectAsState()
    val isColdStart by homeViewModel.isColdStart.collectAsState()
    val userInterests by homeViewModel.userInterests.collectAsState()
    val recommendedCreators by homeViewModel.recommendedCreators.collectAsState()
    val followedChannels by container.socialRepository.followedChannels.collectAsState()

    var showColdStartPicker by remember { mutableStateOf(true) }

    val feedTabs = listOf("For You", "Following", "Trending", "Latest")
    val categories = listOf("All", "Technology", "Gaming", "Entertainment", "Food", "Music", "Science", "Podcasts")

    var reportingVideo by remember { mutableStateOf<Video?>(null) }
    var reportReason by remember { mutableStateOf("Spam or misleading") }
    var snackbarHostState = remember { SnackbarHostState() }

    fun shareVideoNative(video: Video) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Watch '${video.title}' on IOMBG: https://iombg.app/v/${video.videoId}")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Video via")
        context.startActivity(shareIntent)
    }

    if (reportingVideo != null) {
        AlertDialog(
            onDismissRequest = { reportingVideo = null },
            title = { Text("Report Video", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Why are you reporting '${reportingVideo?.title?.take(30)}...'?",
                        color = DarkTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf(
                        "Spam or misleading",
                        "Harmful or abusive content",
                        "Copyright infringement",
                        "Inappropriate content",
                        "Harassment or cyberbullying"
                    ).forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reportReason = reason }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = reportReason == reason,
                                onClick = { reportReason = reason },
                                colors = RadioButtonDefaults.colors(selectedColor = IombgRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(reason, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val vid = reportingVideo
                        reportingVideo = null
                        if (vid != null) {
                            scope.launch {
                                container.socialRepository.submitReport(
                                    reporterUid = currentUser?.uid ?: "current_user",
                                    targetId = vid.videoId,
                                    targetType = "VIDEO",
                                    reason = reportReason,
                                    details = "Reported from Home feed"
                                )
                                snackbarHostState.showSnackbar("Report submitted. Thank you for keeping IOMBG safe.")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed)
                ) {
                    Text("Submit Report", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { reportingVideo = null }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(DarkBackground)) {
                // Top App Bar with Logo & Action Icons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo & Brand
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { homeViewModel.updateSearchQuery("") }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 30.dp, height = 22.dp)
                                .background(IombgRed, RoundedCornerShape(5.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Logo",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "IOMBG",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = Color.White
                        )
                    }

                    // Action Icons: Live, Search, Premium, Notifications, Search
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onSearchClick,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("home_search_button")
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onLiveClick,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("home_live_button")
                        ) {
                            Icon(
                                Icons.Default.Sensors,
                                contentDescription = "Live Streams",
                                tint = IombgRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onPremiumClick,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("home_premium_button")
                        ) {
                            Icon(
                                Icons.Default.WorkspacePremium,
                                contentDescription = "Premium",
                                tint = IombgGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (container.adminRepository.isAuthorizedSuperAdmin(currentUser?.uid)) {
                            IconButton(
                                onClick = onAdminClick,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("home_admin_button")
                            ) {
                                Icon(
                                    Icons.Default.AdminPanelSettings,
                                    contentDescription = "Super Admin Console",
                                    tint = IombgGold,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = onNotificationClick,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("home_notifications_button")
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 6.dp, end = 6.dp)
                                        .size(8.dp)
                                        .background(IombgRed, CircleShape)
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = onMessagesClick,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("home_messages_button")
                            ) {
                                Icon(
                                    Icons.Default.Email,
                                    contentDescription = "Messages",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (unreadMessagesCount > 0) {
                                Surface(
                                    color = IombgRed,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 4.dp, end = 4.dp)
                                        .size(16.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "$unreadMessagesCount",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        AsyncImage(
                            model = currentUser?.photoUrl?.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" },
                            contentDescription = "Profile",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF6366F1))
                        )
                    }
                }

                // Search Bar Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { homeViewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search videos, shorts, creators...", fontSize = 13.sp, color = DarkTextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = DarkTextSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { homeViewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = DarkTextSecondary)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .testTag("search_bar_input"),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.2f),
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true
                )

                // Feed Tabs: For You, Following, Trending, Latest
                ScrollableTabRow(
                    selectedTabIndex = feedTabs.indexOf(selectedTab).coerceAtLeast(0),
                    edgePadding = 16.dp,
                    containerColor = DarkBackground,
                    contentColor = Color.White,
                    divider = {}
                ) {
                    feedTabs.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { homeViewModel.selectTab(tab) },
                            text = {
                                Text(
                                    text = tab,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (selectedTab == tab) Color.White else DarkTextSecondary
                                )
                            },
                            modifier = Modifier.testTag("tab_${tab.lowercase().replace(" ", "_")}")
                        )
                    }
                }

                // Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { homeViewModel.selectCategory(cat) }
                                .testTag("category_chip_${cat.lowercase()}"),
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cold Start Topic Picker Banner (for new users or feed personalization)
            if (isColdStart && showColdStartPicker && selectedTab == "For You" && searchQuery.isBlank()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = IombgRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Personalize Your Feed",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = { showColdStartPicker = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = DarkTextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Select topics you love to instantly tune your intelligent recommendation engine.",
                                fontSize = 12.sp,
                                color = DarkTextSecondary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                            )

                            // Multi-select topic chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Technology", "Gaming", "Entertainment", "Food", "Music", "Science", "Podcasts").forEach { cat ->
                                    val isSelected = userInterests.contains(cat)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { homeViewModel.toggleInterest(cat) },
                                        label = { Text(cat, fontSize = 12.sp) },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = IombgRed,
                                            selectedLabelColor = Color.White,
                                            containerColor = DarkSurfaceVariant,
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // First 2 videos
            items(videos.take(2)) { video ->
                LaunchedEffect(video.videoId) {
                    homeViewModel.recordImpression(video)
                }
                Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                    VideoCard(
                        video = video,
                        onClick = {
                            homeViewModel.recordClick(video)
                            onVideoClick(video)
                        },
                        onShareClick = { shareVideoNative(video) },
                        onNotInterestedClick = {
                            homeViewModel.markNotInterested(video)
                            scope.launch {
                                snackbarHostState.showSnackbar("We will show fewer videos like this.")
                            }
                        },
                        onHideChannelClick = {
                            homeViewModel.hideChannel(video.channelId)
                            scope.launch {
                                snackbarHostState.showSnackbar("Channel hidden from recommendations.")
                            }
                        },
                        onReportClick = { reportingVideo = video }
                    )
                }
            }

            // Shorts Section in between feed
            if (shorts.isNotEmpty()) {
                item {
                    ShortsRow(shorts = shorts, onShortClick = onShortClick)
                }
            }

            // Emerging Creator Discovery Section (Giving emerging creators fair discovery)
            if (recommendedCreators.isNotEmpty() && selectedTab == "For You" && searchQuery.isBlank()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = IombgRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Discover Emerging Creators",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            recommendedCreators.forEach { rec ->
                                val channel = rec.channel
                                val isSubbed = followedChannels.contains(channel.channelId)
                                Card(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .testTag("creator_discovery_${channel.channelId}"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AsyncImage(
                                            model = channel.profileImageUrl.ifBlank { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200" },
                                            contentDescription = channel.channelName,
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(DarkSurfaceVariant)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = channel.channelName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = rec.reason,
                                            fontSize = 10.sp,
                                            color = if (rec.isEmergingCreator) IombgRed else DarkTextMuted,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = { homeViewModel.followCreator(channel) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSubbed) DarkSurfaceVariant else IombgRed,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(
                                                text = if (isSubbed) "Following" else "Follow",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Loading indicator at top if loading
            if (isFeedLoading && videos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = IombgRed,
                            modifier = Modifier.size(36.dp).testTag("feed_loading_indicator")
                        )
                    }
                }
            }

            // Error banner if any error occurred
            if (feedError != null && videos.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = StatusError,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Unable to load feed",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = feedError ?: "Please check your network connection.",
                                fontSize = 12.sp,
                                color = DarkTextSecondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { homeViewModel.refreshFeed() },
                                colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Empty State
            if (!isFeedLoading && videos.isEmpty() && feedError == null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = DarkTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No videos found for '$searchQuery'" else "No videos yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (selectedTab == "Following") "Follow creators to see their published videos here." else "Published videos will appear here.",
                            fontSize = 13.sp,
                            color = DarkTextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { homeViewModel.refreshFeed() },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh Feed", color = Color.White)
                        }
                    }
                }
            }

            // Remaining Videos
            items(videos.drop(2)) { video ->
                LaunchedEffect(video.videoId) {
                    homeViewModel.recordImpression(video)
                }
                Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                    VideoCard(
                        video = video,
                        onClick = {
                            homeViewModel.recordClick(video)
                            onVideoClick(video)
                        },
                        onShareClick = { shareVideoNative(video) },
                        onNotInterestedClick = {
                            homeViewModel.markNotInterested(video)
                            scope.launch {
                                snackbarHostState.showSnackbar("We will show fewer videos like this.")
                            }
                        },
                        onHideChannelClick = {
                            homeViewModel.hideChannel(video.channelId)
                            scope.launch {
                                snackbarHostState.showSnackbar("Channel hidden from recommendations.")
                            }
                        },
                        onReportClick = { reportingVideo = video }
                    )
                }
            }

            // Safe Cursor Pagination Footer at bottom of feed
            if (videos.isNotEmpty()) {
                item {
                    if (hasMoreVideos && !isLoadingMoreVideos && videoPaginationError == null) {
                        LaunchedEffect(videos.size) {
                            homeViewModel.loadMoreVideos()
                        }
                    }

                    if (isLoadingMoreVideos) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp)
                                .testTag("feed_pagination_loading"),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = IombgRed,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp).testTag("pagination_loading_indicator")
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Loading more videos...",
                                color = DarkTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    } else if (videoPaginationError != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                                .testTag("feed_pagination_error"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = videoPaginationError ?: "Failed to load more videos",
                                color = StatusError,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { homeViewModel.retryLoadMoreVideos() },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("feed_pagination_retry_button")
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
