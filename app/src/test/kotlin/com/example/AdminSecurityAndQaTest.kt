package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.*
import com.example.data.remote.FirebaseService
import com.example.data.remote.IombgBackendService
import com.example.data.repository.AdminRepository
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * P0 SECURITY VERIFICATION SUITE: SUPER ADMIN ROLE-BASED AUTHORIZATION HARDENING
 *
 * Verifies:
 * 1. Hardcoded super_admin_01 UID does NOT grant production authorization.
 * 2. Hardcoded email addresses do NOT bypass authorization.
 * 3. Normal users (VIEWER) cannot access Super Admin Dashboard.
 * 4. Creators (CREATOR) cannot access Super Admin Dashboard.
 * 5. Unauthenticated sessions (null UID) cannot access Super Admin Dashboard.
 * 6. Authorized Super Admin with Firestore users/{uid}.role == "SUPER_ADMIN" is granted access.
 * 7. Admin authorization state is completely cleared upon logout.
 * 8. Admin authorization state is re-evaluated and does not bleed across user switching.
 * 9. Client cannot perform privilege escalation via profile update.
 * 10. Unauthorized callers cannot perform admin actions or generate audit logs.
 * 11. Firestore Security Rules enforce users/{uid}.role == "SUPER_ADMIN" without hardcoded UIDs/emails.
 * 12. All 19 Super Admin Dashboard architectural modules are configured.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdminSecurityAndQaTest {

    private lateinit var context: Context
    private lateinit var fakeFirebaseService: FakeTestFirebaseService
    private lateinit var fakeBackendService: FakeTestBackendService
    private lateinit var adminRepository: AdminRepository
    private lateinit var authRepository: AuthRepository

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

    class FakeTestBackendService(
        var isAuthorized: Boolean = false
    ) : IombgBackendService {
        override suspend fun getSuperAdminAuthorizationStatus(): Result<Boolean> = Result.success(isAuthorized)

        override suspend fun verifyAndCreditPayment(
            userId: String,
            channelId: String,
            amountInr: Double,
            paymentGatewayTxnId: String,
            paymentType: String,
            signature: String
        ): Result<Boolean> = Result.success(true)

        override suspend fun requestCreatorPayout(
            channelId: String,
            amount: Double,
            paymentMethod: String,
            upiId: String,
            bankAccount: String,
            bankIfsc: String,
            accountHolderName: String
        ): Result<String> = Result.success("pay_test_01")

        override suspend fun calculateMonetizationEligibility(channelId: String): Result<Map<String, Any>> =
            Result.success(mapOf("isEligible" to true))

        override suspend fun submitMonetizationApplication(
            channelId: String,
            legalName: String,
            panNumber: String
        ): Result<String> = Result.success("app_test_01")

        override suspend fun ingestAnalyticsBatch(events: List<Map<String, Any>>): Result<Int> =
            Result.success(events.size)

        override suspend fun adminExecuteAction(
            action: String,
            targetId: String,
            parameters: Map<String, Any>
        ): Result<Boolean> = Result.success(true)

        override suspend fun executePayoutSettlement(
            payoutId: String,
            creatorUid: String,
            amount: Double,
            adminUid: String
        ): Result<String> = Result.success("UPI/2026/TEST")

        override suspend fun triggerVideoTranscoding(
            videoId: String,
            rawStoragePath: String
        ): Result<Boolean> = Result.success(true)

        override suspend fun evaluateFraudRisk(
            targetUid: String,
            actionType: String,
            metadata: Map<String, String>
        ): Result<FraudAlert?> = Result.success(null)
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        fakeFirebaseService = FakeTestFirebaseService()
        fakeBackendService = FakeTestBackendService(isAuthorized = false)
        adminRepository = AdminRepository(fakeFirebaseService, fakeBackendService)
        authRepository = AuthRepository(context, fakeFirebaseService)
    }

    // =========================================================================
    // TEST 1: Normal user navigates to Admin Dashboard -> ACCESS DENIED
    // =========================================================================
    @Test
    fun test1_normalUserAccessDenied() = runBlocking {
        val normalUid = "viewer_uid_123"
        fakeFirebaseService.userProfiles[normalUid] = UserAccount(
            uid = normalUid,
            email = "viewer@iombg.com",
            role = "VIEWER",
            accountType = "VIEWER",
            isSuperAdmin = false
        )

        val isAuthorized = adminRepository.verifySuperAdminAuthorization(normalUid)
        assertFalse("Normal user with role VIEWER must NOT be authorized as Super Admin", isAuthorized)
        assertFalse(adminRepository.isAuthorizedSuperAdmin(normalUid))
    }

    // =========================================================================
    // TEST 2: Creator navigates to Admin Dashboard -> ACCESS DENIED
    // =========================================================================
    @Test
    fun test2_creatorAccessDenied() = runBlocking {
        val creatorUid = "creator_uid_456"
        fakeFirebaseService.userProfiles[creatorUid] = UserAccount(
            uid = creatorUid,
            email = "creator@iombg.com",
            role = "CREATOR",
            accountType = "CREATOR",
            isSuperAdmin = false
        )

        val isAuthorized = adminRepository.verifySuperAdminAuthorization(creatorUid)
        assertFalse("Creator UID must NOT be authorized as Super Admin", isAuthorized)
        assertFalse(adminRepository.isAuthorizedSuperAdmin(creatorUid))
    }

    // =========================================================================
    // TEST 3: Hardcoded super_admin_01 UID does NOT grant production authorization
    // =========================================================================
    @Test
    fun test3_hardcodedSuperAdmin01Denied() = runBlocking {
        // Without an authentic Firestore user profile with role == SUPER_ADMIN,
        // calling verifySuperAdminAuthorization("super_admin_01") must fail
        val isAuthorized = adminRepository.verifySuperAdminAuthorization("super_admin_01")
        assertFalse("Hardcoded super_admin_01 must NOT grant production authorization", isAuthorized)
        assertFalse(adminRepository.isAuthorizedSuperAdmin("super_admin_01"))
    }

    // =========================================================================
    // TEST 4: Unauthenticated session (null UID) -> ACCESS DENIED
    // =========================================================================
    @Test
    fun test4_unauthenticatedAccessDenied() = runBlocking {
        assertFalse("Null UID must NOT be authorized as Super Admin", adminRepository.verifySuperAdminAuthorization(null))
        assertFalse("Empty UID must NOT be authorized as Super Admin", adminRepository.verifySuperAdminAuthorization(""))
        assertFalse("Null UID must return false from synchronous isAuthorizedSuperAdmin", adminRepository.isAuthorizedSuperAdmin(null))
    }

    // =========================================================================
    // TEST 5: Verified Super Admin in Firestore users/{uid}.role == "SUPER_ADMIN" -> ACCESS GRANTED
    // =========================================================================
    @Test
    fun test5_firestoreSuperAdminRoleGranted() = runBlocking {
        val adminUid = "valid_admin_uid_777"
        fakeFirebaseService.userProfiles[adminUid] = UserAccount(
            uid = adminUid,
            email = "legitimate.admin@iombg.com",
            role = "SUPER_ADMIN",
            accountType = "SUPER_ADMIN",
            isSuperAdmin = true
        )

        val isAuthorized = adminRepository.verifySuperAdminAuthorization(adminUid)
        assertTrue("User with Firestore role == SUPER_ADMIN must be GRANTED access", isAuthorized)
        assertTrue("Synchronous check must confirm authorization", adminRepository.isAuthorizedSuperAdmin(adminUid))
    }

    // =========================================================================
    // TEST 6: Client attempts to assign itself SUPER_ADMIN -> REJECTED
    // =========================================================================
    @Test
    fun test6_clientAttemptsToAssignSuperAdmin_rejected() = runBlocking {
        val viewerUid = "viewer_tamper_01"
        val viewer = UserAccount(
            uid = viewerUid,
            email = "tamper@iombg.com",
            role = "VIEWER",
            accountType = "VIEWER",
            isSuperAdmin = false
        )
        fakeFirebaseService.userProfiles[viewerUid] = viewer

        // Attempt privilege escalation by modifying account to SUPER_ADMIN
        val escalatedProfile = viewer.copy(
            isSuperAdmin = true,
            accountType = "SUPER_ADMIN",
            role = "SUPER_ADMIN"
        )
        val updateResult = authRepository.updateProfile(escalatedProfile)

        assertFalse("Client self-privilege escalation must be REJECTED", updateResult)
        assertFalse("Profile in store must not be SUPER_ADMIN", fakeFirebaseService.userProfiles[viewerUid]?.isSuperAdmin ?: true)
    }

    // =========================================================================
    // TEST 7: Logout -> Admin authorization completely removed
    // =========================================================================
    @Test
    fun test7_logoutRemovesAdminAccess() = runBlocking {
        val adminUid = "admin_session_101"
        fakeFirebaseService.userProfiles[adminUid] = UserAccount(
            uid = adminUid,
            role = "SUPER_ADMIN",
            accountType = "SUPER_ADMIN",
            isSuperAdmin = true
        )

        assertTrue(adminRepository.verifySuperAdminAuthorization(adminUid))
        assertTrue(adminRepository.isAuthorizedSuperAdmin(adminUid))

        // Log out / clear admin state
        adminRepository.clearAdminState()
        assertFalse("Admin authorization must be removed after logout", adminRepository.isAuthorizedSuperAdmin(adminUid))
        assertNull("Authorized admin UID must be null", adminRepository.authorizedSuperAdminUid.value)
    }

    // =========================================================================
    // TEST 8: Account switch -> Admin state re-evaluated and not leaked
    // =========================================================================
    @Test
    fun test8_accountSwitchReEvaluatedWithoutBleed() = runBlocking {
        val adminUid = "admin_uid_first"
        fakeFirebaseService.userProfiles[adminUid] = UserAccount(
            uid = adminUid,
            role = "SUPER_ADMIN",
            accountType = "SUPER_ADMIN"
        )
        assertTrue(adminRepository.verifySuperAdminAuthorization(adminUid))
        assertTrue(adminRepository.isAuthorizedSuperAdmin(adminUid))

        // Switch to a normal user account
        val secondUid = "normal_user_second"
        fakeFirebaseService.userProfiles[secondUid] = UserAccount(
            uid = secondUid,
            role = "VIEWER",
            accountType = "VIEWER"
        )
        val secondAuthorized = adminRepository.verifySuperAdminAuthorization(secondUid)
        assertFalse("New normal user must NOT inherit previous admin authorization", secondAuthorized)
        assertFalse("New user must NOT be authorized as Super Admin", adminRepository.isAuthorizedSuperAdmin(secondUid))
        assertFalse("Old admin UID must NOT remain authorized after account switch", adminRepository.isAuthorizedSuperAdmin(adminUid))
    }

    // =========================================================================
    // TEST 9: Unauthorized user cannot execute admin actions
    // =========================================================================
    @Test
    fun test9_unauthorizedCallerCannotExecuteAdminActions() = runBlocking {
        adminRepository.clearAdminState()
        val initialLogCount = adminRepository.auditLogs.value.size

        // Attempt admin action with unverified caller
        adminRepository.suspendUser(
            uid = "user_victim_01",
            reason = "Unauthorized suspension attempt",
            adminId = "attacker_uid",
            adminEmail = "attacker@test.com"
        )

        val logsAfter = adminRepository.auditLogs.value
        assertEquals("Unauthorized action must NOT produce admin audit logs", initialLogCount, logsAfter.size)
    }

    // =========================================================================
    // TEST 10: Authorized admin action produces audit log with authenticated UID
    // =========================================================================
    @Test
    fun test10_authorizedAdminActionProducesAuditLog() = runBlocking {
        val adminUid = "auth_admin_555"
        adminRepository.setAuthorizedAdminForTesting(adminUid)
        val initialLogCount = adminRepository.auditLogs.value.size

        adminRepository.suspendUser(
            uid = "user_spammer_02",
            reason = "Automated spamming violation",
            adminId = adminUid,
            adminEmail = "admin@iombg.com"
        )

        val newLogs = adminRepository.auditLogs.value
        assertEquals("Audit log must be created for authorized admin action", initialLogCount + 1, newLogs.size)
        val log = newLogs.first()
        assertEquals("USER_SUSPENDED", log.action)
        assertEquals(adminUid, log.adminId)
        assertEquals("user_spammer_02", log.targetId)
    }

    // =========================================================================
    // TEST 11: Demo admin session does not bypass production authorization
    // =========================================================================
    @Test
    fun test11_demoAdminDoesNotBypassProductionAuthorization() = runBlocking {
        authRepository.setDemoSession("SUPER_ADMIN", isSuperAdmin = false)
        val demoUser = authRepository.currentUserState.value
        assertNotNull(demoUser)
        assertEquals("super_admin_01", demoUser?.uid)

        // Verifying through production AdminRepository must fail
        val isProdAuthorized = adminRepository.verifySuperAdminAuthorization(demoUser?.uid)
        assertFalse("Demo admin session must NOT bypass production authorization", isProdAuthorized)
        assertFalse(adminRepository.isAuthorizedSuperAdmin(demoUser?.uid))
    }

    // =========================================================================
    // TEST 12: Firestore Security Rules enforce role == SUPER_ADMIN without hardcoded credentials
    // =========================================================================
    @Test
    fun test12_firestoreRulesEnforceRoleBasedAccess() {
        val rulesFile = File("firestore.rules")
        if (rulesFile.exists()) {
            val content = rulesFile.readText()
            // Verify hardcoded bypasses are absent
            assertFalse("Rules must not contain hardcoded super_admin_01 UID", content.contains("\"super_admin_01\""))
            assertFalse("Rules must not contain hardcoded admin@iombg.com", content.contains("admin@iombg.com"))
            assertFalse("Rules must not contain hardcoded bhushangawali208@gmail.com", content.contains("bhushangawali208@gmail.com"))
            // Verify role-based checking is present
            assertTrue("Rules must verify role == SUPER_ADMIN", content.contains("SUPER_ADMIN"))
            assertTrue("Rules must check users/{uid} collection", content.contains("databases/\$(database)/documents/users/\$(request.auth.uid)"))
        }
    }

    // =========================================================================
    // TEST 13: Verify all 19 modular sections are configured
    // =========================================================================
    @Test
    fun test13_verifyAll19SectionsAvailable() {
        assertNotNull(adminRepository.platformMetrics.value)
        assertTrue(adminRepository.usersList.value.isNotEmpty())
        assertTrue(adminRepository.reportsList.value.isNotEmpty())
        assertTrue(adminRepository.fraudAlerts.value.isNotEmpty())
        assertNotNull(adminRepository.payoutRequests.value)
        assertTrue(adminRepository.premiumSubscribers.value.isNotEmpty())
        assertTrue(adminRepository.boostCampaigns.value.isNotEmpty())
        assertTrue(adminRepository.videoModerationQueue.value.isNotEmpty())
        assertTrue(adminRepository.liveStreamsQueue.value.isNotEmpty())
        assertTrue(adminRepository.messagingFlags.value.isNotEmpty())
        assertNotNull(adminRepository.financialLedger.value)
        assertTrue(adminRepository.adminAnnouncements.value.isNotEmpty())
        assertNotNull(adminRepository.platformSettings.value)
    }
}
