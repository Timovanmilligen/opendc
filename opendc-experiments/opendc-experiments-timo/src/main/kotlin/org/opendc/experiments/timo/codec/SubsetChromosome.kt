package org.opendc.experiments.timo.codec

import io.jenetics.Chromosome
import io.jenetics.util.ISeq

class SubsetChromosome(genes: ISeq<PolicyGene<Pair<String, Any>>> = ISeq.empty()) : PolicyChromosome<Pair<String, Any>>(genes) {

    override fun newInstance(genes: ISeq<PolicyGene<Pair<String, Any>>>): Chromosome<PolicyGene<Pair<String, Any>>> {
        return SubsetChromosome(genes)
    }

    private val genesis = SubsetGene(null)

    override fun newInstance(): Chromosome<PolicyGene<Pair<String, Any>>> {
        return SubsetChromosome(ISeq.of(genesis.newInstance()))
    }
}
