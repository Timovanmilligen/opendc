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

package org.opendc.compute.service.internal

import kotlinx.coroutines.*
import mu.KotlinLogging
import org.opendc.common.util.Pacer
import org.opendc.compute.api.*
import org.opendc.compute.service.ComputeService
import org.opendc.compute.service.driver.Host
import org.opendc.compute.service.driver.HostListener
import org.opendc.compute.service.driver.HostState
import org.opendc.compute.service.scheduler.ComputeScheduler
import org.opendc.compute.service.telemetry.SchedulerStats
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.max

/**
 * Internal implementation of the OpenDC Compute service.
 *
 * @param context The [CoroutineContext] to use in the service.
 * @param clock The clock instance to use.
 * @param scheduler The scheduler implementation to use.
 * @param schedulingQuantum The interval between scheduling cycles.
 */
public class ComputeServiceImpl(
    private val context: CoroutineContext,
    private val clock: Clock,
    private val scheduler: ComputeScheduler,
    schedulingQuantum: Duration
) : ComputeService, HostListener {
    /**
     * The [CoroutineScope] of the service bounded by the lifecycle of the service.
     */
    private val scope = CoroutineScope(context + Job())

    /**
     * The logger instance of this server.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * The [Random] instance used to generate unique identifiers for the objects.
     */
    private val random = Random(0)

    /**
     * A mapping from Host to active Servers.
     */
    public val hostToServers: MutableMap<Host, MutableList<Server>> = mutableMapOf()

    /**
     * A mapping from host to host view.
     */
    private val hostToView = mutableMapOf<Host, HostView>()

    /**
     * The available hypervisors.
     */
    private val availableHosts: MutableSet<HostView> = mutableSetOf()

    /**
     * The servers that should be launched by the service.
     */
    private val queue: Deque<SchedulingRequest> = ArrayDeque()

    /**
     * The active servers in the system.
     */
    private val activeServers: MutableMap<Server, Host> = mutableMapOf()

    /**
     * The registered flavors for this compute service.
     */
    internal val flavorById = mutableMapOf<UUID, InternalFlavor>()

    /**
     * The registered images for this compute service.
     */
    internal val imageById = mutableMapOf<UUID, InternalImage>()

    /**
     * The registered servers for this compute service.
     */
    private val serverById = mutableMapOf<UUID, InternalServer>()
    override val servers: MutableList<Server> = mutableListOf()

    private var maxCores = 0
    private var maxMemory = 0L
    private var _attemptsSuccess = 0L
    private var _attemptsFailure = 0L
    private var _attemptsError = 0L
    private var _serversPending = 0
    private var _serversActive = 0


    /**
     * The [Pacer] to use for scheduling the scheduler cycles.
     */
    private val pacer = Pacer(scope.coroutineContext, clock, schedulingQuantum.toMillis()) { doSchedule() }

    override val hosts: Set<Host>
        get() = hostToView.keys

    override fun newClient(): ComputeClient {
        check(scope.isActive) { "Service is already closed" }
        return object : ComputeClient {
            private var isClosed: Boolean = false

            override suspend fun queryFlavors(): List<Flavor> {
                check(!isClosed) { "Client is already closed" }

                return flavorById.values.map { ClientFlavor(it) }
            }

            override suspend fun findFlavor(id: UUID): Flavor? {
                check(!isClosed) { "Client is already closed" }

                return flavorById[id]?.let { ClientFlavor(it) }
            }

            override suspend fun newFlavor(
                name: String,
                cpuCount: Int,
                memorySize: Long,
                labels: Map<String, String>,
                meta: Map<String, Any>
            ): Flavor {
                check(!isClosed) { "Client is already closed" }

                val uid = UUID(clock.millis(), random.nextLong())
                val flavor = InternalFlavor(
                    this@ComputeServiceImpl,
                    uid,
                    name,
                    cpuCount,
                    memorySize,
                    labels,
                    meta
                )

                flavorById[uid] = flavor

                return ClientFlavor(flavor)
            }

            override suspend fun queryImages(): List<Image> {
                check(!isClosed) { "Client is already closed" }

                return imageById.values.map { ClientImage(it) }
            }

            override suspend fun findImage(id: UUID): Image? {
                check(!isClosed) { "Client is already closed" }

                return imageById[id]?.let { ClientImage(it) }
            }

            override suspend fun newImage(name: String, labels: Map<String, String>, meta: Map<String, Any>): Image {
                check(!isClosed) { "Client is already closed" }

                val uid = UUID(clock.millis(), random.nextLong())
                val image = InternalImage(this@ComputeServiceImpl, uid, name, labels, meta)

                imageById[uid] = image

                return ClientImage(image)
            }

            override suspend fun newServer(
                name: String,
                image: Image,
                flavor: Flavor,
                labels: Map<String, String>,
                meta: Map<String, Any>,
                start: Boolean
            ): Server {
                check(!isClosed) { "Client is closed" }

                val uid = UUID(clock.millis(), random.nextLong())
                val server = InternalServer(
                    this@ComputeServiceImpl,
                    uid,
                    name,
                    requireNotNull(flavorById[flavor.uid]) { "Unknown flavor" },
                    requireNotNull(imageById[image.uid]) { "Unknown image" },
                    labels.toMutableMap(),
                    meta.toMutableMap()
                )
                serverById[uid] = server
                servers.add(server)

                if (start) {
                    server.start()
                }

                return ClientServer(server)
            }

            override suspend fun findServer(id: UUID): Server? {
                check(!isClosed) { "Client is already closed" }

                return serverById[id]?.let { ClientServer(it) }
            }

            override suspend fun queryServers(): List<Server> {
                check(!isClosed) { "Client is already closed" }

                return serverById.values.map { ClientServer(it) }
            }

            override fun close() {
                isClosed = true
            }

            override fun toString(): String = "ComputeClient"
        }
    }

    override fun addHost(host: Host) {
        // Check if host is already known
        if (host in hostToView) {
            return
        }

        val hv = HostView(host)
        maxCores = max(maxCores, host.model.cpuCount)
        maxMemory = max(maxMemory, host.model.memoryCapacity)
        hostToView[host] = hv

        if (host.state == HostState.UP) {
            availableHosts += hv
        }

        scheduler.addHost(hv)
        host.addListener(this)
    }

    override fun removeHost(host: Host) {
        val view = hostToView.remove(host)
        if (view != null) {
            availableHosts.remove(view)
            scheduler.removeHost(view)
            host.removeListener(this)
        }
    }

    override fun lookupHost(server: Server): Host? {
        val internal = requireNotNull(serverById[server.uid]) { "Invalid server passed to lookupHost" }
        return internal.host
    }

    override fun close() {
        hostToView.forEach { scheduler.removeHost(it.value) }
        scope.cancel()
    }

    override fun getSchedulerStats(): SchedulerStats {
        return SchedulerStats(
            availableHosts.size,
            hostToView.size - availableHosts.size,
            _attemptsSuccess,
            _attemptsFailure,
            _attemptsError,
            _serversPending,
            _serversActive
        )
    }

    internal fun schedule(server: InternalServer): SchedulingRequest {
        // logger.debug { "Enqueueing server ${server.uid} to be assigned to host." }
        val now = clock.millis()
        val request = SchedulingRequest(server, now)

        server.launchedAt = Instant.ofEpochMilli(now)
        queue.add(request)
        _serversPending++
        requestSchedulingCycle()
        return request
    }

    internal fun delete(flavor: InternalFlavor) {
        flavorById.remove(flavor.uid)
    }

    internal fun delete(image: InternalImage) {
        imageById.remove(image.uid)
    }

    internal fun delete(server: InternalServer) {
        serverById.remove(server.uid)
        servers.remove(server)
    }

    /**
     * Indicate that a new scheduling cycle is needed due to a change to the service's state.
     */
    private fun requestSchedulingCycle() {
        // Bail out in case the queue is empty.
        if (queue.isEmpty()) {
            return
        }
        pacer.enqueue()
    }

    /**
     * Run a single scheduling iteration.
     */
    private fun doSchedule() {
        while (queue.isNotEmpty()) {
            val request = queue.peek()

            if (request.isCancelled) {
                queue.poll()
                _serversPending--
                continue
            }

            val server = request.server
            val preference: HostView? = (server.meta["host-preference"] as? Host?)?.let { hostToView[it] }
            val hv = preference ?: scheduler.select(server)

            if (hv == null || !hv.host.canFit(server)) {
                logger.trace { "Server $server selected for scheduling but no capacity available for it at the moment" }

                if (server.flavor.memorySize > maxMemory || server.flavor.cpuCount > maxCores) {
                    // Remove the incoming image
                    queue.poll()
                    _serversPending--
                    _attemptsFailure++

                    logger.warn { "Failed to spawn $server: does not fit [${clock.instant()}]" }

                    server.state = ServerState.TERMINATED
                    continue
                } else {
                    break
                }
            }

            val host = hv.host

            // Remove request from queue
            queue.poll()
            _serversPending--

            // logger.info { "Assigned server $server to host $host." }

            // Speculatively update the hypervisor view information to prevent other images in the queue from
            // deciding on stale values.
            hv.instanceCount++
            hv.provisionedCores += server.flavor.cpuCount
            hv.availableMemory -= server.flavor.memorySize // XXX Temporary hack

            scope.launch {
                try {
                    server.host = host
                    host.spawn(server)
                    activeServers[server] = host

                    _serversActive++
                    _attemptsSuccess++
                    // Track servers on each host
                    host.let {
                        if (hostToServers[it].isNullOrEmpty()) {
                            hostToServers[it] = mutableListOf(server)
                        } else {
                            hostToServers[it]?.add(server)
                        }
                    }
                } catch (e: Throwable) {
                    logger.error(e) { "Failed to deploy VM" }

                    hv.instanceCount--
                    hv.provisionedCores -= server.flavor.cpuCount
                    hv.availableMemory += server.flavor.memorySize

                    _attemptsError++
                }
            }
        }
    }

    /**
     * A request to schedule an [InternalServer] onto one of the [Host]s.
     */
    internal data class SchedulingRequest(val server: InternalServer, val submitTime: Long) {
        /**
         * A flag to indicate that the request is cancelled.
         */
        var isCancelled: Boolean = false
    }

    override fun onStateChanged(host: Host, newState: HostState) {
        when (newState) {
            HostState.UP -> {
                logger.debug { "[${clock.instant()}] Host ${host.uid} state changed: $newState" }

                val hv = hostToView[host]
                if (hv != null) {
                    // Corner case for when the hypervisor already exists
                    availableHosts += hv
                }

                // Re-schedule on the new machine
                requestSchedulingCycle()
            }
            HostState.DOWN -> {
                // logger.debug { "[${clock.instant()}] Host ${host.uid} state changed: $newState" }

                val hv = hostToView[host] ?: return
                availableHosts -= hv

                requestSchedulingCycle()
            }
        }
    }

    override fun onStateChanged(host: Host, server: Server, newState: ServerState) {
        require(server is InternalServer) { "Invalid server type passed to service" }

        if (server.host != host) {
            // This can happen when a server is rescheduled and started on another machine, while being deleted from
            // the old machine.
            return
        }

        server.state = newState

        if (newState == ServerState.TERMINATED || newState == ServerState.DELETED) {
            logger.info { "[${clock.instant()}] Server ${server.uid} ${server.name} ${server.flavor} finished." }
            if (activeServers.remove(server) != null) {
                _serversActive--
                hostToServers[host]?.remove(server)
            }

            val hv = hostToView[host]
            if (hv != null) {
                hv.provisionedCores -= server.flavor.cpuCount
                hv.instanceCount--
                hv.availableMemory += server.flavor.memorySize
            } else {
                logger.error { "Unknown host $host" }
            }

            // Try to reschedule if needed
            requestSchedulingCycle()
        }
    }
}
