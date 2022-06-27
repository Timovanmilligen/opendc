package org.opendc.experiments.timo

import com.typesafe.config.ConfigFactory
import org.opendc.compute.service.SnapshotMetricExporter
import org.opendc.telemetry.compute.ComputeMetricExporter
import org.opendc.telemetry.compute.table.HostTableReader
import org.opendc.telemetry.compute.table.ServerTableReader
import org.opendc.telemetry.compute.table.ServiceTableReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths


class MainTraceDataWriter(private val fileName : String, private var hostCount : Int) : ComputeMetricExporter() {
    /**
     * The configuration to use.
     */
    private val config = ConfigFactory.load().getConfig("opendc.experiments.timo")
    private val writer : BufferedWriter
    private var hostCounter = 0
    private val columnNamesString = "Time_minutes Average_cpu_utilization Powerdraw_kJ"
    init {
        val workingDirectory = Paths.get("").toAbsolutePath().toString()
        val outputPath = config.getString("output-path")
        val file = File("$workingDirectory/$outputPath/$fileName")
        file.createNewFile()
        writer = BufferedWriter(FileWriter(file, false))
        writer.write(columnNamesString)
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
        intermediateAggregateHostMetrics = IntermediateAggregateHostMetrics(
            intermediateAggregateHostMetrics.totalActiveTime + reader.cpuActiveTime,
            intermediateAggregateHostMetrics.totalIdleTime + reader.cpuIdleTime,
            intermediateAggregateHostMetrics.totalStealTime + reader.cpuStealTime,
            intermediateAggregateHostMetrics.totalLostTime + reader.cpuLostTime,
            intermediateAggregateHostMetrics.totalPowerDraw + reader.powerTotal,
            intermediateAggregateHostMetrics.totalFailureSlices + slices,
            intermediateAggregateHostMetrics.totalFailureVmSlices + reader.guestsRunning * slices
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
        writer.write("${timestamp/60000} $averageCpuUtilization ${intermediateAggregateHostMetrics.totalPowerDraw/1000}")
        writer.newLine()
    }
    private fun resetMetrics(){
        intermediateHostMetrics.keys.forEach {key ->
            intermediateHostMetrics[key] = IntermediateHostMetrics(0.0,0.0,0.0,0)
        }
        intermediateAggregateHostMetrics = IntermediateAggregateHostMetrics()
        hostCounter = 0
    }
    override fun record(reader: ServerTableReader) {

    }

    override fun record(reader: ServiceTableReader) {

    }
    fun close() {
        writer.flush()
        writer.close()
    }
    private val intermediateHostMetrics: MutableMap<String, IntermediateHostMetrics> = mutableMapOf()
    private var intermediateAggregateHostMetrics = IntermediateAggregateHostMetrics()
    private val SLICE_LENGTH: Long = 5 * 60L

    data class IntermediateHostMetrics (
        val cpuUtilization: Double,
        val cpuUsage: Double,
        val cpuDemand: Double,
        val instanceCount: Long
    )
    data class IntermediateAggregateHostMetrics(
        val totalActiveTime: Long = 0L,
        val totalIdleTime: Long = 0L,
        val totalStealTime: Long = 0L,
        val totalLostTime: Long = 0L,
        val totalPowerDraw: Double = 0.0,
        val totalFailureSlices: Double = 0.0,
        val totalFailureVmSlices: Double = 0.0,
    )
}
