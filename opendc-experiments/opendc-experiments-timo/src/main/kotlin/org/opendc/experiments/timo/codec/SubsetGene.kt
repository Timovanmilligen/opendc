package org.opendc.experiments.timo.codec

import io.jenetics.util.RandomRegistry
import java.util.*

class SubsetGene(allele: Pair<String, Any>?) : PolicyGene<Pair<String, Any>>(allele) {

    override fun newInstance(): PolicyGene<Pair<String, Any>> {
        val random = RandomRegistry.random()
        return SubsetGene(Pair("subsetSize", random.nextInt(1, 33)))
    }

    override fun newInstance(value: Pair<String, Any>?): PolicyGene<Pair<String, Any>> {
        return SubsetGene(value)
    }
}
