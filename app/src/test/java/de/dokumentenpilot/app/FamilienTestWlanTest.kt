package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class FamilienTestWlanTest {
    private val ip = "192.168.1.20"
    @Test fun unveraendertesWlan() {
        assertTrue(FamilienTestWlan.unveraendert(true, true, false, ip, ip))
    }
    @Test fun anderesNetzMitGleicherIp() {
        assertFalse(FamilienTestWlan.unveraendert(false, true, false, ip, ip))
    }
    @Test fun mobilfunk() {
        assertFalse(FamilienTestWlan.unveraendert(true, false, false, ip, ip))
    }
    @Test fun vpn() {
        assertFalse(FamilienTestWlan.unveraendert(true, true, true, ip, ip))
    }
    @Test fun ipWechsel() {
        assertFalse(FamilienTestWlan.unveraendert(true, true, false, ip, "192.168.1.21"))
    }
    @Test fun adressverlust() {
        assertFalse(FamilienTestWlan.unveraendert(true, true, false, ip, null))
    }
}
