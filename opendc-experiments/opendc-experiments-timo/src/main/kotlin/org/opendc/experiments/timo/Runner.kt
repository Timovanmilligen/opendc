package org.opendc.experiments.timo

import io.jenetics.*
import io.jenetics.engine.Engine
import org.opendc.experiments.timo.codec.*
import org.opendc.experiments.timo.operator.GuidedMutator
import org.opendc.experiments.timo.operator.LengthMutator
import org.opendc.experiments.timo.operator.RedundantPruner

fun main(){
    println("hello there")
    val populationSize = 100

    Engine.builder(SchedulingProblem()).optimize(Optimize.MINIMUM).survivorsSelector(TournamentSelector(5))
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

}
