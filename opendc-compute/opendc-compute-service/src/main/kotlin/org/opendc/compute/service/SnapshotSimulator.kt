package org.opendc.compute.service

import org.opendc.compute.service.scheduler.ComputeScheduler

public abstract class SnapshotSimulator {
    public abstract fun simulatePolicy(snapshot: SnapshotParser.ParsedSnapshot, scheduler: ComputeScheduler) : SnapshotMetricExporter.Result
}
