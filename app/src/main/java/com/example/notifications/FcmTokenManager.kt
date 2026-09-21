package com.example.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/**
 * P1-1 FCM Token Registration and Lifecycle Management
 *
 * Enforces:
 * - Scoped strictly to authenticated Firebase UID
 * - Never registers demo users or unauthenticated state
 * - Updates on token refresh
 * - Removes / invalidates user token on logout to prevent state leakage
 * - Stores minimal metadata in users/{uid}/notificationTokens/{tokenId}
 */
class FcmTokenManager(
    private val context: Context,
    private val firestoreProvider: () -> FirebaseFirestore? = {
        try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
    },
    private val authProvider: () -> FirebaseAuth? = {
        try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
    }
) {
    companion object {
        private const val TAG = "FcmTokenManager"
        val DEMO_UIDS = setOf("super_admin_01", "creator_demo_01", "viewer_demo_01")

        @Volatile
        private var instance: FcmTokenManager? = null

        fun getInstance(context: Context): FcmTokenManager {
            return instance ?: synchronized(this) {
                instance ?: FcmTokenManager(context.applicationContext).also { instance = it }
            }
        }

        fun setInstanceForTesting(manager: FcmTokenManager?) {
            instance = manager
        }

        /**
         * Generates deterministic SHA-256 derived ID for a token document.
         */
        fun generateDeterministicTokenId(token: String): String {
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                val bytes = digest.digest(token.toByteArray(Charsets.UTF_8))
                bytes.joinToString("") { "%02x".format(it) }.take(32)
            } catch (e: Exception) {
                token.takeLast(32)
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    var currentRegisteredUid: String? = null
        private set
    var currentCachedToken: String? = null
        private set

    /**
     * Register device FCM token for an authenticated user.
     */
    fun registerTokenForUser(uid: String) {
        if (uid.isBlank() || uid in DEMO_UIDS) {
            Log.d(TAG, "Skipping token registration for demo/invalid UID: $uid")
            return
        }

        // Verify Firebase Auth state matches UID to prevent impersonation
        val currentAuth = authProvider()?.currentUser
        if (currentAuth == null || currentAuth.uid != uid) {
            Log.w(TAG, "Registration skipped: auth UID mismatch ($uid vs ${currentAuth?.uid})")
            return
        }

        scope.launch {
            try {
                val token = try {
                    FirebaseMessaging.getInstance().token.await()
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to retrieve FCM token: ${e.message}")
                    null
                }

                if (!token.isNullOrBlank()) {
                    currentCachedToken = token
                    currentRegisteredUid = uid
                    persistTokenToFirestore(uid, token)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error during token registration: ${e.message}")
            }
        }
    }

    /**
     * Handle FCM token refresh. Updates Firestore for the currently authenticated real user.
     */
    fun onNewToken(newToken: String) {
        if (newToken.isBlank()) return
        currentCachedToken = newToken
        val authUid = authProvider()?.currentUser?.uid ?: currentRegisteredUid
        if (authUid != null && authUid !in DEMO_UIDS) {
            scope.launch {
                try {
                    persistTokenToFirestore(authUid, newToken)
                } catch (e: Exception) {
                    Log.d(TAG, "Error updating refreshed token: ${e.message}")
                }
            }
        }
    }

    /**
     * Persist device token to users/{uid}/notificationTokens/{tokenId}.
     */
    suspend fun persistTokenToFirestore(uid: String, token: String): Boolean {
        if (uid.isBlank() || uid in DEMO_UIDS || token.isBlank()) return false
        val firestore = firestoreProvider() ?: return false

        val tokenId = generateDeterministicTokenId(token)
        val tokenRecord = DeviceNotificationToken(
            tokenId = tokenId,
            token = token,
            platform = "android",
            appVersion = "1.0",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        return try {
            firestore.collection("users")
                .document(uid)
                .collection("notificationTokens")
                .document(tokenId)
                .set(tokenRecord)
                .await()
            Log.d(TAG, "Successfully registered FCM token for user $uid")
            true
        } catch (e: Exception) {
            Log.d(TAG, "Failed to persist token to Firestore: ${e.message}")
            false
        }
    }

    /**
     * Unregister token on logout to ensure strict user/session isolation.
     */
    fun unregisterTokenOnLogout(uid: String?) {
        val targetUid = uid ?: currentRegisteredUid
        val token = currentCachedToken

        currentRegisteredUid = null
        currentCachedToken = null

        if (targetUid.isNullOrBlank() || targetUid in DEMO_UIDS || token.isNullOrBlank()) {
            return
        }

        scope.launch {
            try {
                val firestore = firestoreProvider()
                if (firestore != null) {
                    val tokenId = generateDeterministicTokenId(token)
                    firestore.collection("users")
                        .document(targetUid)
                        .collection("notificationTokens")
                        .document(tokenId)
                        .delete()
                        .await()
                    Log.d(TAG, "Successfully removed token document for $targetUid")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Token unregister on logout: ${e.message}")
            }
        }
    }
}
