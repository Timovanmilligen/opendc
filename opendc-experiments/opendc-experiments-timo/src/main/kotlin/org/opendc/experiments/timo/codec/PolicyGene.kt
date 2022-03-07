package org.opendc.experiments.timo.codec

import io.jenetics.Gene

abstract class PolicyGene<T: Pair<String,Any>>(private val allele: T? = null) :Gene<T,PolicyGene<T>>
{
    override fun isValid(): Boolean = allele != null

    override fun equals(other: Any?): Boolean = other is PolicyGene<*> && allele == other.allele

    override fun allele(): T? = allele

    override fun toString(): String = allele.toString()
}
