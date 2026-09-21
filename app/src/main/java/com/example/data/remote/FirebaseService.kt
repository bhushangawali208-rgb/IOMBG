package com.example.data.remote

import android.os.Bundle
import android.util.Log
import com.example.data.model.*
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

open class FirebaseService(
    private val analyticsProvider: (() -> FirebaseAnalytics?)? = null
) {
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val analytics: FirebaseAnalytics? by lazy {
        try {
            analyticsProvider?.invoke()
        } catch (e: Exception) {
            Log.d("FirebaseService", "Analytics init: ${e.message}")
            null
        }
    }

    val currentFirebaseUser: FirebaseUser?
        get() = try {
            FirebaseAuth.getInstance().currentUser
        } catch (e: Exception) {
            null
        }

    // ==================== USER PROFILE ====================
    open suspend fun getUserProfile(uid: String): UserAccount? {
        return try {
            val snapshot = firestore.collection("users").document(uid).get().await()
            if (snapshot.exists()) {
                snapshot.toObject(UserAccount::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d("FirebaseService", "User profile fetch: ${e.message}")
            null
        }
    }

    open suspend fun saveUserProfile(user: UserAccount): Boolean {
        return try {
            firestore.collection("users").document(user.uid).set(user, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "User profile save: ${e.message}")
            false
        }
    }

    suspend fun requestAccountDeletion(uid: String, reason: String): Boolean {
        return try {
            val reqData = hashMapOf(
                "uid" to uid,
                "reason" to reason,
                "requestedAt" to com.google.firebase.Timestamp.now(),
                "status" to "PENDING"
            )
            firestore.collection("accountDeletionRequests").document(uid).set(reqData).await()
            // Delete personal profile document
            firestore.collection("users").document(uid).delete().await()
            // Delete Auth user
            val user = currentFirebaseUser
            if (user != null && user.uid == uid) {
                user.delete().await()
            }
            true
        } catch (e: Exception) {
            Log.e("FirebaseService", "Account deletion failed: ${e.message}")
            false
        }
    }

    // ==================== CHANNEL ====================
    suspend fun getChannel(channelId: String): Channel? {
        return try {
            val doc = firestore.collection("channels").document(channelId).get().await()
            doc.toObject(Channel::class.java)
        } catch (e: Exception) {
            Log.d("FirebaseService", "Channel fetch: ${e.message}")
            null
        }
    }

    open suspend fun isHandleAvailable(handle: String): Boolean {
        return try {
            val cleanHandle = if (handle.startsWith("@")) handle.lowercase() else "@${handle.lowercase()}"
            val snapshot = firestore.collection("channels")
                .whereEqualTo("handle", cleanHandle)
                .limit(1)
                .get()
                .await()
            snapshot.isEmpty
        } catch (e: Exception) {
            Log.d("FirebaseService", "Handle availability check: ${e.message}")
            true
        }
    }

    open suspend fun getChannelByOwnerUid(ownerUid: String): Channel? {
        return try {
            val snapshot = firestore.collection("channels")
                .whereEqualTo("ownerUid", ownerUid)
                .limit(1)
                .get()
                .await()
            if (!snapshot.isEmpty) {
                snapshot.documents[0].toObject(Channel::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d("FirebaseService", "Channel by owner fetch: ${e.message}")
            null
        }
    }

    open suspend fun createChannel(channel: Channel): Boolean {
        return try {
            val prepared = channel.copy(
                searchName = SearchNormalizer.normalize(channel.channelName),
                searchHandle = SearchNormalizer.normalize(channel.handle)
            )
            firestore.collection("channels").document(prepared.channelId).set(prepared).await()
            // Also initialize creator wallet
            val initialWallet = Wallet(
                walletId = "wallet_${channel.channelId}",
                channelId = channel.channelId,
                ownerUid = channel.ownerUid,
                availableBalance = 0.0,
                pendingBalance = 0.0,
                lifetimeEarnings = 0.0,
                updatedAt = System.currentTimeMillis()
            )
            firestore.collection("wallets").document(initialWallet.walletId).set(initialWallet).await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Channel creation: ${e.message}")
            false
        }
    }

    suspend fun updateChannel(channel: Channel): Boolean {
        return try {
            firestore.collection("channels").document(channel.channelId).set(channel, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Channel update error: ${e.message}")
            false
        }
    }

    fun observePopularChannels(): Flow<List<Channel>> = callbackFlow {
        val listener = firestore.collection("channels")
            .orderBy("subscriberCount", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(Channel::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // ==================== VIDEOS & SHORTS ====================
    suspend fun uploadVideo(video: Video): Boolean {
        return try {
            val prepared = video.copy(
                searchTitle = SearchNormalizer.normalize(video.title),
                searchDescription = SearchNormalizer.normalize(video.description),
                searchTags = video.tags.map { SearchNormalizer.normalize(it) },
                searchChannelName = SearchNormalizer.normalize(video.channelName)
            )
            firestore.collection("videos").document(prepared.videoId).set(prepared).await()
            // Increment video count on channel
            firestore.collection("channels").document(prepared.channelId)
                .update("videoCount", FieldValue.increment(1))
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Video upload: ${e.message}")
            false
        }
    }

    suspend fun updateVideo(video: Video): Boolean {
        return try {
            val prepared = video.copy(
                searchTitle = SearchNormalizer.normalize(video.title),
                searchDescription = SearchNormalizer.normalize(video.description),
                searchTags = video.tags.map { SearchNormalizer.normalize(it) },
                searchChannelName = SearchNormalizer.normalize(video.channelName)
            )
            firestore.collection("videos").document(prepared.videoId).set(prepared, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Video update: ${e.message}")
            false
        }
    }

    suspend fun deleteVideo(videoId: String, channelId: String): Boolean {
        return try {
            firestore.collection("videos").document(videoId).delete().await()
            firestore.collection("channels").document(channelId)
                .update("videoCount", FieldValue.increment(-1))
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Video delete: ${e.message}")
            false
        }
    }

    suspend fun uploadShort(short: ShortItem): Boolean {
        return try {
            val prepared = short.copy(
                searchTitle = SearchNormalizer.normalize(short.title),
                searchDescription = SearchNormalizer.normalize(short.description),
                searchTags = short.tags.map { SearchNormalizer.normalize(it) },
                searchChannelName = SearchNormalizer.normalize(short.channelName)
            )
            firestore.collection("shorts").document(prepared.shortId).set(prepared).await()
            firestore.collection("channels").document(prepared.channelId)
                .update("shortsCount", FieldValue.increment(1))
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Short upload: ${e.message}")
            false
        }
    }

    suspend fun updateShort(short: ShortItem): Boolean {
        return try {
            val prepared = short.copy(
                searchTitle = SearchNormalizer.normalize(short.title),
                searchDescription = SearchNormalizer.normalize(short.description),
                searchTags = short.tags.map { SearchNormalizer.normalize(it) },
                searchChannelName = SearchNormalizer.normalize(short.channelName)
            )
            firestore.collection("shorts").document(prepared.shortId).set(prepared, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Short update: ${e.message}")
            false
        }
    }

    suspend fun deleteShort(shortId: String, channelId: String): Boolean {
        return try {
            firestore.collection("shorts").document(shortId).delete().await()
            firestore.collection("channels").document(channelId)
                .update("shortsCount", FieldValue.increment(-1))
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Short delete: ${e.message}")
            false
        }
    }

    // ==================== PAGINATED CONTENT FEEDS & CURSORS ====================
    open fun observeVideosFirstPage(pageSize: Long = 10L): Flow<PaginatedResult<Video>> = callbackFlow {
        val listener = firestore.collection("videos")
            .whereEqualTo("visibility", "PUBLIC")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.d("FirebaseService", "observeVideosFirstPage error: ${error.message}")
                    trySend(PaginatedResult(emptyList(), null, false))
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents ?: emptyList()
                val list = docs.mapNotNull { it.toObject(Video::class.java) }
                val lastDoc = docs.lastOrNull()
                val hasMore = docs.size >= pageSize
                trySend(PaginatedResult(list, lastDoc, hasMore))
            }
        awaitClose { listener.remove() }
    }

    open suspend fun fetchNextVideosPage(
        pageSize: Long = 10L,
        lastVisible: DocumentSnapshot
    ): PaginatedResult<Video> {
        val snapshot = firestore.collection("videos")
            .whereEqualTo("visibility", "PUBLIC")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(lastVisible)
            .limit(pageSize)
            .get()
            .await()
        val docs = snapshot.documents
        val items = docs.mapNotNull { it.toObject(Video::class.java) }
        val lastDoc = docs.lastOrNull()
        val hasMore = docs.size >= pageSize
        return PaginatedResult(items, lastDoc, hasMore)
    }

    open fun observeVideos(): Flow<List<Video>> = observeVideosFirstPage(10L).map { it.items }

    open fun observeShortsFirstPage(pageSize: Long = 8L): Flow<PaginatedResult<ShortItem>> = callbackFlow {
        val listener = firestore.collection("shorts")
            .whereEqualTo("visibility", "PUBLIC")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.d("FirebaseService", "observeShortsFirstPage error: ${error.message}")
                    trySend(PaginatedResult(emptyList(), null, false))
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents ?: emptyList()
                val list = docs.mapNotNull { it.toObject(ShortItem::class.java) }
                val lastDoc = docs.lastOrNull()
                val hasMore = docs.size >= pageSize
                trySend(PaginatedResult(list, lastDoc, hasMore))
            }
        awaitClose { listener.remove() }
    }

    open suspend fun fetchNextShortsPage(
        pageSize: Long = 8L,
        lastVisible: DocumentSnapshot
    ): PaginatedResult<ShortItem> {
        val snapshot = firestore.collection("shorts")
            .whereEqualTo("visibility", "PUBLIC")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(lastVisible)
            .limit(pageSize)
            .get()
            .await()
        val docs = snapshot.documents
        val items = docs.mapNotNull { it.toObject(ShortItem::class.java) }
        val lastDoc = docs.lastOrNull()
        val hasMore = docs.size >= pageSize
        return PaginatedResult(items, lastDoc, hasMore)
    }

    open fun observeShorts(): Flow<List<ShortItem>> = observeShortsFirstPage(8L).map { it.items }

    open suspend fun searchVideosPaginated(
        queryText: String,
        pageSize: Long = 10L,
        lastVisible: DocumentSnapshot? = null
    ): PaginatedResult<Video> {
        val norm = SearchNormalizer.normalize(queryText)
        if (norm.isBlank()) return PaginatedResult(emptyList(), null, false)
        return try {
            if (lastVisible != null) {
                val q = firestore.collection("videos")
                    .whereEqualTo("visibility", "PUBLIC")
                    .orderBy("title")
                    .startAt(norm)
                    .endAt(norm + "\uf8ff")
                    .startAfter(lastVisible)
                    .limit(pageSize)
                val snapshot = q.get().await()
                val docs = snapshot.documents
                val items = docs.mapNotNull { it.toObject(Video::class.java) }
                    .filter { it.visibility == "PUBLIC" }
                PaginatedResult(items, docs.lastOrNull(), docs.size >= pageSize)
            } else {
                val titleCased = SearchNormalizer.toTitleCase(norm)
                val primaryQuery = firestore.collection("videos")
                    .whereEqualTo("visibility", "PUBLIC")
                    .orderBy("title")
                    .startAt(norm)
                    .endAt(norm + "\uf8ff")
                    .limit(pageSize)
                val primarySnapshot = primaryQuery.get().await()
                val primaryDocs = primarySnapshot.documents
                val resultsMap = LinkedHashMap<String, Video>()

                primaryDocs.mapNotNull { it.toObject(Video::class.java) }
                    .filter { it.visibility == "PUBLIC" }
                    .forEach { resultsMap[it.videoId] = it }

                if (titleCased != norm && resultsMap.size < pageSize) {
                    try {
                        val secondaryQuery = firestore.collection("videos")
                            .whereEqualTo("visibility", "PUBLIC")
                            .orderBy("title")
                            .startAt(titleCased)
                            .endAt(titleCased + "\uf8ff")
                            .limit(pageSize)
                        val secondaryDocs = secondaryQuery.get().await().documents
                        secondaryDocs.mapNotNull { it.toObject(Video::class.java) }
                            .filter { it.visibility == "PUBLIC" }
                            .forEach { resultsMap.putIfAbsent(it.videoId, it) }
                    } catch (e: Exception) {
                        Log.d("FirebaseService", "Secondary titlecase video search skipped: ${e.message}")
                    }
                }

                val sorted = resultsMap.values
                    .sortedWith(
                        compareByDescending<Video> { SearchNormalizer.calculateVideoRelevance(it, queryText) }
                            .thenByDescending { it.createdAt }
                    )
                    .take(pageSize.toInt())

                PaginatedResult(sorted, primaryDocs.lastOrNull(), primaryDocs.size >= pageSize)
            }
        } catch (e: Exception) {
            Log.d("FirebaseService", "searchVideosPaginated: ${e.message}")
            PaginatedResult(emptyList(), null, false)
        }
    }

    open suspend fun searchShortsPaginated(
        queryText: String,
        pageSize: Long = 10L,
        lastVisible: DocumentSnapshot? = null
    ): PaginatedResult<ShortItem> {
        val norm = SearchNormalizer.normalize(queryText)
        if (norm.isBlank()) return PaginatedResult(emptyList(), null, false)
        return try {
            if (lastVisible != null) {
                val q = firestore.collection("shorts")
                    .whereEqualTo("visibility", "PUBLIC")
                    .orderBy("title")
                    .startAt(norm)
                    .endAt(norm + "\uf8ff")
                    .startAfter(lastVisible)
                    .limit(pageSize)
                val snapshot = q.get().await()
                val docs = snapshot.documents
                val items = docs.mapNotNull { it.toObject(ShortItem::class.java) }
                    .filter { it.visibility == "PUBLIC" }
                PaginatedResult(items, docs.lastOrNull(), docs.size >= pageSize)
            } else {
                val titleCased = SearchNormalizer.toTitleCase(norm)
                val primaryQuery = firestore.collection("shorts")
                    .whereEqualTo("visibility", "PUBLIC")
                    .orderBy("title")
                    .startAt(norm)
                    .endAt(norm + "\uf8ff")
                    .limit(pageSize)
                val primarySnapshot = primaryQuery.get().await()
                val primaryDocs = primarySnapshot.documents
                val resultsMap = LinkedHashMap<String, ShortItem>()

                primaryDocs.mapNotNull { it.toObject(ShortItem::class.java) }
                    .filter { it.visibility == "PUBLIC" }
                    .forEach { resultsMap[it.shortId] = it }

                if (titleCased != norm && resultsMap.size < pageSize) {
                    try {
                        val secondaryQuery = firestore.collection("shorts")
                            .whereEqualTo("visibility", "PUBLIC")
                            .orderBy("title")
                            .startAt(titleCased)
                            .endAt(titleCased + "\uf8ff")
                            .limit(pageSize)
                        val secondaryDocs = secondaryQuery.get().await().documents
                        secondaryDocs.mapNotNull { it.toObject(ShortItem::class.java) }
                            .filter { it.visibility == "PUBLIC" }
                            .forEach { resultsMap.putIfAbsent(it.shortId, it) }
                    } catch (e: Exception) {
                        Log.d("FirebaseService", "Secondary titlecase shorts search skipped: ${e.message}")
                    }
                }

                val sorted = resultsMap.values
                    .sortedWith(
                        compareByDescending<ShortItem> { SearchNormalizer.calculateShortRelevance(it, queryText) }
                            .thenByDescending { it.createdAt }
                    )
                    .take(pageSize.toInt())

                PaginatedResult(sorted, primaryDocs.lastOrNull(), primaryDocs.size >= pageSize)
            }
        } catch (e: Exception) {
            Log.d("FirebaseService", "searchShortsPaginated: ${e.message}")
            PaginatedResult(emptyList(), null, false)
        }
    }

    open suspend fun searchChannelsPaginated(
        queryText: String,
        pageSize: Long = 10L,
        lastVisible: DocumentSnapshot? = null
    ): PaginatedResult<Channel> {
        val norm = SearchNormalizer.normalize(queryText).removePrefix("@")
        if (norm.isBlank()) return PaginatedResult(emptyList(), null, false)
        return try {
            if (lastVisible != null) {
                val q = firestore.collection("channels")
                    .orderBy("channelName")
                    .startAt(norm)
                    .endAt(norm + "\uf8ff")
                    .startAfter(lastVisible)
                    .limit(pageSize)
                val snapshot = q.get().await()
                val docs = snapshot.documents
                val items = docs.mapNotNull { it.toObject(Channel::class.java) }
                PaginatedResult(items, docs.lastOrNull(), docs.size >= pageSize)
            } else {
                val titleCased = SearchNormalizer.toTitleCase(norm)
                val primaryQuery = firestore.collection("channels")
                    .orderBy("channelName")
                    .startAt(norm)
                    .endAt(norm + "\uf8ff")
                    .limit(pageSize)
                val primarySnapshot = primaryQuery.get().await()
                val primaryDocs = primarySnapshot.documents
                val resultsMap = LinkedHashMap<String, Channel>()

                primaryDocs.mapNotNull { it.toObject(Channel::class.java) }
                    .forEach { resultsMap[it.channelId] = it }

                if (titleCased != norm && resultsMap.size < pageSize) {
                    try {
                        val secondaryQuery = firestore.collection("channels")
                            .orderBy("channelName")
                            .startAt(titleCased)
                            .endAt(titleCased + "\uf8ff")
                            .limit(pageSize)
                        val secondaryDocs = secondaryQuery.get().await().documents
                        secondaryDocs.mapNotNull { it.toObject(Channel::class.java) }
                            .forEach { resultsMap.putIfAbsent(it.channelId, it) }
                    } catch (e: Exception) {
                        Log.d("FirebaseService", "Secondary titlecase channel search skipped: ${e.message}")
                    }
                }

                if (resultsMap.size < pageSize) {
                    try {
                        val handleQuery = firestore.collection("channels")
                            .orderBy("handle")
                            .startAt(norm)
                            .endAt(norm + "\uf8ff")
                            .limit(pageSize)
                        val handleDocs = handleQuery.get().await().documents
                        handleDocs.mapNotNull { it.toObject(Channel::class.java) }
                            .forEach { resultsMap.putIfAbsent(it.channelId, it) }
                    } catch (e: Exception) {
                        Log.d("FirebaseService", "Handle search skipped: ${e.message}")
                    }
                }

                val sorted = resultsMap.values
                    .sortedWith(
                        compareByDescending<Channel> { SearchNormalizer.calculateChannelRelevance(it, queryText) }
                            .thenByDescending { it.subscriberCount }
                    )
                    .take(pageSize.toInt())

                PaginatedResult(sorted, primaryDocs.lastOrNull(), primaryDocs.size >= pageSize)
            }
        } catch (e: Exception) {
            Log.d("FirebaseService", "searchChannelsPaginated: ${e.message}")
            PaginatedResult(emptyList(), null, false)
        }
    }

    // ==================== LIKES & ENGAGEMENT ====================
    suspend fun likeContent(
        userId: String,
        contentId: String,
        contentType: String,
        targetAuthorUid: String? = null,
        targetTitle: String? = null,
        senderName: String = "Someone",
        senderAvatar: String = ""
    ): Boolean {
        return try {
            val likeKey = "${userId}_${contentId}"
            val likeRecord = LikeRecord(
                likeId = likeKey,
                userId = userId,
                contentId = contentId,
                contentType = contentType,
                isLike = true,
                createdAt = System.currentTimeMillis()
            )
            firestore.collection("likes").document(likeKey).set(likeRecord).await()

            val collectionName = if (contentType == "SHORT") "shorts" else "videos"
            firestore.collection(collectionName).document(contentId)
                .update("likeCount", FieldValue.increment(1))

            // Trigger notification for creator if not liking own content
            if (!targetAuthorUid.isNullOrBlank() && targetAuthorUid != userId) {
                val notifId = "notif_like_${System.currentTimeMillis()}"
                val notif = NotificationItem(
                    notificationId = notifId,
                    recipientUid = targetAuthorUid,
                    senderUid = userId,
                    senderName = senderName,
                    senderAvatarUrl = senderAvatar,
                    type = if (contentType == "SHORT") NotificationType.LIKE else NotificationType.VIDEO_LIKE,
                    title = "New Like! ❤️",
                    body = "$senderName liked your $contentType ${targetTitle?.let { "\"$it\"" } ?: ""}".trim(),
                    targetContentId = contentId,
                    targetContentType = contentType,
                    isRead = false,
                    createdAt = System.currentTimeMillis()
                )
                createNotification(notif)
            }
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Like content error: ${e.message}")
            false
        }
    }

    suspend fun unlikeContent(
        userId: String,
        contentId: String,
        contentType: String
    ): Boolean {
        return try {
            val likeKey = "${userId}_${contentId}"
            firestore.collection("likes").document(likeKey).delete().await()

            val collectionName = if (contentType == "SHORT") "shorts" else "videos"
            firestore.collection(collectionName).document(contentId)
                .update("likeCount", FieldValue.increment(-1))
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Unlike content error: ${e.message}")
            false
        }
    }

    suspend fun fetchUserLikedIds(userId: String, contentType: String): Set<String> {
        return try {
            val snapshot = firestore.collection("likes")
                .whereEqualTo("userId", userId)
                .whereEqualTo("contentType", contentType)
                .get()
                .await()
            snapshot.documents.mapNotNull { it.getString("contentId") }.toSet()
        } catch (e: Exception) {
            Log.d("FirebaseService", "Fetch likes error: ${e.message}")
            emptySet()
        }
    }

    // ==================== FOLLOWS ====================
    suspend fun followChannel(
        followerUid: String,
        followerName: String,
        followerAvatar: String,
        targetChannelId: String,
        targetOwnerUid: String? = null
    ): Boolean {
        return try {
            val followKey = "${followerUid}_${targetChannelId}"
            val record = FollowRecord(
                followId = followKey,
                followerUid = followerUid,
                targetChannelId = targetChannelId,
                createdAt = System.currentTimeMillis()
            )
            firestore.collection("follows").document(followKey).set(record).await()

            firestore.collection("channels").document(targetChannelId)
                .update("subscriberCount", FieldValue.increment(1))

            if (!targetOwnerUid.isNullOrBlank() && targetOwnerUid != followerUid) {
                val notif = NotificationItem(
                    notificationId = "notif_follow_${followerUid}_${targetChannelId}",
                    recipientUid = targetOwnerUid,
                    senderUid = followerUid,
                    senderName = followerName,
                    senderAvatarUrl = followerAvatar,
                    type = NotificationType.NEW_FOLLOWER,
                    title = "New Follower 👤",
                    body = "$followerName is now following your channel.",
                    targetContentId = targetChannelId,
                    targetContentType = "CHANNEL",
                    isRead = false,
                    createdAt = System.currentTimeMillis()
                )
                createNotification(notif)
            }
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Follow channel error: ${e.message}")
            false
        }
    }

    suspend fun unfollowChannel(followerUid: String, targetChannelId: String): Boolean {
        return try {
            val followKey = "${followerUid}_${targetChannelId}"
            firestore.collection("follows").document(followKey).delete().await()

            firestore.collection("channels").document(targetChannelId)
                .update("subscriberCount", FieldValue.increment(-1))
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Unfollow channel error: ${e.message}")
            false
        }
    }

    suspend fun fetchFollowedChannelIds(followerUid: String): Set<String> {
        return try {
            val snapshot = firestore.collection("follows")
                .whereEqualTo("followerUid", followerUid)
                .get()
                .await()
            snapshot.documents.mapNotNull { it.getString("targetChannelId") }.toSet()
        } catch (e: Exception) {
            Log.d("FirebaseService", "Fetch followed channels error: ${e.message}")
            emptySet()
        }
    }

    // ==================== COMMENTS ====================
    fun observeComments(contentId: String): Flow<List<CommentItem>> = callbackFlow {
        val listener = firestore.collection("comments")
            .whereEqualTo("contentId", contentId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(CommentItem::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addComment(comment: CommentItem, contentOwnerUid: String? = null, contentTitle: String? = null): Boolean {
        return try {
            firestore.collection("comments").document(comment.commentId).set(comment).await()
            val collectionName = if (comment.contentType == "SHORT") "shorts" else "videos"
            firestore.collection(collectionName).document(comment.contentId)
                .update("commentCount", FieldValue.increment(1))

            if (!contentOwnerUid.isNullOrBlank() && contentOwnerUid != comment.authorUid) {
                val notif = NotificationItem(
                    notificationId = "notif_cmt_${comment.commentId}",
                    recipientUid = contentOwnerUid,
                    senderUid = comment.authorUid,
                    senderName = comment.authorName,
                    senderAvatarUrl = comment.authorAvatarUrl,
                    type = NotificationType.COMMENT,
                    title = "New Comment 💬",
                    body = "${comment.authorName} commented: \"${comment.text.take(60)}\"",
                    targetContentId = comment.contentId,
                    targetContentType = comment.contentType,
                    isRead = false,
                    createdAt = System.currentTimeMillis()
                )
                createNotification(notif)
            }
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Add comment: ${e.message}")
            false
        }
    }

    suspend fun editComment(commentId: String, newText: String, currentUid: String): Boolean {
        return try {
            val docRef = firestore.collection("comments").document(commentId)
            val doc = docRef.get().await()
            val authorUid = doc.getString("authorUid")
            if (authorUid != currentUid && currentUid.isNotBlank()) {
                return false
            }
            docRef.update(
                mapOf(
                    "text" to newText,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Edit comment error: ${e.message}")
            false
        }
    }

    suspend fun addCommentReply(
        parentCommentId: String,
        contentId: String,
        reply: CommentReply,
        parentAuthorUid: String? = null
    ): Boolean {
        return try {
            val commentRef = firestore.collection("comments").document(parentCommentId)
            commentRef.update(
                "replies", FieldValue.arrayUnion(reply),
                "replyCount", FieldValue.increment(1)
            ).await()

            if (!parentAuthorUid.isNullOrBlank() && parentAuthorUid != reply.authorUid) {
                val notif = NotificationItem(
                    notificationId = "notif_rep_${parentCommentId}_${reply.replyId}",
                    recipientUid = parentAuthorUid,
                    senderUid = reply.authorUid,
                    senderName = reply.authorName,
                    senderAvatarUrl = reply.authorAvatarUrl,
                    type = NotificationType.REPLY,
                    title = "New Reply 💬",
                    body = "${reply.authorName} replied: \"${reply.text.take(60)}\"",
                    targetContentId = contentId,
                    targetContentType = "COMMENT",
                    isRead = false,
                    createdAt = System.currentTimeMillis()
                )
                createNotification(notif)
            }
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Add reply: ${e.message}")
            false
        }
    }

    suspend fun deleteComment(commentId: String, contentId: String, contentType: String): Boolean {
        return try {
            firestore.collection("comments").document(commentId).delete().await()
            val collectionName = if (contentType == "SHORT") "shorts" else "videos"
            firestore.collection(collectionName).document(contentId)
                .update("commentCount", FieldValue.increment(-1))
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Delete comment: ${e.message}")
            false
        }
    }

    suspend fun likeComment(
        userId: String,
        commentId: String,
        isLiked: Boolean,
        authorUid: String? = null,
        userName: String = "Someone",
        userAvatar: String = ""
    ): Boolean {
        return try {
            val delta = if (isLiked) 1L else -1L
            firestore.collection("comments").document(commentId)
                .update("likeCount", FieldValue.increment(delta)).await()

            if (isLiked && !authorUid.isNullOrBlank() && authorUid != userId) {
                val notif = NotificationItem(
                    notificationId = "notif_cmlike_${System.currentTimeMillis()}",
                    recipientUid = authorUid,
                    senderUid = userId,
                    senderName = userName,
                    senderAvatarUrl = userAvatar,
                    type = NotificationType.COMMENT_LIKE,
                    title = "Comment Liked ❤️",
                    body = "$userName liked your comment",
                    targetContentId = commentId,
                    targetContentType = "COMMENT",
                    isRead = false,
                    createdAt = System.currentTimeMillis()
                )
                createNotification(notif)
            }
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Like comment: ${e.message}")
            false
        }
    }

    // ==================== NOTIFICATIONS ====================
    fun observeNotifications(userId: String): Flow<List<NotificationItem>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = try {
            firestore.collection("notifications")
                .whereEqualTo("recipientUid", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("FirebaseService", "Observe notifications error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { it.toNotificationItem() }
                        ?.sortedByDescending { it.createdAt }
                        ?: emptyList()
                    trySend(list)
                }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Register notifications listener failed: ${e.message}")
            trySend(emptyList())
            null
        }
        awaitClose { listener?.remove() }
    }

    suspend fun createNotification(notification: NotificationItem): Boolean {
        if (notification.notificationId.isBlank() || notification.recipientUid.isBlank()) return false
        // Privacy safeguard: never notify sender of their own action
        if (notification.recipientUid == notification.senderUid) return false
        return try {
            firestore.collection("notifications").document(notification.notificationId)
                .set(notification.toMap(), SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Create notification error: ${e.message}")
            false
        }
    }

    suspend fun markNotificationAsRead(notificationId: String): Boolean {
        if (notificationId.isBlank()) return false
        return try {
            val now = System.currentTimeMillis()
            firestore.collection("notifications").document(notificationId)
                .update(
                    mapOf(
                        "isRead" to true,
                        "readAt" to now,
                        "updatedAt" to now
                    )
                ).await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Mark notification read error: ${e.message}")
            false
        }
    }

    suspend fun markAllNotificationsAsRead(userId: String): Boolean {
        if (userId.isBlank()) return false
        return try {
            val snapshot = firestore.collection("notifications")
                .whereEqualTo("recipientUid", userId)
                .whereEqualTo("isRead", false)
                .get().await()

            if (snapshot.isEmpty) return true

            val batch = firestore.batch()
            val now = System.currentTimeMillis()
            for (doc in snapshot.documents) {
                batch.update(
                    doc.reference,
                    mapOf(
                        "isRead" to true,
                        "readAt" to now,
                        "updatedAt" to now
                    )
                )
            }
            batch.commit().await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Mark all read error: ${e.message}")
            false
        }
    }

    suspend fun deleteNotification(notificationId: String): Boolean {
        if (notificationId.isBlank()) return false
        return try {
            firestore.collection("notifications").document(notificationId).delete().await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Delete notification error: ${e.message}")
            false
        }
    }

    suspend fun notifyFollowersLiveStarted(stream: LiveStream) {
        if (stream.channelId.isBlank() || stream.ownerUid.isBlank()) return
        try {
            val followersSnapshot = firestore.collection("follows")
                .whereEqualTo("targetChannelId", stream.channelId)
                .limit(50)
                .get()
                .await()

            val followerUids = followersSnapshot.documents.mapNotNull { it.getString("followerUid") }
                .filter { it.isNotBlank() && it != stream.ownerUid }
                .distinct()

            for (followerUid in followerUids) {
                val notifId = "notif_live_${stream.streamId}_${followerUid.take(8)}"
                val notif = NotificationItem(
                    notificationId = notifId,
                    recipientUid = followerUid,
                    senderUid = stream.ownerUid,
                    senderName = stream.channelName,
                    senderAvatarUrl = stream.channelAvatarUrl,
                    type = NotificationType.LIVE_STARTED,
                    title = "🔴 ${stream.channelName} is LIVE!",
                    body = stream.title.ifBlank { "Live broadcast started" },
                    targetContentId = stream.streamId,
                    targetContentType = "LIVE",
                    isRead = false,
                    createdAt = stream.startedAt
                )
                createNotification(notif)
            }
        } catch (e: Exception) {
            Log.d("FirebaseService", "Notify live started error: ${e.message}")
        }
    }

    // ==================== REPORTS & MODERATION ====================
    suspend fun submitReport(report: ReportItem): Boolean {
        return try {
            firestore.collection("reports").document(report.reportId).set(report).await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Submit report error: ${e.message}")
            false
        }
    }

    suspend fun reportContent(contentId: String, contentType: String, reason: String, reporterUid: String): Boolean {
        val report = ReportItem(
            reportId = "rep_${System.currentTimeMillis()}",
            reporterUid = reporterUid,
            targetId = contentId,
            targetType = contentType,
            reason = reason,
            details = "Reported by user $reporterUid",
            status = "PENDING",
            createdAt = System.currentTimeMillis()
        )
        return submitReport(report)
    }

    // ==================== BLOCKING ====================
    suspend fun blockUser(blockerUid: String, blockedUid: String, blockedUserName: String = ""): Boolean {
        return try {
            val blockDoc = BlockRecord(
                blockId = "${blockerUid}_${blockedUid}",
                blockerUid = blockerUid,
                blockedUid = blockedUid,
                blockedUserName = blockedUserName,
                createdAt = System.currentTimeMillis()
            )
            firestore.collection("blocks").document(blockDoc.blockId).set(blockDoc).await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Block user error: ${e.message}")
            false
        }
    }

    suspend fun unblockUser(blockerUid: String, blockedUid: String): Boolean {
        return try {
            firestore.collection("blocks").document("${blockerUid}_${blockedUid}").delete().await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Unblock user error: ${e.message}")
            false
        }
    }

    suspend fun fetchBlockedUserIds(blockerUid: String): Set<String> {
        return try {
            val snapshot = firestore.collection("blocks")
                .whereEqualTo("blockerUid", blockerUid)
                .get()
                .await()
            snapshot.documents.mapNotNull { it.getString("blockedUid") }.toSet()
        } catch (e: Exception) {
            Log.d("FirebaseService", "Fetch blocked users error: ${e.message}")
            emptySet()
        }
    }

    // ==================== LIVE STREAMING ====================
    fun observeActiveLiveStreams(): Flow<List<LiveStream>> = callbackFlow {
        val listener = firestore.collection("liveStreams")
            .whereEqualTo("isLive", true)
            .orderBy("startedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(LiveStream::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeLiveChat(streamId: String): Flow<List<LiveMessage>> = callbackFlow {
        val listener = firestore.collection("liveStreams").document(streamId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(LiveMessage::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendLiveMessage(message: LiveMessage): Boolean {
        return try {
            firestore.collection("liveStreams").document(message.streamId)
                .collection("messages").document(message.messageId).set(message).await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Send live message: ${e.message}")
            false
        }
    }

    suspend fun createLiveStreamRoom(stream: LiveStream): Boolean {
        return try {
            firestore.collection("liveStreams").document(stream.streamId).set(stream).await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Create live stream room error: ${e.message}")
            false
        }
    }

    suspend fun updateLiveStreamEnded(streamId: String, endedAt: Long, durationSeconds: Long): Boolean {
        return try {
            firestore.collection("liveStreams").document(streamId).update(
                mapOf(
                    "isLive" to false,
                    "status" to "ENDED",
                    "endedAt" to endedAt,
                    "durationSeconds" to durationSeconds
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Update live stream ended error: ${e.message}")
            false
        }
    }

    // ==================== CHAT ====================
    fun observeUserConversations(uid: String): Flow<List<ChatConversation>> = callbackFlow {
        val listener = firestore.collection("conversations")
            .whereArrayContains("participantUids", uid)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(ChatConversation::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = firestore.collection("conversations").document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(ChatMessage::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendChatMessage(message: ChatMessage): Boolean {
        return try {
            firestore.collection("conversations").document(message.conversationId)
                .collection("messages").document(message.messageId).set(message).await()
            firestore.collection("conversations").document(message.conversationId)
                .update(
                    mapOf(
                        "lastMessageText" to message.text,
                        "lastMessageSenderUid" to message.senderUid,
                        "lastMessageTimestamp" to message.timestamp
                    )
                )
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Send chat message: ${e.message}")
            false
        }
    }

    // ==================== WALLET & LEDGER ====================
    fun observeWallet(channelId: String): Flow<Wallet?> = callbackFlow {
        val listener = firestore.collection("wallets").document("wallet_$channelId")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot.toObject(Wallet::class.java))
            }
        awaitClose { listener.remove() }
    }

    fun observeLedger(channelId: String): Flow<List<LedgerEntry>> = callbackFlow {
        val listener = firestore.collection("ledgerEntries")
            .whereEqualTo("channelId", channelId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(LedgerEntry::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // ==================== ADMIN & AUDIT ====================
    suspend fun logAdminAction(log: AdminAuditLog): Boolean {
        return try {
            kotlinx.coroutines.withTimeoutOrNull(3000L) {
                firestore.collection("adminAuditLogs").document(log.logId).set(log).await()
            } != null
        } catch (e: Exception) {
            Log.d("FirebaseService", "Log admin action: ${e.message}")
            false
        }
    }

    // ==================== USER WATCH HISTORY SYNC ====================
    suspend fun syncWatchHistoryItem(userId: String, item: WatchHistoryItem): Boolean {
        if (userId.isBlank() || item.historyId.isBlank() || item.contentId.isBlank()) return false
        return try {
            val payload = hashMapOf<String, Any>(
                "historyId" to item.historyId,
                "userId" to userId,
                "contentType" to item.contentType,
                "contentId" to item.contentId,
                "watchedAt" to item.watchedAt,
                "progressMs" to item.progressMs,
                "durationMs" to item.durationMs,
                "completed" to item.completed,
                "updatedAt" to System.currentTimeMillis(),
                "title" to item.title,
                "channelName" to item.channelName,
                "thumbnailUrl" to item.thumbnailUrl,
                "videoUrl" to item.videoUrl
            )
            firestore.collection("users").document(userId)
                .collection("watchHistory").document(item.historyId)
                .set(payload, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "syncWatchHistoryItem failed: ${e.message}")
            false
        }
    }

    suspend fun fetchWatchHistory(
        userId: String,
        pageSize: Long = 50,
        lastSnapshot: DocumentSnapshot? = null
    ): PaginatedResult<WatchHistoryItem> {
        if (userId.isBlank()) return PaginatedResult(emptyList())
        return try {
            var query = firestore.collection("users").document(userId)
                .collection("watchHistory")
                .orderBy("watchedAt", Query.Direction.DESCENDING)
                .limit(pageSize)

            if (lastSnapshot != null) {
                query = query.startAfter(lastSnapshot)
            }

            val snapshot = query.get().await()
            val items = snapshot.documents.mapNotNull { doc ->
                val historyId = doc.getString("historyId") ?: doc.id
                val contentId = doc.getString("contentId") ?: return@mapNotNull null
                val contentType = doc.getString("contentType") ?: "VIDEO"
                WatchHistoryItem(
                    historyId = historyId,
                    userId = doc.getString("userId") ?: userId,
                    contentType = contentType,
                    contentId = contentId,
                    watchedAt = doc.getLong("watchedAt") ?: 0L,
                    progressMs = doc.getLong("progressMs") ?: 0L,
                    durationMs = doc.getLong("durationMs") ?: 0L,
                    completed = doc.getBoolean("completed") ?: false,
                    updatedAt = doc.getLong("updatedAt") ?: 0L,
                    title = doc.getString("title") ?: "",
                    channelName = doc.getString("channelName") ?: "",
                    thumbnailUrl = doc.getString("thumbnailUrl") ?: "",
                    videoUrl = doc.getString("videoUrl") ?: "",
                    syncStatus = HistorySyncStatus.SYNCED
                )
            }
            val lastDoc = snapshot.documents.lastOrNull()
            PaginatedResult(
                items = items,
                lastSnapshot = lastDoc,
                hasMore = items.size.toLong() == pageSize
            )
        } catch (e: Exception) {
            Log.d("FirebaseService", "fetchWatchHistory failed: ${e.message}")
            PaginatedResult(emptyList())
        }
    }

    suspend fun deleteWatchHistoryItem(userId: String, historyId: String): Boolean {
        if (userId.isBlank() || historyId.isBlank()) return false
        return try {
            firestore.collection("users").document(userId)
                .collection("watchHistory").document(historyId)
                .delete().await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "deleteWatchHistoryItem failed: ${e.message}")
            false
        }
    }

    suspend fun clearWatchHistory(userId: String): Boolean {
        if (userId.isBlank()) return false
        return try {
            val snapshot = firestore.collection("users").document(userId)
                .collection("watchHistory")
                .limit(100)
                .get().await()

            if (snapshot.isEmpty) return true

            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "clearWatchHistory failed: ${e.message}")
            false
        }
    }

    // ==================== FIREBASE STORAGE (DISABLED ON SPARK) ====================
    suspend fun uploadUserAvatar(userId: String, bytes: ByteArray, mimeType: String = "image/jpeg"): String? {
        Log.d("FirebaseService", "Storage upload disabled on Spark plan")
        return null
    }

    suspend fun uploadChannelAvatar(channelId: String, bytes: ByteArray, mimeType: String = "image/jpeg"): String? {
        Log.d("FirebaseService", "Storage upload disabled on Spark plan")
        return null
    }

    suspend fun uploadChannelBanner(channelId: String, bytes: ByteArray, mimeType: String = "image/jpeg"): String? {
        Log.d("FirebaseService", "Storage upload disabled on Spark plan")
        return null
    }

    suspend fun uploadThumbnail(channelId: String, fileName: String, bytes: ByteArray, mimeType: String = "image/jpeg"): String? {
        Log.d("FirebaseService", "Storage upload disabled on Spark plan")
        return null
    }

    suspend fun uploadVideoMedia(channelId: String, videoId: String, bytes: ByteArray, mimeType: String = "video/mp4"): String? {
        Log.d("FirebaseService", "Storage upload disabled on Spark plan")
        return null
    }

    suspend fun uploadChatAttachment(conversationId: String, fileName: String, bytes: ByteArray, mimeType: String): String? {
        Log.d("FirebaseService", "Storage upload disabled on Spark plan")
        return null
    }

    // ==================== FIREBASE ANALYTICS ====================
    fun logEvent(eventName: String, params: Bundle = Bundle()) {
        try {
            analytics?.logEvent(eventName, params)
        } catch (e: Exception) {
            Log.d("FirebaseService", "Analytics event log: ${e.message}")
        }
    }

    fun logVideoView(videoId: String, channelId: String, durationSec: Long = 0) {
        val bundle = Bundle().apply {
            putString("video_id", videoId)
            putString("channel_id", channelId)
            putLong("duration_seconds", durationSec)
        }
        logEvent("iombg_video_view", bundle)
    }

    fun logShortView(shortId: String, channelId: String) {
        val bundle = Bundle().apply {
            putString("short_id", shortId)
            putString("channel_id", channelId)
        }
        logEvent("iombg_short_view", bundle)
    }

    fun logSubscribe(channelId: String) {
        val bundle = Bundle().apply {
            putString("channel_id", channelId)
        }
        logEvent("iombg_channel_subscribe", bundle)
    }

    fun logTip(channelId: String, amountInr: Double) {
        val bundle = Bundle().apply {
            putString("channel_id", channelId)
            putDouble("value", amountInr)
            putString("currency", "INR")
        }
        logEvent("iombg_creator_tip", bundle)
    }

    fun logSearch(query: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SEARCH_TERM, query)
        }
        logEvent(FirebaseAnalytics.Event.SEARCH, bundle)
    }

    fun setUserProperties(accountType: String, isCreator: Boolean, isPremium: Boolean) {
        try {
            analytics?.setUserProperty("account_type", accountType)
            analytics?.setUserProperty("is_creator", isCreator.toString())
            analytics?.setUserProperty("is_premium", isPremium.toString())
        } catch (e: Exception) {
            Log.d("FirebaseService", "User properties set error: ${e.message}")
        }
    }
}

fun DocumentSnapshot.toNotificationItem(): NotificationItem? {
    if (!exists()) return null
    val id = id.ifBlank { getString("notificationId") ?: "" }
    val typeStr = getString("type") ?: "SYSTEM_ALERT"
    val notifType = try {
        NotificationType.valueOf(typeStr)
    } catch (e: Exception) {
        NotificationType.SYSTEM_ALERT
    }
    return NotificationItem(
        notificationId = id,
        recipientUid = getString("recipientUid") ?: "",
        senderUid = getString("senderUid") ?: "",
        senderName = getString("senderName") ?: "",
        senderAvatarUrl = getString("senderAvatarUrl") ?: "",
        type = notifType,
        title = getString("title") ?: "",
        body = getString("body") ?: "",
        targetContentId = getString("targetContentId"),
        targetContentType = getString("targetContentType"),
        isRead = getBoolean("isRead") ?: false,
        createdAt = getLong("createdAt") ?: System.currentTimeMillis()
    )
}

fun NotificationItem.toMap(): Map<String, Any?> = mapOf(
    "notificationId" to notificationId,
    "recipientUid" to recipientUid,
    "senderUid" to senderUid,
    "senderName" to senderName,
    "senderAvatarUrl" to senderAvatarUrl,
    "type" to type.name,
    "title" to title,
    "body" to body,
    "targetContentId" to targetContentId,
    "targetContentType" to targetContentType,
    "isRead" to isRead,
    "createdAt" to createdAt
)

data class PaginatedResult<T>(
    val items: List<T>,
    val lastSnapshot: DocumentSnapshot? = null,
    val hasMore: Boolean = false
)
