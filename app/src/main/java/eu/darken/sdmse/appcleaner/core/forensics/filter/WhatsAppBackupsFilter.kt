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
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.startsWith
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider


@Reusable
class WhatsAppBackupsFilter @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
) : BaseExpendablesFilter() {

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
    }

    override suspend fun match(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        pfpSegs: Segments
    ): ExpendablesFilter.Match? {
        if (pfpSegs.isNotEmpty() && IGNORED_FILES.contains(pfpSegs[pfpSegs.size - 1])) return null

        if (!VALID_LOCS.contains(DataArea.Type.SDCARD)) return null
        if (!VALID_PKGS.contains(pkgId)) return null
        if (VALID_PREFIXES.none { pfpSegs.startsWith(it) }) return null
        if (FILE_REGEXES.none { it.matches(pfpSegs.last()) }) return null

        return if (target.modifiedAt == Instant.EPOCH) {
            null
        } else if (Duration.between(target.modifiedAt, Instant.now()) > Duration.ofDays(1)) {
            target.toDeletionMatch()
        } else {
            null
        }
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
        private val filterProvider: Provider<WhatsAppBackupsFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterWhatsAppBackupsEnabled.value()
        override suspend fun create(): ExpendablesFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): ExpendablesFilter.Factory
    }

    companion object {
        private val IGNORED_FILES: Collection<String> = listOf(
            ".nomedia"
        )
        private val VALID_LOCS = setOf(
            DataArea.Type.SDCARD,
            DataArea.Type.PUBLIC_MEDIA
        )
        private val VALID_PKGS = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
        ).map { it.toPkgId() }

        private val VALID_PREFIXES1 = setOf(
            "WhatsApp",
            "com.whatsapp",
            "com.whatsapp/WhatsApp",

            "WhatsApp Business",
            "com.whatsapp.w4b",
            "com.whatsapp.w4b/WhatsApp Business",
        )
        private val VALID_PREFIXES2 = setOf(
            "Databases",
            "Backups",
        )

        private val VALID_PREFIXES = VALID_PREFIXES1
            .map { pre1 -> VALID_PREFIXES2.map { pre2 -> "$pre1/$pre2" } }
            .flatten()
            .map { it.toSegs() }

        private val FILE_REGEXES by lazy {
            setOf(
                Regex("msgstore-.+?\\.1\\.db\\.crypt\\d+"),
                Regex("backup_settings-.+?\\.1\\.json\\.crypt\\d+"),
                Regex("chatsettingsbackup-.+?\\.1\\.db\\.crypt\\d+"),
                Regex("commerce_backup-.+?\\.1\\.db\\.crypt\\d+"),
                Regex("stickers-.+?\\.1\\.db\\.crypt\\d+"),
                Regex("wa-.+?\\.1\\.db\\.crypt\\d+"),
                Regex("wallpapers-.+?\\.1\\.backup\\.crypt\\d+"),
            )
        }

        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "WhatsApp", "Backups")
    }
}