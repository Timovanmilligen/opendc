/*
 * Copyright (c) 2022 AtLarge Research
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

package org.opendc.compute.portfolio

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Write a [Snapshot] to a file
 */
public class SnapshotWriter {

    public fun writeSnapshot(snapshot: Snapshot) {
        val workingDirectory = Paths.get("").toAbsolutePath().toString()
        val outputPath = "src/main/resources/snapshots"
        var id = 0
        var directoryPath = "$workingDirectory/$outputPath/snapshot_$id"
        var path = Paths.get(directoryPath)

        while (Files.isDirectory(path)) {
            id++
            directoryPath = "$workingDirectory/$outputPath/snapshot_$id"
            path = Paths.get(directoryPath)
        }

        Files.createDirectory(path)

        writeServers(directoryPath, snapshot)
        writeResult(directoryPath, snapshot.result)

        writeTimestamp(directoryPath, snapshot.time)
    }

    private fun writeResult(directoryPath: String, result: Double) {
        val resultFile = File("$directoryPath/result.txt")
        resultFile.createNewFile()
        val resultWriter = BufferedWriter(FileWriter(resultFile, false))
        resultWriter.write("$result")
        resultWriter.close()
    }

    private fun writeTimestamp(directoryPath: String, timestamp: Long) {
        val timestampFile = File("$directoryPath/timestamp.txt")
        timestampFile.createNewFile()
        val timestampWriter = BufferedWriter(FileWriter(timestampFile, false))
        timestampWriter.write("$timestamp")
        timestampWriter.close()
    }

    /**
     * Write which servers are managed by the compute service.
     */
    private fun writeServers(directoryPath: String, snapshot: Snapshot) {
        val queueFile = File("$directoryPath/servers.txt")
        queueFile.createNewFile()
        val queueWriter = BufferedWriter(FileWriter(queueFile, false))
        for (server in snapshot.servers) {
            writeServer(directoryPath, server)
            queueWriter.write(server.name)
            queueWriter.newLine()
        }
        queueWriter.close()
    }

    /**
     * Write server data to a file named after the server [name].txt.
     */
    private fun writeServer(directoryPath: String, server: Snapshot.ServerData) {
        val serverFile = File("$directoryPath/${server.name}.txt")
        serverFile.createNewFile()
        val serverWriter = BufferedWriter(FileWriter(serverFile, false))
        val trace = server.workload.getTrace()
        serverWriter.write("${server.state} ${trace.size} ${server.workload.getOffset()} ${server.cpuCount} ${server.memorySize} ${server.cpuCapacity} ${server.host}")
        serverWriter.newLine()
        for (i in 0 until trace.size) {
            serverWriter.write("${trace.usageCol[i]} ${trace.timestampCol[i]} ${trace.deadlineCol[i]} ${trace.coresCol[i]}")
            serverWriter.newLine()
        }
        serverWriter.close()
    }
}
