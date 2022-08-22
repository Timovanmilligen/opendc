/*
 * Copyright (c) 2021 AtLarge Research
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

package org.opendc.compute.workload

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.opendc.compute.api.Server
import org.opendc.compute.service.*
import org.opendc.compute.service.driver.Host
import org.opendc.compute.service.internal.HostView
import org.opendc.compute.service.scheduler.ComputeScheduler
import org.opendc.compute.simulator.SimHost
import org.opendc.compute.workload.topology.HostSpec
import org.opendc.compute.workload.topology.Topology
import org.opendc.simulator.compute.kernel.interference.VmInterferenceModel
import org.opendc.simulator.compute.power.LinearPowerModel
import org.opendc.simulator.compute.power.SimplePowerDriver
import org.opendc.simulator.compute.workload.SimTraceWorkload
import org.opendc.simulator.flow.FlowEngine
import java.time.Clock
import java.time.Duration
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.max

/**
 * Helper class to simulate VM-based workloads in OpenDC.
 *
 * @param context [CoroutineContext] to run the simulation in.
 * @param clock [Clock] instance tracking simulation time.
 * @param scheduler [ComputeScheduler] implementation to use for the service.
 * @param failureModel A failure model to use for injecting failures.
 * @param interferenceModel The model to use for performance interference.
 * @param schedulingQuantum The scheduling quantum of the scheduler.
 */
public class ComputeServiceHelper(
    private val context: CoroutineContext,
    private val clock: Clock,
    private val scheduler: ComputeScheduler,
    private val failureModel: FailureModel? = null,
    private val interferenceModel: VmInterferenceModel? = null,
    private val schedulingQuantum: Duration = Duration.ofMinutes(5)
) : AutoCloseable {
    /**
     * The [ComputeService] that has been configured by the manager.
     */
    public val service: ComputeService

    /**
     * The [FlowEngine] to simulate the hosts.
     */
    private val _engine = FlowEngine(context, clock)

    /**
     * The hosts that belong to this class.
     */
    private val _hosts = mutableSetOf<SimHost>()

    public var topology: Topology? = null

    init {
        val service = createService(scheduler, schedulingQuantum)
        this.service = service
    }

    /**
     * Run a simulation of the [ComputeService] by replaying the workload trace given by [trace].
     *
     * @param trace The trace to simulate.
     * @param seed The seed for the simulation.
     * @param servers A list to which the created servers is added.
     * @param submitImmediately A flag to indicate that the servers are scheduled immediately (so not at their start time).
     */
    public suspend fun run(
        trace: List<VirtualMachine>,
        seed: Long,
        servers: MutableList<Server>? = null,
        submitImmediately: Boolean = false
    ) {
        val random = Random(seed)
        val injector = failureModel?.createInjector(context, clock, service, random)
        val client = service.newClient()
        // Create new image for the virtual machine
        val image = client.newImage("vm-image")
        try {
            coroutineScope {
                // Start the fault injector
                injector?.start()

                var offset = Long.MIN_VALUE

                for (entry in trace.sortedBy { it.startTime }) {
                    val now = clock.millis()
                    val start = entry.startTime.toEpochMilli()
                    if (offset < 0) {
                        offset = start - now
                    }
                    // Make sure the trace entries are ordered by submission time
                    assert(start - offset >= 0) { "Invalid trace order" }

                    if (!submitImmediately) {
                        delay(max(0, (start - offset) - now))
                    }
                    launch {
                        val workloadOffset = -offset + schedulingQuantum.toMillis() + 1
                        val workload = SimTraceWorkload(entry.trace, workloadOffset)
                        val server = client.newServer(
                            entry.name,
                            image,
                            client.newFlavor(
                                entry.name,
                                entry.cpuCount,
                                entry.memCapacity,
                                meta = if (entry.cpuCapacity > 0.0) mapOf("cpu-capacity" to entry.cpuCapacity) else emptyMap()
                            ),
                            meta = mapOf("workload" to workload)
                        )

                        // println("ENTRY HIT: TIME: ${clock.millis()} end time: ${entry.stopTime.toEpochMilli()} and ${workload.getEndTime()}")
                        // Wait for the server reach its end time
                        servers?.add(server)

                        // Wait for the server reach its end time
                        val endTime = entry.stopTime.toEpochMilli()
                        delay(endTime + workloadOffset - clock.millis() + schedulingQuantum.toMillis())

                        // Stop the server after reaching the end-time of the virtual machine
                        server.stop()
                    }
                }
            }
            yield()
        } finally {
            injector?.close()
            client.close()
            println("main trace done at: ${clock.millis()}")
        }
    }

    /**
     * Register a host for this simulation.
     *
     * @param spec The definition of the host.
     * @param optimize Merge the CPU resources of the host into a single CPU resource.
     * @return The [SimHost] that has been constructed by the runner.
     */
    public fun registerHost(spec: HostSpec, optimize: Boolean = false): SimHost {
        val host = SimHost(
            spec.uid,
            spec.name,
            spec.model,
            spec.meta,
            context,
            _engine,
            spec.hypervisor,
            powerDriver = spec.powerDriver,
            interferenceDomain = interferenceModel?.newDomain(),
            optimize = optimize
        )
        require(_hosts.add(host)) { "Host with uid ${spec.uid} already exists" }
        service.addHost(host)

        return host
    }
    override fun close() {
        service.close()

        for (host in _hosts) {
            host.close()
        }

        _hosts.clear()
    }

    /**
     * Construct a [ComputeService] instance.
     */
    private fun createService(scheduler: ComputeScheduler, schedulingQuantum: Duration): ComputeService {
        return ComputeService(context, clock, scheduler, schedulingQuantum)
    }

    /**
     * Simulate a [ComputeScheduler] from a given [Snapshot].
     */
    /*public override fun simulatePolicy(snapshot: SnapshotParser.ParsedSnapshot, scheduler: ComputeScheduler) : SnapshotMetricExporter.Result {
        val exporter = SnapshotMetricExporter()

        runBlockingSimulation {
            //Create new compute service
            //val computeService = createService(scheduler, schedulingQuantum = Duration.ofSeconds(1), telemetry)
            val runner = ComputeServiceHelper(coroutineContext, clock, scheduler, schedulingQuantum = Duration.ofMillis(1), interferenceModel = interferenceModel)
            runner.apply(topology!!)
            val client = runner.service.newClient()

            // Load the snapshot by placing already active servers on all corresponding hosts
            (runner.service as ComputeServiceImpl).loadSnapshot(snapshot)

            // Create new image for the virtual machine
            val image = client.newImage("vm-image")
            try {
                coroutineScope {
                    snapshot.queue.forEach{serverData ->
                        launch {
                            val workload = serverData.workload
                            workload.getTrace().resetTraceProgression()
                            val server = client.newServer(
                                serverData.name,
                                image,
                                client.newFlavor(
                                    serverData.name,
                                    serverData.cpuCount,
                                    serverData.memorySize,
                                    meta = if (serverData.cpuCapacity > 0.0) mapOf("cpu-capacity" to serverData.cpuCapacity) else emptyMap()
                                ),
                                meta = mapOf("workload" to workload)
                            )
                            // Wait for the server to reach its end time.
                            val endTime = serverData.workload.getEndTime()
                            val startTime = serverData.workload.getStartTime()
                            delay( endTime - startTime)
                            // Delete the server after reaching the end-time of the virtual machine
                            server.delete()
                        }
                    }
                }
                yield()
            }
            catch (e :Throwable){
                e.printStackTrace()
            }
            finally {
                client.close()
                runner.close()
            }
        }
        return exporter.getResult()
    }*/

    public fun applyTopologyFromHosts(hosts: Set<Host>) {
        hosts.forEach {
            val host = (it as SimHost)
            registerHost(HostSpec(host.uid, host.name, host.meta, host.machineModel, SimplePowerDriver(LinearPowerModel(250.0, 60.0))))
        }
    }
}
