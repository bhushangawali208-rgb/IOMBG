package com.example

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.model.Channel
import com.example.data.remote.FirebaseService
import com.example.data.repository.VideoRepository
import com.example.data.service.SandboxVideoStorageService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit & Integration tests for Media Permissions and Photo Picker implementation.
 *
 * Verifies:
 * 1. AndroidManifest does NOT request broad READ_MEDIA_VIDEO or READ_MEDIA_IMAGES or storage permissions.
 * 2. ActivityResultContracts.PickVisualMedia launches correctly for Video and Image selection.
 * 3. Selected URI is received properly from the picker contract.
 * 4. Real local URIs are preserved in upload pipeline without substitution by sample videos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaPermissionsAndPickerTest {

    @Test
    fun testNoBroadMediaPermissionsDeclaredInManifest() {
        val context = RuntimeEnvironment.getApplication()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: emptyList()

        val forbiddenPermissions = listOf(
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
        )

        for (forbidden in forbiddenPermissions) {
            assertFalse(
                "Broad media permission '$forbidden' must NOT be declared or requested in manifest",
                requestedPermissions.contains(forbidden)
            )
        }
    }

    @Test
    fun testPickVisualMedia_videoPickerLaunchesWithVideoMimeType() {
        val context = RuntimeEnvironment.getApplication()
        val contract = ActivityResultContracts.PickVisualMedia()
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)

        val intent = contract.createIntent(context, request)
        assertNotNull("Picker intent must not be null", intent)
        assertEquals("Intent type must be video/*", "video/*", intent.type)
        assertTrue(
            "Action must be ACTION_PICK_IMAGES or fallback ACTION_OPEN_DOCUMENT",
            intent.action == "android.provider.action.PICK_IMAGES" || intent.action == Intent.ACTION_OPEN_DOCUMENT
        )
    }

    @Test
    fun testPickVisualMedia_imagePickerLaunchesWithImageMimeType() {
        val context = RuntimeEnvironment.getApplication()
        val contract = ActivityResultContracts.PickVisualMedia()
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

        val intent = contract.createIntent(context, request)
        assertNotNull("Picker intent must not be null", intent)
        assertEquals("Intent type must be image/*", "image/*", intent.type)
        assertTrue(
            "Action must be ACTION_PICK_IMAGES or fallback ACTION_OPEN_DOCUMENT",
            intent.action == "android.provider.action.PICK_IMAGES" || intent.action == Intent.ACTION_OPEN_DOCUMENT
        )
    }

    @Test
    fun testPickVisualMedia_selectedUriIsReceived() {
        val contract = ActivityResultContracts.PickVisualMedia()
        val expectedUri = Uri.parse("content://media/picker/videos/12345")

        val resultIntent = Intent().apply {
            data = expectedUri
        }

        val parsed = contract.parseResult(Activity.RESULT_OK, resultIntent)
        assertEquals("Parsed URI must match selected intent data URI", expectedUri, parsed)
    }

    @Test
    fun testRealLocalVideoUri_isPreservedWithoutSubstitution() = runBlocking {
        val storageService = SandboxVideoStorageService()
        val localVideoUri = "content://media/external/video/media/778899"

        val result = storageService.uploadSourceVideo(
            creatorUid = "creator_real_user",
            videoId = "vid_real_test",
            fileUri = localVideoUri,
            isShort = false,
            onProgress = {}
        )

        assertTrue(result.isSuccess)
        val finalUrl = result.getOrNull()
        assertEquals(
            "Upload pipeline must NOT substitute real local video with sample video",
            localVideoUri,
            finalUrl
        )
    }

    @Test
    fun testRealLocalShortUri_isPreservedWithoutSubstitution() = runBlocking {
        val storageService = SandboxVideoStorageService()
        val localShortUri = "content://media/external/video/media/short_112233"

        val result = storageService.uploadSourceVideo(
            creatorUid = "creator_real_user",
            videoId = "short_real_test",
            fileUri = localShortUri,
            isShort = true,
            onProgress = {}
        )

        assertTrue(result.isSuccess)
        val finalUrl = result.getOrNull()
        assertEquals(
            "Short upload pipeline must NOT substitute real local short with sample video",
            localShortUri,
            finalUrl
        )
    }
}
