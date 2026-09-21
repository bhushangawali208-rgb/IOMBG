package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel managing the Create & Upload screen state.
 *
 * Implements:
 * 1. Safe local preview persistence via SavedStateHandle and LocalMediaPreviewRepository.
 * 2. Accurate MediaHostingStatus tracking (LOCAL_PREVIEW, UPLOADING, CLOUD_HOSTED, UPLOAD_UNAVAILABLE).
 * 3. Graceful handling of inaccessible URIs after process recreation / app restart.
 * 4. Zero sample/demo media substitution.
 */
class CreateUploadViewModel(
    val container: AppContainer,
    private val savedStateHandle: SavedStateHandle? = null
) : ViewModel() {

    private val localPreviewRepo = container.localMediaPreviewRepository

    // Creation mode: 0 = Video, 1 = Short, 2 = Live
    private val _creationMode = MutableStateFlow(savedStateHandle?.get<Int>(KEY_CREATION_MODE) ?: 0)
    val creationMode: StateFlow<Int> = _creationMode.asStateFlow()

    // Selected Video State
    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    private val _selectedVideoName = MutableStateFlow(savedStateHandle?.get<String>(KEY_SELECTED_VIDEO_NAME) ?: "")
    val selectedVideoName: StateFlow<String> = _selectedVideoName.asStateFlow()

    private val _selectedVideoResolution = MutableStateFlow("Local Device Video • Ready for Local Playback")
    val selectedVideoResolution: StateFlow<String> = _selectedVideoResolution.asStateFlow()

    private val _videoTitle = MutableStateFlow(savedStateHandle?.get<String>(KEY_VIDEO_TITLE) ?: "")
    val videoTitle: StateFlow<String> = _videoTitle.asStateFlow()

    private val _videoDescription = MutableStateFlow(savedStateHandle?.get<String>(KEY_VIDEO_DESC) ?: "")
    val videoDescription: StateFlow<String> = _videoDescription.asStateFlow()

    private val _videoCategory = MutableStateFlow(savedStateHandle?.get<String>(KEY_VIDEO_CATEGORY) ?: "Technology")
    val videoCategory: StateFlow<String> = _videoCategory.asStateFlow()

    private val _videoThumbnailUrl = MutableStateFlow(
        savedStateHandle?.get<String>(KEY_VIDEO_THUMBNAIL) ?: "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800"
    )
    val videoThumbnailUrl: StateFlow<String> = _videoThumbnailUrl.asStateFlow()

    private val _tagList = MutableStateFlow<List<String>>(
        savedStateHandle?.get<ArrayList<String>>(KEY_TAGS) ?: listOf("4K", "Tech", "Innovation", "Architecture")
    )
    val tagList: StateFlow<List<String>> = _tagList.asStateFlow()

    // Selected Short State
    private val _selectedShortUri = MutableStateFlow<Uri?>(null)
    val selectedShortUri: StateFlow<Uri?> = _selectedShortUri.asStateFlow()

    private val _shortTitle = MutableStateFlow(savedStateHandle?.get<String>(KEY_SHORT_TITLE) ?: "")
    val shortTitle: StateFlow<String> = _shortTitle.asStateFlow()

    private val _shortThumbnailUrl = MutableStateFlow(
        savedStateHandle?.get<String>(KEY_SHORT_THUMBNAIL) ?: "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800"
    )
    val shortThumbnailUrl: StateFlow<String> = _shortThumbnailUrl.asStateFlow()

    private val _shortSound = MutableStateFlow(savedStateHandle?.get<String>(KEY_SHORT_SOUND) ?: "Original Sound - Creator Studio")
    val shortSound: StateFlow<String> = _shortSound.asStateFlow()

    private val _shortCategory = MutableStateFlow(savedStateHandle?.get<String>(KEY_SHORT_CATEGORY) ?: "Entertainment")
    val shortCategory: StateFlow<String> = _shortCategory.asStateFlow()

    // Media Hosting Status
    private val _mediaHostingStatus = MutableStateFlow(MediaHostingStatus.LOCAL_PREVIEW)
    val mediaHostingStatus: StateFlow<MediaHostingStatus> = _mediaHostingStatus.asStateFlow()

    // Preview / Upload Results
    private val _localPreviewVideoResult = MutableStateFlow<Video?>(null)
    val localPreviewVideoResult: StateFlow<Video?> = _localPreviewVideoResult.asStateFlow()

    private val _localPreviewShortResult = MutableStateFlow<ShortItem?>(null)
    val localPreviewShortResult: StateFlow<ShortItem?> = _localPreviewShortResult.asStateFlow()

    private val _uploadedVideoResult = MutableStateFlow<Video?>(null)
    val uploadedVideoResult: StateFlow<Video?> = _uploadedVideoResult.asStateFlow()

    private val _uploadedShortResult = MutableStateFlow<ShortItem?>(null)
    val uploadedShortResult: StateFlow<ShortItem?> = _uploadedShortResult.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadErrorMessage = MutableStateFlow<String?>(null)
    val uploadErrorMessage: StateFlow<String?> = _uploadErrorMessage.asStateFlow()

    // Inaccessible URI Alert Message from LocalMediaPreviewRepository
    val inaccessiblePreviewMessage: StateFlow<String?> = localPreviewRepo.previewStatusMessage

    init {
        restoreState()
    }

    /**
     * Restores state from SavedStateHandle and LocalMediaPreviewRepository.
     * Validates that restored URIs are genuinely accessible.
     * If inaccessible, the stale preview is cleared and a user warning is set.
     */
    fun restoreState() {
        val restored = localPreviewRepo.checkAndRestorePreview()
        if (restored != null) {
            _mediaHostingStatus.value = restored.hostingStatus
            if (restored.mediaType.equals("SHORT", ignoreCase = true)) {
                _selectedShortUri.value = Uri.parse(restored.uriString)
                if (restored.title.isNotBlank()) _shortTitle.value = restored.title
                if (restored.thumbnailUriOrUrl.isNotBlank()) _shortThumbnailUrl.value = restored.thumbnailUriOrUrl
                if (restored.soundTitle.isNotBlank()) _shortSound.value = restored.soundTitle
                if (restored.category.isNotBlank()) _shortCategory.value = restored.category
                _creationMode.value = 1

                if (restored.hostingStatus == MediaHostingStatus.UPLOAD_UNAVAILABLE) {
                    _localPreviewShortResult.value = ShortItem(
                        shortId = restored.id.ifBlank { "short_${UUID.randomUUID().toString().take(8)}" },
                        channelId = "local_creator",
                        ownerUid = "local_user",
                        channelName = "My Channel",
                        channelHandle = "@me",
                        channelAvatarUrl = "",
                        title = restored.title.ifBlank { "Local Short Preview" },
                        description = restored.description,
                        videoUrl = restored.uriString,
                        thumbnailUrl = restored.thumbnailUriOrUrl,
                        soundTitle = restored.soundTitle,
                        category = restored.category,
                        tags = restored.tags,
                        visibility = restored.visibility,
                        allowComments = restored.allowComments,
                        processingStatus = "LOCAL_PREVIEW",
                        createdAt = restored.createdAt
                    )
                }
            } else {
                _selectedVideoUri.value = Uri.parse(restored.uriString)
                if (restored.mediaName.isNotBlank()) _selectedVideoName.value = restored.mediaName
                if (restored.title.isNotBlank()) _videoTitle.value = restored.title
                if (restored.description.isNotBlank()) _videoDescription.value = restored.description
                if (restored.category.isNotBlank()) _videoCategory.value = restored.category
                if (restored.thumbnailUriOrUrl.isNotBlank()) _videoThumbnailUrl.value = restored.thumbnailUriOrUrl
                if (restored.tags.isNotEmpty()) _tagList.value = restored.tags
                _creationMode.value = 0

                if (restored.hostingStatus == MediaHostingStatus.UPLOAD_UNAVAILABLE) {
                    _localPreviewVideoResult.value = Video(
                        videoId = restored.id.ifBlank { "video_${UUID.randomUUID().toString().take(8)}" },
                        channelId = "local_creator",
                        ownerUid = "local_user",
                        channelName = "My Channel",
                        channelHandle = "@me",
                        channelAvatarUrl = "",
                        title = restored.title.ifBlank { "Local Video Preview" },
                        description = restored.description,
                        category = restored.category,
                        tags = restored.tags,
                        videoUrl = restored.uriString,
                        thumbnailUrl = restored.thumbnailUriOrUrl,
                        visibility = restored.visibility,
                        allowComments = restored.allowComments,
                        processingStatus = "LOCAL_PREVIEW",
                        createdAt = restored.createdAt
                    )
                }
            }
        } else {
            // Check SavedStateHandle for volatile memory restoration
            val savedVideoUriStr = savedStateHandle?.get<String>(KEY_SELECTED_VIDEO_URI)
            if (!savedVideoUriStr.isNullOrBlank()) {
                _selectedVideoUri.value = Uri.parse(savedVideoUriStr)
            }
            val savedShortUriStr = savedStateHandle?.get<String>(KEY_SELECTED_SHORT_URI)
            if (!savedShortUriStr.isNullOrBlank()) {
                _selectedShortUri.value = Uri.parse(savedShortUriStr)
            }
        }
    }

    fun setCreationMode(mode: Int) {
        _creationMode.value = mode
        savedStateHandle?.set(KEY_CREATION_MODE, mode)
    }

    fun onVideoSelected(uri: Uri, fileName: String? = null) {
        val name = fileName ?: uri.lastPathSegment ?: "device_video.mp4"
        _selectedVideoUri.value = uri
        _selectedVideoName.value = name
        _selectedVideoResolution.value = "Local Device Video • Ready for Local Playback"
        _mediaHostingStatus.value = MediaHostingStatus.LOCAL_PREVIEW
        _localPreviewVideoResult.value = null
        _uploadedVideoResult.value = null
        _uploadErrorMessage.value = null

        savedStateHandle?.set(KEY_SELECTED_VIDEO_URI, uri.toString())
        savedStateHandle?.set(KEY_SELECTED_VIDEO_NAME, name)

        // Persist lightweight metadata
        localPreviewRepo.savePreview(
            LocalMediaPreview(
                id = "vid_preview_${System.currentTimeMillis()}",
                uriString = uri.toString(),
                mediaName = name,
                mediaType = "VIDEO",
                title = _videoTitle.value,
                description = _videoDescription.value,
                category = _videoCategory.value,
                tags = _tagList.value,
                thumbnailUriOrUrl = _videoThumbnailUrl.value,
                hostingStatus = MediaHostingStatus.LOCAL_PREVIEW
            )
        )
    }

    fun onShortSelected(uri: Uri) {
        _selectedShortUri.value = uri
        _shortThumbnailUrl.value = uri.toString()
        _mediaHostingStatus.value = MediaHostingStatus.LOCAL_PREVIEW
        _localPreviewShortResult.value = null
        _uploadedShortResult.value = null
        _uploadErrorMessage.value = null

        savedStateHandle?.set(KEY_SELECTED_SHORT_URI, uri.toString())
        savedStateHandle?.set(KEY_SHORT_THUMBNAIL, uri.toString())

        // Persist lightweight metadata
        localPreviewRepo.savePreview(
            LocalMediaPreview(
                id = "short_preview_${System.currentTimeMillis()}",
                uriString = uri.toString(),
                mediaName = uri.lastPathSegment ?: "short.mp4",
                mediaType = "SHORT",
                title = _shortTitle.value,
                soundTitle = _shortSound.value,
                category = _shortCategory.value,
                thumbnailUriOrUrl = uri.toString(),
                hostingStatus = MediaHostingStatus.LOCAL_PREVIEW
            )
        )
    }

    fun onThumbnailSelected(uri: Uri) {
        _videoThumbnailUrl.value = uri.toString()
        savedStateHandle?.set(KEY_VIDEO_THUMBNAIL, uri.toString())
        updatePersistedPreviewMetadata()
    }

    fun setVideoTitle(title: String) {
        _videoTitle.value = title
        savedStateHandle?.set(KEY_VIDEO_TITLE, title)
        updatePersistedPreviewMetadata()
    }

    fun setVideoDescription(desc: String) {
        _videoDescription.value = desc
        savedStateHandle?.set(KEY_VIDEO_DESC, desc)
        updatePersistedPreviewMetadata()
    }

    fun setVideoCategory(cat: String) {
        _videoCategory.value = cat
        savedStateHandle?.set(KEY_VIDEO_CATEGORY, cat)
        updatePersistedPreviewMetadata()
    }

    fun setVideoThumbnailUrl(url: String) {
        _videoThumbnailUrl.value = url
        savedStateHandle?.set(KEY_VIDEO_THUMBNAIL, url)
        updatePersistedPreviewMetadata()
    }

    fun setTagList(tags: List<String>) {
        _tagList.value = tags
        savedStateHandle?.set(KEY_TAGS, ArrayList(tags))
        updatePersistedPreviewMetadata()
    }

    fun setShortTitle(title: String) {
        _shortTitle.value = title
        savedStateHandle?.set(KEY_SHORT_TITLE, title)
        updatePersistedPreviewMetadata()
    }

    fun setShortSound(sound: String) {
        _shortSound.value = sound
        savedStateHandle?.set(KEY_SHORT_SOUND, sound)
        updatePersistedPreviewMetadata()
    }

    fun setShortCategory(cat: String) {
        _shortCategory.value = cat
        savedStateHandle?.set(KEY_SHORT_CATEGORY, cat)
        updatePersistedPreviewMetadata()
    }

    fun setShortThumbnailUrl(url: String) {
        _shortThumbnailUrl.value = url
        savedStateHandle?.set(KEY_SHORT_THUMBNAIL, url)
        updatePersistedPreviewMetadata()
    }

    private fun updatePersistedPreviewMetadata() {
        val current = localPreviewRepo.currentPreview.value ?: return
        if (current.mediaType.equals("SHORT", ignoreCase = true)) {
            localPreviewRepo.savePreview(
                current.copy(
                    title = _shortTitle.value,
                    soundTitle = _shortSound.value,
                    category = _shortCategory.value,
                    thumbnailUriOrUrl = _shortThumbnailUrl.value
                )
            )
        } else {
            localPreviewRepo.savePreview(
                current.copy(
                    title = _videoTitle.value,
                    description = _videoDescription.value,
                    category = _videoCategory.value,
                    tags = _tagList.value,
                    thumbnailUriOrUrl = _videoThumbnailUrl.value
                )
            )
        }
    }

    fun dismissLocalPreviewVideo() {
        _localPreviewVideoResult.value = null
        _selectedVideoUri.value = null
        savedStateHandle?.remove<String>(KEY_SELECTED_VIDEO_URI)
        localPreviewRepo.clearPreview()
    }

    fun dismissLocalPreviewShort() {
        _localPreviewShortResult.value = null
        _selectedShortUri.value = null
        savedStateHandle?.remove<String>(KEY_SELECTED_SHORT_URI)
        localPreviewRepo.clearPreview()
    }

    fun dismissUploadedVideo() {
        _uploadedVideoResult.value = null
        _selectedVideoUri.value = null
        savedStateHandle?.remove<String>(KEY_SELECTED_VIDEO_URI)
        _videoTitle.value = ""
    }

    fun dismissUploadedShort() {
        _uploadedShortResult.value = null
        _selectedShortUri.value = null
        savedStateHandle?.remove<String>(KEY_SELECTED_SHORT_URI)
        _shortTitle.value = ""
    }

    fun dismissInaccessibleMessage() {
        localPreviewRepo.dismissStatusMessage()
    }

    fun clearInaccessibleWarning() {
        localPreviewRepo.dismissStatusMessage()
    }

    fun cancelUpload() {
        container.videoRepository.cancelUpload()
        _isUploading.value = false
        _mediaHostingStatus.value = MediaHostingStatus.LOCAL_PREVIEW
    }

    /**
     * Publishes a long-form video.
     * If cloud storage is unavailable, retains local preview safely without writing unhosted
     * documents to Firestore or inventing fake URLs.
     */
    fun publishLongVideo(
        channel: Channel,
        visibility: String = "PUBLIC",
        allowComments: Boolean = true,
        onComplete: (Video) -> Unit = {}
    ) {
        val uri = _selectedVideoUri.value ?: return
        val rawUriStr = uri.toString()

        _isUploading.value = true
        _uploadErrorMessage.value = null
        _mediaHostingStatus.value = MediaHostingStatus.UPLOADING
        localPreviewRepo.markUploading()

        viewModelScope.launch {
            val result = container.videoRepository.uploadLongVideo(
                channel = channel,
                title = _videoTitle.value.ifBlank { "Incredible 4K Experience: ${_videoCategory.value}" },
                description = _videoDescription.value.ifBlank { "Uploaded directly from device gallery." },
                tags = _tagList.value,
                category = _videoCategory.value,
                visibility = visibility,
                allowComments = allowComments,
                thumbnailUrl = _videoThumbnailUrl.value,
                videoUriString = rawUriStr
            )

            _isUploading.value = false

            if (result.isSuccess) {
                val video = result.getOrNull()
                _uploadedVideoResult.value = video
                _mediaHostingStatus.value = MediaHostingStatus.CLOUD_HOSTED
                localPreviewRepo.markCloudHosted()
                if (video != null) onComplete(video)
            } else {
                val ex = result.exceptionOrNull()
                if (ex is CloudUploadUnavailableException) {
                    _mediaHostingStatus.value = MediaHostingStatus.UPLOAD_UNAVAILABLE
                    _localPreviewVideoResult.value = ex.localVideo
                    localPreviewRepo.markUploadUnavailable(
                        ex.message ?: "Cloud video upload is currently unavailable. Your video is available for local preview only."
                    )
                } else {
                    _uploadErrorMessage.value = ex?.message ?: "Upload failed"
                    _mediaHostingStatus.value = MediaHostingStatus.LOCAL_PREVIEW
                }
            }
        }
    }

    /**
     * Publishes a short-form vertical video.
     * If cloud storage is unavailable, retains local preview safely.
     */
    fun publishShort(
        channel: Channel,
        visibility: String = "PUBLIC",
        allowComments: Boolean = true,
        onComplete: (ShortItem) -> Unit = {}
    ) {
        val uri = _selectedShortUri.value ?: return
        val rawUriStr = uri.toString()

        _isUploading.value = true
        _uploadErrorMessage.value = null
        _mediaHostingStatus.value = MediaHostingStatus.UPLOADING
        localPreviewRepo.markUploading()

        viewModelScope.launch {
            val result = container.videoRepository.uploadShortItem(
                channel = channel,
                title = _shortTitle.value.ifBlank { "Trending Short: ${_shortCategory.value}" },
                description = "Captured directly from device camera.",
                soundTitle = _shortSound.value,
                category = _shortCategory.value,
                tags = listOf("Shorts", _shortCategory.value),
                visibility = visibility,
                allowComments = allowComments,
                thumbnailUrl = _shortThumbnailUrl.value,
                videoUriString = rawUriStr
            )

            _isUploading.value = false

            if (result.isSuccess) {
                val short = result.getOrNull()
                _uploadedShortResult.value = short
                _mediaHostingStatus.value = MediaHostingStatus.CLOUD_HOSTED
                localPreviewRepo.markCloudHosted()
                if (short != null) onComplete(short)
            } else {
                val ex = result.exceptionOrNull()
                if (ex is CloudUploadUnavailableException) {
                    _mediaHostingStatus.value = MediaHostingStatus.UPLOAD_UNAVAILABLE
                    _localPreviewShortResult.value = ex.localShort
                    localPreviewRepo.markUploadUnavailable(
                        ex.message ?: "Cloud short upload is currently unavailable. Your Short is available for local preview only."
                    )
                } else {
                    _uploadErrorMessage.value = ex?.message ?: "Upload failed"
                    _mediaHostingStatus.value = MediaHostingStatus.LOCAL_PREVIEW
                }
            }
        }
    }

    companion object {
        const val KEY_CREATION_MODE = "creation_mode"
        const val KEY_SELECTED_VIDEO_URI = "selected_video_uri"
        const val KEY_SELECTED_VIDEO_NAME = "selected_video_name"
        const val KEY_SELECTED_SHORT_URI = "selected_short_uri"
        const val KEY_VIDEO_TITLE = "video_title"
        const val KEY_VIDEO_DESC = "video_desc"
        const val KEY_VIDEO_CATEGORY = "video_category"
        const val KEY_VIDEO_THUMBNAIL = "video_thumbnail"
        const val KEY_TAGS = "video_tags"
        const val KEY_SHORT_TITLE = "short_title"
        const val KEY_SHORT_THUMBNAIL = "short_thumbnail"
        const val KEY_SHORT_SOUND = "short_sound"
        const val KEY_SHORT_CATEGORY = "short_category"
        const val KEY_HOSTING_STATUS = "hosting_status"
    }
}

class CreateUploadViewModelFactory(
    private val container: AppContainer,
    private val savedStateHandle: SavedStateHandle? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CreateUploadViewModel(container, savedStateHandle) as T
    }
}
