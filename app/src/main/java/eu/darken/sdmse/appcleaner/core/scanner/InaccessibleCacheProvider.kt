package eu.darken.sdmse.appcleaner.core.scanner

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.toLocalPath
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.storage.StorageStatsManager2
import javax.inject.Inject


@Reusable
class InaccessibleCacheProvider @Inject constructor(
    private val storageStatsManager: StorageStatsManager2,
) {

    suspend fun determineCache(pkg: Installed): InaccessibleCache? {
        val applicationInfo = pkg.applicationInfo

        if (applicationInfo == null) {
            log(TAG, WARN) { "Application info was NULL for ${pkg.id}" }
            return null
        }

        val storageStats = try {
            storageStatsManager.queryStatsForPkg(
                StorageId(internalId = null, externalId = applicationInfo.storageUuid),
                pkg,
            )
        } catch (e: SecurityException) {
            log(TAG, WARN) { "Don't have permission to query app size for ${pkg.id}: $e" }
            return null
        } catch (e: Exception) {
            log(TAG, ERROR) { "Unexpected error when querying app size for ${pkg.id}: ${e.asLog()}" }
            return null
        }

        return InaccessibleCache(
            pkg.installId,
            isSystemApp = pkg.isSystemApp,
            itemCount = 2,
            totalSize = storageStats.cacheBytes,
            publicSize = if (hasApiLevel(31)) {
                @Suppress("NewApi")
                storageStats.externalCacheBytes
            } else null,
            theoreticalPaths = pkg.genTheoreticalPaths(),
        )
    }

    private fun Installed.genTheoreticalPaths() = mutableSetOf<APath>().apply {
        add(File("/storage/emulated/${userHandle.handleId}/Android/data/${id.name}/cache").toLocalPath())
        add(File("/data/user/${userHandle.handleId}/${id.name}/cache").toLocalPath())
    }

    companion object {
        val TAG = logTag("AppCleaner", "Scanner", "Inaccessible")
    }
}