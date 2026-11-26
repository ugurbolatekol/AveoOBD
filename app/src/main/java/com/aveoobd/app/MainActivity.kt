package com.aveoobd.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private var devices: List<BluetoothDevice> = emptyList()
    private lateinit var spinner: Spinner
    private lateinit var tvStatsBox: TextView
    private lateinit var tvTopStatus: TextView
    private lateinit var tvTopTitle: TextView

    private lateinit var viewDashboard: LinearLayout
    private lateinit var viewStats: LinearLayout
    private lateinit var viewSettings: ScrollView
    private lateinit var tabDashBtn: Button
    private lateinit var tabStatsBtn: Button
    private lateinit var tabSettingsBtn: Button

    // Dashboard Elemanları (Ek 2 Tarzı)
    private lateinit var ledEcoBar: LedEcoBarView
    private lateinit var rpmGauge: RpmGaugeView
    private lateinit var tvGearBig: TextView
    private lateinit var barCoolant: GradientBarView
    private lateinit var tvCoolantVal: TextView
    private lateinit var barTrans: GradientBarView
    private lateinit var tvTransVal: TextView
    private lateinit var barInstant: GradientBarView
    private lateinit var tvInstantVal: TextView
    private lateinit var barAvg: GradientBarView
    private lateinit var tvAvgVal: TextView
    private lateinit var barLoad: GradientBarView
    private lateinit var tvLoadVal: TextView
    private lateinit var tvCardVolt: TextView
    private lateinit var tvCardTrim: TextView
    private lateinit var tvCardLpg: TextView

    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiTicker = object : Runnable {
        override fun run() {
            updateDashboardUi()
            uiHandler.postDelayed(this, 150)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.POST_NOTIFICATIONS
                ), 100
            )
        }

        val mainRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#060A10"))
        }

        // ÜST DURUM BARI
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0D1420"))
            setPadding(24, 10, 24, 10)
        }
        tvTopTitle = TextView(this).apply {
            text = "Aveo OBD - Gösterge Paneli"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvTopStatus = TextView(this).apply {
            text = "BAĞLI DEĞİL"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFC400"))
        }
        topBar.addView(tvTopTitle)
        topBar.addView(tvTopStatus)
        mainRoot.addView(topBar)

        // ORTA İÇERİK ALANI
        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        viewDashboard = buildDashboardView()
        viewStats = buildStatsView()
        viewSettings = buildSettingsView()

        contentFrame.addView(viewDashboard)
        contentFrame.addView(viewStats)
        contentFrame.addView(viewSettings)
        mainRoot.addView(contentFrame)

        // ALT NAVİGASYON BARI
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0D1420"))
            setPadding(8, 6, 8, 6)
        }

        tabDashBtn = makeTabButton("GÖSTERGE PANELİ") { switchTab(0) }
        tabStatsBtn = makeTabButton("İSTATİSTİKLER") { switchTab(1) }
        tabSettingsBtn = makeTabButton("AYARLAR") { switchTab(2) }
        val tabPipBtn = makeTabButton("SAĞ PANEL (PiP)") { launchPipMode() }.apply {
            setBackgroundColor(Color.parseColor("#00B8D4"))
            setTextColor(Color.BLACK)
        }

        bottomBar.addView(tabDashBtn)
        bottomBar.addView(tabStatsBtn)
        bottomBar.addView(tabSettingsBtn)
        bottomBar.addView(tabPipBtn)
        mainRoot.addView(bottomBar)

        setContentView(mainRoot)

        if (prefs.btAddress.isBlank()) {
            switchTab(2)
        } else {
            switchTab(0)
            startObdBackgroundIfPermitted()
        }
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(uiTicker)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(uiTicker)
    }

    private fun startObdBackgroundIfPermitted() {
        if (Settings.canDrawOverlays(this)) {
            val i = Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_HIDE_PIP }
            try { startForegroundService(i) } catch (_: Exception) {}
        }
    }

    private fun launchPipMode() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Üzerinde gösterme iznini açın", Toast.LENGTH_LONG).show()
            return
        }
        if (devices.isNotEmpty() && spinner.selectedItemPosition in devices.indices) {
            prefs.btAddress = devices[spinner.selectedItemPosition].address
        }
        val i = Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_SHOW_PIP }
        startForegroundService(i)
        moveTaskToBack(true)
    }

    private fun switchTab(idx: Int) {
        viewDashboard.visibility = if (idx == 0) View.VISIBLE else View.GONE
        viewStats.visibility = if (idx == 1) View.VISIBLE else View.GONE
        viewSettings.visibility = if (idx == 2) View.VISIBLE else View.GONE

        tvTopTitle.text = when (idx) {
            0 -> "Aveo OBD - Gösterge Paneli"
            1 -> "Aveo OBD - Sürüş İstatistikleri"
            else -> "Aveo OBD - Ayarlar"
        }

        val activeBg = Color.parseColor("#00E5FF")
        val idleBg = Color.parseColor("#1E293B")
        listOf(tabDashBtn, tabStatsBtn, tabSettingsBtn).forEachIndexed { i, btn ->
            btn.setBackgroundColor(if (i == idx) activeBg else idleBg)
            btn.setTextColor(if (i == idx) Color.BLACK else Color.WHITE)
        }
        if (idx == 1) refreshStatsView()
    }

    private fun makeTabButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(4, 0, 4, 0)
        }
        setOnClickListener { onClick() }
    }

    private fun makeCardBg(borderColor: String = "#1E293B") = GradientDrawable().apply {
        setColor(Color.parseColor("#0D1420"))
        cornerRadius = 18f
        setStroke(2, Color.parseColor(borderColor))
    }

    // ==================== 1. GÖSTERGE PANELİ (DEVASELAŞTIRILMIŞ) ====================
    private fun buildDashboardView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }

        // LED Verimlilik Barı
        ledEcoBar = LedEcoBarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 42).apply {
                bottomMargin = 6
            }
        }
        root.addView(ledEcoBar)

        val middleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // SOL: RPM Kadranı + Vites Kutusu
        val leftBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.44f)
        }
        rpmGauge = RpmGaugeView(this)
        leftBox.addView(rpmGauge)

        tvGearBig = TextView(this).apply {
            text = "P/N"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            background = makeCardBg("#00E676").apply { strokeWidth = 3 }
            setPadding(22, 8, 22, 8)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply { setMargins(0, 6, 12, 0) }
        }
        leftBox.addView(tvGearBig)
        middleRow.addView(leftBox)

        // SAĞ: Devasa Değerler + Kalın Gösterge Barları
        val rightBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 0, 4, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.56f)
        }

        fun addMetricRow(label: String, valColor: String = "#FFFFFF"): Pair<TextView, GradientBarView> {
            val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val tvLbl = TextView(this).apply {
                text = label; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#94A3B8"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            // Değer boyutu 14sp'den 24sp'ye çıkarıldı ve kalınlaştırıldı
            val tvVal = TextView(this).apply {
                text = "--"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor(valColor))
            }
            header.addView(tvLbl); header.addView(tvVal)
            // Gösterge bar yüksekliği 15dp'den 26dp'ye çıkarıldı
            val bar = GradientBarView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 26).apply {
                    topMargin = 4; bottomMargin = 8
                }
            }
            rightBox.addView(header)
            rightBox.addView(bar)
            return Pair(tvVal, bar)
        }

        val (vCool, bCool) = addMetricRow("SU SICAKLIĞI", "#00E5FF"); tvCoolantVal = vCool; barCoolant = bCool
        val (vTrans, bTrans) = addMetricRow("ŞANZIMAN YAĞI", "#FFC400"); tvTransVal = vTrans; barTrans = bTrans
        val (vInst, bInst) = addMetricRow("ANLIK TÜKETİM", "#00E676"); tvInstantVal = vInst; barInstant = bInst
        val (vAvg, bAvg) = addMetricRow("ORTALAMA (MALİYET)", "#FF9100"); tvAvgVal = vAvg; barAvg = bAvg
        val (vLoad, bLoad) = addMetricRow("MOTOR YÜKÜ", "#E2E8F0"); tvLoadVal = vLoad; barLoad = bLoad

        middleRow.addView(rightBox)
        root.addView(middleRow)

        // ALT KARTLAR
        val cardsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 6, 0, 0)
        }
        fun makeBottomCard(initText: String, border: String) = TextView(this).apply {
            text = initText; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; background = makeCardBg(border).apply { strokeWidth = 3 }; setPadding(12, 12, 12, 12)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(6, 0, 6, 0)
            }
        }
        tvCardVolt = makeBottomCard("AKÜ: -- V", "#00E676")
        tvCardTrim = makeBottomCard("TRİM: S:0 L:0", "#0288D1")
        tvCardLpg = makeBottomCard("LPG: %.1f L".format(prefs.remainingLpg), "#FFC400")
        cardsRow.addView(tvCardVolt); cardsRow.addView(tvCardTrim); cardsRow.addView(tvCardLpg)
        root.addView(cardsRow)

        return root
    }

    private fun updateDashboardUi() {
        tvTopStatus.text = if (ObdState.isConnected) "OBD2 BAĞLI" else ObdState.statusText
        tvTopStatus.setTextColor(if (ObdState.isConnected) Color.parseColor("#00E676") else Color.parseColor("#FFC400"))

        val d = ObdState.latestData ?: return

        val ecoLevel = maxOf(d.load / 100f, d.rpm / 5000f).coerceIn(0.05f, 1f)
        ledEcoBar.progress = ecoLevel
        ledEcoBar.invalidate()

        rpmGauge.rpm = d.rpm
        rpmGauge.speed = d.speed
        rpmGauge.maxRpm = ObdState.maxSessionRpm
        rpmGauge.maxSpeed = ObdState.maxSessionSpeed
        rpmGauge.invalidate()

        tvGearBig.text = ObdState.estimateGear(d.rpm, d.speed)

        tvCoolantVal.text = "${d.coolantTemp} °C"
        barCoolant.progress = ((d.coolantTemp - 20f) / 90f).coerceIn(0f, 1f)
        barCoolant.invalidate()

        val t = d.transTemp
        tvTransVal.text = t?.let { "$it °C" } ?: "-- °C"
        barTrans.progress = if (t != null) ((t - 20f) / 100f).coerceIn(0f, 1f) else 0f
        barTrans.invalidate()

        tvInstantVal.text = if (d.speed >= 3) "%.1f L/100km".format(d.instantL100) else "%.2f L/sa".format(d.instantLh)
        barInstant.progress = (if (d.speed >= 3) d.instantL100 / 20.0 else d.instantLh / 4.0).toFloat().coerceIn(0f, 1f)
        barInstant.invalidate()

        tvAvgVal.text = "%.1f L  (%.2f TL)".format(d.avgL100, d.tlPerKm)
        barAvg.progress = (d.avgL100 / 18.0).toFloat().coerceIn(0f, 1f)
        barAvg.invalidate()

        tvLoadVal.text = "% ${d.load}"
        barLoad.progress = (d.load / 100f).coerceIn(0f, 1f)
        barLoad.invalidate()

        val fuelTag = if (d.isLpg) "LPG" else "BENZİN"
        tvCardVolt.text = "AKÜ: %.1f V".format(d.batteryVolt)
        tvCardTrim.text = "TRİM: S:%+.0f L:%+.0f".format(d.stft, d.ltft)
        tvCardLpg.text = "$fuelTag | %.1f L (~%d km)".format(d.remainingLpg, d.estRangeKm)
    }

    // ==================== 2. İSTATİSTİKLER ====================
    private fun buildStatsView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        tvStatsBox = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#E2E8F0"))
            background = makeCardBg("#334155")
            setPadding(24, 20, 24, 20)
        }
        scroll.addView(tvStatsBox)
        root.addView(scroll)

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 12, 0, 0) }
        val btnRef = Button(this).apply {
            text = "İSTATİSTİKLERİ YENİLE"; setBackgroundColor(Color.parseColor("#0288D1")); setTextColor(Color.WHITE)
            setOnClickListener { refreshStatsView() }
        }
        val btnRes = Button(this).apply {
            text = "İSTATİSTİKLERİ SIFIRLA"; setBackgroundColor(Color.parseColor("#475569")); setTextColor(Color.WHITE)
            setOnClickListener {
                prefs.resetAllStats(); refreshStatsView()
                Toast.makeText(this@MainActivity, "İstatistikler sıfırlandı", Toast.LENGTH_SHORT).show()
            }
        }
        btnRow.addView(btnRef); btnRow.addView(btnRes)
        root.addView(btnRow)
        return root
    }

    private fun refreshStatsView() {
        val maxC = if (prefs.maxCoolant > -999) "${prefs.maxCoolant}°C" else "--"
        val minC = if (prefs.minCoolant < 999) "${prefs.minCoolant}°C" else "--"
        val maxT = if (prefs.maxTrans > -999) "${prefs.maxTrans}°C" else "--"
        val maxF = if (prefs.maxFuelL100 > 0f) "%.1f L/100km".format(prefs.maxFuelL100) else "--"
        val minF = if (prefs.minFuelL100 < 999f) "%.1f L/100km".format(prefs.minFuelL100) else "--"

        tvStatsBox.text = """
            [ REKORLAR / MİN - MAX ]
            • En Yüksek Su Sıcaklığı: $maxC   |   En Düşük: $minC
            • En Yüksek Şanzıman Yağı: $maxT
            • En Yüksek Tüketim: $maxF   |   En Düşük: $minF

            [ GÜNLÜK SÜRÜŞLER (Son 7 Gün) ]
            ${prefs.getDailySummary()}

            [ AYLIK SÜRÜŞLER ]
            ${prefs.getMonthlySummary()}
        """.trimIndent()
    }

    // ==================== 3. AYARLAR ====================
    @SuppressLint("MissingPermission")
    private fun buildSettingsView(): ScrollView {
        val scroll = ScrollView(this)
        val cols = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 14, 20, 14)
        }

        fun secTitle(t: String) = TextView(this).apply {
            text = t; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E5FF")); setPadding(0, 10, 0, 6)
        }
        fun lbl(t: String) = TextView(this).apply {
            text = t; textSize = 12f; setTextColor(Color.parseColor("#94A3B8")); setPadding(0, 6, 0, 2)
        }

        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeCardBg("#1E293B")
            setPadding(18, 12, 18, 16)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 10 }
        }
        leftCol.addView(secTitle("BLUETOOTH & BAĞLANTI"))
        spinner = Spinner(this).apply { setBackgroundColor(Color.parseColor("#1E293B")) }
        leftCol.addView(spinner)
        refreshBluetoothList()

        val btRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 8) }
        val btnBt = Button(this).apply {
            text = "BT AYARLARI"; setBackgroundColor(Color.parseColor("#334155")); setTextColor(Color.WHITE)
            setOnClickListener { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        }
        val btnRef = Button(this).apply {
            text = "YENİLE"; setBackgroundColor(Color.parseColor("#0288D1")); setTextColor(Color.WHITE)
            setOnClickListener { refreshBluetoothList() }
        }
        val btnConn = Button(this).apply {
            text = "BAĞLAN"; setBackgroundColor(Color.parseColor("#00E676")); setTextColor(Color.BLACK)
            setOnClickListener {
                if (devices.isNotEmpty() && spinner.selectedItemPosition in devices.indices) {
                    prefs.btAddress = devices[spinner.selectedItemPosition].address
                }
                startObdBackgroundIfPermitted()
                switchTab(0)
            }
        }
        btRow.addView(btnBt); btRow.addView(btnRef); btRow.addView(btnConn)
        leftCol.addView(btRow)

        val swBoot = Switch(this).apply {
            text = "TS10 Açıldığında Otomatik Başlat"; setTextColor(Color.WHITE); isChecked = prefs.autoStart
            setOnCheckedChangeListener { _, checked -> prefs.autoStart = checked }
        }
        leftCol.addView(swBoot)

        leftCol.addView(secTitle("YAKIT GEÇİŞ MODU"))
        val rgFuel = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val rbAuto = RadioButton(this).apply { id = 1; text = "Otomatik (${prefs.lpgSwitchTemp}°C olunca LPG)"; setTextColor(Color.WHITE) }
        val rbLpg = RadioButton(this).apply { id = 2; text = "Manuel: Sürekli LPG"; setTextColor(Color.WHITE) }
        val rbPet = RadioButton(this).apply { id = 3; text = "Manuel: Sürekli Benzin"; setTextColor(Color.WHITE) }
        rgFuel.addView(rbAuto); rgFuel.addView(rbLpg); rgFuel.addView(rbPet)
        when {
            prefs.autoLpgMode -> rgFuel.check(1)
            prefs.isLpgActive -> rgFuel.check(2)
            else -> rgFuel.check(3)
        }
        rgFuel.setOnCheckedChangeListener { _, id ->
            when (id) {
                1 -> prefs.autoLpgMode = true
                2 -> { prefs.autoLpgMode = false; prefs.isLpgActive = true }
                3 -> { prefs.autoLpgMode = false; prefs.isLpgActive = false }
            }
        }
        leftCol.addView(rgFuel)

        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeCardBg("#1E293B")
            setPadding(18, 12, 18, 16)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 10 }
        }
        rightCol.addView(secTitle("FİYAT & KALİBRASYON"))
        rightCol.addView(lbl("LPG Fiyatı (TL) / Benzin Fiyatı (TL):"))
        val priceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val etLpg = EditText(this).apply {
            setText(prefs.lpgPrice.toString()); setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val etPet = EditText(this).apply {
            setText(prefs.petrolPrice.toString()); setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        priceRow.addView(etLpg); priceRow.addView(etPet)
        rightCol.addView(priceRow)

        rightCol.addView(lbl("Geçiş Sıcaklığı (°C) / LPG Çarpanı:"))
        val calRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val etTemp = EditText(this).apply {
            setText(prefs.lpgSwitchTemp.toString()); setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val etMult = EditText(this).apply {
            setText(prefs.lpgMultiplier.toString()); setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        calRow.addView(etTemp); calRow.addView(etMult)
        rightCol.addView(calRow)

        val btnSave = Button(this).apply {
            text = "AYARLARI KAYDET"; setBackgroundColor(Color.parseColor("#00E676")); setTextColor(Color.BLACK)
            setOnClickListener {
                prefs.lpgPrice = etLpg.text.toString().toFloatOrNull() ?: 26.5f
                prefs.petrolPrice = etPet.text.toString().toFloatOrNull() ?: 45.0f
                prefs.lpgSwitchTemp = etTemp.text.toString().toIntOrNull() ?: 50
                prefs.lpgMultiplier = etMult.text.toString().toFloatOrNull() ?: 1.20f
                rbAuto.text = "Otomatik (${prefs.lpgSwitchTemp}°C olunca LPG)"
                Toast.makeText(this@MainActivity, "Ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
            }
        }
        rightCol.addView(btnSave)

        rightCol.addView(secTitle("SANAL LPG TANKI (33.5 L)"))
        val btnFull = Button(this).apply {
            text = "TAM DOLDURDUM (33.5 L) & SIFIRLA"
            setBackgroundColor(Color.parseColor("#0288D1")); setTextColor(Color.WHITE)
            setOnClickListener {
                prefs.remainingLpg = prefs.tankCapacity; prefs.resetTrip()
                Toast.makeText(this@MainActivity, "Tank 33.5 L yapıldı, sayaç sıfırlandı", Toast.LENGTH_SHORT).show()
            }
        }
        rightCol.addView(btnFull)

        val addRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 6, 0, 0) }
        val etAdd = EditText(this).apply {
            hint = "Litre (örn: 15)"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnAdd = Button(this).apply {
            text = "EKLE"
            setOnClickListener {
                val add = etAdd.text.toString().toFloatOrNull() ?: 0f
                if (add > 0f) {
                    prefs.remainingLpg = (prefs.remainingLpg + add).coerceAtMost(prefs.tankCapacity)
                    Toast.makeText(this@MainActivity, "Kalan LPG: %.1f L".format(prefs.remainingLpg), Toast.LENGTH_SHORT).show()
                }
            }
        }
        addRow.addView(etAdd); btnAdd.let { addRow.addView(it) }
        rightCol.addView(addRow)

        cols.addView(leftCol)
        cols.addView(rightCol)
        scroll.addView(cols)
        return scroll
    }

    @SuppressLint("MissingPermission")
    private fun refreshBluetoothList() {
        val btMgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        devices = try { btMgr.adapter?.bondedDevices?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
        val names = devices.map { "${it.name} (${it.address})" }.ifEmpty { listOf("Eşleşmiş Bluetooth cihazı yok") }
        spinner.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, names) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(Color.WHITE)
                return v
            }
        }
    }
}
