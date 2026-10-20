package com.elozino.splitfy.screens.account.contact_support

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elozino.splitfy.screens.account.legal.SupportContact
import com.elozino.splitfy.screens.account.legal.rememberSupportMailer
import com.elozino.splitfy.ui.theme.primaryLight

/**
 * Every option opens the user's mail app addressed to [SupportContact.EMAIL] with
 * a subject that tells support what kind of request it is. If no mail app can take
 * it, the address is shown so it can be copied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSupportScreen(onNavigateBack: () -> Unit) {
    val sendMail = rememberSupportMailer()
    var mailAppMissing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Contact Support", fontWeight = FontWeight.Bold) }, navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }, containerColor = Color.White
    ) { padding ->
        val options = listOf(
            SupportOption(Icons.Default.Email, "Email support", "Questions about using splitfy", "splitfy support request"),
            SupportOption(Icons.Default.BugReport, "Report a problem", "Something isn't working as it should", "splitfy problem report"),
            SupportOption(Icons.Default.PrivacyTip, "Privacy request", "Access, correct or export your data", "splitfy privacy request"),
        )
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            options.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { mailAppMissing = !sendMail(option.subject) }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(option.icon, contentDescription = null, tint = primaryLight)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(option.title, fontWeight = FontWeight.Bold)
                        Text(option.detail, color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.padding(top = 16.dp))
            Text(
                if (mailAppMissing) "No mail app is set up on this device. Write to us at:" else "Or write to us at:",
                color = if (mailAppMissing) Color.Red else Color.Gray,
                fontSize = 13.sp,
            )
            SelectionContainer {
                Text(SupportContact.EMAIL, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
            Text(
                "We usually reply within two working days.",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

private data class SupportOption(val icon: ImageVector, val title: String, val detail: String, val subject: String)
