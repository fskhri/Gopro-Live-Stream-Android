package com.gopro.rebuild.tiktok

import kotlinx.serialization.Serializable

@Serializable
data class TiktokStreamInfo(
    val fullRtmpUrl: String,
    val baseUrl: String,
    val streamKey: String,
    val shareUrl: String,
    val roomId: String
)

data class TiktokTopic(
    val id: String?,
    val label: String
)

data class GameTag(
    val id: String,
    val name: String
)

enum class RelayStatus {
    IDLE,
    STARTING,
    STREAMING,
    STOPPING,
    ERROR
}
