package org.opendc.experiments.timo.codec

import io.jenetics.util.RandomRegistry
import org.opendc.workflow.service.scheduler.task.*
import java.util.Random

/**
 *
 * @author Fabian Mastenbroek (f.s.mastenbroek@student.tudelft.nl)
 */
class TaskOrderGene(allele: TaskOrderPolicy?) : StagePolicyGene<TaskOrderPolicy>(allele) {
    private val choices: List<(Random) -> TaskOrderPolicy> = listOf(
        { random: Random -> SubmissionTimeTaskOrderPolicy(random.nextBoolean()) },
        { random: Random -> DurationTaskOrderPolicy(random.nextBoolean()) },
        { random: Random -> DependenciesTaskOrderPolicy(random.nextBoolean()) },
        { random: Random -> DependentsTaskOrderPolicy(random.nextBoolean()) },
        { random: Random -> ActiveTaskOrderPolicy(random.nextBoolean()) },
        { random: Random -> DurationHistoryTaskOrderPolicy(random.nextBoolean()) },
        { random: Random -> CompletionTaskOrderPolicy(random.nextBoolean()) },
        { _: Random -> RandomTaskOrderPolicy }
    )

    override fun newInstance(): StagePolicyGene<TaskOrderPolicy> {
        val random = RandomRegistry.getRandom()
        return TaskOrderGene(choices[random.nextInt(choices.size)](random))
    }

    override fun newInstance(value: TaskOrderPolicy?): StagePolicyGene<TaskOrderPolicy> = TaskOrderGene(value)
}
