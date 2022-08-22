package org.opendc.compute.service.scheduler.weights

import org.opendc.compute.api.Server
import org.opendc.compute.service.internal.HostView

/**
 * A [HostWeigher] that weighs the hosts based on the cpu utilization of the host.
 *
 * @param multiplier Weight multiplier ratio. A positive value will result in the scheduler preferring hosts with a larger cpu utilization
 * , and a negative number will result in the scheduler preferring hosts with less cpu utilization.
 */
public class CpuLoadWeigher(override val multiplier: Double = -1.0) : HostWeigher {
    override fun getWeight(host: HostView, server: Server): Double {
        return getCpuUsage(host) / host.host.model.cpuCapacity
    }

    private fun getCpuUsage(view: HostView): Double {
        return view.host.getCpuStats().usage
    }

    override fun toString(): String = "CpuLoadWeigher[multiplier=$multiplier]"
}
