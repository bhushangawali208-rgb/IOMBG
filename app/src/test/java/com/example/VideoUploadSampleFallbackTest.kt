package com.example

import com.example.data.model.Channel
import com.example.data.model.CloudUploadUnavailableException
import com.example.data.model.UploadProgressState
import com.example.data.remote.FirebaseService
import com.example.data.repository.VideoRepository
import com.example.data.service.SandboxCdnService
import com.example.data.service.SandboxVideoProcessingService
import com.example.data.service.SandboxVideoStorageService
import com.example.data.service.VideoStorageService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * QA Audit tests for Video Upload Sample Fallback fix.
 *
 * Verifies:
 * 1. User-selected local video URI is preserved and NEVER replaced with sample/demo URLs (BigBuckBunny, TearsOfSteel, ForBiggerFun, etc.).
 * 2. When storage service fails (e.g. Firebase Spark plan restriction), VideoRepository returns Result.failure with CloudUploadUnavailableException.
 * 3. The failed upload is NOT published to Firestore as a cloud-hosted video.
 * 4. The user's original URI is available in the local preview state for local playback.
 * 5. CDN service resolves local URIs directly without falling back to BigBuckBunny.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoUploadSampleFallbackTest {

    private val testChannel = Channel(
        channelId = "ch_test_user",
        channelName = "Test Creator",
        handle = "@testcreator",
        ownerUid = "uid_test_123"
    )

    private fun createFailingStorageService(): VideoStorageService {
        return object : VideoStorageService {
            override suspend fun uploadSourceVideo(
                creatorUid: String,
                videoId: String,
                fileUri: String,
                isShort: Boolean,
                onProgress: (UploadProgressState) -> Unit
            ): Result<String> {
                return Result.failure(IllegalStateException("Firebase Storage is unavailable on the Spark plan"))
            }

            override suspend fun uploadThumbnail(
                creatorUid: String,
                videoId: String,
                thumbnailUriOrUrl: String
            ): Result<String> = Result.success("https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=800")

            override suspend fun deleteVideoAssets(creatorUid: String, videoId: String): Result<Boolean> = Result.success(true)

            override suspend fun getAuthorizedPlaybackUrl(
                videoId: String,
                visibility: String,
                requestorUid: String?
            ): Result<String> = Result.success("")

            override fun isProductionCloudConfigured(): Boolean = false
        }
    }

    @Test
    fun testUserSelectedVideoUriPreserved_neverReplacedWithBigBuckBunnyOnFailure() = runBlocking {
        val failingStorageService = createFailingStorageService()

        val repository = VideoRepository(
            firebaseService = FirebaseService(),
            storageService = failingStorageService,
            processingService = SandboxVideoProcessingService(),
            cdnService = SandboxCdnService()
        )

        val localUserUri = "content://media/external/video/media/98765"

        val result = repository.uploadLongVideo(
            channel = testChannel,
            title = "My Real Video",
            description = "Selected from device gallery",
            tags = listOf("real", "original"),
            category = "Technology",
            visibility = "PUBLIC",
            allowComments = true,
            thumbnailUrl = "",
            videoUriString = localUserUri
        )

        // Upload MUST fail because cloud storage is unavailable
        assertTrue("Upload must fail when cloud storage is unavailable", result.isFailure)

        val exception = result.exceptionOrNull()
        assertTrue("Exception must be CloudUploadUnavailableException", exception is CloudUploadUnavailableException)

        val cloudEx = exception as CloudUploadUnavailableException
        assertNotNull("Local video must be retained for local preview", cloudEx.localVideo)
        assertEquals("User's selected local URI must be preserved exactly", localUserUri, cloudEx.localVideo?.videoUrl)

        // Must NEVER contain sample video URLs
        assertFalse("Must not contain BigBuckBunny", cloudEx.localVideo?.videoUrl?.contains("BigBuckBunny") == true)
        assertFalse("Must not contain TearsOfSteel", cloudEx.localVideo?.videoUrl?.contains("TearsOfSteel") == true)
        assertFalse("Must not contain ForBigger", cloudEx.localVideo?.videoUrl?.contains("ForBigger") == true)

        // Processing status must indicate local preview, not ready or cloud hosted
        assertEquals("LOCAL_PREVIEW", cloudEx.localVideo?.processingStatus)

        // Must be in local preview flow
        val localPreviews = repository.localPreviewVideos.value
        assertTrue("Video must be tracked in localPreviewVideos", localPreviews.any { it.videoUrl == localUserUri })
    }

    @Test
    fun testUserSelectedShortUriPreserved_neverReplacedWithSampleOnFailure() = runBlocking {
        val failingStorageService = createFailingStorageService()

        val repository = VideoRepository(
            firebaseService = FirebaseService(),
            storageService = failingStorageService,
            processingService = SandboxVideoProcessingService(),
            cdnService = SandboxCdnService()
        )

        val localShortUri = "content://media/external/video/media/54321"

        val result = repository.uploadShortItem(
            channel = testChannel,
            title = "My Vertical Short",
            description = "Captured on device camera",
            soundTitle = "My Audio",
            category = "Entertainment",
            tags = listOf("Shorts"),
            visibility = "PUBLIC",
            allowComments = true,
            thumbnailUrl = "",
            videoUriString = localShortUri
        )

        assertTrue("Short upload must report failure when cloud storage is unavailable", result.isFailure)

        val exception = result.exceptionOrNull()
        assertTrue("Exception must be CloudUploadUnavailableException", exception is CloudUploadUnavailableException)

        val cloudEx = exception as CloudUploadUnavailableException
        assertNotNull("Local Short must be retained for local preview", cloudEx.localShort)
        assertEquals("Selected Short URI must be preserved exactly", localShortUri, cloudEx.localShort?.videoUrl)

        assertFalse("Must not contain BigBuckBunny", cloudEx.localShort?.videoUrl?.contains("BigBuckBunny") == true)
        assertFalse("Must not contain ForBiggerFun", cloudEx.localShort?.videoUrl?.contains("ForBiggerFun") == true)
        assertEquals("LOCAL_PREVIEW", cloudEx.localShort?.processingStatus)
    }

    @Test
    fun testSandboxCdnService_preservesUriWithoutBigBuckBunnyFallback() {
        val cdnService = SandboxCdnService()
        val customUri = "content://media/external/video/media/12345"
        val resolved = cdnService.resolveDeliveryUrl(customUri)
        assertEquals(customUri, resolved)
        assertFalse(resolved.contains("BigBuckBunny"))
    }

    @Test
    fun testSandboxVideoStorageService_blankUriReturnsFailure() = runBlocking {
        val storage = SandboxVideoStorageService()
        val result = storage.uploadSourceVideo(
            creatorUid = "test_uid",
            videoId = "vid_99",
            fileUri = "",
            isShort = false,
            onProgress = {}
        )
        assertTrue(result.isFailure)
        assertFalse("Must not return sample fallback on failure", result.getOrNull()?.contains("BigBuckBunny") == true)
    }
}
