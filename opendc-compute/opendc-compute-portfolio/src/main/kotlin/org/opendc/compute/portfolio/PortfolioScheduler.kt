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

import kotlinx.coroutines.yield
import org.opendc.common.util.Pacer
import org.opendc.compute.api.Server
import org.opendc.compute.service.ComputeService
import org.opendc.compute.service.internal.HostView
import org.opendc.compute.service.scheduler.ComputeScheduler
import org.opendc.compute.workload.ComputeServiceHelper
import org.opendc.compute.workload.telemetry.ComputeMetricReader
import org.opendc.compute.workload.topology.Topology
import org.opendc.compute.workload.topology.apply
import org.opendc.simulator.compute.kernel.interference.VmInterferenceModel
import org.opendc.simulator.core.runBlockingSimulation
import java.time.Clock
import java.time.Duration
import kotlin.coroutines.CoroutineContext

public class PortfolioScheduler(
    coroutineContext: CoroutineContext,
    private val clock: Clock,
    public val portfolio: Portfolio,
    private val topology: Topology,
    public val duration: Duration,
    private val simulationDelay: Duration,
    public val metric: String = "host_energy_efficiency",
    private val maximize: Boolean = true,
    private val saveSnapshots: Boolean = false,
    exportSnapshots: Boolean = false,
    private val interferenceModel: VmInterferenceModel? = null
) : ComputeScheduler {
    /**
     * The history of simulated scheduling policies.
     */
    public val simulationHistory: MutableMap<Long, MutableList<SimulationResult>> = mutableMapOf()

    /**
     * The history of active schedulers.
     */
    public val schedulerHistory: MutableList<SimulationResult> = mutableListOf()

    /**
     * The pool of hosts available to the scheduler.
     */
    private val hosts = mutableListOf<HostView>()
    private var activeScheduler: PortfolioEntry

    private var selections = 0

    // TODO Needs to be assigned before use of the class
    public lateinit var service: ComputeService

    /**
     * The [Pacer] to use for scheduling the portfolio scheduler simulation cycles.
     */
    private val pacer = Pacer(coroutineContext, clock, 1000) { selectPolicy() } // TODO When to enqueue pacer?

    public val snapshotHistory: MutableList<Pair<Snapshot, SnapshotMetricExporter.Result>> =
        mutableListOf()
    private val snapshotWriter: SnapshotWriter? = if (exportSnapshots) SnapshotWriter() else null
    private val snapshotHelper = SnapshotHelper()

    init {
        require(portfolio.smart.size >= 1) { "Portfolio smart policy size must be greater than zero." }
        activeScheduler = portfolio.smart.first()
    }

    /**
     * Get the time it takes to simulate the entire portfolio in ms.
     */
    public fun getTotalSimulationTime(): Long {
        return portfolio.getSize() * simulationDelay.toMillis()
    }

    override fun addHost(host: HostView) {
        hosts.add(host)
        activeScheduler.scheduler.addHost(host)
    }

    override fun removeHost(host: HostView) {
        hosts.remove(host)
        activeScheduler.scheduler.removeHost(host)
    }

    override fun select(server: Server): HostView? {
        return activeScheduler.scheduler.select(server)
    }

    private fun activeMetric(result: SnapshotMetricExporter.Result): Long {
        return when (metric) {
            "cpu_ready" -> result.totalStealTime
            "energy_usage" -> result.totalPowerDraw.toLong()
            "cpu_usage" -> result.meanCpuUsage.toLong()
            "host_energy_efficiency" -> result.hostEnergyEfficiency.toLong()
            else -> throw IllegalArgumentException("Metric not found.")
        }
    }

    /**
     * Compare results, return true if better, false otherwise.
     */
    private fun compareResult(
        bestResult: SnapshotMetricExporter.Result?,
        newResult: SnapshotMetricExporter.Result
    ): Int {
        // Always return better result if best result is null
        bestResult ?: return maximize.compareTo(true)

        return when (metric) {
            "cpu_ready" -> newResult.totalStealTime.compareTo(bestResult.totalStealTime)
            "energy_usage" -> newResult.totalPowerDraw.compareTo(bestResult.totalPowerDraw)
            "cpu_usage" -> newResult.meanCpuUsage.compareTo(bestResult.meanCpuUsage)
            "host_energy_efficiency" -> newResult.hostEnergyEfficiency.compareTo(bestResult.hostEnergyEfficiency)
            else -> throw IllegalArgumentException("Metric not found.")
        }
    }

    private fun selectPolicy() {
        val snapshot =  snapshotHelper.buildSnapshot(service, clock.millis(), duration)
        var bestResult: SnapshotMetricExporter.Result? = null
        clearActiveScheduler()
        selections++
        println("selections: $selections")
        portfolio.smart.forEach {
            println("Simulating policy: ${it.scheduler}")
            val result = simulatePolicy(snapshot, it.scheduler)
            System.gc()
            if (result.hostEnergyEfficiency.isNaN()) {
                println("syncing old scheduler, since no queue")
                // Add available hosts to the new scheduler.
                syncActiveScheduler()
            }
            simulationHistory.computeIfAbsent(snapshot.time) { mutableListOf() }.add(SimulationResult(it.scheduler.toString(), snapshot.time, result))
            if (compareResult(bestResult, result) >= 0) {
                if (maximize) {
                    // println("MAXIMIZE ${result.hostEnergyEfficiency} over ${bestResult?.hostEnergyEfficiency} metric: $metric")
                    activeScheduler = it
                    bestResult = result
                }
            } else if (!maximize) {
                // println("MINIMIZE ${result.hostEnergyEfficiency} over ${bestResult?.hostEnergyEfficiency} metric: $metric")
                activeScheduler = it
                bestResult = result
            }
        }
        schedulerHistory.add(SimulationResult(activeScheduler.scheduler.toString(), snapshot.time, bestResult!!))
        snapshot.result = bestResult!!.totalStealTime.toDouble()
        if (saveSnapshots) {
            snapshotHistory.add(Pair(snapshot, bestResult!!))
        }

        snapshotWriter?.writeSnapshot(snapshot)

        // Add available hosts to the new scheduler.
        syncActiveScheduler()
    }

    /**
     * Simulate a [ComputeScheduler] from a given [Snapshot].
     */
    private fun simulatePolicy(snapshot: Snapshot, scheduler: ComputeScheduler) : SnapshotMetricExporter.Result {
        val exporter = SnapshotMetricExporter()

        runBlockingSimulation {
            val runner = ComputeServiceHelper(coroutineContext, clock, scheduler, schedulingQuantum = Duration.ofMillis(1), interferenceModel = interferenceModel)
            val metricReader = ComputeMetricReader(
                this,
                clock,
                runner.service,
                exporter,
                exportInterval = Duration.ofMinutes(5)
            )

            try {
                runner.apply(topology, optimize = true)
                snapshotHelper.replaySnapshot(runner.service, snapshot)
                yield()
            } finally {
                metricReader.close()
                runner.close()
            }
        }

        return exporter.getResult()
    }

    private fun clearActiveScheduler() {
        for (host in hosts) {
            activeScheduler.scheduler.removeHost(host)
        }
    }

    private fun syncActiveScheduler() {
        for (host in hosts) {
            activeScheduler.scheduler.addHost(host)
        }
    }

    public override fun toString(): String = "Portfolio_Scheduler${duration.toMinutes()}m"
}

