/*
 * Copyright (c) 2021 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.experiments.timo

import org.opendc.compute.service.internal.HostView
import org.opendc.telemetry.compute.ComputeMetricExporter
import org.opendc.telemetry.compute.ComputeMonitor
import org.opendc.telemetry.compute.table.HostInfo
import org.opendc.telemetry.compute.table.HostTableReader
import org.opendc.telemetry.compute.table.ServerTableReader
import org.opendc.telemetry.compute.table.ServiceTableReader
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * A [ComputeMonitor] that tracks the aggregate metrics for each repeat.
 */
class ClusterComputeMetricExporter : ComputeMetricExporter() {

    var liveHostMetrics : MutableMap<String, HostMetrics> = mutableMapOf()
    var cpuReady = mutableMapOf<String,Long>()
    var prevCpuReady = mutableMapOf<String,Long>()
    var beforePrevCpuReady = mutableMapOf<String,Long>()
    var demandedCpuOfServerHost = mutableMapOf<String,Double>()
    var vCpuCores = mutableMapOf<String,Int>()

    override fun record(reader: HostTableReader) {
        val slices = reader.downtime / SLICE_LENGTH
        //println("host: ${reader.host.name},cpu count: ${reader.host.cpuCount} cpu demand: ${reader.cpuDemand}, cpu capacity: ${reader.cpuLimit}, cpu usage: ${reader.cpuUsage}, cpu utilization: ${reader.cpuUtilization}")
        hostAggregateMetrics = AggregateHostMetrics(
            hostAggregateMetrics.totalActiveTime + reader.cpuActiveTime,
            hostAggregateMetrics.totalIdleTime + reader.cpuIdleTime,
            hostAggregateMetrics.totalStealTime + reader.cpuStealTime,
            hostAggregateMetrics.totalLostTime + reader.cpuLostTime,
            hostAggregateMetrics.totalPowerDraw + reader.powerTotal,
            hostAggregateMetrics.totalFailureSlices + slices,
            hostAggregateMetrics.totalFailureVmSlices + reader.guestsRunning * slices
        )

        hostMetrics.compute(reader.host.id) { _, prev ->
            HostMetrics(
                reader.cpuUsage + (prev?.cpuUsage ?: 0.0),
                reader.cpuDemand + (prev?.cpuDemand ?: 0.0),
                reader.guestsRunning + (prev?.instanceCount ?: 0),
                1 + (prev?.count ?: 0)
            )
        }

        liveHostMetrics[reader.host.id] = HostMetrics(reader.cpuUsage,reader.cpuDemand,reader.guestsRunning.toLong(),1+ (liveHostMetrics[reader.host.id]?.count?:0))
    }
    override fun record(reader: ServerTableReader) {
        //Record number of vCpu cores of the server at time t.
        vCpuCores[reader.server.id] = reader.server.cpuCount

        //record t - 2 cpuReady
        beforePrevCpuReady.compute(reader.server.id){id, _ ->
            prevCpuReady[id] ?: 0L
        }
        //record t - 1 cpuReady
        prevCpuReady.compute(reader.server.id) { id ,_ ->
            cpuReady[id] ?: 0L
        }
        //record t cpuReady
        cpuReady[reader.server.id] = reader.cpuStealTime

        //Record cpu demand of host that hosts this server.
        demandedCpuOfServerHost[reader.server.id] = this.liveHostMetrics[reader.host?.id]?.cpuDemand ?: 0.0

        //println("Server: ${reader.server.name}, Host: ${reader.host?.name}, cpu count: ${reader.server.cpuCount}, host cpu count: ${reader.host?.cpuCount} cpu limit: ${reader.cpuLimit}, host usage ${liveHostMetrics[reader.host?.id]?.cpuUsage}, host instanceCount ${liveHostMetrics[reader.host?.id]?.instanceCount}")
    }

    data class ServerMetrics(
        val cpuUsage: Double,
        val cpuDemand: Int,
        val instanceCount: Long,
        val count: Long
    )
    /**
     * Returns the cluster name of a host. Assumes host name format: 'node-<clustername>-<hostnumber>'. Example: 'node-A01-3'
     * where A01 is the cluster name.
     */
    fun getClusterName(hostInfo: HostInfo) : String{
        return hostInfo.name.split("-")[1]
    }

    private val serverMetrics: MutableMap<String, ServerMetrics> = mutableMapOf()
    private var hostAggregateMetrics: AggregateHostMetrics = AggregateHostMetrics()
    private val hostMetrics: MutableMap<String, HostMetrics> = mutableMapOf()
    private val SLICE_LENGTH: Long = 5 * 60L

    data class AggregateHostMetrics(
        val totalActiveTime: Long = 0L,
        val totalIdleTime: Long = 0L,
        val totalStealTime: Long = 0L,
        val totalLostTime: Long = 0L,
        val totalPowerDraw: Double = 0.0,
        val totalFailureSlices: Double = 0.0,
        val totalFailureVmSlices: Double = 0.0,
    )

    data class HostMetrics(
        val cpuUsage: Double,
        val cpuDemand: Double,
        val instanceCount: Long,
        val count: Long
    )

    private var serviceMetrics: AggregateServiceMetrics = AggregateServiceMetrics()

    override fun record(reader: ServiceTableReader) {
        serviceMetrics = AggregateServiceMetrics(
            max(reader.attemptsSuccess, serviceMetrics.vmTotalCount),
            max(reader.serversPending, serviceMetrics.vmWaitingCount),
            max(reader.serversActive, serviceMetrics.vmActiveCount),
            max(0, serviceMetrics.vmInactiveCount),
            max(reader.attemptsFailure, serviceMetrics.vmFailedCount),
        )
    }

    data class AggregateServiceMetrics(
        val vmTotalCount: Int = 0,
        val vmWaitingCount: Int = 0,
        val vmActiveCount: Int = 0,
        val vmInactiveCount: Int = 0,
        val vmFailedCount: Int = 0
    )

    fun getResult(): Result {
        return Result(
            hostAggregateMetrics.totalActiveTime,
            hostAggregateMetrics.totalIdleTime,
            hostAggregateMetrics.totalStealTime,
            hostAggregateMetrics.totalLostTime,
            hostMetrics.map { it.value.cpuUsage / it.value.count }.average(),
            hostMetrics.map { it.value.cpuDemand / it.value.count }.average(),
            hostMetrics.map { it.value.instanceCount.toDouble() / it.value.count }.average(),
            hostMetrics.map { it.value.instanceCount.toDouble() / it.value.count }.maxOrNull() ?: 0.0,
            hostAggregateMetrics.totalPowerDraw,
            hostAggregateMetrics.totalFailureSlices.roundToLong(),
            hostAggregateMetrics.totalFailureVmSlices.roundToLong(),
            serviceMetrics.vmTotalCount,
            serviceMetrics.vmWaitingCount,
            serviceMetrics.vmInactiveCount,
            serviceMetrics.vmFailedCount,
        )
    }

    data class Result(
        val totalActiveTime: Long,
        val totalIdleTime: Long,
        val totalStealTime: Long,
        val totalLostTime: Long,
        val meanCpuUsage: Double,
        val meanCpuDemand: Double,
        val meanNumDeployedImages: Double,
        val maxNumDeployedImages: Double,
        val totalPowerDraw: Double,
        val totalFailureSlices: Long,
        val totalFailureVmSlices: Long,
        val totalVmsSubmitted: Int,
        val totalVmsQueued: Int,
        val totalVmsFinished: Int,
        val totalVmsFailed: Int
    )
}
