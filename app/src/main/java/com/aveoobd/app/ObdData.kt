package com.aveoobd.app

data class ObdData(
    val connected: Boolean = false,
    val statusText: String = "BaÄŸlanÄ±yor...",
    val rpm: Int = 0,
    val speed: Int = 0,
    val maf: Double = 0.0,
    val load: Int = 0,
    val coolantTemp: Int = 0,
    val transTemp: Int? = null,
    val batteryVolt: Double = 0.0,
    val stft: Double = 0.0,
    val ltft: Double = 0.0,
    val instantL100: Double = 0.0,
    val instantLh: Double = 0.0,
    val avgL100: Double = 0.0,
    val tlPerKm: Double = 0.0,
    val isLpg: Boolean = false,
    val remainingLpg: Float = 33.5f,
    val estRangeKm: Int = 0
)