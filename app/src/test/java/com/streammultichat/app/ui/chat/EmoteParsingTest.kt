package com.streammultichat.app.ui.chat

import com.streammultichat.core.model.Emote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmoteParsingTest {

    @Test
    fun `test empty emotes returns plain text`() {
        val (annotated, map) = buildEmoteAnnotatedString("Hello world", emptyList())
        assertEquals("Hello world", annotated.text)
        assertTrue(map.isEmpty())
    }

    @Test
    fun `test single emote replacement`() {
        val emotes = listOf(
            Emote(id = "25", code = "Kappa", imageUrl = "https://example.com/kappa.png")
        )
        val (annotated, map) = buildEmoteAnnotatedString("Hello Kappa world", emotes)
        assertEquals(1, map.size)
        assertTrue(map.containsKey("emote_25"))
        val annotations = annotated.getStringAnnotations(0, annotated.length)
        assertEquals(1, annotations.size)
        assertEquals("emote_25", annotations[0].item)
    }

    @Test
    fun `test multiple distinct emotes and repetitions`() {
        val emotes = listOf(
            Emote(id = "25", code = "Kappa", imageUrl = "https://example.com/kappa.png"),
            Emote(id = "1902", code = "Keepo", imageUrl = "https://example.com/keepo.png")
        )
        val (annotated, map) = buildEmoteAnnotatedString("Kappa and Keepo and Kappa again", emotes)
        assertEquals(2, map.size)
        assertTrue(map.containsKey("emote_25"))
        assertTrue(map.containsKey("emote_1902"))

        val annotations = annotated.getStringAnnotations(0, annotated.length)
        assertEquals(3, annotations.size)
        assertEquals("emote_25", annotations[0].item)
        assertEquals("emote_1902", annotations[1].item)
        assertEquals("emote_25", annotations[2].item)
    }

    @Test
    fun `test longer emote matched before shorter prefix`() {
        val emotes = listOf(
            Emote(id = "1", code = "Kappa", imageUrl = "https://example.com/kappa.png"),
            Emote(id = "2", code = "KappaPride", imageUrl = "https://example.com/kappapride.png")
        )
        val (annotated, map) = buildEmoteAnnotatedString("Check out KappaPride today", emotes)
        assertEquals(1, map.size)
        assertTrue(map.containsKey("emote_2"))
        val annotations = annotated.getStringAnnotations(0, annotated.length)
        assertEquals(1, annotations.size)
        assertEquals("emote_2", annotations[0].item)
    }

    @Test
    fun `test word boundary prevents matching inside other words`() {
        val emotes = listOf(
            Emote(id = "1", code = "cat", imageUrl = "https://example.com/cat.png")
        )
        val (annotated, map) = buildEmoteAnnotatedString("The caterpillar is not a cat", emotes)
        assertEquals(1, map.size)
        assertTrue(annotated.text.contains("caterpillar"))
        val annotations = annotated.getStringAnnotations(0, annotated.length)
        assertEquals(1, annotations.size)
        assertEquals("emote_1", annotations[0].item)
    }

    @Test
    fun `test punctuation around emotes`() {
        val emotes = listOf(
            Emote(id = "25", code = "Kappa", imageUrl = "https://example.com/kappa.png")
        )
        val (annotated, map) = buildEmoteAnnotatedString("Hey, Kappa! Is that (Kappa)?", emotes)
        assertEquals(1, map.size)
        val annotations = annotated.getStringAnnotations(0, annotated.length)
        assertEquals(2, annotations.size)
        assertEquals("emote_25", annotations[0].item)
        assertEquals("emote_25", annotations[1].item)
    }
}
