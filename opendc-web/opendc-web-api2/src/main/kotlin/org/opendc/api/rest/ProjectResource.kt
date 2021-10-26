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

package org.opendc.api.rest

import javax.ws.rs.*

/**
 * A resource representing the created projects.
 */
@Path("/projects")
class ProjectResource {
    /**
     * Obtain all the projects of the current user.
     */
    @GET
    fun getAll() = emptyList<String>()

    /**
     * Create a new project for the current user.
     */
    @POST
    fun create() = "Test"

    /**
     * Obtain a single project by its identifier.
     */
    @GET
    @Path("{id}")
    fun get() = "Test"

    /**
     * Update a project's details.
     */
    @PUT
    @Path("{id}")
    fun update() = "Test"

    /**
     * Delete a project.
     */
    @DELETE
    @Path("{id}")
    fun delete() = "Test"

    /**
     * Obtain the topologies of a project.
     */
    @GET
    @Path("{id}/topologies")
    fun getTopologies() = "Test"

    /**
     * Create a topology for this project.
     */
    @POST
    @Path("{id}/topologies")
    fun createTopology() = "Test"

    /**
     * Obtain the portfolios of a project.
     */
    @GET
    @Path("{id}/portfolios")
    fun getPortfolios() = "Test"

    /**
     * Create a topology for this project.
     */
    @POST
    @Path("{id}/portfolios")
    fun createPortfolio() = "Test"
}
