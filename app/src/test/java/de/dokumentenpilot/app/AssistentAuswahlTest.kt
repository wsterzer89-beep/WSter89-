package de.dokumentenpilot.app
import org.junit.Assert.assertEquals
import org.junit.Test
class AssistentAuswahlTest {
    @Test fun emptyInitially() { assertEquals(emptyList<String>(), AssistentAuswahl(listOf("a","b")).ids()) }
    @Test fun single() { val s = AssistentAuswahl(listOf("a","b","c")); s.set("b",true); assertEquals(listOf("b"),s.ids()) }
    @Test fun multipleAndClear() { val s = AssistentAuswahl(listOf("a","b")); s.set("a",true); s.set("b",true); assertEquals(listOf("a","b"),s.ids()); s.set("a",false); s.set("b",false); assertEquals(emptyList<String>(),s.ids()) }
    @Test fun duplicateAndUnknownIds() { val s = AssistentAuswahl(listOf("a","a","b")); s.set("a",true); s.set("x",true); assertEquals(listOf("a"),s.ids()) }
}
