package org.opendc.compute.service.scheduler.weights

import org.opendc.compute.api.Server
import org.opendc.compute.service.internal.HostView
import org.opendc.simulator.compute.power.InterpolationPowerModel
import org.opendc.simulator.compute.workload.SimTraceWorkload

/**
 * A [HostWeigher] that weighs the hosts based on the estimated power increase when scheduling a server to a host.
 *
 * @param multiplier Weight multiplier ratio. A positive value will result in the scheduler preferring hosts with a larger power increasepackage org.opendc.compute.service.scheduler.weights
 * , and a negative number will result in the scheduler preferring hosts with less of a power increase.
 **/
public class MBFDWeigher(override val multiplier: Double = -1.0) : HostWeigher {
    override fun getWeight(host: HostView, server: Server): Double {
        val ibm = listOf(58.4, 98.0, 109.0, 118.0, 128.0, 140.0, 153.0, 170.0, 189.0, 205.0, 222.0)
        val cpuUsage = host.host.getCpuStats().usage
        val powerModel = InterpolationPowerModel(ibm)
        val currentPower = powerModel.computePower(cpuUsage / host.host.model.cpuCapacity)
        val averageCpuUsage = (server.meta["workload"] as SimTraceWorkload).getAverageCpuLoad()
        val futurePower = powerModel.computePower((cpuUsage + averageCpuUsage) / host.host.model.cpuCapacity)
        return futurePower - currentPower
    }

    override fun toString(): String = "MBFDWeigher[multiplier=$multiplier]"
}
