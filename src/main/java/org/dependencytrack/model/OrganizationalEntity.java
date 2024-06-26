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
package org.dependencytrack.model;

import alpine.server.json.TrimmedStringArrayDeserializer;
import alpine.server.json.TrimmedStringDeserializer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Model class for tracking organizational entities (provider, supplier, manufacturer, etc).
 *
 * @author Steve Springett
 * @since 4.2.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrganizationalEntity implements Serializable {

    private static final long serialVersionUID = 5333594855427723634L;

    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @JsonView(JsonViews.MetadataTools.class)
    private String name;

    @JsonDeserialize(using = TrimmedStringArrayDeserializer.class)
    @JsonView(JsonViews.MetadataTools.class)
    private String[] urls;

    @JsonView(JsonViews.MetadataTools.class)
    private List<OrganizationalContact> contacts;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getUrls() {
        return urls;
    }

    public void setUrls(String[] urls) {
        this.urls = urls;
    }

    public List<OrganizationalContact> getContacts() {
        return contacts;
    }

    public void addContact(OrganizationalContact contact) {
        if (this.contacts == null) {
            this.contacts = new ArrayList<>();
        }
        this.contacts.add(contact);
    }

    public void setContacts(List<OrganizationalContact> contacts) {
        this.contacts = contacts;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final OrganizationalEntity that = (OrganizationalEntity) o;
        return Objects.equals(name, that.name) && Arrays.equals(urls, that.urls) && Objects.equals(contacts, that.contacts);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, contacts);
        result = 31 * result + Arrays.hashCode(urls);
        return result;
    }
}
