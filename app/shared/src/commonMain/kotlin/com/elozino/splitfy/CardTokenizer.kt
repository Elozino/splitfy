package com.elozino.splitfy

import com.elozino.splitfy.domain.model.PaymentMethodType
import kotlin.random.Random

/** What the card form hands over once the number has been exchanged for a token. */
data class TokenizedCard(
    val providerToken: String,
    val brand: String,
    val last4: String,
    val type: PaymentMethodType = PaymentMethodType.CARD,
)

/**
 * Exchanges raw card details for an opaque provider token.
 *
 * The card number never leaves this boundary: only the token and the display digits
 * are sent to the splitfy API, which keeps the backend out of PCI scope entirely.
 */
interface CardTokenizer {
    suspend fun tokenize(
        cardNumber: String,
        expiryMonth: Int,
        expiryYear: Int,
        cvv: String,
        cardHolder: String,
    ): Result<TokenizedCard>
}

/**
 * Development stand-in for the payment provider's SDK.
 *
 * It performs the same validation the provider would and returns a fake token, so
 * the flow is exercised end to end without a real merchant account. Replace with the
 * provider SDK before taking real payments — this one cannot charge anything.
 */
class DevCardTokenizer : CardTokenizer {

    override suspend fun tokenize(
        cardNumber: String,
        expiryMonth: Int,
        expiryYear: Int,
        cvv: String,
        cardHolder: String,
    ): Result<TokenizedCard> {
        val digits = cardNumber.filter(Char::isDigit)

        if (digits.length !in 12..19) {
            return Result.failure(IllegalArgumentException("Enter a valid card number"))
        }
        if (!digits.passesLuhn()) {
            return Result.failure(IllegalArgumentException("That card number looks wrong"))
        }
        if (expiryMonth !in 1..12) {
            return Result.failure(IllegalArgumentException("Enter a valid expiry month"))
        }
        if (cvv.length !in 3..4 || !cvv.all(Char::isDigit)) {
            return Result.failure(IllegalArgumentException("Enter a valid CVV"))
        }
        if (cardHolder.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter the name on the card"))
        }

        // Only the last four digits survive; the rest is discarded here.
        return Result.success(
            TokenizedCard(
                providerToken = "tok_dev_" + Random.nextLong(1_000_000_000L, 9_999_999_999L),
                brand = digits.cardBrand(),
                last4 = digits.takeLast(4),
            )
        )
    }
}

/** The checksum every card issuer uses; catches most typos before a round trip. */
private fun String.passesLuhn(): Boolean {
    var sum = 0
    var double = false
    for (index in lastIndex downTo 0) {
        var digit = this[index] - '0'
        if (double) {
            digit *= 2
            if (digit > 9) digit -= 9
        }
        sum += digit
        double = !double
    }
    return sum % 10 == 0
}

private fun String.cardBrand(): String = when {
    startsWith("4") -> "visa"
    take(2).toIntOrNull() in 51..55 -> "mastercard"
    take(2) in setOf("34", "37") -> "amex"
    startsWith("50") || startsWith("65") -> "verve"
    else -> "card"
}
