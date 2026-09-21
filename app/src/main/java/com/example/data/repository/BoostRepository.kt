package com.example.data.repository

import com.example.data.model.*
import com.example.data.remote.FirebaseService
import com.example.data.service.PaymentService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BoostRepository(
    private val firebaseService: FirebaseService,
    private val paymentService: PaymentService = PaymentService(),
    private val videoRepository: VideoRepository? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // Configurable Boost options
    private val _boostOptions = MutableStateFlow<List<BoostOption>>(
        listOf(
            BoostOption(
                optionId = "opt_starter",
                title = "Starter Discovery",
                budget = 299.0,
                currency = "INR",
                durationDays = 3,
                estimatedReach = "3,000 - 5,500 potential impressions",
                recommendedFor = "Quick testing for newly published videos or shorts"
            ),
            BoostOption(
                optionId = "opt_growth",
                title = "Channel Growth Accelerator",
                budget = 999.0,
                currency = "INR",
                durationDays = 7,
                estimatedReach = "10,000 - 18,000 potential impressions",
                recommendedFor = "Recommended for standard long-form videos & high-engagement shorts"
            ),
            BoostOption(
                optionId = "opt_viral",
                title = "Trending Impact Boost",
                budget = 2499.0,
                currency = "INR",
                durationDays = 14,
                estimatedReach = "25,000 - 45,000 potential impressions",
                recommendedFor = "Major releases, music videos, documentary episodes, and podcasts"
            )
        )
    )
    val boostOptions: StateFlow<List<BoostOption>> = _boostOptions.asStateFlow()

    private val _campaigns = MutableStateFlow<List<BoostCampaign>>(emptyList())
    val campaigns: StateFlow<List<BoostCampaign>> = _campaigns.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadInitialCampaigns()
    }

    private fun loadInitialCampaigns() {
        _campaigns.value = emptyList()
    }

    /**
     * Boost is currently disabled pending production payment gateway launch.
     */
    suspend fun createBoostCampaign(
        payerUserId: String,
        channel: Channel,
        contentId: String,
        contentType: String,
        contentTitle: String,
        contentThumbnailUrl: String,
        budget: Double,
        currency: String = "INR",
        durationDays: Int = 7,
        targetCategory: String = "All"
    ): Result<BoostCampaign> {
        return Result.failure(Exception("Boost is coming soon. Paid promotion is not available yet."))
    }

    fun pauseCampaign(campaignId: String) {
        _campaigns.value = _campaigns.value.map {
            if (it.campaignId == campaignId || it.boostId == campaignId) it.copy(status = BoostStatus.PAUSED) else it
        }
    }

    fun resumeCampaign(campaignId: String) {
        _campaigns.value = _campaigns.value.map {
            if (it.campaignId == campaignId || it.boostId == campaignId) it.copy(status = BoostStatus.ACTIVE) else it
        }
    }

    fun cancelCampaign(campaignId: String) {
        _campaigns.value = _campaigns.value.map {
            if (it.campaignId == campaignId || it.boostId == campaignId) it.copy(status = BoostStatus.CANCELLED) else it
        }
    }

    fun clearError() {
        _error.value = null
    }
}
