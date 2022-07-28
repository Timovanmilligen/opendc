package org.opendc.experiments.timo.codec

import io.jenetics.util.RandomRegistry
import java.util.*

class HostWeighingGene(allele: Pair<String,Any>?) : PolicyGene<Pair<String,Any>>(allele) {

    private val choices: List<(Random) -> Pair<String,Any>> = listOf(
        { random: Random -> Pair("coreRamWeigher", nextDouble(random,-1.0,1.0)) },
        { random: Random -> Pair("cpuDemandWeigher", nextDouble(random,-1.0,1.0)) },
        { random: Random -> Pair("cpuLoadWeigher", nextDouble(random,-1.0,1.0)) },
        { random: Random -> Pair("instanceCountWeigher", nextDouble(random,-1.0,1.0)) },
        { random: Random -> Pair("mclWeigher", nextDouble(random,-1.0,1.0)) },
        { random: Random -> Pair("ramWeigher", nextDouble(random,-1.0,1.0)) },
        { random: Random -> Pair("vCpuCapacityWeigher", nextDouble(random,-1.0,1.0)) },
        { random: Random -> Pair("vCpuWeigher", nextDouble(random,-1.0,1.0)) }
    )

    override fun newInstance(): PolicyGene<Pair<String,Any>> {
        val random = RandomRegistry.random()
        return HostWeighingGene(choices[random.nextInt(choices.size)](random))
    }

    override fun newInstance(value: Pair<String, Any>?): PolicyGene<Pair<String, Any>> {
        return HostWeighingGene(value)
    }

    private fun nextDouble(random: Random, min: Double, max: Double) : Double{
        return min + (max - min) * random.nextDouble()
    }

}

