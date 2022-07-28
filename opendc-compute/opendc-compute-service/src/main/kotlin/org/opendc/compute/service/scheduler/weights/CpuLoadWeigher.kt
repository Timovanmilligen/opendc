package org.opendc.compute.service.scheduler.weights

import org.opendc.compute.api.Server
import org.opendc.compute.service.MachineTracker
import org.opendc.compute.service.driver.Host
import org.opendc.compute.service.internal.HostView
import org.opendc.simulator.compute.SimBareMetalMachine
import java.util.UUID

/**
 * A [HostWeigher] that weighs the hosts based on the cpu utilization of the host.
 *
 * @param multiplier Weight multiplier ratio. A positive value will result in the scheduler preferring hosts with a larger cpu utilization
 * , and a negative number will result in the scheduler preferring hosts with less cpu utilization.
 */
public class CpuLoadWeigher(override val multiplier: Double = -1.0) : HostWeigher, MachineTracker {


    override val hostsToMachine: MutableMap<UUID, SimBareMetalMachine> = mutableMapOf()

    /**
     * Add a machine to a host so current cpu rates can be tracked.
     */
    public override fun addMachine(host: HostView, machine: SimBareMetalMachine) {
        super.addMachine(host, machine)
    }

    public override fun getCpuUsage(host: HostView): Double {
        return super.getCpuUsage(host)
    }

    override fun getWeight(host: HostView, server: Server): Double {
        return getCpuUsage(host)/host.host.model.cpuCapacity
    }

    override fun toString(): String = "CpuLoadWeigher[multiplier=$multiplier]"
}
