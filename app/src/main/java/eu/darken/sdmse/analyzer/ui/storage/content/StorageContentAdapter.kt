package eu.darken.sdmse.analyzer.ui.storage.content

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.analyzer.ui.storage.content.types.AppContentVH
import eu.darken.sdmse.analyzer.ui.storage.content.types.MediaContentVH
import eu.darken.sdmse.analyzer.ui.storage.content.types.SystemContentVH
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.TypedVHCreatorMod
import javax.inject.Inject


class StorageContentAdapter @Inject constructor() :
    ModularAdapter<StorageContentAdapter.BaseVH<StorageContentAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<StorageContentAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        modules.add(DataBinderMod(data))
        modules.add(TypedVHCreatorMod({ data[it] is AppContentVH.Item }) { AppContentVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is MediaContentVH.Item }) { MediaContentVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is SystemContentVH.Item }) { SystemContentVH(it) })
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup
    ) : VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem {
        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new ->
                if (new::class.isInstance(old)) new else null
            }
    }
}