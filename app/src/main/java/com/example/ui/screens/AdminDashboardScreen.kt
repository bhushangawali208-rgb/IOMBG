package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.data.repository.*
import com.example.di.AppContainer
import com.example.ui.components.MetricStatCard
import com.example.ui.components.formatCount
import com.example.ui.components.formatTimeAgo
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val currentUser by container.authRepository.currentUserState.collectAsState()

    var isVerifying by remember { mutableStateOf(true) }
    var isAuthorized by remember { mutableStateOf(container.adminRepository.isAuthorizedSuperAdmin(currentUser?.uid)) }

    LaunchedEffect(currentUser?.uid) {
        isVerifying = true
        isAuthorized = container.adminRepository.verifySuperAdminAuthorization(currentUser?.uid)
        isVerifying = false
    }

    if (isVerifying) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Security Verification", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("admin_verifying_back_button")) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            modifier = modifier
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = IombgGold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Verifying Super Admin Authorization...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    if (!isAuthorized) {
        // Strict 403 Forbidden Access Denied Screen (No admin data exposed)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Security Verification", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("admin_denied_back_button")) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            modifier = modifier
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.GppBad,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "403 - Access Denied",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Super Admin privileges are strictly restricted to the authorized IOMBG platform controller. Client-side bypass or unverified accounts are prohibited.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Surface(
                            color = Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Current UID: ${currentUser?.uid ?: "Unauthenticated"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Required Role: SUPER_ADMIN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IombgGold)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth().testTag("admin_access_denied_exit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Return to Application", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        return
    }

    // Authorized Super Admin View - Data Subscriptions
    val metrics by container.adminRepository.platformMetrics.collectAsState()
    val users by container.adminRepository.usersList.collectAsState()
    val reports by container.adminRepository.reportsList.collectAsState()
    val fraudAlerts by container.adminRepository.fraudAlerts.collectAsState()
    val pendingApps by container.adminRepository.pendingMonetizationApps.collectAsState()
    val payoutRequests by container.adminRepository.payoutRequests.collectAsState()
    val boostCampaigns by container.adminRepository.boostCampaigns.collectAsState()
    val subscribers by container.adminRepository.premiumSubscribers.collectAsState()
    val auditLogs by container.adminRepository.auditLogs.collectAsState()
    val platformSettings by container.adminRepository.platformSettings.collectAsState()
    val videoQueue by container.adminRepository.videoModerationQueue.collectAsState()
    val liveStreams by container.adminRepository.liveStreamsQueue.collectAsState()
    val messagingFlags by container.adminRepository.messagingFlags.collectAsState()
    val financialLedger by container.adminRepository.financialLedger.collectAsState()
    val announcements by container.adminRepository.adminAnnouncements.collectAsState()

    var selectedAdminSection by remember { mutableIntStateOf(0) }
    var userSearchQuery by remember { mutableStateOf("") }
    var contentSearchQuery by remember { mutableStateOf("") }

    val adminId = currentUser?.uid ?: ""
    val adminEmail = currentUser?.email ?: ""

    // Dialog state for user suspension
    var userToSuspend by remember { mutableStateOf<AdminUserSummary?>(null) }
    var suspensionReasonInput by remember { mutableStateOf("") }

    // Dialog state for broadcast announcement
    var showBroadcastDialog by remember { mutableStateOf(false) }
    var broadcastTitleInput by remember { mutableStateOf("") }
    var broadcastMessageInput by remember { mutableStateOf("") }
    var broadcastAudienceInput by remember { mutableStateOf("ALL_USERS") }

    // Dialog state for manual refund
    var transactionToRefund by remember { mutableStateOf<AdminFinancialLedgerItem?>(null) }
    var refundReasonInput by remember { mutableStateOf("") }

    // ==========================================
    // DIALOGS
    // ==========================================
    if (userToSuspend != null) {
        AlertDialog(
            onDismissRequest = { userToSuspend = null },
            title = { Text("Suspend Account: ${userToSuspend?.displayName}", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Suspending this user will immediately revoke upload, commenting, and live streaming permissions.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = suspensionReasonInput,
                        onValueChange = { suspensionReasonInput = it },
                        label = { Text("Suspension Reason & Violation Reference") },
                        modifier = Modifier.fillMaxWidth().testTag("admin_suspend_reason_input"),
                        singleLine = false,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = userToSuspend ?: return@Button
                        val reason = if (suspensionReasonInput.isNotBlank()) suspensionReasonInput else "Platform policy violation"
                        coroutineScope.launch {
                            container.adminRepository.suspendUser(
                                uid = target.uid,
                                reason = reason,
                                adminId = adminId,
                                adminEmail = adminEmail
                            )
                            userToSuspend = null
                            suspensionReasonInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                    modifier = Modifier.testTag("admin_confirm_suspend_button")
                ) {
                    Text("Suspend Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToSuspend = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBroadcastDialog) {
        AlertDialog(
            onDismissRequest = { showBroadcastDialog = false },
            title = { Text("Broadcast System Announcement", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = broadcastTitleInput,
                        onValueChange = { broadcastTitleInput = it },
                        label = { Text("Announcement Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = broadcastMessageInput,
                        onValueChange = { broadcastMessageInput = it },
                        label = { Text("Announcement Body") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Target Audience:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL_USERS" to "All", "ALL_CREATORS" to "Creators", "MONETIZED" to "Monetized").forEach { (aud, label) ->
                            FilterChip(
                                selected = broadcastAudienceInput == aud,
                                onClick = { broadcastAudienceInput = aud },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (broadcastTitleInput.isNotBlank() && broadcastMessageInput.isNotBlank()) {
                            coroutineScope.launch {
                                container.adminRepository.broadcastAnnouncement(
                                    title = broadcastTitleInput,
                                    message = broadcastMessageInput,
                                    targetAudience = broadcastAudienceInput,
                                    adminId = adminId,
                                    adminEmail = adminEmail
                                )
                                showBroadcastDialog = false
                                broadcastTitleInput = ""
                                broadcastMessageInput = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IombgGold)
                ) {
                    Text("Send Broadcast", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBroadcastDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (transactionToRefund != null) {
        AlertDialog(
            onDismissRequest = { transactionToRefund = null },
            title = { Text("Administrative Refund", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Transaction: ${transactionToRefund?.transactionId} (₹${transactionToRefund?.amountInr})", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = refundReasonInput,
                        onValueChange = { refundReasonInput = it },
                        label = { Text("Reason for Administrative Refund") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tx = transactionToRefund ?: return@Button
                        coroutineScope.launch {
                            container.adminRepository.issueAdministrativeRefund(
                                transactionId = tx.transactionId,
                                reason = refundReasonInput.ifBlank { "Administrative chargeback reversal" },
                                adminId = adminId,
                                adminEmail = adminEmail
                            )
                            transactionToRefund = null
                            refundReasonInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Execute Refund")
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToRefund = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ==========================================
    // MAIN DASHBOARD LAYOUT
    // ==========================================
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(IombgGold.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = IombgGold, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("IOMBG Super Admin", fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Text("Platform Controller Console", fontSize = 11.sp, color = IombgGold)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Surface(
                        color = IombgGold.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "SUPER ADMIN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = IombgGold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
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
            // Horizontal 19-Section Super Admin Navigation Hub
            val allSections = listOf(
                "01. Overview" to Icons.Default.Dashboard,
                "02. Users" to Icons.Default.People,
                "03. Creators" to Icons.Default.VideoCameraFront,
                "04. Videos" to Icons.Default.PlayCircle,
                "05. Shorts" to Icons.Default.ElectricBolt,
                "06. Live Streams" to Icons.Default.LiveTv,
                "07. Reports" to Icons.Default.ReportProblem,
                "08. Moderation" to Icons.Default.Shield,
                "09. Messaging Safety" to Icons.Default.Chat,
                "10. Monetization" to Icons.Default.MonetizationOn,
                "11. Payments & Ledger" to Icons.Default.ReceiptLong,
                "12. Creator Payouts" to Icons.Default.AccountBalance,
                "13. Premium" to Icons.Default.WorkspacePremium,
                "14. Boost" to Icons.Default.RocketLaunch,
                "15. Fraud Monitoring" to Icons.Default.Security,
                "16. Analytics" to Icons.Default.Insights,
                "17. Broadcasts" to Icons.Default.Campaign,
                "18. Audit Logs" to Icons.Default.HistoryEdu,
                "19. Settings" to Icons.Default.Settings
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allSections.forEachIndexed { index, (title, icon) ->
                    val isSelected = selectedAdminSection == index
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedAdminSection = index },
                        label = { Text(title, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IombgRed,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // Main Content Area
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedAdminSection) {
                    0 -> {
                        // ======================== 01. OVERVIEW ========================
                        item {
                            Text("Executive KPI Dashboard", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Real-time aggregate platform health & double-entry financials.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricStatCard(title = "Total Users", value = formatCount(metrics.totalUsers), icon = Icons.Default.People, subtitle = "+14.2% MoM", modifier = Modifier.weight(1f))
                                MetricStatCard(title = "Creators", value = formatCount(metrics.totalCreators), icon = Icons.Default.VideoCameraFront, subtitle = "+8.9%", modifier = Modifier.weight(1f))
                            }
                        }

                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricStatCard(title = "Total Views", value = formatCount(metrics.totalViews), icon = Icons.Default.PlayCircle, subtitle = "+24.5%", modifier = Modifier.weight(1f))
                                MetricStatCard(title = "Watch Hours", value = "${formatCount(metrics.totalWatchHours.toLong())} hrs", icon = Icons.Default.Schedule, subtitle = "Total", modifier = Modifier.weight(1f))
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Financial Analytics", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Financial analytics are currently disabled.", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Platform revenue, creator pool settlements, and double-entry ledgers will be activated when the production monetization and payment gateway are launched.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    1 -> {
                        // ======================== 02. USERS ========================
                        item {
                            Text("User Directory & Access Control", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = userSearchQuery,
                                onValueChange = { userSearchQuery = it },
                                placeholder = { Text("Search by name, email, or UID...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        val filteredUsers = users.filter {
                            it.displayName.contains(userSearchQuery, ignoreCase = true) ||
                            it.email.contains(userSearchQuery, ignoreCase = true) ||
                            it.uid.contains(userSearchQuery, ignoreCase = true)
                        }

                        items(filteredUsers) { user ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = user.photoUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                if (user.isCreator) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(color = IombgGold.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                                        Text("CREATOR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = IombgGold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                    }
                                                }
                                            }
                                            Text(user.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("UID: ${user.uid}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
                                        }

                                        Surface(
                                            color = if (user.status == AccountModerationStatus.ACTIVE) StatusSuccess.copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = user.status.name,
                                                color = if (user.status == AccountModerationStatus.ACTIVE) StatusSuccess else MaterialTheme.colorScheme.error,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        if (user.status == AccountModerationStatus.ACTIVE) {
                                            OutlinedButton(
                                                onClick = { userToSuspend = user },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text("Suspend", fontSize = 11.sp)
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        container.adminRepository.restoreUser(user.uid, adminId, adminEmail)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text("Restore", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // ======================== 03. CREATORS & CHANNELS ========================
                        item {
                            Text("Creator Channels Management", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Monitor channel health, audience scale, and monetization status.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        val creatorUsers = users.filter { it.isCreator }
                        items(creatorUsers) { creator ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = creator.photoUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(44.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(creator.channelName ?: creator.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("${formatCount(creator.subscriberCount)} Followers • ${creator.totalVideos} Videos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Surface(
                                            color = if (creator.isMonetized) IombgGold.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (creator.isMonetized) "YPP MONETIZED" else "STANDARD",
                                                color = if (creator.isMonetized) IombgGold else Color.White.copy(alpha = 0.6f),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        if (creator.isMonetized) {
                                            OutlinedButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        container.adminRepository.suspendChannelMonetization(creator.channelId ?: creator.uid, "Admin policy review", adminId, adminEmail)
                                                    }
                                                },
                                                modifier = Modifier.height(34.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Revoke Monetization", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        container.adminRepository.restoreChannelMonetization(creator.channelId ?: creator.uid, adminId, adminEmail)
                                                    }
                                                },
                                                modifier = Modifier.height(34.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Grant Monetization", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // ======================== 04. VIDEOS ========================
                        item {
                            Text("Long-Form Video Moderation Queue", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        val longVideos = videoQueue.filter { !it.isShort }
                        items(longVideos) { video ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row {
                                        AsyncImage(
                                            model = video.thumbnailUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(width = 110.dp, height = 65.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(video.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text("${video.channelName} • ${formatCount(video.viewCount)} views", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Surface(
                                                color = when (video.status) {
                                                    AdminModerationStatus.APPROVED -> StatusSuccess.copy(alpha = 0.2f)
                                                    AdminModerationStatus.FLAGGED -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                                    AdminModerationStatus.RESTRICTED -> IombgGold.copy(alpha = 0.2f)
                                                    AdminModerationStatus.REMOVED -> MaterialTheme.colorScheme.error
                                                },
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                Text(video.status.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    container.adminRepository.moderateContent(video.contentId, false, AdminModerationStatus.REMOVED, "Community guidelines violation", adminId, adminEmail)
                                                }
                                            },
                                            modifier = Modifier.height(34.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Remove", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    container.adminRepository.moderateContent(video.contentId, false, AdminModerationStatus.APPROVED, "Approved by admin", adminId, adminEmail)
                                                }
                                            },
                                            modifier = Modifier.height(34.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Approve", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    4 -> {
                        // ======================== 05. SHORTS ========================
                        item {
                            Text("Shorts Moderation Queue", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        val shortsOnly = videoQueue.filter { it.isShort }
                        items(shortsOnly) { short ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = short.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(width = 60.dp, height = 90.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(short.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2)
                                        Text("${short.channelName} • ${formatCount(short.viewCount)} views", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (short.flagReason != null) {
                                            Text("Flag: ${short.flagReason}", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                container.adminRepository.moderateContent(short.contentId, true, AdminModerationStatus.APPROVED, "Shorts verified safe", adminId, adminEmail)
                                            }
                                        },
                                        modifier = Modifier.height(34.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Approve", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    5 -> {
                        // ======================== 06. LIVE STREAMS ========================
                        item {
                            Text("Active Broadcasts & Live Stream Control", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        items(liveStreams) { stream ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stream.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Surface(color = if (stream.isRestricted) MaterialTheme.colorScheme.error else IombgRed, shape = RoundedCornerShape(4.dp)) {
                                            Text(if (stream.isRestricted) "RESTRICTED" else "LIVE • ${stream.viewerCount}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Channel: ${stream.channelName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        if (!stream.isRestricted) {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        container.adminRepository.moderateLiveStream(stream.streamId, true, "Admin intervention", adminId, adminEmail)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text("Terminate / Restrict Stream", fontSize = 11.sp)
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        container.adminRepository.moderateLiveStream(stream.streamId, false, "Stream reinstated", adminId, adminEmail)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text("Restore Stream", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    6 -> {
                        // ======================== 07. REPORTS ========================
                        item {
                            Text("User Reports & Violation Triage", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        items(reports) { report ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                            Text(report.reasonEnum.name, color = MaterialTheme.colorScheme.error, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                        Text(formatTimeAgo(report.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(report.reason, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(report.details, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    container.adminRepository.reviewReport(report.reportId, ReportStatus.DISMISSED, "Dismissed as non-violating", adminId, adminEmail)
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("Dismiss", fontSize = 11.sp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    container.adminRepository.reviewReport(report.reportId, ReportStatus.RESOLVED, "Violation confirmed and mitigated", adminId, adminEmail)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("Resolve & Enforce", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    7 -> {
                        // ======================== 08. MODERATION CENTER ========================
                        item {
                            Text("Platform Moderation Center & Strikes Policy", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Automatic 3-strike policy on copyright, harassment, and fraud.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Enforcement Protocols Active", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("• Strike 1: 7-day upload and comment suspension", fontSize = 12.sp)
                                    Text("• Strike 2: 14-day channel lockout + monetization freeze", fontSize = 12.sp)
                                    Text("• Strike 3: Permanent device and KYC termination", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    8 -> {
                        // ======================== 09. MESSAGING SAFETY ========================
                        item {
                            Text("Direct Messaging Safety & Anti-Spam", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        items(messagingFlags) { flag ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("From: ${flag.senderName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Surface(color = IombgRed.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                            Text(flag.riskCategory, fontSize = 10.sp, color = IombgRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Snippet: \"${flag.messageSnippet}\"", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    container.adminRepository.resolveMessagingFlag(flag.flagId, true, "Sender messaging restricted", adminId, adminEmail)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("Restrict Sender Messaging", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    9 -> {
                        // ======================== 10. MONETIZATION (YPP) ========================
                        item {
                            Text("YouTube Partner Program (YPP) Applications", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Criteria: 500 Subs + 500 Watch Hours OR 100,000 Shorts Views.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        items(pendingApps) { app ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(app.channelName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${formatCount(app.subscriberCount)} Followers • ${String.format("%,.0f", app.eligibleWatchHoursLast12M)} Watch Hours • ${formatCount(app.eligibleShortsViewsLast90D)} Shorts Views", fontSize = 12.sp, color = IombgGold)
                                    Text("Legal Name: ${app.legalName} • PAN: ${app.panNumber}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    container.adminRepository.reviewMonetizationApp(app.applicationId, adminId, adminEmail, false, "Incomplete documentation")
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("Reject", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    container.adminRepository.reviewMonetizationApp(app.applicationId, adminId, adminEmail, true)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("Approve Creator", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    10 -> {
                        // ======================== 11. PAYMENTS & LEDGER ========================
                        item {
                            Text("Double-Entry Financial Transactions Ledger", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Financial analytics are currently disabled.", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("No transaction logs available while financial systems are inactive.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    11 -> {
                        // ======================== 12. CREATOR PAYOUTS ========================
                        item {
                            Text("Pending Payout Settlements", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No payout requests pending.", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Creator payouts are currently disabled.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    12 -> {
                        // ======================== 13. PREMIUM ========================
                        item {
                            Text("IOMBG Premium Membership Program", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Active Subscribers: ${formatCount(metrics.premiumSubscribers)}", fontSize = 12.sp, color = IombgGold)
                        }

                        items(subscribers) { sub ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(sub.userName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(sub.userEmail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("PREMIUM", fontWeight = FontWeight.Bold, color = IombgGold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    13 -> {
                        // ======================== 14. BOOST ========================
                        item {
                            Text("Promoted Content & Boost Campaigns", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        items(boostCampaigns) { camp ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(camp.contentTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Budget: ₹${camp.budgetAmount.toInt()} • Delivered: ${formatCount(camp.deliveredImpressions)} Imp • ${formatCount(camp.generatedViews)} Views", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        if (camp.status == BoostStatus.ACTIVE) {
                                            OutlinedButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        container.adminRepository.pauseBoostCampaign(camp.campaignId, "Admin paused", adminId, adminEmail)
                                                    }
                                                },
                                                modifier = Modifier.height(34.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Pause", fontSize = 11.sp)
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        container.adminRepository.resumeBoostCampaign(camp.campaignId, adminId, adminEmail)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                                                modifier = Modifier.height(34.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Resume", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    14 -> {
                        // ======================== 15. FRAUD MONITORING ========================
                        item {
                            Text("AI Fraud Detection & Telemetry Alerts", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        items(fraudAlerts) { alert ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Surface(color = if (alert.riskLevel == FraudRiskLevel.CRITICAL) MaterialTheme.colorScheme.error else IombgGold, shape = RoundedCornerShape(4.dp)) {
                                            Text("${alert.riskLevel.name} • ${(alert.score * 100).toInt()}% RISK", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                        Text(alert.category.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(alert.signalType.replace("_", " "), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(alert.evidenceSummary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    container.adminRepository.resolveFraudAlert(alert.alertId, "Mitigation applied", adminId, adminEmail)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("Apply Mitigation", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    15 -> {
                        // ======================== 16. PLATFORM ANALYTICS ========================
                        item {
                            Text("Deep Platform Telemetry & Analytics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Traffic & CDN Delivery", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("• Cloud CDN Cache Hit Ratio: 94.6%", fontSize = 12.sp)
                                    Text("• Edge Egress Latency: 18ms (Mumbai, IN)", fontSize = 12.sp)
                                    Text("• Peak Concurrent Streams: 4,820", fontSize = 12.sp)
                                    Text("• Storage Footprint: 2.4 TB (Transcoded HLS)", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    16 -> {
                        // ======================== 17. BROADCASTS ========================
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("System Announcements", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Button(
                                    onClick = { showBroadcastDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = IombgGold),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("New Broadcast", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }

                        items(announcements) { anc ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(anc.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(formatTimeAgo(anc.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(anc.message, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Audience: ${anc.targetAudience} • Sent By: ${anc.sentByAdmin}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }

                    17 -> {
                        // ======================== 18. AUDIT LOGS ========================
                        item {
                            Text("Immutable Audit Log (${auditLogs.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        items(auditLogs) { log ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(log.action, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = IombgGold)
                                        Text(formatTimeAgo(log.timestamp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Target: ${log.targetType} (${log.targetId})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Reason: ${log.reason}", fontSize = 11.sp)
                                    Text("Admin: ${log.adminEmail}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }

                    18 -> {
                        // ======================== 19. SETTINGS ========================
                        item {
                            Text("Global Platform Configurations", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Monetization & Payout Rules", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("• Support/Thanks Split: 70% Creator / 30% Platform", fontSize = 12.sp)
                                    Text("• Video Ad Revenue Split: 55% Creator / 45% Platform", fontSize = 12.sp)
                                    Text("• Minimum Payout Threshold: ₹5,000 INR", fontSize = 12.sp)
                                    Text("• Monetization Milestone: 500 Subs + 500 Watch Hours OR 100k Shorts Views", fontSize = 12.sp)
                                    Text("• Auto-Spam Filter: BALANCED (30 msgs/min rate limit)", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
