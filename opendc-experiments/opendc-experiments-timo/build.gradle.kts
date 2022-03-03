/*
 * Copyright (c) 2019 AtLarge Research
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

description = "Experiments for Timo work"

/* Build configuration */
plugins {
    `experiment-conventions`
    `testing-conventions`
    `benchmark-conventions`
}

dependencies {
    api(projects.opendcWorkflow.opendcWorkflowApi)
    api(projects.opendcWorkflow.opendcWorkflowService)
    api(projects.opendcCompute.opendcComputeApi)
    api(projects.opendcTelemetry.opendcTelemetryApi)
    api("javax.persistence:javax.persistence-api:2.2")
    api("io.jenetics:jenetics:4.4.0")
    implementation(projects.opendcCommon)
    implementation(libs.kotlin.logging)
    testImplementation(projects.opendcWorkflow.opendcWorkflowWorkload)
    testImplementation(projects.opendcCompute.opendcComputeWorkload)
    implementation(projects.opendcCompute.opendcComputeService)
    testImplementation(projects.opendcSimulator.opendcSimulatorCore)
    testImplementation(projects.opendcTrace.opendcTraceApi)
    testImplementation(projects.opendcTelemetry.opendcTelemetrySdk)
    testRuntimeOnly(projects.opendcTrace.opendcTraceGwf)
    testRuntimeOnly(libs.log4j.slf4j)
}
