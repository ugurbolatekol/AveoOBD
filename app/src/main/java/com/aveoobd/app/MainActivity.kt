package com.aveoobd.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private var devices: List<BluetoothDevice> = emptyList()
    private lateinit var spinner: Spinner

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.POST_NOTIFICATIONS), 100)
        }

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0F141C")) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }
        scroll.addView(root)

        fun title(t: String) = TextView(this).apply {
            text = t; textSize = 18f; setTextColor(Color.parseColor("#00E5FF")); setPadding(0, 18, 0, 8)
        }

        fun label(t: String) = TextView(this).apply {
            text = t; textSize = 13f; setTextColor(Color.parseColor("#94A3B8")); setPadding(0, 10, 0, 2)
        }

        root.addView(title("Chevrolet Aveo T300 — OBD2 & LPG Yol Bilgisayarı"))

        // Bluetooth Seçici + Eşleştir + Yenile
        root.addView(label("OBD2 Bluetooth Cihazı (Önce Bluetooth'tan eşleştirin):"))
        spinner = Spinner(this).apply { setBackgroundColor(Color.parseColor("#1E293B")) }
        root.addView(spinner)
        refreshBluetoothList()

        val btBtnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 8) }
        val btnBtSettings = Button(this).apply {
            text = "BLUETOOTH AYARLARINI AÇ"
            setBackgroundColor(Color.parseColor("#334155")); setTextColor(Color.WHITE)
            setOnClickListener { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        }
        val btnRefresh = Button(this).apply {
            text = "LİSTEYİ YENİLE"
            setBackgroundColor(Color.parseColor("#0288D1")); setTextColor(Color.WHITE)
            setOnClickListener {
                refreshBluetoothList()
                Toast.makeText(this@MainActivity, "Bluetooth listesi yenilendi", Toast.LENGTH_SHORT).show()
            }
        }
        btBtnRow.addView(btnBtSettings); btBtnRow.addView(btnRefresh)
        root.addView(btBtnRow)

        // Başlat / Durdur
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 12, 0, 12) }
        val btnStart = Button(this).apply {
            text = "BAĞLAN VE SAĞ PANELİ BAŞLAT"
            setBackgroundColor(Color.parseColor("#00E676")); setTextColor(Color.BLACK)
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    Toast.makeText(this@MainActivity, "Üzerinde gösterme iznini açın", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (devices.isNotEmpty()) prefs.btAddress = devices[spinner.selectedItemPosition].address
                startForegroundService(Intent(this@MainActivity, OverlayService::class.java))
                Toast.makeText(this@MainActivity, "Sağ panel başlatıldı", Toast.LENGTH_SHORT).show()
            }
        }
        val btnStop = Button(this).apply {
            text = "PANELİ KAPAT"
            setBackgroundColor(Color.parseColor("#FF5252")); setTextColor(Color.WHITE)
            setOnClickListener { stopService(Intent(this@MainActivity, OverlayService::class.java)) }
        }
        btnRow.addView(btnStart); btnRow.addView(btnStop)
        root.addView(btnRow)

        // Yakıt Modu Seçimi
        root.addView(title("Yakıt Geçiş Modu"))
        val rgFuel = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val rbAuto = RadioButton(this).apply { id = 1; text = "Otomatik (${prefs.lpgSwitchTemp}°C olunca LPG'ye geç)"; setTextColor(Color.WHITE) }
        val rbManLpg = RadioButton(this).apply { id = 2; text = "Manuel: Sürekli LPG"; setTextColor(Color.WHITE) }
        val rbManPet = RadioButton(this).apply { id = 3; text = "Manuel: Sürekli Benzin"; setTextColor(Color.WHITE) }
        rgFuel.addView(rbAuto); rgFuel.addView(rbManLpg); rgFuel.addView(rbManPet)

        when {
            prefs.autoLpgMode -> rgFuel.check(1)
            prefs.isLpgActive -> rgFuel.check(2)
            else -> rgFuel.check(3)
        }
        rgFuel.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                1 -> prefs.autoLpgMode = true
                2 -> { prefs.autoLpgMode = false; prefs.isLpgActive = true }
                3 -> { prefs.autoLpgMode = false; prefs.isLpgActive = false }
            }
        }
        root.addView(rgFuel)

        // Fiyat ve Kalibrasyon Ayarları
        root.addView(title("Fiyat & Kalibrasyon Ayarları"))
        root.addView(label("LPG Litre Fiyatı (TL):"))
        val etLpgPrice = EditText(this).apply { setText(prefs.lpgPrice.toString()); setTextColor(Color.WHITE) }
        root.addView(etLpgPrice)

        root.addView(label("Benzin Litre Fiyatı (TL):"))
        val etPetPrice = EditText(this).apply { setText(prefs.petrolPrice.toString()); setTextColor(Color.WHITE) }
        root.addView(etPetPrice)

        root.addView(label("Otomatik LPG Geçiş Sıcaklığı (°C):"))
        val etTemp = EditText(this).apply { setText(prefs.lpgSwitchTemp.toString()); setTextColor(Color.WHITE) }
        root.addView(etTemp)

        root.addView(label("LPG Tüketim Çarpanı (örn: 1.20):"))
        val etMult = EditText(this).apply { setText(prefs.lpgMultiplier.toString()); setTextColor(Color.WHITE) }
        root.addView(etMult)

        val btnSave = Button(this).apply {
            text = "AYARLARI KAYDET"
            setOnClickListener {
                prefs.lpgPrice = etLpgPrice.text.toString().toFloatOrNull() ?: 26.5f
                prefs.petrolPrice = etPetPrice.text.toString().toFloatOrNull() ?: 45.0f
                prefs.lpgSwitchTemp = etTemp.text.toString().toIntOrNull() ?: 50
                prefs.lpgMultiplier = etMult.text.toString().toFloatOrNull() ?: 1.20f
                rbAuto.text = "Otomatik (${prefs.lpgSwitchTemp}°C olunca LPG'ye geç)"
                Toast.makeText(this@MainActivity, "Ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(btnSave)

        // Sanal Tank Dolum
        root.addView(title("Sanal LPG Tankı (Kapasite: 33.5 L)"))
        val btnFull = Button(this).apply {
            text = "TAM DOLDURDUM (33.5 L) & SAYACI SIFIRLA"
            setBackgroundColor(Color.parseColor("#0288D1")); setTextColor(Color.WHITE)
            setOnClickListener {
                prefs.remainingLpg = prefs.tankCapacity
                prefs.resetTrip()
                Toast.makeText(this@MainActivity, "Tank 33.5 L yapıldı, sayaç sıfırlandı", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(btnFull)

        root.addView(label("Kısmi LPG Dolumu (Alınan Litre):"))
        val etAddLpg = EditText(this).apply { hint = "örn: 15"; setTextColor(Color.WHITE) }
        val btnAdd = Button(this).apply {
            text = "KISMİ DOLUM EKLE"
            setOnClickListener {
                val add = etAddLpg.text.toString().toFloatOrNull() ?: 0f
                if (add > 0f) {
                    prefs.remainingLpg = (prefs.remainingLpg + add).coerceAtMost(prefs.tankCapacity)
                    Toast.makeText(this@MainActivity, "Kalan LPG: %.1f L".format(prefs.remainingLpg), Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(etAddLpg)
        root.addView(btnAdd)

        setContentView(scroll)
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
