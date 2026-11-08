package com.deepbrown.noise

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists the sound collection and the global volume. */
class SoundStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadSounds(): List<SavedSound> {
        val raw = prefs.getString(KEY_LIBRARY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                    add(
                        SavedSound(
                            id = id,
                            name = o.optString("name", "Untitled"),
                            color = NoiseColor.fromKey(o.optString("color")),
                            deep = o.optDouble("deep", 0.5).toFloat().coerceIn(0f, 1f),
                            rumble = o.optDouble("rumble", 0.35).toFloat().coerceIn(0f, 1f),
                            width = o.optDouble("width", 0.6).toFloat().coerceIn(0f, 1f),
                            swell = o.optDouble("swell", 0.1).toFloat().coerceIn(0f, 1f),
                        ),
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun saveSounds(sounds: List<SavedSound>) {
        val array = JSONArray()
        for (s in sounds) {
            array.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("color", s.color.name)
                    .put("deep", s.deep.toDouble())
                    .put("rumble", s.rumble.toDouble())
                    .put("width", s.width.toDouble())
                    .put("swell", s.swell.toDouble()),
            )
        }
        prefs.edit().putString(KEY_LIBRARY, array.toString()).apply()
    }

    var volume: Float
        get() = prefs.getFloat(KEY_VOLUME, 0.55f).coerceIn(0f, 1f)
        set(value) {
            prefs.edit().putFloat(KEY_VOLUME, value.coerceIn(0f, 1f)).apply()
        }

    private companion object {
        const val PREFS = "deepbrown"
        const val KEY_LIBRARY = "library_v1"
        const val KEY_VOLUME = "volume"
    }
}
