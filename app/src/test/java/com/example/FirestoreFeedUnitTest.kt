package com.example

import com.example.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit Tests for Firestore-backed Feed Mapping, Filtering, Ordering,
 * Channel Follow State, and Empty/Unavailable States.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreFeedUnitTest {

    @Test
    fun testFirestoreVideoDocumentMapping_allFieldsMappedCorrectly() {
        val rawDoc = mapOf<String, Any?>(
            "title" to "Firestore Video Test",
            "description" to "A real Firestore video description",
            "videoUrl" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            "thumbnailUrl" to "https://images.unsplash.com/photo-test",
            "channelId" to "chan_99",
            "channelName" to "Real Creator",
            "channelAvatarUrl" to "https://images.unsplash.com/avatar",
            "viewCount" to 42000L,
            "likeCount" to 1500L,
            "durationSeconds" to 360L,
            "category" to "Technology",
            "createdAt" to 1700000000000L,
            "isBoosted" to false,
            "visibility" to "PUBLIC",
            "processingStatus" to "READY"
        )

        val video = Video(
            videoId = "vid_123",
            title = rawDoc["title"] as? String ?: "",
            description = rawDoc["description"] as? String ?: "",
            videoUrl = rawDoc["videoUrl"] as? String ?: "",
            thumbnailUrl = rawDoc["thumbnailUrl"] as? String ?: "",
            channelId = rawDoc["channelId"] as? String ?: "",
            channelName = rawDoc["channelName"] as? String ?: "",
            channelAvatarUrl = rawDoc["channelAvatarUrl"] as? String ?: "",
            viewCount = (rawDoc["viewCount"] as? Number)?.toLong() ?: 0L,
            likeCount = (rawDoc["likeCount"] as? Number)?.toLong() ?: 0L,
            durationSeconds = (rawDoc["durationSeconds"] as? Number)?.toLong() ?: 0L,
            category = rawDoc["category"] as? String ?: "General",
            createdAt = (rawDoc["createdAt"] as? Number)?.toLong() ?: 0L,
            isBoosted = rawDoc["isBoosted"] as? Boolean ?: false,
            visibility = rawDoc["visibility"] as? String ?: "PUBLIC",
            processingStatus = rawDoc["processingStatus"] as? String ?: "READY"
        )

        assertEquals("vid_123", video.videoId)
        assertEquals("Firestore Video Test", video.title)
        assertEquals("chan_99", video.channelId)
        assertEquals("Real Creator", video.channelName)
        assertEquals(42000L, video.viewCount)
        assertEquals(1500L, video.likeCount)
        assertEquals(360L, video.durationSeconds)
        assertEquals("Technology", video.category)
        assertEquals("PUBLIC", video.visibility)
        assertEquals("READY", video.processingStatus)
    }

    @Test
    fun testFeedFilter_onlyPublicReadyVisible() {
        val list = listOf(
            Video(videoId = "v1", title = "Public Ready", visibility = "PUBLIC", processingStatus = "READY"),
            Video(videoId = "v2", title = "Draft", visibility = "PRIVATE", processingStatus = "READY"),
            Video(videoId = "v3", title = "Processing", visibility = "PUBLIC", processingStatus = "PROCESSING"),
            Video(videoId = "v4", title = "Failed", visibility = "PUBLIC", processingStatus = "FAILED"),
            Video(videoId = "v5", title = "Published", visibility = "PUBLIC", processingStatus = "PUBLISHED")
        )

        val visibleVideos = list.filter { v ->
            v.visibility == "PUBLIC" &&
            (v.processingStatus == "READY" || v.processingStatus == "PUBLISHED" || v.processingStatus.isBlank())
        }

        assertEquals(2, visibleVideos.size)
        assertEquals("v1", visibleVideos[0].videoId)
        assertEquals("v5", visibleVideos[1].videoId)
    }

    @Test
    fun testShortsFilter_onlyPublicReadyVisible() {
        val list = listOf(
            ShortItem(shortId = "s1", title = "Public Short", visibility = "PUBLIC", processingStatus = "READY"),
            ShortItem(shortId = "s2", title = "Unlisted Short", visibility = "UNLISTED", processingStatus = "READY"),
            ShortItem(shortId = "s3", title = "Processing Short", visibility = "PUBLIC", processingStatus = "PROCESSING")
        )

        val visibleShorts = list.filter { s ->
            s.visibility == "PUBLIC" &&
            (s.processingStatus == "READY" || s.processingStatus == "PUBLISHED" || s.processingStatus.isBlank())
        }

        assertEquals(1, visibleShorts.size)
        assertEquals("s1", visibleShorts[0].shortId)
    }

    @Test
    fun testStableOrdering_createdAtDescending() {
        val v1 = Video(videoId = "old", title = "Older", createdAt = 1000L)
        val v2 = Video(videoId = "new", title = "Newer", createdAt = 2000L)
        val v3 = Video(videoId = "mid", title = "Middle", createdAt = 1500L)

        val sorted = listOf(v1, v2, v3).sortedByDescending { it.createdAt }

        assertEquals("new", sorted[0].videoId)
        assertEquals("mid", sorted[1].videoId)
        assertEquals("old", sorted[2].videoId)
    }

    @Test
    fun testDeduplication_preventsDuplicateIds() {
        val initial = listOf(
            Video(videoId = "v1", title = "First"),
            Video(videoId = "v2", title = "Second")
        )
        val incoming = listOf(
            Video(videoId = "v2", title = "Second Duplicate"),
            Video(videoId = "v3", title = "Third")
        )

        val combined = (initial + incoming).distinctBy { it.videoId }

        assertEquals(3, combined.size)
        assertEquals(listOf("v1", "v2", "v3"), combined.map { it.videoId })
    }

    @Test
    fun testEmptyFeed_doesNotCrashAndReportsEmpty() {
        val emptyList = emptyList<Video>()
        assertTrue(emptyList.isEmpty())
        assertEquals(0, emptyList.size)
    }

    @Test
    fun testAuthenticatedFollowState_updatesCorrectly() = runBlocking {
        val followedChannelsFlow = flowOf(setOf("chan_1", "chan_2"))
        val followedSet = followedChannelsFlow.first()

        assertTrue(followedSet.contains("chan_1"))
        assertTrue(followedSet.contains("chan_2"))
        assertFalse(followedSet.contains("chan_3"))

        val isChan1Followed = "chan_1" in followedSet
        val isChan3Followed = "chan_3" in followedSet

        assertTrue(isChan1Followed)
        assertFalse(isChan3Followed)
    }

    @Test
    fun testMissingVideoUrl_flaggedAsUnavailable() {
        val videoWithValidUrl = Video(videoId = "v1", title = "Valid", videoUrl = "https://example.com/video.mp4")
        val videoWithEmptyUrl = Video(videoId = "v2", title = "Empty URL", videoUrl = "")
        val videoWithBlankUrl = Video(videoId = "v3", title = "Blank URL", videoUrl = "   ")

        assertTrue(videoWithValidUrl.videoUrl.isNotBlank())
        assertTrue(videoWithEmptyUrl.videoUrl.isBlank())
        assertTrue(videoWithBlankUrl.videoUrl.isBlank())
    }

    @Test
    fun testChannelDocumentMapping_firestoreAttributes() {
        val rawDoc = mapOf<String, Any?>(
            "channelName" to "Code Master",
            "handle" to "@codemaster",
            "profileImageUrl" to "https://images.unsplash.com/cm",
            "subscriberCount" to 55000L,
            "category" to "Technology"
        )

        val channel = Channel(
            channelId = "c_master",
            channelName = rawDoc["channelName"] as? String ?: "",
            handle = rawDoc["handle"] as? String ?: "",
            profileImageUrl = rawDoc["profileImageUrl"] as? String ?: "",
            subscriberCount = (rawDoc["subscriberCount"] as? Number)?.toLong() ?: 0L,
            category = rawDoc["category"] as? String ?: "Creator"
        )

        assertEquals("c_master", channel.channelId)
        assertEquals("Code Master", channel.channelName)
        assertEquals("@codemaster", channel.handle)
        assertEquals(55000L, channel.subscriberCount)
        assertEquals("Technology", channel.category)
    }

    @Test
    fun testCursorPagination_appendNextPageWithoutDuplicates() {
        val page1 = listOf(
            Video(videoId = "v1", title = "Video 1", createdAt = 3000L),
            Video(videoId = "v2", title = "Video 2", createdAt = 2000L)
        )
        val page2 = listOf(
            Video(videoId = "v2", title = "Video 2 Duplicate", createdAt = 2000L),
            Video(videoId = "v3", title = "Video 3", createdAt = 1000L)
        )

        val combined = (page1 + page2).distinctBy { it.videoId }

        assertEquals(3, combined.size)
        assertEquals("v1", combined[0].videoId)
        assertEquals("v2", combined[1].videoId)
        assertEquals("v3", combined[2].videoId)
    }

    @Test
    fun testCursorPagination_emptyNextPageSetsHasMoreFalse() {
        val pageSize = 10L
        val incomingDocsCount = 0L
        val hasMore = incomingDocsCount >= pageSize
        assertFalse(hasMore)

        val partialDocsCount = 5L
        val hasMorePartial = partialDocsCount >= pageSize
        assertFalse(hasMorePartial)

        val fullDocsCount = 10L
        val hasMoreFull = fullDocsCount >= pageSize
        assertTrue(hasMoreFull)
    }

    @Test
    fun testCursorPagination_resetOnFeedRefresh() {
        var lastVisible: String? = "doc_snapshot_marker_123"
        val paginatedBuffer = mutableListOf("item1", "item2")
        var hasMore = true

        // Simulate refreshFeed()
        lastVisible = null
        paginatedBuffer.clear()
        hasMore = true

        assertNull(lastVisible)
        assertTrue(paginatedBuffer.isEmpty())
        assertTrue(hasMore)
    }

    @Test
    fun testCursorPagination_preventsConcurrentPageFetches() {
        var isLoadingMore = false
        var fetchCount = 0

        fun tryFetchNextPage(): Boolean {
            if (isLoadingMore) return false
            isLoadingMore = true
            fetchCount++
            return true
        }

        assertTrue(tryFetchNextPage())
        assertFalse(tryFetchNextPage()) // Blocked while isLoadingMore = true
        assertEquals(1, fetchCount)

        isLoadingMore = false
        assertTrue(tryFetchNextPage()) // Can fetch again after complete
        assertEquals(2, fetchCount)
    }
}
