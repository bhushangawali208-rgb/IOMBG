package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.WatchHistoryItem
import com.example.data.remote.FirebaseService
import com.example.data.repository.WatchHistoryRepository
import com.example.di.AppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchHistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var appContainer: AppContainer
    private lateinit var repository: WatchHistoryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        appContainer = AppContainer(context)
        repository = appContainer.watchHistoryRepository
    }

    @Test
    fun testRecordWatch_savesLocallyAndUpdatesProgress() = runBlocking {
        val userId = "user_test_1"
        repository.onUserLogin(userId)

        repository.recordWatch(
            userId = userId,
            contentType = "VIDEO",
            contentId = "video_101",
            progressMs = 15000L,
            durationMs = 60000L,
            title = "Test Video 101",
            channelName = "Test Channel",
            thumbnailUrl = "https://example.com/thumb.jpg",
            videoUrl = "https://example.com/video.mp4"
        )

        // Verify progress is retrievable
        val savedProgress = repository.getProgress(userId, "VIDEO", "video_101")
        assertEquals(15000L, savedProgress)

        // Check history items
        val items = repository.historyItems.first()
        val item = items.find { it.contentId == "video_101" }
        assertNotNull("Recorded item must exist in history", item)
        assertEquals("VIDEO", item?.contentType)
        assertEquals("Test Video 101", item?.title)
        assertFalse("15s / 60s is not completed", item!!.completed)

        // Update progress near end (90%+)
        repository.recordWatch(
            userId = userId,
            contentType = "VIDEO",
            contentId = "video_101",
            progressMs = 56000L,
            durationMs = 60000L,
            title = "Test Video 101",
            channelName = "Test Channel"
        )

        val updatedProgress = repository.getProgress(userId, "VIDEO", "video_101")
        assertEquals(56000L, updatedProgress)

        val updatedItems = repository.historyItems.first()
        val updatedItem = updatedItems.find { it.contentId == "video_101" }
        assertTrue("56s / 60s (>90%) should be marked completed", updatedItem?.completed == true)
    }

    @Test
    fun testShortAndVideo_bothSupported() = runBlocking {
        val userId = "user_multi_type"
        repository.onUserLogin(userId)

        // Record video
        repository.recordWatch(
            userId = userId,
            contentType = "VIDEO",
            contentId = "vid_1",
            progressMs = 5000L,
            durationMs = 30000L,
            title = "Video Title",
            channelName = "Channel A"
        )

        // Record short
        repository.recordWatch(
            userId = userId,
            contentType = "SHORT",
            contentId = "short_1",
            progressMs = 8000L,
            durationMs = 15000L,
            title = "Short Title",
            channelName = "Channel B"
        )

        val items = repository.historyItems.first()
        assertEquals(2, items.size)
        assertTrue(items.any { it.contentType == "VIDEO" && it.contentId == "vid_1" })
        assertTrue(items.any { it.contentType == "SHORT" && it.contentId == "short_1" })
    }

    @Test
    fun testAccountSwitching_isolatesUserAFromUserB() = runBlocking {
        val userA = "user_alice"
        val userB = "user_bob"

        // User A records watch history
        repository.onUserLogin(userA)
        repository.recordWatch(
            userId = userA,
            contentType = "VIDEO",
            contentId = "alice_vid_1",
            progressMs = 10000L,
            durationMs = 40000L,
            title = "Alice Video 1",
            channelName = "Alice Channel"
        )

        val aliceItems = repository.historyItems.first()
        assertTrue(aliceItems.any { it.contentId == "alice_vid_1" })

        // User A logs out
        repository.onUserLogout()
        val loggedOutItems = repository.historyItems.first()
        assertTrue("In-memory history must be cleared on logout", loggedOutItems.isEmpty())

        // User B logs in
        repository.onUserLogin(userB)
        val bobInitialItems = repository.historyItems.first()
        assertFalse("User B must NOT see User A's watch history", bobInitialItems.any { it.contentId == "alice_vid_1" })

        // User B records their own history
        repository.recordWatch(
            userId = userB,
            contentType = "VIDEO",
            contentId = "bob_vid_1",
            progressMs = 20000L,
            durationMs = 50000L,
            title = "Bob Video 1",
            channelName = "Bob Channel"
        )

        val bobItems = repository.historyItems.first()
        assertEquals(1, bobItems.size)
        assertEquals("bob_vid_1", bobItems[0].contentId)
        assertEquals(userB, bobItems[0].userId)
    }

    @Test
    fun testDeleteSingleItem_removesFromLocal() = runBlocking {
        val userId = "user_delete_test"
        repository.onUserLogin(userId)

        repository.recordWatch(
            userId = userId,
            contentType = "VIDEO",
            contentId = "del_vid_1",
            progressMs = 5000L,
            durationMs = 20000L,
            title = "To Delete",
            channelName = "Channel"
        )
        repository.recordWatch(
            userId = userId,
            contentType = "VIDEO",
            contentId = "del_vid_2",
            progressMs = 10000L,
            durationMs = 30000L,
            title = "To Keep",
            channelName = "Channel"
        )

        val itemsBefore = repository.historyItems.first()
        assertEquals(2, itemsBefore.size)

        val itemToDelete = itemsBefore.first { it.contentId == "del_vid_1" }
        repository.deleteItem(userId, itemToDelete.historyId)

        val itemsAfter = repository.historyItems.first()
        assertEquals(1, itemsAfter.size)
        assertEquals("del_vid_2", itemsAfter[0].contentId)
    }

    @Test
    fun testClearHistory_removesAllItems() = runBlocking {
        val userId = "user_clear_test"
        repository.onUserLogin(userId)

        repository.recordWatch(userId, "VIDEO", "clear_1", 1000L, 5000L, "Vid 1", "Ch")
        repository.recordWatch(userId, "SHORT", "clear_2", 2000L, 8000L, "Short 2", "Ch")

        val itemsBefore = repository.historyItems.first()
        assertEquals(2, itemsBefore.size)

        repository.clearHistory(userId)

        val itemsAfter = repository.historyItems.first()
        assertTrue("History must be empty after clearHistory", itemsAfter.isEmpty())
    }
}
