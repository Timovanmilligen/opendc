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

package org.opendc.simulator.compute.workload

import org.opendc.simulator.compute.SimMachineContext
import java.time.Duration

/**
 * A [SimWorkload] that replays a workload trace consisting of multiple fragments, each indicating the resource
 * consumption for some period of time.
 *
 * @param trace The trace of fragments to use.
 * @param offset The offset for the timestamps.
 */
public class SimTraceWorkload(private val trace: SimTrace, private val offset: Long = 0L) : SimWorkload {

    override fun onStart(ctx: SimMachineContext) {
        val lifecycle = SimWorkloadLifecycle(ctx)
        for (cpu in ctx.cpus) {
            cpu.startConsumer(lifecycle.waitFor(trace.newSource(cpu.model, offset)))
        }
    }
    override fun onStop(ctx: SimMachineContext) {}

    override fun toString(): String = "SimTraceWorkload"

    public fun getOffset() : Long {
        return offset
    }

    public fun getEndTime() : Long{
        return trace.getEndTime()
    }
    public fun getTrace():SimTrace{
        return trace
    }
    public fun getStartTime():Long{
        return trace.getStartTime()
    }
    public fun getNormalizedRemainingWorkload(now: Long, duration: Duration): SimTraceWorkload {
        return SimTraceWorkload(trace.getNormalizedRemainingTrace(now, duration, offset), offset - now)
    }

    public fun getAverageCpuLoad():Double{
        return trace.getAverageCpuLoad()
    }
    public fun copyTraceWorkload() : SimTraceWorkload{
        return SimTraceWorkload(trace.getTraceCopy(), offset)
    }

    public fun remainingTraceSize() : Int{
        return trace.remainingTraceSize()
    }
}
public interface TraceProgressListener{

    public fun onProgression(idx:Int, now: Long){}

    public fun getTraceProgression() : Int

    public fun onCpuUsed(usage: Double){}

    public fun getCpuUsed() : Double
}
