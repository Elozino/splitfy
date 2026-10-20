package com.elozino.splitfy.screens.payments.pay

import androidx.compose.runtime.Composable
import com.elozino.splitfy.screens.transactions.pay.PayScreen

@Composable
fun PayScreenWrapper(onNavigateBack: () -> Unit) {
    PayScreen(onNavigateBack = onNavigateBack)
}


