package tv.own.owntv.core.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanionSourceManagerTest {
    @Test
    fun `valid source actions are parsed deterministically`() {
        assertEquals(
            CompanionSourceAction(42L, CompanionSourceActionKind.RESYNC),
            companionSourceAction(mapOf("sourceId" to "42", "action" to "resync")),
        )
        assertEquals(
            CompanionSourceAction(7L, CompanionSourceActionKind.SET_DEFAULT),
            companionSourceAction(mapOf("sourceId" to "7", "action" to "DEFAULT")),
        )
        assertEquals(
            CompanionSourceAction(9L, CompanionSourceActionKind.DELETE),
            companionSourceAction(mapOf("sourceId" to "9", "action" to "delete")),
        )
    }

    @Test
    fun `invalid or ambiguous actions are rejected`() {
        assertNull(companionSourceAction(mapOf("sourceId" to "0", "action" to "delete")))
        assertNull(companionSourceAction(mapOf("sourceId" to "abc", "action" to "resync")))
        assertNull(companionSourceAction(mapOf("sourceId" to "1", "action" to "edit")))
        assertNull(companionSourceAction(mapOf("action" to "delete")))
    }
}
