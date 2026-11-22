package com.aveoobd.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private lateinit var prefs: Prefs

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
            text = t; textSize = 18f; setTextColor(Color.parseColor("#00E5FF")); setPadding(0, 16, 0, 8)
        }

        root.addView(title("Chevrolet Aveo T300 â€” OBD2 & LPG Yol BilgisayarÄ±"))

        // Bluetooth SeÃ§ici
        val btMgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val devices = try { btMgr.adapter?.bondedDevices?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
        val names = devices.map { "${it.name} (${it.address})" }.ifEmpty { listOf("EÅŸleÅŸmiÅŸ Bluetooth cihazÄ± yok") }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names)
            setBackgroundColor(Color.parseColor("#1E293B"))
        }
        root.addView(spinner)

        // BaÅŸlat / Durdur
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 16) }
        val btnStart = Button(this).apply {
            text = "SAÄ PANELÄ° BAÅLAT"
            setBackgroundColor(Color.parseColor("#00E676")); setTextColor(Color.BLACK)
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    Toast.makeText(this@MainActivity, "Ãœzerinde gÃ¶sterme iznini aÃ§Ä±n", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (devices.isNotEmpty()) prefs.btAddress = devices[spinner.selectedItemPosition].address
                startForegroundService(Intent(this@MainActivity, OverlayService::class.java))
                Toast.makeText(this@MainActivity, "SaÄŸ panel baÅŸlatÄ±ldÄ±", Toast.LENGTH_SHORT).show()
            }
        }
        val btnStop = Button(this).apply {
            text = "DURDUR"
            setBackgroundColor(Color.parseColor("#FF5252")); setTextColor(Color.WHITE)
            setOnClickListener { stopService(Intent(this@MainActivity, OverlayService::class.java)) }
        }
        btnRow.addView(btnStart); btnRow.addView(btnStop)
        root.addView(btnRow)

        // LPG & Benzin AyarlarÄ±
        root.addView(title("LPG / Benzin & Sanal Tank AyarlarÄ±"))
        val swAuto = Switch(this).apply {
            text = "Otomatik GeÃ§iÅŸ (50Â°C'de LPG'ye geÃ§)"
            setTextColor(Color.WHITE)
            isChecked = prefs.autoLpgMode
            setOnCheckedChangeListener { _, checked -> prefs.autoLpgMode = checked }
        }
        root.addView(swAuto)

        val etLpgPrice = EditText(this).apply { hint = "LPG Litre FiyatÄ± (TL)"; setText(prefs.lpgPrice.toString()); setTextColor(Color.WHITE) }
        val etPetPrice = EditText(this).apply { hint = "Benzin Litre FiyatÄ± (TL)"; setText(prefs.petrolPrice.toString()); setTextColor(Color.WHITE) }
        val etTemp = EditText(this).apply { hint = "LPG GeÃ§iÅŸ SÄ±caklÄ±ÄŸÄ± (Â°C)"; setText(prefs.lpgSwitchTemp.toString()); setTextColor(Color.WHITE) }
        val etMult = EditText(this).apply { hint = "LPG Ã‡arpanÄ± (Ã¶rn: 1.20)"; setText(prefs.lpgMultiplier.toString()); setTextColor(Color.WHITE) }
        val btnSave = Button(this).apply {
            text = "AYARLARI KAYDET"
            setOnClickListener {
                prefs.lpgPrice = etLpgPrice.text.toString().toFloatOrNull() ?: 26.5f
                prefs.petrolPrice = etPetPrice.text.toString().toFloatOrNull() ?: 45.0f
                prefs.lpgSwitchTemp = etTemp.text.toString().toIntOrNull() ?: 50
                prefs.lpgMultiplier = etMult.text.toString().toFloatOrNull() ?: 1.20f
                Toast.makeText(this@MainActivity, "Ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
            }
        }
        listOf(etLpgPrice, etPetPrice, etTemp, etMult, btnSave).forEach { root.addView(it) }

        // Sanal Tank Dolum
        root.addView(title("Sanal LPG TankÄ± (Kapasite: 33.5 L)"))
        val btnFull = Button(this).apply {
            text = "TAM DOLDURDUM (33.5 L) & SAYACI SIFIRLA"
            setBackgroundColor(Color.parseColor("#0288D1")); setTextColor(Color.WHITE)
            setOnClickListener {
                prefs.remainingLpg = prefs.tankCapacity
                prefs.resetTrip()
                Toast.makeText(this@MainActivity, "Tank 33.5 L yapÄ±ldÄ±, sayaÃ§ sÄ±fÄ±rlandÄ±", Toast.LENGTH_SHORT).show()
            }
        }
        val etAddLpg = EditText(this).apply { hint = "KÄ±smi LPG Dolumu (Litre, Ã¶rn: 15)"; setTextColor(Color.WHITE) }
        val btnAdd = Button(this).apply {
            text = "KISMÄ° DOLUM EKLE"
            setOnClickListener {
                val add = etAddLpg.text.toString().toFloatOrNull() ?: 0f
                if (add > 0f) {
                    prefs.remainingLpg = (prefs.remainingLpg + add).coerceAtMost(prefs.tankCapacity)
                    Toast.makeText(this@MainActivity, "Kalan LPG: %.1f L".format(prefs.remainingLpg), Toast.LENGTH_SHORT).show()
                }
            }
        }
        listOf(btnFull, etAddLpg, btnAdd).forEach { root.addView(it) }

        setContentView(scroll)
    }
}