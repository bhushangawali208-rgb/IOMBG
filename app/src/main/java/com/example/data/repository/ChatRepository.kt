package com.example.data.repository

import android.os.Bundle
import com.example.data.model.*
import com.example.data.remote.FirebaseService
import com.example.data.remote.FirestoreMessagingService
import com.example.data.remote.MessagingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository(
    private val firebaseService: FirebaseService,
    private val messagingService: MessagingService = FirestoreMessagingService(),
    private val socialRepository: SocialRepository? = null
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    // Current active user's conversations (filtered & sorted)
    private val _activeUserUid = MutableStateFlow("")
    val activeUserUid: StateFlow<String> = _activeUserUid.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    init {
        if (_activeUserUid.value.isNotBlank()) {
            messagingService.observeUserConversations(_activeUserUid.value)
        }
    }

    // All conversations stream for active user (excluding pending requests)
    val conversations: StateFlow<List<ChatConversation>> = combine(
        messagingService.allConversations,
        _activeUserUid,
        messagingService.blockedUsersMap
    ) { allConvs, uid, blockedMap ->
        if (uid.isBlank()) return@combine emptyList()
        val blockedList = blockedMap[uid] ?: emptyList()
        allConvs
            .filter { it.participantUids.contains(uid) }
            .filter { !it.isRequest || it.requestStatus == MessageRequestStatus.ACCEPTED }
            .filter { conv ->
                val otherUid = conv.participantUids.firstOrNull { it != uid }
                otherUid == null || !blockedList.contains(otherUid)
            }
            .sortedWith(
                compareByDescending<ChatConversation> { it.isPinned }
                    .thenByDescending { it.lastMessageTimestamp }
            )
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Pending Message Requests stream
    val messageRequests: StateFlow<List<ChatConversation>> = combine(
        messagingService.allConversations,
        _activeUserUid,
        messagingService.blockedUsersMap
    ) { allConvs, uid, blockedMap ->
        if (uid.isBlank()) return@combine emptyList()
        val blockedList = blockedMap[uid] ?: emptyList()
        allConvs
            .filter { it.participantUids.contains(uid) }
            .filter { it.isRequest && it.requestStatus == MessageRequestStatus.PENDING }
            .filter { it.lastMessageSenderUid != uid } // only incoming requests
            .filter { conv ->
                val otherUid = conv.participantUids.firstOrNull { it != uid }
                otherUid == null || !blockedList.contains(otherUid)
            }
            .sortedByDescending { it.lastMessageTimestamp }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Total unread messages count for active user
    val unreadTotalCount: StateFlow<Int> = combine(
        conversations,
        _activeUserUid
    ) { convs, uid ->
        if (uid.isBlank()) 0 else convs.sumOf { it.unreadCounts[uid] ?: 0 }
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    // Current open conversation messages stream
    val currentMessages: StateFlow<List<ChatMessage>> = combine(
        messagingService.allMessages,
        _activeConversationId
    ) { allMsgs, activeId ->
        if (activeId == null) {
            emptyList()
        } else {
            allMsgs
                .filter { it.conversationId == activeId && it.moderationStatus != MessageModerationStatus.HIDDEN_BY_MODERATOR }
                .sortedBy { it.createdAt }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // User presences map
    val userPresences: StateFlow<Map<String, UserPresence>> = messagingService.presences

    // Blocked users for active user
    val blockedUsers: StateFlow<List<String>> = combine(
        messagingService.blockedUsersMap,
        _activeUserUid
    ) { map, uid ->
        if (uid.isBlank()) emptyList() else (map[uid] ?: emptyList())
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Chat Settings for active user
    val chatSettings: StateFlow<ChatSettings> = combine(
        messagingService.chatSettingsMap,
        _activeUserUid
    ) { map, uid ->
        map[uid] ?: ChatSettings(userId = uid)
    }.stateIn(scope, SharingStarted.Eagerly, ChatSettings(userId = ""))

    fun setActiveUser(uid: String) {
        if (uid.isBlank()) {
            clearActiveUser()
            return
        }
        val previousUid = _activeUserUid.value
        if (previousUid == uid) return

        if (previousUid.isNotBlank() && previousUid != uid) {
            _activeConversationId.value = null
            messagingService.clearActiveConversation()
        }
        _activeUserUid.value = uid
        messagingService.observeUserConversations(uid)
    }

    fun clearActiveUser() {
        _activeUserUid.value = ""
        _activeConversationId.value = null
        messagingService.clearAllListeners()
    }

    fun selectConversation(conversationId: String?, authUid: String? = null) {
        _activeConversationId.value = conversationId
        if (conversationId != null) {
            messagingService.observeConversationMessages(conversationId)
            val effectiveAuthUid = authUid?.takeIf { it.isNotBlank() } ?: _activeUserUid.value
            if (effectiveAuthUid.isNotBlank()) {
                scope.launch {
                    messagingService.markAsRead(conversationId, effectiveAuthUid)
                }
            }
            // Log privacy-safe aggregate analytics (no message text)
            try {
                firebaseService.analytics?.logEvent("message_read", Bundle().apply {
                    putString("conversation_id", conversationId)
                })
            } catch (e: Exception) {
                // Ignore analytics errors
            }
        } else {
            messagingService.clearActiveConversation()
        }
    }

    suspend fun sendMessage(
        conversationId: String,
        receiverUid: String,
        text: String,
        replyToMessageId: String? = null,
        replyToSnippet: String? = null,
        replyToSenderName: String? = null,
        mediaUrl: String? = null,
        currentUser: UserAccount?
    ): Result<ChatMessage> {
        val senderUid = currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: _activeUserUid.value.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No authenticated user to send message"))
        val senderName = currentUser?.displayName ?: "User"

        val result = messagingService.sendMessage(
            conversationId = conversationId,
            senderUid = senderUid,
            senderName = senderName,
            receiverUid = receiverUid,
            text = text,
            messageType = MessageType.TEXT,
            replyToMessageId = replyToMessageId,
            replyToSnippet = replyToSnippet,
            replyToSenderName = replyToSenderName,
            mediaUrl = mediaUrl
        )

        if (result.isSuccess) {
            // Log privacy-safe aggregate analytics (NEVER log message text)
            try {
                firebaseService.analytics?.logEvent("message_sent", Bundle().apply {
                    putString("conversation_id", conversationId)
                    putString("message_type", MessageType.TEXT.name)
                    putBoolean("has_reply", replyToMessageId != null)
                })
            } catch (e: Exception) {
                // Ignore analytics failures
            }

            // Send safe Firestore notification to recipient without leaking message text
            if (receiverUid.isNotBlank() && receiverUid != senderUid) {
                val notif = NotificationItem(
                    notificationId = "notif_msg_${UUID.randomUUID().toString().take(8)}",
                    recipientUid = receiverUid,
                    senderUid = senderUid,
                    senderName = senderName,
                    senderAvatarUrl = currentUser?.photoUrl ?: "",
                    type = NotificationType.MESSAGE,
                    title = "New Message from $senderName",
                    body = if (replyToMessageId != null) "$senderName replied to your message." else "$senderName sent you a message.",
                    targetContentId = conversationId,
                    targetContentType = "CHAT",
                    isRead = false,
                    createdAt = System.currentTimeMillis()
                )
                scope.launch {
                    firebaseService.createNotification(notif)
                }
            }
        }

        return result
    }

    suspend fun startNewConversation(
        targetUid: String,
        targetName: String,
        targetHandle: String,
        targetAvatar: String,
        firstMessageText: String,
        isFollowed: Boolean,
        currentUser: UserAccount?
    ): Result<ChatConversation> {
        val senderUid = currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: _activeUserUid.value.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No authenticated user to start conversation"))
        val senderName = currentUser?.displayName ?: "User"
        val senderAvatar = currentUser?.photoUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200"

        val result = messagingService.startNewConversation(
            initiatorUid = senderUid,
            initiatorName = senderName,
            initiatorAvatar = senderAvatar,
            targetUid = targetUid,
            targetName = targetName,
            targetHandle = targetHandle,
            targetAvatar = targetAvatar,
            initialMessageText = firstMessageText,
            isFollowed = isFollowed
        )

        if (result.isSuccess) {
            val conv = result.getOrNull()
            // Log privacy-safe aggregate analytics (NEVER log message text)
            try {
                firebaseService.analytics?.logEvent("conversation_started", Bundle().apply {
                    putBoolean("is_request", conv?.isRequest ?: false)
                })
            } catch (e: Exception) {
                // Ignore analytics failures
            }

            if (conv != null && conv.isRequest && targetUid.isNotBlank() && targetUid != senderUid) {
                val notif = NotificationItem(
                    notificationId = "notif_req_${UUID.randomUUID().toString().take(8)}",
                    recipientUid = targetUid,
                    senderUid = senderUid,
                    senderName = senderName,
                    senderAvatarUrl = senderAvatar,
                    type = NotificationType.MESSAGE_REQUEST,
                    title = "New Message Request",
                    body = "$senderName wants to send you a message.",
                    targetContentId = conv.conversationId,
                    targetContentType = "CHAT_REQUEST",
                    isRead = false,
                    createdAt = System.currentTimeMillis()
                )
                scope.launch {
                    firebaseService.createNotification(notif)
                }
            }
        }

        return result
    }

    suspend fun acceptMessageRequest(conversationId: String, currentUser: UserAccount?): Result<ChatConversation> {
        val authUid = currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: _activeUserUid.value.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No authenticated user to accept message request"))
        val result = messagingService.acceptMessageRequest(conversationId, authUid)

        if (result.isSuccess) {
            val conv = result.getOrNull()
            val senderUid = conv?.participantUids?.firstOrNull { it != authUid }
            if (senderUid != null && senderUid.isNotBlank() && senderUid != authUid) {
                val notif = NotificationItem(
                    notificationId = "notif_acc_${UUID.randomUUID().toString().take(8)}",
                    recipientUid = senderUid,
                    senderUid = authUid,
                    senderName = currentUser?.displayName ?: "Creator",
                    senderAvatarUrl = currentUser?.photoUrl ?: "",
                    type = NotificationType.MESSAGE_REQUEST_ACCEPTED,
                    title = "Message Request Accepted 🎉",
                    body = "${currentUser?.displayName ?: "The user"} accepted your message request.",
                    targetContentId = conversationId,
                    targetContentType = "CHAT",
                    isRead = false,
                    createdAt = System.currentTimeMillis()
                )
                scope.launch {
                    firebaseService.createNotification(notif)
                }
            }
        }

        return result
    }

    suspend fun declineMessageRequest(conversationId: String, currentUser: UserAccount?): Result<Unit> {
        val authUid = currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: _activeUserUid.value.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No authenticated user to decline message request"))
        return messagingService.declineMessageRequest(conversationId, authUid)
    }

    suspend fun deleteOwnMessage(messageId: String, conversationId: String, currentUser: UserAccount?): Result<Unit> {
        val authUid = currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: _activeUserUid.value.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No authenticated user to delete message"))
        return messagingService.deleteMessage(messageId, conversationId, authUid)
    }

    suspend fun reportMessage(
        messageId: String?,
        conversationId: String,
        reportedUid: String,
        category: MessageReportCategory,
        reason: String,
        details: String,
        currentUser: UserAccount?
    ): Result<MessageReport> {
        val authUid = currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: _activeUserUid.value.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No authenticated user to report message"))
        return messagingService.reportMessage(
            reporterUid = authUid,
            reportedUid = reportedUid,
            conversationId = conversationId,
            messageId = messageId,
            category = category,
            reason = reason,
            details = details
        )
    }

    suspend fun blockUser(targetUid: String, targetName: String, currentUser: UserAccount?): Result<Unit> {
        val authUid = currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: _activeUserUid.value.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No authenticated user to block"))
        return messagingService.blockUser(blockerUid = authUid, targetUid = targetUid)
    }

    suspend fun unblockUser(targetUid: String, currentUser: UserAccount?): Result<Unit> {
        val authUid = currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: _activeUserUid.value.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No authenticated user to unblock"))
        return messagingService.unblockUser(blockerUid = authUid, targetUid = targetUid)
    }

    suspend fun togglePinConversation(conversationId: String) {
        val currentList = messagingService.allConversations.value
        val conv = currentList.firstOrNull { it.conversationId == conversationId } ?: return
        val newPinnedState = !conv.isPinned
        try {
            firebaseService.firestore.collection("conversations")
                .document(conversationId)
                .update("isPinned", newPinnedState)
                .await()
        } catch (e: Exception) {
            // Log fallback or error
        }
    }

    suspend fun updateChatSettings(settings: ChatSettings, currentUser: UserAccount?): Result<ChatSettings> {
        val authUid = currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: _activeUserUid.value.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No authenticated user to update chat settings"))
        return messagingService.updateChatSettings(authUid, settings)
    }

    suspend fun setOnlinePresence(isOnline: Boolean, statusText: String = "", currentUser: UserAccount?) {
        val authUid = currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: _activeUserUid.value.takeIf { it.isNotBlank() }
            ?: return
        messagingService.updatePresence(authUid, isOnline, statusText)
    }
}
