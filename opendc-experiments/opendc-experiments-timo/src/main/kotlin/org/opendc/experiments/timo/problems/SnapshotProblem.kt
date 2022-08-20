package org.opendc.experiments.timo.problems

import io.jenetics.Genotype
import io.jenetics.engine.Codec
import io.jenetics.engine.Problem
import io.jenetics.util.RandomRegistry
import mu.KotlinLogging
import org.opendc.compute.service.SnapshotMetricExporter
import org.opendc.compute.service.SnapshotParser
import org.opendc.compute.service.scheduler.FilterScheduler
import org.opendc.compute.service.scheduler.filters.HostFilter
import org.opendc.compute.service.scheduler.weights.HostWeigher
import org.opendc.compute.workload.*
import org.opendc.compute.workload.telemetry.SdkTelemetryManager
import org.opendc.compute.workload.topology.Topology
import org.opendc.compute.workload.topology.apply
import org.opendc.experiments.timo.codec.*
import org.opendc.experiments.timo.util.GenotypeConverter
import org.opendc.simulator.compute.kernel.interference.VmInterferenceModel
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.telemetry.sdk.metrics.export.CoroutineMetricReader
import java.util.function.Function

class SnapshotProblem(private val snapshotHistory: MutableList<SnapshotParser.ParsedSnapshot>, private val topology: Topology, private val interferenceModel: VmInterferenceModel) : Problem<SchedulerSpecification,PolicyGene<Pair<String,Any>>,Long> {

    /**
     * The logger for this instance.
     */
    private val logger = KotlinLogging.logger {}

    override fun fitness(): Function<SchedulerSpecification, Long> {
        return Function<SchedulerSpecification,Long>{spec -> eval(spec)}
    }

    override fun codec(): Codec<SchedulerSpecification, PolicyGene<Pair<String, Any>>> {
        return Codec.of({ Genotype.of(mutableListOf(HostWeighingChromosome().newInstance(),SubsetChromosome().newInstance(),OvercommitChromosome().newInstance())) }, //The initial genotype
            {gt -> GenotypeConverter().invoke(gt)})
    }

    //Fitness is how often a policy is chosen over existing portfolio over all snapshots. (score needs to be better not just equal)
    private fun eval(schedulerSpec : SchedulerSpecification) : Long{
        println("evaluating: $schedulerSpec")
        var schedulerChosen = 0L
        var improvement = 0.0
        for(snapshotEntry in snapshotHistory) {
            runBlockingSimulation {
                val exporter = SnapshotMetricExporter()
                val scheduler = FilterScheduler(schedulerSpec.filters, schedulerSpec.weighers, schedulerSpec.subsetSize)
                val telemetry = SdkTelemetryManager(clock)
                val runner = ComputeServiceHelper(
                    coroutineContext,
                    clock,
                    telemetry,
                    scheduler,
                    interferenceModel = interferenceModel
                )
                telemetry.registerMetricReader(CoroutineMetricReader(this, exporter))
                try {
                    runner.apply(topology)
                    val result = runner.simulatePolicy(snapshotEntry, scheduler)
                    if(result.hostEnergyEfficiency > snapshotEntry.result)
                    {
                        improvement += result.hostEnergyEfficiency-snapshotEntry.result
                        schedulerChosen++
                        println("genetic search energy efficiency: ${result.hostEnergyEfficiency}, old efficiency: ${snapshotEntry.result}")
                    }
                } finally {
                    runner.close()
                    telemetry.close()
                }
            }
        }
        return (improvement*100000).toLong()
    }
}
data class SchedulerSpecification(
    var filters: List<HostFilter>,
    var weighers: List<HostWeigher>,
    var vCpuOverCommit: Double,
    var subsetSize: Int
)
