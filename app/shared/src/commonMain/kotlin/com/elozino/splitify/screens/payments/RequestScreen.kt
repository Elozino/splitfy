package com.elozino.splitify.screens

import androidx.compose.runtime.Composable

@Composable
fun RequestScreenWrapper(onNavigateBack: () -> Unit) {
    RequestScreen(onNavigateBack = onNavigateBack)
}
