package eu.darken.sdmse.setup.storage

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageSetupModule @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val storageManager2: StorageManager2,
    private val dataAreaManager: DataAreaManager,
    private val deviceDetective: DeviceDetective,
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)
    override val state: Flow<SetupModule.State> = refreshTrigger
        .mapLatest {
            val requiredPermission = getRequiredPermission()
            val missingPermission = requiredPermission.filter {
                val isGranted = it.isGranted(context)
                log(TAG) { "${it.permissionId} isGranted=$isGranted" }
                !isGranted
            }.toSet()

            val affectedPaths = storageManager2.storageVolumes
                .filter { it.directory != null }
                .map { volume ->
                    Result.PathAccess(
                        label = when {
                            volume.isPrimary || volume.isEmulated -> R.string.data_area_public_storage_label.toCaString()
                            else -> R.string.data_area_sdcard_label.toCaString()
                        },
                        localPath = LocalPath.build(volume.directory!!),
                        hasAccess = requiredPermission.all { it.isGranted(context) },
                    )
                }

            @Suppress("USELESS_CAST")
            Result(
                missingPermission = missingPermission,
                paths = affectedPaths,
            ) as SetupModule.State
        }
        .onStart { emit(Loading()) }
        .replayingShare(appScope)

    private fun getRequiredPermission(): Set<Permission> = when {
        deviceDetective.getROMType() == RomType.ANDROID_TV -> when {
            hasApiLevel(33) -> setOf(
                @Suppress("NewApi")
                Permission.MANAGE_EXTERNAL_STORAGE,
            )

            else -> setOf(
                Permission.WRITE_EXTERNAL_STORAGE,
                Permission.READ_EXTERNAL_STORAGE,
            )
        }

        else -> when {
            hasApiLevel(30) -> setOf(
                @Suppress("NewApi")
                Permission.MANAGE_EXTERNAL_STORAGE,
            )
            else -> setOf(
                Permission.WRITE_EXTERNAL_STORAGE,
                Permission.READ_EXTERNAL_STORAGE,
            )
        }
    }

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = rngString
    }

    suspend fun onPermissionChanged(permission: Permission, granted: Boolean) {
        log(TAG) { "onPermissionChanged($permission, $granted)" }
        dataAreaManager.reload()
    }

    data class Loading(
        override val startAt: Instant = Instant.now(),
    ) : SetupModule.State.Loading {
        override val type: SetupModule.Type = SetupModule.Type.STORAGE
    }

    data class Result(
        val paths: List<PathAccess>,
        val missingPermission: Set<Permission>,
    ) : SetupModule.State.Current {

        override val type: SetupModule.Type = SetupModule.Type.STORAGE

        override val isComplete: Boolean = missingPermission.isEmpty() && paths.all { it.hasAccess }

        data class PathAccess(
            val label: CaString,
            val localPath: LocalPath,
            val hasAccess: Boolean,
        )
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: StorageSetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "Storage", "Module")
    }
}