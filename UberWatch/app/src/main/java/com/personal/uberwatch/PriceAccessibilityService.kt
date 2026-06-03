package com.personal.uberwatch

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import java.util.regex.Pattern

class PriceAccessibilityService : AccessibilityService() {

    companion object {
        private const val CHANNEL_ID = "uberwatch_alerts"
        private const val NOTIF_ID = 4242
        // Throttle: não reprocessar a árvore inteira a cada evento (são muitos).
        private const val MIN_INTERVAL_MS = 1500L
        // Preços plausíveis de corrida (filtra "R$ 5,00 de desconto" etc grosseiramente)
        private const val MIN_PLAUSIBLE = 5.0f
        private const val MAX_PLAUSIBLE = 2000.0f
    }

    // Regex para R$ 12,34 / R$12.34 / 12,34
    private val pricePattern: Pattern =
        Pattern.compile("R?\\$?\\s*([0-9]{1,4})[.,]([0-9]{2})")

    private var lastScan = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        createChannel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = System.currentTimeMillis()
        if (now - lastScan < MIN_INTERVAL_MS) return
        lastScan = now

        val root = rootInActiveWindow ?: return
        // Só processa se a janela ativa for da Uber
        if (root.packageName?.toString() != "com.ubercab") return

        val prices = mutableListOf<Float>()
        collectPrices(root, prices)
        root.recycle()

        if (prices.isEmpty()) return

        // Heurística: o preço da corrida costuma ser o maior valor plausível na tela
        // (vs. valores menores como taxas, descontos). Ajustável.
        val candidate = prices
            .filter { it in MIN_PLAUSIBLE..MAX_PLAUSIBLE }
            .maxOrNull() ?: return

        evaluate(candidate)
    }

    private fun collectPrices(node: AccessibilityNodeInfo?, out: MutableList<Float>) {
        if (node == null) return
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        listOfNotNull(text, desc).forEach { s ->
            val m = pricePattern.matcher(s)
            while (m.find()) {
                val whole = m.group(1)
                val cents = m.group(2)
                val value = "$whole.$cents".toFloatOrNull()
                if (value != null) out.add(value)
            }
        }
        for (i in 0 until node.childCount) {
            collectPrices(node.getChild(i), out)
        }
    }

    private fun evaluate(price: Float) {
        val target = Config.targetPrice(this)
        val dropPct = Config.dropPct(this)

        // Atualiza pico observado
        val peak = Config.peak(this)
        if (price > peak) {
            Config.setPeak(this, price)
        }
        val currentPeak = maxOf(peak, price)

        // Evita notificar repetidamente para o mesmo preço (ou maior)
        val lastAlert = Config.lastAlert(this)

        var triggered = false
        var reason = ""

        // Regra 1: abaixo do alvo fixo
        if (target > 0f && price <= target) {
            triggered = true
            reason = "abaixo do alvo (R$ ${"%.2f".format(target)})"
        }

        // Regra 2: caiu X% do pico
        if (!triggered && dropPct > 0f && currentPeak > 0f) {
            val threshold = currentPeak * (1f - dropPct / 100f)
            if (price <= threshold) {
                triggered = true
                reason = "caiu ${"%.1f".format(dropPct)}% do pico (R$ ${"%.2f".format(currentPeak)})"
            }
        }

        if (triggered) {
            // Só alerta se for um preço novo/menor que o último alertado
            if (lastAlert == 0f || price < lastAlert) {
                Config.setLastAlert(this, price)
                notifyDrop(price, reason)
            }
        }
    }

    private fun notifyDrop(price: Float, reason: String) {
        val openUber = packageManager.getLaunchIntentForPackage("com.ubercab")
        val pi = if (openUber != null) {
            PendingIntent.getActivity(
                this, 0, openUber,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else null

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Preço caiu: R$ ${"%.2f".format(price)}")
            .setContentText(reason)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { if (pi != null) setContentIntent(pi) }
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, notif)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Alertas de preço Uber",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(ch)
        }
    }

    override fun onInterrupt() {}
}
