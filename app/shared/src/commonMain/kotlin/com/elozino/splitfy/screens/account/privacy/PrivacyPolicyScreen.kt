package com.elozino.splitfy.screens.account.privacy

import androidx.compose.runtime.Composable
import com.elozino.splitfy.screens.account.legal.LegalCopy
import com.elozino.splitfy.screens.account.legal.LegalDocumentScreen

@Composable
fun PrivacyPolicyScreen(onNavigateBack: () -> Unit) {
    LegalDocumentScreen(title = "Privacy Policy", sections = LegalCopy.privacy, onNavigateBack = onNavigateBack)
}
