package org.opendc.experiments.timo

import org.opendc.experiments.timo.codec.SchedulerSpecification


/**
 * A portfolio of scheduling policies
 */
class Portfolio {
    val stale = mutableListOf<PortfolioEntry>()
    val poor = mutableListOf<PortfolioEntry>()
    val smart = mutableListOf<PortfolioEntry>()
}

/**
 * A policy entry for a [Portfolio] of policies to be simulated
 */
data class PortfolioEntry(
    var schedulerSpec: SchedulerSpecification,
    var lastPerformance: Long,
    var staleness: Long
)
