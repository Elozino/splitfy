package com.elozino.splitfy.screens.account.language

import androidx.compose.material3.MaterialTheme
import com.elozino.splitfy.ui.theme.AppTheme
import com.elozino.splitfy.screens.account.shared.AccountViewModel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLanguageScreen(
    onNavigateBack: () -> Unit,
    viewModel: AccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("App Language", fontWeight = FontWeight.Bold) }, navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }, containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val languages = listOf(
            "English (US)",
            "English (UK)",
            "Mandarin",
            "Spanish",
            "Hindi",
            "French",
            "Arabic",
            "Bengali",
            "Russian",
            "Japanese"
        )
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            items(languages) { lang ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.selectLanguage(lang) }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(lang, fontWeight = FontWeight.Medium)
                    RadioButton(
                        selected = state.selectedLanguage == lang,
                        onClick = { viewModel.selectLanguage(lang) },
                        colors = RadioButtonDefaults.colors(selectedColor = AppTheme.colors.brand)
                    )
                }
            }
        }
    }
}
