package com.example.data.model

import java.util.Locale

/**
 * Search result classification types for IOMBG.
 */
enum class SearchResultType(val label: String) {
    ALL("All"),
    VIDEO("Videos"),
    SHORT("Shorts"),
    CHANNEL("Channels"),
    CREATOR("Creators")
}

/**
 * Distinct, typed search result items preserving real Firestore data.
 */
sealed class SearchResultItem {
    abstract val id: String
    abstract val type: SearchResultType

    data class VideoItem(val video: Video) : SearchResultItem() {
        override val id: String get() = video.videoId
        override val type: SearchResultType get() = SearchResultType.VIDEO
    }

    data class ShortItemResult(val short: ShortItem) : SearchResultItem() {
        override val id: String get() = short.shortId
        override val type: SearchResultType get() = SearchResultType.SHORT
    }

    data class ChannelItem(val channel: Channel) : SearchResultItem() {
        override val id: String get() = channel.channelId
        override val type: SearchResultType get() = SearchResultType.CHANNEL
    }

    data class CreatorItem(val channel: Channel) : SearchResultItem() {
        override val id: String get() = "creator_${channel.channelId}"
        override val type: SearchResultType get() = SearchResultType.CREATOR
    }
}

/**
 * UI State for search presentation with full lifecycle coverage.
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Searching : SearchUiState
    data class Success(
        val results: List<SearchResultItem>,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false
    ) : SearchUiState
    data class Empty(val query: String) : SearchUiState
    data class Error(val message: String, val canRetry: Boolean = true) : SearchUiState
}

/**
 * Safe, multi-lingual normalization and prefix range generation for Firestore on Spark.
 * Handles English, Marathi ("मराठी"), Hindi ("हिंदी"), mixed scripts, numbers, and punctuation safely.
 */
object SearchNormalizer {

    /**
     * Normalizes user search query or indexable content string:
     * - Trims leading and trailing whitespace
     * - Collapses internal consecutive whitespace to a single space
     * - Lowercases English/Latin characters safely using Locale.ROOT without altering Unicode Devanagari scripts
     * - Strictly preserves Unicode characters, letters, digits, and punctuation
     */
    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""
        val collapsed = raw.trim().replace(Regex("\\s+"), " ")
        return collapsed.lowercase(Locale.ROOT)
    }

    /**
     * Converts words in text to title case for prefix matching against capitalized legacy fields.
     */
    fun toTitleCase(text: String): String {
        if (text.isBlank()) return ""
        return text.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
            }
        }
    }

    /**
     * Extracts distinct normalized query tokens (minimum 1 character).
     */
    fun extractTokens(text: String): List<String> {
        val norm = normalize(text)
        if (norm.isBlank()) return emptyList()
        return norm.split(" ").filter { it.isNotBlank() }
    }

    /**
     * Returns the Firestore prefix range boundaries: [start, end].
     * Uses '\uf8ff' which is the standard highest UTF-8/UTF-16 code point in Firestore range queries.
     */
    fun getPrefixRange(prefix: String): Pair<String, String> {
        val trimmed = prefix.trim()
        return Pair(trimmed, trimmed + "\uf8ff")
    }

    /**
     * Deterministic scoring to rank merged search results client-side
     * based purely on real document data matches.
     */
    fun calculateVideoRelevance(video: Video, query: String): Int {
        val normQuery = normalize(query)
        if (normQuery.isBlank()) return 0
        var score = 0
        val normTitle = normalize(video.title)
        val normDesc = normalize(video.description)
        val normChannel = normalize(video.channelName)

        when {
            normTitle == normQuery -> score += 100
            normTitle.startsWith(normQuery) -> score += 60
            normTitle.contains(normQuery) -> score += 30
        }

        if (normChannel.contains(normQuery)) score += 25

        if (video.tags.any { normalize(it).contains(normQuery) }) {
            score += 20
        }

        if (normDesc.contains(normQuery)) {
            score += 10
        }

        return score
    }

    fun calculateShortRelevance(short: ShortItem, query: String): Int {
        val normQuery = normalize(query)
        if (normQuery.isBlank()) return 0
        var score = 0
        val normTitle = normalize(short.title)
        val normDesc = normalize(short.description)
        val normChannel = normalize(short.channelName)

        when {
            normTitle == normQuery -> score += 100
            normTitle.startsWith(normQuery) -> score += 60
            normTitle.contains(normQuery) -> score += 30
        }

        if (normChannel.contains(normQuery)) score += 25

        if (short.tags.any { normalize(it).contains(normQuery) }) {
            score += 20
        }

        if (normDesc.contains(normQuery)) {
            score += 10
        }

        return score
    }

    fun calculateChannelRelevance(channel: Channel, query: String): Int {
        val normQuery = normalize(query)
        if (normQuery.isBlank()) return 0
        var score = 0
        val normName = normalize(channel.channelName)
        val cleanQuery = normQuery.removePrefix("@")
        val cleanHandle = normalize(channel.handle).removePrefix("@")

        when {
            normName == cleanQuery || normName == normQuery -> score += 100
            normName.startsWith(cleanQuery) -> score += 60
            normName.contains(cleanQuery) -> score += 30
        }

        when {
            cleanHandle == cleanQuery -> score += 80
            cleanHandle.startsWith(cleanQuery) -> score += 50
            cleanHandle.contains(cleanQuery) -> score += 20
        }

        return score
    }
}
