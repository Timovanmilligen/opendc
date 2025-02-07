package org.opendc.experiments.timo.operator

import io.jenetics.Chromosome
import io.jenetics.Mutator
import io.jenetics.MutatorResult
import io.jenetics.util.ISeq
import org.opendc.experiments.timo.codec.OvercommitChromosome
import org.opendc.experiments.timo.codec.PolicyGene
import org.opendc.experiments.timo.codec.SubsetChromosome
import java.util.*

class LengthMutator(probability: Double) : Mutator<PolicyGene<Pair<String,Any>>, Long>(probability) {
    override fun mutate(chromosome: Chromosome<PolicyGene<Pair<String,Any>>>, p: Double, random: Random): MutatorResult<Chromosome<PolicyGene<Pair<String,Any>>>> {
        //Don't mutate in length
        if(chromosome is OvercommitChromosome){
            return MutatorResult.of(chromosome)
        }
        else if(chromosome is SubsetChromosome){
            return MutatorResult.of(chromosome)
        }

        val rd = random.nextDouble()

        val genes = chromosome.toMutableList()
        if (rd < 1/3.0) {
            if (genes.size > 1) {
                genes.removeAt(0)
            }
        } else {
            genes.add(genes[0].newInstance())
        }
        return MutatorResult.of(chromosome.newInstance(ISeq.of(genes)))
    }
}
