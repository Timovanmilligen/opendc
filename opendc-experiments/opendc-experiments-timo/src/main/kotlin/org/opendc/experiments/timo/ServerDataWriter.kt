package org.opendc.experiments.timo

import com.typesafe.config.ConfigFactory
import org.opendc.compute.service.SnapshotMetricExporter
import org.opendc.telemetry.compute.ComputeMetricExporter
import org.opendc.telemetry.compute.table.HostTableReader
import org.opendc.telemetry.compute.table.ServerTableReader
import org.opendc.telemetry.compute.table.ServiceData
import org.opendc.telemetry.compute.table.ServiceTableReader
import org.opendc.web.runner.WebComputeMetricExporter
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import java.time.Instant


class ServerDataWriter(fileName : String) : ComputeMetricExporter() {

    private val provisionTimes : MutableMap<String,ProvisionTime> = mutableMapOf()

    /**
     * The configuration to use.
     */

    private val config = ConfigFactory.load().getConfig("opendc.experiments.timo")
    private val writer : BufferedWriter
    private var hostCounter = 0
    private val header = "server_id " +
        "cpu_active " +
        "cpu_lost " +
        "cpu_steal " +
        "cpu_idle " +
        "boot_time " +
        "provision_time"
    init {
        val workingDirectory = Paths.get("").toAbsolutePath().toString()
        val outputPath = config.getString("output-path")
        val file = File("$workingDirectory/$outputPath/$fileName.txt")
        file.createNewFile()
        writer = BufferedWriter(FileWriter(file, false))
        writer.write(header)
        writer.newLine()
    }
    override fun record(reader: HostTableReader) {}

    override fun record(reader: ServerTableReader) {
        //Record provision time per server
        if (reader.bootTime != null) {
            if (reader.provisionTime != null) {
                provisionTimes[reader.server.id] = ProvisionTime(reader.provisionTime!!.toEpochMilli(), reader.bootTime!!.toEpochMilli())
            }
            else{
                provisionTimes[reader.server.id] = ProvisionTime(-1,reader.bootTime!!.toEpochMilli())
            }
        } else if (reader.provisionTime != null) {
            provisionTimes[reader.server.id] = ProvisionTime(reader.provisionTime!!.toEpochMilli(), -1)
        }


        serverMetrics.compute(reader.server.id) { _, prev ->
            AggregateServerMetrics(
                reader.cpuActiveTime + (prev?.cpuActiveTime ?: 0),
                reader.cpuLostTime + (prev?.cpuLostTime ?: 0),
                reader.cpuStealTime + (prev?.cpuStealTime ?: 0),
                reader.cpuIdleTime + (prev?.cpuIdleTime ?: 0)
            )
        }
    }

    override fun record(reader: ServiceTableReader) {}

    fun close() {
        writer.flush()
        writer.close()
    }
    fun writeToFile(){
        serverMetrics.keys.forEach{ serverId ->
            val metrics = serverMetrics[serverId]!!
            val provisionTime = provisionTimes[serverId]
            writer.write(
                "$serverId " +
                "${metrics.cpuActiveTime} " +
                "${metrics.cpuLostTime} " +
                "${metrics.cpuStealTime} " +
                "${metrics.cpuIdleTime} " +
                "${provisionTime?.bootTime} " +
                "${provisionTime?.provisionTime}"
            )
            writer.newLine()
        }
    }
    private var serverMetrics :MutableMap<String,AggregateServerMetrics> = mutableMapOf()

    data class AggregateServerMetrics(
        val cpuActiveTime : Long = 0L,
        val cpuLostTime : Long = 0L,
        val cpuStealTime : Long = 0L,
        val cpuIdleTime : Long = 0L
    )
    data class ProvisionTime(
        val provisionTime : Long,
        val bootTime : Long
    )
}
