package ir.carepack.settings.deletion

import android.content.Context
import java.io.File
import java.io.IOException

class AndroidTemporaryDataCleaner(
    context: Context,
) : TemporaryDataCleaner {

    private val applicationContext =
        context.applicationContext

    override suspend fun clearAllTemporaryData() {
        val wholeDirectoryContents =
            listOfNotNull(
                applicationContext.cacheDir,
                applicationContext.codeCacheDir,
                applicationContext.externalCacheDir,
            )

        wholeDirectoryContents.forEach { directory ->
            deleteDirectoryChildrenOrThrow(
                directory = directory,
            )
        }

        val dedicatedTemporaryDirectories =
            listOf(
                File(
                    applicationContext.filesDir,
                    INTERNAL_TEMPORARY_DIRECTORY,
                ),
                File(
                    applicationContext.filesDir,
                    REPORT_PREVIEW_DIRECTORY,
                ),
                File(
                    applicationContext.noBackupFilesDir,
                    INTERNAL_TEMPORARY_DIRECTORY,
                ),
                File(
                    applicationContext.noBackupFilesDir,
                    REPORT_PREVIEW_DIRECTORY,
                ),
            )

        dedicatedTemporaryDirectories.forEach { directory ->
            deleteDirectoryRecursivelyOrThrow(
                directory = directory,
            )
        }
    }

    private fun deleteDirectoryChildrenOrThrow(
        directory: File,
    ) {
        if (!directory.exists()) {
            return
        }

        if (!directory.isDirectory) {
            throw IOException(
                "Temporary cleanup target is not a directory: ${directory.absolutePath}",
            )
        }

        val children =
            directory.listFiles()
                ?: throw IOException(
                    "Temporary cleanup directory could not be read: ${directory.absolutePath}",
                )

        children.forEach { child ->
            deleteDirectoryRecursivelyOrThrow(
                directory = child,
            )
        }
    }

    private fun deleteDirectoryRecursivelyOrThrow(
        directory: File,
    ) {
        if (!directory.exists()) {
            return
        }

        val deleted =
            directory.deleteRecursively()

        if (!deleted && directory.exists()) {
            throw IOException(
                "Temporary CarePack data could not be removed: ${directory.absolutePath}",
            )
        }
    }

    private companion object {

        const val INTERNAL_TEMPORARY_DIRECTORY =
            "carepack-temporary"

        const val REPORT_PREVIEW_DIRECTORY =
            "carepack-report-previews"
    }
}
