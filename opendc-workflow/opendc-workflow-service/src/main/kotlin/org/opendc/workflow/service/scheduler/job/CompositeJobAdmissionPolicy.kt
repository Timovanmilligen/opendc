package org.opendc.workflow.service.scheduler.job

import org.opendc.workflow.service.internal.JobState
import org.opendc.workflow.service.internal.WorkflowServiceImpl

/**
 *
 * @author Fabian Mastenbroek (f.s.mastenbroek@student.tudelft.nl)
 */
public data class CompositeJobAdmissionPolicy(val first: JobAdmissionPolicy, val second: JobAdmissionPolicy) : JobAdmissionPolicy {
    override fun invoke(scheduler: WorkflowServiceImpl): JobAdmissionPolicy.Logic = object : JobAdmissionPolicy.Logic {
        private val firstLogic = first(scheduler)
        private val secondLogic = second(scheduler)

        override fun invoke(job: JobState): JobAdmissionPolicy.Advice {
            return when (val advice = firstLogic(job)) {
                JobAdmissionPolicy.Advice.ADMIT ->
                    when (val secondAdvice = secondLogic(job)) {
                        JobAdmissionPolicy.Advice.STOP, JobAdmissionPolicy.Advice.DENY -> JobAdmissionPolicy.Advice.DENY
                        else -> secondAdvice
                    }
                JobAdmissionPolicy.Advice.ADMIT_LAST -> {
                    when (secondLogic(job)) {
                        JobAdmissionPolicy.Advice.STOP, JobAdmissionPolicy.Advice.DENY -> JobAdmissionPolicy.Advice.STOP
                        else -> JobAdmissionPolicy.Advice.ADMIT_LAST
                    }
                }
                else -> advice
            }
        }
    }

    override fun toString(): String = "Compose($first, $second)"
}
