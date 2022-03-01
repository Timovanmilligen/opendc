package org.opendc.workflow.service.scheduler.task

import org.opendc.workflow.service.internal.TaskState
import org.opendc.workflow.service.internal.WorkflowServiceImpl

public data class CompositeTaskOrderPolicy(val first: TaskOrderPolicy, val second: TaskOrderPolicy) : TaskOrderPolicy {
    override fun invoke(scheduler: WorkflowServiceImpl): Comparator<TaskState> {
        return object : Comparator<TaskState> by first(scheduler).then(second(scheduler)) {}
    }

    override fun toString(): String = "Compose($first, $second)"
}
