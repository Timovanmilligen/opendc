package org.opendc.experiments.timo.codec

import io.jenetics.util.RandomRegistry
import org.opendc.workflow.service.scheduler.job.JobAdmissionPolicy
import org.opendc.workflow.service.scheduler.job.LimitJobAdmissionPolicy
import org.opendc.workflow.service.scheduler.job.NullJobAdmissionPolicy
import org.opendc.workflow.service.scheduler.job.RandomJobAdmissionPolicy
import java.util.Random


class JobAdmissionGene(allele: JobAdmissionPolicy?) : StagePolicyGene<JobAdmissionPolicy>(allele) {
    private val choices: List<(Random) -> JobAdmissionPolicy> = listOf(
        { _: Random -> NullJobAdmissionPolicy },
        { random: Random -> LimitJobAdmissionPolicy(1 + random.nextInt(1000)) },
        { random: Random -> RandomJobAdmissionPolicy(0.01 + 0.99 * random.nextDouble()) }
    )

    override fun newInstance(): StagePolicyGene<JobAdmissionPolicy> {
        val random = RandomRegistry.getRandom()
        return JobAdmissionGene(choices[random.nextInt(choices.size)](random))
    }

    override fun newInstance(value: JobAdmissionPolicy?): StagePolicyGene<JobAdmissionPolicy> = JobAdmissionGene(value)
}
