package org.opendc.experiments.timo.operator

import io.jenetics.Alterer
import io.jenetics.AltererResult
import io.jenetics.Chromosome
import io.jenetics.Genotype
import io.jenetics.Phenotype
import io.jenetics.util.ISeq
import io.jenetics.util.Seq
import org.opendc.experiments.timo.codec.PolicyGene
import org.opendc.experiments.timo.codec.TaskOrderGene

class RedundantPruner: Alterer<PolicyGene<Pair<String, Any>>, Double> {
    override fun alter(population: Seq<Phenotype<PolicyGene<Pair<String, Any>>, Double>>, generation: Long): AltererResult<PolicyGene<Pair<String, Any>>, Double> {
        return AltererResult.of(population.map { phenotype -> prune(phenotype, generation) }.asISeq())
    }

    private fun prune(phenotype: Phenotype<PolicyGene<Pair<String, Any>>, Double>, generation: Long): Phenotype<PolicyGene<Pair<String, Any>>, Double> {
        var changed = false
        val chromosomes = phenotype.genotype().map { chromosome ->
            val res = prune(chromosome)
            if (res != chromosome)
                changed = true
            res
        }
        return if (changed) {
            Phenotype.of<PolicyGene<Pair<String, Any>>, Double>(Genotype.of(chromosomes), generation)
        } else {
            phenotype
        }
    }

    private fun prune(chromosome: Chromosome<PolicyGene<Pair<String, Any>>>): Chromosome<PolicyGene<Pair<String, Any>>> {
        val new = chromosome.toMutableList()
        val it = new.iterator()
        var current = it.next()
        var changed = false

        while (it.hasNext()) {
            val next = it.next()

            if (current == next) {
                changed = true
                it.remove()
                continue
            }

            current = next
        }

        return if (changed) {
            chromosome.newInstance(ISeq.of(new))
        } else {
            chromosome
        }
    }
}
