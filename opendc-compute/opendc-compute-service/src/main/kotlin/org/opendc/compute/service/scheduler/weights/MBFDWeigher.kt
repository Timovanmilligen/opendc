package org.opendc.compute.service.scheduler.weights

import org.opendc.compute.api.Server
import org.opendc.compute.service.MachineTracker
import org.opendc.compute.service.internal.HostView
import org.opendc.simulator.compute.SimBareMetalMachine
import org.opendc.simulator.compute.power.InterpolationPowerModel
import org.opendc.simulator.compute.workload.SimTraceWorkload
import java.util.UUID

/**
 * A [HostWeigher] that weighs the hosts based on the estimated power increase when scheduling a server to a host.
 *
 * @param multiplier Weight multiplier ratio. A positive value will result in the scheduler preferring hosts with a larger power increasepackage org.opendc.compute.service.scheduler.weights
 * , and a negative number will result in the scheduler preferring hosts with less of a power increase.
 **/
public class MBFDWeigher(override val multiplier: Double = -1.0) : HostWeigher, MachineTracker {

    override val hostsToMachine: MutableMap<UUID, SimBareMetalMachine> = mutableMapOf()

    /**
     * Add a machine to a host so current cpu rates can be tracked.
     */
    public override fun addMachine(host: HostView, machine: SimBareMetalMachine) {
        super.addMachine(host, machine)
    }

    public override fun getCpuDemand(host: HostView): Double {
        return super.getCpuDemand(host)
    }

    public override fun getCpuUsage(host: HostView): Double {
        return super.getCpuUsage(host)
    }

    override fun getWeight(host: HostView, server: Server): Double {
        return powerDifference(host, server)
    }
    private fun powerDifference(host: HostView, server: Server): Double {
        val ibm = listOf(58.4, 98.0, 109.0, 118.0, 128.0, 140.0, 153.0, 170.0, 189.0, 205.0, 222.0)
        val powerModel = InterpolationPowerModel(ibm)
        val currentPower = powerModel.computePower(getCpuUsage(host) / host.host.model.cpuCapacity)
        val averageCpuUsage = (server.meta["workload"] as SimTraceWorkload).getAverageCpuLoad()
        val futurePower = powerModel.computePower((getCpuUsage(host) + averageCpuUsage) / host.host.model.cpuCapacity)
        return futurePower - currentPower
    }

    override fun toString(): String = "MBFDWeigher[multiplier=$multiplier]"
}
