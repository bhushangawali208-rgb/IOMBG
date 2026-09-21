package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.NotificationItem
import com.example.data.model.NotificationType
import com.example.di.AppContainer
import com.example.ui.components.formatTimeAgo
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit = {},
    onNavigateToShort: (String) -> Unit = {},
    onNavigateToChannel: (String) -> Unit = {},
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToLive: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val notifications by container.socialRepository.notifications.collectAsState()
    val isLoading by container.socialRepository.isLoadingNotifications.collectAsState()
    val errorMessage by container.socialRepository.notificationsError.collectAsState()
    val currentUser by container.authRepository.currentUserState.collectAsState()

    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    var showPermissionBanner by remember { mutableStateOf(!hasNotificationPermission) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            showPermissionBanner = false
        }
    }

    val hasUnread = remember(notifications) {
        notifications.any { !it.isRead }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity & Notifications", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasUnread) {
                        TextButton(
                            onClick = {
                                container.socialRepository.markAllNotificationsAsRead(currentUser?.uid ?: "")
                            },
                            modifier = Modifier.testTag("mark_all_read_button")
                        ) {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = "Mark all as read",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mark all read", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Android 13+ contextual permission prompt
            if (showPermissionBanner && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("notification_permission_banner"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = IombgRed,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Turn on push notifications",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Get real-time OS alerts for likes, comments, broadcasts, and messages.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("enable_notifications_button")
                        ) {
                            Text("Enable", fontSize = 12.sp)
                        }
                        IconButton(
                            onClick = { showPermissionBanner = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
            when {
                isLoading && notifications.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("notifications_loading_indicator"),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = IombgRed,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                errorMessage != null && notifications.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .testTag("notifications_error_state"),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Failed to load notifications",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorMessage ?: "A network or permissions error occurred.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { container.socialRepository.retryObserveNotifications() },
                            modifier = Modifier.testTag("notifications_retry_button")
                        ) {
                            Text("Retry")
                        }
                    }
                }
                notifications.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                            .testTag("notifications_empty_state"),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No notifications yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "When people interact with your videos, follow your channel, or message you, you will see updates here in real time.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("notifications_list"),
                        contentPadding = PaddingValues(16.dp, bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(notifications, key = { it.notificationId }) { notif ->
                            NotificationCardItem(
                                item = notif,
                                onClick = {
                                    // 1. Mark as read in Firestore and local state
                                    if (!notif.isRead) {
                                        container.socialRepository.markNotificationAsRead(notif.notificationId)
                                    }
                                    // 2. Navigate based on notification type and target ID
                                    handleNotificationNavigation(
                                        item = notif,
                                        onNavigateToVideo = onNavigateToVideo,
                                        onNavigateToShort = onNavigateToShort,
                                        onNavigateToChannel = onNavigateToChannel,
                                        onNavigateToChat = onNavigateToChat,
                                        onNavigateToLive = onNavigateToLive
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun NotificationCardItem(
    item: NotificationItem,
    onClick: () -> Unit
) {
    val cardBg = if (!item.isRead) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_item_${item.notificationId}")
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sender Avatar or Type Icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        when (item.type) {
                            NotificationType.SUPPORT_RECEIVED -> IombgRed.copy(alpha = 0.2f)
                            NotificationType.MONETIZATION_STATUS -> StatusSuccess.copy(alpha = 0.2f)
                            NotificationType.NEW_FOLLOWER -> IombgGold.copy(alpha = 0.2f)
                            NotificationType.VIDEO_LIKE, NotificationType.LIKE -> IombgRed.copy(alpha = 0.2f)
                            NotificationType.COMMENT, NotificationType.REPLY -> Color(0xFF0288D1).copy(alpha = 0.2f)
                            NotificationType.MESSAGE, NotificationType.MESSAGE_REQUEST, NotificationType.MESSAGE_REQUEST_ACCEPTED -> Color(0xFF43A047).copy(alpha = 0.2f)
                            NotificationType.LIVE_STARTED -> IombgRed.copy(alpha = 0.25f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.senderAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.senderAvatarUrl,
                        contentDescription = item.senderName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = when (item.type) {
                            NotificationType.SUPPORT_RECEIVED -> Icons.Default.VolunteerActivism
                            NotificationType.MONETIZATION_STATUS -> Icons.Default.Stars
                            NotificationType.NEW_FOLLOWER -> Icons.Default.PersonAdd
                            NotificationType.VIDEO_LIKE, NotificationType.LIKE -> Icons.Default.Favorite
                            NotificationType.COMMENT, NotificationType.REPLY -> Icons.Default.Comment
                            NotificationType.MESSAGE, NotificationType.MESSAGE_REQUEST, NotificationType.MESSAGE_REQUEST_ACCEPTED -> Icons.Default.Chat
                            NotificationType.LIVE_STARTED -> Icons.Default.LiveTv
                            else -> Icons.Default.Notifications
                        },
                        contentDescription = null,
                        tint = when (item.type) {
                            NotificationType.SUPPORT_RECEIVED -> IombgRed
                            NotificationType.MONETIZATION_STATUS -> StatusSuccess
                            NotificationType.NEW_FOLLOWER -> IombgGold
                            NotificationType.VIDEO_LIKE, NotificationType.LIKE -> IombgRed
                            NotificationType.COMMENT, NotificationType.REPLY -> Color(0xFF0288D1)
                            NotificationType.MESSAGE, NotificationType.MESSAGE_REQUEST, NotificationType.MESSAGE_REQUEST_ACCEPTED -> Color(0xFF43A047)
                            NotificationType.LIVE_STARTED -> IombgRed
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatTimeAgo(item.createdAt),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.body,
                    fontSize = 12.sp,
                    color = if (!item.isRead) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Unread Dot Indicator
            if (!item.isRead) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(IombgRed, CircleShape)
                        .testTag("unread_dot")
                )
            }
        }
    }
}

private fun handleNotificationNavigation(
    item: NotificationItem,
    onNavigateToVideo: (String) -> Unit,
    onNavigateToShort: (String) -> Unit,
    onNavigateToChannel: (String) -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToLive: (String) -> Unit
) {
    val targetId = item.targetContentId
    when (item.type) {
        NotificationType.NEW_FOLLOWER -> {
            val channelOrUser = targetId ?: item.senderUid
            if (channelOrUser.isNotBlank()) {
                onNavigateToChannel(channelOrUser)
            }
        }
        NotificationType.VIDEO_LIKE -> {
            if (!targetId.isNullOrBlank()) {
                onNavigateToVideo(targetId)
            }
        }
        NotificationType.LIKE -> {
            if (!targetId.isNullOrBlank()) {
                onNavigateToShort(targetId)
            }
        }
        NotificationType.COMMENT, NotificationType.REPLY -> {
            if (!targetId.isNullOrBlank()) {
                if (item.targetContentType == "SHORT") {
                    onNavigateToShort(targetId)
                } else {
                    onNavigateToVideo(targetId)
                }
            }
        }
        NotificationType.MESSAGE, NotificationType.MESSAGE_REQUEST, NotificationType.MESSAGE_REQUEST_ACCEPTED -> {
            if (!targetId.isNullOrBlank()) {
                onNavigateToChat(targetId)
            }
        }
        NotificationType.LIVE_STARTED -> {
            if (!targetId.isNullOrBlank()) {
                onNavigateToLive(targetId)
            }
        }
        else -> {
            // For general announcements or unsupported targets, safe no-op
        }
    }
}

