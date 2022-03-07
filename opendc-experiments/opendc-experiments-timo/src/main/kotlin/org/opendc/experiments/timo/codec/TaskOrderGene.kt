package org.opendc.experiments.timo.codec

import io.jenetics.util.RandomRegistry
import java.util.*

class TaskOrderGene(allele: Pair<String,Any>?) : PolicyGene<Pair<String,Any>>(allele) {

    private val choices: List<(Random) -> Pair<String,Any>> = listOf(
        { random: Random -> Pair("submissionTaskOrder",random.nextBoolean()) },
        { random: Random -> Pair("durationTaskOrder",random.nextBoolean()) },
        { random: Random -> Pair("dependenciesTaskOrder",random.nextBoolean()) },
        { random: Random -> Pair("dependentsTaskOrder",random.nextBoolean()) },
        { random: Random -> Pair("activeTaskOrder",random.nextBoolean()) },
        { random: Random -> Pair("durationHistoryTaskOrder",random.nextBoolean()) },
        { random: Random -> Pair("completionTaskOrder",random.nextBoolean()) },
        { _: Random -> Pair("Random",false) }
    )
    override fun newInstance(): PolicyGene<Pair<String,Any>> {
        val random = RandomRegistry.random()
        return TaskOrderGene(choices[random.nextInt(choices.size)](random))
    }

    override fun newInstance(value: Pair<String, Any>?): PolicyGene<Pair<String, Any>> {
        return TaskOrderGene(value)
    }

}
