package org.opendc.experiments.timo.codec

import io.jenetics.Chromosome
import io.jenetics.util.ISeq
import io.jenetics.util.RandomRegistry
import org.opendc.experiments.timo.util.GeometricDistribution
import org.opendc.workflow.service.scheduler.job.JobAdmissionPolicy

class JobAdmissionChromosome(genes: ISeq<StagePolicyGene<JobAdmissionPolicy>> = ISeq.empty()) : StagePolicyChromosome<JobAdmissionPolicy>(genes) {
    private val dist = GeometricDistribution(0.9)

    override fun newInstance(genes: ISeq<StagePolicyGene<JobAdmissionPolicy>>): Chromosome<StagePolicyGene<JobAdmissionPolicy>> {
        return JobAdmissionChromosome(genes)
    }

    private val genesis = JobAdmissionGene(null)

    override fun newInstance(): Chromosome<StagePolicyGene<JobAdmissionPolicy>> {
        val random = RandomRegistry.getRandom()
        return JobAdmissionChromosome(ISeq.of({ genesis.newInstance() }, dist(random)))
    }
}
