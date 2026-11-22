package com.aveoobd.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("aveo_obd_prefs", Context.MODE_PRIVATE)

    var btAddress: String
        get() = sp.getString("bt_address", "") ?: ""
        set(v) = sp.edit().putString("bt_address", v).apply()

    var autoStart: Boolean
        get() = sp.getBoolean("auto_start", true)
        set(v) = sp.edit().putBoolean("auto_start", v).apply()

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

    // Min-Max İstatistikler
    var maxCoolant: Int
        get() = sp.getInt("stat_max_coolant", -999)
        set(v) = sp.edit().putInt("stat_max_coolant", v).apply()

    var minCoolant: Int
        get() = sp.getInt("stat_min_coolant", 999)
        set(v) = sp.edit().putInt("stat_min_coolant", v).apply()

    var maxTrans: Int
        get() = sp.getInt("stat_max_trans", -999)
        set(v) = sp.edit().putInt("stat_max_trans", v).apply()

    var maxFuelL100: Float
        get() = sp.getFloat("stat_max_fuel", 0f)
        set(v) = sp.edit().putFloat("stat_max_fuel", v).apply()

    var minFuelL100: Float
        get() = sp.getFloat("stat_min_fuel", 999f)
        set(v) = sp.edit().putFloat("stat_min_fuel", v).apply()

    fun resetTrip() {
        tripKm = 0f
        tripLpgUsed = 0f
        tripPetrolUsed = 0f
    }

    fun recordDriveStats(kmDelta: Float, fuelDelta: Float, isLpg: Boolean, coolant: Int, trans: Int?, l100: Double, spd: Int) {
        val e = sp.edit()
        if (coolant > -30 && coolant < 150) {
            if (coolant > maxCoolant) e.putInt("stat_max_coolant", coolant)
            if (coolant < minCoolant) e.putInt("stat_min_coolant", coolant)
        }
        if (trans != null && trans > -30 && trans < 180) {
            if (trans > maxTrans) e.putInt("stat_max_trans", trans)
        }
        if (spd >= 20 && l100 > 1.0 && l100 < 50.0) {
            val f = l100.toFloat()
            if (f > maxFuelL100) e.putFloat("stat_max_fuel", f)
            if (f < minFuelL100) e.putFloat("stat_min_fuel", f)
        }

        if (kmDelta > 0f || fuelDelta > 0f) {
            val now = Date()
            val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
            val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(now)
            val costDelta = fuelDelta * (if (isLpg) lpgPrice else petrolPrice)

            val days = HashSet(sp.getStringSet("stat_days", emptySet()) ?: emptySet())
            days.add(dayKey)
            e.putStringSet("stat_days", days)
            e.putFloat("d_${dayKey}_km", sp.getFloat("d_${dayKey}_km", 0f) + kmDelta)
            e.putFloat("d_${dayKey}_fuel", sp.getFloat("d_${dayKey}_fuel", 0f) + fuelDelta)
            e.putFloat("d_${dayKey}_cost", sp.getFloat("d_${dayKey}_cost", 0f) + costDelta)

            val months = HashSet(sp.getStringSet("stat_months", emptySet()) ?: emptySet())
            months.add(monthKey)
            e.putStringSet("stat_months", months)
            e.putFloat("m_${monthKey}_km", sp.getFloat("m_${monthKey}_km", 0f) + kmDelta)
            e.putFloat("m_${monthKey}_fuel", sp.getFloat("m_${monthKey}_fuel", 0f) + fuelDelta)
            e.putFloat("m_${monthKey}_cost", sp.getFloat("m_${monthKey}_cost", 0f) + costDelta)
        }
        e.apply()
    }

    fun getDailySummary(): String {
        val days = (sp.getStringSet("stat_days", emptySet()) ?: emptySet()).sortedDescending().take(7)
        if (days.isEmpty()) return "Henüz günlük sürüş kaydı yok."
        return days.joinToString("\n") { d ->
            val km = sp.getFloat("d_${d}_km", 0f)
            val fuel = sp.getFloat("d_${d}_fuel", 0f)
            val cost = sp.getFloat("d_${d}_cost", 0f)
            val avg = if (km > 0.2f) (fuel / km) * 100f else 0f
            "$d  |  %.1f km  |  Ort: %.1f L  |  %.0f TL".format(km, avg, cost)
        }
    }

    fun getMonthlySummary(): String {
        val months = (sp.getStringSet("stat_months", emptySet()) ?: emptySet()).sortedDescending().take(12)
        if (months.isEmpty()) return "Henüz aylık sürüş kaydı yok."
        return months.joinToString("\n") { m ->
            val km = sp.getFloat("m_${m}_km", 0f)
            val fuel = sp.getFloat("m_${m}_fuel", 0f)
            val cost = sp.getFloat("m_${m}_cost", 0f)
            val avg = if (km > 0.2f) (fuel / km) * 100f else 0f
            "$m  |  %.1f km  |  Ort: %.1f L  |  %.0f TL".format(km, avg, cost)
        }
    }

    fun resetAllStats() {
        val e = sp.edit()
        e.remove("stat_max_coolant").remove("stat_min_coolant").remove("stat_max_trans")
        e.remove("stat_max_fuel").remove("stat_min_fuel")
        (sp.getStringSet("stat_days", emptySet()) ?: emptySet()).forEach {
            e.remove("d_${it}_km").remove("d_${it}_fuel").remove("d_${it}_cost")
        }
        (sp.getStringSet("stat_months", emptySet()) ?: emptySet()).forEach {
            e.remove("m_${it}_km").remove("m_${it}_fuel").remove("m_${it}_cost")
        }
        e.remove("stat_days").remove("stat_months").apply()
    }
}
