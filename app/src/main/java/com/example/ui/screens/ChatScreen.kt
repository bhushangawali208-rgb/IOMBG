package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.di.AppContainer
import com.example.ui.components.formatTimeAgo
import com.example.ui.theme.*
import kotlinx.coroutines.launch

private enum class ChatViewTab {
    ALL,
    UNREAD,
    PINNED,
    REQUESTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // State from Repositories
    val currentUser by container.authRepository.currentUserState.collectAsState()
    val activeChatUid by container.chatRepository.activeUserUid.collectAsState()
    val effectiveUid = currentUser?.uid ?: activeChatUid
    val conversations by container.chatRepository.conversations.collectAsState()
    val messageRequests by container.chatRepository.messageRequests.collectAsState()
    val currentMessages by container.chatRepository.currentMessages.collectAsState()
    val activeConversationId by container.chatRepository.activeConversationId.collectAsState()
    val userPresences by container.chatRepository.userPresences.collectAsState()
    val blockedUsers by container.chatRepository.blockedUsers.collectAsState()
    val chatSettings by container.chatRepository.chatSettings.collectAsState()
    val allChannels by container.channelRepository.popularChannels.collectAsState()

    // Local UI State
    var selectedTab by remember { mutableStateOf(ChatViewTab.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var messageInput by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var actionMessageTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var showNewMessageDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var reportingTarget by remember { mutableStateOf<Triple<String?, String, String>?>(null) } // msgId?, convId, reportedUid
    var blockingTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // targetUid, targetName
    var errorMessageBanner by remember { mutableStateOf<String?>(null) }
    var infoToastMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Find the currently active conversation object
    val activeConversation = remember(conversations, messageRequests, activeConversationId) {
        conversations.firstOrNull { it.conversationId == activeConversationId }
            ?: messageRequests.firstOrNull { it.conversationId == activeConversationId }
    }

    // Identify other participant in active conversation
    val otherParticipantUid = remember(activeConversation, effectiveUid) {
        activeConversation?.participantUids?.firstOrNull { it != effectiveUid } ?: ""
    }
    val otherParticipantName = remember(activeConversation, otherParticipantUid) {
        activeConversation?.participantNames?.get(otherParticipantUid) ?: "Creator"
    }
    val otherParticipantHandle = remember(activeConversation, otherParticipantUid) {
        activeConversation?.participantHandles?.get(otherParticipantUid) ?: "@creator"
    }
    val otherParticipantAvatar = remember(activeConversation, otherParticipantUid) {
        activeConversation?.participantAvatars?.get(otherParticipantUid)
            ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200"
    }
    val otherPresence = remember(userPresences, otherParticipantUid) {
        userPresences[otherParticipantUid]
    }

    // Auto-scroll to latest message when new messages arrive
    LaunchedEffect(currentMessages.size) {
        if (currentMessages.isNotEmpty()) {
            listState.animateScrollToItem(currentMessages.size - 1)
        }
    }

    // Dismiss error banner after 4 seconds
    LaunchedEffect(errorMessageBanner) {
        if (errorMessageBanner != null) {
            kotlinx.coroutines.delay(4000)
            errorMessageBanner = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (activeConversation == null) {
                // ==================== CONVERSATIONS LIST TOP BAR ====================
                Column(modifier = Modifier.background(DarkBackground)) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Messages",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 20.sp
                                )
                                if (messageRequests.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = IombgRed,
                                        shape = CircleShape,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "${messageRequests.size}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showSettingsSheet = true },
                                modifier = Modifier.testTag("chat_settings_button")
                            ) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Chat Settings", tint = DarkTextSecondary)
                            }
                            IconButton(
                                onClick = { showNewMessageDialog = true },
                                modifier = Modifier.testTag("new_message_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "New Message", tint = IombgRed)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
                    )

                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search conversations & creators...", fontSize = 13.sp, color = DarkTextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = DarkTextSecondary, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = DarkTextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("search_conversations_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated,
                            focusedBorderColor = IombgRed.copy(alpha = 0.5f),
                            unfocusedBorderColor = DarkBorderSubtle
                        ),
                        singleLine = true
                    )

                    // Filter tabs row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedTab == ChatViewTab.ALL,
                            onClick = { selectedTab = ChatViewTab.ALL },
                            label = { Text("All (${conversations.size})", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IombgRed.copy(alpha = 0.2f),
                                selectedLabelColor = IombgRed
                            )
                        )
                        FilterChip(
                            selected = selectedTab == ChatViewTab.UNREAD,
                            onClick = { selectedTab = ChatViewTab.UNREAD },
                            label = { Text("Unread", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IombgRed.copy(alpha = 0.2f),
                                selectedLabelColor = IombgRed
                            )
                        )
                        FilterChip(
                            selected = selectedTab == ChatViewTab.REQUESTS,
                            onClick = { selectedTab = ChatViewTab.REQUESTS },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Requests", fontSize = 12.sp)
                                    if (messageRequests.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            color = IombgRed,
                                            shape = CircleShape,
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    "${messageRequests.size}",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IombgRed.copy(alpha = 0.2f),
                                selectedLabelColor = IombgRed
                            )
                        )
                        FilterChip(
                            selected = selectedTab == ChatViewTab.PINNED,
                            onClick = { selectedTab = ChatViewTab.PINNED },
                            label = { Text("Pinned", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IombgRed.copy(alpha = 0.2f),
                                selectedLabelColor = IombgRed
                            )
                        )
                    }
                }
            } else {
                // ==================== ACTIVE CHAT TOP BAR ====================
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                // View channel preview
                            }
                        ) {
                            Box {
                                AsyncImage(
                                    model = otherParticipantAvatar,
                                    contentDescription = otherParticipantName,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                )
                                if (otherPresence?.isOnline == true && chatSettings.showOnlineStatus) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(10.dp)
                                            .background(IombgGreen, CircleShape)
                                            .border(1.5.dp, DarkBackground, CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        otherParticipantName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Verified,
                                        contentDescription = "Verified",
                                        tint = IombgRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = if (otherPresence?.isOnline == true && chatSettings.showOnlineStatus) {
                                        otherPresence?.statusText?.ifBlank { "Online" } ?: "Online"
                                    } else {
                                        "Active ${formatTimeAgo(otherPresence?.lastActiveAt ?: activeConversation.lastMessageTimestamp)}"
                                    },
                                    fontSize = 11.sp,
                                    color = if (otherPresence?.isOnline == true) IombgGreen else DarkTextSecondary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { container.chatRepository.selectConversation(null) },
                            modifier = Modifier.testTag("chat_detail_back_button")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        var showChatMenu by remember { mutableStateOf(false) }

                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Voice & Video calls coming soon in IOMBG v2.5")
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Call, contentDescription = "Call", tint = DarkTextSecondary)
                        }

                        Box {
                            IconButton(onClick = { showChatMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showChatMenu,
                                onDismissRequest = { showChatMenu = false },
                                modifier = Modifier.background(DarkSurfaceElevated)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Report Conversation", color = StatusError) },
                                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = StatusError) },
                                    onClick = {
                                        showChatMenu = false
                                        reportingTarget = Triple(null, activeConversation.conversationId, otherParticipantUid)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Block User", color = StatusError) },
                                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = StatusError) },
                                    onClick = {
                                        showChatMenu = false
                                        blockingTarget = Pair(otherParticipantUid, otherParticipantName)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Chat Settings", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null, tint = DarkTextSecondary) },
                                    onClick = {
                                        showChatMenu = false
                                        showSettingsSheet = true
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
                )
            }
        },
        modifier = modifier.fillMaxSize().background(DarkBackground)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (activeConversation == null) {
                // ==================== CONVERSATIONS LIST / REQUESTS VIEW ====================
                when (selectedTab) {
                    ChatViewTab.REQUESTS -> {
                        MessageRequestsList(
                            requests = messageRequests,
                            onAccept = { req ->
                                coroutineScope.launch {
                                    val res = container.chatRepository.acceptMessageRequest(req.conversationId, currentUser)
                                    if (res.isSuccess) {
                                        snackbarHostState.showSnackbar("Message request accepted")
                                        container.chatRepository.selectConversation(req.conversationId)
                                    }
                                }
                            },
                            onDecline = { req ->
                                coroutineScope.launch {
                                    container.chatRepository.declineMessageRequest(req.conversationId, currentUser)
                                    snackbarHostState.showSnackbar("Request declined")
                                }
                            },
                            onBlock = { req ->
                                val targetUid = req.participantUids.firstOrNull { it != effectiveUid } ?: ""
                                blockingTarget = Pair(targetUid, req.participantNames[targetUid] ?: "User")
                            },
                            onReport = { req ->
                                val targetUid = req.participantUids.firstOrNull { it != effectiveUid } ?: ""
                                reportingTarget = Triple(null, req.conversationId, targetUid)
                            },
                            onOpenSettings = { showSettingsSheet = true }
                        )
                    }
                    else -> {
                        // Filter conversations based on tab and search query
                        val filteredConversations = conversations.filter { conv ->
                            val matchesSearch = if (searchQuery.isBlank()) true else {
                                val otherName = conv.participantNames.values.firstOrNull { it != currentUser?.displayName } ?: ""
                                val otherHandle = conv.participantHandles.values.firstOrNull() ?: ""
                                otherName.contains(searchQuery, ignoreCase = true) ||
                                        otherHandle.contains(searchQuery, ignoreCase = true) ||
                                        conv.lastMessageText.contains(searchQuery, ignoreCase = true)
                            }
                            val matchesTab = when (selectedTab) {
                                ChatViewTab.ALL -> true
                                ChatViewTab.UNREAD -> (conv.unreadCounts[effectiveUid] ?: 0) > 0
                                ChatViewTab.PINNED -> conv.isPinned
                                ChatViewTab.REQUESTS -> false
                            }
                            matchesSearch && matchesTab
                        }

                        if (filteredConversations.isEmpty()) {
                            EmptyConversationsState(
                                searchQuery = searchQuery,
                                onNewChatClick = { showNewMessageDialog = true }
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("conversations_list"),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredConversations, key = { it.conversationId }) { conv ->
                                    val otherUid = conv.participantUids.firstOrNull { it != effectiveUid } ?: ""
                                    val otherName = conv.participantNames[otherUid] ?: "Creator"
                                    val otherHandle = conv.participantHandles[otherUid] ?: "@creator"
                                    val otherAvatar = conv.participantAvatars[otherUid] ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200"
                                    val unreadCount = conv.unreadCounts[effectiveUid] ?: 0
                                    val presence = userPresences[otherUid]

                                    ConversationCard(
                                        conversation = conv,
                                        otherName = otherName,
                                        otherHandle = otherHandle,
                                        otherAvatar = otherAvatar,
                                        unreadCount = unreadCount,
                                        isOnline = presence?.isOnline == true && chatSettings.showOnlineStatus,
                                        onClick = {
                                            container.chatRepository.selectConversation(conv.conversationId, effectiveUid)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ==================== ACTIVE CONVERSATION MESSAGES STREAM ====================
                val isBlockedByMe = blockedUsers.contains(otherParticipantUid)

                Column(modifier = Modifier.fillMaxSize()) {
                    // Error Banner if rate limited or failed
                    errorMessageBanner?.let { err ->
                        Surface(
                            color = StatusError.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusError, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(err, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // Messages Stream List
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .testTag("chat_messages_stream"),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        item {
                            // Conversation Header Info Card
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = otherParticipantAvatar,
                                    contentDescription = otherParticipantName,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, IombgRed.copy(alpha = 0.5f), CircleShape)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(otherParticipantName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                Text(otherParticipantHandle, fontSize = 12.sp, color = DarkTextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Messages are end-to-end encrypted in production. IOMBG Community Guidelines apply.",
                                    fontSize = 10.sp,
                                    color = DarkTextMuted,
                                    modifier = Modifier.padding(horizontal = 32.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }

                        items(currentMessages, key = { it.messageId }) { msg ->
                            val isMe = msg.senderUid == effectiveUid
                            MessageBubble(
                                message = msg,
                                isMe = isMe,
                                onLongClick = { actionMessageTarget = msg },
                                onReplyClick = { replyingToMessage = msg }
                            )
                        }
                    }

                    // Replying to banner
                    replyingToMessage?.let { replyTarget ->
                        Surface(
                            color = DarkSurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IombgRed.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .background(IombgRed, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Replying to ${replyTarget.senderName}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IombgRed
                                    )
                                    Text(
                                        replyTarget.text,
                                        fontSize = 11.sp,
                                        color = DarkTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { replyingToMessage = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel reply", tint = DarkTextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Input Bar or Blocked Notice
                    if (isBlockedByMe) {
                        Surface(
                            color = DarkSurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .navigationBarsPadding(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("You have blocked this user.", color = DarkTextSecondary, fontSize = 13.sp)
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            container.chatRepository.unblockUser(otherParticipantUid, currentUser)
                                            snackbarHostState.showSnackbar("User unblocked")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Unblock", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        // Regular Message Input Bar
                        Surface(
                            color = DarkSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Attachment hint button
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Media & File attachments sandbox enabled")
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Outlined.AttachFile, contentDescription = "Attach", tint = DarkTextSecondary)
                                }

                                OutlinedTextField(
                                    value = messageInput,
                                    onValueChange = { messageInput = it },
                                    placeholder = { Text("Write a message...", fontSize = 13.sp, color = DarkTextMuted) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp)
                                        .testTag("chat_message_input"),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = DarkSurfaceElevated,
                                        unfocusedContainerColor = DarkSurfaceElevated,
                                        focusedBorderColor = IombgRed.copy(alpha = 0.5f),
                                        unfocusedBorderColor = DarkBorderSubtle
                                    ),
                                    maxLines = 4
                                )

                                Spacer(modifier = Modifier.width(4.dp))

                                // Send Button
                                IconButton(
                                    onClick = {
                                        if (messageInput.isNotBlank()) {
                                            val textToSend = messageInput
                                            val replyTarget = replyingToMessage
                                            messageInput = ""
                                            replyingToMessage = null

                                            coroutineScope.launch {
                                                val res = container.chatRepository.sendMessage(
                                                    conversationId = activeConversation.conversationId,
                                                    receiverUid = otherParticipantUid,
                                                    text = textToSend,
                                                    replyToMessageId = replyTarget?.messageId,
                                                    replyToSnippet = replyTarget?.text?.take(50),
                                                    replyToSenderName = replyTarget?.senderName,
                                                    currentUser = currentUser
                                                )
                                                if (res.isFailure) {
                                                    errorMessageBanner = res.exceptionOrNull()?.message ?: "Failed to send message"
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            brush = Brush.linearGradient(listOf(IombgRed, IombgCoral)),
                                            shape = CircleShape
                                        )
                                        .testTag("send_chat_message_button"),
                                    enabled = messageInput.isNotBlank()
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== MESSAGE ACTION BOTTOM SHEET ====================
    actionMessageTarget?.let { targetMsg ->
        val isMe = targetMsg.senderUid == effectiveUid
        ModalBottomSheet(
            onDismissRequest = { actionMessageTarget = null },
            containerColor = DarkSurfaceElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    "Message Options",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Reply
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            replyingToMessage = targetMsg
                            actionMessageTarget = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Reply, contentDescription = null, tint = IombgRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Reply to message", color = Color.White, fontSize = 14.sp)
                }

                // Copy
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboardManager.setText(AnnotatedString(targetMsg.text))
                            actionMessageTarget = null
                            coroutineScope.launch { snackbarHostState.showSnackbar("Message copied to clipboard") }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Copy text", color = Color.White, fontSize = 14.sp)
                }

                // Delete (Own message only)
                if (isMe && targetMsg.moderationStatus != MessageModerationStatus.DELETED) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val convId = activeConversation?.conversationId ?: ""
                                coroutineScope.launch {
                                    container.chatRepository.deleteOwnMessage(targetMsg.messageId, convId, currentUser)
                                    snackbarHostState.showSnackbar("Message deleted")
                                }
                                actionMessageTarget = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = StatusError, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Delete message", color = StatusError, fontSize = 14.sp)
                    }
                }

                // Report (Other user message only)
                if (!isMe) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val convId = activeConversation?.conversationId ?: ""
                                reportingTarget = Triple(targetMsg.messageId, convId, targetMsg.senderUid)
                                actionMessageTarget = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, tint = StatusError, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Report message", color = StatusError, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // ==================== NEW MESSAGE DIALOG ====================
    if (showNewMessageDialog) {
        NewMessageModal(
            channels = allChannels,
            onDismiss = { showNewMessageDialog = false },
            onSelectCreator = { ch ->
                showNewMessageDialog = false
                coroutineScope.launch {
                    val res = container.chatRepository.startNewConversation(
                        targetUid = ch.ownerUid,
                        targetName = ch.channelName,
                        targetHandle = ch.handle,
                        targetAvatar = ch.profileImageUrl,
                        firstMessageText = "",
                        isFollowed = true,
                        currentUser = currentUser
                    )
                    if (res.isSuccess) {
                        val conv = res.getOrNull()
                        if (conv != null) {
                            container.chatRepository.selectConversation(conv.conversationId, effectiveUid)
                        }
                    }
                }
            }
        )
    }

    // ==================== CHAT SETTINGS SHEET ====================
    if (showSettingsSheet) {
        ChatSettingsBottomSheet(
            currentSettings = chatSettings,
            blockedUsers = blockedUsers,
            onDismiss = { showSettingsSheet = false },
            onSaveSettings = { updated ->
                coroutineScope.launch {
                    container.chatRepository.updateChatSettings(updated, currentUser)
                    snackbarHostState.showSnackbar("Chat settings updated")
                    showSettingsSheet = false
                }
            },
            onUnblockUser = { uid ->
                coroutineScope.launch {
                    container.chatRepository.unblockUser(uid, currentUser)
                    snackbarHostState.showSnackbar("User unblocked")
                }
            }
        )
    }

    // ==================== REPORT DIALOG ====================
    reportingTarget?.let { (msgId, convId, reportedUid) ->
        ReportChatDialog(
            onDismiss = { reportingTarget = null },
            onSubmit = { category, reason, details ->
                coroutineScope.launch {
                    container.chatRepository.reportMessage(
                        messageId = msgId,
                        conversationId = convId,
                        reportedUid = reportedUid,
                        category = category,
                        reason = reason,
                        details = details,
                        currentUser = currentUser
                    )
                    snackbarHostState.showSnackbar("Thank you. Report received and sent to IOMBG Trust & Safety.")
                    reportingTarget = null
                }
            }
        )
    }

    // ==================== BLOCK USER DIALOG ====================
    blockingTarget?.let { (targetUid, targetName) ->
        AlertDialog(
            onDismissRequest = { blockingTarget = null },
            title = { Text("Block $targetName?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "$targetName will no longer be able to message you, and their conversations will be hidden.",
                    color = DarkTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            container.chatRepository.blockUser(targetUid, targetName, currentUser)
                            snackbarHostState.showSnackbar("Blocked $targetName")
                            blockingTarget = null
                            container.chatRepository.selectConversation(null)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Block", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { blockingTarget = null }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}

// ==============================================================================
// SUB-COMPONENTS & VIEWS
// ==============================================================================

@Composable
private fun ConversationCard(
    conversation: ChatConversation,
    otherName: String,
    otherHandle: String,
    otherAvatar: String,
    unreadCount: Int,
    isOnline: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("conversation_card_${conversation.conversationId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unreadCount > 0) DarkSurfaceElevated else DarkSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (conversation.isPinned) IombgGold.copy(alpha = 0.4f) else DarkBorderSubtle
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model = otherAvatar,
                    contentDescription = otherName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                            .background(IombgGreen, CircleShape)
                            .border(2.dp, DarkSurface, CircleShape)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = otherName,
                            fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        if (conversation.isPinned) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = IombgGold,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Text(
                        text = formatTimeAgo(conversation.lastMessageTimestamp),
                        fontSize = 11.sp,
                        color = if (unreadCount > 0) IombgRed else DarkTextMuted
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.lastMessageText,
                        fontSize = 12.sp,
                        color = if (unreadCount > 0) Color.White else DarkTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = IombgRed,
                            shape = CircleShape,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$unreadCount",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
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
private fun MessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    onLongClick: () -> Unit,
    onReplyClick: () -> Unit
) {
    val isDeleted = message.moderationStatus == MessageModerationStatus.DELETED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            color = if (isDeleted) {
                DarkSurfaceElevated.copy(alpha = 0.5f)
            } else if (isMe) {
                IombgRed
            } else {
                DarkSurfaceElevated
            },
            border = if (!isMe) androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle) else null,
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clickable(onClick = onLongClick)
                .testTag("chat_message_${message.messageId}")
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Quoted reply snippet inside bubble
                if (message.replyToSnippet != null && !isDeleted) {
                    Surface(
                        color = if (isMe) Color.Black.copy(alpha = 0.25f) else DarkBackground.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(24.dp)
                                    .background(if (isMe) Color.White else IombgRed, RoundedCornerShape(1.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    message.replyToSenderName ?: "User",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMe) Color.White.copy(alpha = 0.9f) else IombgRed
                                )
                                Text(
                                    message.replyToSnippet,
                                    fontSize = 10.sp,
                                    color = if (isMe) Color.White.copy(alpha = 0.7f) else DarkTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Message Body Text
                Text(
                    text = message.text,
                    color = if (isDeleted) {
                        DarkTextMuted
                    } else if (isMe) {
                        Color.White
                    } else {
                        DarkTextPrimary
                    },
                    fontSize = 13.sp,
                    fontStyle = if (isDeleted) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Timestamp & Delivery status ticks
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimeAgo(message.createdAt),
                        color = if (isMe) Color.White.copy(alpha = 0.7f) else DarkTextMuted,
                        fontSize = 9.sp
                    )

                    if (isMe && !isDeleted) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.deliveryStatus) {
                            MessageDeliveryStatus.SENDING -> {
                                Icon(Icons.Default.Schedule, contentDescription = "Sending", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(11.dp))
                            }
                            MessageDeliveryStatus.SENT -> {
                                Icon(Icons.Default.Check, contentDescription = "Sent", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(11.dp))
                            }
                            MessageDeliveryStatus.DELIVERED -> {
                                Icon(Icons.Default.DoneAll, contentDescription = "Delivered", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                            }
                            MessageDeliveryStatus.READ -> {
                                Icon(Icons.Default.DoneAll, contentDescription = "Read", tint = IombgGold, modifier = Modifier.size(12.dp))
                            }
                            MessageDeliveryStatus.FAILED -> {
                                Icon(Icons.Default.Error, contentDescription = "Failed", tint = StatusError, modifier = Modifier.size(11.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageRequestsList(
    requests: List<ChatConversation>,
    onAccept: (ChatConversation) -> Unit,
    onDecline: (ChatConversation) -> Unit,
    onBlock: (ChatConversation) -> Unit,
    onReport: (ChatConversation) -> Unit,
    onOpenSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, IombgRed.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = IombgRed, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Message Requests Protection", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        Text(
                            "Incoming messages from accounts you don't follow. Senders won't know you've viewed them until you accept.",
                            fontSize = 11.sp,
                            color = DarkTextSecondary
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = DarkTextSecondary)
                    }
                }
            }
        }

        if (requests.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.MarkChatRead, contentDescription = null, tint = DarkTextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No pending message requests", color = DarkTextSecondary, fontSize = 14.sp)
                    }
                }
            }
        } else {
            items(requests, key = { it.conversationId }) { req ->
                val senderUid = req.lastMessageSenderUid
                val senderName = req.participantNames[senderUid] ?: "Unknown User"
                val senderHandle = req.participantHandles[senderUid] ?: "@user"
                val senderAvatar = req.participantAvatars[senderUid] ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = senderAvatar,
                                contentDescription = senderName,
                                modifier = Modifier.size(44.dp).clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(senderName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                Text(senderHandle, fontSize = 11.sp, color = DarkTextSecondary)
                            }
                            Text(formatTimeAgo(req.lastMessageTimestamp), fontSize = 10.sp, color = DarkTextMuted)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = DarkBackground,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = req.lastMessageText,
                                fontSize = 12.sp,
                                color = DarkTextPrimary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onAccept(req) },
                                colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("Accept", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { onDecline(req) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextSecondary)
                            ) {
                                Text("Decline", fontSize = 12.sp)
                            }

                            IconButton(
                                onClick = { onBlock(req) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Block, contentDescription = "Block", tint = StatusError)
                            }

                            IconButton(
                                onClick = { onReport(req) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Flag, contentDescription = "Report", tint = DarkTextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationsState(
    searchQuery: String,
    onNewChatClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                color = IombgRed.copy(alpha = 0.12f),
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Forum,
                        contentDescription = null,
                        tint = IombgRed,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (searchQuery.isNotBlank()) "No conversations found" else "Connect with Creators",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (searchQuery.isNotBlank()) "Try searching for a different creator or handle." else "Send direct messages, collaborate with directors, and connect with other creators across IOMBG.",
                fontSize = 12.sp,
                color = DarkTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onNewChatClick,
                colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start a New Conversation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewMessageModal(
    channels: List<Channel>,
    onDismiss: () -> Unit,
    onSelectCreator: (Channel) -> Unit
) {
    var search by remember { mutableStateOf("") }

    val suggestedChannels = remember(channels, search) {
        if (search.isBlank()) {
            channels.take(8)
        } else {
            channels.filter {
                it.channelName.contains(search, ignoreCase = true) ||
                        it.handle.contains(search, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Text("New Message", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search creator or channel handle...", fontSize = 13.sp, color = DarkTextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DarkTextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkBackground,
                    unfocusedContainerColor = DarkBackground
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Suggested Creators & Channels", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkTextSecondary)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestedChannels) { ch ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectCreator(ch) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ch.profileImageUrl,
                            contentDescription = ch.channelName,
                            modifier = Modifier.size(42.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ch.channelName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            Text(ch.handle, fontSize = 11.sp, color = DarkTextSecondary)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DarkTextMuted)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSettingsBottomSheet(
    currentSettings: ChatSettings,
    blockedUsers: List<String>,
    onDismiss: () -> Unit,
    onSaveSettings: (ChatSettings) -> Unit,
    onUnblockUser: (String) -> Unit
) {
    var whoCanMessage by remember { mutableStateOf(currentSettings.whoCanMessageMe) }
    var allowRequests by remember { mutableStateOf(currentSettings.allowMessageRequests) }
    var filterSpam by remember { mutableStateOf(currentSettings.filterSpamRequests) }
    var showOnline by remember { mutableStateOf(currentSettings.showOnlineStatus) }
    var readReceipts by remember { mutableStateOf(currentSettings.sendReadReceipts) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Chat & Privacy Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                Button(
                    onClick = {
                        onSaveSettings(
                            currentSettings.copy(
                                whoCanMessageMe = whoCanMessage,
                                allowMessageRequests = allowRequests,
                                filterSpamRequests = filterSpam,
                                showOnlineStatus = showOnline,
                                sendReadReceipts = readReceipts
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("WHO CAN MESSAGE ME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkTextSecondary)
            Spacer(modifier = Modifier.height(6.dp))

            listOf(
                MessagePermission.EVERYONE to "Everyone (Direct or via Request)",
                MessagePermission.FOLLOWED_ONLY to "Only Channels I Follow",
                MessagePermission.NOBODY to "Nobody (Close Direct Messages)"
            ).forEach { (perm, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { whoCanMessage = perm }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = whoCanMessage == perm,
                        onClick = { whoCanMessage = perm },
                        colors = RadioButtonDefaults.colors(selectedColor = IombgRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, color = Color.White, fontSize = 13.sp)
                }
            }

            Divider(color = DarkBorderSubtle, modifier = Modifier.padding(vertical = 10.dp))

            Text("SAFETY & PRESENCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkTextSecondary)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow Message Requests", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Receive requests from non-followers in a separate tab", color = DarkTextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = allowRequests,
                    onCheckedChange = { allowRequests = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IombgRed)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Spam & Abuse Filter", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Auto-filter repetitive promotional messages", color = DarkTextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = filterSpam,
                    onCheckedChange = { filterSpam = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IombgRed)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show Online Status", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Allow connections to see when you're active", color = DarkTextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = showOnline,
                    onCheckedChange = { showOnline = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IombgRed)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Send Read Receipts", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Let others see when you have viewed their message", color = DarkTextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = readReceipts,
                    onCheckedChange = { readReceipts = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IombgRed)
                )
            }

            if (blockedUsers.isNotEmpty()) {
                Divider(color = DarkBorderSubtle, modifier = Modifier.padding(vertical = 10.dp))
                Text("BLOCKED ACCOUNTS (${blockedUsers.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusError)
                Spacer(modifier = Modifier.height(6.dp))

                blockedUsers.forEach { uid ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(uid, color = DarkTextSecondary, fontSize = 12.sp)
                        TextButton(onClick = { onUnblockUser(uid) }) {
                            Text("Unblock", color = IombgRed, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportChatDialog(
    onDismiss: () -> Unit,
    onSubmit: (MessageReportCategory, String, String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(MessageReportCategory.SPAM) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Content", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Select the reason for your report:", color = DarkTextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))

                listOf(
                    MessageReportCategory.SPAM to "Spam or Advertising",
                    MessageReportCategory.HARASSMENT to "Harassment or Bullying",
                    MessageReportCategory.HATE_ABUSE to "Hate Speech or Discrimination",
                    MessageReportCategory.SCAM_FRAUD to "Scam or Phishing Attempt",
                    MessageReportCategory.SEXUAL_CONTENT to "Inappropriate / Sexual Content",
                    MessageReportCategory.THREATS to "Violence or Threats",
                    MessageReportCategory.OTHER to "Other Policy Violation"
                ).forEach { (cat, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCategory = cat }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            colors = RadioButtonDefaults.colors(selectedColor = IombgRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(label, color = Color.White, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    placeholder = { Text("Additional details (optional)...", fontSize = 12.sp, color = DarkTextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedCategory, selectedCategory.name, details) },
                colors = ButtonDefaults.buttonColors(containerColor = IombgRed)
            ) {
                Text("Submit Report", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DarkTextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}
