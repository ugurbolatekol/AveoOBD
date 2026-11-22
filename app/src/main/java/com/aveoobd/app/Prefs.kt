package com.aveoobd.app

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("aveo_obd_prefs", Context.MODE_PRIVATE)

    var btAddress: String
        get() = sp.getString("bt_address", "") ?: ""
        set(v) = sp.edit().putString("bt_address", v).apply()

    var autoLpgMode: Boolean
        get() = sp.getBoolean("auto_lpg_mode", true)
        set(v) = sp.edit().putBoolean("auto_lpg_mode", v).apply()

    var isLpgActive: Boolean
        get() = sp.getBoolean("is_lpg_active", false)
        set(v) = sp.edit().putBoolean("is_lpg_active", v).apply()

    var lpgSwitchTemp: Int
        get() = sp.getInt("lpg_switch_temp", 50)
        set(v) = sp.edit().putInt("lpg_switch_temp", v).apply()

    var lpgPrice: Float
        get() = sp.getFloat("lpg_price", 26.5f)
        set(v) = sp.edit().putFloat("lpg_price", v).apply()

    var petrolPrice: Float
        get() = sp.getFloat("petrol_price", 45.0f)
        set(v) = sp.edit().putFloat("petrol_price", v).apply()

    var lpgMultiplier: Float
        get() = sp.getFloat("lpg_multiplier", 1.20f)
        set(v) = sp.edit().putFloat("lpg_multiplier", v).apply()

    var tankCapacity: Float
        get() = sp.getFloat("tank_capacity", 33.5f)
        set(v) = sp.edit().putFloat("tank_capacity", v).apply()

    var remainingLpg: Float
        get() = sp.getFloat("remaining_lpg", 33.5f)
        set(v) = sp.edit().putFloat("remaining_lpg", v.coerceAtLeast(0f)).apply()

    var tripKm: Float
        get() = sp.getFloat("trip_km", 0f)
        set(v) = sp.edit().putFloat("trip_km", v).apply()

    var tripLpgUsed: Float
        get() = sp.getFloat("trip_lpg_used", 0f)
        set(v) = sp.edit().putFloat("trip_lpg_used", v).apply()

    var tripPetrolUsed: Float
        get() = sp.getFloat("trip_petrol_used", 0f)
        set(v) = sp.edit().putFloat("trip_petrol_used", v).apply()

    fun resetTrip() {
        tripKm = 0f
        tripLpgUsed = 0f
        tripPetrolUsed = 0f
    }
}