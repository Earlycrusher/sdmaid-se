package eu.darken.sdmse.automation.core.common.stepper

import eu.darken.sdmse.automation.core.specs.AutomationExplorer

data class StepContext(
    val hostContext: AutomationExplorer.Context,
    val tag: String,
    val stepAttempts: Int,
) : AutomationExplorer.Context by hostContext