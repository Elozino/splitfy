package com.elozino.splitify.api

/**
 * The single source of truth for URL paths.
 *
 * The server registers routes from these constants and the client builds requests
 * from them, so a renamed endpoint is a compile error rather than a 404 in production.
 */
object ApiRoutes {

    const val API_PREFIX = "/api/v1"

    object Auth {
        const val BASE = "$API_PREFIX/auth"
        const val REGISTER = "$BASE/register"
        const val LOGIN = "$BASE/login"
        const val REFRESH = "$BASE/refresh"
        const val LOGOUT = "$BASE/logout"
        const val FORGOT_PASSWORD = "$BASE/forgot-password"
        const val RESET_PASSWORD = "$BASE/reset-password"
        const val VERIFY_OTP = "$BASE/verify-otp"
    }

    object Users {
        const val BASE = "$API_PREFIX/users"
        const val ME = "$BASE/me"
        fun byId(id: String) = "$BASE/$id"
        const val SEARCH = "$BASE/search"
    }

    object Groups {
        const val BASE = "$API_PREFIX/groups"
        fun byId(id: String) = "$BASE/$id"
        fun members(id: String) = "$BASE/$id/members"
        fun member(groupId: String, userId: String) = "$BASE/$groupId/members/$userId"
        fun balances(id: String) = "$BASE/$id/balances"
        fun settleUp(id: String) = "$BASE/$id/settle-up"
        fun archive(id: String) = "$BASE/$id/archive"
    }

    object Expenses {
        fun forGroup(groupId: String) = "${Groups.BASE}/$groupId/expenses"
        fun byId(groupId: String, expenseId: String) = "${Groups.BASE}/$groupId/expenses/$expenseId"
    }

    object Settlements {
        fun forGroup(groupId: String) = "${Groups.BASE}/$groupId/settlements"
        fun byId(groupId: String, settlementId: String) = "${Groups.BASE}/$groupId/settlements/$settlementId"
        fun confirm(groupId: String, settlementId: String) =
            "${Groups.BASE}/$groupId/settlements/$settlementId/confirm"
    }

    object Wallet {
        const val BASE = "$API_PREFIX/wallet"
        const val LEDGER = "$BASE/ledger"
        const val TOP_UP = "$BASE/top-up"
        const val WITHDRAW = "$BASE/withdraw"
    }

    object PaymentMethods {
        const val BASE = "$API_PREFIX/payment-methods"
        fun byId(id: String) = "$BASE/$id"
        fun makeDefault(id: String) = "$BASE/$id/default"
    }

    object Transfers {
        const val BASE = "$API_PREFIX/transfers"
        fun byId(id: String) = "$BASE/$id"
        fun approve(id: String) = "$BASE/$id/approve"
        fun decline(id: String) = "$BASE/$id/decline"
    }

    object Activity {
        const val BASE = "$API_PREFIX/activity"
    }

    object Notifications {
        const val BASE = "$API_PREFIX/notifications"
        fun read(id: String) = "$BASE/$id/read"
        const val READ_ALL = "$BASE/read-all"
    }

    object Contacts {
        const val BASE = "$API_PREFIX/contacts"
        fun favorite(userId: String) = "$BASE/$userId/favorite"
    }

    object System {
        const val HEALTH = "/health"
        const val READY = "/ready"
        const val METRICS = "/metrics"
        const val OPENAPI = "/openapi"
    }
}
