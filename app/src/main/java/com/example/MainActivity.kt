package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.LiveStream
import com.example.data.model.ShortItem
import com.example.data.model.Video
import com.example.di.AppContainer
import com.example.ui.components.WelcomeOnboardingDialog
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SearchViewModel
import com.example.ui.viewmodel.ShortsViewModel

import android.content.Intent
import com.example.notifications.IombgFirebaseMessagingService
import com.example.notifications.NotificationChannels

class MainActivity : ComponentActivity() {
    private lateinit var appContainer: AppContainer
    private val pendingDeepLink = mutableStateOf<Pair<String, String?>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = AppContainer(applicationContext)
        NotificationChannels.createNotificationChannels(applicationContext)
        extractNotificationExtras(intent)
        enableEdgeToEdge()

        setContent {
            val mainViewModel = remember { MainViewModel(appContainer) }
            val homeViewModel = remember { HomeViewModel(appContainer) }
            val shortsViewModel = remember { ShortsViewModel(appContainer) }

            val isDarkTheme by mainViewModel.isDarkTheme.collectAsState()
            val currentUser by mainViewModel.currentUser.collectAsState()

            LaunchedEffect(currentUser?.uid, pendingDeepLink.value) {
                if (currentUser != null && pendingDeepLink.value != null) {
                    val (type, id) = pendingDeepLink.value!!
                    pendingDeepLink.value = null
                    mainViewModel.handleDeepLink(type, id)
                }
            }

            IombgTheme(darkTheme = isDarkTheme) {
                if (currentUser == null) {
                    AuthScreen(
                        container = appContainer,
                        onAuthSuccess = {}
                    )
                } else {
                    IombgAppRoot(
                        container = appContainer,
                        mainViewModel = mainViewModel,
                        homeViewModel = homeViewModel,
                        shortsViewModel = shortsViewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractNotificationExtras(intent)
    }

    private fun extractNotificationExtras(intent: Intent?) {
        val type = intent?.getStringExtra(IombgFirebaseMessagingService.EXTRA_DESTINATION_TYPE)
        val id = intent?.getStringExtra(IombgFirebaseMessagingService.EXTRA_DESTINATION_ID)
        if (!type.isNullOrBlank()) {
            pendingDeepLink.value = Pair(type, id)
        }
    }
}

@Composable
fun IombgAppRoot(
    container: AppContainer,
    mainViewModel: MainViewModel,
    homeViewModel: HomeViewModel,
    shortsViewModel: ShortsViewModel
) {
    val selectedTab by mainViewModel.selectedBottomNav.collectAsState()
    val selectedVideo by mainViewModel.selectedVideo.collectAsState()
    val selectedLiveStream by mainViewModel.selectedLiveStream.collectAsState()
    val currentUser by mainViewModel.currentUser.collectAsState()
    val showWelcomeOnboarding by container.authRepository.showWelcomeOnboarding.collectAsState()
    val unreadMessagesCount by container.chatRepository.unreadTotalCount.collectAsState()
    val messageRequests by container.chatRepository.messageRequests.collectAsState()

    var activeSecondaryScreen by remember { mutableStateOf<String?>(null) } // "create_channel", "creator_studio", "monetization", "wallet", "boost", "premium", "notifications", "admin", "search"
    var videoToBoost by remember { mutableStateOf<Video?>(null) }
    var thanksSupportTarget by remember { mutableStateOf<Triple<String, String, String?>?>(null) } // targetChannelId, creatorName, contentTitle
    var activeBroadcastingStream by remember { mutableStateOf<LiveStream?>(null) }
    val searchViewModel = remember { SearchViewModel(container) }

    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        if (uid != null) {
            container.channelRepository.loadChannelForUser(uid)
            container.socialRepository.observeUserNotifications(uid)
            container.socialRepository.syncFollowedChannels(uid)
            container.chatRepository.setActiveUser(uid)
        } else {
            container.channelRepository.clearChannel()
            container.socialRepository.clearNotifications()
            container.socialRepository.clearFollowedChannels()
            container.chatRepository.clearActiveUser()
            container.adminRepository.clearAdminState()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    Column {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = 1.dp
                        )
                        NavigationBar(
                            containerColor = DarkBackground,
                            tonalElevation = 0.dp,
                            modifier = Modifier.testTag("bottom_nav_bar")
                        ) {
                            // Home Tab
                            NavigationBarItem(
                                selected = selectedTab == "home",
                                onClick = { mainViewModel.selectBottomNav("home"); activeSecondaryScreen = null },
                                icon = { Icon(if (selectedTab == "home") Icons.Filled.Home else Icons.Outlined.Home, contentDescription = "Home") },
                                label = { Text("Home", fontSize = 10.sp, fontWeight = if (selectedTab == "home") FontWeight.Bold else FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = Color.White,
                                    unselectedIconColor = DarkTextSecondary,
                                    unselectedTextColor = DarkTextSecondary,
                                    indicatorColor = Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.testTag("bottom_nav_home")
                            )

                            // Shorts Tab
                            NavigationBarItem(
                                selected = selectedTab == "shorts",
                                onClick = { mainViewModel.selectBottomNav("shorts"); activeSecondaryScreen = null },
                                icon = { Icon(if (selectedTab == "shorts") Icons.Filled.FlashOn else Icons.Outlined.FlashOn, contentDescription = "Shorts") },
                                label = { Text("Shorts", fontSize = 10.sp, fontWeight = if (selectedTab == "shorts") FontWeight.Bold else FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = Color.White,
                                    unselectedIconColor = DarkTextSecondary,
                                    unselectedTextColor = DarkTextSecondary,
                                    indicatorColor = Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.testTag("bottom_nav_shorts")
                            )

                            // Create (+) Center Action Tab
                            NavigationBarItem(
                                selected = selectedTab == "create",
                                onClick = { mainViewModel.selectBottomNav("create"); activeSecondaryScreen = null },
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                                            .padding(1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Create",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                },
                                label = { Text("Create", fontSize = 10.sp, color = DarkTextSecondary) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.testTag("bottom_nav_create")
                            )

                            // Direct Messages Tab
                            NavigationBarItem(
                                selected = selectedTab == "chat",
                                onClick = { mainViewModel.selectBottomNav("chat"); activeSecondaryScreen = null },
                                icon = {
                                    Box {
                                        Icon(
                                            if (selectedTab == "chat") Icons.Filled.Email else Icons.Outlined.Email,
                                            contentDescription = "Messages"
                                        )
                                        val totalChatBadge = unreadMessagesCount + messageRequests.size
                                        if (totalChatBadge > 0) {
                                            Surface(
                                                color = IombgRed,
                                                shape = CircleShape,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 6.dp, y = (-2).dp)
                                                    .size(16.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        "$totalChatBadge",
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                label = { Text("Messages", fontSize = 10.sp, fontWeight = if (selectedTab == "chat") FontWeight.Bold else FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = Color.White,
                                    unselectedIconColor = DarkTextSecondary,
                                    unselectedTextColor = DarkTextSecondary,
                                    indicatorColor = Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.testTag("bottom_nav_chat")
                            )

                            // Channel / You Profile Tab
                            NavigationBarItem(
                                selected = selectedTab == "profile",
                                onClick = { mainViewModel.selectBottomNav("profile"); activeSecondaryScreen = null },
                                icon = { Icon(if (selectedTab == "profile") Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle, contentDescription = "You") },
                                label = { Text("You", fontSize = 10.sp, fontWeight = if (selectedTab == "profile") FontWeight.Bold else FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = Color.White,
                                    unselectedIconColor = DarkTextSecondary,
                                    unselectedTextColor = DarkTextSecondary,
                                    indicatorColor = Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.testTag("bottom_nav_profile")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTab) {
                        "home" -> HomeScreen(
                            container = container,
                            homeViewModel = homeViewModel,
                            onVideoClick = { mainViewModel.playVideo(it) },
                            onShortClick = { mainViewModel.selectBottomNav("shorts") },
                            onNotificationClick = { activeSecondaryScreen = "notifications" },
                            onSearchClick = { activeSecondaryScreen = "search" },
                            onLiveClick = {
                                val firstLive = container.liveStreamRepository.activeStreams.value.firstOrNull()
                                if (firstLive != null) {
                                    mainViewModel.openLiveStream(firstLive)
                                }
                            },
                            onPremiumClick = { activeSecondaryScreen = "premium" },
                            onMessagesClick = { mainViewModel.selectBottomNav("chat") },
                            onAdminClick = { activeSecondaryScreen = "admin" }
                        )
                        "shorts" -> ShortsScreen(
                            container = container,
                            shortsViewModel = shortsViewModel,
                            onOpenThanksSupport = { short ->
                                thanksSupportTarget = Triple(short.channelId, short.channelName, short.title)
                            }
                        )
                        "create" -> CreateUploadScreen(
                            container = container,
                            onUploadComplete = {
                                mainViewModel.selectBottomNav("home")
                            },
                            onCreateChannelClick = { activeSecondaryScreen = "create_channel" },
                            onGoLiveStarted = { liveStream ->
                                activeBroadcastingStream = liveStream
                            }
                        )
                        "chat" -> ChatScreen(
                            container = container,
                            onBack = { mainViewModel.selectBottomNav("home") }
                        )
                        "profile" -> ChannelProfileScreen(
                            container = container,
                            onOpenCreatorStudio = { activeSecondaryScreen = "creator_studio" },
                            onCreateChannelClick = { activeSecondaryScreen = "create_channel" },
                            onOpenMonetization = { activeSecondaryScreen = "monetization" },
                            onOpenWallet = { activeSecondaryScreen = "wallet" },
                            onVideoClick = { mainViewModel.playVideo(it) },
                            onShortClick = { mainViewModel.selectBottomNav("shorts") },
                            onOpenAdmin = { activeSecondaryScreen = "admin" },
                            onOpenWatchHistory = { activeSecondaryScreen = "watch_history" }
                        )
                    }
                }
            }

            // Secondary Full Screen Modals
            AnimatedVisibility(
                visible = activeSecondaryScreen != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                when (activeSecondaryScreen) {
                    "create_channel" -> CreateChannelScreen(
                        container = container,
                        onChannelCreated = {
                            activeSecondaryScreen = "creator_studio"
                        },
                        onBack = { activeSecondaryScreen = null }
                    )
                    "creator_studio" -> CreatorStudioScreen(
                        container = container,
                        onBack = { activeSecondaryScreen = null },
                        onOpenMonetization = { activeSecondaryScreen = "monetization" },
                        onOpenWallet = { activeSecondaryScreen = "wallet" },
                        onOpenBoost = { activeSecondaryScreen = "boost" },
                        onNavigateToCreateChannel = { activeSecondaryScreen = "create_channel" }
                    )
                    "monetization" -> MonetizationScreen(
                        container = container,
                        onBack = { activeSecondaryScreen = null }
                    )
                    "wallet" -> WalletScreen(
                        container = container,
                        onBack = { activeSecondaryScreen = null }
                    )
                    "boost" -> BoostScreen(
                        container = container,
                        preSelectedVideo = videoToBoost,
                        onBack = { activeSecondaryScreen = null }
                    )
                    "premium" -> PremiumScreen(
                        container = container,
                        onBack = { activeSecondaryScreen = null }
                    )
                    "notifications" -> NotificationsScreen(
                        container = container,
                        onBack = { activeSecondaryScreen = null },
                        onNavigateToVideo = { videoId ->
                            val video = container.videoRepository.videosFeed.value.firstOrNull { it.videoId == videoId }
                            if (video != null) {
                                activeSecondaryScreen = null
                                mainViewModel.playVideo(video)
                            }
                        },
                        onNavigateToShort = {
                            activeSecondaryScreen = null
                            mainViewModel.selectBottomNav("shorts")
                        },
                        onNavigateToChannel = {
                            activeSecondaryScreen = null
                            mainViewModel.selectBottomNav("profile")
                        },
                        onNavigateToChat = { convId ->
                            activeSecondaryScreen = null
                            container.chatRepository.selectConversation(convId)
                            mainViewModel.selectBottomNav("chat")
                        },
                        onNavigateToLive = { streamId ->
                            val stream = container.liveStreamRepository.activeStreams.value.firstOrNull { it.streamId == streamId }
                            if (stream != null) {
                                activeSecondaryScreen = null
                                mainViewModel.openLiveStream(stream)
                            }
                        }
                    )
                    "admin" -> AdminDashboardScreen(
                        container = container,
                        onBack = { activeSecondaryScreen = null }
                    )
                    "search" -> SearchScreen(
                        container = container,
                        searchViewModel = searchViewModel,
                        onBack = { activeSecondaryScreen = null },
                        onVideoClick = { video ->
                            activeSecondaryScreen = null
                            mainViewModel.playVideo(video)
                        },
                        onShortClick = { short ->
                            activeSecondaryScreen = null
                            mainViewModel.selectBottomNav("shorts")
                        },
                        onChannelClick = { channel ->
                            activeSecondaryScreen = null
                            mainViewModel.selectBottomNav("profile")
                        }
                    )
                    "watch_history" -> WatchHistoryScreen(
                        container = container,
                        onBack = { activeSecondaryScreen = null },
                        onVideoClick = { video ->
                            activeSecondaryScreen = null
                            mainViewModel.playVideo(video)
                        },
                        onShortClick = { shortId ->
                            activeSecondaryScreen = null
                            mainViewModel.selectBottomNav("shorts")
                        }
                    )
                }
            }

            // Welcome Onboarding Dialog (First Login / No Channel Prompt)
            if (showWelcomeOnboarding) {
                WelcomeOnboardingDialog(
                    onExplore = {
                        container.authRepository.dismissWelcomeOnboarding()
                    },
                    onCreateChannel = {
                        container.authRepository.dismissWelcomeOnboarding()
                        activeSecondaryScreen = "create_channel"
                    }
                )
            }

            // Active Video Playback Overlay
            selectedVideo?.let { video ->
                VideoDetailScreen(
                    video = video,
                    container = container,
                    onBack = { mainViewModel.closeVideoPlayer() },
                    onOpenThanksSupport = {
                        thanksSupportTarget = Triple(it.channelId, it.channelName, it.title)
                    },
                    onOpenBoost = {
                        videoToBoost = it
                        activeSecondaryScreen = "boost"
                    },
                    onOpenChannel = {
                        mainViewModel.closeVideoPlayer()
                        mainViewModel.selectBottomNav("profile")
                    },
                    onVideoClick = { mainViewModel.playVideo(it) }
                )
            }

            // Active Live Stream Broadcast Overlay (Viewer Mode)
            selectedLiveStream?.let { liveStream ->
                LiveStreamScreen(
                    stream = liveStream,
                    container = container,
                    onClose = { mainViewModel.closeLiveStream() },
                    onOpenThanksSupport = {
                        thanksSupportTarget = Triple(it.channelId, it.channelName, it.title)
                    }
                )
            }

            // Active Creator Live Studio Overlay (Broadcast & Telemetry Mode)
            activeBroadcastingStream?.let { broadcastingStream ->
                CreatorLiveStudioScreen(
                    stream = broadcastingStream,
                    container = container,
                    onFinishLive = {
                        activeBroadcastingStream = null
                    }
                )
            }

            // Thanks & Creator Support Dialog
            thanksSupportTarget?.let { (chId, name, title) ->
                ThanksSupportDialog(
                    targetChannelId = chId,
                    creatorName = name,
                    contentTitle = title,
                    container = container,
                    onDismiss = { thanksSupportTarget = null },
                    onSuccess = { thanksSupportTarget = null }
                )
            }
        }
    }
}
