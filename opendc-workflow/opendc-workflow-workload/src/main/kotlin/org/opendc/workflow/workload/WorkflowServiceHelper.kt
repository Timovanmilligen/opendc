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

package org.opendc.workflow.workload

import io.opentelemetry.api.metrics.MeterProvider
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.export.MetricProducer
import io.opentelemetry.sdk.metrics.export.MetricReader
import io.opentelemetry.sdk.metrics.export.MetricReaderFactory
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.opendc.compute.api.ComputeClient
import org.opendc.telemetry.sdk.toOtelClock
import org.opendc.workflow.api.Job
import org.opendc.workflow.service.WorkflowService
import java.time.Clock
import java.util.*
import kotlin.coroutines.CoroutineContext

/**
 * Helper class to simulate workflow-based workloads in OpenDC.
 *
 * @param context [CoroutineContext] to run the simulation in.
 * @param clock [Clock] instance tracking simulation time.
 * @param computeClient A [ComputeClient] instance to communicate with the cluster scheduler.
 * @param schedulerSpec The configuration of the workflow scheduler.
 */
public class WorkflowServiceHelper(
    private val context: CoroutineContext,
    private val clock: Clock,
    private val computeClient: ComputeClient,
    private val schedulerSpec: WorkflowSchedulerSpec
) : AutoCloseable {
    /**
     * The [WorkflowService] that is constructed by this runner.
     */
    public val service: WorkflowService

    public var totalJobMakepan: Long = 0

    public var traceJobSize : Long = 0
    /**
     * The [MetricProducer] exposed by the [WorkflowService].
     */
    public lateinit var metricProducer: MetricProducer
        private set

    /**
     * The [MeterProvider] used for the service.
     */
    private val _meterProvider: SdkMeterProvider

    /**
     * The list of [MetricReader]s that have been registered with the runner.
     */
    private val _metricReaders = mutableListOf<MetricReader>()

    init {
        val resource = Resource.builder()
            .put(ResourceAttributes.SERVICE_NAME, "opendc-workflow")
            .build()

        _meterProvider = SdkMeterProvider.builder()
            .setClock(clock.toOtelClock())
            .setResource(resource)
            .registerMetricReader { producer ->
                metricProducer = producer

                val metricReaders = _metricReaders
                object : MetricReader {
                    override fun getPreferredTemporality(): AggregationTemporality = AggregationTemporality.CUMULATIVE
                    override fun flush(): CompletableResultCode {
                        return CompletableResultCode.ofAll(metricReaders.map { it.flush() })
                    }
                    override fun shutdown(): CompletableResultCode {
                        return CompletableResultCode.ofAll(metricReaders.map { it.shutdown() })
                    }
                }
            }
            .build()

        service = WorkflowService(
            context,
            clock,
            _meterProvider,
            computeClient,
            schedulerSpec.schedulingQuantum,
            jobAdmissionPolicy = schedulerSpec.jobAdmissionPolicy,
            jobOrderPolicy = schedulerSpec.jobOrderPolicy,
            taskEligibilityPolicy = schedulerSpec.taskEligibilityPolicy,
            taskOrderPolicy = schedulerSpec.taskOrderPolicy,
        )
    }

    /**
     * Run the specified list of [jobs] using the workflow service and suspend execution until all jobs have
     * finished.
     */
    public suspend fun replay(jobs: List<Job>) {
        traceJobSize = jobs.size.toLong()
        // Sort jobs by their arrival time
        val orderedJobs = jobs.sortedBy { it.metadata.getOrDefault("WORKFLOW_SUBMIT_TIME", Long.MAX_VALUE) as Long }
        if (orderedJobs.isEmpty()) {
            return
        }

        // Wait until the trace is started
        val startTime = orderedJobs[0].metadata.getOrDefault("WORKFLOW_SUBMIT_TIME", Long.MAX_VALUE) as Long
        var offset = 0L

        if (startTime != Long.MAX_VALUE) {
            offset = startTime - clock.millis()
            delay(offset.coerceAtLeast(0))
        }

        coroutineScope {
            for (job in orderedJobs) {
                val submitTime = job.metadata.getOrDefault("WORKFLOW_SUBMIT_TIME", Long.MAX_VALUE) as Long
                if (submitTime != Long.MAX_VALUE) {
                    delay(((submitTime - offset) - clock.millis()).coerceAtLeast(0))
                }

                launch {
                    val start = clock.millis()
                    val jobWaitingTime =  start - submitTime
                    service.invoke(job)
                    val jobMakeSpan = clock.millis() - start
                    totalJobMakepan+= jobMakeSpan
                }
            }
        }
    }

    /**
     * Register a [MetricReader] for this helper.
     *
     * @param factory The factory for the reader to register.
     */
    public fun registerMetricReader(factory: MetricReaderFactory) {
        val reader = factory.apply(metricProducer)
        _metricReaders.add(reader)
    }

    override fun close() {
        computeClient.close()
        service.close()
        _meterProvider.close()
    }
}
