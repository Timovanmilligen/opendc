package org.opendc.experiments.timo.codec

import io.jenetics.util.RandomRegistry
import org.opendc.workflow.service.scheduler.job.*
import java.util.Random

class JobOrderGene(allele: JobOrderPolicy?) : StagePolicyGene<JobOrderPolicy>(allele) {
    private val choices: List<(Random) -> JobOrderPolicy> = listOf(
        { random: Random -> SubmissionTimeJobOrderPolicy(random.nextBoolean()) },
        { random: Random ->  DurationJobOrderPolicy(random.nextBoolean()) },
        { random: Random -> SizeJobOrderPolicy(random.nextBoolean()) },
        { _: Random -> RandomJobOrderPolicy }
    )

    override fun newInstance(): StagePolicyGene<JobOrderPolicy> {
        val random = RandomRegistry.getRandom()
        return JobOrderGene(choices[random.nextInt(choices.size)](random))
    }

    override fun newInstance(value: JobOrderPolicy?): StagePolicyGene<JobOrderPolicy> = JobOrderGene(value)
}
