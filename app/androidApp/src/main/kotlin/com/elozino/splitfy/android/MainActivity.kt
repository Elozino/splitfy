package com.elozino.splitfy.android

import android.os.Bundle
import android.content.Intent
import com.elozino.splitfy.invites.InviteLinks
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.elozino.splitfy.App

/** The only Activity: everything above it is shared Compose UI. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) intent?.dataString?.let(InviteLinks::open)
        setContent { App() }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let(InviteLinks::open)
    }
}

@Preview
@Composable
private fun AppAndroidPreview() {
    App()
}
