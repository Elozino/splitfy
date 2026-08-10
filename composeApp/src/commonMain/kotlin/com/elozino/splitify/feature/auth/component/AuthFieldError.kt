package com.elozino.splitify.feature.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One place every auth form renders an error, so a field error from local
 * validation and one returned by the API look identical.
 */
@Composable
internal fun AuthFieldError(message: String?) {
    if (message == null) return
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = message, fontSize = 13.sp, color = ErrorRed)
}

internal val ErrorRed = Color(0xFFD32F2F)
