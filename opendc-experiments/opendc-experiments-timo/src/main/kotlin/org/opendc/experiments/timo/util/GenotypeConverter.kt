package org.opendc.experiments.timo.util

import io.jenetics.Genotype
import org.opendc.compute.service.scheduler.filters.*
import org.opendc.compute.service.scheduler.weights.*
import org.opendc.experiments.timo.codec.*
import org.opendc.experiments.timo.problems.SchedulerSpecification
import org.opendc.workflow.service.scheduler.job.*
import org.opendc.workflow.service.scheduler.task.*

internal class GenotypeConverter {
    operator fun invoke(gt : Genotype<PolicyGene<Pair<String, Any>>>): SchedulerSpecification {
        val weighers = mutableListOf<HostWeigher>()
        val filters = mutableListOf<HostFilter>(ComputeFilter())
        val gtList = gt.toList()
        val it = gtList.iterator()
        var taskOrderPolicy: TaskOrderPolicy = RandomTaskOrderPolicy
        var taskEligibilityPolicy : TaskEligibilityPolicy = NullTaskEligibilityPolicy
        var jobOrderPolicy: JobOrderPolicy = RandomJobOrderPolicy
        var jobAdmissionPolicy: JobAdmissionPolicy = NullJobAdmissionPolicy
        var schedulingQuantum = 100L
        //Loop over chromosomes in genotype
        while (it.hasNext()) {
            //Chromosome
            val currentChromosome = it.next()
            when (currentChromosome) {
                //HostFilterChromosome
                is HostFilterChromosome -> {
                    val genes = currentChromosome.toList()
                    val geneIterator = genes.iterator()
                    while (geneIterator.hasNext()) {
                        val currentGene = geneIterator.next()
                        val allele = currentGene.allele()!!
                        when (allele.first) {
                            "instanceCountFilter" -> filters.add(InstanceCountFilter(allele.second as Int))
                            "ramFilter" -> filters.add(RamFilter(allele.second as Double))
                            "vCpuFilter" -> filters.add(VCpuFilter(allele.second as Double))
                            else -> filters.add(VCpuCapacityFilter())
                        }
                    }
                }
                //HostWeighingChromosome
                is HostWeighingChromosome -> {
                    val genes = currentChromosome.toList()
                    val geneIterator = genes.iterator()
                    while (geneIterator.hasNext()) {
                        val currentGene = geneIterator.next()
                        val allele = currentGene.allele()!!
                        val multiplier = allele.second as Double
                        when (allele.first) {
                            "coreRamWeigher" -> weighers.add(CoreRamWeigher(multiplier))
                            "ramWeigher" -> weighers.add(RamWeigher(multiplier))
                            "vCpuWeigher" -> weighers.add(VCpuWeigher(multiplier))
                            "vCpuCapacityWeigher" -> weighers.add(VCpuCapacityWeigher(multiplier))
                            else -> weighers.add(InstanceCountWeigher(multiplier))
                        }
                    }
                }
                //J1 Incoming Jobs
                is IncomingJobsChromosome -> {
                    schedulingQuantum = currentChromosome.gene().allele()!!.second as Long
                }
                // Job admission
                is JobAdmissionChromosome -> {
                    jobAdmissionPolicy = currentChromosome.map { convertToJobAdmissionPolicy(it.allele()!!) }.reduce { acc, policy -> CompositeJobAdmissionPolicy(acc, policy) }
                }
                //Job order
                is JobOrderChromosome ->{
                    jobOrderPolicy = currentChromosome.map { convertToJobOrderPolicy(it.allele()!!) }.reduce { acc, policy -> CompositeJobOrderPolicy(acc, policy) }
                }
                //Task eligibility
                is TaskEligibilityChromosome -> {
                    taskEligibilityPolicy = currentChromosome.map { convertToTaskEligibilityPolicy(it.allele()!!) }.reduce { acc, policy -> CompositeTaskEligibilityPolicy(acc, policy) }
                }
                //Task order
                else -> {
                    taskOrderPolicy  = currentChromosome.map { convertToTaskOrderPolicy(it.allele()!!) }.reduce { acc, policy -> CompositeTaskOrderPolicy(acc, policy) }
                }
            }
        }
        filters.addAll(listOf(ComputeFilter(), VCpuFilter(1.0), RamFilter(1.0)))
        return SchedulerSpecification(
            filters = filters,
            weighers = weighers,
            taskOrder = taskOrderPolicy,
            taskEligibility = taskEligibilityPolicy,
            jobOrder = jobOrderPolicy,
            jobAdmission =  jobAdmissionPolicy,
            schedulingQuantum = schedulingQuantum
        )
    }
    private fun convertToJobAdmissionPolicy(allele: Pair<String,Any>?) : JobAdmissionPolicy{
        return when (allele!!.first) {
            "limitJobAdmission" -> LimitJobAdmissionPolicy(allele.second as Int)
            "randomJobAdmission" -> RandomJobAdmissionPolicy(allele.second as Double)
            else -> NullJobAdmissionPolicy
        }
    }

    private fun convertToJobOrderPolicy(allele: Pair<String,Any>?) : JobOrderPolicy{
        return when (allele!!.first) {
            "submissionTimeJobOrder" -> SubmissionTimeJobOrderPolicy(allele.second as Boolean)
            "durationJobOrder" -> DurationJobOrderPolicy(allele.second as Boolean)
            "sizeJobOrder" -> SizeJobOrderPolicy(allele.second as Boolean)
            else -> RandomJobOrderPolicy
        }
    }

    private fun convertToTaskEligibilityPolicy(allele: Pair<String,Any>?) : TaskEligibilityPolicy{
        return when (allele!!.first) {
            "limitTaskEligiblity" -> LimitTaskEligibilityPolicy(allele.second as Int)
            "limitPerJobTaskEligiblity" -> LimitPerJobTaskEligibilityPolicy(allele.second as Int)
            "balancingTaskEligiblity" -> BalancingTaskEligibilityPolicy(allele.second as Double)
            "randomTaskEligiblity" -> RandomTaskEligibilityPolicy(allele.second as Double)
            else -> NullTaskEligibilityPolicy
        }
    }
    private fun convertToTaskOrderPolicy(allele: Pair<String,Any>?) : TaskOrderPolicy{
        val ascending = allele!!.second as Boolean
        return when (allele.first) {
            "submissionTaskOrder" -> SubmissionTimeTaskOrderPolicy(ascending)
            "durationTaskOrder" ->  DurationTaskOrderPolicy(ascending)
            "dependenciesTaskOrder" ->  DependenciesTaskOrderPolicy(ascending)
            "dependentsTaskOrder" ->  DependentsTaskOrderPolicy(ascending)
            "activeTaskOrder" -> ActiveTaskOrderPolicy(ascending)
            "durationHistoryTaskOrder" -> DurationHistoryTaskOrderPolicy(ascending)
            "completionTaskOrder" ->  CompletionTaskOrderPolicy(ascending)
            else -> RandomTaskOrderPolicy
        }
    }
}
