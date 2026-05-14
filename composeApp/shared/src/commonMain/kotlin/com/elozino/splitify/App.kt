package com.elozino.splitify

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.elozino.splitify.core.presentation.navigation.NavSetup
import com.elozino.splitify.ui.theme.AppTheme

@Composable
@Preview
fun App() {
    AppTheme {
        NavSetup()
    }
}