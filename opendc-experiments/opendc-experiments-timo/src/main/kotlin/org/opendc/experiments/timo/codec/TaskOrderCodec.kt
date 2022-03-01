package org.opendc.experiments.timo.codec

import io.jenetics.Genotype
import io.jenetics.engine.Codec
import org.opendc.experiments.timo.input.Input
import org.opendc.workflow.service.scheduler.task.CompositeTaskOrderPolicy
import org.opendc.workflow.service.scheduler.task.TaskOrderPolicy

object TaskOrderCodec : InputCodec<TaskOrderPolicy> {
    override fun build(input: Input): Codec<TaskOrderPolicy, *> {
        val gtf: Genotype<StagePolicyGene<TaskOrderPolicy>> = Genotype.of(TaskOrderChromosome())
        return Codec.of({ gtf }) {
            it.chromosome.map { it.allele!! }.reduce { acc, taskOrderPolicy ->  CompositeTaskOrderPolicy(acc, taskOrderPolicy) }
        }
    }
}
