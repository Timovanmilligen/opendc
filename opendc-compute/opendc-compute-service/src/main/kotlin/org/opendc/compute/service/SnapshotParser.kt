package org.opendc.compute.service

import org.opendc.simulator.compute.workload.SimTrace
import org.opendc.simulator.compute.workload.SimTraceWorkload
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.nio.file.Paths

public class SnapshotParser {

    public fun loadSnapshot(id : Int) : ParsedSnapshot{
        val workingDirectory = Paths.get("").toAbsolutePath().toString()
        val directoryPath = "$workingDirectory/src/main/resources/snapshots/snapshot_$id"
        return ParsedSnapshot(readHostToServers(directoryPath),readQueue(directoryPath),readResult(directoryPath),readTimestamp(directoryPath))
    }
    private fun readResult(directoryPath: String) : Double{
        var result=0.0
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
    private fun readTimestamp(directoryPath: String) : Long{
        var timestamp=0L
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
    private fun readHostToServers(directoryPath: String) : MutableMap<String, MutableList<ServerData>>{
        val hostToServers: MutableMap<String, MutableList<ServerData>> = mutableMapOf()
        val hostToServerFile = File("$directoryPath/hostToServers.txt")
        try {
            BufferedReader(FileReader(hostToServerFile)).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    val hostServerSplit = line!!.split('=')
                    val host = hostServerSplit[0]
                    val servers = hostServerSplit[1]
                    servers.split('-').forEach{ name ->
                        val serverData = readServer(directoryPath,name)
                        if (hostToServers[host].isNullOrEmpty()) {
                            hostToServers[host] = mutableListOf(serverData)
                        } else {
                            hostToServers[host]?.add(serverData)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return hostToServers
    }
    private fun readQueue(directoryPath: String) : MutableList<ServerData>{
        val queue : MutableList<ServerData> = mutableListOf()
        val queueFile = File("$directoryPath/queue.txt")
        try {
            BufferedReader(FileReader(queueFile)).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    queue.add(readServer(directoryPath,line!!))
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return queue
    }

    private fun readServer(directoryPath: String, name: String) : ServerData {
        val serverFile = File("$directoryPath/$name.txt")
        try {
            BufferedReader(FileReader(serverFile)).use { br ->
                var line: String?
                val firstLineSplit = br.readLine().split(' ')
                val size = firstLineSplit[0].toInt()
                val offset = firstLineSplit[1].toLong()
                val cpuCount = firstLineSplit[2].toInt()
                val memorySize = firstLineSplit[3].toLong()
                val cpuCapacity = firstLineSplit[4].toDouble()
                val usageCol = DoubleArray(size)
                val timestampCol= LongArray(size)
                val deadlineCol= LongArray(size)
                val coresCol= IntArray(size)
                var index = 0
                while (br.readLine().also { line = it } != null) {
                    val lineSplit = line!!.split(' ')
                    usageCol[index] = lineSplit[0].toDouble()
                    timestampCol[index] = lineSplit[1].toLong()
                    deadlineCol[index] = lineSplit[2].toLong()
                    coresCol[index] = lineSplit[3].toInt()
                    index++
                }
                val workload = SimTraceWorkload(SimTrace(usageCol,timestampCol,deadlineCol,coresCol,size),offset)
                return ServerData(name,workload,cpuCount,memorySize,cpuCapacity)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw IllegalArgumentException("Failed reading server: $name")
        }
    }
    public data class ServerData(
        val name : String,
        val workload: SimTraceWorkload,
        val cpuCount: Int,
        val memorySize: Long,
        val cpuCapacity : Double
    )
    public data class ParsedSnapshot(
        val hostToServers: MutableMap<String, MutableList<ServerData>>,
        val queue : MutableList<ServerData>,
        var result: Double,
        val time : Long
    )
}
