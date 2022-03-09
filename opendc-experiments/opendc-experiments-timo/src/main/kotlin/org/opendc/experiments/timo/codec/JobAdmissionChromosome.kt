package org.opendc.experiments.timo.codec

import io.jenetics.Chromosome
import io.jenetics.util.ISeq
import io.jenetics.util.RandomRegistry
import org.opendc.experiments.timo.util.GeometricDistribution
import org.opendc.workflow.service.scheduler.job.JobAdmissionPolicy

class JobAdmissionChromosome(genes: ISeq<PolicyGene<Pair<String,Any>>> = ISeq.empty()) : PolicyChromosome<Pair<String,Any>>(genes) {
    private val dist = GeometricDistribution(0.9)

    override fun newInstance(genes: ISeq<PolicyGene<Pair<String,Any>>>): Chromosome<PolicyGene<Pair<String,Any>>> {
        return JobAdmissionChromosome(genes)
    }

    private val genesis = JobAdmissionGene(null)

    override fun newInstance(): Chromosome<PolicyGene<Pair<String,Any>>> {
        val random = RandomRegistry.random()
        return JobAdmissionChromosome(ISeq.of({ genesis.newInstance() }, dist(random)))
    }
}
