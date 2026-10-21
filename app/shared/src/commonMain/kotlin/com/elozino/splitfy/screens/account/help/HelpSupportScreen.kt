package com.elozino.splitfy.screens.account.help

import androidx.compose.material3.MaterialTheme
import com.elozino.splitfy.screens.account.legal.rememberSupportMailer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFAQ: () -> Unit,
    onNavigateToContactSupport: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToTermsOfService: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Help & Support", fontWeight = FontWeight.Bold) }, navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }, containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val sendMail = rememberSupportMailer()
        // Only entries that lead somewhere. Add store-rating and website links once
        // those exist rather than shipping rows that do nothing.
        val items = listOf(
            "FAQ" to onNavigateToFAQ,
            "Contact Support" to onNavigateToContactSupport,
            "Send Feedback" to { sendMail("splitfy feedback"); Unit },
            "Privacy Policy" to onNavigateToPrivacyPolicy,
            "Terms of Service" to onNavigateToTermsOfService,
        )
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            items(items) { (title, onClick) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
