package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ShortItem
import com.example.di.AppContainer
import com.example.ui.components.IombgVideoPlayerView
import com.example.ui.components.formatCount
import com.example.ui.theme.*
import com.example.ui.viewmodel.ShortsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen(
    container: AppContainer,
    shortsViewModel: ShortsViewModel,
    onOpenThanksSupport: (ShortItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val shortsList by shortsViewModel.shorts.collectAsState()
    val likedShorts by shortsViewModel.likedShorts.collectAsState()
    val followedChannels by container.socialRepository.followedChannels.collectAsState()
    val commentsMap by container.videoRepository.commentsMap.collectAsState()
    val currentUser by container.authRepository.currentUserState.collectAsState()
    val currentChannel by container.channelRepository.currentChannel.collectAsState()

    var isShortPlaying by remember { mutableStateOf(true) }
    var isShortMuted by remember { mutableStateOf(false) }
    var shortPlaybackPos by remember { mutableFloatStateOf(0f) }
    var showCommentSheet by remember { mutableStateOf(false) }
    var newShortCommentText by remember { mutableStateOf("") }

    val isFeedLoading by shortsViewModel.isFeedLoading.collectAsState()
    val isLoadingMoreShorts by shortsViewModel.isLoadingMoreShorts.collectAsState()
    val hasMoreShorts by shortsViewModel.hasMoreShorts.collectAsState()
    val shortsPaginationError by shortsViewModel.shortsPaginationError.collectAsState()

    if (shortsList.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isFeedLoading) {
                CircularProgressIndicator(color = IombgRed)
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = DarkTextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Shorts yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Be the first creator to upload an IOMBG Short!",
                        fontSize = 13.sp,
                        color = DarkTextSecondary
                    )
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { shortsList.size })
    val currentIndex = pagerState.currentPage.coerceIn(0, (shortsList.size - 1).coerceAtLeast(0))

    val currentShort = shortsList.getOrElse(currentIndex) { shortsList[0] }
    val isCurrentLiked = likedShorts.contains(currentShort.shortId)
    val isFollowed = followedChannels.contains(currentShort.channelId)
    val shortComments = commentsMap[currentShort.shortId] ?: emptyList()

    fun shareShort(short: ShortItem) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Check out this Short '${short.title}' on IOMBG: https://iombg.app/s/${short.shortId}")
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Short via"))
        container.videoRepository.shareVideo(short.shortId)
    }

    var shortStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Reset playback position and state when active page changes, and handle telemetry
    LaunchedEffect(pagerState.currentPage) {
        shortPlaybackPos = 0f
        isShortPlaying = true
        shortStartTime = System.currentTimeMillis()

        val activeShort = shortsList.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        val uid = currentUser?.uid ?: "anonymous"
        container.watchHistoryRepository.recordWatch(
            userId = uid,
            contentType = "SHORT",
            contentId = activeShort.shortId,
            progressMs = 0L,
            durationMs = activeShort.durationSeconds.toLong() * 1000L,
            title = activeShort.title,
            channelName = activeShort.channelName,
            thumbnailUrl = activeShort.thumbnailUrl,
            videoUrl = activeShort.videoUrl
        )
        container.videoRepository.observeComments(activeShort.shortId)
        shortsViewModel.recordShortImpression(activeShort)
        shortsViewModel.recordShortPlay(activeShort)

        // If viewer stays on short for at least 80% of its duration, record completion
        val watchTargetMs = ((activeShort.durationSeconds.coerceAtLeast(10) * 0.8) * 1000).toLong()
        delay(watchTargetMs)
        shortsViewModel.recordShortCompletion(activeShort)
        container.watchHistoryRepository.recordWatch(
            userId = uid,
            contentType = "SHORT",
            contentId = activeShort.shortId,
            progressMs = activeShort.durationSeconds.toLong() * 1000L,
            durationMs = activeShort.durationSeconds.toLong() * 1000L,
            title = activeShort.title,
            channelName = activeShort.channelName,
            thumbnailUrl = activeShort.thumbnailUrl,
            videoUrl = activeShort.videoUrl,
            forceCloudSync = true
        )
    }

    // Trigger cursor pagination when scrolling near the end of loaded shorts
    LaunchedEffect(pagerState.currentPage, shortsList.size, hasMoreShorts, isLoadingMoreShorts) {
        if (hasMoreShorts && !isLoadingMoreShorts && shortsPaginationError == null && shortsList.isNotEmpty()) {
            if (pagerState.currentPage >= shortsList.size - 2) {
                shortsViewModel.loadMoreShorts()
            }
        }
    }

    // Record view/skip telemetry when switching pages
    fun handleNavigateShort(newIndex: Int) {
        if (newIndex < 0 || newIndex >= shortsList.size) return
        val elapsedSec = (System.currentTimeMillis() - shortStartTime) / 1000
        if (elapsedSec < 3) {
            shortsViewModel.recordShortSkip(currentShort, elapsedSec)
        } else {
            shortsViewModel.recordShortView(currentShort, elapsedSec)
        }
        coroutineScope.launch {
            pagerState.animateScrollToPage(newIndex)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("shorts_vertical_pager"),
                key = { page -> shortsList.getOrNull(page)?.shortId ?: page }
            ) { pageIndex ->
                val short = shortsList[pageIndex]
                val isCurrentPage = pagerState.currentPage == pageIndex

                Box(modifier = Modifier.fillMaxSize()) {
                    // Video player for the short. Only plays when this page is the current page and isShortPlaying is true
                    IombgVideoPlayerView(
                        videoUrl = short.videoUrl,
                        thumbnailUrl = short.thumbnailUrl,
                        isPlaying = isCurrentPage && isShortPlaying,
                        isMuted = isShortMuted,
                        playbackPosition = if (isCurrentPage) shortPlaybackPos else 0f,
                        onPositionChanged = { pos ->
                            if (isCurrentPage) shortPlaybackPos = pos
                        },
                        onDurationChanged = { /* dur */ },
                        onPlayPauseChange = { playState ->
                            if (isCurrentPage) isShortPlaying = playState
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                if (isCurrentPage) {
                                    isShortPlaying = !isShortPlaying
                                }
                            }
                    )

                    // Play / Pause Indicator on Tap (only show for current page if paused)
                    if (isCurrentPage && !isShortPlaying) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Paused",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            // Dark gradient overlay for readability across headers and controls
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Top Header: Shorts Title & Navigation arrows
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = IombgRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Shorts",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Header Controls: Mute Toggle & Vertical swipe navigation arrows
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isShortMuted = !isShortMuted },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("shorts_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isShortMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isShortMuted) "Unmute" else "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            if (currentIndex > 0) handleNavigateShort(currentIndex - 1)
                        },
                        enabled = currentIndex > 0,
                        modifier = Modifier.testTag("shorts_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Previous Short",
                            tint = if (currentIndex > 0) Color.White else Color.Gray
                        )
                    }

                    IconButton(
                        onClick = {
                            if (currentIndex < shortsList.size - 1) handleNavigateShort(currentIndex + 1)
                        },
                        enabled = currentIndex < shortsList.size - 1,
                        modifier = Modifier.testTag("shorts_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Next Short",
                            tint = if (currentIndex < shortsList.size - 1) Color.White else Color.Gray
                        )
                    }
                }
            }

            // Right-Side Action Bar: Like, Comment, Share, Thanks Support, Sound Disc
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Like
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { shortsViewModel.toggleLike(currentShort) },
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("shorts_like_button")
                    ) {
                        Icon(
                            imageVector = if (isCurrentLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isCurrentLiked) IombgRed else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = formatCount(currentShort.likeCount + if (isCurrentLiked) 1 else 0),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Comment
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { showCommentSheet = true },
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("shorts_comment_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Comment,
                            contentDescription = "Comments",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = formatCount(currentShort.commentCount.coerceAtLeast(shortComments.size.toLong())),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Thanks / Support
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { onOpenThanksSupport(currentShort) },
                        modifier = Modifier
                            .size(46.dp)
                            .background(IombgRed.copy(alpha = 0.8f), CircleShape)
                            .testTag("shorts_thanks_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolunteerActivism,
                            contentDescription = "Thanks Support",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "Thanks",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Share
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { shareShort(currentShort) },
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("shorts_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = formatCount(currentShort.shareCount),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Audio Disc Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.DarkGray, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = currentShort.channelAvatarUrl,
                        contentDescription = "Audio Disc",
                        modifier = Modifier.size(24.dp).clip(CircleShape)
                    )
                }
            }

            // Bottom Left: Creator Details & Short Title
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.78f)
                    .padding(start = 16.dp, bottom = 90.dp)
            ) {
                // Creator Handle & Follow Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    if (currentShort.isBoosted) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = IombgRed,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "BOOSTED",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    AsyncImage(
                        model = currentShort.channelAvatarUrl,
                        contentDescription = currentShort.channelName,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentShort.channelHandle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            container.socialRepository.toggleFollowChannel(
                                targetChannelId = currentShort.channelId,
                                targetOwnerUid = currentShort.ownerUid,
                                targetChannelName = currentShort.channelName,
                                follower = currentUser
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowed) Color.White.copy(alpha = 0.3f) else IombgRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp).testTag("shorts_follow_button")
                    ) {
                        Text(
                            text = if (isFollowed) "Following" else "Follow",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Short Title & Tags
                Text(
                    text = currentShort.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Sound Track Tag
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Sound",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = currentShort.soundTitle,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
            }

            // Bottom Progress bar
            LinearProgressIndicator(
                progress = { 0.45f },
                color = IombgRed,
                trackColor = Color.White.copy(alpha = 0.3f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(3.dp)
            )

            // Safe Cursor Pagination Loading / Retry Indicator
            if (isLoadingMoreShorts) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("shorts_pagination_loading"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = IombgRed,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Loading more shorts...",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            } else if (shortsPaginationError != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable { shortsViewModel.retryLoadMoreShorts() }
                        .testTag("shorts_pagination_retry"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Failed to load more. Tap to retry",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    // Comments Bottom Sheet
    if (showCommentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCommentSheet = false },
            containerColor = DarkSurfaceElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Comments (${currentShort.commentCount.coerceAtLeast(shortComments.size.toLong())})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Add comment input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = currentUser?.photoUrl?.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" },
                        contentDescription = "Avatar",
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = newShortCommentText,
                        onValueChange = { newShortCommentText = it },
                        placeholder = { Text("Add a comment...", fontSize = 12.sp, color = DarkTextMuted) },
                        modifier = Modifier.weight(1f).testTag("shorts_add_comment_input"),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newShortCommentText.isNotBlank()) {
                                coroutineScope.launch {
                                    container.videoRepository.addComment(
                                        contentId = currentShort.shortId,
                                        contentType = "SHORT",
                                        author = currentUser,
                                        channel = currentChannel,
                                        text = newShortCommentText
                                    )
                                    newShortCommentText = ""
                                }
                            }
                        },
                        modifier = Modifier.testTag("shorts_submit_comment_button")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = IombgRed)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Comments list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (shortComments.isEmpty()) {
                        item {
                            Text("No comments yet. Be the first to comment!", color = DarkTextSecondary, fontSize = 12.sp)
                        }
                    } else {
                        items(shortComments) { comment ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = comment.authorAvatarUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" },
                                    contentDescription = comment.authorName,
                                    modifier = Modifier.size(28.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(comment.authorName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(comment.text, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showCommentSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}
