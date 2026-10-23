package com.streammultichat.platform.twitch

import com.streammultichat.core.model.ChatMessage
import com.streammultichat.core.model.Emote
import com.streammultichat.core.model.NotificationType
import com.streammultichat.core.model.PlatformType
import com.streammultichat.core.model.StreamNotification
import com.streammultichat.core.model.UserBadge
import java.util.UUID

object TwitchIrcParser {

    /**
     * Parses an IRCv3 message with Twitch tags.
     * Example:
     * @badges=broadcaster/1;color=#00FF7F;display-name=ChannelOwner;emotes=;id=36ba...;tmi-sent-ts=1710000000 :channelowner!channelowner@channelowner.tmi.twitch.tv PRIVMSG #mychannel :Hola a todos!
     */
    fun parseMessage(rawLine: String, fallbackChannel: String): ChatMessage? {
        if (!rawLine.contains("PRIVMSG")) return null

        val tags = mutableMapOf<String, String>()
        var content = rawLine

        // Extract tags if present (prefixed with @)
        if (content.startsWith("@")) {
            val spaceIndex = content.indexOf(' ')
            if (spaceIndex != -1) {
                val tagsString = content.substring(1, spaceIndex)
                tagsString.split(";").forEach { tagPart ->
                    val eqIndex = tagPart.indexOf('=')
                    if (eqIndex != -1) {
                        val key = tagPart.substring(0, eqIndex)
                        val value = tagPart.substring(eqIndex + 1)
                        tags[key] = value
                    }
                }
                content = content.substring(spaceIndex + 1).trimStart()
            }
        }

        // Find PRIVMSG #channel :message_text
        val privmsgIndex = content.indexOf("PRIVMSG ")
        if (privmsgIndex == -1) return null

        val afterPrivmsg = content.substring(privmsgIndex + "PRIVMSG ".length)
        val colonIndex = afterPrivmsg.indexOf(" :")
        if (colonIndex == -1) return null

        val channel = afterPrivmsg.substring(0, colonIndex).removePrefix("#").trim()
        val text = afterPrivmsg.substring(colonIndex + 2)

        val id = tags["id"] ?: "twitch-${UUID.randomUUID()}"
        val displayName = tags["display-name"]?.takeIf { it.isNotBlank() } ?: run {
            // Extract username from :username!username@...
            val exclIndex = content.indexOf('!')
            if (exclIndex > 1 && content.startsWith(":")) {
                content.substring(1, exclIndex)
            } else {
                "TwitchUser"
            }
        }

        val colorHex = tags["color"]?.let { hexString ->
            try {
                if (hexString.startsWith("#")) {
                    java.lang.Long.parseLong(hexString.removePrefix("#"), 16) or 0xFF000000
                } else null
            } catch (_: Exception) {
                null
            }
        }

        // Badges: broadcaster/1,moderator/1,subscriber/12
        val badges = tags["badges"]?.split(",")?.mapNotNull { badgeStr ->
            val name = badgeStr.substringBefore("/").trim()
            if (name.isNotBlank()) {
                UserBadge(
                    id = name,
                    name = name.replaceFirstChar { it.uppercase() },
                    imageUrl = getTwitchBadgeUrl(name)
                )
            } else null
        } ?: emptyList()

        // Emotes: 25:0-4,12-16/1902:6-10
        val emotes = parseTwitchEmotes(tags["emotes"], text)

        val timestamp = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis()
        val hasBits = (tags["bits"]?.toIntOrNull() ?: 0) > 0

        return ChatMessage(
            id = id,
            platform = PlatformType.TWITCH,
            channelName = channel.ifBlank { fallbackChannel },
            authorName = displayName,
            authorColorHex = colorHex ?: PlatformType.TWITCH.brandColorHex,
            messageText = text,
            badges = badges,
            emotes = emotes,
            timestamp = timestamp,
            isHighlight = hasBits,
            rawPayload = rawLine
        )
    }

    /**
     * Parses Twitch IRC USERNOTICE messages (Subscriptions, Resubs, Raids).
     * Example:
     * @badges=subscriber/1;msg-id=sub;system-msg=User\ssubscribed\sat\sTier\s1.;tmi-sent-ts=... :tmi.twitch.tv USERNOTICE #channel :Let's go!
     */
    fun parseUserNotice(rawLine: String, fallbackChannel: String): StreamNotification? {
        if (!rawLine.contains("USERNOTICE")) return null

        val tags = mutableMapOf<String, String>()
        var content = rawLine

        if (content.startsWith("@")) {
            val spaceIndex = content.indexOf(' ')
            if (spaceIndex != -1) {
                val tagsString = content.substring(1, spaceIndex)
                tagsString.split(";").forEach { tagPart ->
                    val eqIndex = tagPart.indexOf('=')
                    if (eqIndex != -1) {
                        val key = tagPart.substring(0, eqIndex)
                        val value = tagPart.substring(eqIndex + 1)
                        tags[key] = value
                    }
                }
                content = content.substring(spaceIndex + 1).trimStart()
            }
        }

        val msgId = tags["msg-id"] ?: "notice"
        val rawSystemMsg = tags["system-msg"]?.replace("\\s", " ")?.replace("\\:", ":") ?: ""
        val displayName = tags["display-name"]?.takeIf { it.isNotBlank() } ?: tags["login"] ?: "Usuario"
        val timestamp = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis()
        val id = tags["id"] ?: "twitch-notice-${UUID.randomUUID()}"

        val notifType = when (msgId) {
            "sub", "resub", "subgift", "anonsubgift", "submysterygift", "primepaidupgrade" -> NotificationType.SUBSCRIPTION
            "raid" -> NotificationType.RAID
            else -> NotificationType.SUBSCRIPTION
        }

        val trailingMsg = if (content.contains(" :")) content.substringAfter(" :").trim() else ""
        val description = if (trailingMsg.isNotBlank()) {
            "$rawSystemMsg: $trailingMsg"
        } else if (rawSystemMsg.isNotBlank()) {
            rawSystemMsg
        } else {
            when (notifType) {
                NotificationType.SUBSCRIPTION -> "$displayName se ha suscrito al canal"
                NotificationType.RAID -> {
                    val count = tags["msg-param-viewerCount"] ?: ""
                    if (count.isNotBlank()) "$displayName inició una raid con $count espectadores"
                    else "$displayName inició una raid"
                }
                else -> "$displayName realizó una acción en Twitch"
            }
        }

        return StreamNotification(
            id = id,
            platform = PlatformType.TWITCH,
            type = notifType,
            user = displayName,
            title = when (notifType) {
                NotificationType.SUBSCRIPTION -> "Suscripción"
                NotificationType.RAID -> "Raid"
                else -> "Alerta"
            },
            description = description,
            amount = tags["msg-param-cumulative-months"]?.toDoubleOrNull() ?: tags["msg-param-viewerCount"]?.toDoubleOrNull(),
            currency = if (notifType == NotificationType.SUBSCRIPTION) "meses" else if (notifType == NotificationType.RAID) "espectadores" else null,
            timestamp = timestamp
        )
    }

    /**
     * Parses Twitch IRC 'emotes' tag format:
     * <emote_id>:<start>-<end>,<start>-<end>/<emote_id>:<start>-<end>
     * start and end are 0-based character offsets in the message text.
     */
    fun parseTwitchEmotes(emotesTag: String?, text: String): List<Emote> {
        if (emotesTag.isNullOrBlank()) return emptyList()
        val result = mutableMapOf<String, Emote>()

        // Split multiple emotes separated by '/'
        val emoteParts = emotesTag.split('/')
        for (part in emoteParts) {
            val colonIdx = part.indexOf(':')
            if (colonIdx == -1) continue
            val emoteId = part.substring(0, colonIdx).trim()
            if (emoteId.isEmpty()) continue

            // Parse positions: e.g. 0-4,12-16
            val ranges = part.substring(colonIdx + 1).split(',')
            var emoteCode: String? = null
            for (range in ranges) {
                val dashIdx = range.indexOf('-')
                if (dashIdx == -1) continue
                val start = range.substring(0, dashIdx).toIntOrNull() ?: continue
                val end = range.substring(dashIdx + 1).toIntOrNull() ?: continue
                if (start in 0..end && end < text.length) {
                    emoteCode = text.substring(start, end + 1)
                    break
                }
            }

            if (emoteCode != null && !result.containsKey(emoteId)) {
                // Twitch v2 dark theme emote CDN URL (crisp 56x56 px 2.0 version)
                val cdnUrl = "https://static-cdn.jtvnw.net/emoticons/v2/$emoteId/default/dark/2.0"
                result[emoteId] = Emote(
                    id = emoteId,
                    code = emoteCode,
                    imageUrl = cdnUrl
                )
            }
        }
        return result.values.toList()
    }

    /**
     * Standard Twitch badge CDN URLs for official platform badges.
     */
    fun getTwitchBadgeUrl(badgeName: String): String? {
        return when (badgeName.lowercase()) {
            "broadcaster" -> "https://static-cdn.jtvnw.net/badges/v1/5527c58c-fb7d-422d-b71b-f5d3d3d6dd07/2"
            "moderator" -> "https://static-cdn.jtvnw.net/badges/v1/3267646d-33f0-4b17-b3df-f923a41db1d0/2"
            "vip" -> "https://static-cdn.jtvnw.net/badges/v1/b817aba4-fad8-49e2-ab54-412b8630ad61/2"
            "subscriber" -> "https://static-cdn.jtvnw.net/badges/v1/5d9f2208-5dd8-449e-82cc-49d950d3a2ed/2"
            "turbo" -> "https://static-cdn.jtvnw.net/badges/v1/bd444ec6-8f34-4bf9-abac-f6c13f8752e6/2"
            "premium", "prime" -> "https://static-cdn.jtvnw.net/badges/v1/a1dd5073-19c3-4911-834c-87b3769f3dd2/2"
            "partner", "verified" -> "https://static-cdn.jtvnw.net/badges/v1/d12a2e27-16f6-41d0-ab77-b780518f00a3/2"
            "staff", "admin" -> "https://static-cdn.jtvnw.net/badges/v1/d97c37bd-a6f5-4c38-8f57-4e4bef88af34/2"
            "artist-badge" -> "https://static-cdn.jtvnw.net/badges/v1/4300a897-03dc-4e8a-ac7e-6407883ea5d0/2"
            else -> null
        }
    }
}
