package com.example.data.repository

import com.example.data.model.*
import com.example.data.remote.FirebaseService
import com.example.data.remote.IombgBackendService
import com.example.data.remote.IombgBackendServiceImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AdminRepository(
    private val firebaseService: FirebaseService,
    private val backendService: IombgBackendService = IombgBackendServiceImpl()
) {

    // Holds the UID of the currently authorized Super Admin verified against Firestore users/{uid}.role == "SUPER_ADMIN"
    private val _authorizedSuperAdminUid = MutableStateFlow<String?>(null)
    val authorizedSuperAdminUid: StateFlow<String?> = _authorizedSuperAdminUid.asStateFlow()

    private val _isAuthorizing = MutableStateFlow(false)
    val isAuthorizing: StateFlow<Boolean> = _isAuthorizing.asStateFlow()

    /**
     * Clears all cached admin authorization state on logout or account switch.
     */
    fun clearAdminState() {
        _authorizedSuperAdminUid.value = null
    }

    /**
     * Synchronous check for UI binding.
     * Evaluates if the UID is currently verified and authorized as a Super Admin in Firestore.
     * Hardcoded demo UIDs (e.g. super_admin_01), emails, and unverified users NEVER return true.
     */
    fun isAuthorizedSuperAdmin(uid: String?): Boolean {
        if (uid.isNullOrBlank()) return false
        // Hardcoded demo UID checks are strictly disallowed
        if (uid == "super_admin_01" && _authorizedSuperAdminUid.value != uid) {
            return false
        }
        return _authorizedSuperAdminUid.value == uid
    }

    /**
     * Authoritative role verification flow:
     * FirebaseAuth.currentUser
     *         ↓
     * authenticated UID
     *         ↓
     * Firestore users/{uid}.role
     *         ↓
     * role == SUPER_ADMIN
     *         ↓
     * allow Admin Dashboard
     */
    suspend fun verifySuperAdminAuthorization(uid: String?): Boolean {
        // Clear previous state first to prevent cross-account bleed
        _authorizedSuperAdminUid.value = null
        if (uid.isNullOrBlank()) return false

        // Insecure bypass prevention: Hardcoded demo identifiers CANNOT grant production Super Admin privileges
        if (uid == "super_admin_01" || uid == "creator_demo_01" || uid == "viewer_demo_01") {
            val fbUser = firebaseService.currentFirebaseUser
            if (fbUser == null || fbUser.uid != uid) {
                // Not an authenticated Firebase user - reject
                return false
            }
        }

        return try {
            _isAuthorizing.value = true
            val profile = firebaseService.getUserProfile(uid)
            val isAuthorized = profile != null && (profile.role == "SUPER_ADMIN" || profile.accountType == "SUPER_ADMIN")
            if (isAuthorized) {
                _authorizedSuperAdminUid.value = uid
                true
            } else {
                _authorizedSuperAdminUid.value = null
                false
            }
        } catch (e: Exception) {
            _authorizedSuperAdminUid.value = null
            false
        } finally {
            _isAuthorizing.value = false
        }
    }

    suspend fun verifySuperAdminOnServer(): Boolean {
        return backendService.getSuperAdminAuthorizationStatus().getOrDefault(false)
    }

    fun getSuperAdminEmail(): String = firebaseService.currentFirebaseUser?.email ?: ""

    // Testing helper for injecting verified admin session in unit tests
    fun setAuthorizedAdminForTesting(uid: String?) {
        _authorizedSuperAdminUid.value = uid
    }

    // Platform Aggregate Metrics
    private val _platformMetrics = MutableStateFlow(PlatformAggregateMetrics())
    val platformMetrics: StateFlow<PlatformAggregateMetrics> = _platformMetrics.asStateFlow()

    // Monetization Applications
    private val _pendingMonetizationApps = MutableStateFlow<List<MonetizationApplication>>(emptyList())
    val pendingMonetizationApps: StateFlow<List<MonetizationApplication>> = _pendingMonetizationApps.asStateFlow()

    // Users Management
    private val _usersList = MutableStateFlow<List<AdminUserSummary>>(emptyList())
    val usersList: StateFlow<List<AdminUserSummary>> = _usersList.asStateFlow()

    // Reports Center
    private val _reportsList = MutableStateFlow<List<ReportItem>>(emptyList())
    val reportsList: StateFlow<List<ReportItem>> = _reportsList.asStateFlow()

    // Fraud Alerts & Telemetry
    private val _fraudAlerts = MutableStateFlow<List<FraudAlert>>(emptyList())
    val fraudAlerts: StateFlow<List<FraudAlert>> = _fraudAlerts.asStateFlow()

    private val _fraudSignals = MutableStateFlow<List<FraudSignal>>(emptyList())
    val fraudSignals: StateFlow<List<FraudSignal>> = _fraudSignals.asStateFlow()

    // Payout Requests
    private val _payoutRequests = MutableStateFlow<List<PayoutRequest>>(emptyList())
    val payoutRequests: StateFlow<List<PayoutRequest>> = _payoutRequests.asStateFlow()

    // Premium Subscribers
    private val _premiumSubscribers = MutableStateFlow<List<AdminSubscriberSummary>>(emptyList())
    val premiumSubscribers: StateFlow<List<AdminSubscriberSummary>> = _premiumSubscribers.asStateFlow()

    // Boost Campaigns
    private val _boostCampaigns = MutableStateFlow<List<BoostCampaign>>(emptyList())
    val boostCampaigns: StateFlow<List<BoostCampaign>> = _boostCampaigns.asStateFlow()

    // Video & Shorts Moderation Queue
    private val _videoModerationQueue = MutableStateFlow<List<AdminVideoModerationItem>>(emptyList())
    val videoModerationQueue: StateFlow<List<AdminVideoModerationItem>> = _videoModerationQueue.asStateFlow()

    // Live Streams Moderation Queue
    private val _liveStreamsQueue = MutableStateFlow<List<AdminLiveStreamItem>>(emptyList())
    val liveStreamsQueue: StateFlow<List<AdminLiveStreamItem>> = _liveStreamsQueue.asStateFlow()

    // Messaging Safety Flags
    private val _messagingFlags = MutableStateFlow<List<AdminMessagingFlagItem>>(emptyList())
    val messagingFlags: StateFlow<List<AdminMessagingFlagItem>> = _messagingFlags.asStateFlow()

    // Financial Ledger (Double-Entry platform transactions)
    private val _financialLedger = MutableStateFlow<List<AdminFinancialLedgerItem>>(emptyList())
    val financialLedger: StateFlow<List<AdminFinancialLedgerItem>> = _financialLedger.asStateFlow()

    // Admin System Broadcasts / Announcements
    private val _adminAnnouncements = MutableStateFlow<List<AdminAnnouncementItem>>(emptyList())
    val adminAnnouncements: StateFlow<List<AdminAnnouncementItem>> = _adminAnnouncements.asStateFlow()

    // Audit Logs
    private val _auditLogs = MutableStateFlow<List<AdminAuditLog>>(emptyList())
    val auditLogs: StateFlow<List<AdminAuditLog>> = _auditLogs.asStateFlow()

    // Platform Settings
    private val _platformSettings = MutableStateFlow(PlatformSettings())
    val platformSettings: StateFlow<PlatformSettings> = _platformSettings.asStateFlow()

    init {
        try {
            firebaseService.auth.addAuthStateListener { fbAuth ->
                val currentUid = fbAuth.currentUser?.uid
                if (currentUid == null || currentUid != _authorizedSuperAdminUid.value) {
                    clearAdminState()
                }
            }
        } catch (_: Exception) {}
        loadMockAdminData()
    }

    private fun loadMockAdminData() {
        // Sample Users
        _usersList.value = listOf(
            AdminUserSummary(
                uid = "creator_demo_01",
                displayName = "Alex Rivera",
                username = "alexrivera",
                email = "alex.creator@iombg.com",
                photoUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400",
                accountType = "CREATOR",
                status = AccountModerationStatus.ACTIVE,
                isCreator = true,
                channelId = "ch_alexrivera",
                channelName = "Alex Rivera Vlogs",
                subscriberCount = 142000,
                totalVideos = 28,
                isMonetized = true,
                isPremium = true,
                createdAt = System.currentTimeMillis() - 86400000L * 180,
                totalWatchMinutes = 480000,
                totalViews = 1200000
            ),
            AdminUserSummary(
                uid = "creator_demo_02",
                displayName = "Maya Chen Tech",
                username = "mayachen",
                email = "maya.tech@iombg.com",
                photoUrl = "https://images.unsplash.com/photo-1580489944761-15a19d654956?w=400",
                accountType = "CREATOR",
                status = AccountModerationStatus.ACTIVE,
                isCreator = true,
                channelId = "ch_02",
                channelName = "CinemaCraft Studios",
                subscriberCount = 28400,
                totalVideos = 14,
                isMonetized = false,
                isPremium = true,
                createdAt = System.currentTimeMillis() - 86400000L * 120,
                totalWatchMinutes = 852000,
                totalViews = 450000
            ),
            AdminUserSummary(
                uid = "user_suspicious_01",
                displayName = "HyperStream Bots",
                username = "hyperbot99",
                email = "bot_cluster@shadowmail.com",
                photoUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400",
                accountType = "VIEWER",
                status = AccountModerationStatus.RESTRICTED,
                isCreator = false,
                channelId = "ch_fake_views_01",
                channelName = "Hyper Bot Channel",
                subscriberCount = 120,
                totalVideos = 1,
                isMonetized = false,
                isPremium = false,
                isCommentRestricted = true,
                isMessageRestricted = true,
                suspensionReason = "Automated view loop scripts & comment spamming",
                createdAt = System.currentTimeMillis() - 86400000L * 5,
                totalWatchMinutes = 12000,
                totalViews = 24000
            ),
            AdminUserSummary(
                uid = "user_spammer_02",
                displayName = "Crypto Moon Booster",
                username = "cryptomoon100x",
                email = "shill@cryptomoon.io",
                photoUrl = "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=400",
                accountType = "VIEWER",
                status = AccountModerationStatus.SUSPENDED,
                isCreator = false,
                isUploadRestricted = true,
                isCommentRestricted = true,
                isMessageRestricted = true,
                suspensionReason = "Phishing links in live chat and direct message requests",
                suspendedAt = System.currentTimeMillis() - 86400000L * 2,
                createdAt = System.currentTimeMillis() - 86400000L * 10,
                totalWatchMinutes = 300,
                totalViews = 50
            ),
            AdminUserSummary(
                uid = "viewer_demo_01",
                displayName = "Devin Vance",
                username = "devinvance",
                email = "devin.vance@gmail.com",
                photoUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400",
                accountType = "VIEWER",
                status = AccountModerationStatus.ACTIVE,
                isCreator = false,
                isPremium = false,
                createdAt = System.currentTimeMillis() - 86400000L * 45,
                totalWatchMinutes = 3200,
                totalViews = 180
            )
        )

        // Sample Pending Monetization Applications
        _pendingMonetizationApps.value = listOf(
            MonetizationApplication(
                applicationId = "app_mon_002",
                channelId = "ch_02",
                ownerUid = "creator_demo_02",
                channelName = "CinemaCraft Studios",
                subscriberCount = 28400,
                eligibleWatchHoursLast12M = 14200.0,
                eligibleShortsViewsLast90D = 890000,
                meetsLongVideoCriteria = true,
                meetsShortsCriteria = true,
                isTermsAccepted = true,
                kycStatus = KYCStatus.DOCUMENTS_UPLOADED,
                legalName = "CinemaCraft LLC",
                panNumber = "CINEMA9988Z",
                status = ApplicationStatus.APPLICATION_PENDING,
                applicationStatus = ApplicationStatus.APPLICATION_PENDING,
                appliedAt = System.currentTimeMillis() - 86400000L * 2
            ),
            MonetizationApplication(
                applicationId = "app_mon_003",
                channelId = "ch_04",
                ownerUid = "creator_demo_04",
                channelName = "Chef Marco Gastro",
                subscriberCount = 890,
                eligibleWatchHoursLast12M = 780.0,
                eligibleShortsViewsLast90D = 412000,
                meetsLongVideoCriteria = true,
                meetsShortsCriteria = true,
                isTermsAccepted = true,
                kycStatus = KYCStatus.DOCUMENTS_UPLOADED,
                legalName = "Marco Rossi",
                panNumber = "MARCO1122P",
                status = ApplicationStatus.APPLICATION_PENDING,
                applicationStatus = ApplicationStatus.APPLICATION_PENDING,
                appliedAt = System.currentTimeMillis() - 86400000L * 1
            )
        )

        // Sample Reports
        _reportsList.value = listOf(
            ReportItem(
                reportId = "rep_001",
                reporterUid = "viewer_demo_01",
                targetId = "vid_demo_scam_01",
                targetType = "VIDEO",
                reason = "Deceptive Cryptocurrency Scheme in Video Description",
                reasonEnum = ReportReason.SCAM_FRAUD,
                details = "Links in the pinned comment redirect to an unverified wallet drainer site.",
                evidenceSummary = "Reported 8 times in last 3 hours by verified viewers.",
                status = "OPEN",
                statusEnum = ReportStatus.OPEN,
                createdAt = System.currentTimeMillis() - 3600000L * 2
            ),
            ReportItem(
                reportId = "rep_002",
                reporterUid = "creator_demo_01",
                targetId = "cmt_spam_881",
                targetType = "COMMENT",
                reason = "Harassment and offensive hate speech targeting community members",
                reasonEnum = ReportReason.HATE_ABUSE,
                details = "Targeted hateful language posted across multiple video comments.",
                evidenceSummary = "AI toxicity score 0.96 flagged automatically.",
                status = "REVIEWING",
                statusEnum = ReportStatus.REVIEWING,
                createdAt = System.currentTimeMillis() - 3600000L * 6
            ),
            ReportItem(
                reportId = "rep_003",
                reporterUid = "creator_demo_02",
                targetId = "msg_req_phish_09",
                targetType = "MESSAGE",
                reason = "Phishing URL sent via cold message request",
                reasonEnum = ReportReason.SCAM_FRAUD,
                details = "Offered fake sponsorship deal with malicious file attachment link.",
                evidenceSummary = "Safety sandbox flagged unknown executable domain.",
                status = "OPEN",
                statusEnum = ReportStatus.OPEN,
                createdAt = System.currentTimeMillis() - 3600000L * 12
            ),
            ReportItem(
                reportId = "rep_004",
                reporterUid = "viewer_user_89",
                targetId = "live_stream_copy_01",
                targetType = "LIVE",
                reason = "Restreaming copyrighted live sports broadcast without authorization",
                reasonEnum = ReportReason.COPYRIGHT,
                details = "Re-broadcasting premium sports feed overlayed with gambling ads.",
                evidenceSummary = "Automated digital fingerprint match confirmed.",
                status = "OPEN",
                statusEnum = ReportStatus.OPEN,
                createdAt = System.currentTimeMillis() - 3600000L * 1
            )
        )

        // Sample Fraud Alerts
        _fraudAlerts.value = listOf(
            FraudAlert(
                alertId = "frd_alt_001",
                userId = "user_suspicious_01",
                channelId = "ch_fake_views_01",
                contentId = "vid_boosted_01",
                category = FraudSignalCategory.ENGAGEMENT,
                signalType = "ARTIFICIAL_VIEW_LOOP_BURST",
                riskLevel = FraudRiskLevel.CRITICAL,
                score = 0.96f,
                evidenceSummary = "1,850 views detected within 120 seconds with identical user-agent headers and sub-second retention.",
                status = FraudAlertStatus.OPEN,
                createdAt = System.currentTimeMillis() - 3600000L * 3
            ),
            FraudAlert(
                alertId = "frd_alt_002",
                userId = "user_spammer_02",
                transactionId = "tx_support_fake_99",
                category = FraudSignalCategory.PAYMENTS,
                signalType = "RAPID_CARD_TESTING_SIGNALS",
                riskLevel = FraudRiskLevel.HIGH,
                score = 0.88f,
                evidenceSummary = "7 consecutive failed micro-transactions from distinct cards within 90 seconds.",
                status = FraudAlertStatus.INVESTIGATING,
                createdAt = System.currentTimeMillis() - 3600000L * 5
            ),
            FraudAlert(
                alertId = "frd_alt_003",
                userId = "user_bot_cluster_44",
                category = FraudSignalCategory.ACCOUNT,
                signalType = "SUSPICIOUS_REGISTRATION_VELOCITY",
                riskLevel = FraudRiskLevel.HIGH,
                score = 0.84f,
                evidenceSummary = "18 accounts created within 4 minutes originating from single VPN egress gateway.",
                status = FraudAlertStatus.OPEN,
                createdAt = System.currentTimeMillis() - 3600000L * 8
            ),
            FraudAlert(
                alertId = "frd_alt_004",
                channelId = "ch_reupload_spam",
                contentId = "short_stolen_01",
                category = FraudSignalCategory.CONTENT,
                signalType = "MASS_DUPLICATE_VIDEO_REUPLOAD",
                riskLevel = FraudRiskLevel.MEDIUM,
                score = 0.76f,
                evidenceSummary = "35 identical 15-second clips uploaded across 5 newly created channels in 1 hour.",
                status = FraudAlertStatus.OPEN,
                createdAt = System.currentTimeMillis() - 3600000L * 14
            )
        )

        // Sample Fraud Signals (Legacy compatibility)
        _fraudSignals.value = listOf(
            FraudSignal(
                signalId = "frd_001",
                targetUid = "user_suspicious_01",
                targetChannelId = "ch_fake_views_01",
                signalType = "SUSPICIOUS_WATCHTIME_MANIPULATION",
                riskLevel = FraudRiskLevel.HIGH,
                confidenceScore = 0.94f,
                evidenceDetails = "1,200 repeated video view loop requests from single cluster IP in 10 minutes.",
                isReviewed = false,
                detectedAt = System.currentTimeMillis() - 3600000 * 4
            ),
            FraudSignal(
                signalId = "frd_002",
                targetUid = "user_bot_02",
                targetChannelId = "ch_bot_farm",
                signalType = "BOT_TRAFFIC_BURST",
                riskLevel = FraudRiskLevel.CRITICAL,
                confidenceScore = 0.98f,
                evidenceDetails = "Rapid automated comment scripts detected on multiple trending videos.",
                isReviewed = false,
                detectedAt = System.currentTimeMillis() - 3600000 * 8
            )
        )

        // Sample Payout Requests (Disabled - no fake payout requests)
        _payoutRequests.value = emptyList()

        // Sample Premium Subscribers
        _premiumSubscribers.value = listOf(
            AdminSubscriberSummary(
                subscriptionId = "sub_prm_001",
                userUid = "creator_demo_01",
                userName = "Alex Rivera",
                userEmail = "alex.creator@iombg.com",
                amountPaidInr = 129.0,
                status = SubscriptionStatus.ACTIVE
            ),
            AdminSubscriberSummary(
                subscriptionId = "sub_prm_002",
                userUid = "creator_demo_02",
                userName = "Maya Chen",
                userEmail = "maya.tech@iombg.com",
                amountPaidInr = 129.0,
                status = SubscriptionStatus.ACTIVE
            ),
            AdminSubscriberSummary(
                subscriptionId = "sub_prm_003",
                userUid = "user_tech_fan_45",
                userName = "Rohan Sharma",
                userEmail = "rohan.sharma@gmail.com",
                amountPaidInr = 129.0,
                status = SubscriptionStatus.ACTIVE
            )
        )

        // Sample Boost Campaigns
        _boostCampaigns.value = listOf(
            BoostCampaign(
                boostId = "boost_001",
                campaignId = "camp_boost_001",
                payerUserId = "creator_demo_01",
                creatorId = "creator_demo_01",
                channelId = "ch_alexrivera",
                contentId = "vid_01",
                contentTitle = "Ultra 4K Cinematic Drone Tour of Tokyo 2026",
                contentThumbnailUrl = "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=600",
                budget = 1000.0,
                budgetAmount = 1000.0,
                targetCategory = "Travel & Tech",
                estimatedImpressions = "15,000 - 25,000",
                deliveredImpressions = 14200,
                generatedViews = 2840,
                generatedClicks = 640,
                totalWatchTimeMinutes = 8520.0,
                status = BoostStatus.ACTIVE,
                createdAt = System.currentTimeMillis() - 86400000L * 3
            ),
            BoostCampaign(
                boostId = "boost_002",
                campaignId = "camp_boost_002",
                payerUserId = "creator_demo_02",
                creatorId = "creator_demo_02",
                channelId = "ch_02",
                contentId = "short_02",
                contentTitle = "Instant AI Code Generation in 10 Seconds!",
                contentThumbnailUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=600",
                budget = 500.0,
                budgetAmount = 500.0,
                targetCategory = "Technology",
                estimatedImpressions = "8,000 - 12,000",
                deliveredImpressions = 7800,
                generatedViews = 3900,
                generatedClicks = 420,
                totalWatchTimeMinutes = 975.0,
                status = BoostStatus.ACTIVE,
                createdAt = System.currentTimeMillis() - 86400000L * 2
            )
        )

        // Legitimate Audit Logs: Empty until authoritative admin actions are logged
        _auditLogs.value = emptyList()

        // Sample Videos & Shorts Moderation Queue
        _videoModerationQueue.value = listOf(
            AdminVideoModerationItem(
                contentId = "vid_01",
                title = "Ultra 4K Cinematic Drone Tour of Tokyo 2026",
                channelId = "ch_alexrivera",
                channelName = "Alex Rivera Vlogs",
                thumbnailUrl = "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=600",
                durationFormatted = "14:20",
                viewCount = 142000,
                isShort = false,
                status = AdminModerationStatus.APPROVED,
                reportCount = 0,
                createdAt = System.currentTimeMillis() - 86400000L * 10
            ),
            AdminVideoModerationItem(
                contentId = "vid_demo_scam_01",
                title = "100x Guaranteed Crypto Return in 24 Hours! [FREE BOT]",
                channelId = "ch_crypto_pump",
                channelName = "MoonInvest Club",
                thumbnailUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=600",
                durationFormatted = "08:45",
                viewCount = 3400,
                isShort = false,
                status = AdminModerationStatus.FLAGGED,
                reportCount = 8,
                flagReason = "Phishing & Financial Fraud",
                createdAt = System.currentTimeMillis() - 86400000L * 1
            ),
            AdminVideoModerationItem(
                contentId = "short_02",
                title = "Instant AI Code Generation in 10 Seconds!",
                channelId = "ch_02",
                channelName = "CinemaCraft Studios",
                thumbnailUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=600",
                durationFormatted = "0:30",
                viewCount = 890000,
                isShort = true,
                status = AdminModerationStatus.APPROVED,
                reportCount = 0,
                createdAt = System.currentTimeMillis() - 86400000L * 5
            ),
            AdminVideoModerationItem(
                contentId = "short_stolen_01",
                title = "Funny Moments Compilation Part 99",
                channelId = "ch_reupload_spam",
                channelName = "ClipHub",
                thumbnailUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600",
                durationFormatted = "0:15",
                viewCount = 12000,
                isShort = true,
                status = AdminModerationStatus.FLAGGED,
                reportCount = 4,
                flagReason = "Mass Re-upload / Duplicate spam",
                createdAt = System.currentTimeMillis() - 3600000L * 14
            )
        )

        // Sample Live Streams
        _liveStreamsQueue.value = listOf(
            AdminLiveStreamItem(
                streamId = "live_001",
                channelId = "ch_alexrivera",
                channelName = "Alex Rivera Vlogs",
                title = "LIVE Q&A: Travel Filmmaking Gear 2026",
                viewerCount = 1420,
                status = "LIVE",
                startedAt = System.currentTimeMillis() - 3600000L * 1,
                isRestricted = false
            ),
            AdminLiveStreamItem(
                streamId = "live_002",
                channelId = "ch_crypto_pump",
                channelName = "MoonInvest Club",
                title = "URGENT BITCOIN AIRDROP LIVE STREAM",
                viewerCount = 280,
                status = "LIVE",
                startedAt = System.currentTimeMillis() - 1800000L,
                isRestricted = true,
                restrictionReason = "Unverified financial claims in chat"
            )
        )

        // Sample Messaging Safety Flags
        _messagingFlags.value = listOf(
            AdminMessagingFlagItem(
                flagId = "msg_flg_01",
                senderUid = "user_spammer_02",
                senderName = "FastEarn Bot",
                recipientUid = "creator_demo_01",
                recipientName = "Alex Rivera",
                messageSnippet = "Earn ₹50,000 daily from home! Click here: http://bit.ly/fake-payout-now",
                riskCategory = "SPAM_PHISHING",
                status = "FLAGGED",
                timestamp = System.currentTimeMillis() - 3600000L * 3
            ),
            AdminMessagingFlagItem(
                flagId = "msg_flg_02",
                senderUid = "user_troll_99",
                senderName = "Anonymous99",
                recipientUid = "creator_demo_02",
                recipientName = "Maya Chen",
                messageSnippet = "Stop uploading or I will spam dislike all your videos",
                riskCategory = "HARASSMENT",
                status = "INVESTIGATING",
                timestamp = System.currentTimeMillis() - 3600000L * 12
            )
        )

        // Financial Ledger (Disabled - no fake transactions)
        _financialLedger.value = emptyList()

        // Sample Admin Announcements
        _adminAnnouncements.value = listOf(
            AdminAnnouncementItem(
                announcementId = "anc_01",
                title = "Creator Studio 2026 Spring Update Released",
                message = "New advanced analytics, HDR 4K processing, and instant UPI payouts are now active.",
                targetAudience = "ALL_CREATORS",
                sentByAdmin = "IOMBG Security Office",
                timestamp = System.currentTimeMillis() - 86400000L * 7
            ),
            AdminAnnouncementItem(
                announcementId = "anc_02",
                title = "Security Notice: Anti-Phishing Safeguards",
                message = "Never share one-time authentication codes or click unverified links sent in message requests.",
                targetAudience = "ALL_USERS",
                sentByAdmin = "IOMBG Security Office",
                timestamp = System.currentTimeMillis() - 86400000L * 14
            )
        )
    }

    // ======================== USER MANAGEMENT ========================
    suspend fun suspendUser(
        uid: String,
        reason: String,
        adminId: String,
        adminEmail: String
    ) {
        if (!isAuthorizedSuperAdmin(adminId)) {
            android.util.Log.w("AdminRepository", "Unauthorized admin action rejected for $adminId")
            return
        }
        _usersList.value = _usersList.value.map { user ->
            if (user.uid == uid) {
                user.copy(
                    status = AccountModerationStatus.SUSPENDED,
                    suspensionReason = reason,
                    suspendedAt = System.currentTimeMillis(),
                    isUploadRestricted = true,
                    isCommentRestricted = true,
                    isMessageRestricted = true
                )
            } else user
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "USER_SUSPENDED",
            targetType = "USER",
            targetId = uid,
            previousValue = "ACTIVE",
            newValue = "SUSPENDED",
            reason = reason
        )
    }

    suspend fun restoreUser(
        uid: String,
        adminId: String,
        adminEmail: String
    ) {
        _usersList.value = _usersList.value.map { user ->
            if (user.uid == uid) {
                user.copy(
                    status = AccountModerationStatus.ACTIVE,
                    suspensionReason = null,
                    suspendedAt = null,
                    isUploadRestricted = false,
                    isCommentRestricted = false,
                    isMessageRestricted = false
                )
            } else user
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "USER_RESTORED",
            targetType = "USER",
            targetId = uid,
            previousValue = "SUSPENDED",
            newValue = "ACTIVE",
            reason = "Account review completed; appeal approved."
        )
    }

    suspend fun updateUserRestrictions(
        uid: String,
        uploadRestricted: Boolean,
        commentRestricted: Boolean,
        messageRestricted: Boolean,
        adminId: String,
        adminEmail: String
    ) {
        _usersList.value = _usersList.value.map { user ->
            if (user.uid == uid) {
                val hasRestrictions = uploadRestricted || commentRestricted || messageRestricted
                user.copy(
                    status = if (hasRestrictions) AccountModerationStatus.RESTRICTED else AccountModerationStatus.ACTIVE,
                    isUploadRestricted = uploadRestricted,
                    isCommentRestricted = commentRestricted,
                    isMessageRestricted = messageRestricted
                )
            } else user
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "USER_RESTRICTIONS_UPDATED",
            targetType = "USER",
            targetId = uid,
            reason = "Restrictions: Upload=$uploadRestricted, Comment=$commentRestricted, Msg=$messageRestricted"
        )
    }

    // ======================== REPORT CENTER ========================
    suspend fun submitReport(report: ReportItem) {
        val newReport = if (report.reportId.isBlank()) {
            report.copy(reportId = "rep_${UUID.randomUUID().toString().take(8)}")
        } else report
        _reportsList.value = listOf(newReport) + _reportsList.value
    }

    suspend fun reviewReport(
        reportId: String,
        status: ReportStatus,
        actionTaken: String?,
        adminId: String,
        adminEmail: String
    ) {
        val oldReport = _reportsList.value.find { it.reportId == reportId }
        _reportsList.value = _reportsList.value.map { rep ->
            if (rep.reportId == reportId) {
                rep.copy(
                    status = status.name,
                    statusEnum = status,
                    actionTaken = actionTaken,
                    reviewedByUid = adminId,
                    resolvedAt = if (status == ReportStatus.RESOLVED || status == ReportStatus.DISMISSED) System.currentTimeMillis() else null
                )
            } else rep
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "REPORT_${status.name}",
            targetType = oldReport?.targetType ?: "CONTENT",
            targetId = oldReport?.targetId ?: reportId,
            previousValue = oldReport?.status,
            newValue = status.name,
            reason = actionTaken ?: "Status changed by Super Admin"
        )
    }

    // ======================== MONETIZATION ========================
    suspend fun reviewMonetizationApp(
        applicationId: String,
        adminId: String,
        adminEmail: String,
        approve: Boolean,
        rejectionReason: String? = null
    ) {
        val app = _pendingMonetizationApps.value.find { it.applicationId == applicationId } ?: return
        val newStatus = if (approve) ApplicationStatus.APPROVED else ApplicationStatus.REJECTED

        _pendingMonetizationApps.value = _pendingMonetizationApps.value.filter { it.applicationId != applicationId }

        // Update creator status in users list if present
        if (approve) {
            _usersList.value = _usersList.value.map { user ->
                if (user.uid == app.ownerUid || user.channelId == app.channelId) {
                    user.copy(isMonetized = true)
                } else user
            }
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = if (approve) "MONETIZATION_APPLICATION_APPROVED" else "MONETIZATION_APPLICATION_REJECTED",
            targetType = "CHANNEL",
            targetId = app.channelId,
            previousValue = "APPLICATION_PENDING",
            newValue = newStatus.name,
            reason = if (approve) "Passed creator review and analytics criteria" else (rejectionReason ?: "Eligibility criteria not fully satisfied")
        )
    }

    suspend fun suspendChannelMonetization(
        channelId: String,
        reason: String,
        adminId: String,
        adminEmail: String
    ) {
        _usersList.value = _usersList.value.map { user ->
            if (user.channelId == channelId) user.copy(isMonetized = false) else user
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "CHANNEL_MONETIZATION_SUSPENDED",
            targetType = "CHANNEL",
            targetId = channelId,
            previousValue = "APPROVED",
            newValue = "SUSPENDED",
            reason = reason
        )
    }

    suspend fun restoreChannelMonetization(
        channelId: String,
        adminId: String,
        adminEmail: String
    ) {
        _usersList.value = _usersList.value.map { user ->
            if (user.channelId == channelId) user.copy(isMonetized = true) else user
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "CHANNEL_MONETIZATION_RESTORED",
            targetType = "CHANNEL",
            targetId = channelId,
            previousValue = "SUSPENDED",
            newValue = "APPROVED",
            reason = "Channel compliance verification completed."
        )
    }

    // ======================== PAYOUTS ========================
    suspend fun processPayoutRequest(
        payoutId: String,
        approve: Boolean,
        transactionRef: String?,
        failureReason: String?,
        adminId: String,
        adminEmail: String
    ) {
        val req = _payoutRequests.value.find { it.payoutId == payoutId } ?: return
        val newStatus = if (approve) PayoutStatus.PAID else PayoutStatus.FAILED

        _payoutRequests.value = _payoutRequests.value.map { p ->
            if (p.payoutId == payoutId) {
                p.copy(
                    status = newStatus,
                    transactionReference = transactionRef,
                    failureReason = failureReason,
                    processedByAdminUid = adminId,
                    processedAt = System.currentTimeMillis()
                )
            } else p
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = if (approve) "PAYOUT_EXECUTED" else "PAYOUT_REJECTED",
            targetType = "PAYOUT_REQUEST",
            targetId = payoutId,
            previousValue = req.status.name,
            newValue = newStatus.name,
            reason = if (approve) "Settled to creator: ${req.amount} INR via ${req.paymentMethod}" else (failureReason ?: "Payout held by Admin")
        )
    }

    // ======================== BOOST MANAGEMENT ========================
    suspend fun pauseBoostCampaign(campaignId: String, reason: String, adminId: String, adminEmail: String) {
        _boostCampaigns.value = _boostCampaigns.value.map { c ->
            if (c.campaignId == campaignId || c.boostId == campaignId) c.copy(status = BoostStatus.PAUSED) else c
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "BOOST_CAMPAIGN_PAUSED",
            targetType = "BOOST_CAMPAIGN",
            targetId = campaignId,
            reason = reason
        )
    }

    suspend fun resumeBoostCampaign(campaignId: String, adminId: String, adminEmail: String) {
        _boostCampaigns.value = _boostCampaigns.value.map { c ->
            if (c.campaignId == campaignId || c.boostId == campaignId) c.copy(status = BoostStatus.ACTIVE) else c
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "BOOST_CAMPAIGN_RESUMED",
            targetType = "BOOST_CAMPAIGN",
            targetId = campaignId,
            reason = "Campaign review passed safety checks."
        )
    }

    suspend fun cancelBoostCampaign(campaignId: String, reason: String, adminId: String, adminEmail: String) {
        _boostCampaigns.value = _boostCampaigns.value.map { c ->
            if (c.campaignId == campaignId || c.boostId == campaignId) c.copy(status = BoostStatus.CANCELLED) else c
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "BOOST_CAMPAIGN_CANCELLED",
            targetType = "BOOST_CAMPAIGN",
            targetId = campaignId,
            reason = reason
        )
    }

    // ======================== FRAUD MONITORING ========================
    suspend fun investigateFraudAlert(alertId: String, adminId: String, adminEmail: String) {
        _fraudAlerts.value = _fraudAlerts.value.map { a ->
            if (a.alertId == alertId) a.copy(status = FraudAlertStatus.INVESTIGATING) else a
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "FRAUD_ALERT_INVESTIGATING",
            targetType = "FRAUD_ALERT",
            targetId = alertId,
            reason = "Admin initiated deep evidence review."
        )
    }

    suspend fun resolveFraudAlert(
        alertId: String,
        actionTaken: String,
        adminId: String,
        adminEmail: String
    ) {
        _fraudAlerts.value = _fraudAlerts.value.map { a ->
            if (a.alertId == alertId) {
                a.copy(
                    status = FraudAlertStatus.RESOLVED,
                    resolvedAt = System.currentTimeMillis(),
                    resolvedByAdminUid = adminId,
                    mitigationAction = actionTaken
                )
            } else a
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "FRAUD_ALERT_RESOLVED",
            targetType = "FRAUD_ALERT",
            targetId = alertId,
            reason = actionTaken
        )
    }

    suspend fun markFraudAlertFalsePositive(
        alertId: String,
        reason: String,
        adminId: String,
        adminEmail: String
    ) {
        _fraudAlerts.value = _fraudAlerts.value.map { a ->
            if (a.alertId == alertId) {
                a.copy(
                    status = FraudAlertStatus.FALSE_POSITIVE,
                    resolvedAt = System.currentTimeMillis(),
                    resolvedByAdminUid = adminId,
                    mitigationAction = "False Positive: $reason"
                )
            } else a
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "FRAUD_ALERT_FALSE_POSITIVE",
            targetType = "FRAUD_ALERT",
            targetId = alertId,
            reason = reason
        )
    }

    suspend fun resolveFraudSignal(signalId: String, adminId: String, adminEmail: String, action: String) {
        _fraudSignals.value = _fraudSignals.value.map {
            if (it.signalId == signalId) it.copy(isReviewed = true, reviewStatus = "ACTIONED") else it
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "FRAUD_SIGNAL_ACTIONED",
            targetType = "FRAUD_SIGNAL",
            targetId = signalId,
            reason = action
        )
    }

    // ======================== PLATFORM SETTINGS ========================
    suspend fun updatePlatformSettings(
        newSettings: PlatformSettings,
        reason: String,
        adminId: String,
        adminEmail: String
    ) {
        _platformSettings.value = newSettings

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "PLATFORM_CONFIG_UPDATED",
            targetType = "PLATFORM_SETTINGS",
            targetId = "global_config",
            reason = reason
        )
    }

    // ======================== VIDEO & SHORTS MODERATION ========================
    suspend fun moderateContent(
        contentId: String,
        isShort: Boolean,
        action: AdminModerationStatus,
        reason: String,
        adminId: String,
        adminEmail: String
    ) {
        _videoModerationQueue.value = _videoModerationQueue.value.map { item ->
            if (item.contentId == contentId) {
                item.copy(status = action, flagReason = reason)
            } else item
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = if (isShort) "SHORT_MODERATION_${action.name}" else "VIDEO_MODERATION_${action.name}",
            targetType = if (isShort) "SHORT" else "VIDEO",
            targetId = contentId,
            newValue = action.name,
            reason = reason
        )
    }

    // ======================== LIVE STREAM MODERATION ========================
    suspend fun moderateLiveStream(
        streamId: String,
        restrict: Boolean,
        reason: String,
        adminId: String,
        adminEmail: String
    ) {
        _liveStreamsQueue.value = _liveStreamsQueue.value.map { stream ->
            if (stream.streamId == streamId) {
                stream.copy(
                    isRestricted = restrict,
                    restrictionReason = if (restrict) reason else null,
                    status = if (restrict) "RESTRICTED" else "LIVE"
                )
            } else stream
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = if (restrict) "LIVE_STREAM_RESTRICTED" else "LIVE_STREAM_UNRESTRICTED",
            targetType = "LIVE_STREAM",
            targetId = streamId,
            reason = reason
        )
    }

    // ======================== MESSAGING SAFETY ========================
    suspend fun resolveMessagingFlag(
        flagId: String,
        restrictSender: Boolean,
        actionNotes: String,
        adminId: String,
        adminEmail: String
    ) {
        val flag = _messagingFlags.value.find { it.flagId == flagId }
        _messagingFlags.value = _messagingFlags.value.map { item ->
            if (item.flagId == flagId) {
                item.copy(status = "RESOLVED")
            } else item
        }

        if (restrictSender && flag != null) {
            updateUserRestrictions(
                uid = flag.senderUid,
                uploadRestricted = false,
                commentRestricted = false,
                messageRestricted = true,
                adminId = adminId,
                adminEmail = adminEmail
            )
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "MESSAGING_SAFETY_RESOLVED",
            targetType = "MESSAGING_FLAG",
            targetId = flagId,
            reason = actionNotes
        )
    }

    // ======================== FINANCIAL LEDGER & REFUNDS ========================
    suspend fun issueAdministrativeRefund(
        transactionId: String,
        reason: String,
        adminId: String,
        adminEmail: String
    ) {
        _financialLedger.value = _financialLedger.value.map { tx ->
            if (tx.transactionId == transactionId) {
                tx.copy(status = "REFUNDED")
            } else tx
        }

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "ADMINISTRATIVE_REFUND_ISSUED",
            targetType = "TRANSACTION",
            targetId = transactionId,
            previousValue = "SETTLED",
            newValue = "REFUNDED",
            reason = reason
        )
    }

    // ======================== SYSTEM ANNOUNCEMENTS ========================
    suspend fun broadcastAnnouncement(
        title: String,
        message: String,
        targetAudience: String,
        adminId: String,
        adminEmail: String
    ) {
        val newAnnouncement = AdminAnnouncementItem(
            announcementId = "anc_${UUID.randomUUID().toString().take(8)}",
            title = title,
            message = message,
            targetAudience = targetAudience,
            sentByAdmin = adminEmail,
            timestamp = System.currentTimeMillis()
        )
        _adminAnnouncements.value = listOf(newAnnouncement) + _adminAnnouncements.value

        logAction(
            adminId = adminId,
            adminEmail = adminEmail,
            action = "ADMIN_ANNOUNCEMENT_BROADCAST",
            targetType = "ANNOUNCEMENT",
            targetId = newAnnouncement.announcementId,
            reason = "Broadcast to $targetAudience: $title"
        )
    }

    // ======================== AUDIT LOGGING ========================
    private suspend fun logAction(
        adminId: String,
        adminEmail: String,
        action: String,
        targetType: String,
        targetId: String,
        previousValue: String? = null,
        newValue: String? = null,
        reason: String
    ) {
        if (!isAuthorizedSuperAdmin(adminId)) {
            android.util.Log.w("AdminRepository", "Rejected logAction: $adminId is not an authorized Super Admin")
            return
        }
        val log = AdminAuditLog(
            logId = "log_${UUID.randomUUID().toString().take(8)}",
            adminId = adminId,
            adminEmail = adminEmail,
            action = action,
            targetType = targetType,
            targetId = targetId,
            previousValue = previousValue,
            newValue = newValue,
            reason = reason,
            timestamp = System.currentTimeMillis()
        )
        _auditLogs.value = listOf(log) + _auditLogs.value
        try {
            firebaseService.logAdminAction(log)
        } catch (_: Exception) {}
    }
}

// ======================== SUPPORTING DATA MODELS FOR ADMIN ========================

enum class AdminModerationStatus {
    APPROVED,
    FLAGGED,
    RESTRICTED,
    REMOVED
}

data class AdminVideoModerationItem(
    val contentId: String = "",
    val title: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val thumbnailUrl: String = "",
    val durationFormatted: String = "",
    val viewCount: Long = 0,
    val isShort: Boolean = false,
    val status: AdminModerationStatus = AdminModerationStatus.APPROVED,
    val reportCount: Int = 0,
    val flagReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class AdminLiveStreamItem(
    val streamId: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val title: String = "",
    val viewerCount: Int = 0,
    val status: String = "LIVE",
    val startedAt: Long = System.currentTimeMillis(),
    val isRestricted: Boolean = false,
    val restrictionReason: String? = null
)

data class AdminMessagingFlagItem(
    val flagId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val recipientUid: String = "",
    val recipientName: String = "",
    val messageSnippet: String = "",
    val riskCategory: String = "SPAM",
    val status: String = "FLAGGED",
    val timestamp: Long = System.currentTimeMillis()
)

data class AdminFinancialLedgerItem(
    val transactionId: String = "",
    val type: String = "THANKS_SUPPORT",
    val amountInr: Double = 0.0,
    val creatorShareInr: Double = 0.0,
    val platformShareInr: Double = 0.0,
    val senderName: String = "",
    val recipientName: String = "",
    val status: String = "SETTLED",
    val paymentMethod: String = "UPI",
    val timestamp: Long = System.currentTimeMillis()
)

data class AdminAnnouncementItem(
    val announcementId: String = "",
    val title: String = "",
    val message: String = "",
    val targetAudience: String = "ALL_USERS",
    val sentByAdmin: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
