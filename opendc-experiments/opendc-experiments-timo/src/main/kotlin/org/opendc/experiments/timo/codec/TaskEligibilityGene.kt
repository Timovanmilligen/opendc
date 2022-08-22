package org.opendc.experiments.timo.codec

import io.jenetics.util.RandomRegistry
import java.util.Random

class TaskEligibilityGene(allele: Pair<String, Any>?) : PolicyGene<Pair<String, Any>>(allele) {
    private val choices: List<(Random) -> Pair<String, Any>> = listOf(
        { _: Random -> Pair("nullTaskEligiblity", 0) },
        { random: Random -> Pair("limitTaskEligiblity", 1 + random.nextInt(1000)) },
        { random: Random -> Pair("limitPerJobTaskEligiblity", 1 + random.nextInt(100)) },
        { random: Random -> Pair("balancingTaskEligiblity", 1.01 + 0.99 * random.nextDouble()) },
        { random: Random -> Pair("randomTaskEligiblity", 0.01 + 0.99 * random.nextDouble()) }
    )

    override fun newInstance(): PolicyGene<Pair<String, Any>> {
        val random = RandomRegistry.random()
        return TaskEligibilityGene(choices[random.nextInt(choices.size)](random))
    }

    override fun newInstance(value: Pair<String, Any>?): PolicyGene<Pair<String, Any>> = TaskEligibilityGene(value)
}
