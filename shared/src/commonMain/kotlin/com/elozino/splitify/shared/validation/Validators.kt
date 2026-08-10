package com.elozino.splitify.shared.validation

import com.elozino.splitify.shared.api.ApiErrorCode
import com.elozino.splitify.shared.api.FieldError
import com.elozino.splitify.shared.api.dto.CreateExpenseRequest
import com.elozino.splitify.shared.api.dto.CreateGroupRequest
import com.elozino.splitify.shared.api.dto.LoginRequest
import com.elozino.splitify.shared.api.dto.RegisterRequest
import com.elozino.splitify.shared.domain.model.CurrencyCode

/**
 * Request validation shared by the client and the server.
 *
 * The client runs these to show inline field errors before a round trip; the server
 * runs the exact same rules because client-side validation is a UX affordance, not
 * a security control.
 */
object Validators {

    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 128
    const val MAX_NAME_LENGTH = 60
    const val MAX_DESCRIPTION_LENGTH = 140
    const val MAX_NOTES_LENGTH = 1000

    /** Deliberately permissive: RFC 5322 in full rejects addresses that work fine. */
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    private val E164_REGEX = Regex("^\\+?[1-9]\\d{6,14}$")

    fun email(value: String, field: String = "email"): FieldError? = when {
        value.isBlank() -> FieldError(field, "Email is required")
        value.length > 254 -> FieldError(field, "Email is too long")
        !EMAIL_REGEX.matches(value.trim()) -> FieldError(field, "Enter a valid email address")
        else -> null
    }

    fun password(value: String, field: String = "password"): FieldError? = when {
        value.isEmpty() -> FieldError(field, "Password is required")
        value.length < MIN_PASSWORD_LENGTH ->
            FieldError(field, "Password must be at least $MIN_PASSWORD_LENGTH characters")
        value.length > MAX_PASSWORD_LENGTH -> FieldError(field, "Password is too long")
        value.none(Char::isDigit) -> FieldError(field, "Password must contain a number")
        value.none(Char::isLetter) -> FieldError(field, "Password must contain a letter")
        else -> null
    }

    fun displayName(value: String, field: String = "displayName"): FieldError? = when {
        value.isBlank() -> FieldError(field, "Name is required")
        value.trim().length < 2 -> FieldError(field, "Name is too short")
        value.length > MAX_NAME_LENGTH -> FieldError(field, "Name must be $MAX_NAME_LENGTH characters or fewer")
        else -> null
    }

    fun phoneNumber(value: String?, field: String = "phoneNumber"): FieldError? = when {
        value.isNullOrBlank() -> null
        !E164_REGEX.matches(value.filterNot { it == ' ' || it == '-' }) ->
            FieldError(field, "Enter a valid phone number")
        else -> null
    }

    fun currency(value: CurrencyCode, field: String = "currency"): FieldError? =
        if (CurrencyCode.isValid(value.code)) null
        else FieldError(field, "Unsupported currency '${value.code}'")

    fun register(request: RegisterRequest): List<FieldError> = listOfNotNull(
        email(request.email),
        password(request.password),
        displayName(request.displayName),
        phoneNumber(request.phoneNumber),
        currency(request.defaultCurrency, "defaultCurrency"),
    )

    fun login(request: LoginRequest): List<FieldError> = listOfNotNull(
        email(request.email),
        if (request.password.isEmpty()) FieldError("password", "Password is required") else null,
    )

    fun createGroup(request: CreateGroupRequest): List<FieldError> = listOfNotNull(
        displayName(request.name, "name"),
        currency(request.currency),
        request.description
            ?.takeIf { it.length > MAX_NOTES_LENGTH }
            ?.let { FieldError("description", "Description is too long") },
    )

    fun createExpense(request: CreateExpenseRequest): List<FieldError> = buildList {
        if (request.description.isBlank()) {
            add(FieldError("description", "Description is required"))
        } else if (request.description.length > MAX_DESCRIPTION_LENGTH) {
            add(FieldError("description", "Description must be $MAX_DESCRIPTION_LENGTH characters or fewer"))
        }
        if (!request.total.isPositive) add(FieldError("total", "Amount must be greater than zero"))
        currency(request.total.currency, "total.currency")?.let(::add)
        request.notes?.takeIf { it.length > MAX_NOTES_LENGTH }
            ?.let { add(FieldError("notes", "Notes are too long")) }
        if (request.split.method.name == "EQUAL" && request.split.participantIds.isEmpty()) {
            add(FieldError("split.participantIds", "Select at least one participant"))
        }
    }
}

/** Thrown by the server (and catchable by shared code) when validation fails. */
class ValidationException(
    val fieldErrors: List<FieldError>,
    val code: ApiErrorCode = ApiErrorCode.VALIDATION_FAILED,
) : Exception(fieldErrors.joinToString { "${it.field}: ${it.message}" })

fun List<FieldError>.throwIfInvalid() {
    if (isNotEmpty()) throw ValidationException(this)
}
