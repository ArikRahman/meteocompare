package com.meteocompare.app.data.repository

/**
 * Anti-rafale en mémoire après une indisponibilité temporaire d'Open-Meteo.
 *
 * Le cache météo Room n'est jamais touché : pendant ce délai, les streams
 * peuvent toujours émettre leurs prévisions précédentes. Aucune tâche ou
 * boucle de retry n'est créée ; un prochain appel redevient possible à
 * l'expiration du délai. Les clés sont limitées pour éviter une rétention
 * mémoire quand l'utilisateur consulte beaucoup de localités.
 */
internal class TransientForecastBackoff(
    private val initialDelayMs: Long = 30_000L,
    private val maxDelayMs: Long = 15 * 60_000L,
    private val maxEntries: Int = 64
) {
    private data class Entry(val status: Int, val failures: Int, val retryAtMs: Long)
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

    /** HTTP à exposer tant que l'appel suivant doit être évité, sinon null. */
    @Synchronized
    fun blockedStatus(key: String, nowMs: Long): Int? =
        entries[key]?.takeIf { nowMs < it.retryAtMs }?.status

    @Synchronized
    fun failed(key: String, status: Int, nowMs: Long) {
        val failures = ((entries[key]?.failures ?: 0) + 1).coerceAtMost(20)
        var delayMs = initialDelayMs
        repeat((failures - 1).coerceAtMost(20)) {
            delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
        }
        entries[key] = Entry(status, failures, nowMs + delayMs)
        while (entries.size > maxEntries) {
            entries.remove(entries.keys.first())
        }
    }

    @Synchronized
    fun succeeded(key: String) {
        entries.remove(key)
    }
}
