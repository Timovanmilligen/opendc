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

fun main(){
    println("hello there")
    val populationSize = 100

    val engine = Engine.builder(SchedulingProblem()).optimize(Optimize.MINIMUM).survivorsSelector(TournamentSelector(5))
        .executor(Runnable::run) // Make sure jenetics does not run concurrently
        .populationSize(populationSize)
        .offspringSelector(RouletteWheelSelector())
        .alterers(
            UniformCrossover(),
            Mutator(0.10),
            GuidedMutator(0.05),
            LengthMutator(0.02),
            RedundantPruner()
        ).build()
    val result = engine.stream()
            .limit(byPopulationConvergence(10E-4))
            .limit(20)
            .collect(EvolutionResult.toBestEvolutionResult())
    println("Best fitness: ${result.bestFitness()}")
    println("Best phenotype: ${result.bestPhenotype()}")

}
