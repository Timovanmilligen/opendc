package org.opendc.experiments.timo.codec

import io.jenetics.Genotype
import io.jenetics.engine.Codec
import io.jenetics.engine.Problem
import io.opentelemetry.sdk.metrics.export.MetricProducer
import org.opendc.compute.service.scheduler.FilterScheduler
import org.opendc.compute.service.scheduler.filters.ComputeFilter
import org.opendc.compute.service.scheduler.filters.HostFilter
import org.opendc.compute.service.scheduler.filters.RamFilter
import org.opendc.compute.service.scheduler.filters.VCpuFilter
import org.opendc.compute.service.scheduler.weights.*
import org.opendc.compute.workload.ComputeServiceHelper
import org.opendc.compute.workload.telemetry.NoopTelemetryManager
import org.opendc.compute.workload.topology.HostSpec
import org.opendc.simulator.compute.kernel.SimSpaceSharedHypervisorProvider
import org.opendc.simulator.compute.model.MachineModel
import org.opendc.simulator.compute.model.MemoryUnit
import org.opendc.simulator.compute.model.ProcessingNode
import org.opendc.simulator.compute.model.ProcessingUnit
import org.opendc.simulator.compute.power.ConstantPowerModel
import org.opendc.simulator.compute.power.SimplePowerDriver
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.trace.Trace
import org.opendc.workflow.service.scheduler.job.*
import org.opendc.workflow.service.scheduler.task.*
import org.opendc.workflow.workload.WorkflowSchedulerSpec
import org.opendc.workflow.workload.WorkflowServiceHelper
import org.opendc.workflow.workload.toJobs
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import java.util.function.Function

class SchedulingProblem : Problem<SchedulerSpecification,PolicyGene<Pair<String,Any>>,Long> {
    override fun fitness(): Function<SchedulerSpecification, Long> {
      return Function<SchedulerSpecification,Long>{spec -> eval(spec)}
    }

    override fun codec(): Codec<SchedulerSpecification, PolicyGene<Pair<String, Any>>> {
        return Codec.of({ Genotype.of(mutableListOf(TaskOrderChromosome().newInstance(), HostWeighingChromosome().newInstance(),
            TaskEligibilityChromosome().newInstance(),JobOrderChromosome().newInstance(),JobAdmissionChromosome().newInstance())) },
            {gt -> convertGenotype(gt)})
    }

    private fun eval(schedulerSpec : SchedulerSpecification) : Long{
        var fitness : Long = 0
        runBlockingSimulation {
            // Configure the ComputeService that is responsible for mapping virtual machines onto physical hosts
            val HOST_COUNT = 4
            val computeScheduler = FilterScheduler(
                filters = listOf(ComputeFilter(), VCpuFilter(1.0), RamFilter(1.0)),
                weighers = schedulerSpec.weighers
            )

            val computeHelper = ComputeServiceHelper(coroutineContext, clock, NoopTelemetryManager(), computeScheduler, schedulingQuantum = Duration.ofSeconds(1))

            repeat(HOST_COUNT) { computeHelper.registerHost(createHostSpec(it)) }

            // Configure the WorkflowService that is responsible for scheduling the workflow tasks onto machines
            val workflowScheduler = WorkflowSchedulerSpec(
                schedulingQuantum = Duration.ofMillis(100),
                jobAdmissionPolicy = schedulerSpec.jobAdmission,
                jobOrderPolicy = schedulerSpec.jobOrder,
                taskEligibilityPolicy = schedulerSpec.taskEligibility,
                taskOrderPolicy = schedulerSpec.taskOrder,
            )
            val workflowHelper = WorkflowServiceHelper(coroutineContext, clock, computeHelper.service.newClient(), workflowScheduler)

            try {
                val trace = Trace.open(
                    Paths.get(checkNotNull(SchedulingProblem::class.java.getResource("/trace.gwf")).toURI()),
                    format = "gwf"
                )

                workflowHelper.replay(trace.toJobs().subList(0,75))
            } finally {
                workflowHelper.close()
                computeHelper.close()
            }

            fitness = workflowHelper.totalJobMakespan / workflowHelper.traceJobSize
        }
        return fitness
    }
    private fun convertGenotype(gt : Genotype<PolicyGene<Pair<String,Any>>>) : SchedulerSpecification {
        val weighers = mutableListOf<HostWeigher>()
        val filters = mutableListOf<HostFilter>()
        val gtList = gt.toList()
        val it = gtList.iterator()
        var taskOrderPolicy: TaskOrderPolicy = RandomTaskOrderPolicy
        var taskEligibilityPolicy : TaskEligibilityPolicy = NullTaskEligibilityPolicy
        var jobOrderPolicy: JobOrderPolicy = RandomJobOrderPolicy
        var jobAdmissionPolicy: JobAdmissionPolicy = NullJobAdmissionPolicy
        //Loop over chromosomes in genotype
        while (it.hasNext()) {
            //Chromosome
            val currentChromosome = it.next()
            when (currentChromosome) {
                //HostWeighingChromosome
                is HostWeighingChromosome -> {
                    val genes = currentChromosome.toList()
                    val geneIterator = genes.iterator()
                    while (geneIterator.hasNext()) {
                        val currentGene = geneIterator.next()
                        val allele = currentGene.allele()!!
                        val multiplier = allele.second as Double
                        when (allele.first) {
                            "coreRamWeigher" -> weighers.add(CoreRamWeigher(multiplier))
                            "ramWeigher" -> weighers.add(RamWeigher(multiplier))
                            "vCpuWeigher" -> weighers.add(VCpuWeigher(multiplier))
                            "vCpuCapacityWeigher" -> weighers.add(VCpuCapacityWeigher(multiplier))
                            else -> weighers.add(InstanceCountWeigher(multiplier))
                        }
                    }
                }
                // Job admission
                is JobAdmissionChromosome-> {
                    jobAdmissionPolicy = currentChromosome.map { convertToJobAdmissionPolicy(it.allele()!!) }.reduce { acc, policy -> CompositeJobAdmissionPolicy(acc, policy) }
                }
                //Job order
                is JobOrderChromosome ->{
                    jobOrderPolicy = currentChromosome.map { convertToJobOrderPolicy(it.allele()!!) }.reduce { acc, policy -> CompositeJobOrderPolicy(acc, policy) }
                }
                //Task eligibility
                is TaskEligibilityChromosome -> {
                    taskEligibilityPolicy = currentChromosome.map { convertToTaskEligibilityPolicy(it.allele()!!) }.reduce { acc, policy -> CompositeTaskEligibilityPolicy(acc, policy) }
                }
                //Task order
                else -> {
                    taskOrderPolicy  = currentChromosome.map { convertToTaskOrderPolicy(it.allele()!!) }.reduce { acc, policy -> CompositeTaskOrderPolicy(acc, policy) }
                }
            }
        }
        filters.addAll(listOf(ComputeFilter(), VCpuFilter(1.0), RamFilter(1.0)))
        return SchedulerSpecification(
            filters = filters,
            weighers = weighers,
            taskOrder = taskOrderPolicy,
            taskEligibility = taskEligibilityPolicy,
            jobOrder = jobOrderPolicy,
            jobAdmission =  jobAdmissionPolicy
        )
    }

    private fun convertToJobAdmissionPolicy(allele: Pair<String,Any>?) : JobAdmissionPolicy{
        return when (allele!!.first) {
            "limitJobAdmission" -> LimitJobAdmissionPolicy(allele.second as Int)
            "randomJobAdmission" -> RandomJobAdmissionPolicy(allele.second as Double)
            else -> NullJobAdmissionPolicy
        }
    }

    private fun convertToJobOrderPolicy(allele: Pair<String,Any>?) : JobOrderPolicy{
        return when (allele!!.first) {
            "submissionTimeJobOrder" -> SubmissionTimeJobOrderPolicy(allele.second as Boolean)
            "durationJobOrder" -> DurationJobOrderPolicy(allele.second as Boolean)
            "sizeJobOrder" -> SizeJobOrderPolicy(allele.second as Boolean)
            else -> RandomJobOrderPolicy
        }
    }

    private fun convertToTaskEligibilityPolicy(allele: Pair<String,Any>?) : TaskEligibilityPolicy{
        return when (allele!!.first) {
            "limitTaskEligiblity" -> LimitTaskEligibilityPolicy(allele.second as Int)
            "limitPerJobTaskEligiblity" -> LimitPerJobTaskEligibilityPolicy(allele.second as Int)
            "balancingTaskEligiblity" -> BalancingTaskEligibilityPolicy(allele.second as Double)
            "randomTaskEligiblity" -> RandomTaskEligibilityPolicy(allele.second as Double)
            else -> NullTaskEligibilityPolicy
        }
    }
    private fun convertToTaskOrderPolicy(allele: Pair<String,Any>?) : TaskOrderPolicy{
        val ascending = allele!!.second as Boolean
        return when (allele.first) {
            "submissionTaskOrder" -> SubmissionTimeTaskOrderPolicy(ascending)
            "durationTaskOrder" ->  DurationTaskOrderPolicy(ascending)
            "dependenciesTaskOrder" ->  DependenciesTaskOrderPolicy(ascending)
            "dependentsTaskOrder" ->  DependentsTaskOrderPolicy(ascending)
            "activeTaskOrder" -> ActiveTaskOrderPolicy(ascending)
            "durationHistoryTaskOrder" -> DurationHistoryTaskOrderPolicy(ascending)
            "completionTaskOrder" ->  CompletionTaskOrderPolicy(ascending)
            else -> RandomTaskOrderPolicy
        }
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
        return res
    }
}
data class SchedulerSpecification(
    var filters: List<HostFilter>,
    var weighers: List<HostWeigher>,
    var taskOrder: TaskOrderPolicy,
    var taskEligibility: TaskEligibilityPolicy,
    var jobOrder: JobOrderPolicy,
    var jobAdmission: JobAdmissionPolicy
    )

class WorkflowMetrics {
    var jobsSubmitted = 0L
    var jobsActive = 0L
    var jobsFinished = 0L
    var tasksSubmitted = 0L
    var tasksActive = 0L
    var tasksFinished = 0L
}
