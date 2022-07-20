package org.opendc.experiments.timo

import com.typesafe.config.ConfigFactory
import mu.KotlinLogging
import org.opendc.compute.service.SnapshotMetricExporter
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
import org.opendc.simulator.compute.power.InterpolationPowerModel
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.telemetry.sdk.metrics.export.CoroutineMetricReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import java.time.Duration
import java.util.*

class PortfolioExperiment : Experiment("Portfolio scheduling experiment") {

    /**
     * The logger for this instance.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * The configuration to use.
     */
    private val config = ConfigFactory.load().getConfig("opendc.experiments.timo")

    /**
     * The [ComputeWorkloadLoader] responsible for loading the traces.
     */
    private var workloadLoader = ComputeWorkloadLoader(File("src/main/resources/trace"))

    private var traceName = "solvinity"

    private var topologyName = "solvinity_topology"
    private val populationSize by anyOf(100)
    private val portfolioSimulationDuration by anyOf(Duration.ofMinutes(20))

    private val metric = "host_energy_efficiency"
    //private val schedulerChoice by anyOf(FFScheduler(),PortfolioScheduler(createPortfolio(), portfolioSimulationDuration, Duration.ofMillis(20)))
    private val seed = 1
    override fun doRun(repeat: Int) {
        println("run, $repeat portfolio simulation duration: ${portfolioSimulationDuration.toMinutes()} minutes")
        val portfolioScheduler = PortfolioScheduler(createPortfolio(), portfolioSimulationDuration, Duration.ofMillis(20), metric = metric)
        runScheduler(portfolioScheduler, "Portfolio_Scheduler${portfolioSimulationDuration.toMinutes()}m.txt")
        //writeSchedulerHistory(portfolioScheduler.schedulerHistory,portfolioScheduler.simulationHistory,"${portfolioScheduler}_history.txt")
        //runScheduler(FFScheduler(), "First_Fit")
        //runScheduler(FilterScheduler(
          //  filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
            //weighers = listOf(CpuLoadWeigher())),"LowestCpuLoad")

        /*  runScheduler(FilterScheduler(
             filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
             weighers = listOf(MCLWeigher())),"MaximumConsolidationLoad")
        runScheduler(FilterScheduler(
             filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
             weighers = listOf(RamWeigher())),"LowestMemoryLoad")
         runScheduler(FilterScheduler(
             filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
             weighers = listOf(VCpuCapacityWeigher())),"VCpuCapacity")*/
    }

    private fun runScheduler(scheduler: ComputeScheduler, fileName: String) = runBlockingSimulation {
        println("Running scheduler: $scheduler")
        val exporter = SnapshotMetricExporter()
        val topology = createTopology(topologyName)
        val perfInterferenceInput = checkNotNull(PortfolioExperiment::class.java.getResourceAsStream("/interference-model-solvinity.json"))
        val performanceInterferenceModel =
            VmInterferenceModelReader()
                .read(perfInterferenceInput)
                .withSeed(seed.toLong())
        val hostDataWriter = MainTraceDataWriter(fileName, topology.resolve().size)
        val serverDataWriter = ServerDataWriter("${fileName}_serverData")
        val workload = createTestWorkload(traceName, 1.0, seed)
        for(entry in workload){
            entry.trace.resetTraceProgression()
        }
        val telemetry = SdkTelemetryManager(clock)
        val runner = ComputeServiceHelper(
            coroutineContext,
            clock,
            telemetry,
            scheduler
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
            runner.service.close()
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
    }

    private fun createPortfolio() : Portfolio {
        val portfolio = Portfolio()
        val lowestCpuLoad = PortfolioEntry(FilterScheduler(
          filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
        weighers = listOf(CpuLoadWeigher())
        ),Long.MAX_VALUE,0)
        val vCpuCapacityWeigher = PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
            weighers = listOf(VCpuCapacityWeigher())
        ),Long.MAX_VALUE,0)
        val lowestMemoryLoad = PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
            weighers = listOf(RamWeigher())
        ),Long.MAX_VALUE,0)
        val firstFit = PortfolioEntry(FFScheduler(),Long.MAX_VALUE,0)
        val maximumConsolidationLoad = PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
            weighers = listOf(MCLWeigher())),Long.MAX_VALUE,0)
        portfolio.addEntry(lowestCpuLoad)
        //portfolio.addEntry(vCpuCapacityWeigher)
        portfolio.addEntry(lowestMemoryLoad)
        //portfolio.addEntry(firstFit)
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
        val file = File("$workingDirectory/$outputPath/$fileName")
        file.createNewFile()
        val writer = BufferedWriter(FileWriter(file, false))
        //writer.write(header)
        //writer.newLine()

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
