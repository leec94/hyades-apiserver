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
package org.dependencytrack.resources.v1.problems;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.net.URI;

/**
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457.html">RFC 9457</a>
 * @since 4.11.0
 */
@ApiModel(
        description = "An RFC 9457 problem object",
        subTypes = InvalidBomProblemDetails.class
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetails {

    public static final String MEDIA_TYPE_JSON = "application/problem+json";

    @ApiModelProperty(
            value = "A URI reference that identifies the problem type",
            example = "https://api.example.org/foo/bar/example-problem"
    )
    private URI type;

    @ApiModelProperty(
            value = "HTTP status code generated by the origin server for this occurrence of the problem",
            example = "400"
    )
    private Integer status;

    @ApiModelProperty(
            value = "Short, human-readable summary of the problem type",
            example = "Example title",
            required = true
    )
    private String title;

    @ApiModelProperty(
            value = "Human-readable explanation specific to this occurrence of the problem",
            example = "Example detail"
    )
    private String detail;

    @ApiModelProperty(
            value = "Reference URI that identifies the specific occurrence of the problem",
            example = "https://api.example.org/foo/bar/example-instance"
    )
    private URI instance;

    public URI getType() {
        return type;
    }

    public void setType(final URI type) {
        this.type = type;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(final Integer status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(final String detail) {
        this.detail = detail;
    }

    public URI getInstance() {
        return instance;
    }

    public void setInstance(final URI instance) {
        this.instance = instance;
    }

}