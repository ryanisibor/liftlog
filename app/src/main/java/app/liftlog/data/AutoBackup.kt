package app.liftlog.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/**
 * Keeps a folder the user chose up to date with their data, without being asked:
 * a dated backup file (one per day, the newest [KEEP_BACKUPS] kept) and a spreadsheet of
 * every set. Runs after workouts are finished or edited.
 */
class AutoBackup(
    private val context: Context,
    private val backup: BackupRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    /** Remembers the folder and keeps access to it across restarts, then backs up straight away. */
    suspend fun enable(folder: Uri) {
        context.contentResolver.takePersistableUriPermission(
            folder,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        settings.setAutoBackupFolder(folder.toString())
        runNow()
    }

    suspend fun disable() {
        val current = settings.autoBackup.first().folderUri
        settings.setAutoBackupFolder(null)
        if (current != null) {
            // Giving up access can fail if it was already revoked; either way it is gone.
            try {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(current),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
            }
        }
    }

    /** Fire-and-forget: called after data changes. Does nothing if automatic backup is off. */
    fun request() {
        scope.launch { runNow() }
    }

    /** Backs up now. Returns true on success, false if it is off or the folder could not be written. */
    suspend fun runNow(): Boolean = mutex.withLock {
        val folderUri = settings.autoBackup.first().folderUri ?: return false
        val succeeded = try {
            withContext(Dispatchers.IO) { write(Uri.parse(folderUri)) }
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            // The folder was deleted or access was revoked; Settings asks the user to choose again.
            false
        } catch (_: IllegalArgumentException) {
            false
        }
        settings.recordAutoBackup(succeeded, System.currentTimeMillis())
        succeeded
    }

    private suspend fun write(folderUri: Uri) {
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?.takeIf { it.isDirectory && it.canWrite() }
            ?: throw IOException("Backup folder is not available")

        replaceFile(folder, "$BACKUP_PREFIX${LocalDate.now()}.json", "application/json", backup.backupJson())
        replaceFile(folder, SPREADSHEET_NAME, "text/csv", backup.setsCsv())

        // Keep the newest few dated backups; the names sort by date.
        folder.listFiles()
            .filter { file -> file.name?.let { it.startsWith(BACKUP_PREFIX) && it.endsWith(".json") } == true }
            .sortedByDescending { it.name }
            .drop(KEEP_BACKUPS)
            .forEach { it.delete() }
    }

    /** Writes [text] to [name] in [folder], replacing a file of that name rather than adding "name (1)". */
    private fun replaceFile(folder: DocumentFile, name: String, mimeType: String, text: String) {
        folder.findFile(name)?.delete()
        val file = folder.createFile(mimeType, name) ?: throw IOException("Could not create $name")
        val stream = context.contentResolver.openOutputStream(file.uri, "wt") ?: throw IOException("Could not open $name")
        stream.bufferedWriter().use { it.write(text) }
    }

    companion object {
        const val BACKUP_PREFIX = "liftlog-backup-"
        const val SPREADSHEET_NAME = "liftlog-sets.csv"
        const val KEEP_BACKUPS = 7
    }
}
