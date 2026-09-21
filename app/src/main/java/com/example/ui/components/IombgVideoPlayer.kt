package com.example.ui.components

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Native Android Video & Audio Player for IOMBG.
 *
 * Core Capabilities:
 * - Uses native Android MediaPlayer + TextureView for seamless Compose integration without z-index flickering.
 * - Routes audio through Android STREAM_MUSIC with AudioAttributes (USAGE_MEDIA, CONTENT_TYPE_MOVIE).
 * - Full Android Audio Focus management: requests focus on play, abandons on pause/stop/background,
 *   and respects transient ducking and loss.
 * - Strictly respects device/user volume and hardware mute settings without bypassing them.
 * - Visible Mute/Unmute state with volume toggle.
 * - Lifecycle-aware lock-screen handling: cleanly pauses playback when the device screen locks or
 *   moves to background, and resumes safely upon foreground return if it was playing.
 * - Supports scrubbing, 10s skip rewind/forward, and fullscreen viewing.
 */
@Composable
fun IombgVideoPlayerView(
    videoUrl: String,
    thumbnailUrl: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    playbackPosition: Float, // 0.0 to 1.0
    seekTrigger: Long = 0L,
    onPositionChanged: (Float) -> Unit,
    onDurationChanged: (Long) -> Unit,
    onPlayPauseChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentVideoUrl by rememberUpdatedState(videoUrl)
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentIsMuted by rememberUpdatedState(isMuted)

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var surface by remember { mutableStateOf<Surface?>(null) }
    var isPrepared by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var wasPlayingBeforeBackground by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }

    // Audio focus listener
    val audioFocusListener = remember {
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    // Permanent loss: cleanly pause
                    onPlayPauseChange(false)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    // Transient loss (e.g. phone call): cleanly pause
                    onPlayPauseChange(false)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // Lower volume slightly while ducking
                    mediaPlayer?.setVolume(0.2f, 0.2f)
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    // Restored focus: reset volume to current mute setting
                    val vol = if (currentIsMuted) 0f else 1f
                    mediaPlayer?.setVolume(vol, vol)
                }
            }
        }
    }

    val audioFocusRequest: Any? = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
        } else {
            null
        }
    }

    fun requestAudioFocus(): Boolean {
        if (audioManager == null) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest is AudioFocusRequest) {
            audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun abandonAudioFocus() {
        if (audioManager == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest is AudioFocusRequest) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    // Initialize MediaPlayer
    DisposableEffect(currentVideoUrl) {
        val player = MediaPlayer().apply {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            setAudioAttributes(audioAttributes)

            setOnPreparedListener { mp ->
                isPrepared = true
                isBuffering = false
                val dur = mp.duration.toLong()
                if (dur > 0) {
                    durationMs = dur
                    onDurationChanged(dur / 1000)
                }
                val vol = if (currentIsMuted) 0f else 1f
                mp.setVolume(vol, vol)
                if (currentIsPlaying) {
                    requestAudioFocus()
                    mp.start()
                }
            }

            setOnCompletionListener {
                onPlayPauseChange(false)
                abandonAudioFocus()
            }

            setOnBufferingUpdateListener { _, _ ->
                // Buffering updates handled by Android native stack
            }

            setOnErrorListener { _, _, _ ->
                isBuffering = false
                true // Handled gracefully without crash
            }
        }

        try {
            if (currentVideoUrl.isNotBlank()) {
                player.setDataSource(context, Uri.parse(currentVideoUrl))
                player.prepareAsync()
            }
        } catch (e: Exception) {
            isBuffering = false
        }

        mediaPlayer = player

        onDispose {
            abandonAudioFocus()
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
            mediaPlayer = null
            isPrepared = false
        }
    }

    // Attach surface when available
    LaunchedEffect(surface, mediaPlayer, isPrepared) {
        val mp = mediaPlayer ?: return@LaunchedEffect
        val s = surface ?: return@LaunchedEffect
        try {
            mp.setSurface(s)
        } catch (e: Exception) {
            // Surface may be unavailable
        }
    }

    // Play / Pause reactivity
    LaunchedEffect(isPlaying, isPrepared, mediaPlayer) {
        val mp = mediaPlayer ?: return@LaunchedEffect
        if (!isPrepared) return@LaunchedEffect
        try {
            if (isPlaying) {
                requestAudioFocus()
                if (!mp.isPlaying) {
                    mp.start()
                }
            } else {
                if (mp.isPlaying) {
                    mp.pause()
                }
                abandonAudioFocus()
            }
        } catch (e: Exception) {
            // Player state exception handling
        }
    }

    // Mute / Unmute reactivity
    LaunchedEffect(isMuted, mediaPlayer, isPrepared) {
        val mp = mediaPlayer ?: return@LaunchedEffect
        if (!isPrepared) return@LaunchedEffect
        try {
            val volume = if (isMuted) 0f else 1f
            mp.setVolume(volume, volume)
        } catch (e: Exception) {
            // Ignore volume set error
        }
    }

    // Explicit seek trigger reactivity
    LaunchedEffect(seekTrigger) {
        if (seekTrigger > 0L) {
            val mp = mediaPlayer ?: return@LaunchedEffect
            if (!isPrepared || durationMs <= 0L) return@LaunchedEffect
            try {
                val targetMs = (durationMs * playbackPosition.coerceIn(0f, 1f)).toInt()
                mp.seekTo(targetMs)
            } catch (e: Exception) {
                // Ignore seek exception
            }
        }
    }

    // Position sync loop during playback
    LaunchedEffect(isPlaying, isPrepared, mediaPlayer) {
        while (isActive && isPlaying && isPrepared) {
            val mp = mediaPlayer
            if (mp != null && mp.isPlaying && durationMs > 0) {
                val current = mp.currentPosition.toFloat()
                val frac = (current / durationMs.toFloat()).coerceIn(0f, 1f)
                onPositionChanged(frac)
            }
            delay(500)
        }
    }

    // Lifecycle Observer for Screen Lock / Background handling
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    // Cleanly pause playback when the device screen locks or the app moves to background
                    val mp = mediaPlayer
                    if (mp != null && mp.isPlaying) {
                        wasPlayingBeforeBackground = true
                        try {
                            mp.pause()
                        } catch (e: Exception) {
                            // Ignored
                        }
                        onPlayPauseChange(false)
                        abandonAudioFocus()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Resume safely when the app returns to foreground if it was playing before lock
                    if (wasPlayingBeforeBackground) {
                        wasPlayingBeforeBackground = false
                        val mp = mediaPlayer
                        if (mp != null && isPrepared) {
                            requestAudioFocus()
                            try {
                                mp.start()
                                onPlayPauseChange(true)
                            } catch (e: Exception) {
                                // Ignored
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    abandonAudioFocus()
                    try {
                        mediaPlayer?.release()
                    } catch (e: Exception) {
                        // Ignored
                    }
                    mediaPlayer = null
                    isPrepared = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // TextureView for smooth hardware-accelerated video rendering with audio
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                            try {
                                val newSurface = Surface(st)
                                surface = newSurface
                                mediaPlayer?.setSurface(newSurface)
                            } catch (e: Exception) {
                                // Ignore if player in invalid state
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            try {
                                surface?.release()
                            } catch (e: Exception) {
                                // Ignore release errors
                            }
                            surface = null
                            try {
                                mediaPlayer?.setSurface(null)
                            } catch (e: Exception) {
                                // Ignore IllegalStateException if MediaPlayer is already released or in error state
                            }
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                            // Frame updated, buffering is complete
                            if (isBuffering) isBuffering = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Show thumbnail artwork while buffering/loading or if surface is not ready
        if (isBuffering && currentVideoUrl.isNotBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Unavailable State Overlay: Shown when video URL is missing or blank (never substituting demo video)
        if (currentVideoUrl.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .testTag("player_video_unavailable_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = "Video Unavailable",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Video Unavailable",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This video has no playable media stream.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
