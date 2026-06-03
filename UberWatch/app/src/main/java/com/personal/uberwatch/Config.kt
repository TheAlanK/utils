package com.personal.uberwatch

import android.content.Context

object Config {
    private const val PREFS = "uberwatch_prefs"
    private const val KEY_TARGET = "target_price"
    private const val KEY_DROP_PCT = "drop_pct"
    private const val KEY_CATEGORIES = "categories"
    private const val KEY_MONITORING = "monitoring"
    private const val KEY_LAST_STATUS = "last_status"
    private const val PREFIX_PEAK = "peak_"
    private const val PREFIX_LAST_ALERT = "lastalert_"

    fun targetPrice(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_TARGET, 0f)

    fun dropPct(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_DROP_PCT, 0f)

    // Texto cru das categorias (como o usuário digitou).
    fun categoriesRaw(ctx: Context): String =
        prefs(ctx).getString(KEY_CATEGORIES, "") ?: ""

    // Lista de categorias a monitorar. Vazio = monitorar o menor preço da tela.
    fun categories(ctx: Context): List<String> =
        categoriesRaw(ctx)
            .split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun monitoring(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_MONITORING, false)

    fun setMonitoring(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_MONITORING, v).apply()
    }

    // Último resumo de leitura (preços lidos por categoria) — mostrado na UI.
    fun lastStatus(ctx: Context): String =
        prefs(ctx).getString(KEY_LAST_STATUS, "") ?: ""

    fun setLastStatus(ctx: Context, v: String) {
        prefs(ctx).edit().putString(KEY_LAST_STATUS, v).apply()
    }

    // Pico e último alerta são por categoria.
    fun peak(ctx: Context, cat: String): Float =
        prefs(ctx).getFloat(PREFIX_PEAK + key(cat), 0f)

    fun setPeak(ctx: Context, cat: String, v: Float) {
        prefs(ctx).edit().putFloat(PREFIX_PEAK + key(cat), v).apply()
    }

    fun lastAlert(ctx: Context, cat: String): Float =
        prefs(ctx).getFloat(PREFIX_LAST_ALERT + key(cat), 0f)

    fun setLastAlert(ctx: Context, cat: String, v: Float) {
        prefs(ctx).edit().putFloat(PREFIX_LAST_ALERT + key(cat), v).apply()
    }

    fun save(ctx: Context, target: Float, dropPct: Float, categories: String) {
        prefs(ctx).edit()
            .putFloat(KEY_TARGET, target)
            .putFloat(KEY_DROP_PCT, dropPct)
            .putString(KEY_CATEGORIES, categories)
            .apply()
    }

    // Limpa pico/alerta de todas as categorias.
    fun reset(ctx: Context) {
        val p = prefs(ctx)
        val e = p.edit()
        for (k in p.all.keys) {
            if (k.startsWith(PREFIX_PEAK) || k.startsWith(PREFIX_LAST_ALERT)) e.remove(k)
        }
        e.remove(KEY_LAST_STATUS)
        e.apply()
    }

    private fun key(cat: String): String = cat.lowercase().trim()

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
