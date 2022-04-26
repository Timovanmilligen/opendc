package org.opendc.experiments.timo.problems

import WorkflowMetricCollector
import io.jenetics.Genotype
import io.jenetics.engine.Codec
import io.jenetics.engine.Problem
import io.opentelemetry.sdk.metrics.export.MetricProducer
import org.opendc.compute.service.SnapshotMetricExporter
import org.opendc.compute.service.scheduler.FilterScheduler
import org.opendc.compute.service.scheduler.filters.HostFilter
import org.opendc.compute.service.scheduler.weights.*
import org.opendc.compute.workload.ComputeServiceHelper
import org.opendc.compute.workload.telemetry.SdkTelemetryManager
import org.opendc.compute.workload.topology.HostSpec
import org.opendc.experiments.timo.codec.*
import org.opendc.experiments.timo.util.GenotypeConverter
import org.opendc.simulator.compute.kernel.SimSpaceSharedHypervisorProvider
import org.opendc.simulator.compute.model.MachineModel
import org.opendc.simulator.compute.model.MemoryUnit
import org.opendc.simulator.compute.model.ProcessingNode
import org.opendc.simulator.compute.model.ProcessingUnit
import org.opendc.simulator.compute.power.ConstantPowerModel
import org.opendc.simulator.compute.power.SimplePowerDriver
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.telemetry.sdk.metrics.export.CoroutineMetricReader
import org.opendc.workflow.api.Job
import org.opendc.workflow.service.internal.WorkflowServiceImpl
import org.opendc.workflow.service.scheduler.job.*
import org.opendc.workflow.service.scheduler.task.*
import org.opendc.workflow.workload.WorkflowSchedulerSpec
import org.opendc.workflow.workload.WorkflowServiceHelper
import java.time.Duration
import java.util.*
import java.util.function.Function

class SchedulingProblem(private val traceJobs: List<Job>) : Problem<SchedulerSpecification, PolicyGene<Pair<String, Any>>,Long> {
    override fun fitness(): Function<SchedulerSpecification, Long> {
      return Function<SchedulerSpecification,Long>{spec -> eval(spec)}
    }

    override fun codec(): Codec<SchedulerSpecification, PolicyGene<Pair<String, Any>>> {
        return Codec.of({ Genotype.of(mutableListOf(TaskOrderChromosome().newInstance(), HostFilterChromosome().newInstance(), HostWeighingChromosome().newInstance(),
            TaskEligibilityChromosome().newInstance(),JobOrderChromosome().newInstance(),JobAdmissionChromosome().newInstance())) },
            {gt -> GenotypeConverter().invoke(gt)})
    }

    private fun eval(schedulerSpec : SchedulerSpecification) : Long{
        var fitness : Long = 0
        runBlockingSimulation {
            val exporter = SnapshotMetricExporter()
            // Configure the ComputeService that is responsible for mapping virtual machines onto physical hosts
            val HOST_COUNT = 4
            val computeScheduler = FilterScheduler(
                filters = schedulerSpec.filters,
                weighers = schedulerSpec.weighers
            )
            val telemetry = SdkTelemetryManager(clock)
            val computeHelper = ComputeServiceHelper(coroutineContext, clock, telemetry, computeScheduler,
                schedulingQuantum = Duration.ofSeconds(1))
            repeat(HOST_COUNT) { computeHelper.registerHost(createHostSpec(it)) }
            telemetry.registerMetricReader(CoroutineMetricReader(this, exporter))

            // Configure the WorkflowService that is responsible for scheduling the workflow tasks onto machines
            val workflowScheduler = WorkflowSchedulerSpec(
                schedulingQuantum = Duration.ofMillis(100),
                jobAdmissionPolicy = schedulerSpec.jobAdmission,
                jobOrderPolicy = schedulerSpec.jobOrder,
                taskEligibilityPolicy = schedulerSpec.taskEligibility,
                taskOrderPolicy = schedulerSpec.taskOrder,
            )
            val workflowHelper = WorkflowServiceHelper(coroutineContext, clock, computeHelper.service.newClient(), workflowScheduler)
            val workflowMetricCollector = WorkflowMetricCollector(clock)
            (workflowHelper.service as WorkflowServiceImpl).addListener(workflowMetricCollector)

            try {
                workflowHelper.replay(traceJobs)

            } finally {
                workflowHelper.close()
                computeHelper.close()
                telemetry.close()
            }
            val computeMetricResult = exporter.getResult()
            fitness = workflowMetricCollector.taskStats.map { it.responseTime }.average().toLong()
        }
        return fitness
    }

    /**
     * Construct a [HostSpec] for a simulated host.
     */
    private fun createHostSpec(uid: Int): HostSpec {
        // Machine model based on: https://www.spec.org/power_ssj2008/results/res2020q1/power_ssj2008-20191125-01012.html
        val node = ProcessingNode("AMD", "am64", "EPYC 7742", 32)
        val cpus = List(node.coreCount) { ProcessingUnit(node, it, 3400.0) }
        val memory = List(8) { MemoryUnit("Samsung", "Unknown", 2933.0, 16_000) }

        val machineModel = MachineModel(cpus, memory)

        return HostSpec(
            UUID(0, uid.toLong()),
            "host-$uid",
            emptyMap(),
            machineModel,
            SimplePowerDriver(ConstantPowerModel(0.0)),
            SimSpaceSharedHypervisorProvider()
        )
    }

    /**
     * Collect the metrics of the workflow service.
     */
    private fun collectMetrics(metricProducer: MetricProducer): WorkflowMetrics {
        val metrics = metricProducer.collectAllMetrics().associateBy { it.name }
        val res = WorkflowMetrics()
        res.jobsSubmitted = metrics["jobs.submitted"]?.longSumData?.points?.last()?.value ?: 0
        res.jobsActive = metrics["jobs.active"]?.longSumData?.points?.last()?.value ?: 0
        res.jobsFinished = metrics["jobs.finished"]?.longSumData?.points?.last()?.value ?: 0
        res.tasksSubmitted = metrics["tasks.submitted"]?.longSumData?.points?.last()?.value ?: 0
        res.tasksActive = metrics["tasks.active"]?.longSumData?.points?.last()?.value ?: 0
        res.tasksFinished = metrics["tasks.finished"]?.longSumData?.points?.last()?.value ?: 0
        res.jobMakeSpan = metrics["jobs.makespan"]?.longSumData?.points?.last()?.value ?: 0
        return res
    }
}
data class SchedulerSpecification(
    var filters: List<HostFilter>,
    var weighers: List<HostWeigher>,
    var taskOrder: TaskOrderPolicy,
    var taskEligibility: TaskEligibilityPolicy,
    var jobOrder: JobOrderPolicy,
    var jobAdmission: JobAdmissionPolicy,
    var schedulingQuantum: Long
    )

class WorkflowMetrics {
    var jobsSubmitted = 0L
    var jobsActive = 0L
    var jobsFinished = 0L
    var tasksSubmitted = 0L
    var tasksActive = 0L
    var tasksFinished = 0L
    var jobMakeSpan = 0L
}
