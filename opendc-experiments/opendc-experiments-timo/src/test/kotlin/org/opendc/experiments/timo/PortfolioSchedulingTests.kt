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
import org.junit.jupiter.api.assertAll
import org.opendc.compute.service.SnapshotMetricExporter
import org.opendc.compute.service.scheduler.*
import org.opendc.compute.service.scheduler.filters.ComputeFilter
import org.opendc.compute.service.scheduler.filters.RamFilter
import org.opendc.compute.service.scheduler.filters.VCpuCapacityFilter
import org.opendc.compute.service.scheduler.filters.VCpuFilter
import org.opendc.compute.service.scheduler.weights.CoreRamWeigher
import org.opendc.compute.service.scheduler.weights.InstanceCountWeigher
import org.opendc.compute.service.scheduler.weights.VCpuCapacityWeigher
import org.opendc.compute.workload.*
import org.opendc.compute.workload.telemetry.SdkTelemetryManager
import org.opendc.compute.workload.topology.Topology
import org.opendc.compute.workload.topology.apply
import org.opendc.experiments.capelin.topology.clusterTopology
import org.opendc.experiments.timo.codec.PolicyGene
import org.opendc.experiments.timo.operator.GuidedMutator
import org.opendc.experiments.timo.operator.LengthMutator
import org.opendc.experiments.timo.operator.RedundantPruner
import org.opendc.experiments.timo.problems.VMProblem
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.telemetry.sdk.metrics.export.CoroutineMetricReader
import java.io.File
import java.time.Duration
import java.util.*

/**
 * An integration test suite for the genetic experiments.
 */
class PortfolioSchedulingTests {
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
     * Setup the experimental environment.
     */
    @BeforeEach
    fun setUp() {
        exporter = SnapshotMetricExporter()
        workloadLoader = ComputeWorkloadLoader(File("src/test/resources/trace"))
        portfolioScheduler = PortfolioScheduler(createPortfolio(), Duration.ofMillis(300002))
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
        val entry2 = PortfolioEntry(FilterScheduler(
            filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
            weighers = listOf(VCpuCapacityWeigher(multiplier = 1.0))
        ),Long.MAX_VALUE,0)
        portfolio.addEntry(entry)
        portfolio.addEntry(entry2)
        return portfolio
    }

    @Test
    fun testRunTimes() = runBlockingSimulation {
        val seed = 1
        val workload = createTestWorkload(0.25, seed)
        val telemetry = SdkTelemetryManager(clock)
        val scheduler = PortfolioScheduler(createSinglePolicyPortfolio(), Duration.ofDays(100000))
        val runner = ComputeServiceHelper(
            coroutineContext,
            clock,
            telemetry,
            scheduler
        )
        val topology = createTopology()

        telemetry.registerMetricReader(CoroutineMetricReader(this, exporter))

        try {
            runner.apply(topology)
            runner.run(workload, seed.toLong())

        } finally {
            runner.close()
            telemetry.close()
        }
        val portfolioSimulationResult = scheduler.snapshotHistory.first().second
        println(
            "Scheduler " +
                "Success=${portfolioSimulationResult.attemptsSuccess} " +
                "Failure=${portfolioSimulationResult.attemptsFailure} " +
                "Error=${portfolioSimulationResult.attemptsError} " +
                "Pending=${portfolioSimulationResult.serversPending} " +
                "Active=${portfolioSimulationResult.serversActive}" +
                "Cpu usage = ${portfolioSimulationResult.meanCpuUsage} " +
                "Cpu demand = ${portfolioSimulationResult.meanCpuDemand}"
        )

        // Note that these values have been verified beforehand
        assertAll(
            { assertEquals(10999592, portfolioSimulationResult.totalIdleTime) { "Idle time incorrect" } },
            { assertEquals(9741207, portfolioSimulationResult.totalActiveTime) { "Active time incorrect" } },
            { assertEquals(0, portfolioSimulationResult.totalStealTime) { "Steal time incorrect" } },
            { assertEquals(0, portfolioSimulationResult.totalLostTime) { "Lost time incorrect" } }
        )
    }
    /**
     * Test a small simulation setup.
     */
    @Test
    fun testSmall() = runBlockingSimulation {
        val seed = 1
        val workload = createTestWorkload(0.25, seed)
        val telemetry = SdkTelemetryManager(clock)
        val runner = ComputeServiceHelper(
            coroutineContext,
            clock,
            telemetry,
            FilterScheduler(
                filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
                weighers = listOf(CoreRamWeigher(multiplier = 1.0))
            )
        )
        val topology = createTopology()

        telemetry.registerMetricReader(CoroutineMetricReader(this, exporter))

        try {
            runner.apply(topology)
            runner.run(workload, seed.toLong())

        } finally {
            runner.close()
            telemetry.close()
        }
        val portfolioSimulationResult = exporter.getResult()
        println(
            "Scheduler " +
                "Success=${portfolioSimulationResult.attemptsSuccess} " +
                "Failure=${portfolioSimulationResult.attemptsFailure} " +
                "Error=${portfolioSimulationResult.attemptsError} " +
                "Pending=${portfolioSimulationResult.serversPending} " +
                "Active=${portfolioSimulationResult.serversActive} " +
                "Cpu usage = ${portfolioSimulationResult.meanCpuUsage} " +
                "Cpu demand = ${portfolioSimulationResult.meanCpuDemand}"
        )
        // Note that these values have been verified beforehand
        assertAll(
            { assertEquals(10999592, portfolioSimulationResult.totalIdleTime) { "Idle time incorrect" } },
            { assertEquals(9741207, portfolioSimulationResult.totalActiveTime) { "Active time incorrect" } },
            { assertEquals(0, portfolioSimulationResult.totalStealTime) { "Steal time incorrect" } },
            { assertEquals(0, portfolioSimulationResult.totalLostTime) { "Lost time incorrect" } },
            { assertEquals(7.011413569311495E8, portfolioSimulationResult.totalPowerDraw, 0.01) { "Incorrect power draw" } }
        )
    }
    /**
     * Obtain the trace reader for the test.
     */
    private fun createTestWorkload(fraction: Double, seed: Int = 0): List<VirtualMachine> {
        val source = trace("bitbrains-small").sampleByLoad(fraction)
        return source.resolve(workloadLoader, Random(seed.toLong()))
    }

    /**
     * Obtain the topology factory for the test.
     */
    private fun createTopology(name: String = "topology"): Topology {
        val stream = checkNotNull(object {}.javaClass.getResourceAsStream("/env/$name.txt"))
        return stream.use { clusterTopology(stream) }
    }
}
