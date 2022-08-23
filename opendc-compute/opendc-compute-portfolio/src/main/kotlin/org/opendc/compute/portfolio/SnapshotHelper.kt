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

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.opendc.compute.service.ComputeService
import org.opendc.simulator.compute.workload.SimTraceWorkload
import java.time.Duration

/**
 * Helper class to build a [Snapshot] from a [ComputeService].
 */
public class SnapshotHelper {
    /**
     * Build a snapshot of the specified [service].
     */
    public fun buildSnapshot(service: ComputeService, now: Long, duration: Duration): Snapshot {
        val servers = mutableListOf<Snapshot.ServerData>()

        for (server in service.servers) {
            val workload = (server.meta["workload"] as SimTraceWorkload).getNormalizedRemainingWorkload(now, duration)
            val flavor = server.flavor
            val host = service.lookupHost(server)
            val serverData = Snapshot.ServerData(
                server.name,
                server.state,
                workload,
                flavor.cpuCount,
                flavor.memorySize,
                (flavor.meta["cpu-capacity"] as Double),
                host?.uid
            )
            servers.add(serverData)
        }

        return Snapshot(servers, 0.0, now)
    }

    /**
     * Replay a snapshot for the specified [service].
     */
    public suspend fun replaySnapshot(service: ComputeService, snapshot: Snapshot) {
        val client = service.newClient()

        // Create new image for the virtual machine
        val image = client.newImage("vm-image")

        try {
            coroutineScope {
                for (serverData in snapshot.servers) {
                    val workload = serverData.workload
                    workload.getTrace().resetTraceProgression()
                    val metadata = mutableMapOf<String, Any>("workload" to workload)
                    val host = service.hosts.find { it.uid == serverData.host }
                    if (host != null) {
                        metadata["host-preference"] = host
                    }

                    launch {
                        val server = client.newServer(
                            serverData.name,
                            image,
                            client.newFlavor(
                                serverData.name,
                                serverData.cpuCount,
                                serverData.memorySize,
                                meta = if (serverData.cpuCapacity > 0.0) mapOf("cpu-capacity" to serverData.cpuCapacity) else emptyMap()
                            ),
                            meta = metadata
                        )
                        // Wait for the server to reach its end time.
                        val endTime = serverData.workload.getEndTime()
                        val startTime = serverData.workload.getStartTime()

                        delay(endTime - startTime)
                        // Stop the server after reaching the end-time of the virtual machine
                        server.stop()
                    }
                }
            }
            yield()
        } finally {
            client.close()
        }
    }
}
