package com.personal.uberwatch

import android.content.Context

object Config {
    private const val PREFS = "uberwatch_prefs"
    private const val KEY_TARGET = "target_price"
    private const val KEY_DROP_PCT = "drop_pct"
    private const val KEY_PEAK = "peak_price"
    private const val KEY_LAST_ALERT = "last_alert_price"

    fun targetPrice(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_TARGET, 0f)

    fun dropPct(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_DROP_PCT, 0f)

    fun peak(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_PEAK, 0f)

    fun lastAlert(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_LAST_ALERT, 0f)

    fun save(ctx: Context, target: Float, dropPct: Float) {
        prefs(ctx).edit()
            .putFloat(KEY_TARGET, target)
            .putFloat(KEY_DROP_PCT, dropPct)
            .apply()
    }

    fun setPeak(ctx: Context, v: Float) {
        prefs(ctx).edit().putFloat(KEY_PEAK, v).apply()
    }

    fun setLastAlert(ctx: Context, v: Float) {
        prefs(ctx).edit().putFloat(KEY_LAST_ALERT, v).apply()
    }

    fun reset(ctx: Context) {
        prefs(ctx).edit()
            .putFloat(KEY_PEAK, 0f)
            .putFloat(KEY_LAST_ALERT, 0f)
            .apply()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
