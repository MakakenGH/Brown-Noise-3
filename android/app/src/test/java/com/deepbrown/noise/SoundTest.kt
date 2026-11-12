package com.deepbrown.noise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundTest {

    @Test
    fun sleepGainCurve() {
        assertEquals(1.0, Sound.sleepGain(60_000L, 30_000L), 1e-9)
        assertEquals(1.0, Sound.sleepGain(30_000L, 30_000L), 1e-9)
        assertEquals(0.5, Sound.sleepGain(15_000L, 30_000L), 1e-9)
        assertEquals(0.0, Sound.sleepGain(0L, 30_000L), 1e-9)
        assertEquals(1.0, Sound.sleepGain(10_000L, 0L), 1e-9)

        var previous = 1.0
        for (remaining in listOf(30_000L, 25_000L, 20_000L, 15_000L, 10_000L, 5_000L, 0L)) {
            val value = Sound.sleepGain(remaining, 30_000L)
            assertTrue("sleep fade must decrease", value <= previous)
            previous = value
        }
    }

    @Test
    fun presetCopiesEveryField() {
        for (preset in Presets) {
            val draft = preset.toDraft("Test")
            assertEquals("Test", draft.name)
            assertEquals(preset.color, draft.color)
            assertEquals(preset.deep, draft.deep, 0f)
            assertEquals(preset.rumble, draft.rumble, 0f)
            assertEquals(preset.width, draft.width, 0f)
            assertEquals(preset.swell, draft.swell, 0f)
            assertEquals(preset.texture, draft.texture, 0f)
            assertEquals(preset.air, draft.air, 0f)
            assertEquals(preset.drift, draft.drift, 0f)
        }
    }

    @Test
    fun randomDraftStaysInSafeRanges() {
        repeat(100) {
            val draft = randomDraft()
            assertTrue("deep ${draft.deep}", draft.deep in 0.2f..0.85f)
            assertTrue("rumble ${draft.rumble}", draft.rumble in 0.05f..0.55f)
            assertTrue("width ${draft.width}", draft.width in 0.25f..0.95f)
            assertTrue("swell ${draft.swell}", draft.swell in 0f..0.4f)
            assertTrue("texture ${draft.texture}", draft.texture in 0f..0.6f)
            assertTrue("air ${draft.air}", draft.air in 0.25f..0.75f)
            assertTrue("drift ${draft.drift}", draft.drift in 0f..0.35f)
        }
    }

    @Test
    fun oldSavedSoundUsesNeutralDefaults() {
        val sound = SavedSound(
            id = "id",
            name = "Old",
            color = NoiseColor.BROWN,
            deep = 0.5f,
            rumble = 0.35f,
            width = 0.6f,
            swell = 0.1f,
        )
        assertEquals(0f, sound.texture, 0f)
        assertEquals(0f, sound.air, 0f)
        assertEquals(0f, sound.drift, 0f)

        val params = sound.params(0.5f)
        assertEquals(0f, params.texture, 0f)
        assertEquals(0f, params.air, 0f)
        assertEquals(0f, params.drift, 0f)
    }
}
