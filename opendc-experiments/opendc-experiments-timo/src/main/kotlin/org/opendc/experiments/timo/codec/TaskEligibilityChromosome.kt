package org.opendc.experiments.timo.codec

import io.jenetics.Chromosome
import io.jenetics.util.ISeq
import io.jenetics.util.RandomRegistry
import org.opendc.experiments.timo.util.GeometricDistribution
import org.opendc.workflow.service.scheduler.task.TaskEligibilityPolicy

class TaskEligibilityChromosome(genes: ISeq<StagePolicyGene<TaskEligibilityPolicy>> = ISeq.empty()) : StagePolicyChromosome<TaskEligibilityPolicy>(genes) {
    private val dist = GeometricDistribution(0.9)

    override fun newInstance(genes: ISeq<StagePolicyGene<TaskEligibilityPolicy>>): Chromosome<StagePolicyGene<TaskEligibilityPolicy>> {
        return TaskEligibilityChromosome(genes)
    }

    private val genesis = TaskEligibilityGene(null)

    override fun newInstance(): Chromosome<StagePolicyGene<TaskEligibilityPolicy>> {
        val random = RandomRegistry.getRandom()
        return TaskEligibilityChromosome(ISeq.of({ genesis.newInstance() }, dist(random)))
    }
}
