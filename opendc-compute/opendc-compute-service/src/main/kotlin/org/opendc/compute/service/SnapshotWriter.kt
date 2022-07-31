package org.opendc.compute.service

import org.opendc.compute.api.Server
import org.opendc.compute.service.scheduler.Snapshot
import org.opendc.simulator.compute.workload.SimTraceWorkload
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Write a [Snapshot] to a file
 */
public class SnapshotWriter {

    public fun writeSnapshot(snapshot: Snapshot, result : Double){
        val workingDirectory = Paths.get("").toAbsolutePath().toString()
        val outputPath = "src/main/resources/snapshots"
        var id = 0
        var directoryPath = "$workingDirectory/$outputPath/snapshot_$id"
        var path = Paths.get(directoryPath)

        while(Files.isDirectory(path)){
            id++
            directoryPath = "$workingDirectory/$outputPath/snapshot_$id"
            path = Paths.get(directoryPath)
        }

        Files.createDirectory(path)

        //Write which servers are active per host
        writeHostToServers(directoryPath,snapshot)

        //Write which servers are in queue
        writeQueue(directoryPath,snapshot)

        writeResult(directoryPath, result)
    }
    private fun writeResult(directoryPath: String,result: Double){
        val resultFile = File("$directoryPath/result.txt")
        resultFile.createNewFile()
        val resultWriter = BufferedWriter(FileWriter(resultFile, false))
        resultWriter.write("$result")
        resultWriter.close()
    }
    /**
     * Write which servers are active on which hosts.
     */
    private fun writeHostToServers(directoryPath: String, snapshot: Snapshot){
        //Write which servers are active per host
        val activeServerFile = File("$directoryPath/hostToServers.txt")
        activeServerFile.createNewFile()
        val hostWriter = BufferedWriter(FileWriter(activeServerFile, false))
        snapshot.hostToServers.keys.forEach{host ->
            val serverString = snapshot.hostToServers[host]?.joinToString("-") { it.name }
            hostWriter.write("${host.name}=$serverString")
            hostWriter.newLine()
            snapshot.hostToServers[host]?.forEach { server ->
                writeServer(directoryPath,server)
            }
        }
        hostWriter.close()
    }
    /**
     * Write which servers are in queue.
     */
    private fun writeQueue(directoryPath: String, snapshot: Snapshot){
        val queueFile = File("$directoryPath/queue.txt")
        queueFile.createNewFile()
        val queueWriter = BufferedWriter(FileWriter(queueFile, false))
        snapshot.queue.forEach{server ->
            writeServer(directoryPath,server)
            queueWriter.write(server.name)
            queueWriter.newLine()
        }
        queueWriter.close()
    }
    /**
     * Write server data to a file named after the server [name].txt.
     */
    private fun writeServer(directoryPath: String, server: Server){
        val serverFile = File("$directoryPath/${server.name}.txt")
        serverFile.createNewFile()
        val serverWriter = BufferedWriter(FileWriter(serverFile, false))
        val trace = (server.meta["workload"] as SimTraceWorkload).getTrace()
        serverWriter.write("${trace.size} ${(server.meta["workload"] as SimTraceWorkload).getOffset()} ${server.flavor.cpuCount} ${server.flavor.memorySize} ${server.flavor.meta["cpu-capacity"] ?: 0.0}")
        serverWriter.newLine()
        for(i in 0 until trace.size){
            serverWriter.write("${trace.usageCol[i]} ${trace.timestampCol[i]} ${trace.deadlineCol[i]} ${trace.coresCol[i]}")
            serverWriter.newLine()
        }
        serverWriter.close()
    }
}
