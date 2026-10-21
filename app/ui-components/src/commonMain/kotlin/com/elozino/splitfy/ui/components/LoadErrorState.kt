package com.elozino.splitfy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elozino.splitfy.ui.theme.primaryLight

/**
 * Shown when a screen's first load fails and there is nothing to show. Without it
 * the screen fell through to its empty state ("No expenses yet"), which reads as a
 * true statement about the user's data rather than a failure.
 */
@Composable
fun LoadErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Couldn't load this", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryLight),
        ) {
            Text("Try again", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}
