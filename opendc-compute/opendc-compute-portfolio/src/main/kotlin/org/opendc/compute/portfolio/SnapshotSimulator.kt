package org.opendc.compute.portfolio

import org.opendc.compute.service.scheduler.ComputeScheduler

public abstract class SnapshotSimulator {
    public abstract fun simulatePolicy(snapshot: SnapshotParser.ParsedSnapshot, scheduler: ComputeScheduler): SnapshotMetricExporter.Result
}
