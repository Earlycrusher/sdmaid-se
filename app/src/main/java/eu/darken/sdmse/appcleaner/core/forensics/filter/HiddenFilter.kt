package eu.darken.sdmse.appcleaner.core.forensics.filter

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.forensics.BaseExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.sieves.JsonAppSieve
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.lowercase
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.storage.StorageEnvironment
import javax.inject.Inject
import javax.inject.Provider


@Reusable
class HiddenFilter @Inject constructor(
    private val jsonBasedSieveFactory: JsonAppSieve.Factory,
    environment: StorageEnvironment,
    private val gatewaySwitch: GatewaySwitch,
) : BaseExpendablesFilter() {

    private val cacheFolderPrefixes = environment.ourCacheDirs.map { it.name }

    private lateinit var sieve: JsonAppSieve

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
        sieve = jsonBasedSieveFactory.create("expendables/db_hidden_caches_files.json")
    }

    override suspend fun match(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        pfpSegs: Segments
    ): ExpendablesFilter.Match? {
        // Default case, we don't handle that.
        // package/cache/file
        if (pfpSegs.size >= 2 && pkgId.name == pfpSegs[0] && cacheFolderPrefixes.contains(pfpSegs[1])) {
            // Case matching is important here as all paths that differ in casing are hidden caches (e.g. not system made)
            return null
        }

        val lcsegments = pfpSegs.lowercase()

        if (lcsegments.isNotEmpty() && IGNORED_FILES.contains(lcsegments[lcsegments.size - 1])) {
            return null
        }

        // topdir/cache.dat
        if (lcsegments.size == 2 && HIDDEN_CACHE_FILES.contains(lcsegments[1])) {
            return target.toDeletionMatch()
        }

        // topdir/files/cache.dat
        if (lcsegments.size == 3 && HIDDEN_CACHE_FILES.contains(lcsegments[2])) {
            return target.toDeletionMatch()
        }

        //    0      1     2
        // topdir/.cache/file
        if (lcsegments.size >= 3 && HIDDEN_CACHE_FOLDERS.contains(lcsegments[1])) {
            return target.toDeletionMatch()
        }
        if (isException(pfpSegs)) return null

        //    0      1     2     3
        // topdir/files/.cache/file
        if (lcsegments.size >= 4
            && "files" == lcsegments[1]
            && (HIDDEN_CACHE_FOLDERS.contains(lcsegments[2]) || "cache" == lcsegments[2])
        ) {
            // cache hidden in files
            return target.toDeletionMatch()
        }

        //    -1     0      1     2     3
        // sdcard/Huawei/Themes/.cache/file
        if (lcsegments.size >= 4
            && areaType == DataArea.Type.SDCARD
            && HIDDEN_CACHE_FOLDERS.contains(lcsegments[2])
        ) {
            // cache hidden in files
            return target.toDeletionMatch()
        }

        return if (pfpSegs.isNotEmpty() && sieve.matches(pkgId, areaType, pfpSegs)) {
            target.toDeletionMatch()
        } else {
            null
        }
    }

    private fun isException(dirs: Segments): Boolean {
        return dirs.size >= 4 && dirs[2] == "Cache" && dirs[3].contains(".unity3d&")
    }

    override suspend fun process(
        targets: Collection<ExpendablesFilter.Match>,
        allMatches: Collection<ExpendablesFilter.Match>
    ): ExpendablesFilter.ProcessResult {
        return deleteAll(
            targets.map { it as ExpendablesFilter.Match.Deletion },
            gatewaySwitch,
            allMatches
        )
    }

    @Reusable
    class Factory @Inject constructor(
        private val settings: AppCleanerSettings,
        private val filterProvider: Provider<HiddenFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterHiddenCachesEnabled.value()
        override suspend fun create(): ExpendablesFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): ExpendablesFilter.Factory
    }

    companion object {
        private val HIDDEN_CACHE_FOLDERS: Collection<String> = listOf(
            "tmp", ".tmp",
            "tmpdata", "tmp-data", "tmp_data",
            ".tmpdata", ".tmp-data", ".tmp_data",
            ".temp", "temp",
            "tempdata", "temp-data", "temp_data",
            ".tempdata", ".temp-data", ".temp_data",
            ".cache", "cache", "_cache", "-cache",
            ".caches", "caches", "_caches", "-caches",
            "imagecache", "image-cache", "image_cache",
            ".imagecache", ".image-cache", ".image_cache",
            "imagecaches", "image-caches", "image_caches",
            ".imagecaches", ".image-caches", ".image_caches",
            "videocache", "video-cache", "video_cache",
            ".videocache", ".video-cache", ".video_cache",
            "videocaches", "video-caches", "video_caches",
            ".videocaches", ".video-caches", ".video_caches",
            "mediacache", "media-cache", "media_cache",
            ".mediacache", ".media-cache", ".media-cache",
            "mediacaches", "media-caches", "media_caches",
            ".mediacaches", ".media-caches", ".media_caches",
            "diskcache", "disk-cache", "disk_cache",
            ".diskcache", ".disk-cache", ".disk_cache",
            "diskcaches", "disk-caches", "disk_caches",
            ".diskcaches", ".disk-caches", ".disk_caches",
            "filescache",
            "AVFSCache"
        ).map { it.lowercase() }

        private val HIDDEN_CACHE_FILES: Collection<String> = listOf(
            "cache.dat",
            "tmp.dat",
            "temp.dat",
            ".temp.jpg"
        ).map { it.lowercase() }

        private val IGNORED_FILES: Collection<String> = listOf(
            ".nomedia"
        ).map { it.lowercase() }

        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "HiddenCaches")
    }
}