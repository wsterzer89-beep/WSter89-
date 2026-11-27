package de.dokumentenpilot.app

import android.content.Context

/** Only synthetic local profiles. Never authorizes archive access or identifies a peer. */
internal class FamilienTestProfile(context: Context) {
    private val prefs = context.getSharedPreferences("familien_testprofile", Context.MODE_PRIVATE)

    fun vorhanden(): Boolean = prefs.getBoolean("demo_member", false)

    fun speichern(): Boolean = prefs.edit().putBoolean("demo_member", true).commit()

    fun entfernen(): Boolean = prefs.edit().remove("demo_member").commit()
}
