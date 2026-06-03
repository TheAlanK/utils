package com.personal.uberwatch

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import java.text.Normalizer
import java.util.regex.Pattern
import kotlin.math.abs

class PriceAccessibilityService : AccessibilityService() {

    companion object {
        const val UBER_PKG = "com.ubercab"
        private const val CHANNEL_ID = "uberwatch_alerts"
        // Relê a tela periodicamente mesmo sem eventos (a Uber não dispara evento
        // de acessibilidade quando o preço fica parado na tela).
        private const val POLL_INTERVAL_MS = 2000L
        // Throttle para não reprocessar a árvore a cada evento (são muitos).
        private const val EVENT_THROTTLE_MS = 700L
        // Evita floodar o log quando o app em foreground não é a Uber.
        private const val FOREIGN_LOG_THROTTLE_MS = 5000L
        // Preços plausíveis de corrida (filtra "R$ 5,00 de desconto" etc grosseiramente)
        private const val MIN_PLAUSIBLE = 5.0f
        private const val MAX_PLAUSIBLE = 2000.0f
    }

    // Regex para R$ 12,34 / R$12.34 / 12,34
    private val pricePattern: Pattern =
        Pattern.compile("R?\\$?\\s*([0-9]{1,4})[.,]([0-9]{2})")

    // Leitura roda em background: refresh() faz IPC e não pode travar a main thread.
    private var worker: HandlerThread? = null
    private var bg: Handler? = null
    private val poller = object : Runnable {
        override fun run() {
            scan()
            bg?.postDelayed(this, POLL_INTERVAL_MS)
        }
    }
    private var lastEvent = 0L
    private var lastForeignLog = 0L

    // Um texto lido na tela com a posição vertical do seu nó (para agrupar por linha).
    private data class Item(val text: String, val cy: Int, val height: Int)

    override fun onServiceConnected() {
        super.onServiceConnected()
        createChannel()
        Diag.serviceConnected = true
        Diag.log("Serviço de acessibilidade conectado")
        val t = HandlerThread("uberwatch-scan").apply { start() }
        worker = t
        bg = Handler(t.looper).also {
            it.removeCallbacks(poller)
            it.post(poller)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Também relê em eventos, para resposta mais rápida quando a tela muda.
        val now = System.currentTimeMillis()
        if (now - lastEvent < EVENT_THROTTLE_MS) return
        lastEvent = now
        val type = event?.let { AccessibilityEvent.eventTypeToString(it.eventType) } ?: "?"
        Diag.log("evento: $type pkg=${event?.packageName}")
        bg?.post { scan() }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Diag.serviceConnected = false
        Diag.log("Serviço desconectado")
        bg?.removeCallbacksAndMessages(null)
        worker?.quitSafely()
        worker = null
        bg = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    private fun scan() {
        Diag.markScan()
        if (!Config.monitoring(this)) return

        val root = rootInActiveWindow
        if (root == null) {
            Diag.log("tick: sem janela ativa (rootInActiveWindow=null)")
            return
        }
        val pkg = root.packageName?.toString()
        if (pkg != UBER_PKG) {
            root.recycle()
            val now = System.currentTimeMillis()
            if (now - lastForeignLog > FOREIGN_LOG_THROTTLE_MS) {
                lastForeignLog = now
                Diag.log("tick: app em foreground não é a Uber (pkg=$pkg)")
            }
            return
        }

        val items = mutableListOf<Item>()
        collect(root, items)
        root.recycle()

        // Todos os preços plausíveis da tela, com a posição vertical onde aparecem.
        val priceItems = mutableListOf<Pair<Float, Int>>() // valor, cy
        for (it in items) {
            val m = pricePattern.matcher(it.text)
            while (m.find()) {
                val v = "${m.group(1)}.${m.group(2)}".toFloatOrNull() ?: continue
                if (v in MIN_PLAUSIBLE..MAX_PLAUSIBLE) priceItems.add(v to it.cy)
            }
        }
        if (priceItems.isEmpty()) {
            Diag.log("tick: nenhum preço plausível na tela (textos lidos=${items.size})")
            return
        }

        val wanted = Config.categories(this)
        // Mantém ordem de inserção para o status ficar estável.
        val results = LinkedHashMap<String, Float>() // categoria -> preço com desconto

        if (wanted.isEmpty()) {
            // Sem categorias: monitora o menor preço plausível da tela inteira.
            results["Menor preço"] = priceItems.minOf { it.first }
        } else {
            for (cat in wanted) {
                val catNorm = normalize(cat)
                if (catNorm.isEmpty()) continue
                // Rótulo da categoria: texto sem dígitos que casa com o nome pedido.
                val label = items.firstOrNull { item ->
                    val n = normalize(item.text)
                    n.isNotEmpty() && item.text.none { c -> c.isDigit() } &&
                        (n.contains(catNorm) || catNorm.startsWith(n) || n.startsWith(catNorm))
                }
                if (label == null) {
                    Diag.log("  '$cat': rótulo não encontrado na tela")
                    continue
                }
                // Preços na MESMA linha do rótulo (mesmo eixo vertical).
                val tol = maxOf(label.height, 1)
                val rowPrices = priceItems.filter { abs(it.second - label.cy) <= tol }
                // Preço com DESCONTO = o menor da linha (o cheio fica riscado e é maior).
                val effective = rowPrices.minByOrNull { it.first }?.first
                if (effective == null) {
                    Diag.log("  '$cat': rótulo achado mas sem preço na mesma linha")
                    continue
                }
                results[cat] = effective
            }
        }

        if (results.isEmpty()) {
            Diag.log("tick: preços=${priceItems.size} na tela, mas nenhuma categoria casou")
            return
        }

        // Atualiza resumo para a UI + log.
        val summary = results.entries.joinToString(", ") { "${it.key}=${"%.2f".format(it.value)}" }
        Config.setLastStatus(
            this,
            results.entries.joinToString("\n") { "${it.key}: R$ ${"%.2f".format(it.value)}" }
        )
        Diag.log("tick: $summary")

        for ((cat, price) in results) evaluate(cat, price)
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<Item>) {
        if (node == null) return
        // Busca conteúdo fresco do app: o cache de acessibilidade pode estar velho, o
        // que fazia o preço só "atualizar" quando a janela mudava de foco. Só vale a pena
        // em nós que têm texto (evita IPC desnecessário nos containers).
        val hasContent = node.text != null || node.contentDescription != null
        if (hasContent) {
            try { node.refresh() } catch (_: Exception) {}
        }
        val r = Rect()
        node.getBoundsInScreen(r)
        val cy = (r.top + r.bottom) / 2
        val h = r.height()
        node.text?.toString()?.let { if (it.isNotBlank()) out.add(Item(it, cy, h)) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) out.add(Item(it, cy, h)) }
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            collect(child, out)
            try { child.recycle() } catch (_: Exception) {}
        }
    }

    private fun evaluate(cat: String, price: Float) {
        val target = Config.targetPrice(this)
        val dropPct = Config.dropPct(this)

        // Atualiza pico observado desta categoria.
        val peak = Config.peak(this, cat)
        if (price > peak) Config.setPeak(this, cat, price)
        val currentPeak = maxOf(peak, price)

        val lastAlert = Config.lastAlert(this, cat)

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

        // Só alerta se for um preço novo/menor que o último alertado (evita spam).
        if (triggered && (lastAlert == 0f || price < lastAlert)) {
            Config.setLastAlert(this, cat, price)
            Diag.log("ALERTA $cat R$ ${"%.2f".format(price)} — $reason")
            notifyDrop(cat, price, reason)
        }
    }

    private fun notifyDrop(cat: String, price: Float, reason: String) {
        val openUber = packageManager.getLaunchIntentForPackage(UBER_PKG)
        val pi = if (openUber != null) {
            PendingIntent.getActivity(
                this, cat.hashCode(), openUber,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else null

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$cat caiu: R$ ${"%.2f".format(price)}")
            .setContentText(reason)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { if (pi != null) setContentIntent(pi) }
            .build()

        // ID por categoria: permite vários alertas simultâneos sem sobrescrever.
        getSystemService(NotificationManager::class.java).notify(cat.hashCode(), notif)
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

    // minúsculas, sem acentos, só letras/dígitos — para casar nomes de categoria
    // mesmo truncados ("Priorida..." casa com "Prioridade").
    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .filter { it.isLetterOrDigit() }
}
