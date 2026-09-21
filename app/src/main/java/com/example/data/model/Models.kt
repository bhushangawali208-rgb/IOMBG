package com.example.data.model

import com.google.firebase.Timestamp
import java.util.Date

// ======================== USER ACCOUNT ========================
data class UserAccount(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val photoUrl: String = "",
    val username: String = "",
    val accountType: String = "VIEWER", // VIEWER, CREATOR, STAFF, SUPER_ADMIN
    val role: String = "VIEWER", // Authoritative Firestore role: VIEWER, CREATOR, STAFF, SUPER_ADMIN
    val isSuperAdmin: Boolean = false,
    val isVerified: Boolean = false,
    val channelId: String? = null,
    val isPremium: Boolean = false,
    val premiumExpiresAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ======================== CHANNEL ========================
data class Channel(
    val channelId: String = "",
    val ownerUid: String = "",
    val channelName: String = "",
    val handle: String = "",
    val profileImageUrl: String = "",
    val bannerImageUrl: String = "",
    val description: String = "",
    val category: String = "General",
    val subscriberCount: Long = 0,
    val videoCount: Long = 0,
    val shortsCount: Long = 0,
    val totalViews: Long = 0,
    val totalWatchHours: Double = 0.0,
    val isMonetized: Boolean = false,
    val monetizationStatus: String = "NOT_APPLIED", // NOT_APPLIED, PENDING, APPROVED, REJECTED
    val supportEnabled: Boolean = true,
    val searchName: String = "",
    val searchHandle: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ======================== VIDEO ========================
data class Video(
    val videoId: String = "",
    val channelId: String = "",
    val ownerUid: String = "",
    val channelName: String = "",
    val channelHandle: String = "",
    val channelAvatarUrl: String = "",
    val title: String = "",
    val description: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val durationSeconds: Long = 0,
    val tags: List<String> = emptyList(),
    val category: String = "Entertainment",
    val visibility: String = "PUBLIC", // PUBLIC, UNLISTED, PRIVATE
    val allowComments: Boolean = true,
    val processingStatus: String = "READY", // UPLOADING, PROCESSING, READY, FAILED
    val uploadProgress: Int = 100,
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val dislikeCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val isBoosted: Boolean = false,
    val isSaved: Boolean = false,
    val searchTitle: String = "",
    val searchDescription: String = "",
    val searchTags: List<String> = emptyList(),
    val searchChannelName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ======================== SHORT ========================
data class ShortItem(
    val shortId: String = "",
    val channelId: String = "",
    val ownerUid: String = "",
    val channelName: String = "",
    val channelHandle: String = "",
    val channelAvatarUrl: String = "",
    val title: String = "",
    val description: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val soundTitle: String = "Original Sound",
    val durationSeconds: Long = 0,
    val tags: List<String> = emptyList(),
    val category: String = "General",
    val visibility: String = "PUBLIC",
    val allowComments: Boolean = true,
    val processingStatus: String = "READY",
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val isBoosted: Boolean = false,
    val searchTitle: String = "",
    val searchDescription: String = "",
    val searchTags: List<String> = emptyList(),
    val searchChannelName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ======================== CLOUD UPLOAD EXCEPTION ========================
class CloudUploadUnavailableException(
    val localVideo: Video? = null,
    val localShort: ShortItem? = null,
    message: String = "Cloud video upload is currently unavailable. Your video is available for local preview only."
) : Exception(message)

// ======================== COMMENTS & REPLIES ========================
data class CommentItem(
    val commentId: String = "",
    val contentId: String = "", // videoId or shortId
    val contentType: String = "VIDEO", // VIDEO, SHORT, LIVE
    val authorUid: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val authorAvatarUrl: String = "",
    val text: String = "",
    val parentCommentId: String? = null,
    val likeCount: Long = 0,
    val isLikedByMe: Boolean = false,
    val replyCount: Long = 0,
    val replies: List<CommentReply> = emptyList(),
    val isPinned: Boolean = false,
    val isHeartedByCreator: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class CommentReply(
    val replyId: String = "",
    val parentCommentId: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val authorAvatarUrl: String = "",
    val text: String = "",
    val likeCount: Long = 0,
    val isLikedByMe: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ======================== RECOMMENDATION EVENTS & ENGINE ========================
enum class EventType {
    IMPRESSION,
    CLICK,
    PLAY,
    WATCH_TIME,
    COMPLETION,
    LIKE,
    UNLIKE,
    COMMENT,
    FOLLOW,
    UNFOLLOW,
    SHARE,
    SAVE,
    SKIP,
    NOT_INTERESTED,
    SEARCH,
    VIDEO_OPEN,
    REPORT,
    DISLIKE,
    REPLY,
    WATCH_START,
    LIVE_IMPRESSION,
    LIVE_OPEN,
    LIVE_WATCH,
    LIVE_WATCH_TIME,
    LIVE_JOIN,
    LIVE_LEAVE,
    LIVE_LIKE,
    LIVE_SHARE,
    LIVE_FOLLOW,
    LIVE_CHAT,
    LIVE_SUPER_SUPPORT,
    LIVE_REPORT
}

data class RecommendationEvent(
    val eventId: String = "",
    val userId: String = "",
    val contentId: String = "",
    val contentType: String = "VIDEO", // VIDEO, SHORT, LIVE, CHANNEL
    val eventType: EventType = EventType.IMPRESSION,
    val timestamp: Long = System.currentTimeMillis(),
    val watchDuration: Long = 0,
    val watchDurationSeconds: Long = 0,
    val totalDurationSeconds: Long = 0,
    val completionPercentage: Float = 0f,
    val contentCategory: String = "",
    val category: String = "",
    val creatorId: String = "",
    val searchQuery: String = "",
    val tags: List<String> = emptyList(),
    val isRewatch: Boolean = false
)

data class RecommendationWeights(
    val watchTimeWeight: Float = 3.5f,
    val completionWeight: Float = 4.0f,
    val repeatedViewWeight: Float = 3.0f,
    val likeWeight: Float = 3.5f,
    val followWeight: Float = 5.0f,
    val shareWeight: Float = 4.0f,
    val saveWeight: Float = 4.0f,
    val commentWeight: Float = 3.0f,
    val searchMatchWeight: Float = 3.5f,
    val categoryAffinityWeight: Float = 16.0f,
    val creatorAffinityWeight: Float = 18.0f,
    val trendingVelocityWeight: Float = 2.5f,
    val recencyFreshnessWeight: Float = 3.0f,
    val smallCreatorBoostWeight: Float = 3.0f,
    val skipPenaltyWeight: Float = -2.5f,
    val notInterestedPenaltyWeight: Float = -15.0f,
    val reportPenaltyWeight: Float = -50.0f,
    val explorationDiversityRatio: Float = 0.20f,
    val consecutiveSameCategoryLimit: Int = 2
)

data class ShortsRecommendationWeights(
    val completionWeight: Float = 5.0f,
    val watchDurationWeight: Float = 3.5f,
    val rewatchWeight: Float = 4.0f,
    val likeWeight: Float = 4.0f,
    val shareWeight: Float = 4.5f,
    val followWeight: Float = 5.5f,
    val skipPenaltyWeight: Float = -3.0f,
    val notInterestedPenaltyWeight: Float = -15.0f,
    val categoryAffinityWeight: Float = 12.0f,
    val creatorAffinityWeight: Float = 15.0f,
    val smallCreatorBoostWeight: Float = 2.5f,
    val consecutiveSkipPenalty: Float = -5.0f
)

data class CreatorRecommendation(
    val channel: Channel,
    val matchScore: Float = 0f,
    val matchedCategory: String = "",
    val isEmergingCreator: Boolean = false,
    val reason: String = "Popular in your favorite topics"
)

// ======================== SOCIAL / ENGAGEMENT ========================
data class FollowRecord(
    val followId: String = "",
    val followerUid: String = "",
    val targetChannelId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class LikeRecord(
    val likeId: String = "",
    val userId: String = "",
    val contentId: String = "",
    val contentType: String = "VIDEO", // VIDEO, SHORT, COMMENT
    val isLike: Boolean = true, // true for like, false for dislike
    val createdAt: Long = System.currentTimeMillis()
)

data class BlockRecord(
    val blockId: String = "",
    val blockerUid: String = "",
    val blockedUid: String = "",
    val blockedUserName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ======================== NOTIFICATIONS ========================
enum class NotificationType {
    NEW_FOLLOWER,
    LIKE,
    VIDEO_LIKE,
    COMMENT_LIKE,
    COMMENT,
    REPLY,
    MESSAGE,
    MESSAGE_REQUEST,
    MESSAGE_REQUEST_ACCEPTED,
    LIVE_STARTED,
    MONETIZATION_STATUS,
    PAYOUT_STATUS,
    SUPPORT_RECEIVED,
    SYSTEM_ALERT
}

data class NotificationItem(
    val notificationId: String = "",
    val recipientUid: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderAvatarUrl: String = "",
    val type: NotificationType = NotificationType.SYSTEM_ALERT,
    val title: String = "",
    val body: String = "",
    val targetContentId: String? = null,
    val targetContentType: String? = null,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// ======================== CHAT & MESSAGING ========================
enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO_CLIP,
    SUPPORT_ATTACHMENT,
    SYSTEM
}

enum class MessageDeliveryStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

enum class MessageModerationStatus {
    VISIBLE,
    REPORTED,
    HIDDEN_BY_MODERATOR,
    DELETED
}

enum class MessageRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}

enum class MessagePermission {
    EVERYONE,
    FOLLOWED_ONLY,
    NOBODY
}

enum class MessageReportCategory {
    SPAM,
    HARASSMENT,
    HATE_ABUSE,
    SCAM_FRAUD,
    SEXUAL_CONTENT,
    THREATS,
    OTHER
}

data class ChatConversation(
    val conversationId: String = "",
    val participantUids: List<String> = emptyList(),
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantHandles: Map<String, String> = emptyMap(),
    val participantAvatars: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastMessageText: String = "",
    val lastMessagePreview: String = "",
    val lastMessageSenderUid: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val lastMessageAt: Long = System.currentTimeMillis(),
    val unreadCounts: Map<String, Int> = emptyMap(),
    val isRequest: Boolean = false,
    val requestStatus: MessageRequestStatus = MessageRequestStatus.ACCEPTED,
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val isBlocked: Boolean = false,
    val blockedByUid: String? = null,
    val blockedBy: List<String> = emptyList()
)

data class ChatMessage(
    val messageId: String = "",
    val conversationId: String = "",
    val senderUid: String = "",
    val receiverUid: String = "",
    val senderName: String = "",
    val text: String = "",
    val messageType: MessageType = MessageType.TEXT,
    val replyToMessageId: String? = null,
    val replyToSnippet: String? = null,
    val replyToSenderName: String? = null,
    val mediaUrl: String? = null,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val deletedAt: Long? = null,
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
    val moderationStatus: MessageModerationStatus = MessageModerationStatus.VISIBLE,
    val timestamp: Long = System.currentTimeMillis()
)

data class UserPresence(
    val uid: String = "",
    val isOnline: Boolean = false,
    val lastActiveAt: Long = System.currentTimeMillis(),
    val statusText: String = ""
)

data class ChatSettings(
    val userId: String = "",
    val whoCanMessageMe: MessagePermission = MessagePermission.EVERYONE,
    val allowMessageRequests: Boolean = true,
    val filterSpamRequests: Boolean = true,
    val showOnlineStatus: Boolean = true,
    val sendReadReceipts: Boolean = true,
    val blockedUserIds: List<String> = emptyList()
)

data class MessageReport(
    val reportId: String = "",
    val reporterUid: String = "",
    val reportedUid: String = "",
    val conversationId: String = "",
    val messageId: String? = null,
    val category: MessageReportCategory = MessageReportCategory.SPAM,
    val reason: String = "",
    val details: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "PENDING"
)

data class MessagingRateLimitState(
    val lastMessageTime: Long = 0,
    val recentMessageTimestamps: List<Long> = emptyList(),
    val lastMessageText: String = "",
    val cooldownUntil: Long = 0
)

// ======================== LIVE STREAMING ========================
enum class StreamHealth {
    EXCELLENT,
    GOOD,
    POOR,
    RECONNECTING
}

enum class LiveModerationStatus {
    VISIBLE,
    DELETED_BY_CREATOR,
    DELETED_BY_USER,
    FLAGGED,
    BLOCKED
}

enum class LiveModerationType {
    DELETE_MESSAGE,
    MUTE_USER,
    BLOCK_USER,
    ENABLE_SLOW_MODE,
    DISABLE_CHAT,
    REPORT_ABUSE
}

data class LiveStream(
    val streamId: String = "",
    val channelId: String = "",
    val ownerUid: String = "",
    val channelName: String = "",
    val channelHandle: String = "",
    val channelAvatarUrl: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "Gaming",
    val tags: List<String> = emptyList(),
    val thumbnailUrl: String = "",
    val streamUrl: String = "", // HLS / WebRTC playback URL
    val visibility: String = "PUBLIC", // PUBLIC, UNLISTED, PRIVATE
    val isLive: Boolean = true,
    val status: String = "AWAITING_BROADCAST", // AWAITING_BROADCAST, LIVE, ENDED
    val viewerCount: Long = 0,
    val peakViewers: Long = 0,
    val totalViews: Long = 0,
    val likeCount: Long = 0,
    val chatMessageCount: Long = 0,
    val isChatEnabled: Boolean = true,
    val isSlowModeEnabled: Boolean = false,
    val slowModeIntervalSeconds: Int = 5,
    val replayAvailable: Boolean = true,
    val replayVideoUrl: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val durationSeconds: Long = 0,
    val isBoosted: Boolean = false,
    val streamHealth: StreamHealth = StreamHealth.EXCELLENT,
    val isDevSandbox: Boolean = true
)

data class LiveMessage(
    val messageId: String = "",
    val streamId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderAvatarUrl: String = "",
    val text: String = "",
    val isSuperSupport: Boolean = false,
    val supportAmount: Double = 0.0,
    val moderationStatus: LiveModerationStatus = LiveModerationStatus.VISIBLE,
    val timestamp: Long = System.currentTimeMillis(),
    val isCreator: Boolean = false,
    val isVerified: Boolean = false
)

data class LiveModerationAction(
    val actionId: String = "",
    val streamId: String = "",
    val targetUid: String = "",
    val targetMessageId: String? = null,
    val actionType: LiveModerationType = LiveModerationType.DELETE_MESSAGE,
    val reason: String = "",
    val performedByUid: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val muteDurationSeconds: Long = 60
)

data class LiveAnalytics(
    val streamId: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val title: String = "",
    val peakViewers: Long = 0,
    val averageViewers: Long = 0,
    val totalViews: Long = 0,
    val totalWatchTimeSeconds: Long = 0,
    val averageWatchDurationSeconds: Long = 0,
    val likes: Long = 0,
    val shares: Long = 0,
    val followersGained: Long = 0,
    val chatMessages: Long = 0,
    val supportRevenue: Double = 0.0,
    val streamDurationSeconds: Long = 0,
    val endedAt: Long = System.currentTimeMillis()
)

data class LiveReplay(
    val replayId: String = "",
    val streamId: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val channelHandle: String = "",
    val channelAvatarUrl: String = "",
    val title: String = "",
    val description: String = "",
    val thumbnailUrl: String = "",
    val videoUrl: String = "",
    val durationSeconds: Long = 0,
    val views: Long = 0,
    val likes: Long = 0,
    val category: String = "General",
    val tags: List<String> = emptyList(),
    val visibility: String = "PUBLIC",
    val createdAt: Long = System.currentTimeMillis()
)

// ======================== CREATOR WALLET & LEDGER ========================
data class Wallet(
    val walletId: String = "",
    val channelId: String = "",
    val ownerUid: String = "",
    val availableBalance: Double = 0.0,
    val pendingBalance: Double = 0.0,
    val lifetimeEarnings: Double = 0.0,
    val totalAdEarnings: Double = 0.0,
    val totalSupportEarnings: Double = 0.0,
    val totalPremiumEarnings: Double = 0.0,
    val totalBoostEarnings: Double = 0.0,
    val totalPaidOut: Double = 0.0,
    val totalTaxWithheld: Double = 0.0,
    val currency: String = "INR",
    val updatedAt: Long = System.currentTimeMillis()
)

enum class LedgerEntryType {
    PREMIUM,
    THANKS_SUPPORT,
    BOOST,
    AD_REVENUE,
    REFUND,
    CHARGEBACK,
    ADJUSTMENT,
    PAYOUT_DEDUCTION,
    SUPPORT_THANKS, // Alias for backward compatibility
    PREMIUM_REVENUE_SHARE, // Alias
    BOOST_REVENUE_SHARE, // Alias
    REFUND_ADJUSTMENT, // Alias
    REVERSAL, // Alias
    ADMIN_CORRECTION // Alias
}

enum class TransactionType {
    PREMIUM,
    THANKS_SUPPORT,
    BOOST,
    AD_REVENUE,
    REFUND,
    CHARGEBACK,
    ADJUSTMENT,
    PAYOUT_DEDUCTION
}

enum class PaymentOperationStatus {
    INITIATED,
    PROCESSING,
    SUCCESSFUL,
    FAILED,
    CANCELLED,
    REFUNDED,
    REVERSED
}

data class LedgerEntry(
    val entryId: String = "",
    val transactionId: String = "", // Alias
    val channelId: String = "",
    val creatorId: String = "",
    val userId: String = "",
    val contentId: String? = null,
    val walletId: String = "",
    val type: LedgerEntryType = LedgerEntryType.AD_REVENUE,
    val transactionType: TransactionType = TransactionType.AD_REVENUE,
    val grossAmount: Double = 0.0,
    val processingFee: Double = 0.0,
    val taxOrWithholding: Double = 0.0,
    val creatorAmount: Double = 0.0,
    val platformAmount: Double = 0.0,
    val creatorShareAmount: Double = 0.0, // Alias
    val platformShareAmount: Double = 0.0, // Alias
    val creatorPercentage: Double = 70.0, // e.g. 70.0 for Support, 55.0 for Ad
    val currency: String = "INR",
    val description: String = "",
    val referenceId: String = "", // transactionId, payoutId, or videoId
    val provider: String = "DEVELOPMENT_SANDBOX_GATEWAY",
    val providerTransactionId: String = "",
    val idempotencyKey: String = "",
    val status: PaymentOperationStatus = PaymentOperationStatus.SUCCESSFUL,
    val isFinalized: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = System.currentTimeMillis()
)

data class FinancialTransaction(
    val transactionId: String = "",
    val userId: String = "",
    val creatorId: String? = null,
    val contentId: String? = null,
    val type: TransactionType = TransactionType.THANKS_SUPPORT,
    val grossAmount: Double = 0.0,
    val processingFee: Double = 0.0,
    val taxOrWithholding: Double = 0.0,
    val creatorAmount: Double = 0.0,
    val platformAmount: Double = 0.0,
    val currency: String = "INR",
    val status: PaymentOperationStatus = PaymentOperationStatus.SUCCESSFUL,
    val provider: String = "DEVELOPMENT_SANDBOX_GATEWAY",
    val providerTransactionId: String = "",
    val idempotencyKey: String = "",
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = System.currentTimeMillis()
)

data class PaymentFeeConfig(
    val processingFeePercent: Double = 2.0, // 2.0% Payment Gateway Fee
    val withholdingTaxPercent: Double = 1.0, // 1.0% TDS Withholding
    val supportCreatorPercent: Double = 70.0, // 70% Creator Net Revenue Share
    val supportPlatformPercent: Double = 30.0, // 30% IOMBG Platform Cut
    val adCreatorPercent: Double = 55.0, // 55% Creator Ad Revenue Pool
    val adPlatformPercent: Double = 45.0, // 45% Platform Ad Cut
    val minPayoutThresholdInr: Double = 1000.0 // Min Payout Limit (₹1,000)
)

// ======================== THANKS / SUPPORT TRANSACTION ========================
data class SupportTransaction(
    val transactionId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val targetChannelId: String = "",
    val contentId: String? = null,
    val contentType: String? = null, // VIDEO, SHORT, LIVE
    val grossAmount: Double = 0.0,
    val processingFee: Double = 0.0,
    val taxOrWithholding: Double = 0.0,
    val creatorAmount: Double = 0.0, // Net Creator Split
    val platformFeeAmount: Double = 0.0, // Platform Split
    val currency: String = "INR",
    val message: String = "",
    val paymentStatus: String = "SUCCESS", // SUCCESS, PENDING, FAILED, REFUNDED
    val status: PaymentOperationStatus = PaymentOperationStatus.SUCCESSFUL,
    val paymentGatewayId: String = "",
    val providerTransactionId: String = "",
    val idempotencyKey: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = System.currentTimeMillis()
)

// ======================== MONETIZATION APPLICATION ========================
enum class ApplicationStatus {
    NOT_ELIGIBLE,
    ELIGIBLE,
    APPLICATION_PENDING,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    SUSPENDED
}

enum class EligibilityPath {
    LONG_FORM,
    SHORTS,
    BOTH,
    NONE
}

enum class KYCStatus {
    NOT_SUBMITTED,
    DOCUMENTS_UPLOADED,
    VERIFIED,
    FAILED
}

data class MonetizationApplication(
    val applicationId: String = "",
    val channelId: String = "",
    val creatorId: String = "",
    val ownerUid: String = "",
    val channelName: String = "",
    val eligibilityPath: EligibilityPath = EligibilityPath.NONE,
    val status: ApplicationStatus = ApplicationStatus.NOT_ELIGIBLE,
    val subscriberCount: Long = 0,
    val eligibleWatchHoursLast12M: Double = 0.0,
    val eligibleShortsViewsLast90D: Long = 0,
    val meetsLongVideoCriteria: Boolean = false,
    val meetsShortsCriteria: Boolean = false,
    val isTermsAccepted: Boolean = false,
    val kycStatus: KYCStatus = KYCStatus.NOT_SUBMITTED,
    val panNumber: String = "",
    val legalName: String = "",
    val applicationStatus: ApplicationStatus = ApplicationStatus.NOT_ELIGIBLE, // Alias for backward compatibility
    val rejectionReason: String? = null,
    val reviewerId: String? = null,
    val reviewedByAdminUid: String? = null, // Alias
    val submittedAt: Long = System.currentTimeMillis(),
    val appliedAt: Long = System.currentTimeMillis(), // Alias
    val reviewedAt: Long? = null,
    val canReapplyAt: Long? = null
)

// ======================== PAYOUT REQUEST ========================
enum class PayoutStatus {
    REQUESTED,
    UNDER_REVIEW,
    PROCESSING,
    PAID,
    FAILED,
    CANCELLED,
    REVERSED,
    PENDING // Alias for REQUESTED
}

data class PayoutRequest(
    val payoutId: String = "",
    val creatorId: String = "",
    val channelId: String = "",
    val ownerUid: String = "",
    val amount: Double = 0.0,
    val currency: String = "INR",
    val paymentMethod: String = "UPI", // UPI, BANK_TRANSFER
    val upiId: String = "",
    val bankAccountNumber: String = "",
    val bankIfsc: String = "",
    val accountHolderName: String = "",
    val kycVerified: Boolean = false,
    val status: PayoutStatus = PayoutStatus.REQUESTED,
    val failureReason: String? = null,
    val providerReference: String? = null,
    val transactionReference: String? = null, // Alias
    val processedByAdminUid: String? = null,
    val requestedAt: Long = System.currentTimeMillis(),
    val processedAt: Long? = null
)

// ======================== PREMIUM SUBSCRIPTION ========================
enum class SubscriptionStatus {
    PENDING,
    ACTIVE,
    CANCELLED,
    EXPIRED,
    PAYMENT_FAILED,
    REFUNDED,
    FAILED // Alias for PAYMENT_FAILED
}

data class PremiumPlan(
    val planId: String = "iombg_premium_monthly",
    val name: String = "IOMBG Premium Monthly",
    val price: Double = 129.0,
    val priceRupees: Int = 129,
    val currency: String = "INR",
    val billingPeriod: String = "MONTHLY", // MONTHLY, ANNUAL, QUARTERLY
    val billingPeriodDays: Int = 30,
    val benefits: List<String> = listOf(
        "Ad-Free Viewing across all Videos & Shorts",
        "Gold Premium Badge on Profile and Comments",
        "Background Playback & Picture-in-Picture Mode",
        "Ultra High 4K Bitrate & Spatial Audio Streaming",
        "Exclusive Access to Premium Creator Masterclasses",
        "Direct Creator Pool Allocation from Monthly Membership"
    ),
    val active: Boolean = true
)

data class PremiumSubscription(
    val subscriptionId: String = "",
    val userId: String = "",
    val planId: String = "",
    val planName: String = "",
    val provider: String = "DEVELOPMENT_SANDBOX_GATEWAY",
    val providerSubscriptionId: String = "",
    val status: String = "ACTIVE", // ACTIVE, CANCELLED, EXPIRED, PENDING, PAYMENT_FAILED, REFUNDED
    val subscriptionStatus: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val amount: Double = 129.0,
    val price: Double = 129.0,
    val currency: String = "INR",
    val startedAt: Long = System.currentTimeMillis(),
    val startDate: Long = System.currentTimeMillis(),
    val renewalAt: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000),
    val expiryDate: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000),
    val cancelledAt: Long? = null,
    val autoRenew: Boolean = true,
    val orderId: String = "",
    val purchaseToken: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ======================== VIDEO & SHORTS BOOST ========================
enum class BoostStatus {
    DRAFT,
    PENDING_PAYMENT,
    ACTIVE,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class BoostOption(
    val optionId: String = "",
    val title: String = "",
    val budget: Double = 500.0,
    val currency: String = "INR",
    val durationDays: Int = 7,
    val estimatedReach: String = "5,000 - 10,000 potential impressions",
    val recommendedFor: String = "Emerging creators looking for initial discovery"
)

data class BoostCampaign(
    val boostId: String = "",
    val campaignId: String = "",
    val payerUserId: String = "",
    val creatorId: String = "",
    val channelId: String = "",
    val ownerUid: String = "",
    val contentId: String = "",
    val targetContentId: String = "",
    val contentType: String = "VIDEO", // VIDEO or SHORT
    val targetContentType: String = "VIDEO",
    val contentTitle: String = "",
    val contentThumbnailUrl: String = "",
    val budget: Double = 500.0,
    val budgetAmount: Double = 500.0,
    val currency: String = "INR",
    val duration: Int = 7,
    val targetCategory: String = "All",
    val estimatedImpressions: String = "5,000 - 10,000",
    val deliveredImpressions: Long = 0,
    val generatedViews: Long = 0,
    val generatedClicks: Long = 0,
    val totalWatchTimeMinutes: Double = 0.0,
    val status: BoostStatus = BoostStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long = System.currentTimeMillis(),
    val startDate: Long = System.currentTimeMillis(),
    val endedAt: Long = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000),
    val endDate: Long = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000)
)

// ======================== MODERATION & REPORTS ========================
enum class ReportReason {
    SPAM,
    HARASSMENT,
    HATE_ABUSE,
    SEXUAL_CONTENT,
    VIOLENCE,
    SCAM_FRAUD,
    COPYRIGHT,
    THREATS,
    OTHER
}

enum class ReportStatus {
    OPEN,
    REVIEWING,
    RESOLVED,
    DISMISSED
}

enum class ContentModerationStatus {
    PUBLIC,
    HIDDEN,
    REMOVED,
    UNDER_REVIEW,
    RESTRICTED
}

data class ReportItem(
    val reportId: String = "",
    val reporterUid: String = "",
    val targetId: String = "",
    val targetType: String = "VIDEO", // VIDEO, SHORT, CHANNEL, COMMENT, USER, LIVE, MESSAGE
    val reason: String = "",
    val reasonEnum: ReportReason = ReportReason.OTHER,
    val details: String = "",
    val evidenceSummary: String = "",
    val status: String = "OPEN", // OPEN, REVIEWING, RESOLVED, DISMISSED
    val statusEnum: ReportStatus = ReportStatus.OPEN,
    val actionTaken: String? = null,
    val reviewedByUid: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null
)

// ======================== USER MODERATION ========================
enum class AccountModerationStatus {
    ACTIVE,
    RESTRICTED,
    SUSPENDED,
    BANNED
}

data class AdminUserSummary(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val accountType: String = "VIEWER",
    val status: AccountModerationStatus = AccountModerationStatus.ACTIVE,
    val isCreator: Boolean = false,
    val channelId: String? = null,
    val channelName: String? = null,
    val subscriberCount: Long = 0,
    val totalVideos: Int = 0,
    val isMonetized: Boolean = false,
    val isPremium: Boolean = false,
    val isUploadRestricted: Boolean = false,
    val isCommentRestricted: Boolean = false,
    val isMessageRestricted: Boolean = false,
    val suspensionReason: String? = null,
    val suspendedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
    val totalWatchMinutes: Long = 0,
    val totalViews: Long = 0
)

// ======================== FRAUD DETECTION ========================
enum class FraudRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class FraudSignalCategory {
    ACCOUNT,
    ENGAGEMENT,
    PAYMENTS,
    CONTENT
}

enum class FraudAlertStatus {
    OPEN,
    INVESTIGATING,
    RESOLVED,
    FALSE_POSITIVE
}

data class FraudSignal(
    val signalId: String = "",
    val targetUid: String = "",
    val targetChannelId: String? = null,
    val targetContentId: String? = null,
    val signalType: String = "", // BOT_TRAFFIC, SUSPICIOUS_WATCHTIME, FAKE_ENGAGEMENT, RAPID_PAYOUT_REQUEST
    val riskLevel: FraudRiskLevel = FraudRiskLevel.MEDIUM,
    val confidenceScore: Float = 0.85f,
    val evidenceDetails: String = "",
    val isReviewed: Boolean = false,
    val reviewStatus: String = "UNDER_INVESTIGATION", // UNDER_INVESTIGATION, CLEARED, ACTIONED
    val detectedAt: Long = System.currentTimeMillis()
)

data class FraudAlert(
    val alertId: String = "",
    val userId: String? = null,
    val channelId: String? = null,
    val contentId: String? = null,
    val transactionId: String? = null,
    val category: FraudSignalCategory = FraudSignalCategory.ENGAGEMENT,
    val signalType: String = "",
    val riskLevel: FraudRiskLevel = FraudRiskLevel.MEDIUM,
    val score: Float = 0.85f,
    val evidenceSummary: String = "",
    val status: FraudAlertStatus = FraudAlertStatus.OPEN,
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val resolvedByAdminUid: String? = null,
    val mitigationAction: String? = null
)

// ======================== ADMIN SUBSCRIBER & CAMPAIGN SUMMARY ========================
data class AdminSubscriberSummary(
    val subscriptionId: String = "",
    val userUid: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val planId: String = "iombg_premium_monthly",
    val planName: String = "IOMBG Premium Monthly",
    val amountPaidInr: Double = 129.0,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val paymentGatewayId: String = "pay_mock_gateway_01",
    val startedAt: Long = System.currentTimeMillis() - 86400000L * 14,
    val renewsAt: Long = System.currentTimeMillis() + 86400000L * 16
)

// ======================== PLATFORM AGGREGATE METRICS ========================
data class PlatformAggregateMetrics(
    val totalUsers: Long = 142500,
    val totalCreators: Long = 24800,
    val totalChannels: Long = 24800,
    val totalVideos: Long = 68420,
    val totalShorts: Long = 189300,
    val totalLiveStreams: Long = 340,
    val activeDailyUsers: Long = 89400,
    val activeMonthlyUsers: Long = 138000,
    val totalViews: Long = 42800000,
    val totalWatchHours: Double = 3120000.0,
    val totalFollowers: Long = 984000,
    val monetizedCreators: Long = 1420,
    val premiumSubscribers: Long = 8450,
    val activeBoostCampaigns: Long = 64,
    val pendingPayoutsCount: Long = 0,
    val pendingPayoutsAmountInr: Double = 0.0,
    val platformGrossRevenueInr: Double = 0.0,
    val creatorPoolDistributedInr: Double = 0.0,
    val platformNetEarningsInr: Double = 0.0,
    val pendingReportsCount: Long = 5,
    val activeFraudAlertsCount: Long = 4,
    val isDevelopmentData: Boolean = true
)

// ======================== ADMIN AUDIT LOG ========================
data class AdminAuditLog(
    val logId: String = "",
    val adminId: String = "",
    val adminEmail: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val previousValue: String? = null,
    val newValue: String? = null,
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// ======================== PLATFORM SETTINGS ========================
data class PlatformSettings(
    val platformName: String = "IOMBG",
    val superAdminUid: String = "",
    val superAdminEmail: String = "",
    val supportCreatorPercent: Double = 70.0,
    val supportPlatformPercent: Double = 30.0,
    val adCreatorPoolPercent: Double = 55.0,
    val adPlatformPercent: Double = 45.0,
    val minPayoutThresholdInr: Double = 5000.0,
    val longVideoMinFollowers: Long = 500,
    val longVideoMinWatchHours: Double = 500.0,
    val shortsMinFollowers: Long = 500,
    val shortsMinViews: Long = 100000,
    val isRegistrationOpen: Boolean = true,
    val isPayoutsEnabled: Boolean = true,
    val isBoostEnabled: Boolean = true,
    val isPremiumEnabled: Boolean = true,
    val autoSpamFilterSensitivity: String = "BALANCED", // LENIENT, BALANCED, STRICT
    val maxMessagesPerMinute: Int = 30
)
