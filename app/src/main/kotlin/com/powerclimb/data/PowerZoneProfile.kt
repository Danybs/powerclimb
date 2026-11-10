package com.powerclimb.data

import io.hammerhead.karooext.models.UserProfile

/**
 * Power zones exactly as configured on the Karoo.
 *
 * The device owns the boundaries; this class only adapts them for the view.
 * No percentage-of-FTP fallback is used, because that would invent boundaries
 * that are not necessarily the ones configured by the rider.
 */
data class PowerZone(
    val index: Int,
    val min: Int,
    val max: Int,
)

data class PowerZoneProfile(
    val zones: List<PowerZone>,
) {
    fun zoneFor(watts: Int?): PowerZone? {
        if (watts == null || zones.isEmpty()) return null

        return zones.firstOrNull { watts >= it.min && watts <= it.max }
            ?: zones.lastOrNull { watts >= it.min }
            ?: zones.firstOrNull()
    }

    companion object {
        val EMPTY = PowerZoneProfile(emptyList())

        fun from(userProfile: UserProfile?): PowerZoneProfile {
            val zones = userProfile
                ?.powerZones
                .orEmpty()
                .filter { it.max >= it.min }
                .mapIndexed { index, zone ->
                    PowerZone(index = index, min = zone.min, max = zone.max)
                }
            return PowerZoneProfile(zones)
        }

        /**
         * Deterministic preview used in the Karoo field editor. It mirrors a
         * typical seven-zone setup so the visual and zone-color variants can be
         * inspected before a ride.
         */
        val PREVIEW = PowerZoneProfile(
            listOf(
                PowerZone(0, 0, 137),
                PowerZone(1, 138, 187),
                PowerZone(2, 188, 224),
                PowerZone(3, 225, 262),
                PowerZone(4, 263, 299),
                PowerZone(5, 300, 374),
                PowerZone(6, 375, Int.MAX_VALUE),
            ),
        )
    }
}
