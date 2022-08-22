package org.opendc.experiments.timo.codec

import io.jenetics.Chromosome
import io.jenetics.util.ISeq

abstract class PolicyChromosome<T : Pair<String, Any>>(private val genes: ISeq<PolicyGene<T>>) : Chromosome<PolicyGene<T>> {
    override fun isValid(): Boolean = genes.all { it.isValid }

    override fun iterator(): MutableIterator<PolicyGene<T>> = genes.iterator()

    override fun length(): Int = genes.length()

    override fun get(index: Int): PolicyGene<T> {
        return genes[index]
    }
    fun toSeq(): ISeq<PolicyGene<T>> = genes

    override fun toString(): String = genes.toString()
}
