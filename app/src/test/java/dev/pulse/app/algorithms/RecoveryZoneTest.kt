package dev.pulse.app.algorithms

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryZoneTest {
    @Test
    fun `границы зон восстановления`() {
        assertEquals(RecoveryZone.RED, recoveryZone(0))
        assertEquals(RecoveryZone.RED, recoveryZone(33))
        assertEquals(RecoveryZone.YELLOW, recoveryZone(34))
        assertEquals(RecoveryZone.YELLOW, recoveryZone(66))
        assertEquals(RecoveryZone.GREEN, recoveryZone(67))
        assertEquals(RecoveryZone.GREEN, recoveryZone(100))
    }

    @Test
    fun `цель по нагрузке зависит от зоны`() {
        assertEquals(14f..18f, strainTarget(RecoveryZone.GREEN))
        assertEquals(10f..14f, strainTarget(RecoveryZone.YELLOW))
        assertEquals(0f..10f, strainTarget(RecoveryZone.RED))
    }
}
