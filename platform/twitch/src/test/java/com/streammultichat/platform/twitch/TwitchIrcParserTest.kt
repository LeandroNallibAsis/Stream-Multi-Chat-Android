package com.streammultichat.platform.twitch

import com.streammultichat.core.model.PlatformType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TwitchIrcParserTest {

    @Test
    fun `parse standard PRIVMSG line with tags`() {
        val rawLine = "@badge-info=;badges=broadcaster/1,subscriber/3012;color=#00FF7F;display-name=IbaiLlanos;emotes=;id=1234-abcd;mod=0;room-id=12345;subscriber=1;tmi-sent-ts=1700000000000;turbo=0;user-id=999;user-type= :ibaillanos!ibaillanos@ibaillanos.tmi.twitch.tv PRIVMSG #ibai :Bienvenidos a la velada!"

        val message = TwitchIrcParser.parseMessage(rawLine, "ibai")

        assertNotNull(message)
        assertEquals("1234-abcd", message!!.id)
        assertEquals(PlatformType.TWITCH, message.platform)
        assertEquals("ibai", message.channelName)
        assertEquals("IbaiLlanos", message.authorName)
        assertEquals("Bienvenidos a la velada!", message.messageText)
        assertEquals(1700000000000L, message.timestamp)
        assertEquals(2, message.badges.size)
        assertEquals("broadcaster", message.badges[0].id)
        assertNotNull(message.badges[0].imageUrl)
        assertEquals("subscriber", message.badges[1].id)
        assertNotNull(message.badges[1].imageUrl)
    }

    @Test
    fun `parse PRIVMSG line with multiple emotes`() {
        val rawLine = "@badges=moderator/1;color=#FF4500;display-name=TwitchMod;emotes=25:0-4,12-16/1902:6-10;id=5678;tmi-sent-ts=1700000001000 :twitchmod!mod@tmi.twitch.tv PRIVMSG #testchannel :Kappa Keepo Kappa"

        val message = TwitchIrcParser.parseMessage(rawLine, "testchannel")

        assertNotNull(message)
        assertEquals("Kappa Keepo Kappa", message!!.messageText)
        assertEquals(2, message.emotes.size)

        val kappa = message.emotes.firstOrNull { it.id == "25" }
        assertNotNull(kappa)
        assertEquals("Kappa", kappa!!.code)
        assertEquals("https://static-cdn.jtvnw.net/emoticons/v2/25/default/dark/2.0", kappa.imageUrl)

        val keepo = message.emotes.firstOrNull { it.id == "1902" }
        assertNotNull(keepo)
        assertEquals("Keepo", keepo!!.code)
        assertEquals("https://static-cdn.jtvnw.net/emoticons/v2/1902/default/dark/2.0", keepo.imageUrl)

        assertEquals(1, message.badges.size)
        assertEquals("moderator", message.badges[0].id)
        assertEquals("https://static-cdn.jtvnw.net/badges/v1/3267646d-33f0-4b17-b3df-f923a41db1d0/2", message.badges[0].imageUrl)
    }

    @Test
    fun `ignore non PRIVMSG lines`() {
        val pingLine = "PING :tmi.twitch.tv"
        assertNull(TwitchIrcParser.parseMessage(pingLine, "ibai"))

        val joinLine = ":user!user@user.tmi.twitch.tv JOIN #channel"
        assertNull(TwitchIrcParser.parseMessage(joinLine, "ibai"))
    }
}
