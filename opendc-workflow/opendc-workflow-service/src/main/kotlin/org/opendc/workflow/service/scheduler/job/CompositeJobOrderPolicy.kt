package org.opendc.workflow.service.scheduler.job

import org.opendc.workflow.service.internal.JobState
import org.opendc.workflow.service.internal.WorkflowServiceImpl

/**
 *
 * @author Fabian Mastenbroek (f.s.mastenbroek@student.tudelft.nl)
 */
public data class CompositeJobOrderPolicy(val first: JobOrderPolicy, val second: JobOrderPolicy) : JobOrderPolicy {
    override fun invoke(scheduler: WorkflowServiceImpl): Comparator<JobState> {
        return object : Comparator<JobState> by first(scheduler).then(second(scheduler)) {}
    }

    override fun toString(): String = "Compose($first, $second)"
}
