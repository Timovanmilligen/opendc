package org.opendc.experiments.timo.util

import java.util.Random
import kotlin.math.ceil
import kotlin.math.ln1p
import kotlin.math.max

internal class GeometricDistribution(probability: Double) {
    private val log1mProbabilityOfSuccess = ln1p(-probability)

    operator fun invoke(random: Random): Int {
        return when (val trial = random.nextDouble()) {
            1.0 -> Integer.MAX_VALUE
            0.0 -> 1
            else -> 1 + max(0, ceil(ln1p(-trial) / log1mProbabilityOfSuccess - 1).toInt())
        }
    }
}
