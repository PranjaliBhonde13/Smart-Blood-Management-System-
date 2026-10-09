package com.sben.bloodrequest;

/**
 * Blood component requested. This records what was asked for; the final
 * component and any crossmatching are decided by qualified blood-bank staff.
 */
public enum BloodComponent {
    WHOLE_BLOOD,
    PACKED_RED_CELLS,
    PLATELETS,
    FRESH_FROZEN_PLASMA,
    CRYOPRECIPITATE,
    NOT_SURE
}
