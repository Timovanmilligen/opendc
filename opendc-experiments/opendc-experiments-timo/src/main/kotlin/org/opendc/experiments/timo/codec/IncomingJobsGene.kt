package org.opendc.experiments.timo.codec

import io.jenetics.util.RandomRegistry

class IncomingJobsGene(allele: Pair<String, Any>?) : PolicyGene<Pair<String, Any>>(allele) {

    override fun newInstance(): PolicyGene<Pair<String, Any>> {
        val random = RandomRegistry.random()
        return IncomingJobsGene(Pair("quantumScheduling", random.nextLong(1L, 201)))
    }

    override fun newInstance(value: Pair<String, Any>?): PolicyGene<Pair<String, Any>> = IncomingJobsGene(value)
}
