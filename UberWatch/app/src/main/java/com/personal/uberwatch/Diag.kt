package com.personal.uberwatch

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Monitoramento interno: buffer de logs em memória (compartilhado entre o serviço
// e a Activity, que rodam no mesmo processo) + espelho no Logcat (tag "UberWatch").
object Diag {
    const val TAG = "UberWatch"
    private const val MAX_LINES = 200

    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Volatile
    var serviceConnected: Boolean = false

    @Volatile
    var scanCount: Int = 0
        private set

    @Volatile
    var lastScanAt: Long = 0L
        private set

    // Marca que um ciclo de leitura aconteceu (prova de que o polling está vivo).
    fun markScan() {
        scanCount++
        lastScanAt = System.currentTimeMillis()
    }

    fun log(msg: String) {
        Log.d(TAG, msg)
        val line = "${fmt.format(Date())}  $msg"
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
    }

    // Linhas mais recentes primeiro.
    fun recent(n: Int = 40): String = synchronized(lines) {
        if (lines.isEmpty()) "(sem logs ainda)"
        else lines.toList().takeLast(n).asReversed().joinToString("\n")
    }

    fun clear() {
        synchronized(lines) { lines.clear() }
        scanCount = 0
        lastScanAt = 0L
    }
}
