package com.elozino.splitify.feature.request

import androidx.compose.runtime.Composable
import com.elozino.splitify.feature.transaction.RequestScreen

@Composable
fun RequestScreenWrapper(onNavigateBack: () -> Unit) {
    RequestScreen(onNavigateBack = onNavigateBack)
}
