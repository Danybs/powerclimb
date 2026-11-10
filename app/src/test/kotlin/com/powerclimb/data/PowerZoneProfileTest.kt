package com.powerclimb.data

import io.hammerhead.karooext.models.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerZoneProfileTest {

    private val fiveZones = PowerZoneProfile(
        listOf(
            PowerZone(0, 0, 149),
            PowerZone(1, 150, 199),
            PowerZone(2, 200, 229),
            PowerZone(3, 230, 259),
            PowerZone(4, 260, Int.MAX_VALUE),
        ),
    )

    @Test
    fun `zone lookup uses device boundaries inclusively`() {
        assertEquals(0, fiveZones.zoneFor(0)?.index)
        assertEquals(0, fiveZones.zoneFor(149)?.index)
        assertEquals(1, fiveZones.zoneFor(150)?.index)
        assertEquals(2, fiveZones.zoneFor(200)?.index)
        assertEquals(4, fiveZones.zoneFor(900)?.index)
    }

    @Test
    fun `zone lookup clamps below the first zone`() {
        val zones = PowerZoneProfile(listOf(PowerZone(0, 100, 199)))
        assertEquals(0, zones.zoneFor(50)?.index)
    }

    @Test
    fun `no average means no zone`() {
        assertNull(fiveZones.zoneFor(null))
    }

    @Test
    fun `no device zones means no zone color`() {
        assertNull(PowerZoneProfile.EMPTY.zoneFor(250))
    }

    @Test
    fun `device zones are copied without changing boundaries`() {
        val units = UserProfile.PreferredUnit(
            distance = UserProfile.PreferredUnit.UnitType.METRIC,
            elevation = UserProfile.PreferredUnit.UnitType.METRIC,
            temperature = UserProfile.PreferredUnit.UnitType.METRIC,
            weight = UserProfile.PreferredUnit.UnitType.METRIC,
        )
        val userProfile = UserProfile(
            weight = 70f,
            preferredUnit = units,
            maxHr = 190,
            restingHr = 50,
            heartRateZones = emptyList(),
            ftp = 250,
            powerZones = listOf(
                UserProfile.Zone(0, 149),
                UserProfile.Zone(150, 199),
                UserProfile.Zone(200, Int.MAX_VALUE),
            ),
        )

        val profile = PowerZoneProfile.from(userProfile)

        assertEquals(
            listOf(
                PowerZone(0, 0, 149),
                PowerZone(1, 150, 199),
                PowerZone(2, 200, Int.MAX_VALUE),
            ),
            profile.zones,
        )
    }

    @Test
    fun `null profile has no zones`() {
        val profile = PowerZoneProfile.from(null)
        assertEquals(0, profile.zones.size)
    }
}
