package eu.darken.sdmse.systemcleaner.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.PreferenceScreenData
import eu.darken.sdmse.common.datastore.PreferenceStoreMapper
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SystemCleanerSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_systemcleaner")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val filterLogFilesEnabled = dataStore.createValue("filter.logfiles.enabled", true)
    val filterAdvertisementsEnabled = dataStore.createValue("filter.advertisements.enabled", true)
    val filterEmptyDirectoriesEnabled = dataStore.createValue("filter.emptydirectories.enabled", true)
    val filterSuperfluosApksEnabled = dataStore.createValue("filter.superfluosapks.enabled", false)
    val filterLostDirEnabled = dataStore.createValue("filter.lostdir.enabled", true)
    val filterLinuxFilesEnabled = dataStore.createValue("filter.linuxfiles.enabled", true)
    val filterMacFilesEnabled = dataStore.createValue("filter.macfiles.enabled", true)
    val filterThumbnailsEnabled = dataStore.createValue("filter.thumbnails.enabled", false)
    val filterTempFilesEnabled = dataStore.createValue("filter.tempfiles.enabled", true)
    val filterAnalyticsEnabled = dataStore.createValue("filter.analytics.enabled", true)
    val filterWindowsFilesEnabled = dataStore.createValue("filter.windowsfiles.enabled", true)

    val filterAnrEnabled = dataStore.createValue("filter.anr.enabled", true)
    val filterLocalTmpEnabled = dataStore.createValue("filter.localtmp.enabled", false)
    val filterDownloadCacheEnabled = dataStore.createValue("filter.downloadcache.enabled", true)
    val filterDataLoggerEnabled = dataStore.createValue("filter.datalogger.enabled", true)
    val filterLogDropboxEnabled = dataStore.createValue("filter.logdropbox.enabled", true)
    val filterRecentTasksEnabled = dataStore.createValue("filter.recenttasks.enabled", false)
    val filterTombstonesEnabled = dataStore.createValue("filter.tombstones.enabled", false)
    val filterUsageStatsEnabled = dataStore.createValue("filter.usagestats.enabled", false)

    val filterScreenshotsEnabled = dataStore.createValue("filter.screenshots.enabled", false)
    val filterScreenshotsAge = dataStore.createValue("filter.screenshots.age", SCREENSHOTS_AGE_DEFAULT, moshi)

    val filterTrashedEnabled = dataStore.createValue("filter.trashed.enabled", false)
    val filterPackageCacheEnabled = dataStore.createValue("filter.packagecache.enabled", false)

    val enabledCustomFilter = dataStore.createValue(
        "filter.custom.enabled",
        emptySet<FilterIdentifier>(),
        moshi
    )

    override val mapper = PreferenceStoreMapper(
        filterLogFilesEnabled,
        filterAdvertisementsEnabled,
        filterEmptyDirectoriesEnabled,
        filterSuperfluosApksEnabled,
        filterScreenshotsEnabled,
        filterScreenshotsAge,
        filterTrashedEnabled,
        filterLostDirEnabled,
        filterLinuxFilesEnabled,
        filterMacFilesEnabled,
        filterThumbnailsEnabled,
        filterTempFilesEnabled,
        filterAnalyticsEnabled,
        filterWindowsFilesEnabled,
        filterAnrEnabled,
        filterLocalTmpEnabled,
        filterDownloadCacheEnabled,
        filterDataLoggerEnabled,
        filterLogDropboxEnabled,
        filterRecentTasksEnabled,
        filterTombstonesEnabled,
        filterUsageStatsEnabled,
        filterPackageCacheEnabled,
    )

    companion object {
        val SCREENSHOTS_AGE_DEFAULT: Duration = Duration.ofDays(14)
        internal val TAG = logTag("SystemCleaner", "Settings")
    }
}