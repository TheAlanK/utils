package com.personal.uberwatch

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.personal.uberwatch.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Pré-preenche com config salva
        val t = Config.targetPrice(this)
        val d = Config.dropPct(this)
        if (t > 0) b.inputTarget.setText(t.toString())
        if (d > 0) b.inputDropPct.setText(d.toString())
        b.inputCategories.setText(Config.categoriesRaw(this))

        b.btnSave.setOnClickListener {
            val target = b.inputTarget.text.toString().toFloatOrNull() ?: 0f
            val drop = b.inputDropPct.text.toString().toFloatOrNull() ?: 0f
            val cats = b.inputCategories.text.toString()
            Config.save(this, target, drop, cats)
            Config.reset(this)
            Toast.makeText(this, "Config salva. Histórico resetado.", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }

        b.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Botão principal: liga o monitoramento e já abre a Uber em foreground.
        b.btnStartMonitor.setOnClickListener {
            if (!isAccessibilityEnabled()) {
                Toast.makeText(
                    this,
                    "Ative a Acessibilidade do UberWatch primeiro.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            val launch = packageManager.getLaunchIntentForPackage(PriceAccessibilityService.UBER_PKG)
            if (launch == null) {
                Toast.makeText(this, "App da Uber não encontrado.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Config.setMonitoring(this, true)
            Config.reset(this)
            Toast.makeText(
                this,
                "Monitorando. Abra a tela de estimativa de preço da Uber.",
                Toast.LENGTH_LONG
            ).show()
            refreshStatus()
            startActivity(launch)
        }

        b.btnStopMonitor.setOnClickListener {
            Config.setMonitoring(this, false)
            Toast.makeText(this, "Monitoramento parado.", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }

        b.btnReset.setOnClickListener {
            Config.reset(this)
            Toast.makeText(this, "Histórico resetado.", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // Confere se o serviço de acessibilidade do app está ativo nas configurações.
    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(packageName) &&
            enabled.contains(PriceAccessibilityService::class.java.simpleName)
    }

    private fun refreshStatus() {
        val target = Config.targetPrice(this)
        val drop = Config.dropPct(this)
        val cats = Config.categories(this)
        val monitoring = Config.monitoring(this)
        val last = Config.lastStatus(this)

        b.status.text = buildString {
            append("Status: ")
            append(if (monitoring) "MONITORANDO" else "parado")
            append("\nAcessibilidade: ")
            append(if (isAccessibilityEnabled()) "ativa" else "DESATIVADA")
            append("\nCategorias: ")
            append(if (cats.isEmpty()) "(todas — menor preço)" else cats.joinToString(", "))
            append("\nAlvo: R$ ${"%.2f".format(target)}")
            append("\nQueda do pico: ${"%.1f".format(drop)}%")
            if (last.isNotBlank()) {
                append("\n\nÚltima leitura:\n")
                append(last)
            }
        }
    }
}
