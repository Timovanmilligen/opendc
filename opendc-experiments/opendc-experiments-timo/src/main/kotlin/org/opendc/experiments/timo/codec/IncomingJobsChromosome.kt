package org.opendc.experiments.timo.codec

import io.jenetics.Chromosome
import io.jenetics.util.ISeq

class IncomingJobsChromosome(genes: ISeq<PolicyGene<Pair<String, Any>>> = ISeq.empty()) : PolicyChromosome<Pair<String,Any>>(genes) {

    override fun newInstance(genes: ISeq<PolicyGene<Pair<String, Any>>>): Chromosome<PolicyGene<Pair<String, Any>>> {
        return IncomingJobsChromosome(genes)
    }

    private val genesis = IncomingJobsGene(null)

    override fun newInstance(): Chromosome<PolicyGene<Pair<String, Any>>> {
        return IncomingJobsChromosome(ISeq.of(genesis.newInstance()))
    }
}
