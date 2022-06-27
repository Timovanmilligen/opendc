package org.opendc.compute.service.scheduler

import org.opendc.compute.api.Server
import org.opendc.compute.service.ComputeService
import org.opendc.compute.service.internal.HostView

/**
 * A [ComputeScheduler] implementation which returns the first [HostView] which can fit a [Server].
 */
public class FFScheduler : ComputeScheduler {

    /**
     * The pool of hosts available to the scheduler.
     */
    private val hosts = mutableListOf<HostView>()


    override fun addHost(host: HostView) {
        hosts.add(host)
    }

    override fun removeHost(host: HostView) {
        hosts.remove(host)
    }

    override fun select(server: Server): HostView? {
        hosts.forEach { host ->
            if (host.host.canFit(server)){
                return host
            }
        }
        return null
    }

    override fun toString() : String = "First_Fit"
}
