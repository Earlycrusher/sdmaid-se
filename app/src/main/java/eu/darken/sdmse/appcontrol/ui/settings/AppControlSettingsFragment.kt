package eu.darken.sdmse.appcontrol.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppControlSettings
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.preferences.BadgedCheckboxPreference
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.showFixSetupHint
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class AppControlSettingsFragment : PreferenceFragment2() {

    private val vm: AppControlSettingsViewModel by viewModels()

    @Inject lateinit var acSettings: AppControlSettings

    override val settings: AppControlSettings by lazy { acSettings }
    override val preferenceFile: Int = R.xml.preferences_appcontrol

    private val determineSizes: BadgedCheckboxPreference
        get() = findPreference(settings.moduleSizingEnabled.keyName)!!

    private val determineRunning: BadgedCheckboxPreference
        get() = findPreference(settings.moduleActivityEnabled.keyName)!!

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()

        determineSizes.badgedAction = {
            setOf(SetupModule.Type.USAGE_STATS).showFixSetupHint(this)
        }
        determineRunning.badgedAction = {
            setOf(SetupModule.Type.USAGE_STATS).showFixSetupHint(this)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.state.observe2(this) { state ->
            determineSizes.isRestricted = !state.state.canInfoSize
            determineRunning.isRestricted = !state.state.canInfoActive
        }
        super.onViewCreated(view, savedInstanceState)
    }

}