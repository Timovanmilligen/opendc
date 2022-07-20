package org.opendc.experiments.timo

import com.typesafe.config.ConfigFactory
import org.opendc.compute.service.SnapshotMetricExporter
import org.opendc.telemetry.compute.ComputeMetricExporter
import org.opendc.telemetry.compute.table.HostTableReader
import org.opendc.telemetry.compute.table.ServerTableReader
import org.opendc.telemetry.compute.table.ServiceData
import org.opendc.telemetry.compute.table.ServiceTableReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import java.time.Instant


class MainTraceDataWriter(private val fileName : String, private var hostCount : Int) : ComputeMetricExporter() {
    /**
     * The configuration to use.
     */
    private val config = ConfigFactory.load().getConfig("opendc.experiments.timo")
    private val writer : BufferedWriter
    private var hostCounter = 0
    private val header = "Time_minutes " +
        "Average_cpu_utilization " +
        "intermediate_powerdraw_kJ " +
        "total_powerdraw_kJ " +
        "cpu_demand " +
        "cpu_usage " +
        "cpu_idle_time " +
        "overall_power_efficiency " +
        "intermediate_power_efficiency " +
        "hosts_up " +
        "hosts_down " +
        "servers_active " +
        "servers_pending " +
        "attemps_success " +
        "attempts_failure " +
        "attemps_error"
    init {
        val workingDirectory = Paths.get("").toAbsolutePath().toString()
        val outputPath = config.getString("output-path")
        val file = File("$workingDirectory/$outputPath/$fileName.txt")
        file.createNewFile()
        writer = BufferedWriter(FileWriter(file, false))
        writer.write(header)
        writer.newLine()
    }
    override fun record(reader: HostTableReader) {
        val slices = reader.downtime / SLICE_LENGTH

        hostCounter++
        //Write intermediate data to file and reset metrics if all hosts have recorded data this timestamp.
        if (hostCounter == hostCount){
            writeToFile(reader.timestamp.toEpochMilli())
            resetMetrics()
        }
        aggregateHostMetrics = AggregateHostMetrics(
            aggregateHostMetrics.totalActiveTime + reader.cpuActiveTime,
            aggregateHostMetrics.totalIdleTime + reader.cpuIdleTime,
            aggregateHostMetrics.totalStealTime + reader.cpuStealTime,
            aggregateHostMetrics.totalLostTime + reader.cpuLostTime,
            aggregateHostMetrics.totalPowerDraw + reader.powerTotal,
            aggregateHostMetrics.totalFailureSlices + slices,
            aggregateHostMetrics.totalFailureVmSlices + reader.guestsRunning * slices,
            aggregateHostMetrics.cpuDemand + reader.cpuDemand,
            aggregateHostMetrics.cpuUsage + reader.cpuUsage
        )
        intermediateAggregateHostMetrics = AggregateHostMetrics(
            intermediateAggregateHostMetrics.totalActiveTime + reader.cpuActiveTime,
            intermediateAggregateHostMetrics.totalIdleTime + reader.cpuIdleTime,
            intermediateAggregateHostMetrics.totalStealTime + reader.cpuStealTime,
            intermediateAggregateHostMetrics.totalLostTime + reader.cpuLostTime,
            intermediateAggregateHostMetrics.totalPowerDraw + reader.powerTotal,
            intermediateAggregateHostMetrics.totalFailureSlices + slices,
            intermediateAggregateHostMetrics.totalFailureVmSlices + reader.guestsRunning * slices,
            intermediateAggregateHostMetrics.cpuDemand + reader.cpuDemand,
            intermediateAggregateHostMetrics.cpuUsage + reader.cpuUsage
        )

        intermediateHostMetrics.compute(reader.host.id) { _, prev ->
            IntermediateHostMetrics(
                reader.cpuUtilization + (prev?.cpuUtilization ?: 0.0),
                reader.cpuUsage + (prev?.cpuUsage ?: 0.0),
                reader.cpuDemand + (prev?.cpuDemand ?: 0.0),
                reader.guestsRunning + (prev?.instanceCount ?: 0)
            )
        }
    }

    private fun writeToFile(timestamp : Long){
        val averageCpuUtilization = intermediateHostMetrics.values.map { it.cpuUtilization }.average()
        val serviceMetricString = "${serviceMetrics.hostsUp} " +
            "${serviceMetrics.hostsDown} " +
            "${serviceMetrics.serversActive} " +
            "${serviceMetrics.serversPending} " +
            "${serviceMetrics.attemptsSuccess} " +
            "${serviceMetrics.attemptsFailure} " +
            "${serviceMetrics.attemptsError}"
        writer.write("${timestamp/60000} $averageCpuUtilization ${intermediateAggregateHostMetrics.totalPowerDraw/1000} ${aggregateHostMetrics.totalPowerDraw/1000} ${intermediateAggregateHostMetrics.cpuDemand} " +
            "${intermediateAggregateHostMetrics.cpuUsage} ${aggregateHostMetrics.totalIdleTime} ${aggregateHostMetrics.cpuUsage/(aggregateHostMetrics.totalPowerDraw/1000)} " +
            "${intermediateAggregateHostMetrics.cpuUsage/(intermediateAggregateHostMetrics.totalPowerDraw/1000)} " +
        serviceMetricString)
        writer.newLine()
    }
    private fun resetMetrics(){
        intermediateHostMetrics.keys.forEach {key ->
            intermediateHostMetrics[key] = IntermediateHostMetrics(0.0,0.0,0.0,0)
        }
        intermediateAggregateHostMetrics = AggregateHostMetrics()
        hostCounter = 0
    }
    override fun record(reader: ServerTableReader) {

    }

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
    fun close() {
        writer.flush()
        writer.close()
    }
    private val intermediateHostMetrics: MutableMap<String, IntermediateHostMetrics> = mutableMapOf()
    private var intermediateAggregateHostMetrics = AggregateHostMetrics()
    private var aggregateHostMetrics = AggregateHostMetrics()
    private val SLICE_LENGTH: Long = 5 * 60L

    data class IntermediateHostMetrics (
        val cpuUtilization: Double,
        val cpuUsage: Double,
        val cpuDemand: Double,
        val instanceCount: Long
    )
    data class AggregateHostMetrics(
        val totalActiveTime: Long = 0L,
        val totalIdleTime: Long = 0L,
        val totalStealTime: Long = 0L,
        val totalLostTime: Long = 0L,
        val totalPowerDraw: Double = 0.0,
        val totalFailureSlices: Double = 0.0,
        val totalFailureVmSlices: Double = 0.0,
        val cpuDemand: Double = 0.0,
        val cpuUsage : Double = 0.0
    )
}
