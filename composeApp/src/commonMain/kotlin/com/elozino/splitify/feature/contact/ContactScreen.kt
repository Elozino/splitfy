package com.elozino.splitify.feature.contact

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen() {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Contacts", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO: Add Contact */ },
                containerColor = Color(0xFFFEBB1B),
                contentColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact")
            }
        }
    ) { padding ->
        val contacts = listOf(
            ContactItemData("Andrew Ainsley", "@andrew_a"),
            ContactItemData("Alfonzo Schuessler", "@alfonzo_s"),
            ContactItemData("Maryland Winkles", "@maryland_w"),
            ContactItemData("Darron Kulikowski", "@darron_k"),
            ContactItemData("Geoffrey Mott", "@geoffrey_m"),
            ContactItemData("Rania Ehrman", "@rania_e"),
            ContactItemData("Ronald Richards", "@ronald_r")
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(contacts) { contact ->
                ContactItem(contact)
                HorizontalDivider(
                    modifier = Modifier.padding(start = 64.dp, top = 8.dp, bottom = 8.dp),
                    color = Color.LightGray.copy(alpha = 0.3f)
                )
            }
        }
    }
}

data class ContactItemData(
    val name: String,
    val username: String
)

@Composable
fun ContactItem(data: ContactItemData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.LightGray.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = data.name.take(1),
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = data.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text = data.username, fontSize = 12.sp, color = Color.Gray)
        }
        
        Button(
            onClick = { /* TODO: Invite/Action */ },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color(0xFFFEBB1B)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFEBB1B)),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp),
            elevation = null
        ) {
            Text("Invite", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
