package pl.openpayments.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ustawienia aplikacji (SharedPreferences + StateFlow dla UI). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("openpayments", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(sp.getString("theme", "system")!!)
    val theme: StateFlow<String> = _theme
    fun setTheme(v: String) { sp.edit { putString("theme", v) }; _theme.value = v }

    private val _dynamic = MutableStateFlow(sp.getBoolean("dynamic_color", true))
    val dynamicColor: StateFlow<Boolean> = _dynamic
    fun setDynamicColor(v: Boolean) { sp.edit { putBoolean("dynamic_color", v) }; _dynamic.value = v }

    var budgetAlerts: Boolean
        get() = sp.getBoolean("budget_alerts", true)
        set(v) = sp.edit { putBoolean("budget_alerts", v) }

    var subscriptionReminders: Boolean
        get() = sp.getBoolean("sub_reminders", true)
        set(v) = sp.edit { putBoolean("sub_reminders", v) }

    var learnRules: Boolean
        get() = sp.getBoolean("learn_rules", true)
        set(v) = sp.edit { putBoolean("learn_rules", v) }

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit { putBoolean("onboarded", v) }

    var autoUpdate: Boolean
        get() = sp.getBoolean("auto_update", true)
        set(v) = sp.edit { putBoolean("auto_update", v) }

    var appLock: Boolean
        get() = sp.getBoolean("app_lock", false)
        set(v) = sp.edit { putBoolean("app_lock", v) }

    /** Ręcznie poprawione role kolumn importu CSV, zapamiętane dla danego układu pliku. */
    fun csvMapping(signature: String): List<String>? =
        sp.getString("csv_map_${signature.hashCode()}", null)?.takeIf { it.substringBefore('\n') == signature }
            ?.substringAfter('\n')?.split(',')

    fun setCsvMapping(signature: String, roles: List<String>?) = sp.edit {
        if (roles == null) remove("csv_map_${signature.hashCode()}")
        else putString("csv_map_${signature.hashCode()}", signature + "\n" + roles.joinToString(","))
    }

    fun alertLevel(key: String) = sp.getInt(key, 0)
    fun setAlertLevel(key: String, v: Int) = sp.edit { putInt(key, v) }
}
