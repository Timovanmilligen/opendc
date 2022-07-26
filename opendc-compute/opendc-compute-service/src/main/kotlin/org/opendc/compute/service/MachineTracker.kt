package org.opendc.compute.service

import org.opendc.compute.service.driver.Host
import org.opendc.compute.service.internal.HostView
import org.opendc.simulator.compute.SimBareMetalMachine
import java.util.UUID

public interface MachineTracker {

    public val hostsToMachine: MutableMap<UUID, SimBareMetalMachine>

    /**
     * Link a machine to a host so current rates can be tracked.
     */
    public fun addMachine(host: HostView, machine: SimBareMetalMachine) {
        hostsToMachine[host.uid] = machine
    }

    public fun getCpuDemand(host: HostView) : Double{
        //Assumes all cpus in a machine have the same capacity
        return host.provisionedCores * (host.host.model.cpuCapacity/host.host.model.cpuCount)
    }

    public fun getCpuUsage(host: HostView) : Double{
        var usage = 0.0
        hostsToMachine[host.uid]?.cpus?.forEach { cpu ->
            usage += cpu.rate
        }
        return usage
    }
}
