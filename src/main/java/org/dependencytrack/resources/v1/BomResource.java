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
 * Copyright (c) OWASP Foundation. All Rights Reserved.
 */
package org.dependencytrack.resources.v1;

import alpine.common.logging.Logger;
import alpine.event.framework.Event;
import alpine.notification.Notification;
import alpine.notification.NotificationLevel;
import alpine.server.auth.PermissionRequired;
import alpine.server.resources.AlpineResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.cyclonedx.CycloneDxMediaType;
import org.cyclonedx.exception.GeneratorException;
import org.dependencytrack.auth.Permissions;
import org.dependencytrack.event.BomUploadEvent;
import org.dependencytrack.event.kafka.KafkaEventDispatcher;
import org.dependencytrack.model.Component;
import org.dependencytrack.model.Project;
import org.dependencytrack.model.validation.ValidUuid;
import org.dependencytrack.notification.NotificationConstants;
import org.dependencytrack.notification.NotificationGroup;
import org.dependencytrack.notification.NotificationScope;
import org.dependencytrack.notification.vo.BomValidationFailed;
import org.dependencytrack.parser.cyclonedx.CycloneDXExporter;
import org.dependencytrack.parser.cyclonedx.CycloneDxValidator;
import org.dependencytrack.parser.cyclonedx.InvalidBomException;
import org.dependencytrack.persistence.QueryManager;
import org.dependencytrack.resources.v1.problems.InvalidBomProblemDetails;
import org.dependencytrack.resources.v1.problems.ProblemDetails;
import org.dependencytrack.resources.v1.vo.BomSubmitRequest;
import org.dependencytrack.resources.v1.vo.BomUploadResponse;
import org.glassfish.jersey.media.multipart.BodyPartEntity;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.Principal;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static java.util.function.Predicate.not;
import static org.dependencytrack.model.ConfigPropertyConstants.BOM_VALIDATION_ENABLED;

/**
 * JAX-RS resources for processing bill-of-material (bom) documents.
 *
 * @author Steve Springett
 * @since 3.0.0
 */
@Path("/v1/bom")
@Tag(name = "bom")
@SecurityRequirements({
        @SecurityRequirement(name = "ApiKeyAuth"),
        @SecurityRequirement(name = "BearerAuth")
})
public class BomResource extends AlpineResource {

    private static final Logger LOGGER = Logger.getLogger(BomResource.class);

    @GET
    @Path("/cyclonedx/project/{uuid}")
    @Produces({CycloneDxMediaType.APPLICATION_CYCLONEDX_XML, CycloneDxMediaType.APPLICATION_CYCLONEDX_JSON, MediaType.APPLICATION_OCTET_STREAM})
    @Operation(
            summary = "Returns dependency metadata for a project in CycloneDX format",
            description = "<p>Requires permission <strong>VIEW_PORTFOLIO</strong></p>"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Dependency metadata for a project in CycloneDX format",
                    content = @Content(schema = @Schema(type = "string"))
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Access to the specified project is forbidden"),
            @ApiResponse(responseCode = "404", description = "The project could not be found")
    })
    @PermissionRequired(Permissions.Constants.VIEW_PORTFOLIO)
    public Response exportProjectAsCycloneDx(
            @Parameter(description = "The UUID of the project to export", schema = @Schema(type = "string", format = "uuid"), required = true)
            @PathParam("uuid") @ValidUuid String uuid,
            @Parameter(description = "The format to output (defaults to JSON)")
            @QueryParam("format") String format,
            @Parameter(description = "Specifies the CycloneDX variant to export. Value options are 'inventory' and 'withVulnerabilities'. (defaults to 'inventory')")
            @QueryParam("variant") String variant,
            @Parameter(description = "Force the resulting BOM to be downloaded as a file (defaults to 'false')")
            @QueryParam("download") boolean download) {
        try (QueryManager qm = new QueryManager()) {
            final Project project = qm.getObjectByUuid(Project.class, uuid);
            if (project == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("The project could not be found.").build();
            }
            if (!qm.hasAccess(super.getPrincipal(), project)) {
                return Response.status(Response.Status.FORBIDDEN).entity("Access to the specified project is forbidden").build();
            }

            final CycloneDXExporter exporter;
            if (StringUtils.trimToNull(variant) == null || variant.equalsIgnoreCase("inventory")) {
                exporter = new CycloneDXExporter(CycloneDXExporter.Variant.INVENTORY, qm);
            } else if (variant.equalsIgnoreCase("withVulnerabilities")) {
                exporter = new CycloneDXExporter(CycloneDXExporter.Variant.INVENTORY_WITH_VULNERABILITIES, qm);
            } else if (variant.equalsIgnoreCase("vdr")) {
                exporter = new CycloneDXExporter(CycloneDXExporter.Variant.VDR, qm);
            } else {
                return Response.status(Response.Status.BAD_REQUEST).entity("Invalid BOM variant specified.").build();
            }

            try {
                if (StringUtils.trimToNull(format) == null || format.equalsIgnoreCase("JSON")) {
                    if (download) {
                        return Response.ok(exporter.export(exporter.create(project), CycloneDXExporter.Format.JSON), MediaType.APPLICATION_OCTET_STREAM)
                                .header("content-disposition", "attachment; filename=\"" + project.getUuid() + "-" + variant + ".cdx.json\"").build();
                    } else {
                        return Response.ok(exporter.export(exporter.create(project), CycloneDXExporter.Format.JSON),
                                CycloneDxMediaType.APPLICATION_CYCLONEDX_JSON).build();
                    }
                } else if (format.equalsIgnoreCase("XML")) {
                    if (download) {
                        return Response.ok(exporter.export(exporter.create(project), CycloneDXExporter.Format.XML), MediaType.APPLICATION_OCTET_STREAM)
                                .header("content-disposition", "attachment; filename=\"" + project.getUuid() + "-" + variant + ".cdx.xml\"").build();
                    } else {
                        return Response.ok(exporter.export(exporter.create(project), CycloneDXExporter.Format.XML),
                                CycloneDxMediaType.APPLICATION_CYCLONEDX_XML).build();
                    }
                } else {
                    return Response.status(Response.Status.BAD_REQUEST).entity("Invalid BOM format specified.").build();
                }
            } catch (GeneratorException e) {
                LOGGER.error("An error occurred while building a CycloneDX document for export", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }
        }
    }

    @GET
    @Path("/cyclonedx/component/{uuid}")
    @Produces(CycloneDxMediaType.APPLICATION_CYCLONEDX_XML)
    @Operation(
            summary = "Returns dependency metadata for a specific component in CycloneDX format",
            description = "<p>Requires permission <strong>VIEW_PORTFOLIO</strong></p>"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Dependency metadata for a specific component in CycloneDX format",
                    content = @Content(schema = @Schema(type = "string"))
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Access to the specified component is forbidden"),
            @ApiResponse(responseCode = "404", description = "The component could not be found")
    })
    @PermissionRequired(Permissions.Constants.VIEW_PORTFOLIO)
    public Response exportComponentAsCycloneDx(
            @Parameter(description = "The UUID of the component to export", schema = @Schema(type = "string", format = "uuid"), required = true)
            @PathParam("uuid") @ValidUuid String uuid,
            @Parameter(description = "The format to output (defaults to JSON)")
            @QueryParam("format") String format) {
        try (QueryManager qm = new QueryManager()) {
            final Component component = qm.getObjectByUuid(Component.class, uuid);
            if (component == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("The component could not be found.").build();
            }
            if (!qm.hasAccess(super.getPrincipal(), component.getProject())) {
                return Response.status(Response.Status.FORBIDDEN).entity("Access to the specified component is forbidden").build();
            }

            final CycloneDXExporter exporter = new CycloneDXExporter(CycloneDXExporter.Variant.INVENTORY, qm);
            try {
                if (StringUtils.trimToNull(format) == null || format.equalsIgnoreCase("JSON")) {
                    return Response.ok(exporter.export(exporter.create(component), CycloneDXExporter.Format.JSON),
                            CycloneDxMediaType.APPLICATION_CYCLONEDX_JSON).build();
                } else if (format.equalsIgnoreCase("XML")) {
                    return Response.ok(exporter.export(exporter.create(component), CycloneDXExporter.Format.XML),
                            CycloneDxMediaType.APPLICATION_CYCLONEDX_XML).build();
                } else {
                    return Response.status(Response.Status.BAD_REQUEST).entity("Invalid BOM format specified.").build();
                }
            } catch (GeneratorException e) {
                LOGGER.error("An error occurred while building a CycloneDX document for export", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Upload a supported bill of material format document",
            description = """
                    <p>
                      Expects CycloneDX and a valid project UUID. If a UUID is not specified,
                      then the <code>projectName</code> and <code>projectVersion</code> must be specified.
                      Optionally, if <code>autoCreate</code> is specified and <code>true</code> and the project does not exist,
                      the project will be created. In this scenario, the principal making the request will
                      additionally need the <strong>PORTFOLIO_MANAGEMENT</strong>, <strong>PORTFOLIO_MANAGEMENT_CREATE</strong>, 
                      or <strong>PROJECT_CREATION_UPLOAD</strong> permission.
                    </p>
                    <p>
                      The BOM will be validated against the CycloneDX schema. If schema validation fails,
                      a response with problem details in RFC 9457 format will be returned. In this case,
                      the response's content type will be <code>application/problem+json</code>.
                    </p>
                    <p>
                      The maximum allowed length of the <code>bom</code> value is 20'000'000 characters.
                      When uploading large BOMs, the <code>POST</code> endpoint is preferred,
                      as it does not have this limit.
                    </p>
                    <p>Requires permission <strong>BOM_UPLOAD</strong></p>""",
            operationId = "UploadBomBase64Encoded"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Token to be used for checking BOM processing progress",
                    content = @Content(schema = @Schema(implementation = BomUploadResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid BOM",
                    content = @Content(
                            schema = @Schema(implementation = InvalidBomProblemDetails.class),
                            mediaType = ProblemDetails.MEDIA_TYPE_JSON
                    )
            ),
            @ApiResponse(responseCode = "400", description = "The uploaded BOM is invalid"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Access to the specified project is forbidden"),
            @ApiResponse(responseCode = "404", description = "The project could not be found")
    })
    @PermissionRequired(Permissions.Constants.BOM_UPLOAD)
    public Response uploadBom(@Parameter(required = true) BomSubmitRequest request) {
        final Validator validator = getValidator();
        if (request.getProject() != null) { // behavior in v3.0.0
            failOnValidationError(
                    validator.validateProperty(request, "project"),
                    validator.validateProperty(request, "bom")
            );
            try (QueryManager qm = new QueryManager()) {
                final Project project = qm.getObjectByUuid(Project.class, request.getProject());
                return process(qm, project, request.getBom());
            }
        } else { // additional behavior added in v3.1.0
            failOnValidationError(
                    validator.validateProperty(request, "projectName"),
                    validator.validateProperty(request, "projectVersion"),
                    validator.validateProperty(request, "bom")
            );
            try (QueryManager qm = new QueryManager()) {
                Project project = qm.getProject(request.getProjectName(), request.getProjectVersion());
                if (project == null && request.isAutoCreate()) {
                    if (hasPermission(Permissions.Constants.PORTFOLIO_MANAGEMENT) || hasPermission(Permissions.Constants.PORTFOLIO_MANAGEMENT_CREATE) || hasPermission(Permissions.Constants.PROJECT_CREATION_UPLOAD)) {
                        Project parent = null;
                        if (request.getParentUUID() != null || request.getParentName() != null) {
                            if (request.getParentUUID() != null) {
                                failOnValidationError(validator.validateProperty(request, "parentUUID"));
                                parent = qm.getObjectByUuid(Project.class, request.getParentUUID());
                            } else {
                                failOnValidationError(
                                        validator.validateProperty(request, "parentName"),
                                        validator.validateProperty(request, "parentVersion")
                                );
                                final String trimmedParentName = StringUtils.trimToNull(request.getParentName());
                                final String trimmedParentVersion = StringUtils.trimToNull(request.getParentVersion());
                                parent = qm.getProject(trimmedParentName, trimmedParentVersion);
                            }

                            if (parent == null) { // if parent project is specified but not found
                                return Response.status(Response.Status.NOT_FOUND).entity("The parent component could not be found.").build();
                            } else if (!qm.hasAccess(super.getPrincipal(), parent)) {
                                return Response.status(Response.Status.FORBIDDEN).entity("Access to the specified parent project is forbidden").build();
                            }
                        }

                        project = qm.createProject(StringUtils.trimToNull(request.getProjectName()), null, StringUtils.trimToNull(request.getProjectVersion()), request.getProjectTags(), parent, null, true, true);
                        Principal principal = getPrincipal();
                        qm.updateNewProjectACL(project, principal);
                    } else {
                        return Response.status(Response.Status.UNAUTHORIZED).entity("The principal does not have permission to create project.").build();
                    }
                }
                return process(qm, project, request.getBom());
            }
        }
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Upload a supported bill of material format document",
            description = """
                    <p>
                      Expects CycloneDX and a valid project UUID. If a UUID is not specified,
                      then the <code>projectName</code> and <code>projectVersion</code> must be specified.
                      Optionally, if <code>autoCreate</code> is specified and <code>true</code> and the project does not exist,
                      the project will be created. In this scenario, the principal making the request will
                      additionally need the <strong>PORTFOLIO_MANAGEMENT</strong>, <strong>PORTFOLIO_MANAGEMENT_CREATE</strong>, 
                      or <strong>PROJECT_CREATION_UPLOAD</strong> permission.
                    </p>
                    <p>
                      The BOM will be validated against the CycloneDX schema. If schema validation fails,
                      a response with problem details in RFC 9457 format will be returned. In this case,
                      the response's content type will be <code>application/problem+json</code>.
                    </p>
                    <p>Requires permission <strong>BOM_UPLOAD</strong></p>""",
            operationId = "UploadBom"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Token to be used for checking BOM processing progress",
                    content = @Content(schema = @Schema(implementation = BomUploadResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid BOM",
                    content = @Content(
                            schema = @Schema(implementation = InvalidBomProblemDetails.class),
                            mediaType = ProblemDetails.MEDIA_TYPE_JSON
                    )
            ),
            @ApiResponse(responseCode = "400", description = "The uploaded BOM is invalid"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Access to the specified project is forbidden"),
            @ApiResponse(responseCode = "404", description = "The project could not be found")
    })
    @PermissionRequired(Permissions.Constants.BOM_UPLOAD)
    public Response uploadBom(
            @FormDataParam("project") String projectUuid,
            @DefaultValue("false") @FormDataParam("autoCreate") boolean autoCreate,
            @FormDataParam("projectName") String projectName,
            @FormDataParam("projectVersion") String projectVersion,
            @FormDataParam("projectTags") String projectTags,
            @FormDataParam("parentName") String parentName,
            @FormDataParam("parentVersion") String parentVersion,
            @FormDataParam("parentUUID") String parentUUID,
            @Parameter(schema = @Schema(type = "string")) @FormDataParam("bom") final List<FormDataBodyPart> artifactParts
    ) {
        if (projectUuid != null) { // behavior in v3.0.0
            try (QueryManager qm = new QueryManager()) {
                final Project project = qm.getObjectByUuid(Project.class, projectUuid);
                return process(qm, project, artifactParts);
            }
        } else { // additional behavior added in v3.1.0
            try (QueryManager qm = new QueryManager()) {
                final String trimmedProjectName = StringUtils.trimToNull(projectName);
                final String trimmedProjectVersion = StringUtils.trimToNull(projectVersion);
                Project project = qm.getProject(trimmedProjectName, trimmedProjectVersion);
                if (project == null && autoCreate) {
                    if (hasPermission(Permissions.Constants.PORTFOLIO_MANAGEMENT) || hasPermission(Permissions.Constants.PORTFOLIO_MANAGEMENT_CREATE) || hasPermission(Permissions.Constants.PROJECT_CREATION_UPLOAD)) {
                        Project parent = null;
                        if (parentUUID != null || parentName != null) {
                            if (parentUUID != null) {

                                parent = qm.getObjectByUuid(Project.class, parentUUID);
                            } else {
                                final String trimmedParentName = StringUtils.trimToNull(parentName);
                                final String trimmedParentVersion = StringUtils.trimToNull(parentVersion);
                                parent = qm.getProject(trimmedParentName, trimmedParentVersion);
                            }

                            if (parent == null) { // if parent project is specified but not found
                                return Response.status(Response.Status.NOT_FOUND).entity("The parent component could not be found.").build();
                            } else if (!qm.hasAccess(super.getPrincipal(), parent)) {
                                return Response.status(Response.Status.FORBIDDEN).entity("Access to the specified parent project is forbidden").build();
                            }
                        }
                        final List<org.dependencytrack.model.Tag> tags = (projectTags != null && !projectTags.isBlank())
                                ? Arrays.stream(projectTags.split(",")).map(String::trim).filter(not(String::isEmpty)).map(org.dependencytrack.model.Tag::new).toList()
                                : null;
                        project = qm.createProject(trimmedProjectName, null, trimmedProjectVersion, tags, parent, null, true, true);
                        Principal principal = getPrincipal();
                        qm.updateNewProjectACL(project, principal);
                    } else {
                        return Response.status(Response.Status.UNAUTHORIZED).entity("The principal does not have permission to create project.").build();
                    }
                }
                return process(qm, project, artifactParts);
            }
        }
    }

    /**
     * Common logic that processes a BOM given a project and encoded payload.
     */
    private Response process(QueryManager qm, Project project, String encodedBomData) {
        if (project != null) {
            if (!qm.hasAccess(super.getPrincipal(), project)) {
                return Response.status(Response.Status.FORBIDDEN).entity("Access to the specified project is forbidden").build();
            }

            final File bomFile;
            try (final var encodedInputStream = new ByteArrayInputStream(encodedBomData.getBytes(StandardCharsets.UTF_8));
                 final var decodedInputStream = Base64.getDecoder().wrap(encodedInputStream);
                 final var byteOrderMarkInputStream = new BOMInputStream(decodedInputStream)) {
                bomFile = validateAndStoreBom(IOUtils.toByteArray(byteOrderMarkInputStream), project);
            } catch (IOException e) {
                LOGGER.error("An unexpected error occurred while validating or storing a BOM uploaded to project: " + project.getUuid(), e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }

            final BomUploadEvent bomUploadEvent = new BomUploadEvent(qm.detach(Project.class, project.getId()), bomFile);
            qm.createWorkflowSteps(bomUploadEvent.getChainIdentifier());
            Event.dispatch(bomUploadEvent);

            BomUploadResponse bomUploadResponse = new BomUploadResponse();
            bomUploadResponse.setToken(bomUploadEvent.getChainIdentifier());
            return Response.ok(bomUploadResponse).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).entity("The project could not be found.").build();
        }
    }

    /**
     * Common logic that processes a BOM given a project and list of multi-party form objects containing decoded payloads.
     */
    private Response process(QueryManager qm, Project project, List<FormDataBodyPart> artifactParts) {
        for (final FormDataBodyPart artifactPart : artifactParts) {
            final BodyPartEntity bodyPartEntity = (BodyPartEntity) artifactPart.getEntity();
            if (project != null) {
                if (!qm.hasAccess(super.getPrincipal(), project)) {
                    return Response.status(Response.Status.FORBIDDEN).entity("Access to the specified project is forbidden").build();
                }

                final File bomFile;
                try (final var inputStream = bodyPartEntity.getInputStream();
                     final var byteOrderMarkInputStream = new BOMInputStream(inputStream)) {
                    bomFile = validateAndStoreBom(IOUtils.toByteArray(byteOrderMarkInputStream), project);
                } catch (IOException e) {
                    LOGGER.error("An unexpected error occurred while validating or storing a BOM uploaded to project: " + project.getUuid(), e);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
                }

                // todo: make option to combine all the bom data so components are reconciled in a single pass.
                // todo: https://github.com/DependencyTrack/dependency-track/issues/130
                final BomUploadEvent bomUploadEvent = new BomUploadEvent(qm.detach(Project.class, project.getId()), bomFile);

                qm.createWorkflowSteps(bomUploadEvent.getChainIdentifier());
                Event.dispatch(bomUploadEvent);

                BomUploadResponse bomUploadResponse = new BomUploadResponse();
                bomUploadResponse.setToken(bomUploadEvent.getChainIdentifier());
                return Response.ok(bomUploadResponse).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity("The project could not be found.").build();
            }
        }
        return Response.ok().build();
    }

    private File validateAndStoreBom(final byte[] bomBytes, final Project project) throws IOException {
        validate(bomBytes, project);

        // TODO: Store externally so other instances of the API server can pick it up.
        //   https://github.com/CycloneDX/cyclonedx-bom-repo-server
        final java.nio.file.Path tmpPath = Files.createTempFile("dtrack-bom-%s".formatted(project.getUuid()), null);
        final File tmpFile = tmpPath.toFile();
        tmpFile.deleteOnExit();

        LOGGER.debug("Writing BOM for project %s to %s".formatted(project.getUuid(), tmpPath));
        try (final var tmpOutputStream = Files.newOutputStream(tmpPath, StandardOpenOption.WRITE)) {
            tmpOutputStream.write(bomBytes);
        }

        return tmpFile;
    }

    static void validate(final byte[] bomBytes, final Project project) {
        try (final var qm = new QueryManager()) {
            if (!qm.isEnabled(BOM_VALIDATION_ENABLED)) {
                return;
            }
        }

        try {
            CycloneDxValidator.getInstance().validate(bomBytes);
        } catch (InvalidBomException e) {
            final var problemDetails = new InvalidBomProblemDetails();
            problemDetails.setStatus(400);
            problemDetails.setTitle("The uploaded BOM is invalid");
            problemDetails.setDetail(e.getMessage());
            if (!e.getValidationErrors().isEmpty()) {
                problemDetails.setErrors(e.getValidationErrors());
            }

            final Response response = Response.status(Response.Status.BAD_REQUEST)
                    .header("Content-Type", ProblemDetails.MEDIA_TYPE_JSON)
                    .entity(problemDetails)
                    .build();

            final var bomEncoded = Base64.getEncoder()
                    .encodeToString(bomBytes);
            dispatchBomValidationFailedNotification(project, bomEncoded, problemDetails.getErrors());

            throw new WebApplicationException(response);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to validate BOM", e);
            final Response response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            throw new WebApplicationException(response);
        }
    }

    private static void dispatchBomValidationFailedNotification(Project project, String bom, List<String> errors) {
        final KafkaEventDispatcher eventDispatcher = new KafkaEventDispatcher();
        eventDispatcher.dispatchNotification(new Notification()
                .scope(NotificationScope.PORTFOLIO)
                .group(NotificationGroup.BOM_VALIDATION_FAILED)
                .level(NotificationLevel.ERROR)
                .title(NotificationConstants.Title.BOM_VALIDATION_FAILED)
                .content("An error occurred while validating a BOM")
                .subject(new BomValidationFailed(project, bom, errors)));
    }
}
