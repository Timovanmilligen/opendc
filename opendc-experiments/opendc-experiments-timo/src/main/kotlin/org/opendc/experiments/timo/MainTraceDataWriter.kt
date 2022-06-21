package org.opendc.experiments.timo

import com.typesafe.config.ConfigFactory
import org.opendc.telemetry.compute.ComputeMetricExporter
import org.opendc.telemetry.compute.table.HostTableReader
import org.opendc.telemetry.compute.table.ServerTableReader
import org.opendc.telemetry.compute.table.ServiceTableReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths


class MainTraceDataWriter : ComputeMetricExporter() {
    /**
     * The configuration to use.
     */
    private val config = ConfigFactory.load().getConfig("opendc.experiments.timo")
    private val writer : BufferedWriter
    private val fileName = "main_trace.txt"
    init {
        val workingDirectory = Paths.get("").toAbsolutePath().toString()
        val outputPath = config.getString("output-path")
        val file = File("$workingDirectory/$outputPath/$fileName")
        file.createNewFile()
        writer = BufferedWriter(FileWriter(file, false))
    }
    override fun record(reader: HostTableReader) {
       // writer.write(reader.timestamp.toEpochMilli().toString())
       // writer.newLine()
    }

    override fun record(reader: ServerTableReader) {

    }

    override fun record(reader: ServiceTableReader) {

    }
    fun close() {
        writer.flush()
        writer.close()
    }
}
