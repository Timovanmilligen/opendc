package org.opendc.experiments.timo.problems

import io.jenetics.Genotype
import io.jenetics.engine.Codec
import io.jenetics.engine.Problem
import io.jenetics.util.RandomRegistry
import mu.KotlinLogging
import org.opendc.compute.service.scheduler.FilterScheduler
import org.opendc.compute.workload.*
import org.opendc.compute.workload.telemetry.SdkTelemetryManager
import org.opendc.compute.workload.topology.Topology
import org.opendc.compute.workload.topology.apply
import org.opendc.experiments.timo.ClusterComputeMetricExporter
import org.opendc.experiments.timo.codec.*
import org.opendc.experiments.timo.util.GenotypeConverter
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.telemetry.compute.ComputeMetricExporter
import org.opendc.telemetry.compute.collectServiceMetrics
import org.opendc.telemetry.compute.table.HostTableReader
import org.opendc.telemetry.compute.table.ServiceData
import org.opendc.telemetry.compute.table.ServiceTableReader
import org.opendc.telemetry.sdk.metrics.export.CoroutineMetricReader
import java.time.Instant
import java.util.function.Function

class VMProblem(private val workload: List<VirtualMachine>, private val topology: Topology) : Problem<SchedulerSpecification,PolicyGene<Pair<String,Any>>,Long> {

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
        val exporter = ClusterComputeMetricExporter()
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
                runner.run(workload, seed.toLong())
                val serviceMetrics = collectServiceMetrics(telemetry.metricProducer)
                logger.debug {
                    "Scheduler " +
                        "Success=${serviceMetrics.attemptsSuccess} " +
                        "Failure=${serviceMetrics.attemptsFailure} " +
                        "Error=${serviceMetrics.attemptsError} " +
                        "Pending=${serviceMetrics.serversPending} " +
                        "Active=${serviceMetrics.serversActive}"
                }
            } finally {
                runner.close()
                telemetry.close()
            }
        }
        val result = exporter.getResult()
        println("mean instance count: ${result.meanNumDeployedImages}")
        println("total cpu ready: ${result.totalStealTime} ")
        return result.totalVmsFinished.toLong()
    }
}
