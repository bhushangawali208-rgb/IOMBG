package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.data.model.LocalMediaPreview
import com.example.data.model.MediaHostingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Validates whether a media URI is currently readable and accessible by this application process.
 */
fun interface UriAccessibilityChecker {
    fun isAccessible(uriString: String): Boolean
}

/**
 * Android system implementation of UriAccessibilityChecker.
 * Handles content URIs, file URIs, and remote HTTPS references safely.
 */
class AndroidUriAccessibilityChecker(private val context: Context) : UriAccessibilityChecker {
    override fun isAccessible(uriString: String): Boolean {
        if (uriString.isBlank()) return false
        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            return false
        }

        return try {
            when (uri.scheme) {
                "content" -> {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                }
                "file" -> {
                    val path = uri.path ?: return false
                    val file = File(path)
                    file.exists() && file.canRead()
                }
                "http", "https" -> true
                else -> false
            }
        } catch (e: SecurityException) {
            false
        } catch (e: java.io.FileNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Local Media Preview Repository for IOMBG.
 *
 * Implements lightweight persistence for user-selected media previews without copying large
 * media files or generating fake cloud URLs / fake Firestore records.
 *
 * Responsibilities:
 * 1. Preserves original local content URIs exactly as selected.
 * 2. Restores media preview metadata across Activity / process recreation and app restart if still accessible.
 * 3. Gracefully clears stale previews and notifies the user if the URI is no longer accessible.
 * 4. Never substitutes fake, demo, or sample media.
 * 5. Accurately tracks MediaHostingStatus: LOCAL_PREVIEW, UPLOADING, CLOUD_HOSTED, UPLOAD_UNAVAILABLE.
 */
class LocalMediaPreviewRepository(
    private val context: Context,
    private val accessibilityChecker: UriAccessibilityChecker = AndroidUriAccessibilityChecker(context)
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentPreview = MutableStateFlow<LocalMediaPreview?>(null)
    val currentPreview: StateFlow<LocalMediaPreview?> = _currentPreview.asStateFlow()

    private val _previewStatusMessage = MutableStateFlow<String?>(null)
    val previewStatusMessage: StateFlow<String?> = _previewStatusMessage.asStateFlow()

    init {
        checkAndRestorePreview()
    }

    /**
     * Persists lightweight preview metadata for a user-selected video or short.
     * Does NOT copy the media file.
     */
    fun savePreview(preview: LocalMediaPreview) {
        prefs.edit()
            .putString(KEY_ID, preview.id)
            .putString(KEY_URI_STRING, preview.uriString)
            .putString(KEY_MEDIA_NAME, preview.mediaName)
            .putString(KEY_MEDIA_TYPE, preview.mediaType)
            .putString(KEY_TITLE, preview.title)
            .putString(KEY_DESCRIPTION, preview.description)
            .putString(KEY_CATEGORY, preview.category)
            .putString(KEY_TAGS, preview.tags.joinToString(","))
            .putString(KEY_THUMBNAIL, preview.thumbnailUriOrUrl)
            .putString(KEY_SOUND_TITLE, preview.soundTitle)
            .putString(KEY_VISIBILITY, preview.visibility)
            .putBoolean(KEY_ALLOW_COMMENTS, preview.allowComments)
            .putString(KEY_HOSTING_STATUS, preview.hostingStatus.name)
            .putString(KEY_ERROR_MESSAGE, preview.errorMessage)
            .putLong(KEY_CREATED_AT, preview.createdAt)
            .apply()

        _currentPreview.value = preview
        _previewStatusMessage.value = null
    }

    /**
     * Checks if a persisted preview exists and verifies whether its URI is still accessible.
     * If accessible, restores it. If inaccessible, cleanly purges the stale preview and
     * notifies the user without substituting any demo media.
     */
    fun checkAndRestorePreview(): LocalMediaPreview? {
        val uriString = prefs.getString(KEY_URI_STRING, null)
        if (uriString.isNullOrBlank()) {
            _currentPreview.value = null
            return null
        }

        val isAccessible = accessibilityChecker.isAccessible(uriString)
        if (!isAccessible) {
            // URI is no longer accessible (permission revoked, file deleted, etc.)
            clearPersistedStorageOnly()
            _currentPreview.value = null
            _previewStatusMessage.value = INACCESSIBLE_URI_MESSAGE
            return null
        }

        val tagsRaw = prefs.getString(KEY_TAGS, "") ?: ""
        val tagsList = if (tagsRaw.isNotBlank()) tagsRaw.split(",") else emptyList()
        val hostingStatusStr = prefs.getString(KEY_HOSTING_STATUS, MediaHostingStatus.LOCAL_PREVIEW.name)
        val hostingStatus = try {
            MediaHostingStatus.valueOf(hostingStatusStr ?: MediaHostingStatus.LOCAL_PREVIEW.name)
        } catch (e: Exception) {
            MediaHostingStatus.LOCAL_PREVIEW
        }

        val restored = LocalMediaPreview(
            id = prefs.getString(KEY_ID, "") ?: "",
            uriString = uriString,
            mediaName = prefs.getString(KEY_MEDIA_NAME, "") ?: "",
            mediaType = prefs.getString(KEY_MEDIA_TYPE, "VIDEO") ?: "VIDEO",
            title = prefs.getString(KEY_TITLE, "") ?: "",
            description = prefs.getString(KEY_DESCRIPTION, "") ?: "",
            category = prefs.getString(KEY_CATEGORY, "Technology") ?: "Technology",
            tags = tagsList,
            thumbnailUriOrUrl = prefs.getString(KEY_THUMBNAIL, "") ?: "",
            soundTitle = prefs.getString(KEY_SOUND_TITLE, "Original Sound") ?: "Original Sound",
            visibility = prefs.getString(KEY_VISIBILITY, "PUBLIC") ?: "PUBLIC",
            allowComments = prefs.getBoolean(KEY_ALLOW_COMMENTS, true),
            hostingStatus = hostingStatus,
            errorMessage = prefs.getString(KEY_ERROR_MESSAGE, null),
            createdAt = prefs.getLong(KEY_CREATED_AT, System.currentTimeMillis())
        )

        _currentPreview.value = restored
        _previewStatusMessage.value = null
        return restored
    }

    /**
     * Clears any active local preview and associated persistence.
     */
    fun clearPreview(statusMessage: String? = null) {
        clearPersistedStorageOnly()
        _currentPreview.value = null
        _previewStatusMessage.value = statusMessage
    }

    /**
     * Marks the current preview as actively uploading.
     */
    fun markUploading() {
        val current = _currentPreview.value ?: return
        savePreview(current.copy(hostingStatus = MediaHostingStatus.UPLOADING, errorMessage = null))
    }

    /**
     * Marks the current preview as upload unavailable (e.g. Firebase Spark restriction).
     * Retains the local preview while explicitly denoting it as unhosted.
     */
    fun markUploadUnavailable(errorMessage: String) {
        val current = _currentPreview.value ?: return
        savePreview(current.copy(
            hostingStatus = MediaHostingStatus.UPLOAD_UNAVAILABLE,
            errorMessage = errorMessage
        ))
    }

    /**
     * Called when media has successfully been uploaded to cloud storage.
     * Clears the local-only preview state.
     */
    fun markCloudHosted() {
        clearPersistedStorageOnly()
        _currentPreview.value = null
        _previewStatusMessage.value = null
    }

    fun dismissStatusMessage() {
        _previewStatusMessage.value = null
    }

    private fun clearPersistedStorageOnly() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "iombg_local_media_preview"
        const val INACCESSIBLE_URI_MESSAGE = "Local preview is no longer available. Please select the media again."

        private const val KEY_ID = "preview_id"
        private const val KEY_URI_STRING = "preview_uri_string"
        private const val KEY_MEDIA_NAME = "preview_media_name"
        private const val KEY_MEDIA_TYPE = "preview_media_type"
        private const val KEY_TITLE = "preview_title"
        private const val KEY_DESCRIPTION = "preview_description"
        private const val KEY_CATEGORY = "preview_category"
        private const val KEY_TAGS = "preview_tags"
        private const val KEY_THUMBNAIL = "preview_thumbnail"
        private const val KEY_SOUND_TITLE = "preview_sound_title"
        private const val KEY_VISIBILITY = "preview_visibility"
        private const val KEY_ALLOW_COMMENTS = "preview_allow_comments"
        private const val KEY_HOSTING_STATUS = "preview_hosting_status"
        private const val KEY_ERROR_MESSAGE = "preview_error_message"
        private const val KEY_CREATED_AT = "preview_created_at"
    }
}
