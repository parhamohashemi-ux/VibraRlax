package com.example.vibrarelax

/** One step of a pattern: duration in ms and relative strength 0f (off) .. 1f (full). */
data class Seg(val ms: Long, val level: Float)

object Patterns {
    val all: LinkedHashMap<String, List<Seg>> = linkedMapOf(
        "Continuous" to listOf(Seg(1000, 1f)),
        "Pulse" to listOf(Seg(350, 1f), Seg(350, 0f)),
        "Wave" to (1..8).map { Seg(110, it / 8f) } + (7 downTo 1).map { Seg(110, it / 8f) } + Seg(200, 0f),
        "Heartbeat" to listOf(Seg(90, 1f), Seg(110, 0f), Seg(90, 0.8f), Seg(700, 0f)),
        "Tap" to listOf(Seg(60, 1f), Seg(140, 0f)),
        "Rhythm" to listOf(
            Seg(200, 1f), Seg(100, 0f), Seg(200, 0.7f), Seg(100, 0f),
            Seg(500, 1f), Seg(300, 0f), Seg(120, 0.5f), Seg(120, 0f),
            Seg(120, 0.5f), Seg(400, 0f)
        )
    )

    /** Turns a pattern into (timings, amplitudes) scaled by intensity (1..100). */
    fun build(name: String, intensity: Int): Pair<LongArray, IntArray> {
        val segs = all[name] ?: all.getValue("Continuous")
        val s = intensity.coerceIn(1, 100) / 100f
        val timings = LongArray(segs.size) { segs[it].ms }
        val amps = IntArray(segs.size) {
            val l = segs[it].level
            if (l <= 0f) 0 else (l * s * 255f).toInt().coerceIn(1, 255)
        }
        return timings to amps
    }
}
