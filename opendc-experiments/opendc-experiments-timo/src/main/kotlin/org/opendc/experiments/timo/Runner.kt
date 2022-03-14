package org.opendc.experiments.timo

import io.jenetics.*
import io.jenetics.engine.Engine
import io.jenetics.engine.EvolutionResult
import io.jenetics.engine.EvolutionStatistics
import io.jenetics.engine.Limits.byPopulationConvergence
import io.jenetics.util.RandomRegistry
import org.opendc.experiments.timo.codec.*
import org.opendc.experiments.timo.operator.GuidedMutator
import org.opendc.experiments.timo.operator.LengthMutator
import org.opendc.experiments.timo.operator.RedundantPruner
import org.opendc.experiments.timo.util.GenotypeConverter
import org.opendc.trace.Trace
import org.opendc.workflow.api.Job
import org.opendc.workflow.workload.WorkflowServiceHelper
import org.opendc.workflow.workload.toJobs
import java.nio.file.Paths
import java.util.*
import kotlin.system.measureTimeMillis

fun main() {
    val populationSize = 100
    val seed : Long = 123
    val maxGenerations = 20L
    val subsets= 10
    val portfolio = Portfolio()
    val trace = Trace.open(
        Paths.get(checkNotNull(SchedulingProblem::class.java.getResource("/trace.gwf")).toURI()),
        format = "gwf"
    )
    val traceSubsets = trace.toJobs().sortedBy { it.metadata.getOrDefault("WORKFLOW_SUBMIT_TIME", Long.MAX_VALUE) as Long }.withIndex()
        .groupBy { it.index / (trace.toJobs().size/subsets) }
        .map { it.value.map { it.value } }

    for (traceSubset in traceSubsets) {

        println("subset size: ${traceSubset.size}")

    val engine = Engine.builder(SchedulingProblem(traceSubset)).optimize(Optimize.MINIMUM).survivorsSelector(TournamentSelector(5))
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

    val run: () -> Unit = {
        val result = RandomRegistry.with(Random(seed)) {
            engine.stream()
                .limit(byPopulationConvergence(10E-4))
                .limit(maxGenerations)
                .peek{update(it)}
                .collect(EvolutionResult.toBestEvolutionResult())
        }
        portfolio.smart.add(PortfolioEntry(GenotypeConverter().invoke(result.bestPhenotype().genotype()),0,0))
    }
    run()
    }
}

fun update(result: EvolutionResult<PolicyGene<Pair<String,Any>>,Long>){

    println("Generation: ${result.generation()}, Altered:${result.alterCount()}, Best phenotype: ${result.bestPhenotype()}")
}

