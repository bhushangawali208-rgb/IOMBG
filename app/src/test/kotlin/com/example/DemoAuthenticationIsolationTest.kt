package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.FraudAlert
import com.example.data.model.UserAccount
import com.example.data.remote.FirebaseService
import com.example.data.remote.IombgBackendService
import com.example.data.repository.AdminRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChannelRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1 AUTHENTICATION CLEANUP & PRODUCTION DEMO ISOLATION TEST SUITE
 *
 * Verifies:
 * 1. DEBUG build can access demo login.
 * 2. RELEASE build cannot access demo login.
 * 3. RELEASE build cannot create demo sessions.
 * 4. RELEASE build cannot create demo channels.
 * 5. Firebase authentication remains available and loads real user profile.
 * 6. Firebase authentication failure does NOT silently create a demo user.
 * 7. Logout clears all demo and real session state.
 * 8. Real Firebase login cannot inherit demo profile data.
 * 9. Demo Super Admin cannot bypass production Super Admin authorization.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoAuthenticationIsolationTest {

    private lateinit var context: Context
    private lateinit var fakeFirebaseService: FakeTestFirebaseService
    private lateinit var fakeBackendService: FakeTestBackendService
    private lateinit var adminRepository: AdminRepository

    class FakeTestFirebaseService : FirebaseService() {
        val userProfiles = mutableMapOf<String, UserAccount>()

        override suspend fun getUserProfile(uid: String): UserAccount? {
            return userProfiles[uid]
        }

        override suspend fun saveUserProfile(user: UserAccount): Boolean {
            userProfiles[user.uid] = user
            return true
        }
    }

    class FakeTestBackendService : IombgBackendService {
        override suspend fun getSuperAdminAuthorizationStatus(): Result<Boolean> = Result.success(false)
        override suspend fun verifyAndCreditPayment(
            userId: String, channelId: String, amountInr: Double,
            paymentGatewayTxnId: String, paymentType: String, signature: String
        ): Result<Boolean> = Result.success(true)
        override suspend fun requestCreatorPayout(
            channelId: String, amount: Double, paymentMethod: String,
            upiId: String, bankAccount: String, bankIfsc: String, accountHolderName: String
        ): Result<String> = Result.success("payout_123")
        override suspend fun calculateMonetizationEligibility(channelId: String): Result<Map<String, Any>> =
            Result.success(mapOf("isEligible" to true))
        override suspend fun submitMonetizationApplication(
            channelId: String, legalName: String, panNumber: String
        ): Result<String> = Result.success("app_123")
        override suspend fun ingestAnalyticsBatch(events: List<Map<String, Any>>): Result<Int> = Result.success(events.size)
        override suspend fun adminExecuteAction(action: String, targetId: String, parameters: Map<String, Any>): Result<Boolean> = Result.success(true)
        override suspend fun executePayoutSettlement(payoutId: String, creatorUid: String, amount: Double, adminUid: String): Result<String> = Result.success("UPI/2026/TEST")
        override suspend fun triggerVideoTranscoding(videoId: String, rawStoragePath: String): Result<Boolean> = Result.success(true)
        override suspend fun evaluateFraudRisk(targetUid: String, actionType: String, metadata: Map<String, String>): Result<FraudAlert?> = Result.success(null)
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        fakeFirebaseService = FakeTestFirebaseService()
        fakeBackendService = FakeTestBackendService()
        adminRepository = AdminRepository(fakeFirebaseService, fakeBackendService)
    }

    @Test
    fun testDebugBuild_canCreateDemoSession() {
        val debugAuthRepo = AuthRepository(context, fakeFirebaseService, isDebug = true)
        val success = debugAuthRepo.setDemoSession("VIEWER", isSuperAdmin = false)

        assertTrue("Debug build must allow demo session creation", success)
        assertNotNull("Debug session user must not be null", debugAuthRepo.currentUserState.value)
        assertEquals("viewer_demo_01", debugAuthRepo.currentUserState.value?.uid)
        assertEquals("devinvance", debugAuthRepo.currentUserState.value?.username)
    }

    @Test
    fun testReleaseBuild_cannotAccessDemoLogin_orCreateDemoSession() {
        val releaseAuthRepo = AuthRepository(context, fakeFirebaseService, isDebug = false)

        // Attempting viewer demo session in release build
        val viewerSuccess = releaseAuthRepo.setDemoSession("VIEWER", isSuperAdmin = false)
        assertFalse("Release build must reject viewer demo session creation", viewerSuccess)
        assertNull("Release currentUserState must remain null", releaseAuthRepo.currentUserState.value)

        // Attempting creator demo session in release build
        val creatorSuccess = releaseAuthRepo.setDemoSession("CREATOR", isSuperAdmin = false)
        assertFalse("Release build must reject creator demo session creation", creatorSuccess)
        assertNull("Release currentUserState must remain null", releaseAuthRepo.currentUserState.value)

        // Attempting super admin demo session in release build
        val adminSuccess = releaseAuthRepo.setDemoSession("SUPER_ADMIN", isSuperAdmin = false)
        assertFalse("Release build must reject admin demo session creation", adminSuccess)
        assertNull("Release currentUserState must remain null", releaseAuthRepo.currentUserState.value)
    }

    @Test
    fun testReleaseBuild_rejectsSetDemoChannel() {
        val releaseChannelRepo = ChannelRepository(fakeFirebaseService, isDebug = false)
        val result = releaseChannelRepo.setDemoChannel()

        assertFalse("Release build must reject demo channel assignment", result)
        assertNull("Release currentChannel must remain null", releaseChannelRepo.currentChannel.value)

        val debugChannelRepo = ChannelRepository(fakeFirebaseService, isDebug = true)
        val debugResult = debugChannelRepo.setDemoChannel()
        assertTrue("Debug build allows demo channel assignment", debugResult)
        assertNotNull("Debug channel must be set", debugChannelRepo.currentChannel.value)
        assertEquals("ch_alexrivera", debugChannelRepo.currentChannel.value?.channelId)
    }

    @Test
    fun testFirebaseAuthentication_remainsAvailable_andLoadsRealProfile() = runBlocking {
        val releaseAuthRepo = AuthRepository(context, fakeFirebaseService, isDebug = false)
        val realUid = "firebase_prod_user_4455"

        val profile = releaseAuthRepo.loadUserProfile(realUid)

        assertNotNull("Real Firebase user profile loading must succeed", profile)
        assertEquals("Real profile UID must match Firebase UID", realUid, profile?.uid)
        assertEquals("Real profile default role must be VIEWER", "VIEWER", profile?.role)
        assertFalse("Real profile must not have isSuperAdmin", profile?.isSuperAdmin ?: true)
        assertNull("Real profile must have null channelId by default", profile?.channelId)
        assertEquals("currentUserState must match real profile", realUid, releaseAuthRepo.currentUserState.value?.uid)
    }

    @Test
    fun testFirebaseAuthFailure_doesNotCreateDemoUser() = runBlocking {
        val releaseAuthRepo = AuthRepository(context, fakeFirebaseService, isDebug = false)
        // With no Firebase service or failed credential verification, authentication fails
        // We verify that currentUserState remains null and no demo user is created
        assertNull("Initial state must be unauthenticated", releaseAuthRepo.currentUserState.value)

        // If loadUserProfile fails or user is null:
        val result = releaseAuthRepo.loadUserProfile("")
        assertNull("Invalid or failed auth profile must be null", result)
        assertNull("Failed auth must never populate currentUserState", releaseAuthRepo.currentUserState.value)
        assertNotEquals("viewer_demo_01", releaseAuthRepo.currentUserState.value?.uid)
        assertNotEquals("creator_demo_01", releaseAuthRepo.currentUserState.value?.uid)
    }

    @Test
    fun testLogout_clearsAllDemoAndRealState() = runBlocking {
        val debugAuthRepo = AuthRepository(context, fakeFirebaseService, isDebug = true)
        debugAuthRepo.setDemoSession("VIEWER", isSuperAdmin = false)
        assertNotNull(debugAuthRepo.currentUserState.value)

        debugAuthRepo.signOut()

        assertNull("Logout must set currentUserState to null", debugAuthRepo.currentUserState.value)
        assertFalse("Logout must dismiss welcome onboarding", debugAuthRepo.showWelcomeOnboarding.value)
        assertNull("Logout must clear any lingering auth errors", debugAuthRepo.authError.value)
    }

    @Test
    fun testRealUserCannotInheritDemoData() = runBlocking {
        val releaseAuthRepo = AuthRepository(context, fakeFirebaseService, isDebug = false)
        val realUserUid = "real_google_user_99"

        val realUser = releaseAuthRepo.loadUserProfile(realUserUid)

        assertNotNull(realUser)
        assertEquals(realUserUid, realUser!!.uid)
        assertNotEquals("creator_demo_01", realUser.uid)
        assertNotEquals("viewer_demo_01", realUser.uid)
        assertNotEquals("super_admin_01", realUser.uid)
        assertNotEquals("Alex Rivera", realUser.displayName)
        assertNotEquals("Devin Vance", realUser.displayName)
        assertNull("Real user cannot inherit demo channel", realUser.channelId)
        assertFalse("Real user cannot inherit admin status", realUser.isSuperAdmin)
    }

    @Test
    fun testDemoSuperAdminCannotBypassProductionAuthorization() = runBlocking {
        val debugAuthRepo = AuthRepository(context, fakeFirebaseService, isDebug = true)
        debugAuthRepo.setDemoSession("SUPER_ADMIN", isSuperAdmin = false)

        val demoUid = debugAuthRepo.currentUserState.value?.uid
        assertEquals("super_admin_01", demoUid)

        // Check AdminRepository authorization
        val isAuthSync = adminRepository.isAuthorizedSuperAdmin(demoUid)
        assertFalse("super_admin_01 must NOT have synchronous admin authorization", isAuthSync)

        val isAuthAsync = adminRepository.verifySuperAdminAuthorization(demoUid)
        assertFalse("super_admin_01 must be REJECTED by verifySuperAdminAuthorization without real Firebase user + Firestore role", isAuthAsync)
        assertNull("Authorized admin UID must remain null", adminRepository.authorizedSuperAdminUid.value)
    }
}
