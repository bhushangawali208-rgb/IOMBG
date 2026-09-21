package com.example.data.model

/**
 * Infrastructure & Media Pipeline Data Models for IOMBG.
 *
 * Provides typed contracts for Video Storage, Video Processing, Adaptive Streaming,
 * CDN Delivery, and Live Broadcasting.
 */

// ======================== VIDEO UPLOAD & PROCESSING STATES ========================

enum class VideoUploadState {
    IDLE,
    SELECTING,
    PREPARING,
    UPLOADING,
    PROCESSING,
    READY,
    FAILED,
    CANCELLED
}

data class UploadProgressState(
    val state: VideoUploadState = VideoUploadState.IDLE,
    val progressPercentage: Int = 0,
    val bytesUploaded: Long = 0,
    val totalBytes: Long = 0,
    val currentStageDescription: String = "",
    val estimatedTimeRemainingSeconds: Long = 0,
    val errorMessage: String? = null,
    val canRetry: Boolean = false
)

// ======================== VIDEO QUALITY & STREAMING ========================

enum class VideoQualityLevel(val displayName: String, val resolution: String, val targetBitrateKbps: Int) {
    AUTO("Auto (Adaptive)", "Dynamic", 0),
    P360("360p", "640x360", 800),
    P480("480p SD", "854x480", 1400),
    P720("720p HD", "1280x720", 2800),
    P1080("1080p Full HD", "1920x1080", 5500),
    P2160_4K("4K UHD HDR", "3840x2160", 16000)
}

data class VideoQualityOption(
    val level: VideoQualityLevel,
    val label: String,
    val resolution: String,
    val bitrateKbps: Int,
    val isSupportedBySource: Boolean = true,
    val manifestUrl: String = ""
)

data class VideoTechnicalMetadata(
    val codec: String = "H.264 / AAC",
    val containerFormat: String = "MP4",
    val resolution: String = "1920x1080",
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 60,
    val bitrateKbps: Int = 5500,
    val audioSampleRateHz: Int = 48000,
    val audioChannels: Int = 2,
    val fileSizeMb: Double = 45.2,
    val durationSeconds: Long = 420,
    val aspectRatio: String = "16:9",
    val isHdr: Boolean = false,
    val colorSpace: String = "BT.709"
)

data class MediaValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val detectedDuration: Long = 0,
    val detectedResolution: String = "",
    val detectedFileSizeMb: Double = 0.0,
    val isAspectValidForShort: Boolean = true
)

// ======================== TRANSCODING PIPELINE ========================

data class TranscodingJobRequest(
    val jobId: String,
    val videoId: String,
    val creatorId: String,
    val channelId: String,
    val sourceStorageUrl: String,
    val isShort: Boolean = false,
    val targetQualities: List<VideoQualityLevel> = listOf(
        VideoQualityLevel.P360,
        VideoQualityLevel.P480,
        VideoQualityLevel.P720,
        VideoQualityLevel.P1080
    ),
    val generateThumbnailCount: Int = 3
)

data class TranscodingJobResponse(
    val jobId: String,
    val videoId: String,
    val status: String = "QUEUED", // QUEUED, PROCESSING, COMPLETED, FAILED
    val estimatedDurationSeconds: Int = 30,
    val hlsMasterManifestUrl: String? = null,
    val isSandbox: Boolean = false
)

data class TranscodingStatusInfo(
    val jobId: String,
    val videoId: String,
    val status: String = "PROCESSING",
    val percentComplete: Int = 0,
    val completedQualities: List<VideoQualityLevel> = emptyList(),
    val thumbnailUrls: List<String> = emptyList(),
    val hlsMasterManifestUrl: String = "",
    val error: String? = null
)

data class HlsManifestInfo(
    val masterManifestUrl: String = "",
    val variantPlaylists: Map<VideoQualityLevel, String> = emptyMap(),
    val audioTracks: List<String> = listOf("default_en")
)

// ======================== CDN DELIVERY ========================

data class CdnEdgeMetrics(
    val edgeNodeId: String = "edge_asia_south_bom_01",
    val region: String = "Mumbai, IN (GCP Cloud CDN)",
    val latencyMs: Long = 18,
    val cacheHitRatioPercent: Float = 94.6f,
    val bandwidthMbps: Float = 48.2f,
    val protocol: String = "HTTP/3 QUIC + TLS 1.3",
    val isSimulated: Boolean = true
)

// ======================== LIVE STREAMING & SECURE CREDENTIALS ========================

enum class LiveStreamLifecycleState {
    CREATED,
    SCHEDULED,
    STARTING,
    LIVE,
    ENDING,
    ENDED,
    FAILED,
    CANCELLED
}

/**
 * Secure Stream Ingest Credentials.
 * CRITICAL SECURITY:
 * NEVER serialize or persist streamKey in public Firestore documents or public logs.
 * Handled strictly in-memory during active authenticated broadcast sessions.
 */
data class SecureStreamCredentials(
    val streamId: String,
    val creatorUid: String,
    val channelId: String,
    val rtmpIngestUrl: String = "Live ingest server not configured",
    val srtIngestUrl: String = "Live ingest server not configured",
    val streamKey: String, // Ephemeral cryptographic key
    val playbackHlsUrl: String = "",
    val expiresAt: Long = System.currentTimeMillis() + 86400000,
    val isSandbox: Boolean = true
)

data class LiveRecordingInfo(
    val recordingId: String = "",
    val streamId: String = "",
    val channelId: String = "",
    val creatorUid: String = "",
    val status: String = "COMPLETED", // RECORDING, PROCESSING, COMPLETED, FAILED
    val rawChunkStoragePath: String = "",
    val replayVideoId: String = "",
    val replayHlsUrl: String = "",
    val durationSeconds: Long = 0,
    val fileSizeMb: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

// ======================== PLAYER STATE ========================

data class PlayerPlaybackState(
    val isPlaying: Boolean = true,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val selectedQuality: VideoQualityLevel = VideoQualityLevel.AUTO,
    val effectiveQuality: VideoQualityLevel = VideoQualityLevel.P1080,
    val availableQualities: List<VideoQualityOption> = emptyList(),
    val bufferPercentage: Int = 65,
    val playbackSpeed: Float = 1.0f,
    val isMuted: Boolean = false,
    val volume: Float = 1.0f,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val isLiveStream: Boolean = false
)

// ======================== MEDIA HOSTING STATUS & LOCAL PREVIEW ========================

enum class MediaHostingStatus {
    NONE,
    LOCAL_PREVIEW,
    UPLOADING,
    CLOUD_HOSTED,
    UPLOAD_UNAVAILABLE
}

data class LocalMediaPreview(
    val id: String = "",
    val uriString: String = "",
    val mediaName: String = "",
    val mediaType: String = "VIDEO", // "VIDEO" or "SHORT"
    val title: String = "",
    val description: String = "",
    val category: String = "Technology",
    val tags: List<String> = emptyList(),
    val thumbnailUriOrUrl: String = "",
    val soundTitle: String = "Original Sound",
    val visibility: String = "PUBLIC",
    val allowComments: Boolean = true,
    val hostingStatus: MediaHostingStatus = MediaHostingStatus.LOCAL_PREVIEW,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

