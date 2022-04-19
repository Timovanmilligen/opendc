package org.opendc.compute.service.scheduler

import java.rmi.server.UID
import java.util.*
import javax.sound.sampled.Port

/**
 * A portfolio of scheduling policies
 */
public class Portfolio {
    public val stale: MutableList<PortfolioEntry> = mutableListOf()
    public val poor: MutableList<PortfolioEntry> = mutableListOf()
    public val smart: MutableList<PortfolioEntry> = mutableListOf()

    public fun addEntry(entry: PortfolioEntry){
        smart.add(entry)
    }
}

/**
 * A policy entry for a [Portfolio] of policies to be simulated
 */
public data class PortfolioEntry(
    var scheduler: ComputeScheduler,
    var lastPerformance: Long,
    var staleness: Long
)
