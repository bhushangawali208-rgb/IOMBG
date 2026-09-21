package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.di.AppContainer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(private val container: AppContainer) : ViewModel() {
    val currentUser = container.authRepository.currentUserState
    val currentChannel = container.channelRepository.currentChannel
    val authError = container.authRepository.authError
    val isAuthLoading = container.authRepository.isLoading
    val notifications = container.socialRepository.notifications
    val unreadNotificationsCount = container.socialRepository.unreadNotificationsCount
    val followedChannels = container.socialRepository.followedChannels
    val likedVideoIds = container.socialRepository.likedVideoIds
    val likedShortIds = container.socialRepository.likedShortIds

    private val _selectedBottomNav = MutableStateFlow("home")
    val selectedBottomNav: StateFlow<String> = _selectedBottomNav.asStateFlow()

    private val _selectedVideo = MutableStateFlow<Video?>(null)
    val selectedVideo: StateFlow<Video?> = _selectedVideo.asStateFlow()

    private val _selectedLiveStream = MutableStateFlow<LiveStream?>(null)
    val selectedLiveStream: StateFlow<LiveStream?> = _selectedLiveStream.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    container.channelRepository.loadChannelForUser(user.uid)
                    container.socialRepository.observeUserNotifications(user.uid)
                    container.socialRepository.syncFollowedChannels(user.uid)
                    container.chatRepository.setActiveUser(user.uid)
                    container.watchHistoryRepository.onUserLogin(user.uid)
                } else {
                    container.socialRepository.clearNotifications()
                    container.socialRepository.clearFollowedChannels()
                    container.chatRepository.clearActiveUser()
                    container.watchHistoryRepository.onUserLogout()
                }
            }
        }
    }

    fun selectBottomNav(route: String) {
        _selectedBottomNav.value = route
    }

    fun playVideo(video: Video) {
        _selectedVideo.value = video
        val uid = currentUser.value?.uid ?: "anonymous"
        container.watchHistoryRepository.recordWatch(
            userId = uid,
            contentType = "VIDEO",
            contentId = video.videoId,
            progressMs = 0L,
            durationMs = video.durationSeconds * 1000L,
            title = video.title,
            channelName = video.channelName,
            thumbnailUrl = video.thumbnailUrl,
            videoUrl = video.videoUrl
        )
        container.videoRepository.incrementViews(video.videoId)
        container.recommendationEventRepository.recordEvent(
            userId = currentUser.value?.uid ?: "anonymous",
            contentId = video.videoId,
            contentType = "VIDEO",
            eventType = EventType.VIDEO_OPEN,
            category = video.category,
            creatorId = video.channelId,
            tags = video.tags
        )
        container.recommendationEventRepository.recordEvent(
            userId = currentUser.value?.uid ?: "anonymous",
            contentId = video.videoId,
            contentType = "VIDEO",
            eventType = EventType.PLAY,
            category = video.category,
            creatorId = video.channelId,
            tags = video.tags
        )
    }

    fun closeVideoPlayer() {
        _selectedVideo.value = null
    }

    fun openLiveStream(stream: LiveStream) {
        _selectedLiveStream.value = stream
    }

    fun closeLiveStream() {
        _selectedLiveStream.value = null
    }

    fun handleDeepLink(destinationType: String?, destinationId: String?) {
        if (destinationType.isNullOrBlank()) return
        val type = destinationType.trim().uppercase()
        val targetId = destinationId?.trim()

        // Validate targetId if provided: alphanumeric, hyphens, underscores, max 128 chars
        if (targetId != null && !Regex("^[a-zA-Z0-9_\\-]{1,128}$").matches(targetId)) {
            android.util.Log.w("MainViewModel", "Rejected invalid deep-link targetId: $targetId")
            return
        }

        when (type) {
            "VIDEO", "LIKE", "COMMENT", "REPLY" -> {
                if (!targetId.isNullOrBlank()) {
                    val video = container.videoRepository.videosFeed.value.firstOrNull { it.videoId == targetId }
                    if (video != null) {
                        playVideo(video)
                    } else {
                        selectBottomNav("home")
                    }
                } else {
                    selectBottomNav("home")
                }
            }
            "SHORT" -> {
                selectBottomNav("shorts")
            }
            "CHANNEL", "NEW_FOLLOWER" -> {
                selectBottomNav("profile")
            }
            "CHAT", "NEW_MESSAGE" -> {
                if (!targetId.isNullOrBlank()) {
                    container.chatRepository.selectConversation(targetId)
                }
                selectBottomNav("chat")
            }
            "LIVE", "LIVE_STARTED" -> {
                if (!targetId.isNullOrBlank()) {
                    val stream = container.liveStreamRepository.activeStreams.value.firstOrNull { it.streamId == targetId }
                    if (stream != null) {
                        openLiveStream(stream)
                    } else {
                        selectBottomNav("explore")
                    }
                } else {
                    selectBottomNav("explore")
                }
            }
            else -> {
                android.util.Log.d("MainViewModel", "Unhandled deep-link type: $type")
            }
        }
    }

    fun setDemoSession(type: String, isSuperAdmin: Boolean) {
        if (!com.example.BuildConfig.DEBUG) return
        val success = container.authRepository.setDemoSession(type, isSuperAdmin)
        if (success) {
            viewModelScope.launch {
                currentUser.value?.uid?.let {
                    container.channelRepository.loadChannelForUser(it)
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            container.authRepository.signOut()
            container.channelRepository.clearChannel()
            container.socialRepository.clearNotifications()
            container.socialRepository.clearFollowedChannels()
            container.chatRepository.clearActiveUser()
            container.adminRepository.clearAdminState()
        }
    }
}

data class FilterState(
    val tab: String,
    val category: String,
    val query: String
)

data class ModerationState(
    val followedChannels: Set<String>,
    val notInterestedIds: Set<String>,
    val hiddenChannelIds: Set<String>,
    val blockedUserIds: Set<String>
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val allVideos = container.videoRepository.videosFeed
    val rawShorts = container.videoRepository.shortsFeed
    val allChannels = container.channelRepository.popularChannels
    val isFeedLoading = container.videoRepository.isFeedLoading
    val feedError = container.videoRepository.feedError
    val isLoadingMoreVideos = container.videoRepository.isLoadingMoreVideos
    val hasMoreVideos = container.videoRepository.hasMoreVideos
    val videoPaginationError = container.videoRepository.videoPaginationError

    private val _selectedTab = MutableStateFlow("For You")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val currentUser = container.authRepository.currentUserState
    val followedChannels = container.socialRepository.followedChannels
    val notInterestedVideos = container.socialRepository.notInterestedVideoIds
    val hiddenChannels = container.socialRepository.hiddenChannelIds
    val blockedUsers = container.socialRepository.blockedUserIds

    // Recommendation Engine integration states
    val userInterests = container.recommendationEventRepository.userInterests
    val eventCount = container.recommendationEventRepository.eventCount
    val categoryWeights = container.recommendationEventRepository.categoryWeights

    val isColdStart: StateFlow<Boolean> = combine(
        eventCount,
        userInterests
    ) { count, interests ->
        count < 2 && interests.isEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val moderationState: Flow<ModerationState> = combine(
        followedChannels,
        notInterestedVideos,
        hiddenChannels,
        blockedUsers
    ) { followed, notInt, hidden, blocked ->
        ModerationState(followed, notInt, hidden, blocked)
    }

    private val filterState: Flow<FilterState> = combine(
        _selectedTab,
        _selectedCategory,
        _searchQuery
    ) { tab, cat, query ->
        FilterState(tab, cat, query)
    }

    val recommendedCreators: StateFlow<List<CreatorRecommendation>> = combine(
        allChannels,
        moderationState,
        categoryWeights
    ) { channels, mod, _ ->
        container.recommendationEventRepository.recommendCreators(
            channels = channels,
            followedChannels = mod.followedChannels,
            blockedUserIds = mod.blockedUserIds
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val shorts: StateFlow<List<ShortItem>> = combine(
        rawShorts,
        moderationState,
        _selectedCategory,
        categoryWeights
    ) { shortsList, mod, category, _ ->
        container.recommendationEventRepository.scoreAndRankShorts(
            shorts = shortsList,
            followedChannels = mod.followedChannels,
            notInterestedIds = mod.notInterestedIds,
            hiddenChannelIds = mod.hiddenChannelIds,
            blockedUserIds = mod.blockedUserIds,
            filterCategory = category
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val rankedVideos: StateFlow<List<Video>> = combine(
        allVideos,
        filterState,
        moderationState,
        categoryWeights
    ) { videos, filter, mod, _ ->
        var list = videos.filter { v ->
            !mod.notInterestedIds.contains(v.videoId) &&
            !mod.hiddenChannelIds.contains(v.channelId) &&
            !mod.blockedUserIds.contains(v.ownerUid)
        }

        if (filter.query.isNotBlank()) {
            list = container.recommendationEventRepository.scoreAndRankVideos(
                videos = list,
                followedChannels = mod.followedChannels,
                notInterestedIds = mod.notInterestedIds,
                hiddenChannelIds = mod.hiddenChannelIds,
                blockedUserIds = mod.blockedUserIds,
                filterCategory = "All",
                searchQuery = filter.query
            )
        } else {
            when (filter.tab) {
                "Trending" -> list = list.sortedByDescending { it.viewCount }
                "Latest" -> list = list.sortedByDescending { it.createdAt }
                "Following" -> {
                    val followedOnly = list.filter { mod.followedChannels.contains(it.channelId) }
                    if (followedOnly.isEmpty()) list else followedOnly
                }
                else -> { // "For You" personalized AI ranking
                    list = container.recommendationEventRepository.scoreAndRankVideos(
                        videos = list,
                        followedChannels = mod.followedChannels,
                        notInterestedIds = mod.notInterestedIds,
                        hiddenChannelIds = mod.hiddenChannelIds,
                        blockedUserIds = mod.blockedUserIds,
                        filterCategory = filter.category,
                        searchQuery = ""
                    )
                }
            }
        }

        if (filter.category != "All" && filter.query.isBlank() && filter.tab != "For You") {
            list = list.filter { it.category.equals(filter.category, ignoreCase = true) }
        }

        list
    }.stateIn(viewModelScope, SharingStarted.Eagerly, container.videoRepository.videosFeed.value)

    fun selectTab(tab: String) {
        _selectedTab.value = tab
    }

    fun selectCategory(cat: String) {
        _selectedCategory.value = cat
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) {
            container.recommendationEventRepository.recordEvent(
                userId = currentUser.value?.uid ?: "current_user",
                contentId = "search_query",
                contentType = "SEARCH",
                eventType = EventType.SEARCH,
                searchQuery = query
            )
        }
    }

    fun recordImpression(video: Video) {
        container.recommendationEventRepository.recordEvent(
            userId = currentUser.value?.uid ?: "current_user",
            contentId = video.videoId,
            contentType = "VIDEO",
            eventType = EventType.IMPRESSION,
            category = video.category,
            creatorId = video.channelId,
            tags = video.tags
        )
    }

    fun recordClick(video: Video) {
        container.recommendationEventRepository.recordEvent(
            userId = currentUser.value?.uid ?: "current_user",
            contentId = video.videoId,
            contentType = "VIDEO",
            eventType = EventType.CLICK,
            category = video.category,
            creatorId = video.channelId,
            tags = video.tags
        )
    }

    fun toggleInterest(category: String) {
        container.recommendationEventRepository.toggleUserInterest(category)
    }

    fun followCreator(channel: Channel) {
        container.socialRepository.toggleFollowChannel(
            targetChannelId = channel.channelId,
            targetOwnerUid = channel.ownerUid,
            targetChannelName = channel.channelName,
            follower = currentUser.value
        )
    }

    fun markNotInterested(video: Video) {
        container.socialRepository.markNotInterested(video.videoId, video.category, currentUser.value)
    }

    fun hideChannel(channelId: String) {
        container.socialRepository.hideChannel(channelId, currentUser.value)
    }

    fun refreshFeed() {
        container.videoRepository.refreshFeed()
        container.channelRepository.refreshPopularChannels()
    }

    fun loadMoreVideos() {
        container.videoRepository.loadMoreVideos()
    }

    fun retryLoadMoreVideos() {
        container.videoRepository.retryLoadMoreVideos()
    }
}

class ShortsViewModel(private val container: AppContainer) : ViewModel() {
    val rawShorts = container.videoRepository.shortsFeed
    val currentUser = container.authRepository.currentUserState
    val currentChannel = container.channelRepository.currentChannel
    val likedShorts = container.socialRepository.likedShortIds
    val followedChannels = container.socialRepository.followedChannels
    val notInterested = container.socialRepository.notInterestedVideoIds
    val hiddenChannels = container.socialRepository.hiddenChannelIds
    val blockedUsers = container.socialRepository.blockedUserIds
    val categoryWeights = container.recommendationEventRepository.categoryWeights

    val isFeedLoading = container.videoRepository.isFeedLoading
    val isLoadingMoreShorts = container.videoRepository.isLoadingMoreShorts
    val hasMoreShorts = container.videoRepository.hasMoreShorts
    val shortsPaginationError = container.videoRepository.shortsPaginationError

    fun loadMoreShorts() {
        container.videoRepository.loadMoreShorts()
    }

    fun retryLoadMoreShorts() {
        container.videoRepository.retryLoadMoreShorts()
    }

    private val moderationState: Flow<ModerationState> = combine(
        followedChannels,
        notInterested,
        hiddenChannels,
        blockedUsers
    ) { followed, notInt, hidden, blocked ->
        ModerationState(followed, notInt, hidden, blocked)
    }

    val shorts: StateFlow<List<ShortItem>> = combine(
        rawShorts,
        moderationState,
        categoryWeights
    ) { list, mod, _ ->
        container.recommendationEventRepository.scoreAndRankShorts(
            shorts = list,
            followedChannels = mod.followedChannels,
            notInterestedIds = mod.notInterestedIds,
            hiddenChannelIds = mod.hiddenChannelIds,
            blockedUserIds = mod.blockedUserIds
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleLike(short: ShortItem) {
        val user = currentUser.value
        val isNowLiked = container.socialRepository.toggleLikeShort(short, user)
        container.videoRepository.toggleLikeShort(short.shortId, isNowLiked)
    }

    fun toggleFollow(channelId: String, ownerUid: String? = null, channelName: String = "") {
        container.socialRepository.toggleFollowChannel(
            targetChannelId = channelId,
            targetOwnerUid = ownerUid,
            targetChannelName = channelName,
            follower = currentUser.value
        )
    }

    fun recordShortImpression(short: ShortItem) {
        container.recommendationEventRepository.recordEvent(
            userId = currentUser.value?.uid ?: "current_user",
            contentId = short.shortId,
            contentType = "SHORT",
            eventType = EventType.IMPRESSION,
            category = short.category,
            creatorId = short.channelId,
            tags = short.tags
        )
    }

    fun recordShortPlay(short: ShortItem) {
        container.recommendationEventRepository.recordEvent(
            userId = currentUser.value?.uid ?: "current_user",
            contentId = short.shortId,
            contentType = "SHORT",
            eventType = EventType.PLAY,
            category = short.category,
            creatorId = short.channelId,
            tags = short.tags
        )
    }

    fun recordShortView(short: ShortItem, watchTimeSeconds: Long) {
        container.recommendationEventRepository.recordEvent(
            userId = currentUser.value?.uid ?: "current_user",
            contentId = short.shortId,
            contentType = "SHORT",
            eventType = EventType.WATCH_TIME,
            watchDurationSeconds = watchTimeSeconds,
            totalDurationSeconds = short.durationSeconds,
            category = short.category,
            creatorId = short.channelId,
            tags = short.tags
        )
    }

    fun recordShortCompletion(short: ShortItem) {
        container.recommendationEventRepository.recordEvent(
            userId = currentUser.value?.uid ?: "current_user",
            contentId = short.shortId,
            contentType = "SHORT",
            eventType = EventType.COMPLETION,
            watchDurationSeconds = short.durationSeconds,
            totalDurationSeconds = short.durationSeconds,
            category = short.category,
            creatorId = short.channelId,
            tags = short.tags
        )
    }

    fun recordShortSkip(short: ShortItem, watchTimeSeconds: Long) {
        container.recommendationEventRepository.recordEvent(
            userId = currentUser.value?.uid ?: "current_user",
            contentId = short.shortId,
            contentType = "SHORT",
            eventType = EventType.SKIP,
            watchDurationSeconds = watchTimeSeconds,
            totalDurationSeconds = short.durationSeconds,
            category = short.category,
            creatorId = short.channelId,
            tags = short.tags
        )
    }

    fun markNotInterested(short: ShortItem) {
        container.socialRepository.markNotInterested(short.shortId, short.category, currentUser.value)
    }

    fun hideChannel(channelId: String) {
        container.socialRepository.hideChannel(channelId, currentUser.value)
    }
}
