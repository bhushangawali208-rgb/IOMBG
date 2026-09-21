package com.example.data.repository

import android.util.Log
import com.example.data.model.*
import com.example.data.remote.FirebaseService
import com.example.data.service.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max

/**
 * Production Live Streaming & Broadcast Repository for IOMBG.
 *
 * Integrates:
 * - Production Live Streaming Ingest & WebRTC/RTMP Broadcast Management
 * - Ephemeral Stream Key & Ingest URL Security (never stored in public documents)
 * - Live Stream Recording & Automated VOD Replay Pipeline
 * - Real-Time Moderation, Auto-Spam Filtering & Rate Limiting
 */
class LiveStreamRepository(
    private val firebaseService: FirebaseService,
    private val recommendationEventRepository: RecommendationEventRepository? = null,
    val streamingService: LiveStreamingService = ProductionLiveStreamingService(),
    val recordingService: LiveRecordingService = CloudLiveRecordingService(),
    val cdnService: CDNService = CloudCdnService()
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _activeStreams = MutableStateFlow<List<LiveStream>>(emptyList())
    val activeStreams: StateFlow<List<LiveStream>> = _activeStreams.asStateFlow()

    private val _liveMessages = MutableStateFlow<List<LiveMessage>>(emptyList())
    val liveMessages: StateFlow<List<LiveMessage>> = _liveMessages.asStateFlow()

    private val _replays = MutableStateFlow<List<LiveReplay>>(emptyList())
    val replays: StateFlow<List<LiveReplay>> = _replays.asStateFlow()

    private val _analyticsHistory = MutableStateFlow<List<LiveAnalytics>>(emptyList())
    val analyticsHistory: StateFlow<List<LiveAnalytics>> = _analyticsHistory.asStateFlow()

    private val _moderationActions = MutableStateFlow<List<LiveModerationAction>>(emptyList())
    val moderationActions: StateFlow<List<LiveModerationAction>> = _moderationActions.asStateFlow()

    // Blocked and muted users (uid -> mute expiration timestamp)
    private val _mutedUsers = MutableStateFlow<Map<String, Long>>(emptyMap())
    val mutedUsers: StateFlow<Map<String, Long>> = _mutedUsers.asStateFlow()

    private val _blockedUsers = MutableStateFlow<Set<String>>(emptySet())
    val blockedUsers: StateFlow<Set<String>> = _blockedUsers.asStateFlow()

    // Track active recording sessions (streamId -> recordingId)
    private val activeRecordings = mutableMapOf<String, String>()

    // Rate limiting (userId -> lastMessageTimestamp)
    private val userLastMessageTimestamp = mutableMapOf<String, Long>()

    // Profanity / Abuse word filters for auto moderation
    private val blockedKeywords = listOf(
        "spam", "scam", "free money", "hack", "giveaway free", "crypto double",
        "hate", "abusive", "threat", "harass"
    )

    init {
        observeFirestoreLiveStreams()
    }

    private fun observeFirestoreLiveStreams() {
        scope.launch {
            try {
                firebaseService.observeActiveLiveStreams().collect { streams ->
                    _activeStreams.value = streams
                }
            } catch (e: Exception) {
                Log.d("LiveStreamRepository", "Observe live streams error: ${e.message}")
            }
        }
    }

    fun observeLiveChat(streamId: String) {
        scope.launch {
            try {
                firebaseService.observeLiveChat(streamId).collect { msgs ->
                    if (msgs.isNotEmpty()) {
                        _liveMessages.value = msgs
                    }
                }
            } catch (e: Exception) {
                Log.d("LiveStreamRepository", "Observe live chat error: ${e.message}")
            }
        }
    }

    // =========================================================================
    // BROADCAST LIFECYCLE & CREDENTIALS
    // =========================================================================

    /**
     * Ephemeral stream credentials generation.
     * Stream key is never permanently logged or saved to public Firestore documents.
     */
    suspend fun getStreamCredentials(
        channelId: String,
        creatorUid: String,
        streamId: String
    ): Result<SecureStreamCredentials> {
        return streamingService.generateStreamCredentials(channelId, creatorUid, streamId)
    }

    suspend fun startLiveStream(
        channel: Channel,
        title: String,
        description: String = "",
        category: String = "Gaming",
        thumbnailUrl: String = "",
        visibility: String = "PUBLIC",
        isChatEnabled: Boolean = true,
        allowReplay: Boolean = true,
        tags: List<String> = emptyList()
    ): LiveStream {
        val streamId = "live_${UUID.randomUUID().toString().take(8)}"

        val stream = LiveStream(
            streamId = streamId,
            channelId = channel.channelId,
            ownerUid = channel.ownerUid,
            channelName = channel.channelName,
            channelHandle = channel.handle,
            channelAvatarUrl = channel.profileImageUrl,
            title = title.ifBlank { "Live Broadcast" },
            description = description,
            category = category,
            tags = tags,
            thumbnailUrl = thumbnailUrl.ifBlank { "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=800" },
            streamUrl = "", // Empty: Awaiting broadcast ingest backend
            visibility = visibility,
            isLive = true,
            status = "AWAITING_BROADCAST",
            viewerCount = 0,
            peakViewers = 0,
            totalViews = 0,
            likeCount = 0,
            chatMessageCount = 0,
            isChatEnabled = isChatEnabled,
            replayAvailable = allowReplay,
            replayVideoUrl = null,
            startedAt = System.currentTimeMillis(),
            streamHealth = StreamHealth.POOR,
            isDevSandbox = !streamingService.isProductionCloudConfigured()
        )

        // Initialize streaming broadcast
        streamingService.prepareBroadcast(
            StreamBroadcastConfig(
                streamId = streamId,
                channelId = channel.channelId,
                title = stream.title,
                isSandboxMode = !streamingService.isProductionCloudConfigured()
            )
        )
        streamingService.startBroadcast(streamId)

        // Initialize live chunk recording if replay is requested
        if (allowReplay) {
            val recResult = recordingService.startRecordingSession(streamId, channel.channelId, channel.ownerUid)
            recResult.getOrNull()?.let { activeRecordings[streamId] = it }
        }

        _activeStreams.value = listOf(stream) + _activeStreams.value.filter { it.streamId != streamId }

        // Persist live stream room metadata to Firestore
        scope.launch {
            try {
                firebaseService.createLiveStreamRoom(stream)
            } catch (e: Exception) {
                Log.d("LiveStreamRepository", "Create live stream room in Firestore error: ${e.message}")
            }
        }

        // Emit recommendation event
        recommendationEventRepository?.recordEvent(
            userId = channel.ownerUid,
            contentId = streamId,
            contentType = "LIVE",
            eventType = EventType.LIVE_OPEN,
            category = category,
            creatorId = channel.channelId,
            tags = tags
        )

        // Dispatch real LIVE_STARTED notifications to channel followers in Firestore
        scope.launch {
            try {
                firebaseService.notifyFollowersLiveStarted(stream)
            } catch (e: Exception) {
                Log.d("LiveStreamRepository", "Live start notification dispatch error: ${e.message}")
            }
        }

        return stream
    }

    suspend fun endLiveStream(streamId: String): LiveAnalytics {
        val stream = _activeStreams.value.firstOrNull { it.streamId == streamId }
        val analytics = streamingService.stopBroadcast(streamId)

        val updatedAnalytics = analytics.copy(
            channelId = stream?.channelId ?: analytics.channelId,
            channelName = stream?.channelName ?: analytics.channelName,
            title = stream?.title ?: analytics.title,
            likes = stream?.likeCount ?: analytics.likes,
            chatMessages = _liveMessages.value.filter { it.streamId == streamId }.size.toLong()
        )

        // Mark stream as ended
        _activeStreams.value = _activeStreams.value.map {
            if (it.streamId == streamId) {
                it.copy(
                    isLive = false,
                    status = "ENDED",
                    endedAt = System.currentTimeMillis(),
                    durationSeconds = updatedAnalytics.streamDurationSeconds
                )
            } else it
        }

        // Persist ended state to Firestore
        scope.launch {
            try {
                firebaseService.updateLiveStreamEnded(streamId, System.currentTimeMillis(), updatedAnalytics.streamDurationSeconds)
            } catch (e: Exception) {
                Log.d("LiveStreamRepository", "Update live stream ended in Firestore error: ${e.message}")
            }
        }

        // Finalize recording and transcode to VOD Replay
        val recordingId = activeRecordings.remove(streamId)
        if (stream?.replayAvailable == true && stream.channelId.isNotBlank()) {
            val recInfoResult = recordingService.finalizeRecordingSession(
                recordingId ?: "rec_$streamId",
                updatedAnalytics.streamDurationSeconds
            )
            val recInfo = recInfoResult.getOrNull() ?: LiveRecordingInfo(
                recordingId = recordingId ?: "rec_$streamId",
                streamId = streamId,
                durationSeconds = updatedAnalytics.streamDurationSeconds
            )

            val dummyChannel = Channel(
                channelId = stream.channelId,
                ownerUid = stream.ownerUid,
                channelName = stream.channelName,
                handle = stream.channelHandle,
                profileImageUrl = stream.channelAvatarUrl
            )

            val replayResult = recordingService.processReplayToVod(
                recordingInfo = recInfo,
                channel = dummyChannel,
                title = "[REPLAY] ${stream.title}",
                description = stream.description,
                category = stream.category,
                tags = stream.tags,
                thumbnailUrl = stream.thumbnailUrl
            )

            replayResult.getOrNull()?.let { replay ->
                if (replay.videoUrl.isNotBlank()) {
                    _replays.value = listOf(replay) + _replays.value
                }
            }
        }

        // Store analytics in history
        _analyticsHistory.value = listOf(updatedAnalytics) + _analyticsHistory.value

        return updatedAnalytics
    }

    // =========================================================================
    // REAL-TIME LIVE CHAT & MODERATION
    // =========================================================================

    fun sendChatMessage(
        streamId: String,
        senderUid: String,
        senderName: String,
        senderAvatar: String,
        text: String,
        isCreator: Boolean = false,
        isSuperSupport: Boolean = false,
        supportAmount: Double = 0.0
    ): Result<LiveMessage> {
        val stream = _activeStreams.value.firstOrNull { it.streamId == streamId }

        if (stream?.isChatEnabled == false && !isCreator) {
            return Result.failure(IllegalStateException("Live chat is currently disabled by the creator."))
        }

        if (_blockedUsers.value.contains(senderUid)) {
            return Result.failure(SecurityException("You have been blocked from sending messages in this live chat."))
        }

        val muteExpiry = _mutedUsers.value[senderUid]
        if (muteExpiry != null && System.currentTimeMillis() < muteExpiry) {
            val remainingSec = (muteExpiry - System.currentTimeMillis()) / 1000
            return Result.failure(IllegalStateException("You are muted for $remainingSec more seconds."))
        }

        val now = System.currentTimeMillis()
        val lastSent = userLastMessageTimestamp[senderUid] ?: 0L
        val minInterval = if (stream?.isSlowModeEnabled == true) (stream.slowModeIntervalSeconds * 1000L) else 1000L

        if (!isCreator && (now - lastSent) < minInterval) {
            val cooldown = ((minInterval - (now - lastSent)) / 1000) + 1
            return Result.failure(IllegalStateException("Slow mode active. Please wait $cooldown seconds."))
        }

        val containsBlockedKeyword = blockedKeywords.any { text.lowercase().contains(it) }
        val moderationStatus = if (containsBlockedKeyword) LiveModerationStatus.FLAGGED else LiveModerationStatus.VISIBLE

        val msg = LiveMessage(
            messageId = "lmsg_${UUID.randomUUID().toString().take(8)}",
            streamId = streamId,
            senderUid = senderUid,
            senderName = senderName,
            senderAvatarUrl = senderAvatar,
            text = text,
            isSuperSupport = isSuperSupport,
            supportAmount = supportAmount,
            moderationStatus = moderationStatus,
            timestamp = now,
            isCreator = isCreator,
            isVerified = isCreator || isSuperSupport
        )

        userLastMessageTimestamp[senderUid] = now

        val updatedMessages = (_liveMessages.value + msg).takeLast(200)
        _liveMessages.value = updatedMessages

        _activeStreams.value = _activeStreams.value.map {
            if (it.streamId == streamId) it.copy(chatMessageCount = it.chatMessageCount + 1) else it
        }

        recommendationEventRepository?.recordEvent(
            userId = senderUid,
            contentId = streamId,
            contentType = "LIVE",
            eventType = EventType.LIVE_CHAT,
            category = stream?.category ?: ""
        )

        // Persist message to Firestore
        scope.launch {
            try {
                firebaseService.sendLiveMessage(msg)
            } catch (e: Exception) {
                Log.d("LiveStreamRepository", "Send live message Firestore error: ${e.message}")
            }
        }

        return Result.success(msg)
    }

    fun deleteChatMessage(messageId: String, streamId: String, performedByUid: String, isCreatorAction: Boolean = false) {
        _liveMessages.value = _liveMessages.value.map {
            if (it.messageId == messageId) {
                it.copy(moderationStatus = if (isCreatorAction) LiveModerationStatus.DELETED_BY_CREATOR else LiveModerationStatus.DELETED_BY_USER)
            } else it
        }

        if (isCreatorAction) {
            val action = LiveModerationAction(
                actionId = "mod_${UUID.randomUUID().toString().take(8)}",
                streamId = streamId,
                targetUid = "",
                targetMessageId = messageId,
                actionType = LiveModerationType.DELETE_MESSAGE,
                reason = "Deleted by creator",
                performedByUid = performedByUid
            )
            _moderationActions.value = listOf(action) + _moderationActions.value
        }
    }

    fun muteUser(streamId: String, targetUid: String, durationSeconds: Long = 60, performedByUid: String) {
        val expiry = System.currentTimeMillis() + (durationSeconds * 1000)
        _mutedUsers.value = _mutedUsers.value + (targetUid to expiry)

        val action = LiveModerationAction(
            actionId = "mod_${UUID.randomUUID().toString().take(8)}",
            streamId = streamId,
            targetUid = targetUid,
            actionType = LiveModerationType.MUTE_USER,
            reason = "Temporary mute ($durationSeconds seconds)",
            performedByUid = performedByUid,
            muteDurationSeconds = durationSeconds
        )
        _moderationActions.value = listOf(action) + _moderationActions.value
    }

    fun blockUserFromChat(streamId: String, targetUid: String, performedByUid: String) {
        _blockedUsers.value = _blockedUsers.value + targetUid

        _liveMessages.value = _liveMessages.value.map {
            if (it.senderUid == targetUid) it.copy(moderationStatus = LiveModerationStatus.BLOCKED) else it
        }

        val action = LiveModerationAction(
            actionId = "mod_${UUID.randomUUID().toString().take(8)}",
            streamId = streamId,
            targetUid = targetUid,
            actionType = LiveModerationType.BLOCK_USER,
            reason = "Permanently blocked from stream chat",
            performedByUid = performedByUid
        )
        _moderationActions.value = listOf(action) + _moderationActions.value
    }

    fun setSlowMode(streamId: String, enabled: Boolean, intervalSeconds: Int = 5) {
        _activeStreams.value = _activeStreams.value.map {
            if (it.streamId == streamId) {
                it.copy(isSlowModeEnabled = enabled, slowModeIntervalSeconds = intervalSeconds)
            } else it
        }
    }

    fun setChatEnabled(streamId: String, enabled: Boolean) {
        _activeStreams.value = _activeStreams.value.map {
            if (it.streamId == streamId) {
                it.copy(isChatEnabled = enabled)
            } else it
        }
    }

    // =========================================================================
    // VIEWER INTERACTIONS & RECOMMENDATION EVENTS
    // =========================================================================

    fun recordLike(streamId: String, userId: String) {
        _activeStreams.value = _activeStreams.value.map {
            if (it.streamId == streamId) it.copy(likeCount = it.likeCount + 1) else it
        }

        val stream = _activeStreams.value.firstOrNull { it.streamId == streamId }
        recommendationEventRepository?.recordEvent(
            userId = userId,
            contentId = streamId,
            contentType = "LIVE",
            eventType = EventType.LIVE_LIKE,
            category = stream?.category ?: "",
            creatorId = stream?.channelId ?: ""
        )
    }

    fun recordShare(streamId: String, userId: String) {
        val stream = _activeStreams.value.firstOrNull { it.streamId == streamId }
        recommendationEventRepository?.recordEvent(
            userId = userId,
            contentId = streamId,
            contentType = "LIVE",
            eventType = EventType.LIVE_SHARE,
            category = stream?.category ?: "",
            creatorId = stream?.channelId ?: ""
        )
    }

    fun recordJoin(streamId: String, userId: String) {
        val stream = _activeStreams.value.firstOrNull { it.streamId == streamId }
        recommendationEventRepository?.recordEvent(
            userId = userId,
            contentId = streamId,
            contentType = "LIVE",
            eventType = EventType.LIVE_JOIN,
            category = stream?.category ?: "",
            creatorId = stream?.channelId ?: ""
        )
    }

    fun recordLeave(streamId: String, userId: String, watchDurationSeconds: Long) {
        val stream = _activeStreams.value.firstOrNull { it.streamId == streamId }
        recommendationEventRepository?.recordEvent(
            userId = userId,
            contentId = streamId,
            contentType = "LIVE",
            eventType = EventType.LIVE_LEAVE,
            watchDurationSeconds = watchDurationSeconds,
            category = stream?.category ?: "",
            creatorId = stream?.channelId ?: ""
        )
    }

    fun recordReport(streamId: String, reporterUid: String, reason: String, details: String) {
        val stream = _activeStreams.value.firstOrNull { it.streamId == streamId }
        recommendationEventRepository?.recordEvent(
            userId = reporterUid,
            contentId = streamId,
            contentType = "LIVE",
            eventType = EventType.LIVE_REPORT,
            category = stream?.category ?: "",
            creatorId = stream?.channelId ?: ""
        )
    }

    // =========================================================================
    // REPLAY MANAGEMENT
    // =========================================================================

    fun updateReplay(
        replayId: String,
        title: String,
        description: String,
        thumbnailUrl: String,
        visibility: String
    ) {
        _replays.value = _replays.value.map {
            if (it.replayId == replayId) {
                it.copy(
                    title = title,
                    description = description,
                    thumbnailUrl = thumbnailUrl,
                    visibility = visibility
                )
            } else it
        }
    }

    fun deleteReplay(replayId: String) {
        _replays.value = _replays.value.filter { it.replayId != replayId }
    }
}
