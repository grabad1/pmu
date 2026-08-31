package rs.etf.focusguard.data

import android.content.Context
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rs.etf.focusguard.LOG_TAG
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import javax.inject.Inject
import javax.inject.Singleton

/** Where a save ended up, so the screen can show the path rather than a bare "done". */
sealed interface ExportResult {
    data class Saved(val path: String) : ExportResult
    data object Failed : ExportResult
}

/**
 * Writes a report to a text file in the app's own folder on external storage.
 *
 * The destination is `getExternalFilesDir(DIRECTORY_DOCUMENTS)` —
 * `/sdcard/Android/data/rs.etf.focusguard/files/Documents` — chosen because it needs no
 * permission on any supported version and can be read straight off the device with `adb pull`
 * or Android Studio's Device Explorer, without `run-as` and without a debuggable build.
 * Internal storage (`openFileOutput`) is only reachable through `run-as` or root, which makes
 * it awkward to show anyone.
 *
 * Note that *no* app-specific folder is browsable in the system Files app: Android 11 hid
 * `Android/data` from it. Somewhere the user can open on the phone itself would have to be
 * public Downloads via MediaStore, which is a different job.
 *
 * [validate] deliberately checks the *directory*, not the file. Asking `canWrite()` of a file
 * that does not exist yet is always false, so a guard written that way blocks the very first
 * save and every save after it.
 */
@Singleton
class SummaryFileExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** True when the folder exists, or could be created, and can be written to. */
    fun validate(): Boolean {
        val directory = documentsDirectory() ?: return false
        val mounted = Environment.getExternalStorageState(directory) == Environment.MEDIA_MOUNTED
        return mounted && (directory.exists() || directory.mkdirs()) && directory.canWrite()
    }

    /**
     * Writes [content] to [fileName], replacing any file of that name.
     *
     * Never throws: a failed save is reported back so the UI can say so, because the user
     * asked for a file and silence would look like success.
     */
    suspend fun save(content: String, fileName: String): ExportResult =
        withContext(Dispatchers.IO) {
            if (!validate()) {
                Log.w(LOG_TAG, "Export refused: ${documentsDirectory()} is not writable")
                return@withContext ExportResult.Failed
            }

            val file = File(documentsDirectory(), fileName)
            try {
                PrintWriter(OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8)).use {
                    it.print(content)
                    it.flush()
                }
                Log.i(LOG_TAG, "Export saved to ${file.absolutePath} (${file.length()} bytes)")
                ExportResult.Saved(file.absolutePath)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Export to ${file.absolutePath} failed", e)
                ExportResult.Failed
            }
        }

    private fun documentsDirectory(): File? =
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
}
