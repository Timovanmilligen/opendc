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

/**
 * Main entrypoint of the experiment.
 */
fun main() {
    val exp = PortfolioExperiment()
    exp.runScenario(0)
}

class PortfolioExperiment {
    /**
     * The logger for this instance.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * The configuration to use.
     */
    private val config = ConfigFactory.load().getConfig("opendc.experiments.timo")

    private val geneticSearchWriter : BufferedWriter
    /**
     * The [ComputeWorkloadLoader] responsible for loading the traces.
     */
    private var workloadLoader = ComputeWorkloadLoader(File("src/main/resources/trace"))

    private var traceName = "solvinity"

    private var topologyName = "solvinity_topology"
    private val maxGenerations = 50L
    private val populationSize = 30
    private val vCpuAllocationRatio = 16.0
    private val ramAllocationRatio = 1.0
    private val portfolioSimulationDuration = Duration.ofMinutes(20)
    private val interferenceModel: VmInterferenceModel
    private val saveSnapshots = false
    private val exportSnapshots = true
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
        val geneticSearchFile = File("$workingDirectory/$outputPath/$traceName/genetic_search.txt")
        geneticSearchFile.createNewFile()
        geneticSearchWriter = BufferedWriter(FileWriter(geneticSearchFile, false))
    }

    fun runScenario(repeat: Int) {
        //runGeneticSearch("bitbrains_baseline", 128..181)

        println("run, $repeat portfolio simulation duration: ${portfolioSimulationDuration.toMinutes()} minutes")
        runScheduler(FilterScheduler(
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
            weighers = listOf(VCpuCapacityWeigher())),"VCpuCapacity")
        //val portfolioScheduler = PortfolioScheduler(createPortfolio(), portfolioSimulationDuration, Duration.ofMillis(20), metric = metric,
       //     saveSnapshots = saveSnapshots, exportSnapshots = exportSnapshots)
       // runScheduler(portfolioScheduler, "Portfolio_Scheduler${portfolioSimulationDuration.toMinutes()}m")
       // writeSchedulerHistory(portfolioScheduler.schedulerHistory,portfolioScheduler.simulationHistory,"${portfolioScheduler}_history.txt")
    }

    private fun runGeneticSearch(folderName: String, range : IntRange){
        val snapshots : MutableList<SnapshotParser.ParsedSnapshot> = mutableListOf()
        for (i in range){
            snapshots.add(SnapshotParser(folderName).loadSnapshot(i))
        }
        println( "Running genetic search" )
        val geneticSearchHeader = "Generation Avg_fitness Best_fitness Scheduler vCpuOvercommit"
        geneticSearchWriter.write(geneticSearchHeader)
        geneticSearchWriter.newLine()
        geneticSearchWriter.flush()
        val engine = Engine.builder(SnapshotProblem(snapshots,createTopology(topologyName),interferenceModel)).optimize(Optimize.MAXIMUM).survivorsSelector(TournamentSelector(5))
            .executor(Runnable::run) // Make sure Jenetics does not run concurrently
            .populationSize(populationSize)
            .offspringSelector(RouletteWheelSelector())
            .alterers(
                UniformCrossover(),
                Mutator(0.10),
                GuidedMutator(0.10),
                LengthMutator(0.02),
                RedundantPruner()
            ).build()

        val result = RandomRegistry.with(Random(seed.toLong())) {
            engine.stream()
                .limit(Limits.byFitnessConvergence(10, 30, 10e-4))
                .limit(maxGenerations)
                .peek { update(it) }
                .collect(EvolutionResult.toBestEvolutionResult())
        }
        println("Best fitness: ${result.bestFitness()}, genotype: ${GenotypeConverter().invoke(result.bestPhenotype().genotype())}")
        val schedulerSpec = GenotypeConverter().invoke(result.bestPhenotype().genotype())
        geneticSearchWriter.write("${result.generation()} ${result.population().map{it.fitness()}.average()} " +
            "${result.bestFitness()} ${FilterScheduler(weighers = schedulerSpec.weighers, filters = schedulerSpec.filters,
                subsetSize = schedulerSpec.subsetSize)} ${schedulerSpec.vCpuOverCommit}")
        geneticSearchWriter.flush()
        geneticSearchWriter.close()
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
            filters = listOf(ComputeFilter(),VCpuFilter(24.0),RamFilter(1.0)),
            weighers= listOf(InstanceCountWeigher(0.9412430903700983)), subsetSize = 9),Long.MAX_VALUE,0)
        val secondGeneticSearchResult = PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(),VCpuFilter(25.0),RamFilter(1.0)),
            weighers= listOf(InstanceCountWeigher(0.5857407043555648)),
            subsetSize = 9),Long.MAX_VALUE,0)
        val bitbrainsGeneticResult  = PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(),VCpuFilter(20.0),RamFilter(1.0)),
            weighers= listOf(CoreRamWeigher(-0.6865062188603075)), subsetSize = 13),Long.MAX_VALUE,0)
        val bitbrainsGeneticResult2  = PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(),VCpuFilter(48.0),RamFilter(1.0)),
            weighers= listOf(CoreRamWeigher(-0.9087571514776227),VCpuWeigher(allocationRatio = 48.0, multiplier = 0.511676672730697)), subsetSize = 4),Long.MAX_VALUE,0)
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

    private fun update(result: EvolutionResult<PolicyGene<Pair<String, Any>>, Long>){
        println("Generation: ${result.generation()}, Population size: ${result.population().size()} Altered:${result.alterCount()}, Best phenotype: ${result.bestPhenotype()}, Average fitness: ${result.population().map{it.fitness()}.average()}")

        val schedulerSpec = GenotypeConverter().invoke(result.bestPhenotype().genotype())
        println( "best scheduler this generation: $schedulerSpec")
        geneticSearchWriter.write("${result.generation()} ${result.population().map{it.fitness()}.average()} " +
            "${result.bestFitness()} ${FilterScheduler(weighers = schedulerSpec.weighers, filters = schedulerSpec.filters,
                subsetSize = schedulerSpec.subsetSize)} ${schedulerSpec.vCpuOverCommit}")
        geneticSearchWriter.newLine()
        geneticSearchWriter.flush()
    }
}
