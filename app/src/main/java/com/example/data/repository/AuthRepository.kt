package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.data.model.UserAccount
import com.example.data.remote.FirebaseService
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.notifications.FcmTokenManager

class AuthRepository(
    private val context: Context,
    private val firebaseService: FirebaseService,
    private val isDebug: Boolean = com.example.BuildConfig.DEBUG,
    private val fcmTokenManager: FcmTokenManager = FcmTokenManager.getInstance(context)
) {
    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.d("AuthRepository", "FirebaseAuth fallback: ${e.message}")
            null
        }
    }
    private val credentialManager by lazy {
        try {
            CredentialManager.create(context)
        } catch (e: Exception) {
            Log.d("AuthRepository", "CredentialManager fallback: ${e.message}")
            null
        }
    }

    private val _currentUserState = MutableStateFlow<UserAccount?>(null)
    val currentUserState: StateFlow<UserAccount?> = _currentUserState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _showWelcomeOnboarding = MutableStateFlow(false)
    val showWelcomeOnboarding: StateFlow<Boolean> = _showWelcomeOnboarding.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        try {
            val user = auth?.currentUser
            if (user != null) {
                val uid = user.uid
                scope.launch {
                    loadUserProfile(uid)
                }
            } else {
                _currentUserState.value = null
            }
            auth?.addAuthStateListener { firebaseAuth ->
                val fbUser = firebaseAuth.currentUser
                if (fbUser != null) {
                    if (_currentUserState.value?.uid != fbUser.uid) {
                        scope.launch {
                            loadUserProfile(fbUser.uid)
                        }
                    }
                } else {
                    if (!isDebug) {
                        _currentUserState.value = null
                    } else {
                        val currentUid = _currentUserState.value?.uid
                        if (currentUid != null && currentUid != "super_admin_01" && currentUid != "creator_demo_01" && currentUid != "viewer_demo_01") {
                            _currentUserState.value = null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("AuthRepository", "Firebase auth initialization: ${e.message}")
        }
    }

    fun clearError() {
        _authError.value = null
    }

    fun dismissWelcomeOnboarding() {
        _showWelcomeOnboarding.value = false
    }

    fun triggerWelcomeOnboarding() {
        _showWelcomeOnboarding.value = true
    }

    suspend fun loadUserProfile(uid: String): UserAccount? {
        if (uid.isBlank()) return null
        _isLoading.value = true
        return try {
            val profile = firebaseService.getUserProfile(uid)
            if (profile != null) {
                _currentUserState.value = profile
                fcmTokenManager.registerTokenForUser(uid)
                profile
            } else {
                // Auto create viewer profile on first login
                val fbUser = auth?.currentUser
                val email = fbUser?.email ?: ""
                val cleanUsername = if (email.isNotBlank()) {
                    email.substringBefore("@").lowercase().replace(Regex("[^a-z0-9_]"), "_")
                } else {
                    "user_${uid.take(6).lowercase()}"
                }

                // New accounts are always initialized with default VIEWER role.
                // Super Admin role can ONLY be granted via authoritative Firestore role assignment.
                val newProfile = UserAccount(
                    uid = uid,
                    displayName = fbUser?.displayName ?: "IOMBG Explorer",
                    email = email,
                    phoneNumber = fbUser?.phoneNumber ?: "",
                    photoUrl = fbUser?.photoUrl?.toString() ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400",
                    username = cleanUsername,
                    accountType = "VIEWER",
                    role = "VIEWER",
                    isSuperAdmin = false,
                    isVerified = false,
                    channelId = null,
                    isPremium = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                firebaseService.saveUserProfile(newProfile)
                _currentUserState.value = newProfile
                fcmTokenManager.registerTokenForUser(uid)
                _showWelcomeOnboarding.value = true
                newProfile
            }
        } catch (e: Exception) {
            Log.d("AuthRepository", "Failed to load user profile: ${e.message}")
            null
        } finally {
            _isLoading.value = false
        }
    }

    // Google Sign-In via Credential Manager
    suspend fun signInWithGoogle(activity: Activity, webClientId: String): Boolean {
        _isLoading.value = true
        _authError.value = null
        return try {
            val cm = credentialManager
            val fbAuth = auth
            if (cm == null || fbAuth == null) {
                _authError.value = "Authentication services are unavailable on this device."
                return false
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = cm.getCredential(activity, request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = fbAuth.signInWithCredential(authCredential).await()
                val user = authResult.user
                if (user != null) {
                    loadUserProfile(user.uid)
                    true
                } else {
                    _authError.value = "Firebase authentication returned no user."
                    false
                }
            } else {
                _authError.value = "Invalid Google credentials received."
                false
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d("AuthRepository", "Google Sign-in was cancelled by user: ${e.message}")
            _authError.value = "Sign in was cancelled"
            false
        } catch (e: GetCredentialException) {
            Log.d("AuthRepository", "Google Sign-In credential exception: ${e.message}")
            _authError.value = e.localizedMessage ?: "Google Sign-In failed"
            false
        } catch (e: Exception) {
            Log.d("AuthRepository", "Google Sign In failed: ${e.message}")
            _authError.value = e.localizedMessage ?: "Failed to sign in with Google"
            false
        } finally {
            _isLoading.value = false
        }
    }

    // Sign in with Phone Auth Credential (kept modular for future OTP roadmap)
    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): Boolean {
        _isLoading.value = true
        _authError.value = null
        return try {
            val fbAuth = auth
            if (fbAuth != null) {
                val result = fbAuth.signInWithCredential(credential).await()
                val user = result.user
                if (user != null) {
                    loadUserProfile(user.uid)
                    true
                } else {
                    _authError.value = "Phone authentication returned no user."
                    false
                }
            } else {
                _authError.value = "Authentication service unavailable"
                false
            }
        } catch (e: Exception) {
            _authError.value = e.localizedMessage ?: "Invalid OTP or phone verification failed"
            false
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun linkChannelToUser(channelId: String): Boolean {
        val current = _currentUserState.value ?: return false
        val updated = current.copy(
            channelId = channelId,
            accountType = "CREATOR",
            updatedAt = System.currentTimeMillis()
        )
        return updateProfile(updated)
    }

    suspend fun updateProfile(updated: UserAccount): Boolean {
        _isLoading.value = true
        return try {
            val current = _currentUserState.value
            // Security Enforcement: Prevent client-side self-assignment of SUPER_ADMIN
            if (updated.isSuperAdmin && current?.isSuperAdmin != true) {
                Log.w("AuthRepository", "Unauthorized privilege escalation rejected: cannot self-assign isSuperAdmin")
                _authError.value = "Unauthorized: Super Admin role cannot be assigned client-side"
                return false
            }
            if (updated.accountType == "SUPER_ADMIN" && current?.accountType != "SUPER_ADMIN") {
                Log.w("AuthRepository", "Unauthorized privilege escalation rejected: cannot self-assign accountType=SUPER_ADMIN")
                _authError.value = "Unauthorized: Super Admin role cannot be assigned client-side"
                return false
            }

            val success = firebaseService.saveUserProfile(updated)
            if (success) {
                _currentUserState.value = updated
            }
            success
        } catch (e: Exception) {
            _authError.value = e.localizedMessage
            false
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun signOut() {
        val currentUid = _currentUserState.value?.uid
        fcmTokenManager.unregisterTokenOnLogout(currentUid)
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.d("AuthRepository", "Sign out: ${e.message}")
        }
        try {
            kotlinx.coroutines.withTimeoutOrNull(1000L) {
                credentialManager?.clearCredentialState(ClearCredentialStateRequest())
            }
        } catch (e: Exception) {
            Log.d("AuthRepository", "Error clearing credentials: ${e.message}")
        }
        _currentUserState.value = null
        _showWelcomeOnboarding.value = false
        _authError.value = null
    }

    suspend fun requestAccountDeletion(reason: String = "User requested deletion via in-app settings"): Result<Boolean> {
        val user = _currentUserState.value ?: return Result.failure(IllegalStateException("No authenticated user"))
        return try {
            val success = firebaseService.requestAccountDeletion(user.uid, reason)
            if (success) {
                signOut()
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to delete account on server"))
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "requestAccountDeletion failed: ${e.message}")
            Result.failure(e)
        }
    }

    // Set demo user for immediate test preview (strictly DEBUG/development testing only)
    fun setDemoSession(accountType: String = "VIEWER", isSuperAdmin: Boolean = false, showWelcome: Boolean = false): Boolean {
        if (!isDebug) {
            Log.w("AuthRepository", "Rejected setDemoSession: Demo sessions are strictly disabled in release builds")
            return false
        }
        if (auth?.currentUser != null) {
            Log.w("AuthRepository", "Cannot activate demo session while a real Firebase user is authenticated (${auth?.currentUser?.uid})")
            return false
        }
        val isCreator = accountType == "CREATOR"
        val isSuperAdminRole = isSuperAdmin || accountType == "SUPER_ADMIN"
        val demoUid = when {
            isSuperAdminRole -> "super_admin_01"
            isCreator -> "creator_demo_01"
            else -> "viewer_demo_01"
        }
        val demoUser = UserAccount(
            uid = demoUid,
            displayName = when {
                isSuperAdminRole -> "IOMBG Super Admin"
                isCreator -> "Alex Rivera"
                else -> "Devin Vance"
            },
            email = when {
                isSuperAdminRole -> "demo.admin@iombg.test"
                isCreator -> "alex.creator@iombg.com"
                else -> "devin.vance@gmail.com"
            },
            phoneNumber = "+1 555-0199",
            photoUrl = if (isCreator) "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400" else "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400",
            username = when {
                isSuperAdminRole -> "admin"
                isCreator -> "alexrivera"
                else -> "devinvance"
            },
            accountType = accountType,
            isSuperAdmin = isSuperAdmin,
            isVerified = isCreator || isSuperAdminRole,
            channelId = if (isCreator) "ch_alexrivera" else null,
            isPremium = isSuperAdminRole || isCreator,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        _currentUserState.value = demoUser
        _showWelcomeOnboarding.value = showWelcome
        return true
    }
}

