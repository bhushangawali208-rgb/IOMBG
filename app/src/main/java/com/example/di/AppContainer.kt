package com.example.di

import android.content.Context
import com.example.data.remote.FirebaseService
import com.example.data.remote.FirestoreMessagingService
import com.example.data.remote.IombgBackendService
import com.example.data.remote.IombgBackendServiceImpl
import com.example.data.remote.MessagingService
import com.example.data.remote.RecommendationEngine
import com.example.data.repository.*
import com.example.data.service.*
import com.google.firebase.analytics.FirebaseAnalytics

class AppContainer(context: Context) {
    val firebaseService: FirebaseService by lazy {
        FirebaseService(analyticsProvider = {
            try {
                FirebaseAnalytics.getInstance(context)
            } catch (e: Exception) {
                null
            }
        })
    }
    val backendService: IombgBackendService by lazy { IombgBackendServiceImpl() }

    // Media & Infrastructure Provider Services
    val videoStorageService: VideoStorageService by lazy { FirebaseVideoStorageService() }
    val videoProcessingService: VideoProcessingService by lazy { CloudVideoProcessingService(backendService) }
    val videoPlaybackService: VideoPlaybackService by lazy { StandardVideoPlaybackService() }
    val cdnService: CDNService by lazy { CloudCdnService() }
    val liveStreamingService: LiveStreamingService by lazy { ProductionLiveStreamingService() }
    val liveRecordingService: LiveRecordingService by lazy { CloudLiveRecordingService() }
    val paymentService: PaymentService by lazy { PaymentService(backendService = backendService) }

    val recommendationEngine: RecommendationEngine by lazy { RecommendationEngine(context) }
    val fcmTokenManager: com.example.notifications.FcmTokenManager by lazy { com.example.notifications.FcmTokenManager.getInstance(context) }
    val recommendationEventRepository: RecommendationEventRepository by lazy {
        RecommendationEventRepository(context, engine = recommendationEngine, backendService = backendService)
    }
    val localMediaPreviewRepository: LocalMediaPreviewRepository by lazy { LocalMediaPreviewRepository(context) }
    val authRepository: AuthRepository by lazy { AuthRepository(context, firebaseService, fcmTokenManager = fcmTokenManager) }
    val channelRepository: ChannelRepository by lazy { ChannelRepository(firebaseService) }
    val videoRepository: VideoRepository by lazy {
        VideoRepository(
            firebaseService = firebaseService,
            storageService = videoStorageService,
            processingService = videoProcessingService,
            cdnService = cdnService,
            localPreviewRepository = localMediaPreviewRepository
        )
    }
    val socialRepository: SocialRepository by lazy { SocialRepository(firebaseService, recommendationEventRepository) }
    val liveStreamRepository: LiveStreamRepository by lazy {
        LiveStreamRepository(
            firebaseService = firebaseService,
            recommendationEventRepository = recommendationEventRepository,
            streamingService = liveStreamingService,
            recordingService = liveRecordingService,
            cdnService = cdnService
        )
    }
    val messagingService: MessagingService by lazy {
        FirestoreMessagingService(
            firestore = firebaseService.firestore,
            auth = firebaseService.auth
        )
    }
    val chatRepository: ChatRepository by lazy {
        ChatRepository(
            firebaseService = firebaseService,
            messagingService = messagingService,
            socialRepository = socialRepository
        )
    }
    val monetizationRepository: MonetizationRepository by lazy { MonetizationRepository(firebaseService, backendService) }
    val walletRepository: WalletRepository by lazy { WalletRepository(firebaseService, backendService) }
    val premiumRepository: PremiumRepository by lazy { PremiumRepository(paymentService = paymentService) }
    val boostRepository: BoostRepository by lazy { BoostRepository(firebaseService = firebaseService, paymentService = paymentService, videoRepository = videoRepository) }
    val adminRepository: AdminRepository by lazy { AdminRepository(firebaseService, backendService) }
    val watchHistoryRepository: WatchHistoryRepository by lazy { WatchHistoryRepository(context, firebaseService) }
}
