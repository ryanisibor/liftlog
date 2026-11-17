package app.liftlog.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.liftlog.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    val weightUnit: Flow<WeightUnit> = context.dataStore.data.map { prefs ->
        prefs[WEIGHT_UNIT]?.let { name -> WeightUnit.entries.find { it.name == name } } ?: WeightUnit.KG
    }

    val restSeconds: Flow<Int> = context.dataStore.data.map { it[REST_SECONDS] ?: DEFAULT_REST_SECONDS }

    suspend fun setWeightUnit(unit: WeightUnit) {
        context.dataStore.edit { it[WEIGHT_UNIT] = unit.name }
    }

    suspend fun setRestSeconds(seconds: Int) {
        context.dataStore.edit { it[REST_SECONDS] = seconds.coerceIn(MIN_REST_SECONDS, MAX_REST_SECONDS) }
    }

    suspend fun seedVersion(): Int = context.dataStore.data.first()[SEED_VERSION] ?: 0

    suspend fun setSeedVersion(version: Int) {
        context.dataStore.edit { it[SEED_VERSION] = version }
    }

    companion object {
        const val DEFAULT_REST_SECONDS = 120
        const val MIN_REST_SECONDS = 15
        const val MAX_REST_SECONDS = 600

        private val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        private val REST_SECONDS = intPreferencesKey("rest_seconds")
        private val SEED_VERSION = intPreferencesKey("seed_version")
    }
}
