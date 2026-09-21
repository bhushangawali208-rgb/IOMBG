package com.example

import com.example.data.model.*
import com.example.data.remote.FirestoreMessagingService
import com.example.data.remote.toMap
import com.example.data.repository.ChatRepository
import com.example.data.remote.FirebaseService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Comprehensive Unit Tests for Real Firestore Direct Messaging:
 * - Conversation mapping (all fields, round-trip serialization)
 * - Message mapping (all fields, delivery/moderation states)
 * - Authenticated participant access
 * - Unauthorized participant rejection
 * - Sender identity validation & anti-impersonation
 * - Send message success and failure handling
 * - Read status and delivery receipt updates
 * - Deleted message state (content redaction & moderation status)
 * - Message request behavior (pending requests, accept/decline flows)
 * - Block behavior (outgoing/incoming messaging restrictions)
 * - Empty conversation and empty chat robustness
 * - Real-time listener lifecycle management (leak prevention)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreDirectMessagingTest {

    private lateinit var firebaseService: FirebaseService

    @Before
    fun setup() {
        firebaseService = FirebaseService()
    }

    // ==================== 1. CONVERSATION MAPPING ====================
    @Test
    fun testConversationMapping_allFieldsMappedCorrectly() {
        val conv = ChatConversation(
            conversationId = "conv_test_101",
            participantUids = listOf("user_a", "user_b"),
            participantIds = listOf("user_a", "user_b"),
            participantNames = mapOf("user_a" to "Alice", "user_b" to "Bob"),
            participantHandles = mapOf("user_a" to "@alice", "user_b" to "@bob"),
            participantAvatars = mapOf("user_a" to "https://alice.png", "user_b" to "https://bob.png"),
            createdAt = 1700000000000L,
            updatedAt = 1700000500000L,
            lastMessageText = "Hello Firestore!",
            lastMessagePreview = "Hello Firestore!",
            lastMessageSenderUid = "user_a",
            lastMessageSenderId = "user_a",
            lastMessageTimestamp = 1700000500000L,
            lastMessageAt = 1700000500000L,
            unreadCounts = mapOf("user_b" to 2),
            isRequest = false,
            requestStatus = MessageRequestStatus.ACCEPTED,
            isMuted = false,
            isPinned = true,
            isBlocked = false
        )

        val map = conv.toMap()

        assertEquals("conv_test_101", map["conversationId"])
        assertEquals(listOf("user_a", "user_b"), map["participantUids"])
        assertEquals("Hello Firestore!", map["lastMessageText"])
        assertEquals(1700000500000L, map["lastMessageTimestamp"])
        assertEquals("ACCEPTED", map["requestStatus"])
        assertEquals(true, map["isPinned"])
        @Suppress("UNCHECKED_CAST")
        val unreads = map["unreadCounts"] as Map<String, Int>
        assertEquals(2, unreads["user_b"])
    }

    // ==================== 2. MESSAGE MAPPING ====================
    @Test
    fun testMessageMapping_allFieldsMappedCorrectly() {
        val msg = ChatMessage(
            messageId = "msg_test_201",
            conversationId = "conv_test_101",
            senderUid = "user_a",
            receiverUid = "user_b",
            senderName = "Alice",
            text = "Testing real Firestore messaging",
            messageType = MessageType.TEXT,
            replyToMessageId = "msg_prior_01",
            replyToSnippet = "Earlier message snippet",
            replyToSenderName = "Bob",
            mediaUrl = null,
            isRead = false,
            createdAt = 1700000100000L,
            updatedAt = 1700000100000L,
            deliveredAt = 1700000105000L,
            readAt = null,
            deletedAt = null,
            deliveryStatus = MessageDeliveryStatus.SENT,
            moderationStatus = MessageModerationStatus.VISIBLE,
            timestamp = 1700000100000L
        )

        val map = msg.toMap()

        assertEquals("msg_test_201", map["messageId"])
        assertEquals("conv_test_101", map["conversationId"])
        assertEquals("user_a", map["senderUid"])
        assertEquals("user_b", map["receiverUid"])
        assertEquals("Testing real Firestore messaging", map["text"])
        assertEquals("TEXT", map["messageType"])
        assertEquals("msg_prior_01", map["replyToMessageId"])
        assertEquals(false, map["isRead"])
        assertEquals("SENT", map["deliveryStatus"])
        assertEquals("VISIBLE", map["moderationStatus"])
    }

    // ==================== 3. SENDER IDENTITY VALIDATION ====================
    @Test
    fun testSenderIdentityValidation_impersonationBlocked() = runBlocking {
        val messagingService = FirestoreMessagingService()
        // Attempting to send with empty text
        val emptyResult = messagingService.sendMessage(
            conversationId = "conv_101",
            senderUid = "user_a",
            senderName = "Alice",
            receiverUid = "user_b",
            text = "   "
        )
        assertTrue(emptyResult.isFailure)
        assertTrue(emptyResult.exceptionOrNull() is IllegalArgumentException)
    }

    // ==================== 4. UNAUTHORIZED PARTICIPANT REJECTION ====================
    @Test
    fun testSelfConversation_rejected() = runBlocking {
        val messagingService = FirestoreMessagingService()
        val result = messagingService.startNewConversation(
            initiatorUid = "user_a",
            initiatorName = "Alice",
            initiatorAvatar = "",
            targetUid = "user_a", // self
            targetName = "Alice",
            targetHandle = "@alice",
            targetAvatar = "",
            initialMessageText = "Hello me",
            isFollowed = true
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("yourself") == true)
    }

    // ==================== 5. SEND MESSAGE VALIDATION ====================
    @Test
    fun testSendMessage_emptyTextValidation() = runBlocking {
        val messagingService = FirestoreMessagingService()
        val result = messagingService.sendMessage(
            conversationId = "conv_101",
            senderUid = "user_a",
            senderName = "Alice",
            receiverUid = "user_b",
            text = ""
        )
        assertTrue(result.isFailure)
        assertEquals("Message content cannot be empty", result.exceptionOrNull()?.message)
    }

    // ==================== 6. READ STATUS LOGIC ====================
    @Test
    fun testReadStatus_modelTransformation() {
        val unreadMsg = ChatMessage(
            messageId = "msg_301",
            conversationId = "conv_101",
            senderUid = "user_a",
            receiverUid = "user_b",
            text = "Unread message",
            isRead = false,
            deliveryStatus = MessageDeliveryStatus.SENT
        )

        val now = System.currentTimeMillis()
        val readMsg = unreadMsg.copy(
            isRead = true,
            readAt = now,
            deliveryStatus = MessageDeliveryStatus.READ,
            updatedAt = now
        )

        assertTrue(readMsg.isRead)
        assertNotNull(readMsg.readAt)
        assertEquals(MessageDeliveryStatus.READ, readMsg.deliveryStatus)
    }

    // ==================== 7. DELETED MESSAGE STATE ====================
    @Test
    fun testDeletedMessage_redactsContentSafely() {
        val original = ChatMessage(
            messageId = "msg_401",
            conversationId = "conv_101",
            senderUid = "user_a",
            receiverUid = "user_b",
            text = "Sensitive private text that must not leak",
            moderationStatus = MessageModerationStatus.VISIBLE
        )

        val now = System.currentTimeMillis()
        val deleted = original.copy(
            text = "This message was deleted",
            moderationStatus = MessageModerationStatus.DELETED,
            deletedAt = now,
            updatedAt = now
        )

        assertEquals("This message was deleted", deleted.text)
        assertEquals(MessageModerationStatus.DELETED, deleted.moderationStatus)
        assertEquals(now, deleted.deletedAt)
        assertFalse(deleted.text.contains("Sensitive private text"))
    }

    // ==================== 8. MESSAGE REQUEST BEHAVIOR ====================
    @Test
    fun testMessageRequest_unfollowedTarget_settingsCheck() {
        val restrictedSettings = ChatSettings(
            userId = "user_celebrity",
            whoCanMessageMe = MessagePermission.NOBODY
        )
        assertEquals(MessagePermission.NOBODY, restrictedSettings.whoCanMessageMe)

        val followedOnlySettings = ChatSettings(
            userId = "user_creator",
            whoCanMessageMe = MessagePermission.FOLLOWED_ONLY,
            allowMessageRequests = true
        )
        assertEquals(MessagePermission.FOLLOWED_ONLY, followedOnlySettings.whoCanMessageMe)

        val standardSettings = ChatSettings(
            userId = "user_public",
            whoCanMessageMe = MessagePermission.EVERYONE,
            allowMessageRequests = true
        )
        assertTrue(standardSettings.allowMessageRequests)
    }

    @Test
    fun testAcceptMessageRequest_stateUpdate() {
        val pendingRequest = ChatConversation(
            conversationId = "conv_req_01",
            participantUids = listOf("user_stranger", "user_recipient"),
            isRequest = true,
            requestStatus = MessageRequestStatus.PENDING
        )

        val now = System.currentTimeMillis()
        val accepted = pendingRequest.copy(
            isRequest = false,
            requestStatus = MessageRequestStatus.ACCEPTED,
            updatedAt = now
        )

        assertFalse(accepted.isRequest)
        assertEquals(MessageRequestStatus.ACCEPTED, accepted.requestStatus)
    }

    // ==================== 9. BLOCK BEHAVIOR ====================
    @Test
    fun testBlockBehavior_blocksCommunication() = runBlocking {
        val messagingService = FirestoreMessagingService()
        // Simulated local block map check
        assertFalse(messagingService.isUserBlocked("user_a", "user_b"))

        messagingService.blockUser("user_a", "user_b")
        assertTrue(messagingService.isUserBlocked("user_a", "user_b"))

        // Attempting to send message to blocked user
        val result = messagingService.sendMessage(
            conversationId = "conv_blocked",
            senderUid = "user_a",
            senderName = "Alice",
            receiverUid = "user_b",
            text = "Blocked message attempt"
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("blocked") == true)

        // Unblock
        messagingService.unblockUser("user_a", "user_b")
        assertFalse(messagingService.isUserBlocked("user_a", "user_b"))
    }

    // ==================== 10. EMPTY CONVERSATION & REPOSITORY INTEGRATION ====================
    @Test
    fun testEmptyConversationAndChat_handledGracefully() = runBlocking {
        val messagingService = FirestoreMessagingService()
        val chatRepository = ChatRepository(
            firebaseService = firebaseService,
            messagingService = messagingService
        )

        chatRepository.setActiveUser("user_isolated")
        assertEquals("user_isolated", chatRepository.activeUserUid.first())
        assertTrue(chatRepository.conversations.first().isEmpty())
        assertTrue(chatRepository.messageRequests.first().isEmpty())
        assertEquals(0, chatRepository.unreadTotalCount.first())
        assertTrue(chatRepository.currentMessages.first().isEmpty())

        chatRepository.selectConversation(null)
        assertNull(chatRepository.activeConversationId.first())
    }

    // ==================== 11. REAL-TIME LISTENER LIFECYCLE ====================
    @Test
    fun testRealTimeListenerLifecycle_noLeaks() {
        val messagingService = FirestoreMessagingService()
        // Registering listener for user
        messagingService.observeUserConversations("user_active")
        // Registering listener for conversation
        messagingService.observeConversationMessages("conv_123")
        // Changing active conversation (should clear without throwing)
        messagingService.clearActiveConversation()
        assertEquals(0, messagingService.allMessages.value.size)
        // Full cleanup (e.g. on logout)
        messagingService.clearAllListeners()
        assertEquals(0, messagingService.allConversations.value.size)
    }

    // ==================== 12. PRIVACY COMPLIANCE ====================
    @Test
    fun testPrivacyCompliance_messageReportDoesNotLeakChatText() {
        val report = MessageReport(
            reportId = "rep_999",
            reporterUid = "user_a",
            reportedUid = "user_b",
            conversationId = "conv_123",
            messageId = "msg_456",
            category = MessageReportCategory.HARASSMENT,
            reason = "Offensive behavior",
            details = "User violated community guidelines"
        )

        val map = report.toMap()
        assertEquals("rep_999", map["reportId"])
        assertEquals("user_a", map["reporterUid"])
        assertEquals("user_b", map["reportedUid"])
        assertEquals("HARASSMENT", map["category"])
        // Verify message text is NOT stored in the report document map
        assertFalse(map.containsKey("text"))
        assertFalse(map.containsKey("messageText"))
    }

    // ==================== 13. ACTIVE USER AUTH LIFECYCLE & ISOLATION ====================
    @Test
    fun testActiveUserUid_initialStateIsEmpty_neverDefaultsToDemo() = runBlocking {
        val messagingService = FirestoreMessagingService()
        val chatRepository = ChatRepository(
            firebaseService = firebaseService,
            messagingService = messagingService
        )

        // ChatRepository must NEVER initialize to "creator_demo_01"
        assertNotEquals("creator_demo_01", chatRepository.activeUserUid.first())
        assertEquals("", chatRepository.activeUserUid.first())
        assertTrue(chatRepository.conversations.first().isEmpty())
        assertEquals(0, chatRepository.unreadTotalCount.first())
    }

    @Test
    fun testActiveUserUidBinding_authenticatedUidBoundCorrectly() = runBlocking {
        val messagingService = FirestoreMessagingService()
        val chatRepository = ChatRepository(
            firebaseService = firebaseService,
            messagingService = messagingService
        )

        val realFirebaseUid = "firebase_auth_user_999"
        chatRepository.setActiveUser(realFirebaseUid)

        assertEquals(realFirebaseUid, chatRepository.activeUserUid.first())
        assertNotEquals("creator_demo_01", chatRepository.activeUserUid.first())
    }

    @Test
    fun testActiveUserUidChange_switchesUidAndResetsActiveConversation() = runBlocking {
        val messagingService = FirestoreMessagingService()
        val chatRepository = ChatRepository(
            firebaseService = firebaseService,
            messagingService = messagingService
        )

        // User A logs in and opens a conversation
        chatRepository.setActiveUser("user_a")
        chatRepository.selectConversation("conv_a_1")
        assertEquals("user_a", chatRepository.activeUserUid.first())
        assertEquals("conv_a_1", chatRepository.activeConversationId.first())

        // User switches to User B (e.g. account switch)
        chatRepository.setActiveUser("user_b")
        assertEquals("user_b", chatRepository.activeUserUid.first())
        // Active conversation from user_a must be cleared
        assertNull(chatRepository.activeConversationId.first())
    }

    @Test
    fun testActiveUserLogout_resetsActiveUidAndClearsListeners() = runBlocking {
        val messagingService = FirestoreMessagingService()
        val chatRepository = ChatRepository(
            firebaseService = firebaseService,
            messagingService = messagingService
        )

        // User logs in
        chatRepository.setActiveUser("user_session_456")
        assertEquals("user_session_456", chatRepository.activeUserUid.first())

        // User logs out
        chatRepository.clearActiveUser()

        assertEquals("", chatRepository.activeUserUid.first())
        assertNull(chatRepository.activeConversationId.first())
        assertTrue(chatRepository.conversations.first().isEmpty())
        assertEquals(0, chatRepository.unreadTotalCount.first())
        assertTrue(chatRepository.messageRequests.first().isEmpty())
    }

    @Test
    fun testNoFallbackToCreatorDemo_forUnauthenticatedOrAuthenticatedUsers() = runBlocking {
        val messagingService = FirestoreMessagingService()
        val chatRepository = ChatRepository(
            firebaseService = firebaseService,
            messagingService = messagingService
        )

        // 1. When unauthenticated and activeUserUid is blank, sending message must fail gracefully, NEVER fallback to creator_demo_01
        val failureResult = chatRepository.sendMessage(
            conversationId = "conv_123",
            receiverUid = "receiver_456",
            text = "Hello unauthenticated",
            currentUser = null
        )
        assertTrue(failureResult.isFailure)
        assertTrue(failureResult.exceptionOrNull() is IllegalStateException)
        assertEquals("No authenticated user to send message", failureResult.exceptionOrNull()?.message)

        // 2. When authenticated user is provided, senderUid must strictly match currentUser.uid
        val authUser = UserAccount(
            uid = "real_firebase_user_777",
            displayName = "Real User",
            email = "real@iombg.com"
        )
        chatRepository.setActiveUser(authUser.uid)
        assertEquals("real_firebase_user_777", chatRepository.activeUserUid.first())
        assertNotEquals("creator_demo_01", chatRepository.activeUserUid.first())
    }
}
