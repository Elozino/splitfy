package com.elozino.splitfy.screens.payments.request

import androidx.compose.runtime.Composable
import com.elozino.splitfy.screens.transactions.request.RequestScreen

@Composable
fun RequestScreenWrapper(onNavigateBack: () -> Unit) {
    RequestScreen(onNavigateBack = onNavigateBack)
}


