package eu.darken.sdmse.corpsefinder.core.filter

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.listFiles
import eu.darken.sdmse.common.files.core.local.LocalGateway
import eu.darken.sdmse.common.files.core.walk
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Provider


@Reusable
class AppSourceCorpseFilter @Inject constructor(
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
    private val corpseFinderSettings: CorpseFinderSettings,
) : CorpseFilter(TAG, DEFAULT_PROGRESS) {

    override suspend fun doScan(): Collection<Corpse> {
        log(TAG) { "Scanning..." }

        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!gateway.hasRoot()) {
            log(TAG) { "LocalGateway has no root, skipping." }
            return emptySet()
        }

        return areaManager.currentAreas()
            .filter { it.type == DataArea.Type.APP_APP }
            .map { area ->
                updateProgressPrimary(
                    { c: Context -> c.getString(R.string.general_progress_processing_x, area.label) }.toCaString()
                )
                log(TAG) { "Reading $area" }
                updateProgressSecondary(R.string.general_progress_searching)
                val topLevelContents = area.path.listFiles(gatewaySwitch)

                log(TAG) { "Filtering $area" }
                updateProgressSecondary(R.string.general_progress_filtering)
                doFilter(topLevelContents)
            }
            .flatten()
    }

    private suspend fun doFilter(candidates: Collection<APath>): Collection<Corpse> {
        updateProgressCount(Progress.Count.Percent(0, candidates.size))

        val includeRiskKeeper: Boolean = corpseFinderSettings.includeRiskKeeper.value()
        val includeRiskCommon: Boolean = corpseFinderSettings.includeRiskCommon.value()

        return candidates
            .asFlow()
            .mapNotNull {
                log(TAG) { "Checking $it" }
                increaseProgress()
                fileForensics.findOwners(it)
            }
            .filter { ownerInfo ->
                (ownerInfo.areaInfo.type == DataArea.Type.APP_APP).also {
                    if (!it) log(TAG, WARN) { "Wrong area: $ownerInfo" }
                }
            }
            .filter { it.isCorpse }
            .filter { !it.isKeeper || includeRiskKeeper }
            .filter { !it.isCommon || includeRiskCommon }
            .map { ownerInfo ->
                val content = ownerInfo.item.walk(gatewaySwitch).toSet()
                Corpse(
                    filterType = this::class,
                    ownerInfo = ownerInfo,
                    content = content,
                    isWriteProtected = false,
                    riskLevel = when {
                        ownerInfo.isKeeper -> RiskLevel.USER_GENERATED
                        ownerInfo.isCommon -> RiskLevel.COMMON
                        else -> RiskLevel.NORMAL
                    }
                ).also { log(TAG, INFO) { "Found Corpse: $it" } }
            }
            .toList()
    }

    @Reusable
    class Factory @Inject constructor(
        private val settings: CorpseFinderSettings,
        private val filterProvider: Provider<AppSourceCorpseFilter>
    ) : CorpseFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterAppSourceEnabled.value()
        override suspend fun create(): CorpseFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): CorpseFilter.Factory
    }

    companion object {
        val DEFAULT_PROGRESS = Progress.Data(
            primary = R.string.corpsefinder_filter_appsource_label.toCaString(),
            secondary = R.string.general_progress_loading.toCaString(),
            count = Progress.Count.Indeterminate()
        )
        val TAG: String = logTag("CorpseFinder", "Filter", "App", "Source")
    }
}