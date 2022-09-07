package org.opendc.experiments.timo

import com.typesafe.config.ConfigFactory
import io.jenetics.*
import io.jenetics.engine.Engine
import io.jenetics.engine.EvolutionResult
import io.jenetics.engine.Limits
import io.jenetics.util.RandomRegistry
import mu.KotlinLogging
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
import org.opendc.experiments.timo.codec.PolicyGene
import org.opendc.experiments.timo.operator.GuidedMutator
import org.opendc.experiments.timo.operator.LengthMutator
import org.opendc.experiments.timo.operator.RedundantPruner
import org.opendc.experiments.timo.problems.SnapshotProblem
import org.opendc.experiments.timo.util.GenotypeConverter
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
import kotlin.math.exp
import kotlin.math.log

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

    private var traceName = "bitbrains"

    private var topologyName = "solvinity_topology"
    private val maxGenerations = 50L
    private val populationSize by anyOf(30)
    private val vCpuAllocationRatio by anyOf(16.0)
    private val ramAllocationRatio by anyOf(1.0)
    private val portfolioSimulationDuration by anyOf(Duration.ofMinutes(20))
    private val interferenceModel: VmInterferenceModel
    private val saveSnapshots = false
    private val exportSnapshots = false
    private val metric = "host_energy_efficiency"
    //private val schedulerChoice by anyOf(FFScheduler(),PortfolioScheduler(createPortfolio(), portfolioSimulationDuration, Duration.ofMillis(20)))
    private val seed = 1
    init {
        val perfInterferenceInput = checkNotNull(PortfolioExperiment::class.java.getResourceAsStream("/interference-model-solvinity.json"))
        interferenceModel =
            VmInterferenceModelReader()
                .read(perfInterferenceInput)
                .withSeed(seed.toLong())
        val workingDirectory = Paths.get("").toAbsolutePath().toString()
        val outputPath = config.getString("output-path")
    }
    override fun doRun(repeat: Int) {
       // runGeneticSearch("Solvinity_baseline", 0..183)

        /*runScheduler(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(FFWeigher())),"FirstFit")
        runScheduler(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(CpuDemandWeigher())),"LowestCpuDemand")
        runScheduler(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(CpuLoadWeigher())),"LowestCpuLoad")
        runScheduler(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(MCLWeigher())),"MaximumConsolidationLoad")
        runScheduler(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(RamWeigher())),"LowestMemoryLoad")
        runScheduler(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(VCpuCapacityWeigher())),"VCpuCapacity")*/
        //val portfolioScheduler = PortfolioScheduler(createPortfolio(), portfolioSimulationDuration, Duration.ofMillis(20), metric = metric,
        //    saveSnapshots = saveSnapshots, exportSnapshots = exportSnapshots)
      //  runScheduler(portfolioScheduler, "Portfolio_Scheduler${portfolioSimulationDuration.toMinutes()}m")
      //  writeSchedulerHistory(portfolioScheduler.schedulerHistory,portfolioScheduler.simulationHistory,"${portfolioScheduler}_history.txt")
        val bestScheduler = FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(RamWeigher()))
        val geneticScheduler = FilterScheduler(
            filters = listOf(ComputeFilter(),VCpuFilter(40.0),RamFilter(1.0)),
            weighers= listOf(VCpuWeigher(0.9176161100317948), VCpuWeigher(0.7533259515543165)), subsetSize = 25)
        val solvinityGeneticScheduler = FilterScheduler(
            filters = listOf(ComputeFilter(),VCpuFilter(16.0),RamFilter(1.0)),
            weighers= listOf(RamWeigher(-0.9877656354684774), VCpuWeigher(0.7533259515543165)), subsetSize = 27)
        runSnapshot(bestScheduler)
        runSnapshot(solvinityGeneticScheduler)
        val solvinityScheduler = FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(3.0), RamFilter(1.0)),
            weighers= listOf(InstanceCountWeigher(1.0), VCpuWeigher(3.0,0.22)), subsetSize = 21)
        //runSnapshot(solvinityScheduler)
        //runSnapshot(bestScheduler)
        //runSnapshot(FilterScheduler(
         //   filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
         //   weighers = listOf(FFWeigher())))
        //runSnapshot(FilterScheduler(
          // filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            //weighers = listOf(CpuLoadWeigher())))
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
        val lowestCpuDemand = PortfolioEntry(FilterScheduler(
          filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
        weighers = listOf(CpuDemandWeigher())
        ),Long.MAX_VALUE,0)
        val lowestCpuLoad= PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(CpuLoadWeigher())
        ),Long.MAX_VALUE,0)
        val vCpuCapacityWeigher = PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(VCpuCapacityWeigher())
        ),Long.MAX_VALUE,0)
        val lowestMemoryLoad = PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(RamWeigher())
        ),Long.MAX_VALUE,0)
        val firstFit = PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(FFWeigher())),Long.MAX_VALUE,0)
        val maximumConsolidationLoad = PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(vCpuAllocationRatio), RamFilter(ramAllocationRatio)),
            weighers = listOf(MCLWeigher())),Long.MAX_VALUE,0)
        val geneticSearchResult = PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(),VCpuFilter(3.0),RamFilter(1.0)),
            weighers= listOf(InstanceCountWeigher(1.0),VCpuWeigher(3.0,0.22)), subsetSize = 21),Long.MAX_VALUE,0)
        val bbGeneticSearchResult = PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(),VCpuFilter(4.0),RamFilter(1.0)),
            weighers= listOf(MCLWeigher(0.5772043223952548),CpuLoadWeigher(-0.47949372965813275)), subsetSize = 31),Long.MAX_VALUE,0)
        portfolio.addEntry(lowestCpuDemand)
        portfolio.addEntry(lowestCpuLoad)
        //portfolio.addEntry(bitbrainsGeneticResult)
        //portfolio.addEntry(bitbrainsGeneticResult2)
        portfolio.addEntry(vCpuCapacityWeigher)
        portfolio.addEntry(lowestMemoryLoad)
        portfolio.addEntry(firstFit)
        portfolio.addEntry(maximumConsolidationLoad)
        //portfolio.addEntry(geneticSearchResult)
        //portfolio.addEntry(secondGeneticSearchResult)
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
