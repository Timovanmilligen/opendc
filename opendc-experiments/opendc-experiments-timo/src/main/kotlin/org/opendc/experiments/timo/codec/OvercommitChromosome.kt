package org.opendc.experiments.timo.codec

import io.jenetics.Chromosome
import io.jenetics.util.ISeq

class OvercommitChromosome(genes: ISeq<PolicyGene<Pair<String, Any>>> = ISeq.empty()) : PolicyChromosome<Pair<String, Any>>(genes) {

    override fun newInstance(genes: ISeq<PolicyGene<Pair<String, Any>>>): Chromosome<PolicyGene<Pair<String, Any>>> {
        return OvercommitChromosome(genes)
    }

    private val genesis = OvercommitGene(null)

    override fun newInstance(): Chromosome<PolicyGene<Pair<String, Any>>> {
        return OvercommitChromosome(ISeq.of(genesis.newInstance()))
    }
}
