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

import org.opendc.compute.api.ServerState
import org.opendc.simulator.compute.workload.SimTrace
import org.opendc.simulator.compute.workload.SimTraceWorkload
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.nio.file.Paths
import java.util.*

public class SnapshotParser(private val folderName: String) {

    public fun loadSnapshot(id: Int): Snapshot {
        val workingDirectory = Paths.get("").toAbsolutePath().toString()
        val directoryPath = "$workingDirectory/src/main/resources/snapshots/$folderName/snapshot_$id"
        return Snapshot(
            readServers(directoryPath),
            readResult(directoryPath),
            readTimestamp(directoryPath)
        )
    }

    private fun readResult(directoryPath: String): Double {
        var result = 0.0
        val resultFile = File("$directoryPath/result.txt")
        try {
            BufferedReader(FileReader(resultFile)).use { br ->
                result = br.readLine().toDouble()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return result
    }

    private fun readTimestamp(directoryPath: String): Long {
        var timestamp = 0L
        val timestampFile = File("$directoryPath/timestamp.txt")
        try {
            BufferedReader(FileReader(timestampFile)).use { br ->
                timestamp = br.readLine().toLong()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return timestamp
    }

    private fun readServers(directoryPath: String): List<Snapshot.ServerData> {
        val queue: MutableList<Snapshot.ServerData> = mutableListOf()
        val queueFile = File("$directoryPath/servers.txt")
        try {
            BufferedReader(FileReader(queueFile)).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    queue.add(readServer(directoryPath, line!!))
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return queue
    }

    private fun readServer(directoryPath: String, name: String): Snapshot.ServerData {
        val serverFile = File("$directoryPath/$name.txt")
        try {
            BufferedReader(FileReader(serverFile)).use { br ->
                var line: String?
                val firstLineSplit = br.readLine().split(' ')
                val state = ServerState.valueOf(firstLineSplit[0])
                val size = firstLineSplit[1].toInt()
                val offset = firstLineSplit[2].toLong()
                val cpuCount = firstLineSplit[3].toInt()
                val memorySize = firstLineSplit[4].toLong()
                val cpuCapacity = firstLineSplit[5].toDouble()
                val host = if (firstLineSplit[6] != "null") UUID.fromString(firstLineSplit[5]) else null
                val usageCol = DoubleArray(size)
                val timestampCol = LongArray(size)
                val deadlineCol = LongArray(size)
                val coresCol = IntArray(size)
                var index = 0
                while (br.readLine().also { line = it } != null) {
                    val lineSplit = line!!.split(' ')
                    usageCol[index] = lineSplit[0].toDouble()
                    timestampCol[index] = lineSplit[1].toLong()
                    deadlineCol[index] = lineSplit[2].toLong()
                    coresCol[index] = lineSplit[3].toInt()
                    index++
                }
                val workload = SimTraceWorkload(SimTrace(usageCol, timestampCol, deadlineCol, coresCol, size), offset)
                return Snapshot.ServerData(name, state, workload, cpuCount, memorySize, cpuCapacity, host)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw IllegalArgumentException("Failed reading server: $name")
        }
    }

}
