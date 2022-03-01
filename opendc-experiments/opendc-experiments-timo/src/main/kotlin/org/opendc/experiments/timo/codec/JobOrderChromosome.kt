package org.opendc.experiments.timo.codec

import io.jenetics.Chromosome
import io.jenetics.util.ISeq
import io.jenetics.util.RandomRegistry
import org.opendc.workflow.service.scheduler.job.JobOrderPolicy
import org.opendc.experiments.timo.util.GeometricDistribution

class JobOrderChromosome(genes: ISeq<StagePolicyGene<JobOrderPolicy>> = ISeq.empty()) : StagePolicyChromosome<JobOrderPolicy>(genes) {
    private val dist = GeometricDistribution(0.9)

    override fun newInstance(genes: ISeq<StagePolicyGene<JobOrderPolicy>>): Chromosome<StagePolicyGene<JobOrderPolicy>> {
        return JobOrderChromosome(genes)
    }

    private val genesis = JobOrderGene(null)

    override fun newInstance(): Chromosome<StagePolicyGene<JobOrderPolicy>> {
        val random = RandomRegistry.getRandom()
        return JobOrderChromosome(ISeq.of({ genesis.newInstance() }, dist(random)))
    }
}
