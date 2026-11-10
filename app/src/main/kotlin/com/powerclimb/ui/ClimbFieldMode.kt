package com.powerclimb.ui

/**
 * The three native-style presentations exposed as separate Karoo data types.
 *
 * Karoo does not let an extension add the native "Visual / Zone Color" toggles
 * to the data-field picker, so each presentation is registered as its own
 * data type instead.
 */
enum class ClimbFieldMode(val typeId: String) {
    NUMERIC("climb-avg-power"),
    VISUAL("climb-avg-power-visual"),
    ZONE("climb-avg-power-zone"),
}
