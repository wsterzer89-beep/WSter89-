package de.dokumentenpilot.app
internal class AssistentAuswahl(private val allowed: List<String>) {
    private val selected = mutableSetOf<String>()
    fun set(id: String, checked: Boolean) {
        if (id !in allowed) return
        if (checked) selected.add(id) else selected.remove(id)
    }
    fun ids(): List<String> = allowed.distinct().filter { it in selected }
}
