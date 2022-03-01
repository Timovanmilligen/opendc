package org.opendc.workflow.service.scheduler.task

import org.opendc.workflow.service.internal.TaskState
import org.opendc.workflow.service.internal.WorkflowServiceImpl

/**
 *
 * @author Fabian Mastenbroek (f.s.mastenbroek@student.tudelft.nl)
 */
public data class CompositeTaskEligibilityPolicy(val first: TaskEligibilityPolicy, val second: TaskEligibilityPolicy) : TaskEligibilityPolicy {
    override fun invoke(scheduler: WorkflowServiceImpl): TaskEligibilityPolicy.Logic = object : TaskEligibilityPolicy.Logic {
        private val firstLogic = first(scheduler)
        private val secondLogic = second(scheduler)

        override fun invoke(task: TaskState): TaskEligibilityPolicy.Advice {
            return when (val advice = firstLogic(task)) {
                TaskEligibilityPolicy.Advice.ADMIT -> when (val secondAdvice = secondLogic(task)) {
                    TaskEligibilityPolicy.Advice.STOP, TaskEligibilityPolicy.Advice.DENY -> TaskEligibilityPolicy.Advice.DENY
                    else -> secondAdvice
                }
                TaskEligibilityPolicy.Advice.ADMIT_LAST -> {
                    when (secondLogic(task)) {
                        TaskEligibilityPolicy.Advice.STOP, TaskEligibilityPolicy.Advice.DENY -> TaskEligibilityPolicy.Advice.STOP
                        else -> TaskEligibilityPolicy.Advice.ADMIT_LAST
                    }
                }
                else -> advice
            }
        }
    }

    override fun toString(): String = "Compose($first, $second)"
}
