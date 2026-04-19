package ephyra.presentation.core.preference

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceDataStore

/**
 * Bridges an Android [SharedPreferences] instance to the AndroidX
 * [PreferenceDataStore] API used by [ConfigurableSource] extension preference screens.
 *
 * Lives in `presentation-core` because it is a pure UI-adapter with no data-layer
 * dependencies — it wraps a [SharedPreferences] that is handed in by the caller,
 * and exposes it to the AndroidX Preference library.
 */
class SharedPreferencesDataStore(private val prefs: SharedPreferences) : PreferenceDataStore() {

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (key == null) return defValue
        return prefs.getBoolean(key, defValue)
    }

    override fun putBoolean(key: String?, value: Boolean) {
        if (key == null) return
        prefs.edit { putBoolean(key, value) }
    }

    override fun getInt(key: String?, defValue: Int): Int {
        if (key == null) return defValue
        return prefs.getInt(key, defValue)
    }

    override fun putInt(key: String?, value: Int) {
        if (key == null) return
        prefs.edit { putInt(key, value) }
    }

    override fun getLong(key: String?, defValue: Long): Long {
        if (key == null) return defValue
        return prefs.getLong(key, defValue)
    }

    override fun putLong(key: String?, value: Long) {
        if (key == null) return
        prefs.edit { putLong(key, value) }
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        if (key == null) return defValue
        return prefs.getFloat(key, defValue)
    }

    override fun putFloat(key: String?, value: Float) {
        if (key == null) return
        prefs.edit { putFloat(key, value) }
    }

    override fun getString(key: String?, defValue: String?): String? {
        if (key == null) return defValue
        return prefs.getString(key, defValue)
    }

    override fun putString(key: String?, value: String?) {
        if (key == null) return
        prefs.edit { putString(key, value) }
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        if (key == null) return defValues
        // Return a defensive copy — SharedPreferences may return a mutable internally-backed set
        // that callers must not modify (see SharedPreferences.getStringSet() docs).
        return prefs.getStringSet(key, defValues)?.toMutableSet()
    }

    override fun putStringSet(key: String?, values: MutableSet<String>?) {
        if (key == null) return
        prefs.edit { putStringSet(key, values) }
    }
}
