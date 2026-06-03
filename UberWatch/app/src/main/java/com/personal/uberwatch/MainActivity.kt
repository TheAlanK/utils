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

        b.btnSave.setOnClickListener {
            val target = b.inputTarget.text.toString().toFloatOrNull() ?: 0f
            val drop = b.inputDropPct.text.toString().toFloatOrNull() ?: 0f
            Config.save(this, target, drop)
            Config.reset(this)
            Toast.makeText(this, "Config salva. Histórico resetado.", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }

        b.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        b.btnOpenUber.setOnClickListener {
            val launch = packageManager.getLaunchIntentForPackage("com.ubercab")
            if (launch != null) {
                startActivity(launch)
            } else {
                Toast.makeText(this, "App da Uber não encontrado.", Toast.LENGTH_SHORT).show()
            }
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

    private fun refreshStatus() {
        val target = Config.targetPrice(this)
        val drop = Config.dropPct(this)
        val peak = Config.peak(this)
        b.status.text = buildString {
            append("Status:\n")
            append("Alvo: R$ ${"%.2f".format(target)}\n")
            append("Queda do pico: ${"%.1f".format(drop)}%\n")
            append("Pico observado: R$ ${"%.2f".format(peak)}")
        }
    }
}
