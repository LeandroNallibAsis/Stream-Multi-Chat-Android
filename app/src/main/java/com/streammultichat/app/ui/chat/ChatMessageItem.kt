package com.streammultichat.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streammultichat.core.model.ChatMessage
import com.streammultichat.core.model.PlatformType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 14,
    showTimestamps: Boolean = true,
    showPlatformBadges: Boolean = true,
    backgroundOpacity: Float = 0.35f
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormatter.format(Date(message.timestamp)) }

    val platformColor = Color(message.platform.brandColorHex)
    val authorColor = if (message.authorColorHex != null && message.authorColorHex != 0L) {
        Color(message.authorColorHex!!)
    } else {
        platformColor
    }

    val safeOpacity = backgroundOpacity.coerceIn(0.05f, 0.95f)
    val backgroundColor = if (message.isHighlight) {
        platformColor.copy(alpha = (safeOpacity * 0.7f).coerceIn(0.12f, 0.5f))
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = safeOpacity)
    }

    val authorFontSize = (fontSizeSp - 1).coerceAtLeast(11).sp
    val messageFontSize = fontSizeSp.sp
    val messageLineHeight = (fontSizeSp * 1.35f).sp
    val timestampFontSize = (fontSizeSp - 3).coerceAtLeast(10).sp
    val badgeFontSize = (fontSizeSp - 5).coerceAtLeast(8).sp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            // Header: Platform Tag + Badges + Author Name + Timestamp
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Platform tag (only if showPlatformBadges is true)
                if (showPlatformBadges) {
                    PlatformTag(platform = message.platform, fontSizeSp = badgeFontSize.value.toInt())
                }

                // User badges (broadcaster, moderator, subscriber, etc.)
                message.badges.forEach { badge ->
                    UserBadgeItem(
                        badge = badge,
                        sizeDp = (fontSizeSp + 2).dp,
                        fontSizeSp = badgeFontSize.value.toInt(),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }

                // Author name
                Text(
                    text = message.authorName,
                    color = authorColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = authorFontSize,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )

                // Timestamp (only if showTimestamps is true)
                if (showTimestamps) {
                    Text(
                        text = formattedTime,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = timestampFontSize,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }

            // Message text with inline graphical emotes (Coil)
            ChatMessageTextWithEmotes(
                messageText = message.messageText,
                emotes = message.emotes,
                fontSizeSp = fontSizeSp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
fun PlatformTag(
    platform: PlatformType,
    fontSizeSp: Int = 9
) {
    val color = Color(platform.brandColorHex)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.25f))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = platform.displayName.uppercase(),
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = fontSizeSp.sp,
            letterSpacing = 0.5.sp
        )
    }
}
