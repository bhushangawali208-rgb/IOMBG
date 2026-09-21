package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.di.AppContainer
import com.example.ui.theme.*
import com.example.ui.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    container: AppContainer,
    searchViewModel: SearchViewModel,
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onShortClick: (ShortItem) -> Unit,
    onChannelClick: (Channel) -> Unit
) {
    val query by searchViewModel.query.collectAsState()
    val selectedTab by searchViewModel.selectedTab.collectAsState()
    val uiState by searchViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding()
            ) {
                // Top Search Bar Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("search_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkTextPrimary
                        )
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { searchViewModel.onQueryChanged(it) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .testTag("search_input_field"),
                        placeholder = {
                            Text(
                                "Search videos, shorts, channels...",
                                fontSize = 14.sp,
                                color = DarkTextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = DarkTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchViewModel.clearSearch() },
                                    modifier = Modifier.testTag("search_clear_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = DarkTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IombgRed,
                            unfocusedBorderColor = DarkBorderSubtle,
                            focusedTextColor = DarkTextPrimary,
                            unfocusedTextColor = DarkTextPrimary,
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated
                        )
                    )
                }

                // Filter Chips Row
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        SearchFilterChip(
                            label = "All",
                            isSelected = selectedTab == SearchResultType.ALL,
                            testTag = "search_filter_all",
                            onClick = { searchViewModel.onTabSelected(SearchResultType.ALL) }
                        )
                    }
                    item {
                        SearchFilterChip(
                            label = "Videos",
                            isSelected = selectedTab == SearchResultType.VIDEO,
                            testTag = "search_filter_videos",
                            onClick = { searchViewModel.onTabSelected(SearchResultType.VIDEO) }
                        )
                    }
                    item {
                        SearchFilterChip(
                            label = "Shorts",
                            isSelected = selectedTab == SearchResultType.SHORT,
                            testTag = "search_filter_shorts",
                            onClick = { searchViewModel.onTabSelected(SearchResultType.SHORT) }
                        )
                    }
                    item {
                        SearchFilterChip(
                            label = "Channels",
                            isSelected = selectedTab == SearchResultType.CHANNEL,
                            testTag = "search_filter_channels",
                            onClick = { searchViewModel.onTabSelected(SearchResultType.CHANNEL) }
                        )
                    }
                }
                HorizontalDivider(color = DarkBorderSubtle, thickness = 0.5.dp)
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is SearchUiState.Idle -> {
                    SearchEmptyQueryView()
                }
                is SearchUiState.Searching -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("search_loading_state"),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = IombgRed,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                is SearchUiState.Empty -> {
                    SearchNoResultsView(query = state.query)
                }
                is SearchUiState.Error -> {
                    SearchErrorView(
                        message = state.message,
                        canRetry = state.canRetry,
                        onRetry = { searchViewModel.retry() }
                    )
                }
                is SearchUiState.Success -> {
                    SearchResultsListView(
                        results = state.results,
                        hasMore = state.hasMore,
                        isLoadingMore = state.isLoadingMore,
                        onLoadMore = { searchViewModel.loadNextPage() },
                        onVideoClick = onVideoClick,
                        onShortClick = onShortClick,
                        onChannelClick = onChannelClick
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchFilterChip(
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) IombgRed else DarkSurfaceElevated,
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else DarkTextSecondary
        )
    }
}

@Composable
private fun SearchEmptyQueryView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("search_empty_query_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = DarkTextMuted,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Search IOMBG",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Find videos, shorts, and channels by title, creator, or topic.",
            fontSize = 13.sp,
            color = DarkTextSecondary,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun SearchNoResultsView(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("search_no_results_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            tint = DarkTextMuted,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No results found",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (query.isNotBlank()) "No matches found for \"$query\"." else "No content matched your query.",
            fontSize = 13.sp,
            color = DarkTextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Try different keywords, creator names, or check your spelling.",
            fontSize = 12.sp,
            color = DarkTextMuted
        )
    }
}

@Composable
private fun SearchErrorView(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("search_error_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = StatusError,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Search unavailable",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            color = DarkTextSecondary
        )
        if (canRetry) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                modifier = Modifier.testTag("search_retry_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry Search")
            }
        }
    }
}

@Composable
private fun SearchResultsListView(
    results: List<SearchResultItem>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onShortClick: (ShortItem) -> Unit,
    onChannelClick: (Channel) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("search_results_list"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = results,
            key = { "${it.type.name}_${it.id}" }
        ) { item ->
            when (item) {
                is SearchResultItem.VideoItem -> {
                    SearchVideoResultCard(
                        video = item.video,
                        onClick = { onVideoClick(item.video) }
                    )
                }
                is SearchResultItem.ShortItemResult -> {
                    SearchShortResultCard(
                        short = item.short,
                        onClick = { onShortClick(item.short) }
                    )
                }
                is SearchResultItem.ChannelItem -> {
                    SearchChannelResultCard(
                        channel = item.channel,
                        isCreator = false,
                        onClick = { onChannelClick(item.channel) }
                    )
                }
                is SearchResultItem.CreatorItem -> {
                    SearchChannelResultCard(
                        channel = item.channel,
                        isCreator = true,
                        onClick = { onChannelClick(item.channel) }
                    )
                }
            }
        }

        // Pagination footer
        item {
            if (hasMore) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingMore) {
                        CircularProgressIndicator(
                            color = IombgRed,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        OutlinedButton(
                            onClick = onLoadMore,
                            modifier = Modifier.testTag("search_load_more_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextPrimary)
                        ) {
                            Text("Load More Results", fontSize = 13.sp)
                        }
                    }
                }
            } else if (results.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .testTag("search_end_of_results"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "End of search results",
                        fontSize = 12.sp,
                        color = DarkTextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchVideoResultCard(
    video: Video,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("search_result_video_${video.videoId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 75.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                if (video.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = DarkTextMuted,
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center)
                    )
                }
                // Badge
                Surface(
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.TopStart),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = "VIDEO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = IombgRed,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = video.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.channelName.ifBlank { "Channel" },
                    fontSize = 12.sp,
                    color = DarkTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (video.category.isNotBlank() || video.viewCount > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${video.category} • ${video.viewCount} views",
                        fontSize = 11.sp,
                        color = DarkTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchShortResultCard(
    short: ShortItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("search_result_short_${short.shortId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Portrait Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 65.dp, height = 90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                if (short.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = short.thumbnailUrl,
                        contentDescription = short.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = IombgCoral,
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.Center)
                    )
                }
                Surface(
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.TopStart),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = "SHORT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = IombgCoral,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = short.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = short.channelName.ifBlank { "Creator" },
                    fontSize = 12.sp,
                    color = DarkTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (short.viewCount > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${short.viewCount} views",
                        fontSize = 11.sp,
                        color = DarkTextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchChannelResultCard(
    channel: Channel,
    isCreator: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("search_result_channel_${channel.channelId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel Avatar
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(DarkSurface)
                    .border(1.dp, DarkBorderSubtle, CircleShape)
            ) {
                if (channel.profileImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = channel.profileImageUrl,
                        contentDescription = channel.channelName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = DarkTextSecondary,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.channelName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isCreator) IombgGold.copy(alpha = 0.2f) else DarkSurface
                    ) {
                        Text(
                            text = if (isCreator) "CREATOR" else "CHANNEL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCreator) IombgGold else DarkTextSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (channel.handle.startsWith("@")) channel.handle else "@${channel.handle}",
                    fontSize = 12.sp,
                    color = DarkTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${channel.subscriberCount} subscribers • ${channel.videoCount} videos",
                    fontSize = 11.sp,
                    color = DarkTextMuted
                )
            }
        }
    }
}
