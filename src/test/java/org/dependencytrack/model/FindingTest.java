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

import org.dependencytrack.PersistenceCapableTest;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class FindingTest extends PersistenceCapableTest {

    private final UUID projectUuid = UUID.randomUUID();
    private final Date attributedOn = new Date();
    private final Finding finding = new Finding(projectUuid, "component-uuid", "component-name", "component-group",
            "component-version", "component-purl", "component-cpe", "vuln-uuid", "vuln-source", "vuln-vulnId", "vuln-title",
            "vuln-subtitle", "vuln-description", "vuln-recommendation", Severity.HIGH, BigDecimal.valueOf(7.2), BigDecimal.valueOf(8.4), "cvssV2-vector", "cvssV3-vector", BigDecimal.valueOf(1.25), BigDecimal.valueOf(1.75), BigDecimal.valueOf(1.3),
            "owasp-vector", BigDecimal.valueOf(0.5), BigDecimal.valueOf(0.9), null, AnalyzerIdentity.INTERNAL_ANALYZER, attributedOn, null, null, AnalysisState.NOT_AFFECTED, true);

    @Test
    public void testComponent() {
        Map<String, Object> map = finding.getComponent();
        Assert.assertEquals("component-uuid", map.get("uuid"));
        Assert.assertEquals("component-name", map.get("name"));
        Assert.assertEquals("component-group", map.get("group"));
        Assert.assertEquals("component-version", map.get("version"));
        Assert.assertEquals("component-purl", map.get("purl"));
    }

    @Test
    public void testVulnerability() {
        Map<String, Object> map = finding.getVulnerability();
        Assert.assertEquals("vuln-uuid", map.get("uuid"));
        Assert.assertEquals("vuln-source", map.get("source"));
        Assert.assertEquals("vuln-vulnId", map.get("vulnId"));
        Assert.assertEquals("vuln-title", map.get("title"));
        Assert.assertEquals("vuln-subtitle", map.get("subtitle"));
        //Assert.assertEquals("vuln-description", map.get("description"));
        //Assert.assertEquals("vuln-recommendation", map.get("recommendation"));
        Assert.assertEquals(BigDecimal.valueOf(7.2), map.get("cvssV2BaseScore"));
        Assert.assertEquals(BigDecimal.valueOf(8.4), map.get("cvssV3BaseScore"));
        Assert.assertEquals("cvssV2-vector", map.get("cvssV2Vector"));
        Assert.assertEquals("cvssV3-vector", map.get("cvssV3Vector"));
        Assert.assertEquals(BigDecimal.valueOf(1.25), map.get("owaspLikelihoodScore"));
        Assert.assertEquals(BigDecimal.valueOf(1.75), map.get("owaspTechnicalImpactScore"));
        Assert.assertEquals(BigDecimal.valueOf(1.3), map.get("owaspBusinessImpactScore"));
        Assert.assertEquals("owasp-vector", map.get("owaspRRVector"));
        Assert.assertEquals(Severity.HIGH.name(), map.get("severity"));
        Assert.assertEquals(1, map.get("severityRank"));
        Assert.assertEquals(BigDecimal.valueOf(0.5), map.get("epssScore"));
        Assert.assertEquals(BigDecimal.valueOf(0.9), map.get("epssPercentile"));
    }

    @Test
    public void testAnalysis() {
        Map<String, Object> map = finding.getAnalysis();
        Assert.assertEquals(AnalysisState.NOT_AFFECTED, map.get("state"));
        Assert.assertEquals(true, map.get("isSuppressed"));
    }

    @Test
    public void testMatrix() {
        Assert.assertEquals(projectUuid + ":component-uuid" + ":vuln-uuid", finding.getMatrix());
    }

    @Test
    public void testGetCwes() {
        assertThat(Finding.getCwes("787,79,,89,"))
                .hasSize(3)
                .satisfiesExactly(
                        cwe -> assertThat(cwe.getCweId()).isEqualTo(787),
                        cwe -> assertThat(cwe.getCweId()).isEqualTo(79),
                        cwe -> assertThat(cwe.getCweId()).isEqualTo(89)
                );
    }

    @Test
    public void testGetCwesWhenInputIsEmpty() {
        assertThat(Finding.getCwes("")).isNull();
        assertThat(Finding.getCwes(",")).isNull();
    }

    @Test
    public void testGetCwesWhenInputIsNull() {
        assertThat(Finding.getCwes(null)).isNull();
    }

}
