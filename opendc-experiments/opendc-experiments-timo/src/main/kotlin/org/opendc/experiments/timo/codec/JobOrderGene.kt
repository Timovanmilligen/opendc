package org.opendc.experiments.timo.codec

import io.jenetics.util.RandomRegistry
import java.util.Random

class JobOrderGene(allele: Pair<String,Any>?) : PolicyGene<Pair<String,Any>>(allele) {
    private val choices: List<(Random) -> Pair<String,Any>> = listOf(
        { random: Random -> Pair("submissionTimeJobOrder",random.nextBoolean()) },
        { random: Random ->  Pair("durationJobOrder",random.nextBoolean()) },
        { random: Random -> Pair("sizeJobOrder",random.nextBoolean()) },
        { _: Random -> Pair("randomJobOrder",true) }
    )

    override fun newInstance(): PolicyGene<Pair<String,Any>> {
        val random = RandomRegistry.random()
        return JobOrderGene(choices[random.nextInt(choices.size)](random))
    }

    override fun newInstance(value: Pair<String,Any>?): PolicyGene<Pair<String,Any>> = JobOrderGene(value)
}
