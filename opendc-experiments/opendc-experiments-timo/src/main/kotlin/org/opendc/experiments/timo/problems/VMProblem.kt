package org.opendc.experiments.timo.problems

import io.jenetics.Genotype
import io.jenetics.engine.Codec
import io.jenetics.engine.Problem
import io.jenetics.util.RandomRegistry
import org.opendc.compute.service.scheduler.FilterScheduler
import org.opendc.compute.service.scheduler.filters.ComputeFilter
import org.opendc.compute.service.scheduler.filters.RamFilter
import org.opendc.compute.service.scheduler.filters.VCpuFilter
import org.opendc.compute.workload.*
import org.opendc.compute.workload.telemetry.SdkTelemetryManager
import org.opendc.compute.workload.topology.Topology
import org.opendc.compute.workload.topology.apply
import org.opendc.experiments.capelin.topology.clusterTopology
import org.opendc.experiments.timo.codec.*
import org.opendc.experiments.timo.util.GenotypeConverter
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.telemetry.compute.ComputeMetricExporter
import org.opendc.telemetry.compute.table.HostTableReader
import org.opendc.telemetry.compute.table.ServiceData
import org.opendc.telemetry.compute.table.ServiceTableReader
import org.opendc.telemetry.sdk.metrics.export.CoroutineMetricReader
import java.io.File
import java.time.Instant
import java.util.*
import java.util.function.Function

class VMProblem(private val workload: List<VirtualMachine>, private val topology: Topology) : Problem<SchedulerSpecification,PolicyGene<Pair<String,Any>>,Long> {

    /**
     * The monitor used to keep track of the metrics.
     */
    private var exporter: TestComputeMetricExporter

    init{
        exporter = TestComputeMetricExporter()
    }

    override fun fitness(): Function<SchedulerSpecification, Long> {
        return Function<SchedulerSpecification,Long>{spec -> eval(spec)}
    }

    override fun codec(): Codec<SchedulerSpecification, PolicyGene<Pair<String, Any>>> {
        return Codec.of({ Genotype.of(mutableListOf(HostWeighingChromosome().newInstance(),HostFilterChromosome().newInstance())) }, //The initial genotype
            {gt -> GenotypeConverter().invoke(gt)})
    }

    private fun eval(schedulerSpec : SchedulerSpecification) : Long{

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

                println(
                    "Scheduler " +
                        "Success=${exporter.serviceMetrics.attemptsSuccess} " +
                        "Failure=${exporter.serviceMetrics.attemptsFailure} " +
                        "Error=${exporter.serviceMetrics.attemptsError} " +
                        "Pending=${exporter.serviceMetrics.serversPending} " +
                        "Active=${exporter.serviceMetrics.serversActive}"
                )
            } finally {
                runner.close()
                telemetry.close()
            }
        }
        if((exporter.serviceMetrics.attemptsFailure or exporter.serviceMetrics.attemptsError or exporter.serviceMetrics.serversActive or exporter.serviceMetrics.serversPending)>0){
            return Long.MAX_VALUE
        }
        return exporter.lostTime
    }

    class TestComputeMetricExporter : ComputeMetricExporter() {
        var serviceMetrics: ServiceData = ServiceData(Instant.ofEpochMilli(0), 0, 0, 0, 0, 0, 0, 0)
        var idleTime = 0L
        var activeTime = 0L
        var stealTime = 0L
        var lostTime = 0L
        var energyUsage = 0.0
        var uptime = 0L

        override fun record(reader: ServiceTableReader) {
            serviceMetrics = ServiceData(
                reader.timestamp,
                reader.hostsUp,
                reader.hostsDown,
                reader.serversPending,
                reader.serversActive,
                reader.attemptsSuccess,
                reader.attemptsFailure,
                reader.attemptsError
            )
        }

        override fun record(reader: HostTableReader) {
            idleTime += reader.cpuIdleTime
            activeTime += reader.cpuActiveTime
            stealTime += reader.cpuStealTime
            lostTime += reader.cpuLostTime
            energyUsage += reader.powerTotal
            uptime += reader.uptime
        }
    }
}
