package de.dokumentenpilot.app

import org.junit.Assert.*
import org.junit.Test

class FamilienEinladungTest {
    private val invite = FamilienEinladung.Einladung("synthetic-id", "owner", "recipient", 1000)
    @Test fun correctRecipientCanAccept() {
        assertEquals(FamilienEinladung.Status.ANGENOMMEN, FamilienEinladung.annehmen(invite, "recipient", 999)?.status)
    }
    @Test fun wrongRecipientAndExpiredDenied() {
        assertNull(FamilienEinladung.annehmen(invite, "other", 999))
        assertNull(FamilienEinladung.annehmen(invite, "recipient", 1000))
    }
    @Test fun acceptedInvitationCannotBeAcceptedAgain() {
        val accepted = FamilienEinladung.annehmen(invite, "recipient", 999)!!
        assertNull(FamilienEinladung.annehmen(accepted, "recipient", 999))
    }
    @Test fun onlyOwnerCanRevoke() {
        assertNull(FamilienEinladung.widerrufen(invite, "recipient"))
        val revoked = FamilienEinladung.widerrufen(invite, "owner")!!
        assertNull(FamilienEinladung.annehmen(revoked, "recipient", 999))
    }
}
