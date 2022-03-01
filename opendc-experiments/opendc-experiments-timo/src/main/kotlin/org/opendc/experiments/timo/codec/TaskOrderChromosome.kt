package org.opendc.experiments.timo.codec

import io.jenetics.Chromosome
import io.jenetics.util.ISeq
import io.jenetics.util.RandomRegistry
import org.opendc.experiments.timo.util.GeometricDistribution
import org.opendc.workflow.service.scheduler.task.TaskOrderPolicy

class TaskOrderChromosome(genes: ISeq<StagePolicyGene<TaskOrderPolicy>> = ISeq.empty()) : StagePolicyChromosome<TaskOrderPolicy>(genes) {
    private val dist = GeometricDistribution(0.85)
    override fun newInstance(genes: ISeq<StagePolicyGene<TaskOrderPolicy>>): Chromosome<StagePolicyGene<TaskOrderPolicy>> {
        return TaskOrderChromosome(genes)
    }

    private val genesis = TaskOrderGene(null)

    override fun newInstance(): Chromosome<StagePolicyGene<TaskOrderPolicy>> {
        val random = RandomRegistry.getRandom()
        return TaskOrderChromosome(ISeq.of({ genesis.newInstance() }, dist(random)))
    }
}
