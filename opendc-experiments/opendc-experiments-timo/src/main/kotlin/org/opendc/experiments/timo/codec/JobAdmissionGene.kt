package org.opendc.experiments.timo.codec

import io.jenetics.util.RandomRegistry
import java.util.Random

class JobAdmissionGene(allele: Pair<String, Any>?) : PolicyGene<Pair<String, Any>>(allele) {
    private val choices: List<(Random) -> Pair<String, Any>> = listOf(
        { _: Random -> Pair("nullJobAdmission", 0) },
        { random: Random -> Pair("limitJobAdmission", 1 + random.nextInt(1000)) },
        { random: Random -> Pair("randomJobAdmission", 0.01 + 0.99 * random.nextDouble()) }
    )

    override fun newInstance(): PolicyGene<Pair<String, Any>> {
        val random = RandomRegistry.random()
        return JobAdmissionGene(choices[random.nextInt(choices.size)](random))
    }

    override fun newInstance(value: Pair<String, Any>?): PolicyGene<Pair<String, Any>> = JobAdmissionGene(value)
}
