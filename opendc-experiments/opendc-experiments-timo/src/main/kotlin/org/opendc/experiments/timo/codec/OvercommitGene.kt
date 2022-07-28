package org.opendc.experiments.timo.codec

import io.jenetics.util.RandomRegistry
import java.util.*

class OvercommitGene(allele: Pair<String,Any>?) : PolicyGene<Pair<String,Any>>(allele) {

    override fun newInstance(): PolicyGene<Pair<String,Any>> {
        val random = RandomRegistry.random()
        return OvercommitGene(Pair("overCommit",random.nextInt(1,49)))
    }

    override fun newInstance(value: Pair<String, Any>?): PolicyGene<Pair<String, Any>> {
        return OvercommitGene(value)
    }
}

