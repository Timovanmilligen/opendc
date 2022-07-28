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
import org.opendc.compute.service.MachineTracker
import org.opendc.compute.service.SnapshotMetricExporter
import org.opendc.compute.service.SnapshotSimulator
import org.opendc.compute.service.driver.Host
import org.opendc.compute.service.internal.HostView
import org.opendc.simulator.compute.SimBareMetalMachine
import java.time.Duration
import java.util.*

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
    private val simulationDelay: Duration,
    public val metric : String = "host_energy_efficiency",
    private val maximize : Boolean = true,
    private val saveSnapshots : Boolean = false
) : ComputeScheduler, MachineTracker {


    override val hostsToMachine: MutableMap<UUID, SimBareMetalMachine> = mutableMapOf()

    public val snapshotHistory: MutableList<Pair<Snapshot, SnapshotMetricExporter.Result>> = mutableListOf()

    /**
     * The history of simulated scheduling policies.
     */
    public val simulationHistory: MutableMap<Long,MutableList<SimulationResult>> = mutableMapOf()

    /**
     * The history of active schedulers.
     */
    public val schedulerHistory: MutableList<SimulationResult> = mutableListOf()
    /**
     * The pool of hosts available to the scheduler.
     */
    private val hosts = mutableListOf<HostView>()
    private var snapshotSimulator : SnapshotSimulator? = null
    private var activeScheduler: PortfolioEntry

    private var selections = 0
    init {
        require(portfolio.smart.size >= 1) { "Portfolio smart policy size must be greater than zero." }
        activeScheduler = portfolio.smart.first()
    }

    public override fun addMachine(host: HostView, machine: SimBareMetalMachine) {
        super.addMachine(host, machine)
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

    private fun activeMetric(result : SnapshotMetricExporter.Result) : Long{
        return when (metric) {
            "cpu_ready" -> result.totalStealTime
            "energy_usage" -> result.totalPowerDraw.toLong()
            "cpu_usage" -> result.meanCpuUsage.toLong()
            "host_energy_efficiency" -> result.hostEnergyEfficiency.toLong()
            else -> {
                throw java.lang.IllegalArgumentException("Metric not found.")
            }
        }
    }
    /**
     * Compare results, return true if better, false otherwise.
     */
    private fun compareResult(bestResult: SnapshotMetricExporter.Result?, newResult : SnapshotMetricExporter.Result) : Int{
        //Always return better result if best result is null
        bestResult ?: return maximize.compareTo(true)

        return when(metric){
            "cpu_ready" -> newResult.totalStealTime.compareTo(bestResult.totalStealTime)
            "energy_usage" -> newResult.totalPowerDraw.compareTo(bestResult.totalPowerDraw)
            "cpu_usage" -> newResult.meanCpuUsage.compareTo(bestResult.meanCpuUsage)
            "host_energy_efficiency" -> newResult.hostEnergyEfficiency.compareTo(bestResult.hostEnergyEfficiency)
            else -> {
                throw java.lang.IllegalArgumentException("Metric not found.")
            }
        }
    }
    public fun selectPolicy(snapshot: Snapshot)  {
        var bestResult : SnapshotMetricExporter.Result? = null
        clearActiveScheduler()
        selections++
        println("selections: ${selections}")
        portfolio.smart.forEach {
            println("Simulating policy: ${it.scheduler}")
            val result = snapshotSimulator!!.simulatePolicy(snapshot,it.scheduler)
            if(result.hostEnergyEfficiency.isNaN()){
                println("syncing old scheduler, since no queue")
                //Add available hosts to the new scheduler.
                syncActiveScheduler()
            }
            simulationHistory[snapshot.time]?.add(SimulationResult(it.scheduler.toString(),snapshot.time,result)) ?: run {
                simulationHistory[snapshot.time] = mutableListOf(SimulationResult(it.scheduler.toString(),snapshot.time,result))
            }

            if(compareResult(bestResult,result)>=0){
                if(maximize){
                    //println("MAXIMIZE ${result.hostEnergyEfficiency} over ${bestResult?.hostEnergyEfficiency} metric: $metric")
                    activeScheduler = it
                    it.lastPerformance = activeMetric(result)
                    it.staleness = 0
                    bestResult = result
                }
            }
            else if(!maximize) {
               // println("MINIMIZE ${result.hostEnergyEfficiency} over ${bestResult?.hostEnergyEfficiency} metric: $metric")
                activeScheduler = it
                it.lastPerformance = activeMetric(result)
                it.staleness = 0
                bestResult = result
            }
        }
        schedulerHistory.add(SimulationResult(activeScheduler.scheduler.toString(),snapshot.time,bestResult!!))

        if(saveSnapshots) {
        snapshotHistory.add(Pair(snapshot,bestResult!!))
        }
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
            if(activeScheduler.scheduler is MachineTracker){
                (activeScheduler.scheduler as MachineTracker).addMachine(host,hostsToMachine[host.uid]!!)
            }
        }
    }

    public override fun toString(): String = "Portfolio_Scheduler${duration.toMinutes()}m"
}

public data class Snapshot(
    public val queue: Deque<Server>,
    public val hostToServers: Map<Host,MutableList<Server>>,
    public val time: Long,
    public val duration: Duration
)
public data class SimulationResult(
    public val scheduler: String,
    public val time: Long,
    public val performance: SnapshotMetricExporter.Result
)

