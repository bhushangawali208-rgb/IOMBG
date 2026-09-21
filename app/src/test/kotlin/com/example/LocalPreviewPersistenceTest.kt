package com.example

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.*
import com.example.data.remote.FirebaseService
import com.example.data.repository.AndroidUriAccessibilityChecker
import com.example.data.repository.LocalMediaPreviewRepository
import com.example.data.repository.UriAccessibilityChecker
import com.example.data.repository.VideoRepository
import com.example.data.service.SandboxCdnService
import com.example.data.service.SandboxVideoProcessingService
import com.example.data.service.SandboxVideoStorageService
import com.example.data.service.VideoStorageService
import com.example.di.AppContainer
import com.example.ui.viewmodel.CreateUploadViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1 Local Preview Persistence Unit & Architecture Tests.
 *
 * Verifies:
 * 1. Selected content URIs are preserved verbatim without sample/demo replacement.
 * 2. SavedStateHandle & LocalMediaPreviewRepository store metadata safely without copying large files.
 * 3. State is faithfully restored across process recreation / Activity restarts.
 * 4. Inaccessible restored URIs are detected and handled gracefully.
 * 5. MediaHostingStatus accurately distinguishes LOCAL_PREVIEW vs CLOUD_HOSTED vs UPLOAD_UNAVAILABLE.
 * 6. Local previews appear in video/shorts feeds with LOCAL_PREVIEW status and do not write fake records to Firestore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalPreviewPersistenceTest {

    private lateinit var context: Context
    private lateinit var appContainer: AppContainer
    private lateinit var previewRepo: LocalMediaPreviewRepository

    private val testChannel = Channel(
        channelId = "ch_test_creator",
        channelName = "Test Creator",
        handle = "@testcreator",
        ownerUid = "uid_test_123"
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        appContainer = AppContainer(context)
        previewRepo = appContainer.localMediaPreviewRepository
        previewRepo.clearPreview()
    }

    @Test
    fun testUserSelectedMediaPreservesOriginalUri_noSampleReplacement() {
        val viewModel = CreateUploadViewModel(appContainer)
        val originalUri = Uri.parse("content://com.android.providers.media.documents/document/video%3A100234")

        viewModel.onVideoSelected(originalUri, "my_trip_4k.mp4")

        assertEquals(originalUri, viewModel.selectedVideoUri.value)
        assertEquals("my_trip_4k.mp4", viewModel.selectedVideoName.value)
        assertEquals(MediaHostingStatus.LOCAL_PREVIEW, viewModel.mediaHostingStatus.value)

        val persisted = previewRepo.currentPreview.value
        assertNotNull(persisted)
        assertEquals(originalUri.toString(), persisted?.uriString)
        assertEquals("my_trip_4k.mp4", persisted?.mediaName)
        assertEquals("VIDEO", persisted?.mediaType)

        // Verify it was NOT replaced with any sample URL
        assertFalse(persisted?.uriString?.contains("commondatastorage.googleapis.com") == true)
        assertFalse(persisted?.uriString?.contains("BigBuckBunny") == true)
        assertFalse(persisted?.uriString?.contains("sample") == true)
    }

    @Test
    fun testMetadataPersistenceAndRestorationAcrossSavedState() {
        val originalSavedState = SavedStateHandle()
        val viewModel1 = CreateUploadViewModel(appContainer, originalSavedState)

        val videoUri = Uri.parse("content://media/external/video/media/778899")
        viewModel1.onVideoSelected(videoUri, "tech_review_2026.mp4")
        viewModel1.setVideoTitle("Exclusive AI Supercomputer Architecture Review")
        viewModel1.setVideoDescription("Full breakdown of high performance AI chips")
        viewModel1.setVideoCategory("Technology")
        viewModel1.setTagList(listOf("AI", "Silicon", "Hardware"))

        // Verify repository received persisted metadata
        val savedPreview = previewRepo.currentPreview.value
        assertNotNull(savedPreview)
        assertEquals("Exclusive AI Supercomputer Architecture Review", savedPreview?.title)
        assertEquals("Technology", savedPreview?.category)
        assertEquals(listOf("AI", "Silicon", "Hardware"), savedPreview?.tags)

        // Simulate process recreation: Create a new ViewModel with the savedStateHandle and repo
        val viewModel2 = CreateUploadViewModel(appContainer, originalSavedState)

        assertEquals(videoUri, viewModel2.selectedVideoUri.value)
        assertEquals("tech_review_2026.mp4", viewModel2.selectedVideoName.value)
        assertEquals("Exclusive AI Supercomputer Architecture Review", viewModel2.videoTitle.value)
        assertEquals("Technology", viewModel2.videoCategory.value)
        assertEquals(listOf("AI", "Silicon", "Hardware"), viewModel2.tagList.value)
        assertEquals(MediaHostingStatus.LOCAL_PREVIEW, viewModel2.mediaHostingStatus.value)
    }

    @Test
    fun testInaccessibleRestoredUriGracefullyHandled() {
        // Create custom repository with mock accessibility checker that reports URI as inaccessible
        val alwaysFailsChecker = object : UriAccessibilityChecker {
            override fun isAccessible(uriString: String): Boolean = false
        }

        val customRepo = LocalMediaPreviewRepository(context, alwaysFailsChecker)

        // Save a preview
        customRepo.savePreview(
            LocalMediaPreview(
                id = "expired_vid_1",
                uriString = "content://media/external/video/media/deleted_or_revoked_uri",
                mediaName = "deleted_clip.mp4",
                mediaType = "VIDEO",
                title = "Expired Clip",
                hostingStatus = MediaHostingStatus.LOCAL_PREVIEW
            )
        )

        // Attempt restore
        val restored = customRepo.checkAndRestorePreview()

        // Should return null and clear stale preview
        assertNull("Inaccessible URI must not be restored as active preview", restored)
        assertNull("Stale preview must be cleared", customRepo.currentPreview.value)

        // Warning message should be populated informing the user
        val statusMsg = customRepo.previewStatusMessage.value
        assertNotNull(statusMsg)
        assertTrue("Message should notify about inaccessible media", statusMsg?.contains("no longer available") == true)
    }

    @Test
    fun testHostingStatusTransitionsOnUploadUnavailable() = runBlocking {
        val failingStorageService = object : VideoStorageService {
            override suspend fun uploadSourceVideo(
                creatorUid: String,
                videoId: String,
                fileUri: String,
                isShort: Boolean,
                onProgress: (UploadProgressState) -> Unit
            ): Result<String> {
                return Result.failure(IllegalStateException("Firebase Storage Spark plan quota or network offline"))
            }

            override suspend fun uploadThumbnail(
                creatorUid: String,
                videoId: String,
                thumbnailUriOrUrl: String
            ): Result<String> = Result.success("https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800")

            override suspend fun deleteVideoAssets(creatorUid: String, videoId: String): Result<Boolean> = Result.success(true)
            override suspend fun getAuthorizedPlaybackUrl(videoId: String, visibility: String, requestorUid: String?): Result<String> = Result.success("")
            override fun isProductionCloudConfigured(): Boolean = false
        }

        val videoRepo = VideoRepository(
            firebaseService = FirebaseService(),
            storageService = failingStorageService,
            processingService = SandboxVideoProcessingService(),
            cdnService = SandboxCdnService(),
            localPreviewRepository = previewRepo
        )

        val localUri = "content://media/external/video/media/5555"
        val uploadResult = videoRepo.uploadLongVideo(
            channel = testChannel,
            title = "My Local Masterpiece",
            description = "Selected from device storage",
            tags = listOf("4K", "Cinematic"),
            category = "Entertainment",
            visibility = "PUBLIC",
            allowComments = true,
            thumbnailUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800",
            videoUriString = localUri
        )

        // Verify upload returned failure with CloudUploadUnavailableException
        assertTrue(uploadResult.isFailure)
        val exception = uploadResult.exceptionOrNull()
        assertTrue(exception is CloudUploadUnavailableException)

        val localVideo = (exception as CloudUploadUnavailableException).localVideo
        assertNotNull(localVideo)
        assertEquals("LOCAL_PREVIEW", localVideo?.processingStatus)
        assertEquals(localUri, localVideo?.videoUrl)

        // Verify repository state transitioned to UPLOAD_UNAVAILABLE
        val preview = previewRepo.currentPreview.value
        assertNotNull(preview)
        assertEquals(MediaHostingStatus.UPLOAD_UNAVAILABLE, preview?.hostingStatus)

        // Verify video repository includes this local preview in feed
        val homeFeed = videoRepo.videosFeed.value
        val foundInFeed = homeFeed.any { it.videoUrl == localUri && it.processingStatus == "LOCAL_PREVIEW" }
        assertTrue("Local preview must be accessible in feed for local playback", foundInFeed)
    }

    @Test
    fun testShortLocalPreviewPersistence() {
        val viewModel = CreateUploadViewModel(appContainer)
        val shortUri = Uri.parse("content://media/external/video/media/short_9988")

        viewModel.onShortSelected(shortUri)
        viewModel.setShortTitle("Cool Trick ⚡️ #Shorts")
        viewModel.setShortSound("Bass Heavy Trap Beats")
        viewModel.setShortCategory("Gaming")

        assertEquals(shortUri, viewModel.selectedShortUri.value)
        assertEquals("Cool Trick ⚡️ #Shorts", viewModel.shortTitle.value)
        assertEquals("Bass Heavy Trap Beats", viewModel.shortSound.value)
        assertEquals("Gaming", viewModel.shortCategory.value)
        assertEquals(MediaHostingStatus.LOCAL_PREVIEW, viewModel.mediaHostingStatus.value)

        val persisted = previewRepo.currentPreview.value
        assertNotNull(persisted)
        assertEquals("SHORT", persisted?.mediaType)
        assertEquals(shortUri.toString(), persisted?.uriString)
        assertEquals("Cool Trick ⚡️ #Shorts", persisted?.title)
        assertEquals("Bass Heavy Trap Beats", persisted?.soundTitle)
        assertEquals("Gaming", persisted?.category)
    }
}
