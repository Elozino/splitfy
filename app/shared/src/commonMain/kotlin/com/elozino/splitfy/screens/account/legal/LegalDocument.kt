package com.elozino.splitfy.screens.account.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A scrolling legal document: title bar, optional draft banner, then numbered sections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(
    title: String,
    sections: List<LegalSection>,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        containerColor = Color.White,
    ) { padding ->
        SelectionContainer {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                LegalReviewBanner()
                Text(
                    "Last updated: ${LegalCopy.LAST_UPDATED}",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                sections.forEach { section ->
                    Text(section.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        section.body,
                        color = Color.DarkGray,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                    )
                }
            }
        }
    }
}

/** Visible until [LegalCopy.LEGAL_REVIEW_PENDING] is switched off after legal sign-off. */
@Composable
fun LegalReviewBanner() {
    if (!LegalCopy.LEGAL_REVIEW_PENDING) return
    Text(
        "Draft — pending legal review. Bracketed items are still to be confirmed.",
        color = Color(0xFF7A4B00),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(Color(0xFFFFF4E0), RoundedCornerShape(8.dp))
            .padding(12.dp),
    )
}
