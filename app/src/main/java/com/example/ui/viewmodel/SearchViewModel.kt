package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.di.AppContainer
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedTab = MutableStateFlow(SearchResultType.ALL)
    val selectedTab: StateFlow<SearchResultType> = _selectedTab.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var paginationJob: Job? = null

    // Cursor tracking
    private var lastVideoDoc: DocumentSnapshot? = null
    private var lastShortDoc: DocumentSnapshot? = null
    private var lastChannelDoc: DocumentSnapshot? = null

    private var hasMoreVideos = false
    private var hasMoreShorts = false
    private var hasMoreChannels = false

    private val currentItems = mutableListOf<SearchResultItem>()
    private val seenIds = mutableSetOf<String>()

    companion object {
        const val SEARCH_PAGE_SIZE = 10L
        const val DEBOUNCE_DELAY_MS = 300L
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()

        val normalized = SearchNormalizer.normalize(newQuery)
        if (normalized.isBlank()) {
            resetSearchState()
            _uiState.value = SearchUiState.Idle
            return
        }

        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_DELAY_MS)
            executeSearch(normalized, isNewSearch = true)
        }
    }

    fun onTabSelected(tab: SearchResultType) {
        if (_selectedTab.value == tab) return
        _selectedTab.value = tab

        val currentQuery = SearchNormalizer.normalize(_query.value)
        if (currentQuery.isBlank()) {
            resetSearchState()
            _uiState.value = SearchUiState.Idle
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            executeSearch(currentQuery, isNewSearch = true)
        }
    }

    fun retry() {
        val currentQuery = SearchNormalizer.normalize(_query.value)
        if (currentQuery.isBlank()) {
            _uiState.value = SearchUiState.Idle
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            executeSearch(currentQuery, isNewSearch = true)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        paginationJob?.cancel()
        _query.value = ""
        resetSearchState()
        _uiState.value = SearchUiState.Idle
    }

    fun loadNextPage() {
        if (paginationJob?.isActive == true) return
        val currentState = _uiState.value as? SearchUiState.Success ?: return
        if (!currentState.hasMore || currentState.isLoadingMore) return

        val currentQuery = SearchNormalizer.normalize(_query.value)
        if (currentQuery.isBlank()) return

        _uiState.value = currentState.copy(isLoadingMore = true)

        paginationJob = viewModelScope.launch {
            try {
                executeSearch(currentQuery, isNewSearch = false)
            } catch (e: CancellationException) {
                // Ignore coroutine cancellation
            } catch (e: Exception) {
                _uiState.value = currentState.copy(isLoadingMore = false)
            }
        }
    }

    private suspend fun executeSearch(queryText: String, isNewSearch: Boolean) {
        if (isNewSearch) {
            resetSearchState()
            _uiState.value = SearchUiState.Searching

            // Record search event in algorithm recommendations repository
            try {
                val currentUid = container.authRepository.currentUserState.value?.uid ?: "current_user"
                container.recommendationEventRepository.recordEvent(
                    userId = currentUid,
                    contentId = "search_query",
                    contentType = "SEARCH",
                    eventType = EventType.SEARCH,
                    searchQuery = queryText
                )
            } catch (e: Exception) {
                // Safe non-fatal logging
            }
        }

        try {
            val newItems = mutableListOf<SearchResultItem>()
            val tab = _selectedTab.value

            when (tab) {
                SearchResultType.ALL -> {
                    // Merged multi-type search with cursor pagination
                    if (isNewSearch || hasMoreVideos) {
                        val videoResult = container.videoRepository.searchVideosPaginated(
                            queryText = queryText,
                            pageSize = SEARCH_PAGE_SIZE,
                            lastVisible = if (isNewSearch) null else lastVideoDoc
                        )
                        lastVideoDoc = videoResult.lastSnapshot
                        hasMoreVideos = videoResult.hasMore
                        videoResult.items
                            .filter { it.visibility == "PUBLIC" }
                            .forEach { video ->
                                if (seenIds.add(video.videoId)) {
                                    newItems.add(SearchResultItem.VideoItem(video))
                                }
                            }
                    }

                    if (isNewSearch || hasMoreShorts) {
                        val shortResult = container.videoRepository.searchShortsPaginated(
                            queryText = queryText,
                            pageSize = SEARCH_PAGE_SIZE,
                            lastVisible = if (isNewSearch) null else lastShortDoc
                        )
                        lastShortDoc = shortResult.lastSnapshot
                        hasMoreShorts = shortResult.hasMore
                        shortResult.items
                            .filter { it.visibility == "PUBLIC" }
                            .forEach { short ->
                                if (seenIds.add(short.shortId)) {
                                    newItems.add(SearchResultItem.ShortItemResult(short))
                                }
                            }
                    }

                    if (isNewSearch || hasMoreChannels) {
                        val channelResult = container.channelRepository.searchChannelsPaginated(
                            queryText = queryText,
                            pageSize = SEARCH_PAGE_SIZE,
                            lastVisible = if (isNewSearch) null else lastChannelDoc
                        )
                        lastChannelDoc = channelResult.lastSnapshot
                        hasMoreChannels = channelResult.hasMore
                        channelResult.items.forEach { channel ->
                            if (seenIds.add(channel.channelId)) {
                                newItems.add(SearchResultItem.ChannelItem(channel))
                            }
                        }
                    }
                }

                SearchResultType.VIDEO -> {
                    val result = container.videoRepository.searchVideosPaginated(
                        queryText = queryText,
                        pageSize = SEARCH_PAGE_SIZE,
                        lastVisible = if (isNewSearch) null else lastVideoDoc
                    )
                    lastVideoDoc = result.lastSnapshot
                    hasMoreVideos = result.hasMore
                    result.items
                        .filter { it.visibility == "PUBLIC" }
                        .forEach { video ->
                            if (seenIds.add(video.videoId)) {
                                newItems.add(SearchResultItem.VideoItem(video))
                            }
                        }
                }

                SearchResultType.SHORT -> {
                    val result = container.videoRepository.searchShortsPaginated(
                        queryText = queryText,
                        pageSize = SEARCH_PAGE_SIZE,
                        lastVisible = if (isNewSearch) null else lastShortDoc
                    )
                    lastShortDoc = result.lastSnapshot
                    hasMoreShorts = result.hasMore
                    result.items
                        .filter { it.visibility == "PUBLIC" }
                        .forEach { short ->
                            if (seenIds.add(short.shortId)) {
                                newItems.add(SearchResultItem.ShortItemResult(short))
                            }
                        }
                }

                SearchResultType.CHANNEL, SearchResultType.CREATOR -> {
                    val result = container.channelRepository.searchChannelsPaginated(
                        queryText = queryText,
                        pageSize = SEARCH_PAGE_SIZE,
                        lastVisible = if (isNewSearch) null else lastChannelDoc
                    )
                    lastChannelDoc = result.lastSnapshot
                    hasMoreChannels = result.hasMore
                    result.items.forEach { channel ->
                        val item = if (tab == SearchResultType.CREATOR) {
                            SearchResultItem.CreatorItem(channel)
                        } else {
                            SearchResultItem.ChannelItem(channel)
                        }
                        if (seenIds.add(item.id)) {
                            newItems.add(item)
                        }
                    }
                }
            }

            currentItems.addAll(newItems)

            val hasMore = when (tab) {
                SearchResultType.ALL -> hasMoreVideos || hasMoreShorts || hasMoreChannels
                SearchResultType.VIDEO -> hasMoreVideos
                SearchResultType.SHORT -> hasMoreShorts
                SearchResultType.CHANNEL, SearchResultType.CREATOR -> hasMoreChannels
            }

            if (currentItems.isEmpty()) {
                _uiState.value = SearchUiState.Empty(queryText)
            } else {
                _uiState.value = SearchUiState.Success(
                    results = currentItems.toList(),
                    hasMore = hasMore,
                    isLoadingMore = false
                )
            }
        } catch (e: CancellationException) {
            // Task canceled due to newer query
            throw e
        } catch (e: Exception) {
            if (isNewSearch) {
                _uiState.value = SearchUiState.Error(
                    message = e.message ?: "Search request failed. Please check connection and retry.",
                    canRetry = true
                )
            } else {
                val currentState = _uiState.value as? SearchUiState.Success
                if (currentState != null) {
                    _uiState.value = currentState.copy(isLoadingMore = false)
                }
            }
        }
    }

    private fun resetSearchState() {
        currentItems.clear()
        seenIds.clear()
        lastVideoDoc = null
        lastShortDoc = null
        lastChannelDoc = null
        hasMoreVideos = false
        hasMoreShorts = false
        hasMoreChannels = false
    }
}
