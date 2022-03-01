package org.opendc.experiments.timo.util

import io.jenetics.internal.math.base
import java.util.Random

fun Random.normalInt(value: Int, min: Int, max: Int): Int {
    val std = (max - min)*0.25;
    val gaussian = nextGaussian()
    return base.clamp(gaussian * std + value, min.toDouble(), max.toDouble()).toInt()
}

fun Random.normal(value: Double, min: Double, max: Double): Double {
    val std = (max - min)*0.25;
    val gaussian = nextGaussian()
    return base.clamp(gaussian * std + value, min, max)
}
