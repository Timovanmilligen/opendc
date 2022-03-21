package org.opendc.experiments.timo

import io.jenetics.*
import com.typesafe.config.ConfigFactory
import io.jenetics.engine.Engine
import io.jenetics.engine.EvolutionResult
import io.jenetics.engine.Limits
import io.jenetics.util.RandomRegistry
import mu.KotlinLogging
import org.opendc.experiments.timo.codec.PolicyGene
import org.opendc.experiments.timo.operator.GuidedMutator
import org.opendc.experiments.timo.operator.LengthMutator
import org.opendc.experiments.timo.operator.RedundantPruner
import org.opendc.experiments.timo.problems.SchedulingProblem
import org.opendc.experiments.timo.util.GenotypeConverter
import org.opendc.harness.dsl.Experiment
import org.opendc.harness.dsl.anyOf
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.trace.Trace
import org.opendc.workflow.workload.toJobs
import java.nio.file.Paths
import java.util.*

class WorkflowExperiment() : Experiment("Genetic workflow") {

    /**
     * The logger for this instance.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * The configuration to use.
     */
    private val config = ConfigFactory.load().getConfig("opendc.experiments.timo")

    private val populationSize by anyOf(100)

    private val seed : Long = 123

    private val maxGenerations = 4L

    val subsets = 10

    private val portfolio = Portfolio()

    private val trace = Trace.open(
        Paths.get(checkNotNull(WorkflowExperiment::class.java.getResource("/trace.gwf")).toURI()),
        format = "gwf"
    )

    override fun doRun(repeat: Int) : Unit  = runBlockingSimulation {
        val traceSubsets = trace.toJobs().subList(0,75).sortedBy { it.metadata.getOrDefault("WORKFLOW_SUBMIT_TIME", Long.MAX_VALUE) as Long }//.withIndex()
        //.groupBy { it.index / (trace.toJobs().size/subsets) }
        //.map { it.value.map { it.value } }

        //for (traceSubset in traceSubsets) {

        // println("subset size: ${traceSubset.size}")

        val engine = Engine.builder(SchedulingProblem(traceSubsets)).optimize(Optimize.MINIMUM).survivorsSelector(TournamentSelector(5))
            //.executor(Runnable::run) // Make sure Jenetics does not run concurrently
            .populationSize(populationSize)
            .offspringSelector(RouletteWheelSelector())
            .alterers(
                UniformCrossover(),
                Mutator(0.10),
                GuidedMutator(0.05),
                LengthMutator(0.02),
                RedundantPruner()
            ).build()

        val result = RandomRegistry.with(Random(seed)) {
            engine.stream()
                .limit(Limits.byPopulationConvergence(10E-4))
                .limit(maxGenerations)
                .peek{update(it)}
                .collect(EvolutionResult.toBestEvolutionResult())
        }
        portfolio.smart.add(PortfolioEntry(GenotypeConverter().invoke(result.bestPhenotype().genotype()),0,0))
        logger.info { "Best fitness: ${result.bestFitness()}" }
    }

    private fun update(result: EvolutionResult<PolicyGene<Pair<String, Any>>,Long>){
        logger.info("Generation: ${result.generation()}, Population size: ${result.population().size()} Altered:${result.alterCount()}, Best phenotype: ${result.bestPhenotype()}, Average fitness: ${result.population().map{it.fitness()}.average()}")
    }
}
