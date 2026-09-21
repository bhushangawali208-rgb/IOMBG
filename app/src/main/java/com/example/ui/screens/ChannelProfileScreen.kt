package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ShortItem
import com.example.data.model.Video
import com.example.di.AppContainer
import com.example.ui.components.VideoCard
import com.example.ui.components.formatCount
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ChannelProfileScreen(
    container: AppContainer,
    onOpenCreatorStudio: () -> Unit,
    onCreateChannelClick: () -> Unit,
    onOpenMonetization: () -> Unit,
    onOpenWallet: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onShortClick: (ShortItem) -> Unit,
    onOpenAdmin: () -> Unit = {},
    onOpenWatchHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }

    val currentUser by container.authRepository.currentUserState.collectAsState()
    val currentChannel by container.channelRepository.currentChannel.collectAsState()
    val videos by container.videoRepository.videosFeed.collectAsState()
    val shorts by container.videoRepository.shortsFeed.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Videos, 1: Shorts, 2: About

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "IOMBG values your personal data and privacy. We collect minimal telemetry required to deliver video streaming, creator revenue allocation, and fraud prevention.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• Data Collected: Account email, profile name, uploaded video metadata, watch history, and payment ledger receipts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• Third-Party Sharing: We never sell personal data. Financial transactions are securely processed via certified payment gateways.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Note for Store Submission: Prior to production release, this policy is published at the public URL configured in Play Console.",
                        style = MaterialTheme.typography.labelSmall,
                        color = IombgGold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed)
                ) {
                    Text("Close")
                }
            }
        )
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms of Service", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "By using IOMBG, you agree to comply with our community and copyright standards.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• Content Guidelines: Zero tolerance for hate speech, harassment, sexually explicit material, or illegal content.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• Creator Monetization: Adherence to the 70/30 creator revenue split, tax compliance, and automated copyright clearance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Note for Store Submission: Standard Terms of Service URL must be linked in Store Listing.",
                        style = MaterialTheme.typography.labelSmall,
                        color = IombgGold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTermsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed)
                ) {
                    Text("Close")
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountDialog = false },
            title = { Text("Delete Account & Data", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Are you sure you want to delete your account? This action is PERMANENT and cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "In compliance with Google Play Data Safety requirements, all your user profile records, authentication credentials, and personal data will be completely erased.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isDeletingAccount = true
                            container.authRepository.requestAccountDeletion()
                            container.channelRepository.clearChannel()
                            isDeletingAccount = false
                            showDeleteAccountDialog = false
                        }
                    },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Permanently Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountDialog = false },
                    enabled = !isDeletingAccount
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out of IOMBG", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to sign out? You will need to sign in with your Google account again.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        coroutineScope.launch {
                            container.authRepository.signOut()
                            container.channelRepository.clearChannel()
                            container.adminRepository.clearAdminState()
                            container.socialRepository.clearNotifications()
                            container.socialRepository.clearFollowedChannels()
                            container.chatRepository.clearActiveUser()
                            container.watchHistoryRepository.clearActiveUser()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            if (currentChannel != null) {
                // ==========================================
                // CREATOR MODE (Channel Exists)
                // ==========================================
                val channel = currentChannel!!

                // Channel Banner
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = channel.bannerImageUrl,
                            contentDescription = "Channel Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Channel Avatar & Identity Header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Offset Avatar
                            AsyncImage(
                                model = channel.profileImageUrl,
                                contentDescription = channel.channelName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .offset(y = (-30).dp)
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(3.dp)
                                    .clip(CircleShape)
                            )

                            // Action Pills
                            Row(
                                modifier = Modifier.padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = onOpenCreatorStudio,
                                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("creator_studio_button")
                                ) {
                                    Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Creator Studio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = { showLogoutDialog = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .testTag("profile_sign_out_button")
                                ) {
                                    Icon(
                                        Icons.Default.Logout,
                                        contentDescription = "Sign Out",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Name, Handle, Badges
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = channel.channelName,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = IombgRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "${channel.handle} • ${formatCount(channel.subscriberCount)} followers • ${channel.videoCount} videos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        currentUser?.email?.let { email ->
                            if (email.isNotBlank()) {
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = channel.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Quick Creator Tools Hub
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onOpenMonetization,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = IombgGold, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Monetize", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onOpenWallet,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Wallet", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            if (container.adminRepository.isAuthorizedSuperAdmin(currentUser?.uid)) {
                                Button(
                                    onClick = onOpenAdmin,
                                    modifier = Modifier.weight(1f).testTag("creator_admin_console_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = IombgGold),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Admin", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Channel Tabs
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.surface,
                            divider = {}
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Videos", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Shorts", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text("About & Settings", fontWeight = FontWeight.Bold) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Tab Content
                when (selectedTab) {
                    0 -> {
                        items(videos) { video ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                VideoCard(video = video, onClick = { onVideoClick(video) })
                            }
                        }
                    }
                    1 -> {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                shorts.forEach { short ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        onClick = { onShortClick(short) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = short.thumbnailUrl,
                                                contentDescription = short.title,
                                                modifier = Modifier
                                                    .size(60.dp, 80.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(short.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2)
                                                Text("${formatCount(short.viewCount)} views • ${formatCount(short.likeCount)} likes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("About Channel", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(channel.description, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.height(14.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text("📊 Total Lifetime Views: ${formatCount(channel.totalViews)}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("⏱️ Total Watch Time: ${String.format("%.1f", channel.totalWatchHours)} Hours", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("🌐 Joined IOMBG: 2026", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(14.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(10.dp))
                                    ClickableSettingsRow(
                                        icon = Icons.Default.History,
                                        title = "Watch History",
                                        value = "View & Sync",
                                        onClick = onOpenWatchHistory,
                                        testTag = "creator_settings_watch_history_row"
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                    OutlinedButton(
                                        onClick = { showLogoutDialog = true },
                                        modifier = Modifier.fillMaxWidth().testTag("about_sign_out_button"),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = IombgRed),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sign Out of Account", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ==========================================
                // VIEWER MODE (No Channel Created Yet)
                // ==========================================
                val user = currentUser
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // User Profile Header Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
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
                                            model = user?.photoUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400",
                                            contentDescription = "User Avatar",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(CircleShape)
                                                .border(2.dp, IombgRed, CircleShape)
                                        )

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column {
                                            Text(
                                                text = user?.displayName ?: "IOMBG Viewer",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = user?.email ?: "viewer@iombg.com",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                color = Color.White.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "VIEWER ACCOUNT",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = { showLogoutDialog = true },
                                        modifier = Modifier.testTag("viewer_sign_out_button")
                                    ) {
                                        Icon(Icons.Default.Logout, contentDescription = "Sign Out", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Prompt Card: "Create Your Channel"
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IombgRed.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(IombgRed.copy(alpha = 0.15f), Color.Transparent)
                                        )
                                    )
                                    .padding(20.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .background(IombgRed, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.VideoCall, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = "Become an IOMBG Creator",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "Create your channel to upload 4K HDR videos, broadcast live streams, build a dedicated audience, and earn revenue with direct creator monetization.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        lineHeight = 18.sp
                                    )

                                    Spacer(modifier = Modifier.height(18.dp))

                                    Button(
                                        onClick = onCreateChannelClick,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("profile_create_channel_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Create Your Channel", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Account Settings & Preferences Section
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Account & Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(12.dp))

                                ClickableSettingsRow(
                                    icon = Icons.Default.History,
                                    title = "Watch History",
                                    value = "View & Sync",
                                    onClick = onOpenWatchHistory,
                                    testTag = "settings_watch_history_row"
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                SettingsRow(icon = Icons.Default.Hd, title = "Streaming Quality", value = "Up to 4K Ultra HD")
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                SettingsRow(icon = Icons.Default.Notifications, title = "Notifications", value = "Push Enabled")
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                SettingsRow(icon = Icons.Default.Security, title = "Security & Firebase Auth", value = "Google Verified")
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                ClickableSettingsRow(
                                    icon = Icons.Default.Policy,
                                    title = "Privacy Policy",
                                    value = "View Notice",
                                    onClick = { showPrivacyDialog = true },
                                    testTag = "settings_privacy_policy_row"
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                ClickableSettingsRow(
                                    icon = Icons.Default.Description,
                                    title = "Terms of Service",
                                    value = "View Terms",
                                    onClick = { showTermsDialog = true },
                                    testTag = "settings_terms_row"
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                ClickableSettingsRow(
                                    icon = Icons.Default.DeleteForever,
                                    title = "Account & Data Deletion",
                                    value = "Permanent",
                                    titleColor = MaterialTheme.colorScheme.error,
                                    onClick = { showDeleteAccountDialog = true },
                                    testTag = "settings_delete_account_row"
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                SettingsRow(icon = Icons.Default.Info, title = "IOMBG Version", value = "v2.4.0 (2026)")

                                if (container.adminRepository.isAuthorizedSuperAdmin(currentUser?.uid)) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    Button(
                                        onClick = onOpenAdmin,
                                        modifier = Modifier.fillMaxWidth().testTag("viewer_open_admin_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = IombgGold),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open Super Admin Console", fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedButton(
                                    onClick = { showLogoutDialog = true },
                                    modifier = Modifier.fillMaxWidth().testTag("viewer_sign_out_button_bottom"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = IombgRed),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sign Out of IOMBG", fontWeight = FontWeight.Bold)
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
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ClickableSettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    testTag: String = "",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (titleColor != MaterialTheme.colorScheme.onSurface) titleColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = titleColor, fontWeight = if (titleColor != MaterialTheme.colorScheme.onSurface) FontWeight.SemiBold else FontWeight.Normal)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

