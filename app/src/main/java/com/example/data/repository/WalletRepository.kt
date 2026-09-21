package com.example.data.repository

import com.example.data.model.*
import com.example.data.remote.FirebaseService
import com.example.data.remote.IombgBackendService
import com.example.data.remote.IombgBackendServiceImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * WalletRepository: Manages creator earnings, financial ledger, and payouts.
 *
 * All financial state (balances, revenue share calculations, payout status)
 * is SERVER-AUTHORITATIVE and read-only on the client. Writing to ledger or balances
 * is exclusively executed through backend Cloud Functions.
 */
class WalletRepository(
    private val firebaseService: FirebaseService,
    private val backendService: IombgBackendService = IombgBackendServiceImpl()
) {
    private val _walletState = MutableStateFlow<Wallet?>(null)
    val walletState: StateFlow<Wallet?> = _walletState.asStateFlow()

    private val _ledgerEntries = MutableStateFlow<List<LedgerEntry>>(emptyList())
    val ledgerEntries: StateFlow<List<LedgerEntry>> = _ledgerEntries.asStateFlow()

    private val _payoutRequests = MutableStateFlow<List<PayoutRequest>>(emptyList())
    val payoutRequests: StateFlow<List<PayoutRequest>> = _payoutRequests.asStateFlow()

    init {
        loadFinancialData()
    }

    private fun loadFinancialData() {
        // Real-money monetization is currently disabled pending production backend launch.
        // Wallet state is initialized without fake balances or mock earnings.
        _walletState.value = null
        _ledgerEntries.value = emptyList()
        _payoutRequests.value = emptyList()
    }

    /**
     * Creator payouts are currently disabled pending production monetization system launch.
     */
    suspend fun requestPayout(
        channelId: String,
        ownerUid: String,
        amount: Double,
        paymentMethod: String,
        upiId: String,
        bankAccount: String,
        bankIfsc: String,
        accountHolderName: String
    ): Result<PayoutRequest> {
        return Result.failure(Exception("Payouts are not available yet. Creator payouts will be enabled after IOMBG launches its production monetization system."))
    }
}
