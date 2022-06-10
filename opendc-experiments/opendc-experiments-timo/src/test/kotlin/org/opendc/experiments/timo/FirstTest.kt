package org.opendc.experiments.timo

/*
 * Copyright (c) 2020 AtLarge Research
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

import io.jenetics.*
import io.jenetics.engine.Engine
import io.jenetics.engine.EvolutionResult
import io.jenetics.util.RandomRegistry
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opendc.compute.service.SnapshotMetricExporter
import org.opendc.compute.service.scheduler.*
import org.opendc.compute.service.scheduler.filters.ComputeFilter
import org.opendc.compute.service.scheduler.filters.RamFilter
import org.opendc.compute.service.scheduler.filters.VCpuFilter
import org.opendc.compute.service.scheduler.weights.CoreRamWeigher
import org.opendc.compute.workload.*
import org.opendc.compute.workload.telemetry.SdkTelemetryManager
import org.opendc.compute.workload.topology.Topology
import org.opendc.compute.workload.topology.apply
import org.opendc.experiments.capelin.topology.clusterTopology
import org.opendc.experiments.timo.codec.PolicyGene
import org.opendc.experiments.timo.operator.GuidedMutator
import org.opendc.experiments.timo.operator.LengthMutator
import org.opendc.experiments.timo.operator.RedundantPruner
import org.opendc.experiments.timo.problems.SnapshotProblem
import org.opendc.experiments.timo.util.GenotypeConverter
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.telemetry.sdk.metrics.export.CoroutineMetricReader
import java.io.File
import java.time.Duration
import java.util.*

/**
 * An integration test suite for the genetic experiments.
 */
class FirstTest {
    /**
     * The monitor used to keep track of the metrics.
     */
    private lateinit var exporter: SnapshotMetricExporter

    /**
     * The [ComputeWorkloadLoader] responsible for loading the traces.
     */
    private lateinit var workloadLoader: ComputeWorkloadLoader

    /**
     * The [PortfolioScheduler] to use for all experiments.
     */
    private lateinit var portfolioScheduler: PortfolioScheduler

    /**
     * The logger for this instance.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * Set up the experimental environment.
     */
    @BeforeEach
    fun setUp() {
        exporter = SnapshotMetricExporter()
        workloadLoader = ComputeWorkloadLoader(File("src/test/resources/trace"))
        portfolioScheduler = PortfolioScheduler(createSinglePolicyPortfolio(), Duration.ofMillis(300000),Duration.ofMillis(20))
    }
    private fun createSinglePolicyPortfolio() :Portfolio {
        val portfolio = Portfolio()
        val entry = PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
            weighers = listOf(CoreRamWeigher(multiplier = 1.0))
        ),Long.MAX_VALUE,0)
        portfolio.addEntry(entry)

        return portfolio
    }
    private fun createPortfolio() : Portfolio{
        val portfolio = Portfolio()
        val entry = PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
            weighers = listOf(CoreRamWeigher(multiplier = 1.0))
        ),Long.MAX_VALUE,0)
        println(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
            weighers = listOf(CoreRamWeigher(multiplier = 1.0))
        ))
        //val entry2 = PortfolioEntry(FilterScheduler(
          //  filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
            //weighers = listOf(VCpuCapacityWeigher(multiplier = 1.0))
        //),Long.MAX_VALUE,0)
        portfolio.addEntry(entry)
       // portfolio.addEntry(entry2)
        return portfolio
    }

    @Test
    fun runTrace() = runBlockingSimulation {
        val workload = createTestWorkload(0.05)
        val telemetry = SdkTelemetryManager(clock)
        val runner = ComputeServiceHelper(
            coroutineContext,
            clock,
            telemetry,
            portfolioScheduler
        )
        val topology = createTopology()

        telemetry.registerMetricReader(CoroutineMetricReader(this, exporter))

        try {
            runner.apply(topology)
            runner.run(workload, 0)

            val result = exporter.getResult()
            println(
                "Scheduler " +
                    "Success=${result.attemptsSuccess} " +
                    "Failure=${result.attemptsFailure} " +
                    "Error=${result.attemptsError} " +
                    "Pending=${result.serversPending} " +
                    "Active=${result.serversActive}"
            )

        } finally {
            runner.close()
            telemetry.close()
        }
        //val newPortfolio = extendPortfolioFromSnapshots(portfolioScheduler.portfolio,portfolioScheduler.snapshotHistory,topology)
        assertEquals(1,1)
    }
    private fun extendPortfolioFromSnapshots(portfolio: Portfolio, snapshotHistory : MutableList<Pair<Snapshot,SnapshotMetricExporter.Result>>, topology: Topology, optimize: Optimize = Optimize.MINIMUM) : Portfolio{
        val populationSize = 100
        val seed = 123L
        val maxGenerations = 10L

        snapshotHistory.forEach { snapshotEntry ->
            val engine = Engine.builder(SnapshotProblem(snapshotEntry.first, topology)).optimize(optimize).survivorsSelector(TournamentSelector(5))
                .executor(Runnable::run) // Make sure Jenetics does not run concurrently
                .populationSize(populationSize)
                .offspringSelector(RouletteWheelSelector())
                .alterers(
                    UniformCrossover(),
                    Mutator(0.10),
                    GuidedMutator(0.05),
                    LengthMutator(0.02),
                    RedundantPruner()
                ).build()

            val result = RandomRegistry.with(Random(seed)) {
                engine.stream()
                    .limit(maxGenerations)
                    .peek{ update(it) }
                    .collect(EvolutionResult.toBestEvolutionResult())
            }
            val schedulerSpec = GenotypeConverter().invoke(result.bestPhenotype().genotype())
            portfolio.addEntry(PortfolioEntry(FilterScheduler(schedulerSpec.filters,schedulerSpec.weighers),Long.MAX_VALUE,0))
        }
        return portfolio
    }

    /**
     * Obtain the trace reader for the test.
     */
    private fun createTestWorkload(fraction: Double, seed: Int = 0): List<VirtualMachine> {
        val source = trace("bitbrains-faststorage").sampleByLoad(fraction)
        return source.resolve(workloadLoader, Random(seed.toLong()))
    }

    /**
     * Obtain the topology factory for the test.
     */
    private fun createTopology(name: String = "topology"): Topology {
        val stream = checkNotNull(object {}.javaClass.getResourceAsStream("/env/$name.txt"))
        return stream.use { clusterTopology(stream) }
    }
    private fun update(result: EvolutionResult<PolicyGene<Pair<String, Any>>,Long>){

        println("Generation: ${result.generation()}, Population size:${result.population().size()} Altered:${result.alterCount()}, Best phenotype: ${result.bestPhenotype()}, Average fitness: ${result.population().map{it.fitness()}.average()}")
    }

    private fun getFractionsOfTrace(trace: List<VirtualMachine>, subsets :Int) : List<List<VirtualMachine>>{
        val fractionSize = 1.0 / subsets
        logger.info { "Diving trace in fractions of $fractionSize" }
        val traceSubsets = mutableListOf<List<VirtualMachine>>()
        val endTime = trace.last().startTime.toEpochMilli()
        val startTime = trace.first().startTime.toEpochMilli()
        val totalTime = endTime - startTime
        logger.info { "Starttime: $startTime, Endtime: $endTime, Totaltime: $totalTime" }
        var nextJob = 0

        for (i in 0 until subsets){
            val subset = mutableListOf<VirtualMachine>()
            for (j in nextJob until trace.size){
                if((trace[j].startTime.toEpochMilli()  - startTime)/totalTime.toDouble() <= (i+1) * fractionSize ){
                    logger.info{"Starttime:${trace[j].startTime.toEpochMilli()- startTime} fraction:${(trace[j].startTime.toEpochMilli()  - startTime)/totalTime.toDouble()}"}
                    //include job in subset
                    subset.add(trace[j])
                }
                else{
                    nextJob = j
                    //Next jobs won't be in this subset since it is sorted on submit time, so break this for loop.
                    break
                }
            }
            traceSubsets.add(subset)
        }
        return traceSubsets
    }
}
