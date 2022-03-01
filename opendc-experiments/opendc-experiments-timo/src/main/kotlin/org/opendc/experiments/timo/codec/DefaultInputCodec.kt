package org.opendc.experiments.timo.codec

import io.jenetics.EnumGene
import io.jenetics.Genotype
import io.jenetics.PermutationChromosome
import io.jenetics.engine.Codec
import io.jenetics.util.ISeq
import org.opendc.experiments.timo.input.Input

/**
 * An [InputCodec] that encodes the values of the input as [EnumGene].
 */
object DefaultInputCodec : InputCodec<Any?> {
    override fun build(input: Input): Codec<Any?, *> {
        val gtf = { Genotype.of(PermutationChromosome.of(ISeq.of(input.values), 1)) }
        return Codec.of(gtf) { input.converter.fromString(it.gene.allele, input.type) }
    }
}
