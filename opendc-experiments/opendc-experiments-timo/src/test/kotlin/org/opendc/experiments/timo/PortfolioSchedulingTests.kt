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

import mu.KotlinLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.opendc.compute.service.SnapshotMetricExporter
import org.opendc.compute.service.scheduler.*
import org.opendc.compute.service.scheduler.filters.ComputeFilter
import org.opendc.compute.service.scheduler.filters.RamFilter
import org.opendc.compute.service.scheduler.filters.VCpuFilter
import org.opendc.compute.service.scheduler.weights.CoreRamWeigher
import org.opendc.compute.service.scheduler.weights.VCpuCapacityWeigher
import org.opendc.compute.workload.*
import org.opendc.compute.workload.telemetry.SdkTelemetryManager
import org.opendc.compute.workload.topology.Topology
import org.opendc.compute.workload.topology.apply
import org.opendc.experiments.capelin.topology.clusterTopology
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

    /**
    * Test correctly loading a queue into the portfolio scheduler simulator.
     */
    @Test
    fun testQueueLoading() = runBlockingSimulation {
        val seed = 1
        val workload = createTestWorkload("bitbrains-small",0.25, seed)
        val telemetry = SdkTelemetryManager(clock)
        val scheduler = PortfolioScheduler(createSinglePolicyPortfolio(), Duration.ofDays(100000),Duration.ofMillis(20))
        val runner = ComputeServiceHelper(
            coroutineContext,
            clock,
            telemetry,
            scheduler,
            schedulingQuantum = Duration.ofMillis(1)
        )
        val topology = createTopology("single")

        telemetry.registerMetricReader(CoroutineMetricReader(this, exporter))

        try {
            runner.apply(topology)
            runner.run(workload, seed.toLong())

        } finally {
            runner.close()
            telemetry.close()
        }
        val traceResult = exporter.getResult()
        val portfolioSimulationResult = scheduler.snapshotHistory.first().second
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
        // Test that the simulated result from the portfolio scheduler is the same as the actual result from the trace.
        assertAll(
            { assertEquals(traceResult.totalIdleTime, portfolioSimulationResult.totalIdleTime) { "Idle time incorrect" } },
            { assertEquals(traceResult.totalActiveTime, portfolioSimulationResult.totalActiveTime) { "Active time incorrect" } },
            { assertEquals(traceResult.totalStealTime, portfolioSimulationResult.totalStealTime) { "Steal time incorrect" } },
            { assertEquals(traceResult.totalLostTime, portfolioSimulationResult.totalLostTime) { "Lost time incorrect" } },
            { assertEquals(traceResult.totalPowerDraw, portfolioSimulationResult.totalPowerDraw, 0.01) { "Incorrect power draw" } }
        )
    }
    /**
     *
     */
    @Test
    fun testActiveHostLoading() = runBlockingSimulation {
        val seed = 1
        val workload = createTestWorkload("bitbrains-small",1.0, seed)
        val telemetry = SdkTelemetryManager(clock)
        val scheduler = PortfolioScheduler(createSinglePolicyPortfolio(), Duration.ofMinutes(5),Duration.ofMillis(20))
        val runner = ComputeServiceHelper(
            coroutineContext,
            clock,
            telemetry,
            scheduler,
            schedulingQuantum = Duration.ofMillis(1)
        )
        val topology = createTopology("single")

        telemetry.registerMetricReader(CoroutineMetricReader(this, exporter))

        try {
            runner.apply(topology)
            runner.run(workload, seed.toLong())

        } finally {
            runner.close()
            telemetry.close()
        }
        val traceResult = exporter.getResult()
        val portfolioSimulationResult = scheduler.snapshotHistory.first().second
        println(
            "Scheduler " +
                "Success=${traceResult.attemptsSuccess} " +
                "Failure=${traceResult.attemptsFailure} " +
                "Error=${traceResult.attemptsError} " +
                "Pending=${traceResult.serversPending} " +
                "Active=${traceResult.serversActive}" +
                "Cpu usage = ${traceResult.meanCpuUsage} " +
                "Cpu demand = ${traceResult.meanCpuDemand}"
        )
        // Test that the simulated result from the portfolio scheduler is the same as the actual result from the trace.
        assertAll(
            { assertEquals(traceResult.totalIdleTime, portfolioSimulationResult.totalIdleTime) { "Idle time incorrect" } },
            { assertEquals(traceResult.totalActiveTime, portfolioSimulationResult.totalActiveTime) { "Active time incorrect" } },
            { assertEquals(traceResult.totalStealTime, portfolioSimulationResult.totalStealTime) { "Steal time incorrect" } },
            { assertEquals(traceResult.totalLostTime, portfolioSimulationResult.totalLostTime) { "Lost time incorrect" } },
            { assertEquals(traceResult.totalPowerDraw, portfolioSimulationResult.totalPowerDraw, 0.01) { "Incorrect power draw" } }
        )
    }
    /**
     * Test a small simulation setup.
     */
    @Test
    fun testSmall() = runBlockingSimulation {
        val seed = 1
        val workload = createTestWorkload("bitbrains-small",1.0, seed)
        val telemetry = SdkTelemetryManager(clock)
        val runner = ComputeServiceHelper(
            coroutineContext,
            clock,
            telemetry,
            FilterScheduler(
                filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
                weighers = listOf(CoreRamWeigher(multiplier = 1.0))
            ),
            schedulingQuantum = Duration.ofMillis(1)

        )
        val topology = createTopology("single")

        telemetry.registerMetricReader(CoroutineMetricReader(this, exporter))

        try {
            runner.apply(topology)
            runner.run(workload, seed.toLong())

        } finally {
            runner.close()
            telemetry.close()
        }
        val result = exporter.getResult()

        println(
            "Scheduler " +
                "Success=${result.attemptsSuccess} " +
                "Failure=${result.attemptsFailure} " +
                "Error=${result.attemptsError} " +
                "Pending=${result.serversPending} " +
                "Active=${result.serversActive}"
        )
        // Note that these values have been verified beforehand
        assertAll(
            { assertEquals(10999592, result.totalIdleTime) { "Idle time incorrect" } },
            { assertEquals(9741207, result.totalActiveTime) { "Active time incorrect" } },
            { assertEquals(0, result.totalStealTime) { "Steal time incorrect" } },
            { assertEquals(0, result.totalLostTime) { "Lost time incorrect" } },
            { assertEquals(7.011413569311495E8, result.totalPowerDraw, 0.01) { "Incorrect power draw" } }
        )
    }
    /**
     * Obtain the trace reader for the test.
     */
    private fun createTestWorkload(traceName: String,fraction: Double, seed: Int = 0): List<VirtualMachine> {
        val source = trace(traceName).sampleByLoad(fraction)
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
