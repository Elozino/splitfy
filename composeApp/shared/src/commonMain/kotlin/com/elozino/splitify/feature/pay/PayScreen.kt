package com.elozino.splitify.feature.pay

import androidx.compose.runtime.Composable
import com.elozino.splitify.feature.transaction.PayScreen

@Composable
fun PayScreenWrapper(onNavigateBack: () -> Unit) {
    PayScreen(onNavigateBack = onNavigateBack)
}
