package org.opendc.compute.service.scheduler.weights

import org.opendc.compute.api.Server
import org.opendc.compute.service.internal.HostView

/**
 * A [HostWeigher] that weighs the hosts based on the gap between requested and actually used resources on the host.
 *
 * @param multiplier Weight multiplier ratio. A positive value will result in the scheduler preferring hosts with a larger gap
 * , and a negative number will result in the scheduler preferring hosts with less of a gap.
 */
public class MCLWeigher(override val multiplier: Double = 1.0) : HostWeigher {
    override fun getWeight(host: HostView, server: Server): Double {
        val cpuStats = host.host.getCpuStats()
        return cpuStats.demand - cpuStats.usage
    }

    override fun toString(): String = "MaximumConsolidationLoad[multiplier=$multiplier]"
}
