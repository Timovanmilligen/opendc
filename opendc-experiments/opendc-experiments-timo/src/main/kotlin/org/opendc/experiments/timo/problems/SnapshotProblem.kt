package org.opendc.experiments.timo.problems

import io.jenetics.Genotype
import io.jenetics.engine.Codec
import io.jenetics.engine.Problem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import mu.KotlinLogging
import org.opendc.compute.api.Server
import org.opendc.compute.portfolio.SnapshotMetricExporter
import org.opendc.compute.portfolio.SnapshotParser
import org.opendc.compute.service.scheduler.FilterScheduler
import org.opendc.compute.service.scheduler.filters.HostFilter
import org.opendc.compute.service.scheduler.weights.HostWeigher
import org.opendc.compute.workload.*
import org.opendc.compute.workload.telemetry.ComputeMetricReader
import org.opendc.compute.workload.topology.Topology
import org.opendc.compute.workload.topology.apply
import org.opendc.experiments.timo.codec.*
import org.opendc.experiments.timo.util.GenotypeConverter
import org.opendc.simulator.core.runBlockingSimulation
import java.time.Duration
import java.util.function.Function

class SnapshotProblem(
    private val snapshotHistory: MutableList<SnapshotParser.ParsedSnapshot>,
    private val topology: Topology
) : Problem<SchedulerSpecification, PolicyGene<Pair<String, Any>>, Long> {

    /**
     * The logger for this instance.
     */
    private val logger = KotlinLogging.logger {}

    override fun fitness(): Function<SchedulerSpecification, Long> {
        return Function<SchedulerSpecification, Long> { spec -> eval(spec) }
    }

    override fun codec(): Codec<SchedulerSpecification, PolicyGene<Pair<String, Any>>> {
        return Codec.of(
            { Genotype.of(mutableListOf(HostWeighingChromosome().newInstance(), SubsetChromosome().newInstance(), OvercommitChromosome().newInstance())) }, // The initial genotype
            { gt -> GenotypeConverter().invoke(gt) }
        )
    }

    // Fitness is how often a policy is chosen over existing portfolio over all snapshots. (score needs to be better not just equal)
    private fun eval(schedulerSpec: SchedulerSpecification): Long {
        println("evaluating: $schedulerSpec")
        var schedulerChosen = 0L
        var improvement = 0.0
        for (snapshotEntry in snapshotHistory) {
            runBlockingSimulation {
                val exporter = SnapshotMetricExporter()
                val scheduler = FilterScheduler(schedulerSpec.filters, schedulerSpec.weighers, schedulerSpec.subsetSize)
                val runner = ComputeServiceHelper(
                    coroutineContext,
                    clock,
                    scheduler,
                    interferenceModel = null // TODO Fix
                )

                val servers = mutableListOf<Server>()
                val metricReader = ComputeMetricReader(
                    this,
                    clock,
                    runner.service,
                    servers,
                    exporter,
                    exportInterval = Duration.ofMinutes(5)
                )

                try {
                    runner.apply(topology)

                    val result = simulatePolicy(snapshotEntry, runner, servers)
                    if (result.hostEnergyEfficiency > snapshotEntry.result) {
                        improvement += result.hostEnergyEfficiency - snapshotEntry.result
                        schedulerChosen++
                        println("genetic search energy efficiency: ${result.hostEnergyEfficiency}, old efficiency: ${snapshotEntry.result}")
                    }
                } finally {
                    runner.close()
                    metricReader.close()
                }
            }
        }
        return (improvement * 100000).toLong()
    }

    private suspend fun simulatePolicy(snapshot: SnapshotParser.ParsedSnapshot, runner: ComputeServiceHelper, servers: MutableList<Server>?): SnapshotMetricExporter.Result {
        val exporter = SnapshotMetricExporter()
        val client = runner.service.newClient()

        // Load the snapshot by placing already active servers on all corresponding hosts
        // (runner.service as ComputeServiceImpl).loadSnapshot(snapshot)

        // Create new image for the virtual machine
        val image = client.newImage("vm-image")
        try {
            coroutineScope {
                snapshot.queue.forEach { serverData ->
                    launch {
                        val workload = serverData.workload
                        workload.getTrace().resetTraceProgression()
                        val server = client.newServer(
                            serverData.name,
                            image,
                            client.newFlavor(
                                serverData.name,
                                serverData.cpuCount,
                                serverData.memorySize,
                                meta = if (serverData.cpuCapacity > 0.0) mapOf("cpu-capacity" to serverData.cpuCapacity) else emptyMap()
                            ),
                            meta = mapOf("workload" to workload)
                        )
                        servers?.add(server)

                        // Wait for the server to reach its end time.
                        val endTime = serverData.workload.getEndTime()
                        val startTime = serverData.workload.getStartTime()
                        delay(endTime - startTime)
                        // Delete the server after reaching the end-time of the virtual machine
                        server.delete()
                    }
                }
            }
            yield()
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            client.close()
            runner.close()
        }
        return exporter.getResult()
    }
}

data class SchedulerSpecification(
    var filters: List<HostFilter>,
    var weighers: List<HostWeigher>,
    var vCpuOverCommit: Double,
    var subsetSize: Int
)
