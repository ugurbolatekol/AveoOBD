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

    // Dashboard Elemanları
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

    private fun makeCardBg(borderColor: String = "#1E293B", strokeWidthPx: Int = 2) = GradientDrawable().apply {
        setColor(Color.parseColor("#0D1420"))
        cornerRadius = 18f
        setStroke(strokeWidthPx, Color.parseColor(borderColor))
    }

    // ==================== 1. GÖSTERGE PANELİ ====================
    private fun buildDashboardView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }

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
            type
