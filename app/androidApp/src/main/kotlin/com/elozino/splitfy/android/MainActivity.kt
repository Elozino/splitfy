package com.elozino.splitfy.android

import android.os.Bundle
import android.content.Intent
import com.elozino.splitfy.invites.InviteLinks
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.elozino.splitfy.App
import com.elozino.splitfy.api.dto.PushData
import com.elozino.splitfy.push.PushNotifications

/**
 * The only Activity: everything above it is shared Compose UI. A FragmentActivity
 * (still a ComponentActivity) because the app lock's BiometricPrompt attaches to one.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            intent?.dataString?.let(InviteLinks::open)
            PushNotifications.openGroup(intent?.getStringExtra(PushData.GROUP_ID))
        }
        setContent { App() }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let(InviteLinks::open)
        PushNotifications.openGroup(intent.getStringExtra(PushData.GROUP_ID))
    }
}

@Preview
@Composable
private fun AppAndroidPreview() {
    App()
}
