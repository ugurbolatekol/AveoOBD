package com.aveoobd.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class ObdManager(private val adapter: BluetoothAdapter?) {
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @SuppressLint("MissingPermission")
    fun connect(address: String): Boolean {
        disconnect()
        if (adapter == null || address.isBlank()) return false
        return try {
            val device = adapter.getRemoteDevice(address)
            socket = device.createRfcommSocketToServiceRecord(sppUuid)
            adapter.cancelDiscovery()
            socket?.connect()
            input = socket?.inputStream
            output = socket?.outputStream
            sendCmd("ATZ", 600)
            sendCmd("ATE0", 200)
            sendCmd("ATL0", 200)
            sendCmd("ATS0", 200)
            sendCmd("ATSP0", 300)
            true
        } catch (e: Exception) {
            disconnect()
            false
        }
    }

    fun sendCmd(cmd: String, delayMs: Long = 80): String {
        return try {
            output?.write("$cmd\r".toByteArray())
            output?.flush()
            Thread.sleep(delayMs)
            val sb = StringBuilder()
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < 700) {
                if ((input?.available() ?: 0) > 0) {
                    val c = input!!.read().toChar()
                    if (c == '>') break
                    sb.append(c)
                } else {
                    Thread.sleep(15)
                }
            }
            sb.toString().replace("\r", "").replace("\n", "").replace(" ", "").trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun readIntPid(pid: String, expectedHeader: String): List<Int>? {
        val raw = sendCmd(pid)
        val idx = raw.indexOf(expectedHeader)
        if (idx == -1) return null
        val hex = raw.substring(idx + expectedHeader.length)
        val bytes = mutableListOf<Int>()
        var i = 0
        while (i + 1 < hex.length && bytes.size < 4) {
            val b = hex.substring(i, i + 2).toIntOrNull(16) ?: break
            bytes.add(b)
            i += 2
        }
        return if (bytes.isEmpty()) null else bytes
    }

    fun readTransTempGM(): Int? {
        sendCmd("ATSH7E2", 60)
        val raw = sendCmd("221940", 120)
        sendCmd("ATSH7DF", 60)
        val idx = raw.indexOf("621940")
        if (idx == -1 || idx + 8 > raw.length) return null
        val a = raw.substring(idx + 6, idx + 8).toIntOrNull(16) ?: return null
        return a - 40
    }

    fun readVoltage(): Double {
        val raw = sendCmd("ATRV", 100)
        val cleaned = raw.replace("V", "", ignoreCase = true).replace("[^0-9.]".toRegex(), "")
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    fun readDtcs(): String {
        val raw = sendCmd("03", 300)
        return if (raw.contains("43") && raw.length > 4) raw else "ArÄ±za kodu yok"
    }

    fun clearDtcs(): Boolean {
        val raw = sendCmd("04", 300)
        return raw.contains("44")
    }

    fun disconnect() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null; input = null; output = null
    }
}