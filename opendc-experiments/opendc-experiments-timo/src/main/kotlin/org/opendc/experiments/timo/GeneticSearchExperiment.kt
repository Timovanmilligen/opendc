package org.opendc.experiments.timo

import com.typesafe.config.ConfigFactory
import io.jenetics.*
import io.jenetics.engine.Engine
import io.jenetics.engine.EvolutionResult
import io.jenetics.engine.Limits
import io.jenetics.util.RandomRegistry
import mu.KotlinLogging
import org.opendc.compute.service.SnapshotParser
import org.opendc.compute.service.scheduler.*
import org.opendc.compute.workload.topology.Topology
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
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import java.util.*

class GeneticSearchExperiment : Experiment("Genetic Search Experiment") {

    /**
     * The logger for this instance.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * The configuration to use.
     */
    private val config = ConfigFactory.load().getConfig("opendc.experiments.timo")

    private val geneticSearchWriter : BufferedWriter


    private var traceName = "solvinity"

    private var topologyName = "solvinity_topology"
    private val maxGenerations = 50L
    private val populationSize by anyOf(30)

    private val interferenceModel: VmInterferenceModel

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
    override fun doRun(repeat: Int) {
         runGeneticSearch("solvinity_baseline", 0..91)
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

    /**
     * Obtain the topology factory for the test.
     */
    private fun createTopology(name: String = "topology"): Topology {
        val ibm = listOf(58.4, 98.0, 109.0, 118.0, 128.0, 140.0, 153.0, 170.0, 189.0, 205.0, 222.0)
        val stream = checkNotNull(object {}.javaClass.getResourceAsStream("/env/$name.txt"))
        return stream.use { clusterTopology(stream, powerModel = InterpolationPowerModel(ibm)) }
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
