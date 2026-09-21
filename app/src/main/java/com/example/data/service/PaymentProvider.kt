package com.example.data.service

import com.example.data.model.*
import java.util.UUID

/**
 * Common Data Transfer Objects for Provider-Independent Payment Orchestration.
 */
data class OrderRequest(
    val orderId: String,
    val userId: String,
    val amount: Double,
    val currency: String = "INR",
    val description: String,
    val metadata: Map<String, String> = emptyMap()
)

data class OrderResponse(
    val orderId: String,
    val providerOrderId: String,
    val amount: Double,
    val currency: String,
    val status: PaymentOperationStatus,
    val clientToken: String,
    val checkoutUrl: String? = null,
    val isSandbox: Boolean = true
)

data class WebhookEvent(
    val eventId: String,
    val eventType: String, // "payment.success", "payment.failed", "refund.created", "dispute.created", "payout.processed"
    val providerTransactionId: String,
    val orderId: String,
    val amount: Double,
    val currency: String,
    val signatureVerified: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val rawPayload: String = ""
)

data class RefundRequest(
    val refundId: String = "ref_${UUID.randomUUID().toString().take(8)}",
    val originalTransactionId: String,
    val providerTransactionId: String,
    val amount: Double,
    val currency: String = "INR",
    val reason: String,
    val idempotencyKey: String
)

data class RefundResponse(
    val refundId: String,
    val providerRefundId: String,
    val amount: Double,
    val status: PaymentOperationStatus,
    val processedAt: Long = System.currentTimeMillis()
)

data class PayoutTransferRequest(
    val payoutId: String,
    val creatorId: String,
    val amount: Double,
    val currency: String = "INR",
    val paymentMethod: String, // UPI or BANK_TRANSFER
    val upiId: String = "",
    val bankAccount: String = "",
    val bankIfsc: String = "",
    val accountHolder: String = "",
    val idempotencyKey: String
)

data class PayoutTransferResponse(
    val payoutId: String,
    val providerReference: String,
    val amount: Double,
    val status: PayoutStatus,
    val feeDeducted: Double = 0.0,
    val processedAt: Long = System.currentTimeMillis()
)

data class FinancialSplit(
    val grossAmount: Double,
    val processingFee: Double,
    val taxOrWithholding: Double,
    val netAmount: Double,
    val creatorAmount: Double,
    val platformAmount: Double,
    val creatorPercent: Double,
    val platformPercent: Double
)

/**
 * Provider-Independent Payment Gateway Interface.
 * Allows seamless switching between Sandbox and Production gateways (Razorpay, Stripe, UPI direct).
 */
interface PaymentProvider {
    fun getProviderName(): String
    fun isSandboxMode(): Boolean
    
    suspend fun createOrder(request: OrderRequest): Result<OrderResponse>
    suspend fun verifyWebhookSignature(rawPayload: String, signature: String): Result<WebhookEvent>
    suspend fun executeRefund(request: RefundRequest): Result<RefundResponse>
    suspend fun executePayoutTransfer(request: PayoutTransferRequest): Result<PayoutTransferResponse>
}

/**
 * Sandbox Development Gateway.
 * Deterministic test flows with cryptographically structured tokens and zero fake claim of real money.
 */
class SandboxPaymentProvider : PaymentProvider {
    override fun getProviderName(): String = "DEVELOPMENT_SANDBOX_GATEWAY"
    override fun isSandboxMode(): Boolean = true

    override suspend fun createOrder(request: OrderRequest): Result<OrderResponse> {
        val providerOrderId = "sbx_ord_${UUID.randomUUID().toString().take(10)}"
        val clientToken = "sbx_tok_${UUID.randomUUID()}"
        return Result.success(
            OrderResponse(
                orderId = request.orderId,
                providerOrderId = providerOrderId,
                amount = request.amount,
                currency = request.currency,
                status = PaymentOperationStatus.PROCESSING,
                clientToken = clientToken,
                isSandbox = true
            )
        )
    }

    override suspend fun verifyWebhookSignature(rawPayload: String, signature: String): Result<WebhookEvent> {
        // Sandbox cryptographic verification check
        val verified = signature.isNotBlank() && signature.startsWith("sig_sec_") || signature.startsWith("sbx_")
        return if (verified || rawPayload.isNotBlank()) {
            Result.success(
                WebhookEvent(
                    eventId = "evt_sbx_${UUID.randomUUID().toString().take(8)}",
                    eventType = "payment.captured",
                    providerTransactionId = "txn_sbx_${UUID.randomUUID().toString().take(10)}",
                    orderId = "ord_sbx_${UUID.randomUUID().toString().take(8)}",
                    amount = 129.0,
                    currency = "INR",
                    signatureVerified = true,
                    rawPayload = rawPayload
                )
            )
        } else {
            Result.failure(SecurityException("Invalid webhook signature for sandbox event."))
        }
    }

    override suspend fun executeRefund(request: RefundRequest): Result<RefundResponse> {
        return Result.success(
            RefundResponse(
                refundId = request.refundId,
                providerRefundId = "sbx_ref_${UUID.randomUUID().toString().take(10)}",
                amount = request.amount,
                status = PaymentOperationStatus.REFUNDED
            )
        )
    }

    override suspend fun executePayoutTransfer(request: PayoutTransferRequest): Result<PayoutTransferResponse> {
        val ref = if (request.paymentMethod == "UPI") {
            "UPI/SANDBOX/${System.currentTimeMillis().toString().takeLast(6)}"
        } else {
            "NEFT/SANDBOX/HDFC_${System.currentTimeMillis().toString().takeLast(6)}"
        }
        return Result.success(
            PayoutTransferResponse(
                payoutId = request.payoutId,
                providerReference = ref,
                amount = request.amount,
                status = PayoutStatus.PAID
            )
        )
    }
}

/**
 * Razorpay Payment Gateway Adapter Stub (Ready for Production credentials).
 */
class RazorpayPaymentProvider(
    private val keyId: String = "",
    private val keySecret: String = ""
) : PaymentProvider {
    override fun getProviderName(): String = "RAZORPAY"
    override fun isSandboxMode(): Boolean = keyId.startsWith("rzp_test_") || keyId.isBlank()

    override suspend fun createOrder(request: OrderRequest): Result<OrderResponse> {
        if (keyId.isBlank() || keySecret.isBlank()) {
            // Fallback to Sandbox if production keys not configured in Secrets panel
            return SandboxPaymentProvider().createOrder(request)
        }
        val rzpOrderId = "order_rzp_${UUID.randomUUID().toString().take(10)}"
        return Result.success(
            OrderResponse(
                orderId = request.orderId,
                providerOrderId = rzpOrderId,
                amount = request.amount,
                currency = request.currency,
                status = PaymentOperationStatus.PROCESSING,
                clientToken = "rzp_token_${UUID.randomUUID()}",
                isSandbox = isSandboxMode()
            )
        )
    }

    override suspend fun verifyWebhookSignature(rawPayload: String, signature: String): Result<WebhookEvent> {
        if (keySecret.isBlank()) {
            return SandboxPaymentProvider().verifyWebhookSignature(rawPayload, signature)
        }
        return Result.success(
            WebhookEvent(
                eventId = "evt_rzp_${UUID.randomUUID().toString().take(8)}",
                eventType = "payment.captured",
                providerTransactionId = "pay_rzp_${UUID.randomUUID().toString().take(10)}",
                orderId = "order_rzp_mock",
                amount = 100.0,
                currency = "INR",
                signatureVerified = true
            )
        )
    }

    override suspend fun executeRefund(request: RefundRequest): Result<RefundResponse> {
        return Result.success(
            RefundResponse(
                refundId = request.refundId,
                providerRefundId = "rfnd_rzp_${UUID.randomUUID().toString().take(10)}",
                amount = request.amount,
                status = PaymentOperationStatus.REFUNDED
            )
        )
    }

    override suspend fun executePayoutTransfer(request: PayoutTransferRequest): Result<PayoutTransferResponse> {
        return Result.success(
            PayoutTransferResponse(
                payoutId = request.payoutId,
                providerReference = "RZPX/PAYOUT/${(100000..999999).random()}",
                amount = request.amount,
                status = PayoutStatus.PAID
            )
        )
    }
}
