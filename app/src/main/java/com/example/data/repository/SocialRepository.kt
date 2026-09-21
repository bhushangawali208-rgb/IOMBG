package com.example.data.repository

import android.util.Log
import com.example.data.model.*
import com.example.data.remote.FirebaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SocialRepository(
    private val firebaseService: FirebaseService,
    private val recommendationEventRepository: RecommendationEventRepository? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // ======================== NOTIFICATIONS ========================
    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _isLoadingNotifications = MutableStateFlow<Boolean>(false)
    val isLoadingNotifications: StateFlow<Boolean> = _isLoadingNotifications.asStateFlow()

    private val _notificationsError = MutableStateFlow<String?>(null)
    val notificationsError: StateFlow<String?> = _notificationsError.asStateFlow()

    private var currentObservedUid: String? = null
    private var notificationsJob: Job? = null

    val unreadNotificationsCount: StateFlow<Int> = _notifications.map { list ->
        list.count { !it.isRead }
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    // ======================== FOLLOW SYSTEM ========================
    private val _followedChannels = MutableStateFlow<Set<String>>(emptySet())
    val followedChannels: StateFlow<Set<String>> = _followedChannels.asStateFlow()

    // ======================== LIKE SYSTEM ========================
    private val _likedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val likedVideoIds: StateFlow<Set<String>> = _likedVideoIds.asStateFlow()

    private val _likedShortIds = MutableStateFlow<Set<String>>(emptySet())
    val likedShortIds: StateFlow<Set<String>> = _likedShortIds.asStateFlow()

    private val _likedCommentIds = MutableStateFlow<Set<String>>(emptySet())
    val likedCommentIds: StateFlow<Set<String>> = _likedCommentIds.asStateFlow()

    // ======================== BLOCK SYSTEM ========================
    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds.asStateFlow()

    // ======================== NOT INTERESTED / HIDE ========================
    private val _notInterestedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val notInterestedVideoIds: StateFlow<Set<String>> = _notInterestedVideoIds.asStateFlow()

    // ======================== HIDDEN CHANNELS ========================
    private val _hiddenChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenChannelIds: StateFlow<Set<String>> = _hiddenChannelIds.asStateFlow()

    // ======================== BOOST CAMPAIGNS ========================
    private val _boostCampaigns = MutableStateFlow<List<BoostCampaign>>(emptyList())
    val boostCampaigns: StateFlow<List<BoostCampaign>> = _boostCampaigns.asStateFlow()

    init {
        loadInitialSocialData()
        val currentUid = firebaseService.currentFirebaseUser?.uid
        if (!currentUid.isNullOrBlank()) {
            observeUserNotifications(currentUid)
            syncFollowedChannels(currentUid)
        }
    }

    fun syncFollowedChannels(userId: String) {
        if (userId.isBlank()) {
            _followedChannels.value = emptySet()
            return
        }
        scope.launch {
            try {
                val follows = firebaseService.fetchFollowedChannelIds(userId)
                _followedChannels.value = follows
            } catch (e: Exception) {
                Log.e("SocialRepository", "Error syncing followed channels: ${e.message}")
            }
        }
    }

    fun clearFollowedChannels() {
        _followedChannels.value = emptySet()
    }

    private fun loadInitialSocialData() {
        val sampleBoosts = listOf(
            BoostCampaign(
                campaignId = "bst_001",
                channelId = "ch_01",
                ownerUid = "creator_demo_01",
                targetContentId = "vid_001",
                targetContentType = "VIDEO",
                contentTitle = "The Next Generation of AI Supercomputers: Deep Architecture Review",
                budgetAmount = 1500.0,
                targetCategory = "Technology",
                estimatedImpressions = "15,000 - 25,000",
                deliveredImpressions = 18450,
                generatedViews = 4820,
                generatedClicks = 5410,
                status = BoostStatus.ACTIVE
            )
        )
        _boostCampaigns.value = sampleBoosts
    }

    // ======================== FOLLOW ACTIONS ========================
    fun isFollowing(channelId: String): Boolean {
        return _followedChannels.value.contains(channelId)
    }

    fun toggleFollowChannel(
        targetChannelId: String,
        targetOwnerUid: String? = null,
        targetChannelName: String = "",
        follower: UserAccount? = null
    ): Boolean {
        val current = _followedChannels.value
        val followerUid = follower?.uid ?: "current_user"
        val followerName = follower?.displayName ?: "IOMBG Member"
        val followerAvatar = follower?.photoUrl ?: ""

        val isNowFollowing = if (current.contains(targetChannelId)) {
            _followedChannels.value = current - targetChannelId
            scope.launch {
                firebaseService.unfollowChannel(followerUid, targetChannelId)
                recommendationEventRepository?.recordEvent(
                    userId = followerUid,
                    contentId = targetChannelId,
                    contentType = "CHANNEL",
                    eventType = EventType.UNFOLLOW
                )
            }
            false
        } else {
            _followedChannels.value = current + targetChannelId
            scope.launch {
                firebaseService.followChannel(
                    followerUid = followerUid,
                    followerName = followerName,
                    followerAvatar = followerAvatar,
                    targetChannelId = targetChannelId,
                    targetOwnerUid = targetOwnerUid
                )
                recommendationEventRepository?.recordEvent(
                    userId = followerUid,
                    contentId = targetChannelId,
                    contentType = "CHANNEL",
                    eventType = EventType.FOLLOW
                )
            }
            true
        }
        return isNowFollowing
    }

    // ======================== LIKE ACTIONS ========================
    fun isLikedVideo(videoId: String): Boolean = _likedVideoIds.value.contains(videoId)
    fun isLikedShort(shortId: String): Boolean = _likedShortIds.value.contains(shortId)
    fun isLikedComment(commentId: String): Boolean = _likedCommentIds.value.contains(commentId)

    fun toggleLikeVideo(video: Video, user: UserAccount?): Boolean {
        val current = _likedVideoIds.value
        val userId = user?.uid ?: "current_user"
        val userName = user?.displayName ?: "IOMBG Member"
        val userAvatar = user?.photoUrl ?: ""

        val isNowLiked = if (current.contains(video.videoId)) {
            _likedVideoIds.value = current - video.videoId
            scope.launch {
                firebaseService.unlikeContent(userId, video.videoId, "VIDEO")
                recommendationEventRepository?.recordEvent(
                    userId = userId,
                    contentId = video.videoId,
                    contentType = "VIDEO",
                    eventType = EventType.UNLIKE,
                    category = video.category,
                    tags = video.tags
                )
            }
            false
        } else {
            _likedVideoIds.value = current + video.videoId
            scope.launch {
                firebaseService.likeContent(
                    userId = userId,
                    contentId = video.videoId,
                    contentType = "VIDEO",
                    targetAuthorUid = video.ownerUid,
                    targetTitle = video.title,
                    senderName = userName,
                    senderAvatar = userAvatar
                )
                recommendationEventRepository?.recordEvent(
                    userId = userId,
                    contentId = video.videoId,
                    contentType = "VIDEO",
                    eventType = EventType.LIKE,
                    category = video.category,
                    tags = video.tags
                )
            }
            true
        }
        return isNowLiked
    }

    fun toggleLikeShort(short: ShortItem, user: UserAccount?): Boolean {
        val current = _likedShortIds.value
        val userId = user?.uid ?: "current_user"
        val userName = user?.displayName ?: "IOMBG Member"
        val userAvatar = user?.photoUrl ?: ""

        val isNowLiked = if (current.contains(short.shortId)) {
            _likedShortIds.value = current - short.shortId
            scope.launch {
                firebaseService.unlikeContent(userId, short.shortId, "SHORT")
                recommendationEventRepository?.recordEvent(
                    userId = userId,
                    contentId = short.shortId,
                    contentType = "SHORT",
                    eventType = EventType.UNLIKE,
                    category = short.category,
                    tags = short.tags
                )
            }
            false
        } else {
            _likedShortIds.value = current + short.shortId
            scope.launch {
                firebaseService.likeContent(
                    userId = userId,
                    contentId = short.shortId,
                    contentType = "SHORT",
                    targetAuthorUid = short.ownerUid,
                    targetTitle = short.title,
                    senderName = userName,
                    senderAvatar = userAvatar
                )
                recommendationEventRepository?.recordEvent(
                    userId = userId,
                    contentId = short.shortId,
                    contentType = "SHORT",
                    eventType = EventType.LIKE,
                    category = short.category,
                    tags = short.tags
                )
            }
            true
        }
        return isNowLiked
    }

    fun toggleLikeComment(
        contentId: String,
        commentId: String,
        authorUid: String? = null,
        user: UserAccount? = null
    ): Boolean {
        val current = _likedCommentIds.value
        val userId = user?.uid ?: "current_user"
        val userName = user?.displayName ?: "IOMBG Member"
        val userAvatar = user?.photoUrl ?: ""

        val isNowLiked = if (current.contains(commentId)) {
            _likedCommentIds.value = current - commentId
            scope.launch {
                firebaseService.likeComment(
                    userId = userId,
                    commentId = commentId,
                    isLiked = false,
                    authorUid = authorUid,
                    userName = userName,
                    userAvatar = userAvatar
                )
            }
            false
        } else {
            _likedCommentIds.value = current + commentId
            scope.launch {
                firebaseService.likeComment(
                    userId = userId,
                    commentId = commentId,
                    isLiked = true,
                    authorUid = authorUid,
                    userName = userName,
                    userAvatar = userAvatar
                )
            }
            true
        }
        return isNowLiked
    }

    // ======================== NOTIFICATION ACTIONS ========================
    fun markNotificationAsRead(notificationId: String) {
        if (notificationId.isBlank()) return
        _notifications.value = _notifications.value.map {
            if (it.notificationId == notificationId) it.copy(isRead = true) else it
        }
        scope.launch {
            firebaseService.markNotificationAsRead(notificationId)
        }
    }

    fun markAllNotificationsAsRead(userId: String = "") {
        val targetUid = userId.ifBlank { currentObservedUid ?: firebaseService.currentFirebaseUser?.uid ?: return }
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        scope.launch {
            firebaseService.markAllNotificationsAsRead(targetUid)
        }
    }

    fun deleteNotification(notificationId: String) {
        if (notificationId.isBlank()) return
        _notifications.value = _notifications.value.filter { it.notificationId != notificationId }
        scope.launch {
            firebaseService.deleteNotification(notificationId)
        }
    }

    fun createNotification(notification: NotificationItem) {
        if (notification.recipientUid.isBlank() || notification.recipientUid == notification.senderUid) {
            return
        }
        scope.launch {
            firebaseService.createNotification(notification)
        }
    }

    fun addNotification(item: NotificationItem) {
        createNotification(item)
    }

    // ======================== BLOCK SYSTEM ACTIONS ========================
    fun isUserBlocked(userId: String): Boolean = _blockedUserIds.value.contains(userId)

    fun blockUser(blockerUid: String, blockedUid: String, blockedUserName: String = "") {
        _blockedUserIds.value = _blockedUserIds.value + blockedUid
        scope.launch {
            firebaseService.blockUser(blockerUid, blockedUid, blockedUserName)
        }
    }

    fun unblockUser(blockerUid: String, blockedUid: String) {
        _blockedUserIds.value = _blockedUserIds.value - blockedUid
        scope.launch {
            firebaseService.unblockUser(blockerUid, blockedUid)
        }
    }

    // ======================== NOT INTERESTED & HIDE ========================
    fun markNotInterested(videoId: String, category: String = "", user: UserAccount? = null) {
        _notInterestedVideoIds.value = _notInterestedVideoIds.value + videoId
        scope.launch {
            recommendationEventRepository?.recordEvent(
                userId = user?.uid ?: "current_user",
                contentId = videoId,
                contentType = "VIDEO",
                eventType = EventType.NOT_INTERESTED,
                category = category
            )
        }
    }

    fun hideChannel(channelId: String, user: UserAccount? = null) {
        _hiddenChannelIds.value = _hiddenChannelIds.value + channelId
        scope.launch {
            recommendationEventRepository?.recordEvent(
                userId = user?.uid ?: "current_user",
                contentId = channelId,
                contentType = "CHANNEL",
                eventType = EventType.NOT_INTERESTED
            )
        }
    }

    fun undoNotInterested(videoId: String) {
        _notInterestedVideoIds.value = _notInterestedVideoIds.value - videoId
    }

    fun undoHideChannel(channelId: String) {
        _hiddenChannelIds.value = _hiddenChannelIds.value - channelId
    }

    // ======================== REPORT ACTIONS ========================
    suspend fun submitReport(
        reporterUid: String,
        targetId: String,
        targetType: String,
        reason: String,
        details: String
    ): ReportItem {
        val report = ReportItem(
            reportId = "rep_${UUID.randomUUID().toString().take(8)}",
            reporterUid = reporterUid,
            targetId = targetId,
            targetType = targetType,
            reason = reason,
            details = details,
            status = "PENDING",
            createdAt = System.currentTimeMillis()
        )
        firebaseService.submitReport(report)
        recommendationEventRepository?.recordEvent(
            userId = reporterUid,
            contentId = targetId,
            contentType = targetType,
            eventType = EventType.REPORT
        )
        return report
    }

    fun observeUserNotifications(userId: String) {
        if (userId.isBlank()) {
            clearNotifications()
            return
        }
        if (currentObservedUid == userId && notificationsJob?.isActive == true) {
            return
        }
        currentObservedUid = userId
        _isLoadingNotifications.value = true
        _notificationsError.value = null

        notificationsJob?.cancel()
        notificationsJob = scope.launch {
            try {
                firebaseService.observeNotifications(userId)
                    .catch { e ->
                        Log.e("SocialRepository", "Observe notifications error: ${e.message}", e)
                        _notificationsError.value = e.message ?: "Failed to load notifications"
                        _isLoadingNotifications.value = false
                    }
                    .collect { list ->
                        val filtered = list.filter { it.recipientUid == userId }
                            .sortedByDescending { it.createdAt }
                        _notifications.value = filtered
                        _isLoadingNotifications.value = false
                        _notificationsError.value = null
                    }
            } catch (e: Exception) {
                Log.e("SocialRepository", "Error subscribing to notifications: ${e.message}", e)
                _notificationsError.value = e.message ?: "Failed to load notifications"
                _isLoadingNotifications.value = false
            }
        }
    }

    fun retryObserveNotifications() {
        val uid = currentObservedUid ?: firebaseService.currentFirebaseUser?.uid
        if (!uid.isNullOrBlank()) {
            currentObservedUid = null
            observeUserNotifications(uid)
        }
    }

    fun clearNotifications() {
        notificationsJob?.cancel()
        notificationsJob = null
        currentObservedUid = null
        _notifications.value = emptyList()
        _isLoadingNotifications.value = false
        _notificationsError.value = null
    }

    // ======================== BOOST CAMPAIGNS ========================
    suspend fun createBoostCampaign(
        channel: Channel,
        video: Video,
        budgetAmount: Double,
        targetCategory: String
    ): BoostCampaign {
        val campaign = BoostCampaign(
            campaignId = "bst_${UUID.randomUUID().toString().take(8)}",
            channelId = channel.channelId,
            ownerUid = channel.ownerUid,
            targetContentId = video.videoId,
            targetContentType = "VIDEO",
            contentTitle = video.title,
            contentThumbnailUrl = video.thumbnailUrl,
            budgetAmount = budgetAmount,
            targetCategory = targetCategory,
            estimatedImpressions = "${(budgetAmount * 10).toInt()} - ${(budgetAmount * 18).toInt()}",
            deliveredImpressions = 0,
            generatedViews = 0,
            status = BoostStatus.ACTIVE
        )
        _boostCampaigns.value = listOf(campaign) + _boostCampaigns.value
        return campaign
    }
}
