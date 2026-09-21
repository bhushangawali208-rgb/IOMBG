package com.example.notifications

/**
 * P1-1 FCM Models and Payload Validation
 */

data class DeviceNotificationToken(
    val tokenId: String = "",
    val token: String = "",
    val platform: String = "android",
    val appVersion: String = "1.0",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class FcmNotificationPayload(
    val type: String,
    val title: String,
    val body: String,
    val targetContentId: String? = null,
    val targetContentType: String? = null,
    val senderUid: String? = null,
    val senderName: String? = null,
    val senderAvatarUrl: String? = null
) {
    companion object {
        val SUPPORTED_TYPES = setOf(
            "LIKE",
            "COMMENT",
            "REPLY",
            "NEW_FOLLOWER",
            "NEW_MESSAGE",
            "LIVE_STARTED"
        )

        val VALID_CONTENT_TYPES = setOf(
            "VIDEO",
            "SHORT",
            "CHANNEL",
            "CHAT",
            "LIVE"
        )

        private val SAFE_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")

        /**
         * Safely parse and validate an incoming FCM remote message data map.
         * Returns null if any required field is missing or contains invalid/dangerous input.
         */
        fun parse(data: Map<String, String>): FcmNotificationPayload? {
            val rawType = data["type"]?.trim()?.uppercase() ?: return null
            if (rawType !in SUPPORTED_TYPES) {
                return null
            }

            val rawTitle = data["title"]?.trim() ?: ""
            val rawBody = data["body"]?.trim() ?: ""

            if (rawTitle.isBlank() && rawBody.isBlank()) {
                return null
            }

            val sanitizedTitle = rawTitle.take(100)
            val sanitizedBody = rawBody.take(500)

            val rawContentId = data["targetContentId"]?.trim()
            val targetContentId = if (!rawContentId.isNullOrBlank()) {
                if (!SAFE_ID_REGEX.matches(rawContentId)) return null
                rawContentId
            } else null

            val rawContentType = data["targetContentType"]?.trim()?.uppercase()
            val targetContentType = if (!rawContentType.isNullOrBlank()) {
                if (rawContentType !in VALID_CONTENT_TYPES) return null
                rawContentType
            } else null

            val rawSenderUid = data["senderUid"]?.trim()
            val senderUid = if (!rawSenderUid.isNullOrBlank()) {
                if (!SAFE_ID_REGEX.matches(rawSenderUid)) return null
                rawSenderUid
            } else null

            val senderName = data["senderName"]?.trim()?.take(64)
            val senderAvatarUrl = data["senderAvatarUrl"]?.trim()?.take(512)

            return FcmNotificationPayload(
                type = rawType,
                title = sanitizedTitle.ifBlank { "IOMBG" },
                body = sanitizedBody,
                targetContentId = targetContentId,
                targetContentType = targetContentType,
                senderUid = senderUid,
                senderName = senderName,
                senderAvatarUrl = senderAvatarUrl
            )
        }
    }
}
