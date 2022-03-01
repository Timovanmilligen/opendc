package org.opendc.workflow.service.scheduler.job

import org.opendc.workflow.service.internal.JobState
import org.opendc.workflow.service.internal.WorkflowServiceImpl
import kotlin.random.Random

/**
 * A [JobAdmissionPolicy] that accepts jobs with a certain probability.
 *
 * @property probability The probability for accepting a job.
 */
public data class RandomJobAdmissionPolicy(val probability: Double = 0.5) : JobAdmissionPolicy {

    override fun invoke(scheduler: WorkflowServiceImpl) : JobAdmissionPolicy.Logic = object : JobAdmissionPolicy.Logic {
        val random = Random(123)

        override fun invoke(
            job: JobState
        ): JobAdmissionPolicy.Advice =
            if (random.nextDouble() <= probability || scheduler.activeJobs.isEmpty())
                JobAdmissionPolicy.Advice.ADMIT
            else {
                JobAdmissionPolicy.Advice.DENY
            }
    }

    override fun toString(): String = "Random($probability)"
}
