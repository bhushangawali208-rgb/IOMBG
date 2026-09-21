package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * P1-1 Android Notification Channels for IOMBG
 */
object NotificationChannels {

    const val CHANNEL_ID_MESSAGES = "channel_iombg_messages"
    const val CHANNEL_ID_SOCIAL = "channel_iombg_social"
    const val CHANNEL_ID_LIVE = "channel_iombg_live"

    /**
     * Idempotently create notification channels on Android 8.0+ (API 26+).
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // 1. Direct Messages
        val messagesChannel = NotificationChannel(
            CHANNEL_ID_MESSAGES,
            "IOMBG Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Direct chat messages and conversation updates"
            enableVibration(true)
            setShowBadge(true)
        }

        // 2. Social Engagements (Likes, comments, follows)
        val socialChannel = NotificationChannel(
            CHANNEL_ID_SOCIAL,
            "IOMBG Social",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Likes, comments, replies, and new followers"
            enableVibration(true)
            setShowBadge(true)
        }

        // 3. Live Broadcasts
        val liveChannel = NotificationChannel(
            CHANNEL_ID_LIVE,
            "IOMBG Live",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Live streams and creator broadcasts"
            enableVibration(true)
            setShowBadge(true)
        }

        notificationManager.createNotificationChannels(
            listOf(messagesChannel, socialChannel, liveChannel)
        )
    }

    /**
     * Map validated notification payload type to the appropriate channel ID.
     */
    fun getChannelIdForType(type: String): String {
        return when (type) {
            "NEW_MESSAGE" -> CHANNEL_ID_MESSAGES
            "LIVE_STARTED" -> CHANNEL_ID_LIVE
            else -> CHANNEL_ID_SOCIAL
        }
    }
}
