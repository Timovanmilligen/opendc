package org.opendc.experiments.timo.codec

import io.jenetics.Genotype
import io.jenetics.engine.Codec
import org.opendc.experiments.timo.input.Input
import org.opendc.workflow.service.scheduler.job.CompositeJobAdmissionPolicy
import org.opendc.workflow.service.scheduler.job.JobAdmissionPolicy

object JobAdmissionCodec : InputCodec<JobAdmissionPolicy> {
    override fun build(input: Input): Codec<JobAdmissionPolicy, *> {
        val gtf: Genotype<StagePolicyGene<JobAdmissionPolicy>> = Genotype.of(JobAdmissionChromosome())
        return Codec.of({ gtf }) {
            it.chromosome.map { it.allele!! }.reduce { acc, policy ->  CompositeJobAdmissionPolicy(acc, policy) }
        }
    }
}
