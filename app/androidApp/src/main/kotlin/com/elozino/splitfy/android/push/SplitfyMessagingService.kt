package com.elozino.splitfy.android.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.elozino.splitfy.R
import com.elozino.splitfy.android.MainActivity
import com.elozino.splitfy.api.dto.PushData
import com.elozino.splitfy.push.PushNotifications
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM entry point.
 *
 * In the background, FCM shows notification messages itself (on the channel named
 * in the manifest) and puts the data payload in the launch intent's extras, which
 * [MainActivity] reads. In the foreground it hands them here instead, so we post
 * the same notification ourselves.
 */
class SplitfyMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushNotifications.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        show(this, notification.title.orEmpty(), notification.body.orEmpty(), message.data)
    }

    companion object {
        const val CHANNEL_ID = "splitfy_default"

        /** Creates the channel and fetches the current token. A no-op without google-services.json. */
        fun initialise(context: Context) {
            createChannel(context)
            if (FirebaseApp.getApps(context).isEmpty()) return
            FirebaseMessaging.getInstance().token.addOnSuccessListener(PushNotifications::onNewToken)
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_ID, "Activity", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Expenses, payments and reminders from your groups"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun show(context: Context, title: String, body: String, data: Map<String, String>) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data[PushData.GROUP_ID]?.let { putExtra(PushData.GROUP_ID, it) }
            }
            val tap = PendingIntent.getActivity(
                context,
                (data[PushData.GROUP_ID] ?: title).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_splitfy)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(tap)
                .build()
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}
