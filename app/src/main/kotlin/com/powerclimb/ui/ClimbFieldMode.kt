package com.powerclimb.ui

/**
 * The native-style presentations exposed as separate Karoo data types.
 *
 * Karoo does not let an extension add the native "Zone Color" toggle to the
 * data-field picker, so the zone presentation is registered as its own data
 * type instead.
 */
enum class ClimbFieldMode(val typeId: String) {
    NUMERIC("climb-avg-power"),
    ZONE("climb-avg-power-zone"),
}
