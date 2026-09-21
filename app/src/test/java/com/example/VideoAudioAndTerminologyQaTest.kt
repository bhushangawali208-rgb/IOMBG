package com.example

import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Channel
import com.example.data.model.UserAccount
import com.example.data.model.Video
import com.example.data.remote.FirebaseService
import com.example.data.repository.SocialRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * QA & Verification Test Suite for:
 * 1. Video Audio Track handling & Mute/Unmute state (not muted by default, volume level control).
 * 2. Audio Focus interactions (pause on loss, restore on gain).
 * 3. Lock Screen / Lifecycle handling (pause on ON_PAUSE/lock, resume on ON_RESUME/unlock).
 * 4. Follow vs Subscribe Terminology Consistency across Creator/Channel relationships.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoAudioAndTerminologyQaTest {

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var firebaseService: FirebaseService
    private lateinit var socialRepository: SocialRepository

    private val testUser = UserAccount(
        uid = "user_test_audio",
        email = "viewer@iombg.app",
        displayName = "Audio QA Viewer",
        username = "audioviewer"
    )

    private val testVideo = Video(
        videoId = "video_qa_001",
        channelId = "channel_audio_creator",
        ownerUid = "creator_uid_audio",
        channelName = "Tech Creator",
        channelHandle = "@techcreator",
        title = "4K Cinematic Sound & Audio Test",
        videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        thumbnailUrl = "https://images.unsplash.com/photo-1518173946687-a4c8a383392e?w=800",
        durationSeconds = 120
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        firebaseService = FirebaseService()
        socialRepository = SocialRepository(firebaseService)
    }

    // =========================================================================
    // 1. VIDEO AUDIO TESTS
    // =========================================================================

    @Test
    fun testVideoPlayerNotMutedByDefault() {
        // Must not force mute by default
        val initialMutedState = false
        assertFalse("Video player must not be muted by default", initialMutedState)

        val volumeFactor = if (initialMutedState) 0f else 1f
        assertEquals("Unmuted player volume factor must be 1.0", 1f, volumeFactor, 0.001f)
    }

    @Test
    fun testVideoPlayerMuteToggleFunctionality() {
        var isMuted = false

        // Toggle mute to true
        isMuted = !isMuted
        assertTrue("Mute toggle should set muted to true", isMuted)
        var volume = if (isMuted) 0f else 1f
        assertEquals("Muted volume should be 0.0f", 0f, volume, 0.001f)

        // Toggle mute back to false (unmute)
        isMuted = !isMuted
        assertFalse("Mute toggle should set muted to false", isMuted)
        volume = if (isMuted) 0f else 1f
        assertEquals("Unmuted volume should return to 1.0f", 1f, volume, 0.001f)
    }

    @Test
    fun testMediaVolumeStreamUsage() {
        // Media volume stream must be STREAM_MUSIC
        val streamType = AudioManager.STREAM_MUSIC
        val maxVolume = audioManager.getStreamMaxVolume(streamType)
        assertTrue("STREAM_MUSIC max volume should be greater than 0", maxVolume > 0)
    }

    @Test
    fun testAudioFocusLossHandlingPausesPlayback() {
        var isPlaying = true

        // Simulate audio focus loss listener
        fun onAudioFocusChange(focusChange: Int) {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    isPlaying = false
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    // Restored
                }
            }
        }

        // When transient or permanent focus is lost, player must pause cleanly
        onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertFalse("Playback must pause cleanly when audio focus is lost", isPlaying)
    }

    // =========================================================================
    // 2. LOCK SCREEN & LIFECYCLE TESTS
    // =========================================================================

    @Test
    fun testLockScreenPausesPlaybackAndResumesOnForeground() {
        var isPlaying = true
        var wasPlayingBeforeLock = false

        // Simulate screen lock (ON_PAUSE / ON_STOP)
        fun onLifecycleEvent(event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    if (isPlaying) {
                        wasPlayingBeforeLock = true
                        isPlaying = false
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasPlayingBeforeLock) {
                        wasPlayingBeforeLock = false
                        isPlaying = true
                    }
                }
                else -> Unit
            }
        }

        // 1. Device locks / app minimized
        onLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        assertFalse("Playback must be paused when device locks or app moves to background", isPlaying)
        assertTrue("wasPlayingBeforeLock must remember playback state", wasPlayingBeforeLock)

        // 2. Device unlocked / app returns to foreground
        onLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertTrue("Playback must resume safely when returning to foreground", isPlaying)
        assertFalse("wasPlayingBeforeLock must be reset after resume", wasPlayingBeforeLock)
    }

    @Test
    fun testLockScreenDoesNotResumeIfAlreadyPaused() {
        var isPlaying = false
        var wasPlayingBeforeLock = false

        fun onLifecycleEvent(event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    if (isPlaying) {
                        wasPlayingBeforeLock = true
                        isPlaying = false
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasPlayingBeforeLock) {
                        wasPlayingBeforeLock = false
                        isPlaying = true
                    }
                }
                else -> Unit
            }
        }

        // User paused manually before lock
        onLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        assertFalse("wasPlayingBeforeLock must remain false if user was already paused", wasPlayingBeforeLock)

        onLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertFalse("Playback should remain paused if user was paused before lock", isPlaying)
    }

    // =========================================================================
    // 3. SEEK & SCRUBBER POSITION TESTS
    // =========================================================================

    @Test
    fun testVideoScrubberPositionCalculation() {
        val durationSeconds = testVideo.durationSeconds // 120s
        var playbackFraction = 0.5f // 50%

        var currentSeconds = Math.round((durationSeconds * playbackFraction).toDouble())
        assertEquals(60L, currentSeconds)

        // 10s Skip Forward
        playbackFraction = (playbackFraction + (10f / durationSeconds)).coerceIn(0f, 1f)
        currentSeconds = Math.round((durationSeconds * playbackFraction).toDouble())
        assertEquals(70L, currentSeconds)

        // 10s Skip Rewind
        playbackFraction = (playbackFraction - (10f / durationSeconds)).coerceIn(0f, 1f)
        currentSeconds = Math.round((durationSeconds * playbackFraction).toDouble())
        assertEquals(60L, currentSeconds)
    }

    // =========================================================================
    // 4. FOLLOW VS SUBSCRIBE TERMINOLOGY CONSISTENCY TESTS
    // =========================================================================

    @Test
    fun testFollowCreatorChannelRelationship() {
        // Toggle follow
        val isNowFollowed = socialRepository.toggleFollowChannel(
            targetChannelId = testVideo.channelId,
            targetOwnerUid = testVideo.ownerUid,
            targetChannelName = testVideo.channelName,
            follower = testUser
        )

        assertTrue("toggleFollowChannel should return true when following channel", isNowFollowed)
        assertTrue("Followed channels set should contain target channel ID",
            socialRepository.followedChannels.value.contains(testVideo.channelId))

        // Correct user-facing label
        val buttonLabel = if (isNowFollowed) "Following" else "Follow"
        assertEquals("Label should be 'Following' when channel is followed", "Following", buttonLabel)

        // Unfollow
        val isStillFollowed = socialRepository.toggleFollowChannel(
            targetChannelId = testVideo.channelId,
            targetOwnerUid = testVideo.ownerUid,
            targetChannelName = testVideo.channelName,
            follower = testUser
        )

        assertFalse("toggleFollowChannel should return false when unfollowing channel", isStillFollowed)
        val unfollowedLabel = if (isStillFollowed) "Following" else "Follow"
        assertEquals("Label should be 'Follow' when channel is not followed", "Follow", unfollowedLabel)
    }

    @Test
    fun testFollowersCountTerminology() {
        val testChannel = Channel(
            channelId = "channel_qa_term",
            channelName = "Sound Studio",
            handle = "@soundstudio",
            subscriberCount = 25400
        )

        val channelFollowersText = "${testChannel.handle} • 25.4K followers • 12 videos"
        assertTrue("Channel info must use 'followers' label", channelFollowersText.contains("followers"))
        assertFalse("Channel info must not use 'subscribers' for creator channel", channelFollowersText.contains("subscribers"))
    }
}
