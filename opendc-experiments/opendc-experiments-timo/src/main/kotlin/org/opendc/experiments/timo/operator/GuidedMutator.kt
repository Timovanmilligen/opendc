package org.opendc.experiments.timo.operator

import io.jenetics.Mutator
import org.opendc.experiments.timo.codec.PolicyGene
import org.opendc.experiments.timo.util.normal
import org.opendc.experiments.timo.util.normalInt
import java.util.*

/**
 *
 * @author Fabian Mastenbroek (f.s.mastenbroek@student.tudelft.nl)
 */

class GuidedMutator(probability: Double) : Mutator<PolicyGene<Pair<String, Any>>, Long>(probability) {
    override fun mutate(gene: PolicyGene<Pair<String, Any>>, random: Random): PolicyGene<Pair<String, Any>> {
        val allele = gene.allele()
        return when (allele!!.first) {
            "coreRamWeigher" -> {
                val min = -1.0
                val max = 1.0
                val value = allele.second
                val newValue = random.normal(value as Double, min, max)

                gene.newInstance(Pair(allele.first,newValue))
            }
            "ramWeigher" -> {
                val min = -1.0
                val max = 1.0
                val value = allele.second
                val newValue = random.normal(value as Double, min, max)

                gene.newInstance(Pair(allele.first,newValue))
            }
            "vCpuWeigher" -> {
                val min = 0.1
                val max = 2.0
                val value = allele.second
                val newValue = random.normal(value as Double, min, max)

                gene.newInstance(Pair(allele.first,newValue))
            }
            "limitJobAdmission" ->{
                val min = 1
                val max = 1000

                val value = allele.second as Int
                val newValue = random.normalInt(value, min, max)
                gene.newInstance(Pair(allele.first,newValue))
            }
            "randomJobAdmission" -> {
                val min = 0.01
                val max = 1.0

                val value = allele.second as Double
                val newValue = random.normal(value, min, max)
                gene.newInstance(Pair(allele.first,newValue))
            }
            "limitTaskEligiblity"-> {
                val min = 1
                val max = 1000

                val value = allele.second as Int
                val newValue = random.normalInt(value, min, max)

                gene.newInstance(Pair(allele.first,newValue))
            }
            "limitPerJobTaskEligiblity"-> {
                val min = 1
                val max = 100

                val value = allele.second as Int
                val newValue = random.normalInt(value, min, max)

                gene.newInstance(Pair(allele.first,newValue))
            }
            "balancingTaskEligiblity"-> {
                val min = 1.01
                val max =2.0

                val value = allele.second as Double
                val newValue = random.normal(value, min, max)

                gene.newInstance(Pair(allele.first,newValue))
            }
            "randomTaskEligiblity"-> {
                val min = 0.01
                val max =1.0

                val value = allele.second as Double
                val newValue = random.normal(value, min, max)

                gene.newInstance(Pair(allele.first,newValue))
            }
            else -> gene
        }
    }
}

