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

package org.opendc.compute.service.driver.telemetry

/**
 * Statistics about the CPUs of a guest.
 *
 * @property activeTime The cumulative time (in seconds) that the CPUs of the guest were actively running.
 * @property idleTime The cumulative time (in seconds) the CPUs of the guest were idle.
 * @property stealTime The cumulative CPU time (in seconds) that the guest was ready to run, but not granted time by the host.
 * @property lostTime The cumulative CPU time (in seconds) that was lost due to interference with other machines.
 * @property capacity The available CPU capacity of the guest (in MHz).
 * @property usage Amount of CPU resources (in MHz) actually used by the guest.
 * @property utilization Utilization of the CPU resources (in %) relative to the total CPU capacity.
 */
public data class GuestCpuStats(
    val activeTime: Long,
    val idleTime: Long,
    val stealTime: Long,
    val lostTime: Long,
    val capacity: Double,
    val usage: Double,
    val utilization: Double
)
