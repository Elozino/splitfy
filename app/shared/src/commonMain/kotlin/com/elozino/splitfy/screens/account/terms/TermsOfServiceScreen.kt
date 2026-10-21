package com.elozino.splitfy.screens.account.terms

import androidx.compose.runtime.Composable
import com.elozino.splitfy.screens.account.legal.LegalCopy
import com.elozino.splitfy.screens.account.legal.LegalDocumentScreen

@Composable
fun TermsOfServiceScreen(onNavigateBack: () -> Unit) {
    LegalDocumentScreen(title = "Terms of Service", sections = LegalCopy.terms, onNavigateBack = onNavigateBack)
}
