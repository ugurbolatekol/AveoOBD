package com.aveoobd.app

import android.app.*
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
import kotlinx.coroutines.*

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private var rootView: LinearLayout? = null
    private var isMini = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var obd: ObdManager? = null

    private lateinit var tvEco: TextView
    private lateinit var tvRpm: TextView
    private lateinit var tvInstant: TextView
    private lateinit var tvAvg: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvVoltTrim: TextView
    private lateinit var tvTank: TextView
    private lateinit var btnFuel: Button
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
            .setContentText("SaÄŸ panel Ã§alÄ±ÅŸÄ±yor")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1, notif)
        }
    }

    private fun buildOverlay() {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#D9121822"))
            cornerRadius = 24f
            setStroke(3, Color.parseColor("#00E5FF"))
        }

        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(18, 14, 18, 14)
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
            text = "â€”"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
            setPadding(12, 2, 12, 2)
            setOnClickListener {
                isMini = !isMini
                contentBox.visibility = if (isMini) View.GONE else View.VISIBLE
                text = if (isMini) "+" else "â€”"
            }
        }
        topBar.addView(tvEco)
        topBar.addView(btnMini)
        rootView!!.addView(topBar)

        contentBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        tvRpm = makeText("0 RPM", 20f, "#FFFFFF")
        tvInstant = makeText("ANLIK: 0.0 L/sa", 16f, "#00E5FF")
        tvAvg = makeText("ORT: 0.0 L | 0.00 TL/km", 13f, "#FFB300")
        tvTemp = makeText("SU: --Â°C | ÅNZ: --Â°C", 13f, "#E0E0E0")
        tvVoltTrim = makeText("AKÃœ: --V | T: 0/0%", 12f, "#B0BEC5")
        tvTank = makeText("LPG: 33.5 L (~350 km)", 13f, "#80CBC4")

        btnFuel = Button(this).apply {
            text = if (prefs.isLpgActive) "YAKIT: LPG" else "YAKIT: BENZÄ°N"
            textSize = 12f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00E5FF"))
            setOnClickListener {
                prefs.autoLpgMode = false
                prefs.isLpgActive = !prefs.isLpgActive
                text = if (prefs.isLpgActive) "MANUEL: LPG" else "MANUEL: BENZÄ°N"
            }
        }

        listOf(tvRpm, tvInstant, tvAvg, tvTemp, tvVoltTrim, tvTank, btnFuel).forEach {
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

    private fun makeText(t: String, size: Float, color: String) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(Color.parseColor(color))
        setPadding(0, 6, 0, 6)
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
                    updateStatus("Ayarlardan OBD seÃ§in")
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
                    if (dtHours > 0 && dtHours < 0.05) {
                        val fuelDelta = (lh * dtHours).toFloat()
                        val kmDelta = (spd * dtHours).toFloat()
                        prefs.tripKm += kmDelta
                        if (isLpg) {
                            prefs.tripLpgUsed += fuelDelta
                            prefs.remainingLpg -= fuelDelta
                        } else {
                            prefs.tripPetrolUsed += fuelDelta
                        }
                    }

                    val totalFuel = prefs.tripLpgUsed + prefs.tripPetrolUsed
                    val avg = if (prefs.tripKm > 0.2f) (totalFuel / prefs.tripKm) * 100.0 else l100
                    val cost = (prefs.tripLpgUsed * prefs.lpgPrice) + (prefs.tripPetrolUsed * prefs.petrolPrice)
                    val tlKm = if (prefs.tripKm > 0.2f) (cost / prefs.tripKm).toDouble() else 0.0
                    val range = if (avg > 1.0) ((prefs.remainingLpg / avg) * 100).toInt() else 0

                    val data = ObdData(true, "BAÄLI", rpm, spd, maf, load, coolant, transTemp, volt, stft, ltft, l100, lh, avg, tlKm, isLpg, prefs.remainingLpg, range)
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
        val (ecoText, ecoColor) = when {
            d.speed > 15 && d.load < 12 -> "CUT-OFF (0 YAKIT)" to "#00E5FF"
            d.rpm > 3200 || d.load > 75 -> "GAZI AZALT!" to "#FF1744"
            d.rpm > 2500 || d.load > 50 -> "ORTA TÃœKETÄ°M" to "#FFC400"
            else -> "EKONOMÄ°K SÃœRÃœÅ" to "#00E676"
        }
        tvEco.text = ecoText
        tvEco.setTextColor(Color.parseColor(ecoColor))
        tvRpm.text = "${d.rpm} RPM  |  ${d.speed} km/s"
        tvInstant.text = if (d.speed >= 3) "ANLIK: %.1f L/100km".format(d.instantL100) else "RÃ–LANTÄ°: %.2f L/sa".format(d.instantLh)
        tvAvg.text = "ORT: %.1f L | %.2f TL/km".format(d.avgL100, d.tlPerKm)
        val tStr = d.transTemp?.let { "${it}Â°C" } ?: "--"
        tvTemp.text = "SU: ${d.coolantTemp}Â°C | ÅNZ: $tStr"
        tvTemp.setTextColor(if (d.coolantTemp >= 98 || (d.transTemp ?: 0) >= 105) Color.RED else Color.parseColor("#E0E0E0"))
        tvVoltTrim.text = "AKÃœ: %.1fV | S:%+.0f L:%+.0f".format(d.batteryVolt, d.stft, d.ltft)
        tvTank.text = "LPG: %.1f L (~%d km)".format(d.remainingLpg, d.estRangeKm)
        tvTank.setTextColor(when {
            d.remainingLpg < 2f -> Color.RED
            d.remainingLpg < 5f -> Color.parseColor("#FF9100")
            else -> Color.parseColor("#80CBC4")
        })
        val modeTag = if (prefs.autoLpgMode) "OTO" else "MAN"
        btnFuel.text = if (d.isLpg) "$modeTag: LPG" else "$modeTag: BENZÄ°N"
        btnFuel.setBackgroundColor(if (d.isLpg) Color.parseColor("#00E676") else Color.parseColor("#FFC400"))
    }

    override fun onDestroy() {
        scope.cancel()
        obd?.disconnect()
        rootView?.let { wm.removeView(it) }
        super.onDestroy()
    }
}