package com.example

import com.example.data.model.*
import com.example.data.remote.FirebaseService
import com.example.data.repository.LiveStreamRepository
import com.example.data.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P0 Production Cleanup Tests for Live Streaming.
 *
 * Verifies:
 * 1. LiveStreamRepository does NOT inject startup mock streams or replays.
 * 2. LiveStreamingService (both Sandbox and Production) does NOT contain sample video URLs (BigBuckBunny, TearsOfSteel).
 * 3. LiveRecordingService does NOT inject fallback sample video URLs.
 * 4. New live stream rooms start with status AWAITING_BROADCAST and empty streamUrl.
 * 5. Stream credentials have empty playbackHlsUrl when no ingest is configured.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveStreamNoSampleFallbackTest {

    private val testChannel = Channel(
        channelId = "ch_test_live",
        channelName = "Live Creator",
        handle = "@livecreator",
        ownerUid = "uid_live_123"
    )

    @Test
    fun testLiveStreamRepository_noInitialMockData() {
        val repo = LiveStreamRepository(
            streamingService = SandboxLiveStreamingService(),
            recordingService = SandboxLiveRecordingService(),
            cdnService = SandboxCdnService(),
            firebaseService = FirebaseService(),
            recommendationEventRepository = null
        )

        // Must start with no mock streams or replays
        assertTrue("Active streams must not contain mock data", repo.activeStreams.value.isEmpty())
        assertTrue("Replays must not contain mock data", repo.replays.value.isEmpty())
    }

    @Test
    fun testSandboxLiveStreamingService_credentialsHaveNoSampleUrls() = runBlocking {
        val service = SandboxLiveStreamingService()
        val credsResult = service.generateStreamCredentials("ch_test", "uid_test", "stream_001")
        assertTrue(credsResult.isSuccess)
        val creds = credsResult.getOrThrow()

        assertTrue("Playback HLS URL must be empty when no live ingest server is configured", creds.playbackHlsUrl.isEmpty())
        assertFalse("Must not contain BigBuckBunny", creds.playbackHlsUrl.contains("BigBuckBunny"))
        assertFalse("Must not contain TearsOfSteel", creds.playbackHlsUrl.contains("TearsOfSteel"))
    }

    @Test
    fun testProductionLiveStreamingService_credentialsHaveNoSampleUrls() = runBlocking {
        val service = ProductionLiveStreamingService()
        val credsResult = service.generateStreamCredentials("ch_test", "uid_test", "stream_002")
        assertTrue(credsResult.isSuccess)
        val creds = credsResult.getOrThrow()

        assertTrue("Playback HLS URL must be empty until live ingest server is configured", creds.playbackHlsUrl.isEmpty())
        assertFalse("Must not contain BigBuckBunny", creds.playbackHlsUrl.contains("BigBuckBunny"))
        assertFalse("Must not contain TearsOfSteel", creds.playbackHlsUrl.contains("TearsOfSteel"))
    }

    @Test
    fun testStartLiveStream_setsAwaitingBroadcastAndEmptyUrl() = runBlocking {
        val repo = LiveStreamRepository(
            streamingService = SandboxLiveStreamingService(),
            recordingService = SandboxLiveRecordingService(),
            cdnService = SandboxCdnService(),
            firebaseService = FirebaseService(),
            recommendationEventRepository = null
        )

        val stream = repo.startLiveStream(
            channel = testChannel,
            title = "Testing Live Ingest Offline",
            category = "Technology"
        )

        assertEquals("AWAITING_BROADCAST", stream.status)
        assertTrue("Stream URL must be empty until ingest is active", stream.streamUrl.isEmpty())
        assertFalse("Stream URL must not contain BigBuckBunny", stream.streamUrl.contains("BigBuckBunny"))
        assertFalse("Stream URL must not contain TearsOfSteel", stream.streamUrl.contains("TearsOfSteel"))
        assertEquals(0L, stream.viewerCount)
    }

    @Test
    fun testRecordingService_noSampleVideoFallbacks() = runBlocking {
        val sandboxRecording = SandboxLiveRecordingService()
        val result = sandboxRecording.finalizeRecordingSession("rec_dummy", 100)
        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertFalse("Replay URL must not contain TearsOfSteel", info.replayHlsUrl.contains("TearsOfSteel"))
        assertFalse("Replay URL must not contain BigBuckBunny", info.replayHlsUrl.contains("BigBuckBunny"))

        val replayResult = sandboxRecording.processReplayToVod(
            recordingInfo = info,
            channel = testChannel,
            title = "Test Replay",
            description = "Description",
            category = "Technology",
            tags = listOf("Live"),
            thumbnailUrl = "https://example.com/thumb.jpg"
        )
        assertTrue(replayResult.isSuccess)
        val replay = replayResult.getOrThrow()
        assertFalse("Video URL must not contain TearsOfSteel", replay.videoUrl.contains("TearsOfSteel"))
        assertFalse("Video URL must not contain BigBuckBunny", replay.videoUrl.contains("BigBuckBunny"))
    }
}
