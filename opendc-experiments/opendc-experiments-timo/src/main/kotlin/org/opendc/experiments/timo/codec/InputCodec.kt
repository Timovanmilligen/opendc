package org.opendc.experiments.timo.codec

import io.jenetics.engine.Codec
import org.opendc.experiments.timo.input.Input

/**
 * An interface for encoding an [Input] into a [Genotype].
 */
interface InputCodec<T> {
    /**
     * Build a [Codec] for the specified [input].
     *
     * @param input The input to build the codec for.
     */
    fun build(input: Input): Codec<T, *>
}
