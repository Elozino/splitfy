package com.elozino.splitfy.backend.payments

import java.util.UUID
import kotlinx.coroutines.CancellationException

/** Explicit opt-in for local harnesses only. Not bound in production DI or registered in application routes. */
data class SandboxPaymentFlags(val wallet: Boolean = false, val payments: Boolean = false)

class SandboxPayments(
    private val repository: PaymentRepository,
    private val provider: PaymentProvider,
    private val verifier: PaystackWebhookVerifier,
    private val flags: SandboxPaymentFlags = SandboxPaymentFlags(),
) {
    private fun enabled() = check(flags.wallet && flags.payments) { "Sandbox payments are disabled" }

    /** Destination email must come from the authenticated user's verified account, not a checkout callback. */
    suspend fun topUp(user: UUID, amount: Long, key: String, verifiedEmail: String): PaymentIntent {
        enabled()
        return repository.create(user, PaymentKind.TOP_UP, amount, "NGN", key, verifiedEmail)
    }
    /** Recipient must be resolved by trusted server-side onboarding; client provider tokens are not accepted. */
    suspend fun withdrawal(user: UUID, amount: Long, key: String, verifiedRecipient: String): PaymentIntent {
        enabled()
        require(verifiedRecipient.startsWith("RCP_"))
        return repository.create(user, PaymentKind.WITHDRAWAL, amount, "NGN", key, verifiedRecipient)
    }

    // Dispatch is deliberately separate from creation. After a timeout/crash, reconcile the stored
    // reference before an operator retries; no automatic retry may generate a new reference.
    suspend fun checkout(reference: String): String {
        enabled()
        return provider.initialize(pendingIntent(reference, PaymentKind.TOP_UP))
    }
    suspend fun dispatchWithdrawal(reference: String) {
        enabled()
        provider.withdraw(pendingIntent(reference, PaymentKind.WITHDRAWAL))
    }
    private suspend fun pendingIntent(reference: String, kind: PaymentKind): PaymentIntent =
        requireNotNull(repository.find(reference)).also {
            require(it.status == PaymentStatus.PENDING && it.kind == kind)
        }

    suspend fun webhook(raw: ByteArray, signature: String?): Boolean {
        enabled()
        return verifier.verify(raw, signature)?.let { repository.apply(it) } ?: false
    }

    /** Reports differences for review/resend; never synthesizes a webhook or mutates balances. */
    suspend fun reconcile(limit: Int = 100): List<ReconciliationResult> {
        enabled()
        return repository.pending(limit).map { intent ->
            try {
                val observed = provider.verify(intent)
                val matches = observed.reference == intent.reference && observed.amount == intent.amount && observed.currency == intent.currency
                ReconciliationResult(intent.reference, if (!matches) "MISMATCH" else observed.status)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ReconciliationResult(intent.reference, "VERIFY_FAILED")
            }
        }
    }
}
data class ReconciliationResult(val reference: String, val providerStatus: String)
