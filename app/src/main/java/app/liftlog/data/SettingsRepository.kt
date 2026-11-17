package app.liftlog.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.liftlog.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AutoBackupStatus(
    val folderUri: String? = null,
    val lastSuccessAt: Long? = null,
    val lastFailed: Boolean = false,
) {
    val enabled: Boolean get() = folderUri != null
}

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

    /** Where automatic backups go, how the last one went, and when. Folder null means off. */
    val autoBackup: Flow<AutoBackupStatus> = context.dataStore.data.map { prefs ->
        AutoBackupStatus(
            folderUri = prefs[AUTO_BACKUP_FOLDER],
            lastSuccessAt = prefs[AUTO_BACKUP_LAST_SUCCESS],
            lastFailed = prefs[AUTO_BACKUP_LAST_FAILED] == 1,
        )
    }

    suspend fun setAutoBackupFolder(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(AUTO_BACKUP_FOLDER) else prefs[AUTO_BACKUP_FOLDER] = uri
            prefs.remove(AUTO_BACKUP_LAST_FAILED)
        }
    }

    suspend fun recordAutoBackup(succeeded: Boolean, at: Long) {
        context.dataStore.edit { prefs ->
            if (succeeded) prefs[AUTO_BACKUP_LAST_SUCCESS] = at
            prefs[AUTO_BACKUP_LAST_FAILED] = if (succeeded) 0 else 1
        }
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
        private val AUTO_BACKUP_FOLDER = stringPreferencesKey("auto_backup_folder")
        private val AUTO_BACKUP_LAST_SUCCESS = longPreferencesKey("auto_backup_last_success")
        private val AUTO_BACKUP_LAST_FAILED = intPreferencesKey("auto_backup_last_failed")
    }
}
