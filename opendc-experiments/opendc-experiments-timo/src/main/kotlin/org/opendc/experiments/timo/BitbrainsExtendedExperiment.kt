package org.opendc.experiments.timo

import com.typesafe.config.ConfigFactory
import org.opendc.compute.service.SnapshotMetricExporter
import org.opendc.compute.service.SnapshotParser
import org.opendc.compute.service.scheduler.*
import org.opendc.compute.service.scheduler.filters.ComputeFilter
import org.opendc.compute.service.scheduler.filters.RamFilter
import org.opendc.compute.service.scheduler.filters.VCpuFilter
import org.opendc.compute.service.scheduler.weights.*
import org.opendc.compute.workload.*
import org.opendc.compute.workload.telemetry.SdkTelemetryManager
import org.opendc.compute.workload.topology.Topology
import org.opendc.compute.workload.topology.apply
import org.opendc.compute.workload.util.VmInterferenceModelReader
import org.opendc.experiments.capelin.topology.clusterTopology
import org.opendc.harness.dsl.Experiment
import org.opendc.harness.dsl.anyOf
import org.opendc.simulator.compute.kernel.interference.VmInterferenceModel
import org.opendc.simulator.compute.power.InterpolationPowerModel
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.telemetry.sdk.metrics.export.CoroutineMetricReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import java.time.Duration
import java.util.*

class BitbrainsExtendedExperiment : Experiment("Bitbrains baseline portfolio scheduling experiment") {

    /**
     * The configuration to use.
     */
    private val config = ConfigFactory.load().getConfig("opendc.experiments.timo")

    /**
     * The [ComputeWorkloadLoader] responsible for loading the traces.
     */
    private var workloadLoader = ComputeWorkloadLoader(File("src/main/resources/trace"))

    private var traceName = "bitbrains"

    private var topologyName = "solvinity_topology"
    private val vCpuAllocationRatio by anyOf(16.0)
    private val ramAllocationRatio by anyOf(1.0)
    private val portfolioSimulationDuration by anyOf(Duration.ofMinutes(20))
    private val interferenceModel: VmInterferenceModel
    private val saveSnapshots = false
    private val exportSnapshots = false
    private val metric = "host_energy_efficiency"
    private val seed = 1
    init {
        val perfInterferenceInput = checkNotNull(SolvinityBaselineExperiment::class.java.getResourceAsStream("/interference-model-solvinity.json"))
        interferenceModel =
            VmInterferenceModelReader()
                .read(perfInterferenceInput)
                .withSeed(seed.toLong())

    }
    override fun doRun(repeat: Int) {
        val reflectionResults : Deque<Pair<ComputeScheduler,Long>> = java.util.ArrayDeque()
        reflectionResults.add(Pair(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(4.0), RamFilter(1.0)),
            weighers= listOf(MCLWeigher(0.57), CpuLoadWeigher(-0.47)), subsetSize = 31),1370400000))
        val portfolioScheduler = PortfolioScheduler(createPortfolio(), portfolioSimulationDuration, Duration.ofMillis(20), metric = metric,
            saveSnapshots = saveSnapshots, exportSnapshots = exportSnapshots, reflectionResults = reflectionResults)
        runScheduler(portfolioScheduler, "Portfolio_Scheduler${portfolioSimulationDuration.toMinutes()}m")
        writeSchedulerHistory(portfolioScheduler.schedulerHistory,portfolioScheduler.simulationHistory,"${portfolioScheduler}_history.txt")
    }

    private fun runSnapshot(scheduler: ComputeScheduler)=
        runBlockingSimulation {
            val snapshot = SnapshotParser("solvinity_baseline").loadSnapshot(0)
            val topology = createTopology(topologyName)
            val telemetry = SdkTelemetryManager(clock)
            val runner = ComputeServiceHelper(
                coroutineContext,
                clock,
                telemetry,
                scheduler,
                interferenceModel = interferenceModel
            )

            try {
                val result = runner.simulatePolicy(snapshot, scheduler,topology)
                println("efficiency: ${result.hostEnergyEfficiency}")
            } finally {
                runner.close()
                telemetry.close()
            }
        }

    private fun runScheduler(scheduler: ComputeScheduler, fileName: String) = runBlockingSimulation {
        println("Running scheduler: $scheduler")
        val exporter = SnapshotMetricExporter()
        val topology = createTopology(topologyName)
        val hostDataWriter = MainTraceDataWriter("$traceName/$fileName", topology.resolve().size)
        val serverDataWriter = ServerDataWriter("$traceName/${fileName}_serverData")
        val workload = createWorkload(traceName, seed)
        for(entry in workload){
            entry.trace.resetTraceProgression()
        }
        val telemetry = SdkTelemetryManager(clock)
        val runner = ComputeServiceHelper(
            coroutineContext,
            clock,
            telemetry,
            scheduler,
            interferenceModel = interferenceModel
        )
        telemetry.registerMetricReader(CoroutineMetricReader(this, exporter))
        telemetry.registerMetricReader(CoroutineMetricReader(this, hostDataWriter))
        telemetry.registerMetricReader(CoroutineMetricReader(this, serverDataWriter))
        try {
            runner.apply(topology)
            runner.run(workload,seed.toLong())
        }
        finally {
            runner.close()
            telemetry.close()
            hostDataWriter.close()
            serverDataWriter.writeToFile()
            serverDataWriter.close()
        }
        val result = exporter.getResult()
        println(
            "Scheduler " +
                "Success=${result.attemptsSuccess} " +
                "Failure=${result.attemptsFailure} " +
                "Error=${result.attemptsError} " +
                "Pending=${result.serversPending} " +
                "Active=${result.serversActive} " +
                "Steal time = ${result.totalStealTime} " +
                "Power draw = ${result.totalPowerDraw} " +
                "Cpu demand = ${result.meanCpuDemand}"
        )
        System.gc()
    }
    /**
     * Obtain the trace.
     */
    private fun createWorkload(traceName: String, seed: Int = 0): List<VirtualMachine> {
        return trace(traceName).resolve(workloadLoader,Random(seed.toLong()))
    }

    private fun createPortfolio() : Portfolio {
        val portfolio = Portfolio()
        val lowestCpuDemand = FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(CpuDemandWeigher())
        )
        val lowestCpuLoad= FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(CpuLoadWeigher())
        )
        val vCpuCapacityWeigher = FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(VCpuCapacityWeigher())
        )
        val lowestMemoryLoad = FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(RamWeigher())
        )
        val firstFit = FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(FFWeigher()))
        val maximumConsolidationLoad = FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(MCLWeigher()))
        portfolio.addEntry(lowestCpuDemand)
        portfolio.addEntry(lowestCpuLoad)
        portfolio.addEntry(vCpuCapacityWeigher)
        portfolio.addEntry(lowestMemoryLoad)
        portfolio.addEntry(firstFit)
        portfolio.addEntry(maximumConsolidationLoad)
        return portfolio
    }
    /**
     * Obtain the trace reader for the test.
     */
    private fun createTestWorkload(traceName: String,fraction: Double, seed: Int = 0): List<VirtualMachine> {
        val source = trace(traceName).sampleByLoad(fraction)
        return source.resolve(workloadLoader, Random(seed.toLong()))
    }

    /**
     * Obtain the topology factory for the test.
     */
    private fun createTopology(name: String = "topology"): Topology {
        val ibm = listOf(58.4, 98.0, 109.0, 118.0, 128.0, 140.0, 153.0, 170.0, 189.0, 205.0, 222.0)
        val stream = checkNotNull(object {}.javaClass.getResourceAsStream("/env/$name.txt"))
        return stream.use { clusterTopology(stream, powerModel = InterpolationPowerModel(ibm)) }
    }

    private fun writeSchedulerHistory(schedulerHistory: MutableList<SimulationResult>, simulationHistory: MutableMap<Long,MutableList<SimulationResult>>, fileName : String){

        val workingDirectory = Paths.get("").toAbsolutePath().toString()
        val outputPath = config.getString("output-path")
        val file = File("$workingDirectory/$outputPath/$traceName/$fileName")
        file.createNewFile()
        val writer = BufferedWriter(FileWriter(file, false))
        for(entry in schedulerHistory){
            val simulationResults = simulationHistory[entry.time]
            val resultString = simulationResults?.joinToString(separator = " ") { activeMetric(it.performance).toString() }
            writer.write("${entry.time/60000} ${entry.scheduler} $resultString")
            writer.newLine()
        }
        writer.flush()
        writer.close()
    }

    private fun activeMetric(result : SnapshotMetricExporter.Result) : Any{
        return when (metric) {
            "cpu_ready" -> result.totalStealTime
            "energy_usage" -> result.totalPowerDraw / 1000
            "cpu_usage" -> result.meanCpuUsage
            "host_energy_efficiency" -> result.hostEnergyEfficiency
            else -> {
                throw java.lang.IllegalArgumentException("Metric not found.")
            }
        }
    }
}
