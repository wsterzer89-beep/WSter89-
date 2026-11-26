package de.dokumentenpilot.app

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Owns pilot resources only. UI must close this scope on lock, stop or cancellation. */
internal class FamilienTestLebenszyklus : AutoCloseable {
    private val ressourcen = mutableListOf<AutoCloseable>()
    private var beendet = false
    private val timer = Executors.newSingleThreadScheduledExecutor()

    init {
        timer.schedule({ close() }, 10, TimeUnit.MINUTES)
    }

    fun <T : AutoCloseable> registrieren(resource: T): T {
        synchronized(this) {
            if (!beendet) {
                ressourcen.add(resource)
                return resource
            }
        }
        runCatching { resource.close() }
        throw IllegalStateException("Testsitzung beendet")
    }

    @Synchronized
    fun aktiv(): Boolean = !beendet

    override fun close() {
        val zuSchliessen = synchronized(this) {
            if (beendet) return
            beendet = true
            ressourcen.asReversed().toList().also { ressourcen.clear() }
        }
        timer.shutdownNow()
        zuSchliessen.forEach { runCatching { it.close() } }
    }
}
