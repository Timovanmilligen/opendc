package org.opendc.experiments.timo.util

import io.jenetics.Genotype
import org.opendc.compute.service.scheduler.filters.*
import org.opendc.compute.service.scheduler.weights.*
import org.opendc.experiments.timo.codec.*
import org.opendc.experiments.timo.problems.SchedulerSpecification
import org.opendc.workflow.service.scheduler.job.*
import org.opendc.workflow.service.scheduler.task.*

internal class GenotypeConverter {
    operator fun invoke(gt: Genotype<PolicyGene<Pair<String, Any>>>): SchedulerSpecification {
        val weighers = mutableListOf<HostWeigher>()
        val gtList = gt.toList()
        val overCommitChromosome = gtList.filterIsInstance<OvercommitChromosome>().first()
        val vCpuOvercommitRate = (overCommitChromosome.toList().first().allele()!!.second as Int).toDouble()
        val subsetChromosome = gtList.filterIsInstance<SubsetChromosome>().first()
        val subsetSize = subsetChromosome.toList().first().allele()!!.second as Int

        val it = gtList.iterator()
        // Loop over chromosomes in genotype
        while (it.hasNext()) {
            // Chromosome
            when (val currentChromosome = it.next()) {
                // HostWeighingChromosome
                is HostWeighingChromosome -> {
                    val genes = currentChromosome.toList()
                    val geneIterator = genes.iterator()
                    while (geneIterator.hasNext()) {
                        val currentGene = geneIterator.next()
                        val allele = currentGene.allele()!!
                        val multiplier = allele.second as Double
                        when (allele.first) {
                            "coreRamWeigher" -> weighers.add(CoreRamWeigher(multiplier))
                            "cpuDemandWeigher" -> weighers.add(CpuDemandWeigher(multiplier))
                            "cpuLoadWeigher" -> weighers.add(CpuLoadWeigher(multiplier))
                            "instanceCountWeigher" -> weighers.add(InstanceCountWeigher(multiplier))
                            "mclWeigher" -> weighers.add(MCLWeigher(multiplier))
                            "ramWeigher" -> weighers.add(RamWeigher(multiplier))
                            "vCpuCapacityWeigher" -> weighers.add(VCpuCapacityWeigher(multiplier))
                            else -> weighers.add(VCpuWeigher(allocationRatio = vCpuOvercommitRate, multiplier))
                        }
                    }
                }
            }
        }
        val filters = mutableListOf(ComputeFilter(), VCpuFilter(vCpuOvercommitRate), RamFilter(1.0))

        return SchedulerSpecification(
            filters = filters,
            weighers = weighers,
            vCpuOverCommit = vCpuOvercommitRate,
            subsetSize = subsetSize
        )
    }
}
