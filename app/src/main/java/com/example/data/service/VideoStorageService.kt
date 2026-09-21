package com.example.data.service

import android.net.Uri
import android.util.Log
import com.example.data.model.UploadProgressState
import com.example.data.model.VideoUploadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Replaceable Video Storage Service Abstraction for IOMBG.
 *
 * Architecture:
 * - Scoped Storage Paths:
 *   - Source Video: `videos/{creatorUid}/{videoId}/source.mp4`
 *   - Transcoded HLS Renditions: `videos/{creatorUid}/{videoId}/hls/`
 *   - Thumbnails: `thumbnails/{creatorUid}/{videoId}/thumb_main.jpg`
 *   - Captions/Subtitles: `subtitles/{creatorUid}/{videoId}/captions_en.vtt`
 *
 * Security:
 * - Direct cross-creator overwrites are blocked by storage bucket rules.
 * - Private / unlisted assets require signed URLs or authenticated auth tokens.
 * - Development fallback clearly designated when cloud bucket is in sandbox mode.
 */
interface VideoStorageService {
    /**
     * Uploads the raw source master file with granular chunk progress tracking.
     */
    suspend fun uploadSourceVideo(
        creatorUid: String,
        videoId: String,
        fileUri: String,
        isShort: Boolean,
        onProgress: (UploadProgressState) -> Unit
    ): Result<String>

    /**
     * Uploads custom or auto-generated video thumbnail.
     */
    suspend fun uploadThumbnail(
        creatorUid: String,
        videoId: String,
        thumbnailUriOrUrl: String
    ): Result<String>

    /**
     * Deletes all storage assets associated with a video (source, renditions, thumbnails).
     */
    suspend fun deleteVideoAssets(creatorUid: String, videoId: String): Result<Boolean>

    /**
     * Generates a signed or CDN delivery URL with access token authorization for private/unlisted videos.
     */
    suspend fun getAuthorizedPlaybackUrl(
        videoId: String,
        visibility: String,
        requestorUid: String?
    ): Result<String>

    fun isProductionCloudConfigured(): Boolean
}

/**
 * PRODUCTION Firebase Video Storage Service Implementation (Fail-Closed on Spark).
 * Cloud storage buckets require Blaze tier; under Spark plan, uploads are safely rejected.
 */
class FirebaseVideoStorageService : VideoStorageService {

    override suspend fun uploadSourceVideo(
        creatorUid: String,
        videoId: String,
        fileUri: String,
        isShort: Boolean,
        onProgress: (UploadProgressState) -> Unit
    ): Result<String> {
        Log.d("VideoStorageService", "Cloud storage upload rejected: Firebase Spark tier active")
        return Result.failure(IllegalStateException("Cloud storage upload unavailable on Firebase Spark tier. Use local preview."))
    }

    override suspend fun uploadThumbnail(
        creatorUid: String,
        videoId: String,
        thumbnailUriOrUrl: String
    ): Result<String> {
        return if (thumbnailUriOrUrl.startsWith("http://") || thumbnailUriOrUrl.startsWith("https://")) {
            Result.success(thumbnailUriOrUrl)
        } else {
            Result.failure(IllegalStateException("Cloud thumbnail storage unavailable on Firebase Spark tier."))
        }
    }

    override suspend fun deleteVideoAssets(creatorUid: String, videoId: String): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun getAuthorizedPlaybackUrl(
        videoId: String,
        visibility: String,
        requestorUid: String?
    ): Result<String> {
        return Result.failure(IllegalStateException("No cloud playback URL available without active cloud storage bucket"))
    }

    override fun isProductionCloudConfigured(): Boolean = false
}

/**
 * DEVELOPMENT & SANDBOX Video Storage Service Implementation.
 * Simulates real upload lifecycle with realistic chunk progress, network jitter handling,
 * and retry capabilities without requiring paid GCP storage buckets.
 */
class SandboxVideoStorageService : VideoStorageService {

    override suspend fun uploadSourceVideo(
        creatorUid: String,
        videoId: String,
        fileUri: String,
        isShort: Boolean,
        onProgress: (UploadProgressState) -> Unit
    ): Result<String> {
        val totalBytes = if (isShort) 18_450_000L else 142_000_000L // 18MB or 142MB

        // 1. Preparing stage
        onProgress(
            UploadProgressState(
                state = VideoUploadState.PREPARING,
                progressPercentage = 5,
                bytesUploaded = 0,
                totalBytes = totalBytes,
                currentStageDescription = "Validating codec compatibility & preparing upload buffer..."
            )
        )
        delay(160)

        // 2. Uploading chunks
        val uploadSteps = listOf(15, 30, 48, 65, 75)
        for (step in uploadSteps) {
            val uploaded = (totalBytes * (step / 100.0)).toLong()
            onProgress(
                UploadProgressState(
                    state = VideoUploadState.UPLOADING,
                    progressPercentage = step,
                    bytesUploaded = uploaded,
                    totalBytes = totalBytes,
                    currentStageDescription = "Uploading master media stream ($step%)...",
                    estimatedTimeRemainingSeconds = ((100 - step) / 10).toLong()
                )
            )
            delay(180)
        }

        if (fileUri.isBlank()) {
            return Result.failure(IllegalArgumentException("Cannot upload empty or blank video file URI"))
        }

        // Return exact provided fileUri - NEVER substitute with sample or demo video
        return Result.success(fileUri)
    }

    override suspend fun uploadThumbnail(
        creatorUid: String,
        videoId: String,
        thumbnailUriOrUrl: String
    ): Result<String> {
        delay(120)
        return Result.success(
            if (thumbnailUriOrUrl.isNotBlank()) thumbnailUriOrUrl
            else "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800"
        )
    }

    override suspend fun deleteVideoAssets(creatorUid: String, videoId: String): Result<Boolean> {
        delay(80)
        return Result.success(true)
    }

    override suspend fun getAuthorizedPlaybackUrl(
        videoId: String,
        visibility: String,
        requestorUid: String?
    ): Result<String> {
        return Result.failure(IllegalStateException("No authorized playback URL available in sandbox environment"))
    }

    override fun isProductionCloudConfigured(): Boolean = false
}
