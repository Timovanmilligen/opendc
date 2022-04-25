package org.opendc.compute.service

import org.opendc.compute.service.scheduler.ComputeScheduler
import org.opendc.compute.service.scheduler.Snapshot

public abstract class SnapshotSimulator {
    public abstract fun simulatePolicy(snapshot: Snapshot, scheduler: ComputeScheduler) : SnapshotMetricExporter.Result
}
