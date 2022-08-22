import org.opendc.workflow.service.internal.JobState
import org.opendc.workflow.service.internal.TaskState
import org.opendc.workflow.service.internal.WorkflowSchedulerListener
import org.opendc.workflow.service.scheduler.job.toposort
import java.time.Clock
import java.util.*
import kotlin.math.max

public class WorkflowMetricCollector(private val clock: Clock) : WorkflowSchedulerListener {

    /**
     * The number of finished jobs.
     */
    public var jobsFinished: Long = 0

    /**
     * The number of finished tasks.
     */
    public var tasksFinished: Long = 0

    /**
     * The job stats.
     */
    public val jobStats: MutableList<JobStats> = mutableListOf()

    /**
     * The task stats.
     */
    public val taskStats: MutableList<TaskStats> = mutableListOf()

    private var stats: MutableMap<UUID, JobMonitor> = mutableMapOf<UUID, JobMonitor>()

    override fun jobFinished(job: JobState) {
        jobsFinished += 1
        val stat = stats.remove(job.job.uid)!!

        val criticalPath = job.job.toposort()
        val length = mutableMapOf<UUID, Int>()
        val finishes = mutableMapOf<UUID, Long>()

        for (task in criticalPath) {
            val taskStat = stat.tasks.getValue(task.uid)
            val parent = task.dependencies.maxByOrNull { stat.tasks.getValue(it.uid).startTime }
            val parentFinish = parent?.let { stat.tasks.getValue(it.uid).finishTime }
            val start = taskStat.startTime
            val end = taskStat.finishTime
            val submit = taskStat.submitTime
            val execution = end - start
            length[task.uid] = (parent?.let { length[it.uid] } ?: 0) + 1
            finishes[task.uid] = max(parentFinish ?: 0, start) + execution
            taskStats.add(TaskStats(task.uid, max(start - submit, 0)))
        }

        val (cpl, count) = let { _ ->
            val max = finishes.maxByOrNull { it.value }
            val count = max?.let { length[it.key] } ?: 0
            val min = job.job.tasks.asSequence().map { stat.tasks.getValue(it.uid).startTime }.minOfOrNull { it }
            Pair(max(1, (max?.value ?: 0) - (min ?: 0)), count)
        }

        val (makespan, waiting) = let {
            val submit = stat.tasks.asSequence().map { it.value.submitTime }.minOfOrNull { it } ?: 0
            val start = stat.tasks.asSequence().map { it.value.startTime }.minOfOrNull { it } ?: 0
            val finish = stat.tasks.asSequence().map { it.value.finishTime }.minOfOrNull { it } ?: 0
            Pair(finish - submit, start - submit)
        }
        jobStats.add(JobStats(job.job.uid, makespan, waiting, cpl, count, makespan / cpl))
    }

    override fun jobStarted(job: JobState) {
    }

    override fun jobSubmitted(job: JobState) {
        val stat = JobMonitor()
        stats[job.job.uid] = stat
    }

    override fun taskAssigned(task: TaskState) {
    }

    override fun taskFinished(task: TaskState) {
        tasksFinished += 1
        val stat = stats[task.job.job.uid]!!
        stat.tasks.getValue(task.task.uid).finishTime = clock.millis()
    }

    override fun taskReady(task: TaskState) {
        stats[task.job.job.uid]!!.tasks[task.task.uid] = TaskMonitor(clock.millis())
    }

    override fun taskStarted(task: TaskState) {
        val stat = stats[task.job.job.uid]!!
        stat.tasks.getValue(task.task.uid).startTime = clock.millis()
    }
}
/**
 * An object to track the stats of tasks within a job.
 */
public class JobMonitor {
    public val tasks: MutableMap<UUID, TaskMonitor> = mutableMapOf<UUID, TaskMonitor>()
}

public data class TaskMonitor(
    val submitTime: Long,
    var startTime: Long = 0,
    var finishTime: Long = 0
)

public data class JobStats(
    val job: UUID,
    val makespan: Long,
    val waitTime: Long,
    val criticalPath: Long,
    val criticalPathLength: Int,
    val nsl: Long
)
public data class TaskStats(
    val task: UUID,
    val responseTime: Long
)
