package app.liftlog

import android.app.Application
import android.content.Context
import app.liftlog.data.BackupRepository
import app.liftlog.data.ExerciseSeed
import app.liftlog.data.SettingsRepository
import app.liftlog.data.db.AppDatabase
import app.liftlog.timer.RestTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-wired dependencies shared across the app. */
class AppContainer(private val context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.create(context) }
    val exerciseDao get() = database.exerciseDao()
    val workoutDao get() = database.workoutDao()
    val programDao get() = database.programDao()
    val settings = SettingsRepository(context)
    val restTimer = RestTimer(context)
    val backup by lazy { BackupRepository(context, database.backupDao(), exerciseDao) }

    /** Loads the bundled exercise library on first launch and whenever it has grown. */
    fun seedExercisesIfNeeded() {
        applicationScope.launch(Dispatchers.IO) {
            if (settings.seedVersion() >= ExerciseSeed.VERSION) return@launch
            val text = context.assets.open(ExerciseSeed.ASSET_NAME).bufferedReader().use { it.readText() }
            exerciseDao.insertIgnoringExisting(ExerciseSeed.parse(text))
            settings.setSeedVersion(ExerciseSeed.VERSION)
        }
    }
}

class LiftLogApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.seedExercisesIfNeeded()
    }
}
