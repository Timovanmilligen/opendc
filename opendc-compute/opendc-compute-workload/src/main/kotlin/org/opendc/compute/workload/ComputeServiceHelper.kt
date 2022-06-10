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
import org.opendc.compute.service.ComputeService
import org.opendc.compute.service.SnapshotMetricExporter
import org.opendc.compute.service.SnapshotSimulator
import org.opendc.compute.service.driver.Host
import org.opendc.compute.service.internal.ComputeServiceImpl
import org.opendc.compute.service.scheduler.*
import org.opendc.compute.simulator.SimHost
import org.opendc.compute.workload.telemetry.SdkTelemetryManager
import org.opendc.compute.workload.telemetry.TelemetryManager
import org.opendc.compute.workload.topology.HostSpec
import org.opendc.compute.workload.topology.Topology
import org.opendc.compute.workload.topology.apply
import org.opendc.simulator.compute.kernel.interference.VmInterferenceModel
import org.opendc.simulator.compute.power.LinearPowerModel
import org.opendc.simulator.compute.power.SimplePowerDriver
import org.opendc.simulator.compute.workload.SimTraceWorkload
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.simulator.flow.FlowEngine
import org.opendc.telemetry.sdk.metrics.export.CoroutineMetricReader
import java.time.Clock
import java.time.Duration
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.exp
import kotlin.math.max
import kotlin.reflect.jvm.internal.impl.serialization.deserialization.FlexibleTypeDeserializer.ThrowException

/**
 * Helper class to simulate VM-based workloads in OpenDC.
 *
 * @param context [CoroutineContext] to run the simulation in.
 * @param clock [Clock] instance tracking simulation time.
 * @param telemetry Helper class for managing telemetry.
 * @param scheduler [ComputeScheduler] implementation to use for the service.
 * @param failureModel A failure model to use for injecting failures.
 * @param interferenceModel The model to use for performance interference.
 * @param schedulingQuantum The scheduling quantum of the scheduler.
 */
public class ComputeServiceHelper(
    private val context: CoroutineContext,
    private val clock: Clock,
    private val telemetry: TelemetryManager,
    private val scheduler: ComputeScheduler,
    private val failureModel: FailureModel? = null,
    private val interferenceModel: VmInterferenceModel? = null,
    schedulingQuantum: Duration = Duration.ofMinutes(5)
) : AutoCloseable, SnapshotSimulator() {
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
        if(scheduler is PortfolioScheduler){
            scheduler.addSimulator(this)
        }
        val service = createService(scheduler, schedulingQuantum)
        this.service = service
    }

    /**
     * Converge a simulation of the [ComputeService] by replaying the workload trace given by [trace].
     */
    public suspend fun run(trace: List<VirtualMachine>, seed: Long, submitImmediately: Boolean = false) {
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
                        val workloadOffset = -offset + 300001
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

                        // Wait for the server reach its end time
                        val endTime = entry.stopTime.toEpochMilli()
                        delay(endTime + workloadOffset - clock.millis() + 5 * 60 * 1000)
                        // Delete the server after reaching the end-time of the virtual machine
                        server.delete()
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
        val meterProvider = telemetry.createMeterProvider(spec)
        val host = SimHost(
            spec.uid,
            spec.name,
            spec.model,
            spec.meta,
            context,
            _engine,
            meterProvider,
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
        val meterProvider = telemetry.createMeterProvider(scheduler)
        return ComputeService(context, clock, meterProvider, scheduler, schedulingQuantum)
    }

    /**
     * Simulate a [ComputeScheduler] from a given [Snapshot].
     */
    public override fun simulatePolicy(snapshot: Snapshot, scheduler: ComputeScheduler) : SnapshotMetricExporter.Result {
        val exporter = SnapshotMetricExporter()

            runBlockingSimulation {
                val telemetry = SdkTelemetryManager(clock)
                //Create new compute service
                //val computeService = createService(scheduler, schedulingQuantum = Duration.ofSeconds(1), telemetry)
                val runner = ComputeServiceHelper(coroutineContext, clock, telemetry, scheduler, schedulingQuantum = Duration.ofMillis(1))
                telemetry.registerMetricReader(CoroutineMetricReader(this, exporter))
                runner.apply(topology!!)
                val client = runner.service.newClient()

                // Load the snapshot by placing already active servers on all corresponding hosts
                (runner.service as ComputeServiceImpl).loadSnapshot(snapshot)

                // Create new image for the virtual machine
                val image = client.newImage("vm-image")
                try {
                    coroutineScope {
                        snapshot.queue.forEach{nextServer ->
                            launch {
                                // println("Launching server ${nextServer.uid} at time ${clock.millis()}")
                                val (remainingTrace, offset) = (nextServer.meta["workload"] as SimTraceWorkload).getNormalizedRemainingTraceAndOffset(snapshot.time, snapshot.duration)
                                val workload = SimTraceWorkload(remainingTrace, offset)
                                val server = client.newServer(
                                    nextServer.name,
                                    image,
                                    client.newFlavor(
                                        nextServer.name,
                                        nextServer.flavor.cpuCount,
                                        nextServer.flavor.memorySize,
                                        meta = nextServer.meta
                                    ),
                                    meta = mapOf("workload" to workload)
                                )
                                // Wait for the server to reach its end time.
                                val endTime = remainingTrace.getEndTime()
                                delay( endTime + offset - clock.millis() + 5 * 60 * 1000)
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
                    runner.service.close()
                    runner.close()
                    telemetry.close()
                    //println("Done at: ${clock.millis()}")
                }
            }
        return exporter.getResult()
    }

    public fun applyTopologyFromHosts(hosts: MutableSet<Host>)  {
        hosts.forEach {
            val host = (it as SimHost)
            registerHost(HostSpec(host.uid,host.name,host.meta,host.machineModel, SimplePowerDriver(LinearPowerModel(250.0, 60.0))))
        }
    }
}
