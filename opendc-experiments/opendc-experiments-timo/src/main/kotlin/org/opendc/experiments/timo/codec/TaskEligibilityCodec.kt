package org.opendc.experiments.timo.codec

import io.jenetics.Genotype
import io.jenetics.engine.Codec
import org.opendc.experiments.timo.input.Input
import org.opendc.workflow.service.scheduler.task.CompositeTaskEligibilityPolicy
import org.opendc.workflow.service.scheduler.task.TaskEligibilityPolicy

object TaskEligibilityCodec : InputCodec<TaskEligibilityPolicy> {
    override fun build(input: Input): Codec<TaskEligibilityPolicy, *> {
        val gtf: Genotype<StagePolicyGene<TaskEligibilityPolicy>> = Genotype.of(TaskEligibilityChromosome())

        return Codec.of({ gtf }) {
            it.chromosome.map { it.allele!! }.reduce { acc, policy -> CompositeTaskEligibilityPolicy(acc, policy) }
        }
    }
}
