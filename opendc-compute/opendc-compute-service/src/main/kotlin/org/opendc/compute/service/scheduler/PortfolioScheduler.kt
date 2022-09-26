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
 * FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.compute.service.scheduler

import org.opendc.compute.api.Server
import org.opendc.compute.service.*
import org.opendc.compute.service.internal.HostView
import org.opendc.simulator.compute.SimBareMetalMachine
import java.time.Duration
import java.util.*

/**
 * A [ComputeScheduler] implementation that can select a scheduling by simulating the performance of a portfolio of [ComputeScheduler].
 * Uses the [activeScheduler] to select a host.
 *
 * @param duration the duration in the future for which to simulate a policy for.
 * @param metric the metric to optimize for.
 * @param maximize to maximize (or minimize) for a metric.
 * @param saveSnapshots to save the snapshots in the [snapshotHistory].
 * @param exportSnapshots to export the snapshots to disk.
 * @param reflectionResults a queue of policies to possibly add at a later time in the simulation (to simulate adding a policy after reflecting on past performance).
 */
public class PortfolioScheduler(
    public val portfolio : Portfolio,
    public val duration: Duration,
    private val simulationDelay: Duration,
    public val metric : String = "host_energy_efficiency",
    private val maximize : Boolean = true,
    private val saveSnapshots : Boolean = false,
    private val exportSnapshots : Boolean = false,
    private val reflectionResults: Deque<Pair<ComputeScheduler,Long>> = ArrayDeque()
) : ComputeScheduler, MachineTracker {

    override val hostsToMachine: MutableMap<UUID, SimBareMetalMachine> = mutableMapOf()

    public val snapshotHistory: MutableList<Pair<SnapshotParser.ParsedSnapshot, SnapshotMetricExporter.Result>> = mutableListOf()


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
    private var activeScheduler: ComputeScheduler

    private var selections = 0
    init {
        require(portfolio.getSize() >= 1) { "Portfolio smart policy size must be greater than zero." }
        activeScheduler = portfolio.policies.first()
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
        activeScheduler.addHost(host)
    }

    override fun removeHost(host: HostView) {
        hosts.remove(host)
        activeScheduler.removeHost(host)
    }

    override fun select(server: Server): HostView? {
        return activeScheduler.select(server)
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
    public fun selectPolicy(snapshot: SnapshotParser.ParsedSnapshot)  {
        if(reflectionResults.isNotEmpty()){
            if(snapshot.time>reflectionResults.peek().second){
                portfolio.addEntry(reflectionResults.pop().first)
            }
        }
        var bestResult : SnapshotMetricExporter.Result? = null
        clearActiveScheduler()
        selections++
        println("selections: $selections")
        portfolio.policies.forEach {
            val result = snapshotSimulator!!.simulatePolicy(snapshot,it)
            System.gc()
            if(result.hostEnergyEfficiency.isNaN()){
                println("syncing old scheduler, since no queue")
                //Add available hosts to the new scheduler.
                syncActiveScheduler()
            }
            simulationHistory[snapshot.time]?.add(SimulationResult(it.toString(),snapshot.time,result)) ?: run {
                simulationHistory[snapshot.time] = mutableListOf(SimulationResult(it.toString(),snapshot.time,result))
            }

            if(compareResult(bestResult,result)>=0){
                if(maximize){
                    //println("MAXIMIZE ${result.hostEnergyEfficiency} over ${bestResult?.hostEnergyEfficiency} metric: $metric")
                    activeScheduler = it
                    bestResult = result
                }
            }
            else if(!maximize) {
               // println("MINIMIZE ${result.hostEnergyEfficiency} over ${bestResult?.hostEnergyEfficiency} metric: $metric")
                activeScheduler = it
                bestResult = result
            }
        }
        schedulerHistory.add(SimulationResult(activeScheduler.toString(),snapshot.time,bestResult!!))
        snapshot.result = bestResult!!.hostEnergyEfficiency
        if(saveSnapshots) {
            snapshotHistory.add(Pair(snapshot,bestResult!!))
        }
        if(exportSnapshots)
        {
            SnapshotWriter().writeSnapshot(snapshot)
        }
        //Add available hosts to the new scheduler.
        syncActiveScheduler()
    }
    private fun clearActiveScheduler(){
        for (host in hosts) {
            activeScheduler.removeHost(host)
        }
    }
    private fun syncActiveScheduler(){
        for (host in hosts) {
            activeScheduler.addHost(host)
            if(activeScheduler is MachineTracker){
                (activeScheduler as MachineTracker).addMachine(host,hostsToMachine[host.uid]!!)
            }
        }
    }

    public override fun toString(): String = "Portfolio_Scheduler${duration.toMinutes()}m"
}


public data class SimulationResult(
    public val scheduler: String,
    public val time: Long,
    public val performance: SnapshotMetricExporter.Result
)

