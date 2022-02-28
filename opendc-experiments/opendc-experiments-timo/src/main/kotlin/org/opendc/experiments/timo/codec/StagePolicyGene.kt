package org.opendc.experiments.timo.codec

import java.util.Objects
import io.jenetics.Gene
import org.opendc.workflow.service.scheduler.StagePolicy

abstract class StagePolicyGene<T : StagePolicy<*>>(private val allele: T? = null) : Gene<T, StagePolicyGene<T>> {
    override fun getAllele(): T? = allele

    override fun isValid(): Boolean = allele != null

    override fun equals(other: Any?): Boolean = other is StagePolicyGene<*> && allele == other.allele

    override fun hashCode(): Int = Objects.hash(allele, StagePolicyGene::class)

    override fun toString(): String = allele.toString()
}

