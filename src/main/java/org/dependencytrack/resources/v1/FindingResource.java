/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.dependencytrack.resources.v1;

import alpine.common.logging.Logger;
import alpine.event.framework.Event;
import alpine.persistence.PaginatedResult;
import alpine.server.auth.PermissionRequired;
import alpine.server.resources.AlpineResource;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.ResponseHeader;
import org.dependencytrack.auth.Permissions;
import org.dependencytrack.event.PortfolioRepositoryMetaAnalysisEvent;
import org.dependencytrack.event.ProjectRepositoryMetaAnalysisEvent;
import org.dependencytrack.event.ProjectVulnerabilityAnalysisEvent;
import org.dependencytrack.integrations.FindingPackagingFormat;
import org.dependencytrack.model.Finding;
import org.dependencytrack.model.Project;
import org.dependencytrack.model.Vulnerability;
import org.dependencytrack.persistence.QueryManager;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * JAX-RS resources for processing findings.
 *
 * @author Steve Springett
 * @since 3.1.0
 */
@Path("/v1/finding")
@Api(value = "finding", authorizations = @Authorization(value = "X-Api-Key"))
public class FindingResource extends AlpineResource {

    private static final Logger LOGGER = Logger.getLogger(FindingResource.class);

    @GET
    @Path("/project/{uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Returns a list of all findings for a specific project",
            response = Finding.class,
            responseContainer = "List",
            responseHeaders = @ResponseHeader(name = TOTAL_COUNT_HEADER, response = Long.class, description = "The total number of findings")
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Access to the specified project is forbidden"),
            @ApiResponse(code = 404, message = "The project could not be found")
    })
    @PermissionRequired(Permissions.Constants.VIEW_VULNERABILITY)
    public Response getFindingsByProject(@PathParam("uuid") String uuid,
                                         @ApiParam(value = "Optionally includes suppressed findings")
                                         @QueryParam("suppressed") boolean suppressed,
                                         @ApiParam(value = "Optionally limit findings to specific sources of vulnerability intelligence")
                                         @QueryParam("source") Vulnerability.Source source) {
        try (QueryManager qm = new QueryManager(getAlpineRequest())) {
            final Project project = qm.getObjectByUuid(Project.class, uuid);
            if (project != null) {
                if (qm.hasAccess(super.getPrincipal(), project)) {
                    final PaginatedResult findings = qm.getFindingsPage(project, source, suppressed);
                    return Response.ok(findings.getObjects()).header(TOTAL_COUNT_HEADER, findings.getTotal()).build();
                } else {
                    return Response.status(Response.Status.FORBIDDEN).entity("Access to the specified project is forbidden").build();
                }
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity("The project could not be found.").build();
            }
        }
    }

    @GET
    @Path("/project/{uuid}/export")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Returns the findings for the specified project as FPF"
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Access to the specified project is forbidden"),
            @ApiResponse(code = 404, message = "The project could not be found")
    })
    @PermissionRequired(Permissions.Constants.VIEW_VULNERABILITY)
    public Response exportFindingsByProject(@PathParam("uuid") String uuid) {
        try (QueryManager qm = new QueryManager(getAlpineRequest())) {
            final Project project = qm.getObjectByUuid(Project.class, uuid);
            if (project != null) {
                if (qm.hasAccess(super.getPrincipal(), project)) {
                    final List<Finding> findings = qm.getFindings(project);
                    final FindingPackagingFormat fpf = new FindingPackagingFormat(UUID.fromString(uuid), findings);
                    final Response.ResponseBuilder rb = Response.ok(fpf.getDocument().toString(), "application/json");
                    rb.header("Content-Disposition", "inline; filename=findings-" + uuid + ".fpf");
                    return rb.build();
                } else {
                    return Response.status(Response.Status.FORBIDDEN).entity("Access to the specified project is forbidden").build();
                }
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity("The project could not be found.").build();
            }
        }
    }

    @POST
    @Path("/portfolio/analyze")
    @ApiOperation(value = "Triggers Vulnerability Analysis for the entire portfolio")
    @ApiResponses(value = {
            @ApiResponse(code = 304, message = "Analysis is already in progress"),
            @ApiResponse(code = 401, message = "Unauthorized")
    })
    @PermissionRequired(Permissions.Constants.SYSTEM_CONFIGURATION) // Require admin privileges due to system impact
    public Response analyzePortfolio() {
        LOGGER.info("Portfolio analysis requested by " + super.getPrincipal().getName());
        if (Event.isEventBeingProcessed(PortfolioRepositoryMetaAnalysisEvent.CHAIN_IDENTIFIER)) {
            LOGGER.info("Another portfolio analysis event is already being processed; Dropping");
            return Response.status(Response.Status.NOT_MODIFIED).build();
        }

        Event.dispatch(new PortfolioRepositoryMetaAnalysisEvent());
        return Response.ok().build();
    }

    @POST
    @Path("/project/{uuid}/analyze")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Triggers Vulnerability Analysis on a specific project",
            response = Project.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Access to the specified project is forbidden"),
            @ApiResponse(code = 404, message = "The project could not be found")
    })
    @PermissionRequired(Permissions.Constants.VIEW_VULNERABILITY)
    public Response analyzeProject(
            @ApiParam(value = "The UUID of the project to analyze", required = true)
            @PathParam("uuid") String uuid) {
        try (QueryManager qm = new QueryManager()) {
            final Project project = qm.getObjectByUuid(Project.class, uuid);
            if (project != null) {
                if (qm.hasAccess(super.getPrincipal(), project)) {
                    LOGGER.info("Analysis of project " + project.getUuid() + " requested by " + super.getPrincipal().getName());

                    final ProjectVulnerabilityAnalysisEvent vae = new ProjectVulnerabilityAnalysisEvent(project.getUuid());
                    qm.createReanalyzeSteps(vae.getChainIdentifier());
                    Event.dispatch(vae);
                    final ProjectRepositoryMetaAnalysisEvent projectRepositoryMetaAnalysisEvent = new ProjectRepositoryMetaAnalysisEvent(project.getUuid());
                    Event.dispatch(projectRepositoryMetaAnalysisEvent);

                    return Response.ok(Collections.singletonMap("token", vae.getChainIdentifier())).build();
                } else {
                    return Response.status(Response.Status.FORBIDDEN).entity("Access to the specified project is forbidden").build();
                }
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity("The project could not be found.").build();
            }
        }
    }


}
