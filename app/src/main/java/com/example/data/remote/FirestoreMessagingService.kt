package com.example.data.remote

import android.util.Log
import com.example.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Production Firebase Firestore Direct Messaging Service.
 * Implements persistent real-time messaging conforming to the Firebase Spark plan:
 * - Direct Firestore persistence for conversations, messages, blocks, reports, settings, and presences.
 * - Real-time snapshot listeners with proactive listener lifecycle management (prevents leaks).
 * - Read receipts and delivery tracking.
 * - Soft-deletion preserving timeline while redacting message text.
 * - Granular access control and participant validation.
 * - Zero dependency on Cloud Functions or Firebase Storage for basic text messaging.
 */
class FirestoreMessagingService(
    firestore: FirebaseFirestore? = null,
    auth: FirebaseAuth? = null
) : MessagingService {

    private val db: FirebaseFirestore? = firestore ?: try {
        FirebaseFirestore.getInstance()
    } catch (e: Throwable) {
        null
    }

    private val authInstance: FirebaseAuth? = auth ?: try {
        FirebaseAuth.getInstance()
    } catch (e: Throwable) {
        null
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    // Real-time listener handles for leak prevention
    private var conversationsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var blockedListener: ListenerRegistration? = null
    private var currentObservedUid: String? = null
    private var currentObservedConversationId: String? = null

    /**
     * Attaches a real-time Firestore listener for all conversations where the user is a participant.
     */
    override fun observeUserConversations(uid: String) {
        if (uid.isBlank()) return
        if (currentObservedUid == uid && conversationsListener != null) return

        // Clean up previous listeners
        conversationsListener?.remove()
        blockedListener?.remove()
        currentObservedUid = uid

        // Listen for conversations
        val firestoreDb = db ?: return
        try {
            conversationsListener = firestoreDb.collection("conversations")
                .whereArrayContains("participantUids", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("FirestoreMessaging", "Error observing conversations for $uid: ${error.message}")
                        return@addSnapshotListener
                    }
                    val convs = snapshot?.documents?.mapNotNull { doc ->
                        doc.toChatConversation()
                    }?.sortedByDescending { it.lastMessageTimestamp } ?: emptyList()

                    _conversations.value = convs
                }
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "Failed to attach conversation listener: ${e.message}")
        }

        // Listen for user blocks
        try {
            blockedListener = firestoreDb.collection("blocks")
                .whereEqualTo("blockerUid", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("FirestoreMessaging", "Error observing blocks for $uid: ${error.message}")
                        return@addSnapshotListener
                    }
                    val blockedList = snapshot?.documents?.mapNotNull { it.getString("targetUid") } ?: emptyList()
                    val currentMap = _blockedUsersMap.value.toMutableMap()
                    currentMap[uid] = blockedList
                    _blockedUsersMap.value = currentMap
                }
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "Failed to attach blocks listener: ${e.message}")
        }
    }

    /**
     * Attaches a real-time Firestore listener for messages in the active conversation.
     */
    override fun observeConversationMessages(conversationId: String) {
        if (conversationId.isBlank()) {
            clearActiveConversation()
            return
        }
        if (currentObservedConversationId == conversationId && messagesListener != null) return

        messagesListener?.remove()
        currentObservedConversationId = conversationId

        val firestoreDb = db ?: return
        try {
            messagesListener = firestoreDb.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("FirestoreMessaging", "Error observing messages for $conversationId: ${error.message}")
                        return@addSnapshotListener
                    }
                    val msgs = snapshot?.documents?.mapNotNull { doc ->
                        doc.toChatMessage()
                    } ?: emptyList()

                    _messages.value = msgs
                }
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "Failed to attach message listener: ${e.message}")
        }
    }

    /**
     * Detaches the active message listener and clears active messages state.
     */
    override fun clearActiveConversation() {
        messagesListener?.remove()
        messagesListener = null
        currentObservedConversationId = null
        _messages.value = emptyList()
    }

    /**
     * Detaches all active listeners (e.g. on user logout).
     */
    override fun clearAllListeners() {
        conversationsListener?.remove()
        conversationsListener = null
        messagesListener?.remove()
        messagesListener = null
        blockedListener?.remove()
        blockedListener = null
        currentObservedUid = null
        currentObservedConversationId = null
        _conversations.value = emptyList()
        _messages.value = emptyList()
    }

    override suspend fun fetchConversationsForUser(uid: String): List<ChatConversation> {
        val firestoreDb = db ?: return _conversations.value
        return try {
            val snapshot = firestoreDb.collection("conversations")
                .whereArrayContains("participantUids", uid)
                .get()
                .await()
            val list = snapshot.documents.mapNotNull { it.toChatConversation() }
                .sortedByDescending { it.lastMessageTimestamp }
            _conversations.value = list
            list
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "fetchConversationsForUser failed: ${e.message}")
            _conversations.value
        }
    }

    override suspend fun getMessagesForConversation(
        conversationId: String,
        limit: Int,
        beforeTimestamp: Long?
    ): List<ChatMessage> {
        val firestoreDb = db ?: return _messages.value
        return try {
            var query: Query = firestoreDb.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)

            if (beforeTimestamp != null) {
                query = query.endBefore(beforeTimestamp)
            }
            if (limit > 0) {
                query = query.limit(limit.toLong())
            }

            val snapshot = query.get().await()
            val list = snapshot.documents.mapNotNull { it.toChatMessage() }
            _messages.value = list
            list
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "getMessagesForConversation failed: ${e.message}")
            _messages.value
        }
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
        if (trimmed.isEmpty() && mediaUrl.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Message content cannot be empty"))
        }

        // Validate sender authenticity if Firebase Auth is currently authenticated
        val currentAuthUid = try { authInstance?.currentUser?.uid } catch (e: Exception) { null }
        if (currentAuthUid != null && currentAuthUid != senderUid) {
            return Result.failure(SecurityException("Sender identity mismatch: cannot send message as another user"))
        }

        // Validate blocking status
        if (isUserBlocked(receiverUid, senderUid)) {
            return Result.failure(IllegalStateException("You are blocked by this user"))
        }
        if (isUserBlocked(senderUid, receiverUid)) {
            return Result.failure(IllegalStateException("You have blocked this user. Unblock to send a message."))
        }

        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val now = System.currentTimeMillis()
        val chatMessage = ChatMessage(
            messageId = msgId,
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
            deliveredAt = now,
            deliveryStatus = MessageDeliveryStatus.SENT,
            moderationStatus = MessageModerationStatus.VISIBLE,
            timestamp = now
        )

        val firestoreDb = db
        if (firestoreDb == null) {
            val current = _messages.value
            _messages.value = current + chatMessage
            return Result.success(chatMessage)
        }

        return try {
            val convDocRef = firestoreDb.collection("conversations").document(conversationId)
            val msgDocRef = convDocRef.collection("messages").document(msgId)

            // Write message document
            msgDocRef.set(chatMessage.toMap()).await()

            // Update conversation last message metadata and increment receiver unread count
            convDocRef.update(
                mapOf(
                    "lastMessageText" to trimmed,
                    "lastMessagePreview" to trimmed,
                    "lastMessageSenderUid" to senderUid,
                    "lastMessageSenderId" to senderUid,
                    "lastMessageTimestamp" to now,
                    "lastMessageAt" to now,
                    "updatedAt" to now,
                    "unreadCounts.$receiverUid" to FieldValue.increment(1)
                )
            ).await()

            Result.success(chatMessage)
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "sendMessage failed: ${e.message}", e)
            Result.failure(e)
        }
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

        // Validate sender authenticity if Firebase Auth is currently authenticated
        val currentAuthUid = try { authInstance?.currentUser?.uid } catch (e: Exception) { null }
        if (currentAuthUid != null && currentAuthUid != initiatorUid) {
            return Result.failure(SecurityException("Initiator identity mismatch"))
        }

        val firestoreDb = db
        // Check if conversation already exists between these users
        if (firestoreDb != null) {
            try {
                val existingSnapshot = firestoreDb.collection("conversations")
                    .whereArrayContains("participantUids", initiatorUid)
                    .get()
                    .await()
                val existing = existingSnapshot.documents
                    .mapNotNull { it.toChatConversation() }
                    .firstOrNull { it.participantUids.contains(targetUid) }

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
            } catch (e: Exception) {
                Log.w("FirestoreMessaging", "Check existing conversation failed: ${e.message}")
            }
        }

        // Check target settings
        val targetSettings = getChatSettings(targetUid)
        if (targetSettings.whoCanMessageMe == MessagePermission.NOBODY) {
            return Result.failure(IllegalStateException("$targetName does not accept direct messages."))
        }
        if (targetSettings.whoCanMessageMe == MessagePermission.FOLLOWED_ONLY && !isFollowed) {
            return Result.failure(IllegalStateException("$targetName only accepts messages from channels they follow."))
        }

        val isRequest = !isFollowed && targetSettings.allowMessageRequests
        val convId = "conv_${UUID.randomUUID().toString().replace("-", "").take(16)}"
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

        if (firestoreDb == null) {
            val current = _conversations.value
            _conversations.value = listOf(newConv) + current.filterNot { it.conversationId == convId }
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

        return try {
            firestoreDb.collection("conversations")
                .document(convId)
                .set(newConv.toMap())
                .await()

            if (initialMessageText.isNotBlank()) {
                sendMessage(
                    conversationId = convId,
                    senderUid = initiatorUid,
                    senderName = initiatorName,
                    receiverUid = targetUid,
                    text = initialMessageText
                )
            }

            Result.success(newConv)
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "startNewConversation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun acceptMessageRequest(
        conversationId: String,
        authUid: String
    ): Result<ChatConversation> {
        val firestoreDb = db
        val now = System.currentTimeMillis()
        if (firestoreDb == null) {
            val conv = _conversations.value.firstOrNull { it.conversationId == conversationId }
                ?: return Result.failure(NoSuchElementException("Conversation not found"))
            if (!conv.participantUids.contains(authUid)) {
                return Result.failure(SecurityException("Unauthorized to modify this conversation"))
            }
            val updated = conv.copy(
                isRequest = false,
                requestStatus = MessageRequestStatus.ACCEPTED,
                updatedAt = now
            )
            _conversations.value = _conversations.value.map { if (it.conversationId == conversationId) updated else it }
            return Result.success(updated)
        }

        return try {
            val convDocRef = firestoreDb.collection("conversations").document(conversationId)
            val snapshot = convDocRef.get().await()
            val conv = snapshot.toChatConversation()
                ?: return Result.failure(NoSuchElementException("Conversation not found"))

            if (!conv.participantUids.contains(authUid)) {
                return Result.failure(SecurityException("Unauthorized to modify this conversation"))
            }

            convDocRef.update(
                mapOf(
                    "isRequest" to false,
                    "requestStatus" to MessageRequestStatus.ACCEPTED.name,
                    "updatedAt" to now
                )
            ).await()

            val updated = conv.copy(
                isRequest = false,
                requestStatus = MessageRequestStatus.ACCEPTED,
                updatedAt = now
            )
            Result.success(updated)
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "acceptMessageRequest failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun declineMessageRequest(
        conversationId: String,
        authUid: String
    ): Result<Unit> {
        val firestoreDb = db
        if (firestoreDb == null) {
            val conv = _conversations.value.firstOrNull { it.conversationId == conversationId }
                ?: return Result.failure(NoSuchElementException("Conversation not found"))
            if (!conv.participantUids.contains(authUid)) {
                return Result.failure(SecurityException("Unauthorized to modify this conversation"))
            }
            _conversations.value = _conversations.value.filterNot { it.conversationId == conversationId }
            return Result.success(Unit)
        }

        return try {
            val convDocRef = firestoreDb.collection("conversations").document(conversationId)
            val snapshot = convDocRef.get().await()
            val conv = snapshot.toChatConversation()
                ?: return Result.failure(NoSuchElementException("Conversation not found"))

            if (!conv.participantUids.contains(authUid)) {
                return Result.failure(SecurityException("Unauthorized to modify this conversation"))
            }

            convDocRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "declineMessageRequest failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteMessage(
        messageId: String,
        conversationId: String,
        authUid: String
    ): Result<Unit> {
        val firestoreDb = db
        val now = System.currentTimeMillis()
        if (firestoreDb == null) {
            val msg = _messages.value.firstOrNull { it.messageId == messageId }
                ?: return Result.failure(NoSuchElementException("Message not found"))
            if (msg.senderUid != authUid) {
                return Result.failure(SecurityException("Users can only delete their own messages"))
            }
            val updated = msg.copy(
                text = "This message was deleted",
                moderationStatus = MessageModerationStatus.DELETED,
                deletedAt = now,
                updatedAt = now
            )
            _messages.value = _messages.value.map { if (it.messageId == messageId) updated else it }
            return Result.success(Unit)
        }

        return try {
            val msgRef = firestoreDb.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)

            val snapshot = msgRef.get().await()
            val msg = snapshot.toChatMessage()
                ?: return Result.failure(NoSuchElementException("Message not found"))

            if (msg.senderUid != authUid) {
                return Result.failure(SecurityException("Users can only delete their own messages"))
            }

            msgRef.update(
                mapOf(
                    "text" to "This message was deleted",
                    "moderationStatus" to MessageModerationStatus.DELETED.name,
                    "deletedAt" to now,
                    "updatedAt" to now
                )
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "deleteMessage failed: ${e.message}", e)
            Result.failure(e)
        }
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
        val reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val now = System.currentTimeMillis()
        val report = MessageReport(
            reportId = reportId,
            reporterUid = reporterUid,
            reportedUid = reportedUid,
            conversationId = conversationId,
            messageId = messageId,
            category = category,
            reason = reason,
            details = details,
            createdAt = now,
            status = "PENDING"
        )

        val currentReports = _reports.value
        _reports.value = currentReports + report
        val firestoreDb = db ?: return Result.success(report)

        return try {
            firestoreDb.collection("reports")
                .document(reportId)
                .set(report.toMap())
                .await()

            if (messageId != null) {
                try {
                    firestoreDb.collection("conversations")
                        .document(conversationId)
                        .collection("messages")
                        .document(messageId)
                        .update(
                            mapOf(
                                "moderationStatus" to MessageModerationStatus.REPORTED.name,
                                "updatedAt" to now
                            )
                        ).await()
                } catch (e: Exception) {
                    Log.w("FirestoreMessaging", "Flagging reported message failed: ${e.message}")
                }
            }

            Result.success(report)
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "reportMessage failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun blockUser(blockerUid: String, targetUid: String): Result<Unit> {
        val blockId = "${blockerUid}_$targetUid"
        val current = _blockedUsersMap.value[blockerUid] ?: emptyList()
        if (!current.contains(targetUid)) {
            val updated = _blockedUsersMap.value.toMutableMap()
            updated[blockerUid] = current + targetUid
            _blockedUsersMap.value = updated
        }
        val firestoreDb = db ?: return Result.success(Unit)

        return try {
            firestoreDb.collection("blocks")
                .document(blockId)
                .set(
                    mapOf(
                        "blockId" to blockId,
                        "blockerUid" to blockerUid,
                        "targetUid" to targetUid,
                        "createdAt" to System.currentTimeMillis()
                    )
                ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "blockUser failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun unblockUser(blockerUid: String, targetUid: String): Result<Unit> {
        val blockId = "${blockerUid}_$targetUid"
        val current = _blockedUsersMap.value[blockerUid] ?: emptyList()
        val updated = _blockedUsersMap.value.toMutableMap()
        updated[blockerUid] = current.filterNot { it == targetUid }
        _blockedUsersMap.value = updated
        val firestoreDb = db ?: return Result.success(Unit)

        return try {
            firestoreDb.collection("blocks")
                .document(blockId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "unblockUser failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun markAsRead(conversationId: String, authUid: String): Result<Unit> {
        val firestoreDb = db ?: return Result.success(Unit)
        return try {
            val convDocRef = firestoreDb.collection("conversations").document(conversationId)
            val now = System.currentTimeMillis()

            // Reset unread count for the current user
            convDocRef.update("unreadCounts.$authUid", 0).await()

            // Fetch unread messages where recipient is authUid
            val unreadSnapshot = convDocRef.collection("messages")
                .whereEqualTo("receiverUid", authUid)
                .whereEqualTo("isRead", false)
                .get()
                .await()

            if (!unreadSnapshot.isEmpty) {
                val batch = firestoreDb.batch()
                for (doc in unreadSnapshot.documents) {
                    batch.update(
                        doc.reference,
                        mapOf(
                            "isRead" to true,
                            "readAt" to now,
                            "deliveryStatus" to MessageDeliveryStatus.READ.name,
                            "updatedAt" to now
                        )
                    )
                }
                batch.commit().await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "markAsRead failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun updatePresence(
        uid: String,
        isOnline: Boolean,
        statusText: String
    ): Result<Unit> {
        val presence = UserPresence(
            uid = uid,
            isOnline = isOnline,
            lastActiveAt = System.currentTimeMillis(),
            statusText = statusText
        )

        val updated = _presences.value.toMutableMap()
        updated[uid] = presence
        _presences.value = updated
        val firestoreDb = db ?: return Result.success(Unit)

        return try {
            firestoreDb.collection("presences")
                .document(uid)
                .set(presence.toMap(), SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "updatePresence failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun updateChatSettings(
        uid: String,
        settings: ChatSettings
    ): Result<ChatSettings> {
        val updated = _chatSettingsMap.value.toMutableMap()
        updated[uid] = settings
        _chatSettingsMap.value = updated
        val firestoreDb = db ?: return Result.success(settings)

        return try {
            firestoreDb.collection("chatSettings")
                .document(uid)
                .set(settings.toMap(), SetOptions.merge())
                .await()

            Result.success(settings)
        } catch (e: Exception) {
            Log.e("FirestoreMessaging", "updateChatSettings failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun getChatSettings(uid: String): ChatSettings {
        return _chatSettingsMap.value[uid] ?: ChatSettings(userId = uid)
    }

    override fun isUserBlocked(blockerUid: String, targetUid: String): Boolean {
        val list = _blockedUsersMap.value[blockerUid] ?: emptyList()
        return list.contains(targetUid)
    }
}

// ==================== DOCUMENT CONVERTERS ====================

fun DocumentSnapshot.toChatConversation(): ChatConversation? {
    if (!exists()) return null
    val id = id.ifBlank { getString("conversationId") ?: "" }
    val pUids = (get("participantUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    val pNames = (get("participantNames") as? Map<*, *>)?.mapNotNull { (k, v) ->
        if (k is String && v is String) k to v else null
    }?.toMap() ?: emptyMap()
    val pHandles = (get("participantHandles") as? Map<*, *>)?.mapNotNull { (k, v) ->
        if (k is String && v is String) k to v else null
    }?.toMap() ?: emptyMap()
    val pAvatars = (get("participantAvatars") as? Map<*, *>)?.mapNotNull { (k, v) ->
        if (k is String && v is String) k to v else null
    }?.toMap() ?: emptyMap()
    val unreads = (get("unreadCounts") as? Map<*, *>)?.mapNotNull { (k, v) ->
        val count = (v as? Number)?.toInt()
        if (k is String && count != null) k to count else null
    }?.toMap() ?: emptyMap()

    val reqStatusStr = getString("requestStatus") ?: "ACCEPTED"
    val reqStatus = try {
        MessageRequestStatus.valueOf(reqStatusStr)
    } catch (e: Exception) {
        MessageRequestStatus.ACCEPTED
    }

    return ChatConversation(
        conversationId = id,
        participantUids = pUids,
        participantIds = pUids,
        participantNames = pNames,
        participantHandles = pHandles,
        participantAvatars = pAvatars,
        createdAt = getLong("createdAt") ?: System.currentTimeMillis(),
        updatedAt = getLong("updatedAt") ?: System.currentTimeMillis(),
        lastMessageText = getString("lastMessageText") ?: getString("lastMessagePreview") ?: "",
        lastMessagePreview = getString("lastMessagePreview") ?: getString("lastMessageText") ?: "",
        lastMessageSenderUid = getString("lastMessageSenderUid") ?: getString("lastMessageSenderId") ?: "",
        lastMessageSenderId = getString("lastMessageSenderId") ?: getString("lastMessageSenderUid") ?: "",
        lastMessageTimestamp = getLong("lastMessageTimestamp") ?: getLong("lastMessageAt") ?: System.currentTimeMillis(),
        lastMessageAt = getLong("lastMessageAt") ?: getLong("lastMessageTimestamp") ?: System.currentTimeMillis(),
        unreadCounts = unreads,
        isRequest = getBoolean("isRequest") ?: false,
        requestStatus = reqStatus,
        isMuted = getBoolean("isMuted") ?: false,
        isPinned = getBoolean("isPinned") ?: false,
        isBlocked = getBoolean("isBlocked") ?: false,
        blockedByUid = getString("blockedByUid"),
        blockedBy = (get("blockedBy") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    )
}

fun DocumentSnapshot.toChatMessage(): ChatMessage? {
    if (!exists()) return null
    val id = id.ifBlank { getString("messageId") ?: "" }
    val msgTypeStr = getString("messageType") ?: "TEXT"
    val msgType = try {
        MessageType.valueOf(msgTypeStr)
    } catch (e: Exception) {
        MessageType.TEXT
    }
    val delStatusStr = getString("deliveryStatus") ?: "SENT"
    val delStatus = try {
        MessageDeliveryStatus.valueOf(delStatusStr)
    } catch (e: Exception) {
        MessageDeliveryStatus.SENT
    }
    val modStatusStr = getString("moderationStatus") ?: "VISIBLE"
    val modStatus = try {
        MessageModerationStatus.valueOf(modStatusStr)
    } catch (e: Exception) {
        MessageModerationStatus.VISIBLE
    }

    return ChatMessage(
        messageId = id,
        conversationId = getString("conversationId") ?: "",
        senderUid = getString("senderUid") ?: "",
        receiverUid = getString("receiverUid") ?: "",
        senderName = getString("senderName") ?: "",
        text = getString("text") ?: "",
        messageType = msgType,
        replyToMessageId = getString("replyToMessageId"),
        replyToSnippet = getString("replyToSnippet"),
        replyToSenderName = getString("replyToSenderName"),
        mediaUrl = getString("mediaUrl"),
        isRead = getBoolean("isRead") ?: false,
        createdAt = getLong("createdAt") ?: System.currentTimeMillis(),
        updatedAt = getLong("updatedAt") ?: System.currentTimeMillis(),
        deliveredAt = getLong("deliveredAt"),
        readAt = getLong("readAt"),
        deletedAt = getLong("deletedAt"),
        deliveryStatus = delStatus,
        moderationStatus = modStatus,
        timestamp = getLong("timestamp") ?: getLong("createdAt") ?: System.currentTimeMillis()
    )
}

fun ChatConversation.toMap(): Map<String, Any?> = mapOf(
    "conversationId" to conversationId,
    "participantUids" to participantUids,
    "participantIds" to participantIds,
    "participantNames" to participantNames,
    "participantHandles" to participantHandles,
    "participantAvatars" to participantAvatars,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "lastMessageText" to lastMessageText,
    "lastMessagePreview" to lastMessagePreview,
    "lastMessageSenderUid" to lastMessageSenderUid,
    "lastMessageSenderId" to lastMessageSenderId,
    "lastMessageTimestamp" to lastMessageTimestamp,
    "lastMessageAt" to lastMessageAt,
    "unreadCounts" to unreadCounts,
    "isRequest" to isRequest,
    "requestStatus" to requestStatus.name,
    "isMuted" to isMuted,
    "isPinned" to isPinned,
    "isBlocked" to isBlocked,
    "blockedByUid" to blockedByUid,
    "blockedBy" to blockedBy
)

fun ChatMessage.toMap(): Map<String, Any?> = mapOf(
    "messageId" to messageId,
    "conversationId" to conversationId,
    "senderUid" to senderUid,
    "receiverUid" to receiverUid,
    "senderName" to senderName,
    "text" to text,
    "messageType" to messageType.name,
    "replyToMessageId" to replyToMessageId,
    "replyToSnippet" to replyToSnippet,
    "replyToSenderName" to replyToSenderName,
    "mediaUrl" to mediaUrl,
    "isRead" to isRead,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "deliveredAt" to deliveredAt,
    "readAt" to readAt,
    "deletedAt" to deletedAt,
    "deliveryStatus" to deliveryStatus.name,
    "moderationStatus" to moderationStatus.name,
    "timestamp" to timestamp
)

fun ChatSettings.toMap(): Map<String, Any?> = mapOf(
    "userId" to userId,
    "whoCanMessageMe" to whoCanMessageMe.name,
    "allowMessageRequests" to allowMessageRequests,
    "filterSpamRequests" to filterSpamRequests,
    "showOnlineStatus" to showOnlineStatus,
    "sendReadReceipts" to sendReadReceipts,
    "blockedUserIds" to blockedUserIds
)

fun UserPresence.toMap(): Map<String, Any?> = mapOf(
    "uid" to uid,
    "isOnline" to isOnline,
    "lastActiveAt" to lastActiveAt,
    "statusText" to statusText
)

fun MessageReport.toMap(): Map<String, Any?> = mapOf(
    "reportId" to reportId,
    "reporterUid" to reporterUid,
    "reportedUid" to reportedUid,
    "conversationId" to conversationId,
    "messageId" to messageId,
    "category" to category.name,
    "reason" to reason,
    "details" to details,
    "createdAt" to createdAt,
    "status" to status
)
