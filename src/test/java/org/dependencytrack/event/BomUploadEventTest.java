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
package org.dependencytrack.event;

import org.dependencytrack.model.Project;
import org.dependencytrack.proto.storage.v1alpha1.FileMetadata;
import org.junit.Assert;
import org.junit.Test;

public class BomUploadEventTest {

    @Test
    public void testFileConstructor() {
        Project project = new Project();
        FileMetadata fileMetadata = FileMetadata.getDefaultInstance();
        BomUploadEvent event = new BomUploadEvent(project, FileMetadata.getDefaultInstance());
        Assert.assertEquals(project, event.getProject());
        Assert.assertEquals(fileMetadata, event.getFileMetadata());
    }
}
