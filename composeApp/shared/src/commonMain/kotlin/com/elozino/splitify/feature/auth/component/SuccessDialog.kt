package com.elozino.splitify.feature.auth.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun SuccessDialog(
    onDismissRequest: () -> Unit,
    icon: ImageVector = Icons.Default.Person,
    title: String = "Sign in Successful!",
    message: String = "Please wait...\nYou will be directed to the homepage."
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color(0xFFFEBB1B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = message,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color(0xFFFEBB1B),
                    strokeWidth = 4.dp
                )
            }
        }
    }
}
