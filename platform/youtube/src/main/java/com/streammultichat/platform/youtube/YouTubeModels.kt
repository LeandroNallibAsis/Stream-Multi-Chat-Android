package com.streammultichat.platform.youtube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class YouTubeInnerTubeRequest(
    val context: YouTubeInnerTubeContext = YouTubeInnerTubeContext(),
    val continuation: String
)

@Serializable
data class YouTubeInnerTubeContext(
    val client: YouTubeClientDescriptor = YouTubeClientDescriptor()
)

@Serializable
data class YouTubeClientDescriptor(
    val clientName: String = "WEB",
    val clientVersion: String = "2.20240901.00.00"
)

@Serializable
data class YouTubeGetLiveChatResponse(
    val continuationContents: YouTubeContinuationContents? = null
)

@Serializable
data class YouTubeContinuationContents(
    val liveChatContinuation: YouTubeLiveChatContinuation? = null
)

@Serializable
data class YouTubeLiveChatContinuation(
    val continuations: List<YouTubeContinuationItem>? = null,
    val actions: List<YouTubeActionItem>? = null
)

@Serializable
data class YouTubeContinuationItem(
    val invalidationContinuationData: YouTubeContinuationData? = null,
    val timedContinuationData: YouTubeContinuationData? = null
)

@Serializable
data class YouTubeContinuationData(
    val continuation: String? = null,
    val timeoutMs: Long? = null
)

@Serializable
data class YouTubeActionItem(
    val addChatItemAction: YouTubeAddChatItemAction? = null
)

@Serializable
data class YouTubeAddChatItemAction(
    val item: YouTubeChatItemRenderer? = null
)

@Serializable
data class YouTubeChatItemRenderer(
    val liveChatTextMessageRenderer: YouTubeTextMessageRenderer? = null,
    val liveChatPaidMessageRenderer: YouTubePaidMessageRenderer? = null
)

@Serializable
data class YouTubeTextMessageRenderer(
    val id: String? = null,
    val authorName: YouTubeSimpleText? = null,
    val message: YouTubeRunsText? = null,
    val timestampUsec: String? = null,
    val authorBadges: List<YouTubeBadgeItem>? = null
)

@Serializable
data class YouTubePaidMessageRenderer(
    val id: String? = null,
    val authorName: YouTubeSimpleText? = null,
    val message: YouTubeRunsText? = null,
    val purchaseAmountText: YouTubeSimpleText? = null,
    val timestampUsec: String? = null
)

@Serializable
data class YouTubeBadgeItem(
    val liveChatAuthorBadgeRenderer: YouTubeAuthorBadgeRenderer? = null
)

@Serializable
data class YouTubeAuthorBadgeRenderer(
    val tooltip: String? = null,
    val customThumbnail: YouTubeThumbnailsContainer? = null,
    val icon: YouTubeIcon? = null
)

@Serializable
data class YouTubeIcon(
    val iconType: String? = null
)

@Serializable
data class YouTubeSimpleText(
    val simpleText: String? = null
)

@Serializable
data class YouTubeRunsText(
    val runs: List<YouTubeRun>? = null
)

@Serializable
data class YouTubeRun(
    val text: String? = null,
    val emoji: YouTubeEmoji? = null
)

@Serializable
data class YouTubeEmoji(
    val emojiId: String? = null,
    val shortcuts: List<String>? = null,
    val image: YouTubeThumbnailsContainer? = null,
    val isCustomEmoji: Boolean? = null
)

@Serializable
data class YouTubeThumbnailsContainer(
    val thumbnails: List<YouTubeThumbnailItem>? = null
)

@Serializable
data class YouTubeThumbnailItem(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)
