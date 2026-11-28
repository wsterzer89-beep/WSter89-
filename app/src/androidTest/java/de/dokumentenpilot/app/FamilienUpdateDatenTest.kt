package de.dokumentenpilot.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(AndroidJUnit4::class)
class FamilienUpdateDatenTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val ownerStore = FamilienGeraeteVertrauen(context)
    private val regelStore = FamilienArchivFreigaben(context)
    private val fixture = context.getSharedPreferences("familien_upgrade_fixture", Context.MODE_PRIVATE)
    private val id = "f0f0190e-ca30-4a4d-a418-acc12ee3698d.dp"
    private val titel = "Synthetischer Update-Nachweis.pdf"
    private val kategorie = "Synthetische Update-Kategorie"
    private val schluesselAlias = "DokumentenPilotSchluessel"

    @Test fun verschluesseltesArchivUndFreigabenUeberstehenUpdate() {
        val phase = InstrumentationRegistry.getArguments().getString("familyUpgrade")
        assumeTrue(phase == "seed" || phase == "verify")
        if (phase == "seed") vorbereiten() else pruefen()
    }

    private fun vorbereiten() {
        // The fresh emulator uses the same alias and format as the installed baseline.
        val owner = FamilienGeraeteIdentitaet.laden().fingerprint
        val peer = FamilienGeraeteIdentitaet.nurTransporttest().fingerprint
        val schluessel = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(schluesselAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
        val pdf = checkNotNull(FamilienTestPdf.erstellen("demo-1"))
        try {
            File(context.filesDir, "dokumente").apply { mkdirs() }.resolve(id)
                .writeBytes(ArchivKryptografie.verschluesseln(pdf, schluessel))
        } finally { pdf.fill(0) }
        File(context.filesDir, "dokumentnamen.txt").appendText("$id|$titel\n")
        assertTrue(context.getSharedPreferences("dokument_kategorien", Context.MODE_PRIVATE).edit()
            .putString(id, kategorie).commit())
        assertTrue(ownerStore.bestaetigen(peer))
        assertTrue(ownerStore.geraeteNameSpeichern(peer, "Synthetisches Partnergerät"))
        val doc = FamilienBerechtigungen.Dokument(id, owner, kategorie)
        val regel = FamilienBerechtigungen.Freigabe(peer, FamilienBerechtigungen.Bereich.KATEGORIE, kategorie,
            setOf(FamilienBerechtigungen.Aktion.ANSEHEN), setOf(id), true, archivEigentuemerId = owner)
        assertTrue(regelStore.speichern(owner, peer, FamilienArchivFreigaben.Regeln(listOf(regel), setOf(id)), listOf(doc)))
        assertTrue(fixture.edit().putString("owner", owner).putString("peer", peer).commit())
    }

    private fun pruefen() {
        val owner = checkNotNull(fixture.getString("owner", null))
        val peer = checkNotNull(fixture.getString("peer", null))
        assertEquals(owner, FamilienGeraeteIdentitaet.laden().fingerprint)
        assertEquals(FamilienGeraeteVertrauen.Status.BESTAETIGT, ownerStore.status(peer))
        assertEquals("Synthetisches Partnergerät", ownerStore.geraeteName(peer))
        assertFalse(FamilienHintergrundRechte(context).erlaubt(peer))
        val eintrag = FamilienArchivDaten.vomDatentraeger(context, owner).single { it.dokument.id == id }
        assertEquals(titel, eintrag.titel)
        assertEquals(kategorie, eintrag.dokument.kategorie)
        val regel = checkNotNull(regelStore.lesen(owner, peer))
        assertTrue(regel.freigaben.single().neueDokumenteAutomatisch)
        assertEquals(setOf(id), regel.privat)
        assertTrue(FamilienArchivDaten.neuesAngebot(peer, listOf(eintrag), emptyList(), regel, false).isEmpty())
        val schluessel = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(schluesselAlias, null)
        assertTrue(schluessel is SecretKey)
        val klartext = FamilienArchivDaten.entschluesseln(context, id)
        try { assertEquals("%PDF-", klartext.take(5).toByteArray().toString(Charsets.US_ASCII)) }
        finally { klartext.fill(0) }
    }
}
