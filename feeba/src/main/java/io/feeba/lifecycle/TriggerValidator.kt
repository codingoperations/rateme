package io.feeba.lifecycle

import android.app.Activity
import io.feeba.data.LocalStateHolder
import io.feeba.data.RuleSet
import io.feeba.data.RuleType
import io.feeba.data.SurveyPresentation
import io.feeba.data.TriggerCondition
import io.feeba.data.isEvent
import io.feeba.data.isPageTrigger

class TriggerValidator {


    fun onEvent(eventName: String, value: String? = null, localStateHolder: LocalStateHolder): ValidatorResult? {
        Logger.log(LogLevel.DEBUG, "TriggerValidator:: onEvent -> $eventName, value: $value")
        // check if we have a survey for this event
        val config = localStateHolder.readLocalConfig()
        // check if we have a survey for this event
        Logger.log(LogLevel.DEBUG, "TriggerValidator:: surveyPlans -> ${config.surveyPlans}")
        for (surveyPlan in config.surveyPlans) {
            for (ruleSet in surveyPlan.ruleSetList) {
                // if all conditions are met, return the survey
                var allConditionsMet = false
                for (triggerCondition: TriggerCondition in ruleSet.triggers) {
                    if (isEvent(triggerCondition) && triggerCondition.eventName == eventName) {
                        allConditionsMet = true
                    }
                }
                if (allConditionsMet) {
                    return ValidatorResult(surveyPlan.surveyPresentation, 0, ruleSet)
                }
            }
        }
        return null

    }

    fun pageOpened(pageName: String, localStateHolder: LocalStateHolder): ValidatorResult? {
        Logger.log(LogLevel.DEBUG, "TriggerValidator:: pageOpened -> $pageName")
        // check if we have a survey for this event
        val config = localStateHolder.readLocalConfig()
        // check if we have a survey for this event
        Logger.log(LogLevel.DEBUG, "TriggerValidator:: surveyPlans -> ${config.surveyPlans}")
        for (surveyPlan in config.surveyPlans) {
            for (ruleSet in surveyPlan.ruleSetList) {
                // if all conditions are met, return the survey
                var allConditionsMet = false
                if (isPageTrigger(ruleSet)) {
                    allConditionsMet = true
                }
                if (allConditionsMet) {
                    if (ruleSet.triggers.none { it.eventName == pageName }) {
                        // page name is not in the trigger conditions, we pass the condition block
                        continue
                    }
                    val surveyOpenDelaySec = try {
                        ruleSet.triggers.filter { it.type == RuleType.SESSION_DURATION }.getOrNull(0)?.value?.toLongOrNull() ?: 0
                    } catch (throwable: Throwable) {
                        Logger.log(LogLevel.ERROR, "Failed to parse page timing condition value: $throwable")
                        0
                    }
                    return ValidatorResult(surveyPlan.surveyPresentation, surveyOpenDelaySec * 1000, ruleSet)
                }
            }
        }
        return null
    }
}

data class ValidatorResult(
    val surveyPresentation: SurveyPresentation,
    val delay: Long,
    val ruleSet: RuleSet
)

fun validateEvent(triggerCondition: TriggerCondition) {
    when (triggerCondition.conditional) {
        "ex" -> {}
        "eq" -> {}
        "neq" -> {}
        "gt" -> {}
        "gte" -> {}
        "lt" -> {}
        "lte" -> {}
        else -> {
            Logger.log(LogLevel.WARN, "Unknown operator: ${triggerCondition.conditional}")
            null
        }
    }
}

fun onActivityPaused(activity: Activity) {
    Logger.log(LogLevel.DEBUG, "onActivityPaused: $activity")
    // TODO remove any callbacks that are waiting to run on this activity
}

fun onActivityResumed(activity: Activity) {
    Logger.log(LogLevel.DEBUG, "onActivityResumed: $activity")
    // TODO Check if there is any pending survey to show
}

fun onAppOpened(starterActivity: Activity) {
    Logger.log(LogLevel.DEBUG, "onAppOpened: $starterActivity")
    // TODO Check if there is any pending survey to show
}

private fun showSurveyDialog(activity: Activity) {
    // TODO show survey dialog
//        val fm: FragmentManager = activity.fragmentManager
//        SurveyFragment()
//            .apply {
//                arguments = Bundle().apply {
//                    putString(
//                        KEY_SURVEY_URL,
//                        "http://dev-dashboard.feeba.io/s/feeba/6504ee57ba0d101292e066a8"
//                    )
//                }
//            }
//            .show(
//                fm,
//                "SurveyFragment"
//            )
//        showSurveyFragment(activity.fragmentManager, "http://dev-dashboard.feeba.io/s/feeba/6504ee57ba0d101292e066a8")
}