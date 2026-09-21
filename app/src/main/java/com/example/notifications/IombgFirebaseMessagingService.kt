package com.example.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * P1-1 Firebase Cloud Messaging Service for IOMBG
 *
 * Safely handles incoming push notifications and token rotations.
 * Only validates and presents supported IOMBG notifications.
 */
class IombgFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "IombgMessagingService"

        const val EXTRA_DESTINATION_TYPE = "extra_destination_type"
        const val EXTRA_DESTINATION_ID = "extra_destination_id"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM onNewToken received")
        FcmTokenManager.getInstance(applicationContext).onNewToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Ensure channels are configured
        NotificationChannels.createNotificationChannels(applicationContext)

        val rawData = remoteMessage.data
        if (rawData.isEmpty()) {
            Log.d(TAG, "Received FCM message with empty data payload, skipping")
            return
        }

        val payload = FcmNotificationPayload.parse(rawData)
        if (payload == null) {
            Log.w(TAG, "Ignoring malformed, untrusted, or unsupported FCM payload")
            return
        }

        showSystemNotification(payload)
    }

    /**
     * Show notification with safe intent routing and channel alignment.
     */
    private fun showSystemNotification(payload: FcmNotificationPayload) {
        // Android 13+ permission check before posting notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "POST_NOTIFICATIONS permission not granted, skipping display")
                return
            }
        }

        val channelId = NotificationChannels.getChannelIdForType(payload.type)

        // Build explicit intent to MainActivity with validated extras
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DESTINATION_TYPE, payload.targetContentType ?: payload.type)
            payload.targetContentId?.let { putExtra(EXTRA_DESTINATION_ID, it) }
        }

        val requestCode = (payload.targetContentId?.hashCode() ?: System.currentTimeMillis().toInt()) and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(
                if (payload.type == "NEW_MESSAGE" || payload.type == "LIVE_STARTED")
                    NotificationCompat.PRIORITY_HIGH
                else
                    NotificationCompat.PRIORITY_DEFAULT
            )

        val notificationManager = NotificationManagerCompat.from(this)
        try {
            notificationManager.notify(requestCode, notificationBuilder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException posting notification: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error posting notification: ${e.message}", e)
        }
    }
}
