package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ArchivKryptografieTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val plain = "Synthetisches Testdokument".toByteArray()

    private fun legacy(): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key)
        return byteArrayOf(c.iv.size.toByte()) + c.iv + c.doFinal(plain)
    }

    @Test fun readsLegacyArchive() {
        assertArrayEquals(plain, ArchivKryptografie.entschluesseln(legacy(), key))
    }

    @Test fun legacyReaderReadsNewArchive() {
        val data = ArchivKryptografie.verschluesseln(plain, key)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(1, 13)))
        assertArrayEquals(plain, c.doFinal(data.copyOfRange(13, data.size)))
    }

    @Test fun randomIvAndEmptyPayload() {
        val a = ArchivKryptografie.verschluesseln(byteArrayOf(), key)
        val b = ArchivKryptografie.verschluesseln(byteArrayOf(), key)
        assertFalse(a.copyOfRange(1, 13).contentEquals(b.copyOfRange(1, 13)))
        assertArrayEquals(byteArrayOf(), ArchivKryptografie.entschluesseln(a, key))
    }

    @Test fun rejectsTampering() {
        val data = legacy()
        for (i in listOf(1, 13, data.lastIndex)) {
            val changed = data.copyOf()
            changed[i] = (changed[i].toInt() xor 1).toByte()
            assertThrows(javax.crypto.AEADBadTagException::class.java) {
                ArchivKryptografie.entschluesseln(changed, key)
            }
        }
    }

    @Test fun rejectsWrongKey() {
        assertThrows(javax.crypto.AEADBadTagException::class.java) {
            ArchivKryptografie.entschluesseln(legacy(), SecretKeySpec(ByteArray(32), "AES"))
        }
    }

    @Test fun rejectsMalformedHeaders() {
        for (data in listOf(byteArrayOf(), ByteArray(28), ByteArray(29))) {
            assertThrows(IllegalArgumentException::class.java) {
                ArchivKryptografie.entschluesseln(data, key)
            }
        }
    }
}
