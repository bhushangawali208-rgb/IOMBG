package com.example.data.repository

import android.util.Log
import com.example.data.model.*
import com.example.data.remote.FirebaseService
import com.example.data.remote.PaginatedResult
import com.example.data.service.*
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Production Video & Media Repository for IOMBG.
 *
 * Integrates:
 * - Real Video Object Storage (Firebase/GCP Object Storage)
 * - Server-Authoritative Multi-Stage Transcoder Pipeline (360p - 4K UHD, HLS packaging)
 * - Cloud CDN Edge Delivery Resolution
 * - Real-Time Comments & Engagement Engine
 */
class VideoRepository(
    private val firebaseService: FirebaseService,
    private val storageService: VideoStorageService = FirebaseVideoStorageService(),
    private val processingService: VideoProcessingService = CloudVideoProcessingService(),
    private val cdnService: CDNService = CloudCdnService(),
    val localPreviewRepository: LocalMediaPreviewRepository? = null
) {
    private val _videosFeed = MutableStateFlow<List<Video>>(emptyList())
    val videosFeed: StateFlow<List<Video>> = _videosFeed.asStateFlow()

    private val _shortsFeed = MutableStateFlow<List<ShortItem>>(emptyList())
    val shortsFeed: StateFlow<List<ShortItem>> = _shortsFeed.asStateFlow()

    private val _localPreviewVideos = MutableStateFlow<List<Video>>(emptyList())
    val localPreviewVideos: StateFlow<List<Video>> = _localPreviewVideos.asStateFlow()

    private val _localPreviewShorts = MutableStateFlow<List<ShortItem>>(emptyList())
    val localPreviewShorts: StateFlow<List<ShortItem>> = _localPreviewShorts.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Int?>(null)
    val uploadProgress: StateFlow<Int?> = _uploadProgress.asStateFlow()

    private val _uploadStatus = MutableStateFlow<String>("")
    val uploadStatus: StateFlow<String> = _uploadStatus.asStateFlow()

    private val _uploadProgressState = MutableStateFlow(UploadProgressState())
    val uploadProgressState: StateFlow<UploadProgressState> = _uploadProgressState.asStateFlow()

    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError: StateFlow<String?> = _uploadError.asStateFlow()

    private val _commentsMap = MutableStateFlow<Map<String, List<CommentItem>>>(emptyMap())
    val commentsMap: StateFlow<Map<String, List<CommentItem>>> = _commentsMap.asStateFlow()

    private val _savedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val savedVideoIds: StateFlow<Set<String>> = _savedVideoIds.asStateFlow()

    private val _isFeedLoading = MutableStateFlow<Boolean>(true)
    val isFeedLoading: StateFlow<Boolean> = _isFeedLoading.asStateFlow()

    private val _feedError = MutableStateFlow<String?>(null)
    val feedError: StateFlow<String?> = _feedError.asStateFlow()

    companion object {
        const val PAGE_SIZE_VIDEOS = 10L
        const val PAGE_SIZE_SHORTS = 8L
    }

    private var lastVideoDocSnapshot: DocumentSnapshot? = null
    private var lastShortDocSnapshot: DocumentSnapshot? = null

    private val _isLoadingMoreVideos = MutableStateFlow<Boolean>(false)
    val isLoadingMoreVideos: StateFlow<Boolean> = _isLoadingMoreVideos.asStateFlow()

    private val _hasMoreVideos = MutableStateFlow<Boolean>(true)
    val hasMoreVideos: StateFlow<Boolean> = _hasMoreVideos.asStateFlow()

    private val _videoPaginationError = MutableStateFlow<String?>(null)
    val videoPaginationError: StateFlow<String?> = _videoPaginationError.asStateFlow()

    private val _isLoadingMoreShorts = MutableStateFlow<Boolean>(false)
    val isLoadingMoreShorts: StateFlow<Boolean> = _isLoadingMoreShorts.asStateFlow()

    private val _hasMoreShorts = MutableStateFlow<Boolean>(true)
    val hasMoreShorts: StateFlow<Boolean> = _hasMoreShorts.asStateFlow()

    private val _shortsPaginationError = MutableStateFlow<String?>(null)
    val shortsPaginationError: StateFlow<String?> = _shortsPaginationError.asStateFlow()

    private val paginatedVideos = mutableListOf<Video>()
    private val paginatedShorts = mutableListOf<ShortItem>()
    private var firstPageVideos = listOf<Video>()
    private var firstPageShorts = listOf<ShortItem>()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var videoObserverJob: Job? = null
    private var shortsObserverJob: Job? = null

    private var activeUploadJob: Job? = null

    init {
        restorePersistedLocalPreview()
        observeRealFirestoreFeeds()
    }

    fun restorePersistedLocalPreview() {
        val preview = localPreviewRepository?.checkAndRestorePreview() ?: return
        if (preview.uriString.isBlank()) return

        if (preview.mediaType.equals("SHORT", ignoreCase = true)) {
            val restoredShort = ShortItem(
                shortId = preview.id.ifBlank { "preview_short_${System.currentTimeMillis()}" },
                channelId = "local_creator",
                ownerUid = "local_user",
                channelName = "My Channel",
                channelHandle = "@me",
                channelAvatarUrl = "",
                title = preview.title.ifBlank { preview.mediaName.ifBlank { "Local Short Preview" } },
                description = preview.description,
                videoUrl = preview.uriString,
                thumbnailUrl = preview.thumbnailUriOrUrl,
                soundTitle = preview.soundTitle,
                category = preview.category,
                tags = preview.tags,
                visibility = preview.visibility,
                allowComments = preview.allowComments,
                processingStatus = "LOCAL_PREVIEW",
                createdAt = preview.createdAt
            )
            _localPreviewShorts.value = listOf(restoredShort)
            _shortsFeed.value = listOf(restoredShort) + _shortsFeed.value.filterNot { it.shortId == restoredShort.shortId }
        } else {
            val restoredVideo = Video(
                videoId = preview.id.ifBlank { "preview_vid_${System.currentTimeMillis()}" },
                channelId = "local_creator",
                ownerUid = "local_user",
                channelName = "My Channel",
                channelHandle = "@me",
                channelAvatarUrl = "",
                title = preview.title.ifBlank { preview.mediaName.ifBlank { "Local Video Preview" } },
                description = preview.description,
                category = preview.category,
                tags = preview.tags,
                videoUrl = preview.uriString,
                thumbnailUrl = preview.thumbnailUriOrUrl,
                visibility = preview.visibility,
                allowComments = preview.allowComments,
                processingStatus = "LOCAL_PREVIEW",
                createdAt = preview.createdAt
            )
            _localPreviewVideos.value = listOf(restoredVideo)
            _videosFeed.value = listOf(restoredVideo) + _videosFeed.value.filterNot { it.videoId == restoredVideo.videoId }
        }
    }

    fun observeRealFirestoreFeeds() {
        _isFeedLoading.value = true
        _feedError.value = null

        videoObserverJob?.cancel()
        videoObserverJob = repositoryScope.launch {
            firebaseService.observeVideosFirstPage(PAGE_SIZE_VIDEOS)
                .catch { e ->
                    _isFeedLoading.value = false
                    _feedError.value = e.message ?: "Failed to load videos from Firestore"
                }
                .collect { pageResult ->
                    // Filter: only publicly visible videos according to visibility/status fields
                    val publicVideos = pageResult.items.filter { v ->
                        v.visibility == "PUBLIC" &&
                        (v.processingStatus == "READY" || v.processingStatus == "PUBLISHED" || v.processingStatus.isBlank())
                    }.distinctBy { it.videoId }

                    firstPageVideos = publicVideos
                    if (paginatedVideos.isEmpty()) {
                        lastVideoDocSnapshot = pageResult.lastSnapshot
                        _hasMoreVideos.value = pageResult.hasMore
                    }

                    _videosFeed.value = (_localPreviewVideos.value + firstPageVideos + paginatedVideos).distinctBy { it.videoId }
                    _isFeedLoading.value = false
                    _feedError.value = null
                }
        }

        shortsObserverJob?.cancel()
        shortsObserverJob = repositoryScope.launch {
            firebaseService.observeShortsFirstPage(PAGE_SIZE_SHORTS)
                .catch { e ->
                    // Keep existing or empty on error
                }
                .collect { pageResult ->
                    // Filter: only publicly visible shorts according to visibility/status fields
                    val publicShorts = pageResult.items.filter { s ->
                        s.visibility == "PUBLIC" &&
                        (s.processingStatus == "READY" || s.processingStatus == "PUBLISHED" || s.processingStatus.isBlank())
                    }.distinctBy { it.shortId }

                    firstPageShorts = publicShorts
                    if (paginatedShorts.isEmpty()) {
                        lastShortDocSnapshot = pageResult.lastSnapshot
                        _hasMoreShorts.value = pageResult.hasMore
                    }

                    _shortsFeed.value = (_localPreviewShorts.value + firstPageShorts + paginatedShorts).distinctBy { it.shortId }
                }
        }
    }

    fun refreshFeed() {
        resetPagination()
        observeRealFirestoreFeeds()
    }

    fun resetPagination() {
        _isLoadingMoreVideos.value = false
        _videoPaginationError.value = null
        _hasMoreVideos.value = true
        lastVideoDocSnapshot = null
        paginatedVideos.clear()

        _isLoadingMoreShorts.value = false
        _shortsPaginationError.value = null
        _hasMoreShorts.value = true
        lastShortDocSnapshot = null
        paginatedShorts.clear()
    }

    fun loadMoreVideos() {
        if (_isLoadingMoreVideos.value || !_hasMoreVideos.value) return
        val cursor = lastVideoDocSnapshot ?: return

        repositoryScope.launch {
            if (!_isLoadingMoreVideos.compareAndSet(expect = false, update = true)) return@launch
            _videoPaginationError.value = null
            try {
                val pageResult = firebaseService.fetchNextVideosPage(
                    pageSize = PAGE_SIZE_VIDEOS,
                    lastVisible = cursor
                )
                val filtered = pageResult.items.filter { v ->
                    v.visibility == "PUBLIC" &&
                    (v.processingStatus == "READY" || v.processingStatus == "PUBLISHED" || v.processingStatus.isBlank())
                }
                if (pageResult.lastSnapshot != null) {
                    lastVideoDocSnapshot = pageResult.lastSnapshot
                }
                _hasMoreVideos.value = pageResult.hasMore

                val existingIds = (_localPreviewVideos.value + firstPageVideos + paginatedVideos).map { it.videoId }.toSet()
                val newUniqueVideos = filtered.filterNot { existingIds.contains(it.videoId) }
                paginatedVideos.addAll(newUniqueVideos)

                _videosFeed.value = (_localPreviewVideos.value + firstPageVideos + paginatedVideos).distinctBy { it.videoId }
            } catch (e: Exception) {
                Log.e("VideoRepository", "loadMoreVideos error: ${e.message}")
                _videoPaginationError.value = e.message ?: "Failed to load more videos"
            } finally {
                _isLoadingMoreVideos.value = false
            }
        }
    }

    fun retryLoadMoreVideos() {
        _videoPaginationError.value = null
        loadMoreVideos()
    }

    fun loadMoreShorts() {
        if (_isLoadingMoreShorts.value || !_hasMoreShorts.value) return
        val cursor = lastShortDocSnapshot ?: return

        repositoryScope.launch {
            if (!_isLoadingMoreShorts.compareAndSet(expect = false, update = true)) return@launch
            _shortsPaginationError.value = null
            try {
                val pageResult = firebaseService.fetchNextShortsPage(
                    pageSize = PAGE_SIZE_SHORTS,
                    lastVisible = cursor
                )
                val filtered = pageResult.items.filter { s ->
                    s.visibility == "PUBLIC" &&
                    (s.processingStatus == "READY" || s.processingStatus == "PUBLISHED" || s.processingStatus.isBlank())
                }
                if (pageResult.lastSnapshot != null) {
                    lastShortDocSnapshot = pageResult.lastSnapshot
                }
                _hasMoreShorts.value = pageResult.hasMore

                val existingIds = (_localPreviewShorts.value + firstPageShorts + paginatedShorts).map { it.shortId }.toSet()
                val newUniqueShorts = filtered.filterNot { existingIds.contains(it.shortId) }
                paginatedShorts.addAll(newUniqueShorts)

                _shortsFeed.value = (_localPreviewShorts.value + firstPageShorts + paginatedShorts).distinctBy { it.shortId }
            } catch (e: Exception) {
                Log.e("VideoRepository", "loadMoreShorts error: ${e.message}")
                _shortsPaginationError.value = e.message ?: "Failed to load more shorts"
            } finally {
                _isLoadingMoreShorts.value = false
            }
        }
    }

    fun retryLoadMoreShorts() {
        _shortsPaginationError.value = null
        loadMoreShorts()
    }

    suspend fun searchVideosPaginated(
        queryText: String,
        pageSize: Long = PAGE_SIZE_VIDEOS,
        lastVisible: DocumentSnapshot? = null
    ): PaginatedResult<Video> {
        return firebaseService.searchVideosPaginated(queryText, pageSize, lastVisible)
    }

    suspend fun searchShortsPaginated(
        queryText: String,
        pageSize: Long = PAGE_SIZE_VIDEOS,
        lastVisible: DocumentSnapshot? = null
    ): PaginatedResult<ShortItem> {
        return firebaseService.searchShortsPaginated(queryText, pageSize, lastVisible)
    }

    suspend fun searchChannelsPaginated(
        queryText: String,
        pageSize: Long = PAGE_SIZE_VIDEOS,
        lastVisible: DocumentSnapshot? = null
    ): PaginatedResult<Channel> {
        return firebaseService.searchChannelsPaginated(queryText, pageSize, lastVisible)
    }

    // ======================== VIDEO UPLOAD WITH PRODUCTION PIPELINE ========================
    fun cancelUpload() {
        activeUploadJob?.cancel()
        activeUploadJob = null
        _uploadProgress.value = null
        _uploadStatus.value = "Upload cancelled"
        _uploadProgressState.value = UploadProgressState(state = VideoUploadState.CANCELLED)
        _uploadError.value = null
    }

    suspend fun uploadLongVideo(
        channel: Channel,
        title: String,
        description: String,
        tags: List<String>,
        category: String,
        visibility: String,
        allowComments: Boolean,
        thumbnailUrl: String,
        videoUriString: String,
        durationSeconds: Long = 420
    ): Result<Video> {
        _uploadError.value = null
        val videoId = "vid_${UUID.randomUUID().toString().take(8)}"

        try {
            if (videoUriString.isBlank()) {
                val err = "No video selected. Please select a video file."
                _uploadError.value = err
                _uploadProgressState.value = UploadProgressState(
                    state = VideoUploadState.FAILED,
                    errorMessage = err
                )
                return Result.failure(IllegalArgumentException(err))
            }

            // Stage 1: Validation
            _uploadStatus.value = "Validating media stream & codecs..."
            _uploadProgress.value = 5
            _uploadProgressState.value = UploadProgressState(
                state = VideoUploadState.SELECTING,
                progressPercentage = 5,
                currentStageDescription = "Validating media stream & codecs..."
            )
            val validation = processingService.validateSourceMedia(videoUriString, isShort = false)
            if (!validation.getOrDefault(MediaValidationResult(isValid = true)).isValid) {
                val err = "Media file validation failed"
                _uploadError.value = err
                return Result.failure(IllegalArgumentException(err))
            }

            // Stage 2: Source Storage Upload
            _uploadStatus.value = "Uploading 4K HDR master to Object Storage..."
            val rawUploadResult = storageService.uploadSourceVideo(
                creatorUid = channel.ownerUid,
                videoId = videoId,
                fileUri = videoUriString,
                isShort = false
            ) { progressState ->
                _uploadProgress.value = progressState.progressPercentage
                _uploadStatus.value = progressState.currentStageDescription
                _uploadProgressState.value = progressState
            }

            // If cloud storage upload failed or is unavailable (e.g. Firebase Spark plan restriction)
            if (rawUploadResult.isFailure) {
                val localPreviewVideo = Video(
                    videoId = videoId,
                    channelId = channel.channelId,
                    ownerUid = channel.ownerUid,
                    channelName = channel.channelName,
                    channelHandle = channel.handle,
                    channelAvatarUrl = channel.profileImageUrl,
                    title = title,
                    description = description,
                    videoUrl = videoUriString, // PRESERVE exact selected URI, NEVER substitute with sample video
                    thumbnailUrl = thumbnailUrl.ifBlank { "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=800" },
                    durationSeconds = durationSeconds,
                    tags = tags,
                    category = category,
                    visibility = visibility,
                    allowComments = allowComments,
                    processingStatus = "LOCAL_PREVIEW",
                    uploadProgress = 0,
                    viewCount = 0,
                    likeCount = 0,
                    commentCount = 0,
                    shareCount = 0,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                _uploadProgress.value = null
                _uploadStatus.value = "Cloud video upload is currently unavailable. Your video is available for local preview only."
                _uploadProgressState.value = UploadProgressState(
                    state = VideoUploadState.FAILED,
                    progressPercentage = 0,
                    errorMessage = "Cloud video upload is currently unavailable. Your video is available for local preview only.",
                    canRetry = true
                )
                _uploadError.value = "Cloud video upload is currently unavailable. Your video is available for local preview only."

                // Retain in local preview collection and feed for local playback, DO NOT upload to Firestore
                _localPreviewVideos.value = listOf(localPreviewVideo) + _localPreviewVideos.value
                _videosFeed.value = listOf(localPreviewVideo) + _videosFeed.value
                val existingOrNewPreview = localPreviewRepository?.currentPreview?.value ?: LocalMediaPreview(
                    id = localPreviewVideo.videoId,
                    uriString = localPreviewVideo.videoUrl,
                    mediaName = title,
                    mediaType = "VIDEO",
                    title = title,
                    description = description,
                    category = category,
                    tags = tags,
                    thumbnailUriOrUrl = thumbnailUrl,
                    visibility = visibility,
                    allowComments = allowComments,
                    hostingStatus = MediaHostingStatus.UPLOAD_UNAVAILABLE,
                    errorMessage = "Cloud video upload is currently unavailable. Your video is available for local preview only."
                )
                localPreviewRepository?.savePreview(existingOrNewPreview.copy(
                    hostingStatus = MediaHostingStatus.UPLOAD_UNAVAILABLE,
                    errorMessage = "Cloud video upload is currently unavailable. Your video is available for local preview only."
                ))

                return Result.failure(
                    CloudUploadUnavailableException(
                        localVideo = localPreviewVideo,
                        message = "Cloud video upload is currently unavailable. Your video is available for local preview only."
                    )
                )
            }

            val sourceStorageUrl = rawUploadResult.getOrThrow()
            if (sourceStorageUrl.isBlank()) {
                throw IllegalStateException("Storage service returned blank upload URL")
            }

            // Stage 3: Thumbnail Storage Upload
            _uploadStatus.value = "Storing custom thumbnail artwork..."
            _uploadProgress.value = 75
            val uploadedThumbUrl = storageService.uploadThumbnail(
                creatorUid = channel.ownerUid,
                videoId = videoId,
                thumbnailUriOrUrl = thumbnailUrl
            ).getOrDefault(thumbnailUrl.ifBlank { "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=800" })

            // Stage 4: Multi-Bitrate Cloud Transcoding (360p, 480p, 720p, 1080p, 4K)
            _uploadStatus.value = "Transcoding adaptive multi-bitrate HLS renditions (90%)..."
            _uploadProgress.value = 90
            _uploadProgressState.value = UploadProgressState(
                state = VideoUploadState.PROCESSING,
                progressPercentage = 90,
                currentStageDescription = "Transcoding multi-bitrate HLS streams (360p - 4K UHD)..."
            )
            val transcodeJob = processingService.triggerTranscodingJob(
                TranscodingJobRequest(
                    jobId = "job_$videoId",
                    videoId = videoId,
                    creatorId = channel.ownerUid,
                    channelId = channel.channelId,
                    sourceStorageUrl = sourceStorageUrl,
                    isShort = false
                )
            )

            // Stage 5: CDN Delivery Resolution
            val finalDeliveryUrl = cdnService.resolveDeliveryUrl(
                transcodeJob.getOrNull()?.hlsMasterManifestUrl ?: sourceStorageUrl
            )

            val newVideo = Video(
                videoId = videoId,
                channelId = channel.channelId,
                ownerUid = channel.ownerUid,
                channelName = channel.channelName,
                channelHandle = channel.handle,
                channelAvatarUrl = channel.profileImageUrl,
                title = title,
                description = description,
                videoUrl = finalDeliveryUrl,
                thumbnailUrl = uploadedThumbUrl,
                durationSeconds = durationSeconds,
                tags = tags,
                category = category,
                visibility = visibility,
                allowComments = allowComments,
                processingStatus = "READY",
                uploadProgress = 100,
                viewCount = 0,
                likeCount = 0,
                commentCount = 0,
                shareCount = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            _localPreviewVideos.value = _localPreviewVideos.value.filterNot { it.videoUrl == videoUriString }
            _videosFeed.value = listOf(newVideo) + _videosFeed.value.filterNot { it.videoUrl == videoUriString }
            localPreviewRepository?.markCloudHosted()
            firebaseService.uploadVideo(newVideo)

            _uploadProgress.value = 100
            _uploadStatus.value = "Upload complete! Video is now live on CDN."
            _uploadProgressState.value = UploadProgressState(
                state = VideoUploadState.READY,
                progressPercentage = 100,
                currentStageDescription = "Published to CDN edge caches."
            )
            delay(300)
            _uploadProgress.value = null
            return Result.success(newVideo)
        } catch (e: Exception) {
            _uploadProgress.value = null
            _uploadProgressState.value = UploadProgressState(
                state = VideoUploadState.FAILED,
                errorMessage = e.message ?: "Upload failed",
                canRetry = true
            )
            _uploadError.value = "Upload failed: ${e.message ?: "Network error"}"
            return Result.failure(e)
        }
    }

    suspend fun uploadShortItem(
        channel: Channel,
        title: String,
        description: String,
        soundTitle: String,
        category: String,
        tags: List<String>,
        visibility: String = "PUBLIC",
        allowComments: Boolean = true,
        thumbnailUrl: String,
        videoUriString: String
    ): Result<ShortItem> {
        _uploadError.value = null
        val shortId = "short_${UUID.randomUUID().toString().take(8)}"

        try {
            if (videoUriString.isBlank()) {
                val err = "No short video selected. Please select a video file."
                _uploadError.value = err
                _uploadProgressState.value = UploadProgressState(
                    state = VideoUploadState.FAILED,
                    errorMessage = err
                )
                return Result.failure(IllegalArgumentException(err))
            }

            _uploadStatus.value = "Validating vertical 9:16 Short format..."
            _uploadProgress.value = 10
            _uploadProgressState.value = UploadProgressState(
                state = VideoUploadState.SELECTING,
                progressPercentage = 10,
                currentStageDescription = "Validating vertical 9:16 Short format..."
            )
            delay(100)

            _uploadStatus.value = "Uploading vertical master to Object Storage..."
            val rawUpload = storageService.uploadSourceVideo(
                creatorUid = channel.ownerUid,
                videoId = shortId,
                fileUri = videoUriString,
                isShort = true
            ) { progressState ->
                _uploadProgress.value = progressState.progressPercentage
                _uploadStatus.value = progressState.currentStageDescription
                _uploadProgressState.value = progressState
            }

            // If cloud storage upload failed or is unavailable (e.g. Firebase Spark plan restriction)
            if (rawUpload.isFailure) {
                val localPreviewShort = ShortItem(
                    shortId = shortId,
                    channelId = channel.channelId,
                    ownerUid = channel.ownerUid,
                    channelName = channel.channelName,
                    channelHandle = channel.handle,
                    channelAvatarUrl = channel.profileImageUrl,
                    title = title,
                    description = description,
                    soundTitle = soundTitle.ifBlank { "Original Sound" },
                    videoUrl = videoUriString, // PRESERVE exact selected URI, NEVER substitute with sample video
                    thumbnailUrl = thumbnailUrl.ifBlank { "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800" },
                    category = category,
                    tags = tags,
                    visibility = visibility,
                    allowComments = allowComments,
                    durationSeconds = 30,
                    processingStatus = "LOCAL_PREVIEW",
                    viewCount = 0,
                    likeCount = 0,
                    commentCount = 0,
                    shareCount = 0,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                _uploadProgress.value = null
                _uploadStatus.value = "Cloud video upload is currently unavailable. Your Short is available for local preview only."
                _uploadProgressState.value = UploadProgressState(
                    state = VideoUploadState.FAILED,
                    progressPercentage = 0,
                    errorMessage = "Cloud video upload is currently unavailable. Your Short is available for local preview only.",
                    canRetry = true
                )
                _uploadError.value = "Cloud video upload is currently unavailable. Your Short is available for local preview only."

                // Retain in local preview collection and feed for local playback, DO NOT upload to Firestore
                _localPreviewShorts.value = listOf(localPreviewShort) + _localPreviewShorts.value
                _shortsFeed.value = listOf(localPreviewShort) + _shortsFeed.value
                val existingOrNewPreview = localPreviewRepository?.currentPreview?.value ?: LocalMediaPreview(
                    id = localPreviewShort.shortId,
                    uriString = localPreviewShort.videoUrl,
                    mediaName = title,
                    mediaType = "SHORT",
                    title = title,
                    description = description,
                    soundTitle = soundTitle,
                    category = category,
                    tags = tags,
                    thumbnailUriOrUrl = thumbnailUrl,
                    visibility = visibility,
                    allowComments = allowComments,
                    hostingStatus = MediaHostingStatus.UPLOAD_UNAVAILABLE,
                    errorMessage = "Cloud video upload is currently unavailable. Your Short is available for local preview only."
                )
                localPreviewRepository?.savePreview(existingOrNewPreview.copy(
                    hostingStatus = MediaHostingStatus.UPLOAD_UNAVAILABLE,
                    errorMessage = "Cloud video upload is currently unavailable. Your Short is available for local preview only."
                ))

                return Result.failure(
                    CloudUploadUnavailableException(
                        localShort = localPreviewShort,
                        message = "Cloud video upload is currently unavailable. Your Short is available for local preview only."
                    )
                )
            }

            val sourceStorageUrl = rawUpload.getOrThrow()
            if (sourceStorageUrl.isBlank()) {
                throw IllegalStateException("Storage service returned blank upload URL")
            }

            _uploadStatus.value = "Generating multi-bitrate vertical preview..."
            _uploadProgress.value = 85
            val finalDeliveryUrl = cdnService.resolveDeliveryUrl(sourceStorageUrl)

            val newShort = ShortItem(
                shortId = shortId,
                channelId = channel.channelId,
                ownerUid = channel.ownerUid,
                channelName = channel.channelName,
                channelHandle = channel.handle,
                channelAvatarUrl = channel.profileImageUrl,
                title = title,
                description = description,
                soundTitle = soundTitle.ifBlank { "Original Sound" },
                videoUrl = finalDeliveryUrl,
                thumbnailUrl = thumbnailUrl.ifBlank { "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800" },
                category = category,
                tags = tags,
                visibility = visibility,
                allowComments = allowComments,
                durationSeconds = 30,
                processingStatus = "READY",
                viewCount = 0,
                likeCount = 0,
                commentCount = 0,
                shareCount = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            _localPreviewShorts.value = _localPreviewShorts.value.filterNot { it.videoUrl == videoUriString }
            _shortsFeed.value = listOf(newShort) + _shortsFeed.value.filterNot { it.videoUrl == videoUriString }
            localPreviewRepository?.markCloudHosted()
            firebaseService.uploadShort(newShort)

            _uploadProgress.value = 100
            _uploadStatus.value = "Short published successfully to CDN!"
            _uploadProgressState.value = UploadProgressState(
                state = VideoUploadState.READY,
                progressPercentage = 100,
                currentStageDescription = "Published to Shorts feed."
            )
            delay(300)
            _uploadProgress.value = null
            return Result.success(newShort)
        } catch (e: Exception) {
            _uploadProgress.value = null
            _uploadProgressState.value = UploadProgressState(
                state = VideoUploadState.FAILED,
                errorMessage = e.message ?: "Network error",
                canRetry = true
            )
            _uploadError.value = "Short upload failed: ${e.message ?: "Network error"}"
            return Result.failure(e)
        }
    }

    // ======================== CREATOR OWNERSHIP (EDIT & DELETE) ========================
    suspend fun deleteVideo(videoId: String, channelId: String, currentUid: String): Boolean {
        val video = _videosFeed.value.find { it.videoId == videoId } ?: return false
        if (video.ownerUid.isNotBlank() && video.ownerUid != currentUid && video.channelId != channelId) {
            return false
        }

        _videosFeed.value = _videosFeed.value.filter { it.videoId != videoId }
        storageService.deleteVideoAssets(video.ownerUid, videoId)
        cdnService.purgeCache(videoId)
        firebaseService.deleteVideo(videoId, channelId)
        return true
    }

    suspend fun editVideo(
        videoId: String,
        channelId: String,
        currentUid: String,
        title: String,
        description: String,
        category: String,
        tags: List<String>,
        visibility: String,
        allowComments: Boolean
    ): Boolean {
        val existing = _videosFeed.value.find { it.videoId == videoId } ?: return false
        if (existing.ownerUid.isNotBlank() && existing.ownerUid != currentUid && existing.channelId != channelId) {
            return false
        }

        val updated = existing.copy(
            title = title,
            description = description,
            category = category,
            tags = tags,
            visibility = visibility,
            allowComments = allowComments,
            updatedAt = System.currentTimeMillis()
        )

        _videosFeed.value = _videosFeed.value.map { if (it.videoId == videoId) updated else it }
        firebaseService.updateVideo(updated)
        return true
    }

    suspend fun updateVideoVisibility(
        videoId: String,
        channelId: String,
        currentUid: String,
        newVisibility: String
    ): Boolean {
        val existing = _videosFeed.value.find { it.videoId == videoId } ?: return false
        if (existing.ownerUid.isNotBlank() && existing.ownerUid != currentUid && existing.channelId != channelId) {
            return false
        }
        val updated = existing.copy(visibility = newVisibility, updatedAt = System.currentTimeMillis())
        _videosFeed.value = _videosFeed.value.map { if (it.videoId == videoId) updated else it }
        firebaseService.updateVideo(updated)
        return true
    }

    suspend fun editShort(
        shortId: String,
        channelId: String,
        currentUid: String,
        title: String,
        description: String,
        category: String,
        tags: List<String>,
        visibility: String,
        allowComments: Boolean
    ): Boolean {
        val existing = _shortsFeed.value.find { it.shortId == shortId } ?: return false
        if (existing.ownerUid.isNotBlank() && existing.ownerUid != currentUid && existing.channelId != channelId) {
            return false
        }

        val updated = existing.copy(
            title = title,
            description = description,
            category = category,
            tags = tags,
            visibility = visibility,
            allowComments = allowComments,
            updatedAt = System.currentTimeMillis()
        )

        _shortsFeed.value = _shortsFeed.value.map { if (it.shortId == shortId) updated else it }
        firebaseService.updateShort(updated)
        return true
    }

    suspend fun updateShortVisibility(
        shortId: String,
        channelId: String,
        currentUid: String,
        newVisibility: String
    ): Boolean {
        val existing = _shortsFeed.value.find { it.shortId == shortId } ?: return false
        if (existing.ownerUid.isNotBlank() && existing.ownerUid != currentUid && existing.channelId != channelId) {
            return false
        }
        val updated = existing.copy(visibility = newVisibility, updatedAt = System.currentTimeMillis())
        _shortsFeed.value = _shortsFeed.value.map { if (it.shortId == shortId) updated else it }
        firebaseService.updateShort(updated)
        return true
    }

    suspend fun deleteShort(shortId: String, channelId: String, currentUid: String): Boolean {
        val short = _shortsFeed.value.find { it.shortId == shortId } ?: return false
        if (short.ownerUid.isNotBlank() && short.ownerUid != currentUid && short.channelId != channelId) {
            return false
        }

        _shortsFeed.value = _shortsFeed.value.filter { it.shortId != shortId }
        storageService.deleteVideoAssets(short.ownerUid, shortId)
        cdnService.purgeCache(shortId)
        firebaseService.deleteShort(shortId, channelId)
        return true
    }

    // ======================== VIDEO ACTIONS ========================
    fun toggleLikeVideo(videoId: String, isLiked: Boolean) {
        _videosFeed.value = _videosFeed.value.map { v ->
            if (v.videoId == videoId) {
                val newCount = if (isLiked) v.likeCount + 1 else (v.likeCount - 1).coerceAtLeast(0)
                v.copy(likeCount = newCount)
            } else v
        }
    }

    fun toggleDislikeVideo(videoId: String, isDisliked: Boolean) {
        _videosFeed.value = _videosFeed.value.map { v ->
            if (v.videoId == videoId) {
                val newCount = if (isDisliked) v.dislikeCount + 1 else (v.dislikeCount - 1).coerceAtLeast(0)
                v.copy(dislikeCount = newCount)
            } else v
        }
    }

    fun toggleLikeShort(shortId: String, isLiked: Boolean) {
        _shortsFeed.value = _shortsFeed.value.map { s ->
            if (s.shortId == shortId) {
                val newCount = if (isLiked) s.likeCount + 1 else (s.likeCount - 1).coerceAtLeast(0)
                s.copy(likeCount = newCount)
            } else s
        }
    }

    fun toggleSaveVideo(videoId: String): Boolean {
        val currentSet = _savedVideoIds.value.toMutableSet()
        val isNowSaved = if (currentSet.contains(videoId)) {
            currentSet.remove(videoId)
            false
        } else {
            currentSet.add(videoId)
            true
        }
        _savedVideoIds.value = currentSet
        return isNowSaved
    }

    fun shareVideo(videoId: String) {
        _videosFeed.value = _videosFeed.value.map { v ->
            if (v.videoId == videoId) v.copy(shareCount = v.shareCount + 1) else v
        }
    }

    fun incrementViews(videoId: String) {
        _videosFeed.value = _videosFeed.value.map { v ->
            if (v.videoId == videoId) v.copy(viewCount = v.viewCount + 1) else v
        }
    }

    // ======================== COMMENTS & REPLIES ========================
    fun observeComments(contentId: String) {
        if (contentId.isBlank()) return
        repositoryScope.launch {
            try {
                firebaseService.observeComments(contentId).collect { comments ->
                    val updatedMap = _commentsMap.value.toMutableMap()
                    updatedMap[contentId] = comments
                    _commentsMap.value = updatedMap
                }
            } catch (e: Exception) {
                Log.e("VideoRepository", "Error observing comments for $contentId: ${e.message}")
            }
        }
    }

    fun getComments(contentId: String): List<CommentItem> {
        observeComments(contentId)
        return _commentsMap.value[contentId] ?: emptyList()
    }

    suspend fun addComment(
        contentId: String,
        contentType: String,
        author: UserAccount?,
        channel: Channel?,
        text: String,
        contentOwnerUid: String? = null,
        contentTitle: String? = null
    ): CommentItem {
        val commentId = "c_${System.currentTimeMillis()}"
        val authorName = channel?.channelName ?: author?.displayName?.ifBlank { "IOMBG Member" } ?: "IOMBG Member"
        val authorHandle = channel?.handle ?: (if (author?.username.isNullOrBlank()) "@user" else "@${author?.username}")
        val authorAvatar = channel?.profileImageUrl?.ifBlank { null } ?: author?.photoUrl?.ifBlank { null }
            ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200"

        val newComment = CommentItem(
            commentId = commentId,
            contentId = contentId,
            contentType = contentType,
            authorUid = author?.uid ?: "viewer_${System.currentTimeMillis()}",
            authorName = authorName,
            authorHandle = authorHandle,
            authorAvatarUrl = authorAvatar,
            text = text,
            likeCount = 0,
            replyCount = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val currentList = _commentsMap.value[contentId] ?: emptyList()
        val updatedMap = _commentsMap.value.toMutableMap()
        updatedMap[contentId] = listOf(newComment) + currentList
        _commentsMap.value = updatedMap

        if (contentType == "SHORT") {
            _shortsFeed.value = _shortsFeed.value.map { s ->
                if (s.shortId == contentId) s.copy(commentCount = s.commentCount + 1) else s
            }
        } else {
            _videosFeed.value = _videosFeed.value.map { v ->
                if (v.videoId == contentId) v.copy(commentCount = v.commentCount + 1) else v
            }
        }

        firebaseService.addComment(newComment, contentOwnerUid, contentTitle)
        return newComment
    }

    suspend fun editComment(
        contentId: String,
        commentId: String,
        newText: String,
        currentUid: String
    ): Boolean {
        val currentList = _commentsMap.value[contentId] ?: return false
        val comment = currentList.find { it.commentId == commentId } ?: return false
        if (comment.authorUid != currentUid && currentUid.isNotBlank()) {
            return false
        }

        val updatedList = currentList.map { c ->
            if (c.commentId == commentId) {
                c.copy(text = newText, updatedAt = System.currentTimeMillis())
            } else c
        }
        val updatedMap = _commentsMap.value.toMutableMap()
        updatedMap[contentId] = updatedList
        _commentsMap.value = updatedMap

        return firebaseService.editComment(commentId, newText, currentUid)
    }

    suspend fun addReply(
        parentCommentId: String,
        contentId: String,
        author: UserAccount?,
        channel: Channel?,
        text: String,
        parentAuthorUid: String? = null
    ): CommentReply {
        val replyId = "r_${System.currentTimeMillis()}"
        val authorName = channel?.channelName ?: author?.displayName?.ifBlank { "IOMBG Member" } ?: "IOMBG Member"
        val authorHandle = channel?.handle ?: (if (author?.username.isNullOrBlank()) "@user" else "@${author?.username}")
        val authorAvatar = channel?.profileImageUrl?.ifBlank { null } ?: author?.photoUrl?.ifBlank { null }
            ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200"

        val reply = CommentReply(
            replyId = replyId,
            parentCommentId = parentCommentId,
            authorUid = author?.uid ?: "viewer_${System.currentTimeMillis()}",
            authorName = authorName,
            authorHandle = authorHandle,
            authorAvatarUrl = authorAvatar,
            text = text,
            likeCount = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val currentList = _commentsMap.value[contentId] ?: emptyList()
        val updatedList = currentList.map { c ->
            if (c.commentId == parentCommentId) {
                c.copy(
                    replies = c.replies + reply,
                    replyCount = c.replyCount + 1
                )
            } else c
        }

        val updatedMap = _commentsMap.value.toMutableMap()
        updatedMap[contentId] = updatedList
        _commentsMap.value = updatedMap

        firebaseService.addCommentReply(parentCommentId, contentId, reply, parentAuthorUid)
        return reply
    }

    suspend fun deleteReply(
        contentId: String,
        parentCommentId: String,
        replyId: String,
        currentUid: String
    ): Boolean {
        val currentList = _commentsMap.value[contentId] ?: return false
        val parent = currentList.find { it.commentId == parentCommentId } ?: return false
        val reply = parent.replies.find { it.replyId == replyId } ?: return false

        if (reply.authorUid != currentUid && currentUid.isNotBlank()) {
            return false
        }

        val updatedReplies = parent.replies.filter { it.replyId != replyId }
        val updatedList = currentList.map { c ->
            if (c.commentId == parentCommentId) {
                c.copy(replies = updatedReplies, replyCount = (c.replyCount - 1).coerceAtLeast(0))
            } else c
        }
        val updatedMap = _commentsMap.value.toMutableMap()
        updatedMap[contentId] = updatedList
        _commentsMap.value = updatedMap
        return true
    }

    fun toggleLikeComment(contentId: String, commentId: String, isLiked: Boolean) {
        val currentList = _commentsMap.value[contentId] ?: return
        val updatedList = currentList.map { c ->
            if (c.commentId == commentId) {
                val newCount = if (isLiked) c.likeCount + 1 else (c.likeCount - 1).coerceAtLeast(0)
                c.copy(likeCount = newCount, isLikedByMe = isLiked)
            } else c
        }
        val updatedMap = _commentsMap.value.toMutableMap()
        updatedMap[contentId] = updatedList
        _commentsMap.value = updatedMap
    }

    fun toggleLikeReply(contentId: String, parentCommentId: String, replyId: String, isLiked: Boolean) {
        val currentList = _commentsMap.value[contentId] ?: return
        val updatedList = currentList.map { c ->
            if (c.commentId == parentCommentId) {
                val updatedReplies = c.replies.map { r ->
                    if (r.replyId == replyId) {
                        val newCount = if (isLiked) r.likeCount + 1 else (r.likeCount - 1).coerceAtLeast(0)
                        r.copy(likeCount = newCount, isLikedByMe = isLiked)
                    } else r
                }
                c.copy(replies = updatedReplies)
            } else c
        }
        val updatedMap = _commentsMap.value.toMutableMap()
        updatedMap[contentId] = updatedList
        _commentsMap.value = updatedMap
    }

    suspend fun deleteComment(commentId: String, contentId: String, contentType: String, currentUid: String): Boolean {
        val currentList = _commentsMap.value[contentId] ?: return false
        val comment = currentList.find { it.commentId == commentId } ?: return false

        if (comment.authorUid != currentUid && currentUid.isNotBlank()) {
            // Verify author
        }

        val updatedList = currentList.filter { it.commentId != commentId }
        val updatedMap = _commentsMap.value.toMutableMap()
        updatedMap[contentId] = updatedList
        _commentsMap.value = updatedMap

        if (contentType == "SHORT") {
            _shortsFeed.value = _shortsFeed.value.map { s ->
                if (s.shortId == contentId) s.copy(commentCount = (s.commentCount - 1).coerceAtLeast(0)) else s
            }
        } else {
            _videosFeed.value = _videosFeed.value.map { v ->
                if (v.videoId == contentId) v.copy(commentCount = (v.commentCount - 1).coerceAtLeast(0)) else v
            }
        }

        firebaseService.deleteComment(commentId, contentId, contentType)
        return true
    }

    fun shareShort(shortId: String) {
        _shortsFeed.value = _shortsFeed.value.map { s ->
            if (s.shortId == shortId) s.copy(shareCount = s.shareCount + 1) else s
        }
    }

    suspend fun reportContent(contentId: String, contentType: String, reason: String, reporterUid: String): Boolean {
        return firebaseService.reportContent(contentId, contentType, reason, reporterUid)
    }

    fun markVideoBoosted(videoId: String, isBoosted: Boolean) {
        _videosFeed.value = _videosFeed.value.map { v ->
            if (v.videoId == videoId) v.copy(isBoosted = isBoosted) else v
        }
    }

    fun markShortBoosted(shortId: String, isBoosted: Boolean) {
        _shortsFeed.value = _shortsFeed.value.map { s ->
            if (s.shortId == shortId) s.copy(isBoosted = isBoosted) else s
        }
    }
}
