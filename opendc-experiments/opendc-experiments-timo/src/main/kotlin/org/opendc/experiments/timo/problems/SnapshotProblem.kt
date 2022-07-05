package org.opendc.experiments.timo.problems

import io.jenetics.Genotype
import io.jenetics.engine.Codec
import io.jenetics.engine.Problem
import io.jenetics.util.RandomRegistry
import mu.KotlinLogging
import org.opendc.compute.service.SnapshotMetricExporter
import org.opendc.compute.service.scheduler.FilterScheduler
import org.opendc.compute.service.scheduler.Snapshot
import org.opendc.compute.workload.*
import org.opendc.compute.workload.telemetry.SdkTelemetryManager
import org.opendc.compute.workload.topology.Topology
import org.opendc.compute.workload.topology.apply
import org.opendc.experiments.timo.codec.*
import org.opendc.experiments.timo.util.GenotypeConverter
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.telemetry.compute.collectServiceMetrics
import org.opendc.telemetry.sdk.metrics.export.CoroutineMetricReader
import java.util.function.Function

class SnapshotProblem(private val snapshot: Snapshot, private val topology: Topology) : Problem<SchedulerSpecification,PolicyGene<Pair<String,Any>>,Long> {

    /**
     * The logger for this instance.
     */
    private val logger = KotlinLogging.logger {}

    override fun fitness(): Function<SchedulerSpecification, Long> {
        return Function<SchedulerSpecification,Long>{spec -> eval(spec)}
    }

    override fun codec(): Codec<SchedulerSpecification, PolicyGene<Pair<String, Any>>> {
        return Codec.of({ Genotype.of(mutableListOf(HostWeighingChromosome().newInstance(),HostFilterChromosome().newInstance())) }, //The initial genotype
            {gt -> GenotypeConverter().invoke(gt)})
    }

    private fun eval(schedulerSpec : SchedulerSpecification) : Long{
        val exporter = SnapshotMetricExporter()
        var result = SnapshotMetricExporter.Result(0,0,0,0,0.0,0.0,0.0,
            0.0,0.0,0,0,0,0,0,0,0,0.0)
        runBlockingSimulation {
            val seed = 1
            val computeScheduler = FilterScheduler(schedulerSpec.filters, schedulerSpec.weighers, 1, RandomRegistry.random())
            val telemetry = SdkTelemetryManager(clock)
            val runner = ComputeServiceHelper(
                coroutineContext,
                clock,
                telemetry,
                computeScheduler
            )
            telemetry.registerMetricReader(CoroutineMetricReader(this, exporter))
            try {
                runner.apply(topology)
                result = runner.simulatePolicy(snapshot,computeScheduler)
                logger.debug {
                    "Scheduler " +
                        "Success=${result.attemptsSuccess} " +
                        "Failure=${result.attemptsFailure} " +
                        "Error=${result.attemptsError} " +
                        "Pending=${result.serversPending} " +
                        "Active=${result.serversActive}"
                }
                println("total cpu ready: ${result.totalStealTime} ")
            } finally {
                runner.close()
                telemetry.close()
            }
        }

        return result.totalStealTime
    }
}
