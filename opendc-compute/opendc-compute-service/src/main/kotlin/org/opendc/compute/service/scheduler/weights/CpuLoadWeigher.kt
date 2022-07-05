package org.opendc.compute.service.scheduler.weights

import org.opendc.compute.api.Server
import org.opendc.compute.service.internal.HostView

/**
 * A [HostWeigher] that weighs the hosts based on the remaining number of vCPUs available.
 *
 */
public class CpuLoadWeigher(override val multiplier: Double = -1.0) : HostWeigher {


    override fun getWeight(host: HostView, server: Server): Double {
        return host.provisionedCores * (host.host.model.cpuCapacity/host.host.model.cpuCount)
    }

    override fun toString(): String = "LowestCpuLoad"
}
