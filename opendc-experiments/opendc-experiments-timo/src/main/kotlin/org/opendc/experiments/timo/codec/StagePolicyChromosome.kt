package org.opendc.experiments.timo.codec

import io.jenetics.Chromosome
import io.jenetics.util.ISeq
import org.opendc.workflow.service.scheduler.StagePolicy

abstract class StagePolicyChromosome<T : StagePolicy<*>>(private val genes: ISeq<StagePolicyGene<T>>) : Chromosome<StagePolicyGene<T>> {
    override fun isValid(): Boolean = genes.all { it.isValid }

    override fun getGene(index: Int): StagePolicyGene<T> = genes[index]

    override fun iterator(): MutableIterator<StagePolicyGene<T>> = genes.iterator()

    override fun length(): Int = genes.length()

    override fun toSeq(): ISeq<StagePolicyGene<T>> = genes

    override fun toString(): String = genes.toString()
}

