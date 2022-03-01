package org.opendc.experiments.timo.codec

import io.jenetics.Genotype
import io.jenetics.engine.Codec
import org.opendc.experiments.timo.input.Input
import org.opendc.workflow.service.scheduler.job.CompositeJobOrderPolicy
import org.opendc.workflow.service.scheduler.job.JobOrderPolicy

object JobOrderCodec : InputCodec<JobOrderPolicy> {
    override fun build(input: Input): Codec<JobOrderPolicy, *> {
        val gtf: Genotype<StagePolicyGene<JobOrderPolicy>> = Genotype.of(JobOrderChromosome())
        return Codec.of({ gtf }) {
            it.chromosome.map { it.allele!! }.reduce { acc, jobOrderPolicy ->  CompositeJobOrderPolicy(acc, jobOrderPolicy) }
        }
    }
}

