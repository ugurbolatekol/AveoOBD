package com.aveoobd.app

import android.app.*
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
import kotlinx.coroutines.*

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var panelBg: GradientDrawable
    private var rootView: LinearLayout? = null
    private var isMini = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var obd: ObdManager? = null

    private lateinit var tvEco: TextView
    private lateinit var ecoBar: ProgressBar
    private lateinit var tvRpm: TextView
    private lateinit var tvInstant: TextView
    private lateinit var tvAvg: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvVoltTrim: TextView
    private lateinit var tvTank: TextView
    private lateinit var tvFuelBadge: TextView
    private lateinit var contentBox: LinearLayout

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotif()
        buildOverlay()
        startPolling()
    }

    private fun startForegroundNotif() {
        val chId = "aveo_obd_ch"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(chId, "Aveo OBD", NotificationManager.IMPORTANCE_LOW))
        val notif = Notification.Builder(this, chId)
            .setContentTitle("Aveo OBD Aktif")
            .setContentText("Sağ panel çalışıyor")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(1, notif)
            }
        } catch (_: Exception) {
            startForeground(1, notif)
        }
    }

    private fun buildOverlay() {
        panelBg = GradientDrawable().apply {
            setColor(Color.parseColor("#E0121822"))
            cornerRadius = 24f
            setStroke(4, Color.parseColor("#00E676"))
        }

        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBg
            setPadding(18, 12, 18, 14)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tvEco = TextView(this).apply {
            text = "EKO HAZIR"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E676"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnMini = Button(this).apply {
            text = "-"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
            setPadding(14, 0, 14, 0)
            minimumWidth = 0; minWidth = 0; minimumHeight = 0; minHeight = 0
            setOnClickListener {
                isMini = !isMini
                contentBox.visibility = if (isMini) View.GONE else View.VISIBLE
                ecoBar.visibility = if (isMini) View.GONE else View.VISIBLE
                text = if (isMini) "+" else "-"
            }
        }

        val btnClose = Button(this).apply {
            text = "X"
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CCFF1744"))
            setPadding(14, 0, 14, 0)
            minimumWidth = 0; minWidth = 0; minimumHeight = 0; minHeight = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 8 }
            setOnClickListener { stopSelf() }
        }

        topBar.addView(tvEco)
        topBar.addView(btnMini)
        topBar.addView(btnClose)
        rootView!!.addView(topBar)

        ecoBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 25
            progressTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 18
            ).apply { topMargin = 8; bottomMargin = 6 }
        }
        rootView!!.addView(ecoBar)

        contentBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        tvRpm = makeText("0 RPM  |  0 km/s", 18f, "#FFFFFF")
        tvInstant = makeText("ANLIK: 0.0 L/sa", 16f, "#00E5FF")
        tvAvg = makeText("ORT: 0.0 L | 0.00 TL/km", 13f, "#FFB300")
        tvTemp = makeText("SU: --°C | ŞNZ: --°C", 13f, "#E0E0E0")
        tvVoltTrim = makeText("AKÜ: --V | S: 0 L: 0", 12f, "#B0BEC5")
        tvTank = makeText("LPG: %.1f L".format(prefs.remainingLpg), 13f, "#80CBC4")

        tvFuelBadge = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            setPadding(10, 6, 10, 6)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6 }
        }
        updateFuelBadge(prefs.isLpgActive)

        listOf(tvRpm, tvInstant, tvAvg, tvTemp, tvVoltTrim, tvTank, tvFuelBadge).forEach {
            contentBox.addView(it)
        }
        rootView!!.addView(contentBox)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 12
        }
        wm.addView(rootView, params)
    }

    private fun updateFuelBadge(isLpg: Boolean) {
        val modeTag = if (prefs.autoLpgMode) "OTO (${prefs.lpgSwitchTemp}°C)" else "MANUEL"
        tvFuelBadge.text = if (isLpg) "$modeTag: LPG" else "$modeTag: BENZİN"
        tvFuelBadge.setBackgroundColor(if (isLpg) Color.parseColor("#00E676") else Color.parseColor("#FFC400"))
    }

    private fun makeText(t: String, size: Float, color: String) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(Color.parseColor(color))
        setPadding(0, 5, 0, 5)
    }

    private fun startPolling() {
        val btMgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        obd = ObdManager(btMgr.adapter)
        scope.launch {
            var lastTime = System.currentTimeMillis()
            var loopCount = 0
            var transTemp: Int? = null
            var volt = 0.0
            while (isActive) {
                if (prefs.btAddress.isBlank()) {
                    updateStatus("Ayarlardan OBD seçin")
                    delay(3000)
                    continue
                }
                if (!obd!!.connect(prefs.btAddress)) {
                    updateStatus("OBD bekleniyor...")
                    delay(4000)
                    continue
                }
                while (isActive) {
                    val rpmB = obd!!.readIntPid("010C", "410C")
                    if (rpmB == null) break
                    val rpm = ((rpmB[0] * 256) + rpmB.getOrElse(1) { 0 }) / 4
                    val spd = obd!!.readIntPid("010D", "410D")?.get(0) ?: 0
                    val mafB = obd!!.readIntPid("0110", "4110")
                    val maf = if (mafB != null && mafB.size >= 2) ((mafB[0] * 256) + mafB[1]) / 100.0 else 0.0
                    val load = ((obd!!.readIntPid("0104", "4104")?.get(0) ?: 0) * 100) / 255
                    val coolant = (obd!!.readIntPid("0105", "4105")?.get(0) ?: 40) - 40
                    val stft = ((obd!!.readIntPid("0106", "4106")?.get(0) ?: 128) - 128) * 100.0 / 128.0
                    val ltft = ((obd!!.readIntPid("0107", "4107")?.get(0) ?: 128) - 128) * 100.0 / 128.0

                    if (loopCount % 10 == 0) {
                        volt = obd!!.readVoltage()
                        transTemp = obd!!.readTransTempGM()
                    }
                    loopCount++

                    if (prefs.autoLpgMode) {
                        prefs.isLpgActive = coolant >= prefs.lpgSwitchTemp
                    }
                    val isLpg = prefs.isLpgActive
                    val mult = if (isLpg) prefs.lpgMultiplier.toDouble() else 1.0
                    val lh = (maf * 3600.0 / (14.7 * 745.0)) * mult
                    val l100 = if (spd >= 3) (lh / spd) * 100.0 else 0.0

                    val now = System.currentTimeMillis()
                    val dtHours = (now - lastTime) / 3600000.0
                    lastTime = now
                    var fuelDelta = 0f
                    var kmDelta = 0f
                    if (dtHours > 0 && dtHours < 0.05) {
                        fuelDelta = (lh * dtHours).toFloat()
                        kmDelta = (spd * dtHours).toFloat()
                        prefs.tripKm += kmDelta
                        if (isLpg) {
                            prefs.tripLpgUsed += fuelDelta
                            prefs.remainingLpg -= fuelDelta
                        } else {
                            prefs.tripPetrolUsed += fuelDelta
                        }
                    }

                    // İstatistikleri kaydet
                    prefs.recordDriveStats(kmDelta, fuelDelta, isLpg, coolant, transTemp, l100, spd)

                    val totalFuel = prefs.tripLpgUsed + prefs.tripPetrolUsed
                    val avg = if (prefs.tripKm > 0.2f) (totalFuel / prefs.tripKm) * 100.0 else l100
                    val cost = (prefs.tripLpgUsed * prefs.lpgPrice) + (prefs.tripPetrolUsed * prefs.petrolPrice)
                    val tlKm = if (prefs.tripKm > 0.2f) (cost / prefs.tripKm).toDouble() else 0.0
                    val range = if (avg > 1.0) ((prefs.remainingLpg / avg) * 100).toInt() else 0

                    val data = ObdData(true, "BAĞLI", rpm, spd, maf, load, coolant, transTemp, volt, stft, ltft, l100, lh, avg, tlKm, isLpg, prefs.remainingLpg, range)
                    mainHandler.post { render(data) }
                    delay(150)
                }
            }
        }
    }

    private fun updateStatus(msg: String) {
        mainHandler.post { tvEco.text = msg }
    }

    private fun render(d: ObdData) {
        val (ecoText, ecoColor, barVal) = when {
            d.speed > 15 && d.load < 12 -> Triple("CUT-OFF (0 YAKIT)", "#00E5FF", 10)
            d.rpm > 3200 || d.load > 75 -> Triple("GAZI AZALT!", "#FF1744", 95)
            d.rpm > 2500 || d.load > 50 -> Triple("ORTA TÜKETİM", "#FFC400", 65)
            else -> Triple("EKONOMİK SÜRÜŞ", "#00E676", 35)
        }
        val parsedColor = Color.parseColor(ecoColor)
        tvEco.text = ecoText
        tvEco.setTextColor(parsedColor)
        ecoBar.progress = barVal
        ecoBar.progressTintList = ColorStateList.valueOf(parsedColor)
        panelBg.setStroke(4, parsedColor)

        tvRpm.text = "${d.rpm} RPM  |  ${d.speed} km/s"
        tvInstant.text = if (d.speed >= 3) "ANLIK: %.1f L/100km".format(d.instantL100) else "RÖLANTİ: %.2f L/sa".format(d.instantLh)
        tvAvg.text = "ORT: %.1f L | %.2f TL/km".format(d.avgL100, d.tlPerKm)
        val tStr = d.transTemp?.let { "${it}°C" } ?: "--"
        tvTemp.text = "SU: ${d.coolantTemp}°C | ŞNZ: $tStr"
        tvTemp.setTextColor(if (d.coolantTemp >= 98 || (d.transTemp ?: 0) >= 105) Color.RED else Color.parseColor("#E0E0E0"))
        tvVoltTrim.text = "AKÜ: %.1fV | S:%+.0f L:%+.0f".format(d.batteryVolt, d.stft, d.ltft)
        tvTank.text = "LPG: %.1f L (~%d km)".format(d.remainingLpg, d.estRangeKm)
        tvTank.setTextColor(when {
            d.remainingLpg < 2f -> Color.RED
            d.remainingLpg < 5f -> Color.parseColor("#FF9100")
            else -> Color.parseColor("#80CBC4")
        })
        updateFuelBadge(d.isLpg)
    }

    override fun onDestroy() {
        scope.cancel()
        obd?.disconnect()
        rootView?.let { wm.removeView(it) }
        super.onDestroy()
    }
}
