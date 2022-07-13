package org.opendc.compute.service

import org.opendc.telemetry.compute.ComputeMetricExporter
import org.opendc.telemetry.compute.ComputeMonitor
import org.opendc.telemetry.compute.table.HostTableReader
import org.opendc.telemetry.compute.table.ServiceData
import org.opendc.telemetry.compute.table.ServiceTableReader
import java.security.Provider.Service
import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * A [ComputeMonitor] that tracks the aggregate metrics for each repeat.
 */
public class SnapshotMetricExporter : ComputeMetricExporter() {
    override fun record(reader: HostTableReader) {
        val slices = reader.downtime / SLICE_LENGTH
        val kek = reader.timestamp.toEpochMilli()
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
    }

    private var hostAggregateMetrics: AggregateHostMetrics = AggregateHostMetrics()
    private val hostMetrics: MutableMap<String, HostMetrics> = mutableMapOf()
    private val SLICE_LENGTH: Long = 5 * 60L

    public data class AggregateHostMetrics(
        val totalActiveTime: Long = 0L,
        val totalIdleTime: Long = 0L,
        val totalStealTime: Long = 0L,
        val totalLostTime: Long = 0L,
        val totalPowerDraw: Double = 0.0,
        val totalFailureSlices: Double = 0.0,
        val totalFailureVmSlices: Double = 0.0
    )

    public data class HostMetrics(
        val cpuUsage: Double,
        val cpuDemand: Double,
        val instanceCount: Long,
        val count: Long
    )

    private var serviceMetrics: ServiceData = ServiceData(Instant.ofEpochMilli(0), 0, 0, 0, 0, 0, 0, 0)

    override fun record(reader: ServiceTableReader) {
         serviceMetrics = ServiceData(
            reader.timestamp,
            reader.hostsUp,
            reader.hostsDown,
            reader.serversPending,
            reader.serversActive,
            reader.attemptsSuccess,
            reader.attemptsFailure,
            reader.attemptsError
        )
    }

    public fun getResult(): Result {
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
            serviceMetrics.attemptsSuccess,
            serviceMetrics.attemptsFailure,
            serviceMetrics.attemptsError,
            serviceMetrics.serversPending,
            serviceMetrics.serversActive,
            hostMetrics.map{it.value.cpuUsage}.sum().div(hostAggregateMetrics.totalPowerDraw/1000)
        )
    }

    public data class Result(
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
        val attemptsSuccess : Int,
        val attemptsFailure: Int,
        val attemptsError: Int,
        val serversPending: Int,
        val serversActive: Int,
        val hostEnergyEfficiency: Double
    )
}
