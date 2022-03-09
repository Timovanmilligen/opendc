package org.opendc.experiments.timo

import io.jenetics.*
import io.jenetics.engine.Engine
import io.jenetics.engine.EvolutionResult
import io.jenetics.engine.Limits.byPopulationConvergence
import io.jenetics.util.RandomRegistry
import org.opendc.experiments.timo.codec.*
import org.opendc.experiments.timo.operator.GuidedMutator
import org.opendc.experiments.timo.operator.LengthMutator
import org.opendc.experiments.timo.operator.RedundantPruner
import java.util.*
import kotlin.system.measureTimeMillis

fun main() {
    val populationSize = 100
    val seed : Long = 123
    val engine = Engine.builder(SchedulingProblem()).optimize(Optimize.MINIMUM).survivorsSelector(TournamentSelector(5))
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
                .limit(20)
                .peek{update(it)}
                .collect(EvolutionResult.toBestEvolutionResult())
        }
    }
    println("execution time: ${measureTimeMillis(run)}")
}

fun update(result: EvolutionResult<PolicyGene<Pair<String,Any>>,Long>){
    println("Generation: ${result.generation()}, Best phenotype: ${result.bestPhenotype()}")
}

