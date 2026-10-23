package com.streammultichat.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.streammultichat.core.model.Emote
import com.streammultichat.core.model.UserBadge

/**
 * Renders a user badge:
 * If an imageUrl is present (Twitch CDN, Kick CDN, YouTube custom thumbnail), renders a crisp
 * graphic image with Coil. If absent, renders a styled fallback chip.
 */
@Composable
fun UserBadgeItem(
    badge: UserBadge,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 16.dp,
    fontSizeSp: Int = 9
) {
    if (!badge.imageUrl.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(badge.imageUrl)
                .crossfade(true)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = badge.name,
            modifier = modifier
                .size(sizeDp)
                .clip(RoundedCornerShape(3.dp)),
            contentScale = ContentScale.Fit
        )
    } else {
        UserBadgeTag(badgeName = badge.name, fontSizeSp = fontSizeSp)
    }
}

/**
 * Text chip fallback for badges without an image URL.
 */
@Composable
fun UserBadgeTag(
    badgeName: String,
    fontSizeSp: Int = 9
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = badgeName,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSizeSp.sp
        )
    }
}

/**
 * Renders message text with inline graphical emotes using Coil and Compose InlineTextContent.
 * Cached via remember() to guarantee 60 FPS performance on devices with low memory like Samsung A04.
 */
@Composable
fun ChatMessageTextWithEmotes(
    messageText: String,
    emotes: List<Emote>,
    fontSizeSp: Int = 14,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    modifier: Modifier = Modifier
) {
    val messageFontSize = fontSizeSp.sp
    val messageLineHeight = (fontSizeSp * 1.35f).sp
    val emoteSizeSp = (fontSizeSp * 1.35f).sp

    if (emotes.isEmpty()) {
        Text(
            text = messageText,
            style = style.copy(
                fontSize = messageFontSize,
                lineHeight = messageLineHeight
            ),
            color = color,
            modifier = modifier
        )
    } else {
        val (annotatedText, matchedEmotes) = remember(messageText, emotes) {
            buildEmoteAnnotatedString(messageText, emotes)
        }

        val inlineContentMap = remember(matchedEmotes, emoteSizeSp) {
            matchedEmotes.mapValues { (_, emote) ->
                InlineTextContent(
                    Placeholder(
                        width = emoteSizeSp,
                        height = emoteSizeSp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(emote.imageUrl)
                            .crossfade(true)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = emote.code,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        Text(
            text = annotatedText,
            inlineContent = inlineContentMap,
            style = style.copy(
                fontSize = messageFontSize,
                lineHeight = messageLineHeight
            ),
            color = color,
            modifier = modifier
        )
    }
}

/**
 * Builds an AnnotatedString replacing emote tokens with inline content markers,
 * respecting word boundaries and ordering by code length to match longest tokens first.
 */
fun buildEmoteAnnotatedString(
    text: String,
    emotes: List<Emote>
): Pair<AnnotatedString, Map<String, Emote>> {
    if (emotes.isEmpty() || text.isEmpty()) {
        return AnnotatedString(text) to emptyMap()
    }

    val emoteMap = mutableMapOf<String, Emote>()
    emotes.forEach { emote ->
        if (!emoteMap.containsKey(emote.code)) {
            emoteMap[emote.code] = emote
        }
    }

    val sortedCodes = emoteMap.keys.sortedByDescending { it.length }
    val regexPattern = sortedCodes.map { Regex.escape(it) }.joinToString("|").toRegex()

    val matchedEmotes = mutableMapOf<String, Emote>()
    val builder = AnnotatedString.Builder()
    var lastIndex = 0

    for (match in regexPattern.findAll(text)) {
        val range = match.range
        val code = match.value

        // Check word boundaries: avoid matching inside other words (e.g. "cat" inside "caterpillar")
        val isPrefixInsideWord = range.first > 0 &&
                text[range.first - 1].isLetterOrDigit() &&
                code.first().isLetterOrDigit()
        val isSuffixInsideWord = range.last < text.length - 1 &&
                text[range.last + 1].isLetterOrDigit() &&
                code.last().isLetterOrDigit()

        if (isPrefixInsideWord || isSuffixInsideWord) {
            continue
        }

        val emote = emoteMap[code] ?: continue
        val inlineId = "emote_${emote.id}"
        matchedEmotes[inlineId] = emote

        if (range.first > lastIndex) {
            builder.append(text.substring(lastIndex, range.first))
        }

        builder.appendInlineContent(inlineId, code)
        lastIndex = range.last + 1
    }

    if (lastIndex < text.length) {
        builder.append(text.substring(lastIndex))
    }

    return builder.toAnnotatedString() to matchedEmotes
}
