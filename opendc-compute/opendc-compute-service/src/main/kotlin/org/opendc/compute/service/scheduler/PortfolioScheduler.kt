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

package org.opendc.compute.service.scheduler

import org.opendc.compute.api.Server
import org.opendc.compute.service.SnapshotMetricExporter
import org.opendc.compute.service.SnapshotSimulator
import org.opendc.compute.service.driver.Host
import org.opendc.compute.service.internal.HostView
import org.opendc.telemetry.compute.ComputeMetricExporter
import org.opendc.telemetry.compute.ComputeMonitor
import org.opendc.telemetry.compute.table.HostTableReader
import org.opendc.telemetry.compute.table.ServiceData
import org.opendc.telemetry.compute.table.ServiceTableReader
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * A [ComputeScheduler] implementation that uses filtering and weighing passes to select
 * the host to schedule a [Server] on, as well as selecting between different clusters.
 *
 *
 * This implementation is based on the filter scheduler from OpenStack Nova.
 * See: https://docs.openstack.org/nova/latest/user/filter-scheduler.html
 *
 * @param filters The list of filters to apply when searching for an appropriate host.
 * @param weighers The list of weighers to apply when searching for an appropriate host.
 * @param subsetSize The size of the subset of best hosts from which a target is randomly chosen.
 * @param random A [Random] instance for selecting
 */
public class PortfolioScheduler(
    public val portfolio : Portfolio,
    public val duration: Duration,
    public val simulationDelay: Duration
) : ComputeScheduler {


    public val snapshotHistory: MutableList<Pair<Snapshot, SnapshotMetricExporter.Result>> = mutableListOf()
    /**
     * The pool of hosts available to the scheduler.
     */
    private val hosts = mutableListOf<HostView>()
    private var snapshotSimulator : SnapshotSimulator? = null
    private var activeScheduler: PortfolioEntry

    init {
        require(portfolio.smart.size >= 1) { "Portfolio smart policy size must be greater than zero." }
        activeScheduler = portfolio.smart.first()
    }

    /**
     * Get the time it takes to simulate the entire portfolio in ms.
     */
    public fun getTotalSimulationTime() : Long{
        return portfolio.getSize() * simulationDelay.toMillis()
    }
    /**
     * Add a [SnapshotSimulator] to the scheduler.
     */
    public fun addSimulator(simulator : SnapshotSimulator)
    {
        this.snapshotSimulator = simulator
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

    public fun selectPolicy(snapshot: Snapshot)  {
        var bestPerformance = Long.MAX_VALUE
        var bestResult : SnapshotMetricExporter.Result? = null
        clearActiveScheduler()
        portfolio.smart.forEach {
            println("Simulating policy: ${it.scheduler}")
            val result = snapshotSimulator!!.simulatePolicy(snapshot,it.scheduler)
            if(result.totalStealTime < bestPerformance){
                bestPerformance = result.totalStealTime
                activeScheduler = it
                it.lastPerformance = result.totalStealTime
                it.staleness = 0
                bestResult = result
            }
            //println("utilization: ${result.meanCpuUsage}")
        }
        snapshotHistory.add(Pair(snapshot,bestResult!!))
        /*snapshotHistory.forEach{ entry ->
            entry.first.hostToServers.forEach{
                println("LISTING SNAPSHOTS host: ${it.key.name}, servers: ${it.value.size}")
            }
        }*/
        //Add available hosts to the new scheduler.
        syncActiveScheduler()
    }
    private fun clearActiveScheduler(){
        for (host in hosts) {
            activeScheduler.scheduler.removeHost(host)
        }
    }
    private fun syncActiveScheduler(){
        for (host in hosts) {
            activeScheduler.scheduler.addHost(host)
        }
    }
}

public data class Snapshot(
    public val queue: Deque<Server>,
    public val hostToServers: Map<Host,MutableList<Server>>,
    public val time: Long,
    public val duration: Duration
)

