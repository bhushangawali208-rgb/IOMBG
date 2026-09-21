package com.example.data.remote

import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Replaceable Messaging Service Interface.
 * Defines contract for real-time messaging, presence, anti-spam, requests, and moderation.
 */
interface MessagingService {
    val allConversations: StateFlow<List<ChatConversation>>
    val allMessages: StateFlow<List<ChatMessage>>
    val presences: StateFlow<Map<String, UserPresence>>
    val blockedUsersMap: StateFlow<Map<String, List<String>>> // blockerUid -> list of blockedUids
    val reports: StateFlow<List<MessageReport>>
    val chatSettingsMap: StateFlow<Map<String, ChatSettings>>

    suspend fun fetchConversationsForUser(uid: String): List<ChatConversation>
    suspend fun getMessagesForConversation(conversationId: String, limit: Int = 50, beforeTimestamp: Long? = null): List<ChatMessage>
    suspend fun sendMessage(
        conversationId: String,
        senderUid: String,
        senderName: String,
        receiverUid: String,
        text: String,
        messageType: MessageType = MessageType.TEXT,
        replyToMessageId: String? = null,
        replyToSnippet: String? = null,
        replyToSenderName: String? = null,
        mediaUrl: String? = null
    ): Result<ChatMessage>

    suspend fun startNewConversation(
        initiatorUid: String,
        initiatorName: String,
        initiatorAvatar: String,
        targetUid: String,
        targetName: String,
        targetHandle: String,
        targetAvatar: String,
        initialMessageText: String,
        isFollowed: Boolean
    ): Result<ChatConversation>

    suspend fun acceptMessageRequest(conversationId: String, authUid: String): Result<ChatConversation>
    suspend fun declineMessageRequest(conversationId: String, authUid: String): Result<Unit>
    suspend fun deleteMessage(messageId: String, conversationId: String, authUid: String): Result<Unit>
    suspend fun reportMessage(
        reporterUid: String,
        reportedUid: String,
        conversationId: String,
        messageId: String?,
        category: MessageReportCategory,
        reason: String,
        details: String
    ): Result<MessageReport>

    suspend fun blockUser(blockerUid: String, targetUid: String): Result<Unit>
    suspend fun unblockUser(blockerUid: String, targetUid: String): Result<Unit>
    suspend fun markAsRead(conversationId: String, authUid: String): Result<Unit>
    suspend fun updatePresence(uid: String, isOnline: Boolean, statusText: String = ""): Result<Unit>
    suspend fun updateChatSettings(uid: String, settings: ChatSettings): Result<ChatSettings>
    fun getChatSettings(uid: String): ChatSettings
    fun isUserBlocked(blockerUid: String, targetUid: String): Boolean

    // Real-time listener lifecycle methods
    fun observeUserConversations(uid: String) {}
    fun observeConversationMessages(conversationId: String) {}
    fun clearActiveConversation() {}
    fun clearAllListeners() {}
}

/**
 * Sandboxed In-Memory implementation with real-time reactive streams.
 * Isolated development/test fallback only. Production messaging uses FirestoreMessagingService.
 */
@Deprecated(
    message = "SandboxMessagingService is isolated for testing/offline mock fallback only. Production messaging uses FirestoreMessagingService.",
    replaceWith = ReplaceWith("FirestoreMessagingService", "com.example.data.remote.FirestoreMessagingService")
)
class SandboxMessagingService : MessagingService {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _conversations = MutableStateFlow<List<ChatConversation>>(emptyList())
    override val allConversations: StateFlow<List<ChatConversation>> = _conversations.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val allMessages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _presences = MutableStateFlow<Map<String, UserPresence>>(emptyMap())
    override val presences: StateFlow<Map<String, UserPresence>> = _presences.asStateFlow()

    private val _blockedUsersMap = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    override val blockedUsersMap: StateFlow<Map<String, List<String>>> = _blockedUsersMap.asStateFlow()

    private val _reports = MutableStateFlow<List<MessageReport>>(emptyList())
    override val reports: StateFlow<List<MessageReport>> = _reports.asStateFlow()

    private val _chatSettingsMap = MutableStateFlow<Map<String, ChatSettings>>(emptyMap())
    override val chatSettingsMap: StateFlow<Map<String, ChatSettings>> = _chatSettingsMap.asStateFlow()

    // Anti-Spam state tracking per user UID
    private val rateLimitTracker = mutableMapOf<String, MessagingRateLimitState>()

    // Safe Non-Content Analytics Counter
    var totalMessagesSentCount = 0L
        private set
    var totalConversationsStartedCount = 0L
        private set
    var totalReportsCount = 0L
        private set

    init {
        seedInitialConversations()
        seedInitialPresences()
    }

    private fun seedInitialPresences() {
        _presences.value = mapOf(
            "creator_demo_01" to UserPresence("creator_demo_01", isOnline = true, lastActiveAt = System.currentTimeMillis(), statusText = "Editing video 🎬"),
            "user_collab_01" to UserPresence("user_collab_01", isOnline = true, lastActiveAt = System.currentTimeMillis() - 60000, statusText = "Online"),
            "user_fan_99" to UserPresence("user_fan_99", isOnline = false, lastActiveAt = System.currentTimeMillis() - 3600000 * 4, statusText = "Active 4h ago"),
            "ch_nexus" to UserPresence("ch_nexus", isOnline = true, lastActiveAt = System.currentTimeMillis(), statusText = "Streaming Live 🔴"),
            "ch_tech" to UserPresence("ch_tech", isOnline = false, lastActiveAt = System.currentTimeMillis() - 7200000, statusText = "Active 2h ago"),
            "ch_cyber" to UserPresence("ch_cyber", isOnline = true, lastActiveAt = System.currentTimeMillis() - 120000, statusText = "Online")
        )
    }

    private fun seedInitialConversations() {
        val now = System.currentTimeMillis()

        val sampleConvs = listOf(
            ChatConversation(
                conversationId = "conv_001",
                participantUids = listOf("creator_demo_01", "user_collab_01"),
                participantIds = listOf("creator_demo_01", "user_collab_01"),
                participantNames = mapOf(
                    "creator_demo_01" to "Alex Rivera",
                    "user_collab_01" to "Maya Lin"
                ),
                participantHandles = mapOf(
                    "creator_demo_01" to "@alexrivera",
                    "user_collab_01" to "@mayalin_directs"
                ),
                participantAvatars = mapOf(
                    "creator_demo_01" to "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400",
                    "user_collab_01" to "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400"
                ),
                createdAt = now - 86400000 * 2,
                updatedAt = now - 1800000,
                lastMessageText = "Loved the new tech breakdown! Are you open for a collaboration video next week?",
                lastMessagePreview = "Loved the new tech breakdown! Are you open for a collaboration video next week?",
                lastMessageSenderUid = "user_collab_01",
                lastMessageSenderId = "user_collab_01",
                lastMessageTimestamp = now - 1800000,
                lastMessageAt = now - 1800000,
                unreadCounts = mapOf("creator_demo_01" to 1),
                isRequest = false,
                requestStatus = MessageRequestStatus.ACCEPTED,
                isPinned = true
            ),
            ChatConversation(
                conversationId = "conv_002",
                participantUids = listOf("creator_demo_01", "user_fan_99"),
                participantIds = listOf("creator_demo_01", "user_fan_99"),
                participantNames = mapOf(
                    "creator_demo_01" to "Alex Rivera",
                    "user_fan_99" to "Rohan Sharma"
                ),
                participantHandles = mapOf(
                    "creator_demo_01" to "@alexrivera",
                    "user_fan_99" to "@rohan_vfx"
                ),
                participantAvatars = mapOf(
                    "creator_demo_01" to "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400",
                    "user_fan_99" to "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400"
                ),
                createdAt = now - 86400000 * 5,
                updatedAt = now - 86400000,
                lastMessageText = "Thank you so much for the detailed reply!",
                lastMessagePreview = "Thank you so much for the detailed reply!",
                lastMessageSenderUid = "creator_demo_01",
                lastMessageSenderId = "creator_demo_01",
                lastMessageTimestamp = now - 86400000,
                lastMessageAt = now - 86400000,
                unreadCounts = emptyMap(),
                isRequest = false,
                requestStatus = MessageRequestStatus.ACCEPTED
            ),
            // Message Request from unknown non-followed creator
            ChatConversation(
                conversationId = "conv_req_003",
                participantUids = listOf("creator_demo_01", "creator_sponsor_77"),
                participantIds = listOf("creator_demo_01", "creator_sponsor_77"),
                participantNames = mapOf(
                    "creator_demo_01" to "Alex Rivera",
                    "creator_sponsor_77" to "Apex Hardware Sponsorships"
                ),
                participantHandles = mapOf(
                    "creator_demo_01" to "@alexrivera",
                    "creator_sponsor_77" to "@apex_hardware"
                ),
                participantAvatars = mapOf(
                    "creator_demo_01" to "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400",
                    "creator_sponsor_77" to "https://images.unsplash.com/photo-1560250097-0b93528c311a?w=400"
                ),
                createdAt = now - 3600000 * 2,
                updatedAt = now - 3600000 * 2,
                lastMessageText = "Hi Alex, we would like to sponsor your next 4K render benchmark video. Can we discuss rates?",
                lastMessagePreview = "Hi Alex, we would like to sponsor your next 4K render benchmark video. Can we discuss rates?",
                lastMessageSenderUid = "creator_sponsor_77",
                lastMessageSenderId = "creator_sponsor_77",
                lastMessageTimestamp = now - 3600000 * 2,
                lastMessageAt = now - 3600000 * 2,
                unreadCounts = mapOf("creator_demo_01" to 1),
                isRequest = true,
                requestStatus = MessageRequestStatus.PENDING
            )
        )
        _conversations.value = sampleConvs

        val sampleMsgs = listOf(
            ChatMessage(
                messageId = "msg_01",
                conversationId = "conv_001",
                senderUid = "user_collab_01",
                receiverUid = "creator_demo_01",
                senderName = "Maya Lin",
                text = "Hey Alex! Just saw your newest quantum hardware breakdown on IOMBG. The graphics were outstanding!",
                messageType = MessageType.TEXT,
                createdAt = now - 3600000 * 2,
                updatedAt = now - 3600000 * 2,
                deliveredAt = now - 3600000 * 2 + 1000,
                readAt = now - 3600000 * 2 + 5000,
                deliveryStatus = MessageDeliveryStatus.READ,
                timestamp = now - 3600000 * 2
            ),
            ChatMessage(
                messageId = "msg_02",
                conversationId = "conv_001",
                senderUid = "creator_demo_01",
                receiverUid = "user_collab_01",
                senderName = "Alex Rivera",
                text = "Thanks Maya! Appreciate it. Took about 30 hours in Blender to get the simulations accurate.",
                messageType = MessageType.TEXT,
                createdAt = now - 3600000,
                updatedAt = now - 3600000,
                deliveredAt = now - 3600000 + 1000,
                readAt = now - 3600000 + 3000,
                deliveryStatus = MessageDeliveryStatus.READ,
                timestamp = now - 3600000
            ),
            ChatMessage(
                messageId = "msg_03",
                conversationId = "conv_001",
                senderUid = "user_collab_01",
                receiverUid = "creator_demo_01",
                senderName = "Maya Lin",
                text = "Loved the new tech breakdown! Are you open for a collaboration video next week?",
                messageType = MessageType.TEXT,
                replyToMessageId = "msg_02",
                replyToSnippet = "Took about 30 hours in Blender to get the simulations accurate.",
                replyToSenderName = "Alex Rivera",
                createdAt = now - 1800000,
                updatedAt = now - 1800000,
                deliveredAt = now - 1800000 + 1000,
                readAt = null,
                deliveryStatus = MessageDeliveryStatus.DELIVERED,
                timestamp = now - 1800000
            ),
            ChatMessage(
                messageId = "msg_req_01",
                conversationId = "conv_req_003",
                senderUid = "creator_sponsor_77",
                receiverUid = "creator_demo_01",
                senderName = "Apex Hardware Sponsorships",
                text = "Hi Alex, we would like to sponsor your next 4K render benchmark video. Can we discuss rates?",
                messageType = MessageType.TEXT,
                createdAt = now - 3600000 * 2,
                updatedAt = now - 3600000 * 2,
                deliveredAt = now - 3600000 * 2 + 1000,
                readAt = null,
                deliveryStatus = MessageDeliveryStatus.DELIVERED,
                timestamp = now - 3600000 * 2
            )
        )
        _messages.value = sampleMsgs
    }

    override suspend fun fetchConversationsForUser(uid: String): List<ChatConversation> {
        val blockedList = _blockedUsersMap.value[uid] ?: emptyList()
        return _conversations.value
            .filter { it.participantUids.contains(uid) }
            .filter { conv ->
                // Filter out conversations with blocked users
                val otherUid = conv.participantUids.firstOrNull { it != uid }
                otherUid == null || !blockedList.contains(otherUid)
            }
            .sortedByDescending { it.lastMessageTimestamp }
    }

    override suspend fun getMessagesForConversation(conversationId: String, limit: Int, beforeTimestamp: Long?): List<ChatMessage> {
        val filtered = _messages.value
            .filter { it.conversationId == conversationId && it.moderationStatus != MessageModerationStatus.HIDDEN_BY_MODERATOR }
            .let { list ->
                if (beforeTimestamp != null) list.filter { it.createdAt < beforeTimestamp } else list
            }
            .sortedBy { it.createdAt }

        return filtered.takeLast(limit)
    }

    override suspend fun sendMessage(
        conversationId: String,
        senderUid: String,
        senderName: String,
        receiverUid: String,
        text: String,
        messageType: MessageType,
        replyToMessageId: String?,
        replyToSnippet: String?,
        replyToSenderName: String?,
        mediaUrl: String?
    ): Result<ChatMessage> {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && mediaUrl == null) {
            return Result.failure(IllegalArgumentException("Message content cannot be empty"))
        }

        // 1. Anti-Spam Check: Rate limiting & duplicate detection
        val now = System.currentTimeMillis()
        val rateState = rateLimitTracker[senderUid] ?: MessagingRateLimitState()

        if (rateState.cooldownUntil > now) {
            val remainingSec = ((rateState.cooldownUntil - now) / 1000).coerceAtLeast(1)
            return Result.failure(IllegalStateException("Messaging cooldown active. Please wait ${remainingSec}s before sending."))
        }

        // Check duplicate within 5 seconds
        if (rateState.lastMessageText == trimmed && (now - rateState.lastMessageTime) < 5000) {
            return Result.failure(IllegalStateException("Duplicate message detected. Please wait a moment."))
        }

        // Rate limit: max 12 messages per 30 seconds
        val window30s = rateState.recentMessageTimestamps.filter { now - it < 30000 }
        if (window30s.size >= 12) {
            val cooldownUntil = now + 45000 // 45s cooldown
            rateLimitTracker[senderUid] = rateState.copy(cooldownUntil = cooldownUntil)
            return Result.failure(IllegalStateException("You are sending messages too quickly. Cooldown for 45s applied."))
        }

        // Update rate limit tracker
        rateLimitTracker[senderUid] = MessagingRateLimitState(
            lastMessageTime = now,
            recentMessageTimestamps = window30s + now,
            lastMessageText = trimmed,
            cooldownUntil = 0
        )

        // 2. Block Check
        if (isUserBlocked(receiverUid, senderUid)) {
            return Result.failure(IllegalStateException("You cannot message this user."))
        }
        if (isUserBlocked(senderUid, receiverUid)) {
            return Result.failure(IllegalStateException("You have blocked this user. Unblock to send messages."))
        }

        // 3. Construct Secure Message
        val newMsgId = "msg_${UUID.randomUUID().toString().take(10)}"
        val chatMessage = ChatMessage(
            messageId = newMsgId,
            conversationId = conversationId,
            senderUid = senderUid,
            receiverUid = receiverUid,
            senderName = senderName,
            text = trimmed,
            messageType = messageType,
            replyToMessageId = replyToMessageId,
            replyToSnippet = replyToSnippet,
            replyToSenderName = replyToSenderName,
            mediaUrl = mediaUrl,
            isRead = false,
            createdAt = now,
            updatedAt = now,
            deliveredAt = now + 400,
            deliveryStatus = MessageDeliveryStatus.SENT,
            moderationStatus = MessageModerationStatus.VISIBLE,
            timestamp = now
        )

        // Update in-memory state
        _messages.value = _messages.value + chatMessage
        totalMessagesSentCount += 1

        // Update Conversation Last Message & Unread Count
        _conversations.value = _conversations.value.map { conv ->
            if (conv.conversationId == conversationId) {
                val currentUnread = conv.unreadCounts[receiverUid] ?: 0
                val updatedUnreads = conv.unreadCounts.toMutableMap()
                updatedUnreads[receiverUid] = currentUnread + 1

                conv.copy(
                    lastMessageText = trimmed,
                    lastMessagePreview = trimmed,
                    lastMessageSenderUid = senderUid,
                    lastMessageSenderId = senderUid,
                    lastMessageTimestamp = now,
                    lastMessageAt = now,
                    updatedAt = now,
                    unreadCounts = updatedUnreads
                )
            } else conv
        }

        // Simulate real-time automated delivery & read receipts in sandbox
        scope.launch {
            delay(1200)
            _messages.value = _messages.value.map { m ->
                if (m.messageId == newMsgId) m.copy(deliveryStatus = MessageDeliveryStatus.DELIVERED, deliveredAt = System.currentTimeMillis()) else m
            }
        }

        return Result.success(chatMessage)
    }

    override suspend fun startNewConversation(
        initiatorUid: String,
        initiatorName: String,
        initiatorAvatar: String,
        targetUid: String,
        targetName: String,
        targetHandle: String,
        targetAvatar: String,
        initialMessageText: String,
        isFollowed: Boolean
    ): Result<ChatConversation> {
        if (initiatorUid == targetUid) {
            return Result.failure(IllegalArgumentException("Cannot start conversation with yourself"))
        }

        // Check if conversation already exists
        val existing = _conversations.value.firstOrNull { conv ->
            conv.participantUids.contains(initiatorUid) && conv.participantUids.contains(targetUid)
        }

        if (existing != null) {
            if (initialMessageText.isNotBlank()) {
                sendMessage(
                    conversationId = existing.conversationId,
                    senderUid = initiatorUid,
                    senderName = initiatorName,
                    receiverUid = targetUid,
                    text = initialMessageText
                )
            }
            return Result.success(existing)
        }

        // Target Settings check
        val targetSettings = getChatSettings(targetUid)
        if (targetSettings.whoCanMessageMe == MessagePermission.NOBODY) {
            return Result.failure(IllegalStateException("$targetName does not accept direct messages."))
        }
        if (targetSettings.whoCanMessageMe == MessagePermission.FOLLOWED_ONLY && !isFollowed) {
            return Result.failure(IllegalStateException("$targetName only accepts messages from channels they follow."))
        }

        val isRequest = !isFollowed && targetSettings.allowMessageRequests
        val convId = "conv_${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()

        val newConv = ChatConversation(
            conversationId = convId,
            participantUids = listOf(initiatorUid, targetUid),
            participantIds = listOf(initiatorUid, targetUid),
            participantNames = mapOf(
                initiatorUid to initiatorName,
                targetUid to targetName
            ),
            participantHandles = mapOf(
                initiatorUid to "@${initiatorName.lowercase().replace(" ", "_")}",
                targetUid to targetHandle
            ),
            participantAvatars = mapOf(
                initiatorUid to initiatorAvatar,
                targetUid to targetAvatar
            ),
            createdAt = now,
            updatedAt = now,
            lastMessageText = initialMessageText.ifBlank { "Started conversation" },
            lastMessagePreview = initialMessageText.ifBlank { "Started conversation" },
            lastMessageSenderUid = initiatorUid,
            lastMessageSenderId = initiatorUid,
            lastMessageTimestamp = now,
            lastMessageAt = now,
            unreadCounts = if (initialMessageText.isNotBlank()) mapOf(targetUid to 1) else emptyMap(),
            isRequest = isRequest,
            requestStatus = if (isRequest) MessageRequestStatus.PENDING else MessageRequestStatus.ACCEPTED
        )

        _conversations.value = listOf(newConv) + _conversations.value
        totalConversationsStartedCount += 1

        if (initialMessageText.isNotBlank()) {
            sendMessage(
                conversationId = convId,
                senderUid = initiatorUid,
                senderName = initiatorName,
                receiverUid = targetUid,
                text = initialMessageText
            )
        }

        return Result.success(newConv)
    }

    override suspend fun acceptMessageRequest(conversationId: String, authUid: String): Result<ChatConversation> {
        val conv = _conversations.value.firstOrNull { it.conversationId == conversationId }
            ?: return Result.failure(NoSuchElementException("Conversation not found"))

        if (!conv.participantUids.contains(authUid)) {
            return Result.failure(SecurityException("Unauthorized to modify this conversation"))
        }

        val updated = conv.copy(
            isRequest = false,
            requestStatus = MessageRequestStatus.ACCEPTED,
            updatedAt = System.currentTimeMillis()
        )

        _conversations.value = _conversations.value.map { if (it.conversationId == conversationId) updated else it }
        return Result.success(updated)
    }

    override suspend fun declineMessageRequest(conversationId: String, authUid: String): Result<Unit> {
        val conv = _conversations.value.firstOrNull { it.conversationId == conversationId }
            ?: return Result.failure(NoSuchElementException("Conversation not found"))

        if (!conv.participantUids.contains(authUid)) {
            return Result.failure(SecurityException("Unauthorized to modify this conversation"))
        }

        _conversations.value = _conversations.value.filterNot { it.conversationId == conversationId }
        _messages.value = _messages.value.filterNot { it.conversationId == conversationId }
        return Result.success(Unit)
    }

    override suspend fun deleteMessage(messageId: String, conversationId: String, authUid: String): Result<Unit> {
        val msg = _messages.value.firstOrNull { it.messageId == messageId }
            ?: return Result.failure(NoSuchElementException("Message not found"))

        if (msg.senderUid != authUid) {
            return Result.failure(SecurityException("Users can only delete their own messages"))
        }

        val now = System.currentTimeMillis()
        _messages.value = _messages.value.map {
            if (it.messageId == messageId) {
                it.copy(
                    text = "This message was deleted",
                    moderationStatus = MessageModerationStatus.DELETED,
                    deletedAt = now,
                    updatedAt = now
                )
            } else it
        }

        return Result.success(Unit)
    }

    override suspend fun reportMessage(
        reporterUid: String,
        reportedUid: String,
        conversationId: String,
        messageId: String?,
        category: MessageReportCategory,
        reason: String,
        details: String
    ): Result<MessageReport> {
        val report = MessageReport(
            reportId = "rep_${UUID.randomUUID().toString().take(8)}",
            reporterUid = reporterUid,
            reportedUid = reportedUid,
            conversationId = conversationId,
            messageId = messageId,
            category = category,
            reason = reason,
            details = details,
            createdAt = System.currentTimeMillis(),
            status = "PENDING"
        )

        _reports.value = _reports.value + report
        totalReportsCount += 1

        // Flag message if specific message reported
        if (messageId != null) {
            _messages.value = _messages.value.map {
                if (it.messageId == messageId) it.copy(moderationStatus = MessageModerationStatus.REPORTED) else it
            }
        }

        return Result.success(report)
    }

    override suspend fun blockUser(blockerUid: String, targetUid: String): Result<Unit> {
        val currentBlocked = _blockedUsersMap.value[blockerUid] ?: emptyList()
        if (!currentBlocked.contains(targetUid)) {
            val updated = _blockedUsersMap.value.toMutableMap()
            updated[blockerUid] = currentBlocked + targetUid
            _blockedUsersMap.value = updated
        }
        return Result.success(Unit)
    }

    override suspend fun unblockUser(blockerUid: String, targetUid: String): Result<Unit> {
        val currentBlocked = _blockedUsersMap.value[blockerUid] ?: emptyList()
        val updated = _blockedUsersMap.value.toMutableMap()
        updated[blockerUid] = currentBlocked.filterNot { it == targetUid }
        _blockedUsersMap.value = updated
        return Result.success(Unit)
    }

    override suspend fun markAsRead(conversationId: String, authUid: String): Result<Unit> {
        val now = System.currentTimeMillis()

        _conversations.value = _conversations.value.map { conv ->
            if (conv.conversationId == conversationId) {
                val updatedUnreads = conv.unreadCounts.toMutableMap()
                updatedUnreads[authUid] = 0
                conv.copy(unreadCounts = updatedUnreads)
            } else conv
        }

        _messages.value = _messages.value.map { msg ->
            if (msg.conversationId == conversationId && msg.receiverUid == authUid && !msg.isRead) {
                msg.copy(isRead = true, readAt = now, deliveryStatus = MessageDeliveryStatus.READ)
            } else msg
        }

        return Result.success(Unit)
    }

    override suspend fun updatePresence(uid: String, isOnline: Boolean, statusText: String): Result<Unit> {
        val updated = _presences.value.toMutableMap()
        updated[uid] = UserPresence(
            uid = uid,
            isOnline = isOnline,
            lastActiveAt = System.currentTimeMillis(),
            statusText = statusText
        )
        _presences.value = updated
        return Result.success(Unit)
    }

    override suspend fun updateChatSettings(uid: String, settings: ChatSettings): Result<ChatSettings> {
        val updated = _chatSettingsMap.value.toMutableMap()
        updated[uid] = settings
        _chatSettingsMap.value = updated
        return Result.success(settings)
    }

    override fun getChatSettings(uid: String): ChatSettings {
        return _chatSettingsMap.value[uid] ?: ChatSettings(userId = uid)
    }

    override fun isUserBlocked(blockerUid: String, targetUid: String): Boolean {
        val list = _blockedUsersMap.value[blockerUid] ?: emptyList()
        return list.contains(targetUid)
    }
}
