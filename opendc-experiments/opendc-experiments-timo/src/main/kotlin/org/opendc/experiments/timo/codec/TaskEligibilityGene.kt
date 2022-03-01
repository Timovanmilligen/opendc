package org.opendc.experiments.timo.codec

import io.jenetics.util.RandomRegistry
import org.opendc.workflow.service.scheduler.task.RandomTaskEligibilityPolicy
import org.opendc.workflow.service.scheduler.task.*
import java.util.Random

class TaskEligibilityGene(allele: TaskEligibilityPolicy?) : StagePolicyGene<TaskEligibilityPolicy>(allele) {
    private val choices: List<(Random) -> TaskEligibilityPolicy> = listOf(
        { _: Random -> NullTaskEligibilityPolicy },
        { random: Random -> LimitTaskEligibilityPolicy(1 + random.nextInt(1000)) },
        { random: Random -> LimitPerJobTaskEligibilityPolicy(1 + random.nextInt(100)) },
        { random: Random -> BalancingTaskEligibilityPolicy(1.01 + 0.99 * random.nextDouble()) },
        { random: Random -> RandomTaskEligibilityPolicy(0.01 + 0.99 * random.nextDouble()) }
    )

    override fun newInstance(): StagePolicyGene<TaskEligibilityPolicy> {
        val random = RandomRegistry.getRandom()
        return TaskEligibilityGene(choices[random.nextInt(choices.size)](random))
    }

    override fun newInstance(value: TaskEligibilityPolicy?): StagePolicyGene<TaskEligibilityPolicy> = TaskEligibilityGene(value)
}
