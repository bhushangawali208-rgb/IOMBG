package com.example.data.repository

import com.example.data.model.Channel
import com.example.data.remote.FirebaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID

class ChannelRepository(
    private val firebaseService: FirebaseService,
    private val isDebug: Boolean = com.example.BuildConfig.DEBUG
) {
    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel: StateFlow<Channel?> = _currentChannel.asStateFlow()

    private val _popularChannels = MutableStateFlow<List<Channel>>(emptyList())
    val popularChannels: StateFlow<List<Channel>> = _popularChannels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var channelsObserverJob: Job? = null

    init {
        observeRealPopularChannels()
    }

    fun observeRealPopularChannels() {
        channelsObserverJob?.cancel()
        channelsObserverJob = repositoryScope.launch {
            firebaseService.observePopularChannels()
                .catch {
                    // Fail gracefully without crashing
                }
                .collect { channels ->
                    _popularChannels.value = channels.distinctBy { it.channelId }
                }
        }
    }

    fun refreshPopularChannels() {
        observeRealPopularChannels()
    }

    suspend fun loadChannelForUser(ownerUid: String): Channel? {
        _isLoading.value = true
        return try {
            val channel = firebaseService.getChannelByOwnerUid(ownerUid)
            _currentChannel.value = channel
            channel
        } catch (e: Exception) {
            _currentChannel.value = null
            null
        } finally {
            _isLoading.value = false
        }
    }

    fun setDemoChannel(callerUid: String? = null): Boolean {
        if (!isDebug) {
            android.util.Log.w("ChannelRepository", "Ignored setDemoChannel(): Demo channels disabled in release builds")
            return false
        }
        val activeUid = callerUid ?: firebaseService.currentFirebaseUser?.uid
        if (activeUid != null && activeUid != "creator_demo_01" && activeUid != "super_admin_01" && activeUid != "viewer_demo_01") {
            android.util.Log.w("ChannelRepository", "Ignored setDemoChannel(): Cannot assign demo channel to authenticated user ($activeUid)")
            return false
        }
        _currentChannel.value = Channel(
            channelId = "ch_alexrivera",
            ownerUid = "creator_demo_01",
            channelName = "Nexus Media Studios",
            handle = "@nexusmedia",
            profileImageUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400",
            bannerImageUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=1200",
            description = "Creating high-octane 4K technology deep-dives, film breakdowns, and creator tutorials.",
            category = "Technology",
            subscriberCount = 14200,
            videoCount = 48,
            shortsCount = 112,
            totalViews = 1850000,
            totalWatchHours = 6420.5,
            isMonetized = true,
            monetizationStatus = "APPROVED",
            supportEnabled = true
        )
        return true
    }

    fun clearChannel() {
        _currentChannel.value = null
    }

    suspend fun isHandleAvailable(handle: String): Boolean {
        return firebaseService.isHandleAvailable(handle)
    }

    suspend fun createChannel(
        ownerUid: String,
        name: String,
        handle: String,
        description: String,
        category: String,
        profileImageUrl: String,
        bannerImageUrl: String
    ): Channel? {
        _isLoading.value = true
        return try {
            val cleanHandle = if (handle.startsWith("@")) handle.lowercase() else "@${handle.lowercase()}"
            val newChannel = Channel(
                channelId = "ch_${UUID.randomUUID().toString().take(8)}",
                ownerUid = ownerUid,
                channelName = name.trim(),
                handle = cleanHandle.trim(),
                profileImageUrl = profileImageUrl.ifBlank { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400" },
                bannerImageUrl = bannerImageUrl.ifBlank { "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=1200" },
                description = description.trim(),
                category = category,
                subscriberCount = 0,
                videoCount = 0,
                shortsCount = 0,
                totalViews = 0,
                totalWatchHours = 0.0,
                isMonetized = false,
                monetizationStatus = "NOT_APPLIED",
                supportEnabled = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val success = firebaseService.createChannel(newChannel)
            if (success) {
                _currentChannel.value = newChannel
                newChannel
            } else {
                null
            }
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun updateChannel(updatedChannel: Channel): Boolean {
        _isLoading.value = true
        return try {
            val finalChannel = updatedChannel.copy(updatedAt = System.currentTimeMillis())
            _currentChannel.value = finalChannel
            _popularChannels.value = _popularChannels.value.map {
                if (it.channelId == finalChannel.channelId) finalChannel else it
            }
            firebaseService.updateChannel(finalChannel)
            true
        } catch (e: Exception) {
            false
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun searchChannelsPaginated(
        queryText: String,
        pageSize: Long = 10L,
        lastVisible: com.google.firebase.firestore.DocumentSnapshot? = null
    ): com.example.data.remote.PaginatedResult<Channel> {
        return firebaseService.searchChannelsPaginated(queryText, pageSize, lastVisible)
    }
}

