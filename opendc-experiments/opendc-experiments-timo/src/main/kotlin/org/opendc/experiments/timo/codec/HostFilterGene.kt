package org.opendc.experiments.timo.codec

import io.jenetics.util.RandomRegistry
import java.util.*

class HostFilterGene(allele: Pair<String, Any>?) : PolicyGene<Pair<String, Any>>(allele) {

    private val choices: List<(Random) -> Pair<String, Any>> = listOf(
        { random: Random -> Pair("instanceCountFilter", random.nextInt(1, 21)) },
        { random: Random -> Pair("ramFilter", nextDouble(random, 0.5, 1.5)) },
        { random: Random -> Pair("vCpuFilter", nextDouble(random, 0.5, 1.5)) },
        { _: Random -> Pair("vCpuCapacityFilter", 0) }
    )
    override fun newInstance(): PolicyGene<Pair<String, Any>> {
        val random = RandomRegistry.random()
        return HostFilterGene(choices[random.nextInt(choices.size)](random))
    }

    override fun newInstance(value: Pair<String, Any>?): PolicyGene<Pair<String, Any>> {
        return HostFilterGene(value)
    }

    private fun nextDouble(random: Random, min: Double, max: Double): Double {
        return min + (max - min) * random.nextDouble()
    }
}
