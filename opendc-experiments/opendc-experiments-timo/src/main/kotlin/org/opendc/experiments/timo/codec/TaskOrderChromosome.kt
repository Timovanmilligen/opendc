package org.opendc.experiments.timo.codec

import io.jenetics.Chromosome
import io.jenetics.util.ISeq
import io.jenetics.util.RandomRegistry
import org.opendc.experiments.timo.util.GeometricDistribution

class TaskOrderChromosome(genes: ISeq<PolicyGene<Pair<String, Any>>> = ISeq.empty()) : PolicyChromosome<Pair<String, Any>>(genes) {
    private val dist = GeometricDistribution(0.85)
    override fun newInstance(genes: ISeq<PolicyGene<Pair<String, Any>>>): Chromosome<PolicyGene<Pair<String, Any>>> {
        return TaskOrderChromosome(genes)
    }

    private val genesis = TaskOrderGene(null)

    override fun newInstance(): Chromosome<PolicyGene<Pair<String, Any>>> {
        val random = RandomRegistry.random()
        return TaskOrderChromosome(ISeq.of({ genesis.newInstance() }, dist(random)))
    }
}
