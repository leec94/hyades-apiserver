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
package org.dependencytrack.policy.cel.compat;

import alpine.common.logging.Logger;
import io.github.nscuro.versatile.Comparator;
import io.github.nscuro.versatile.Vers;
import io.github.nscuro.versatile.version.VersioningScheme;
import org.dependencytrack.model.PolicyCondition;
import org.json.JSONObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.dependencytrack.policy.cel.compat.CelPolicyScriptSourceBuilder.escapeQuotes;

public class CoordinatesCelPolicyScriptSourceBuilder implements CelPolicyScriptSourceBuilder {

    private static final Logger LOGGER = Logger.getLogger(CoordinatesCelPolicyScriptSourceBuilder.class);
    private static final Pattern VERSION_OPERATOR_PATTERN = Pattern.compile("^(?<operator>[<>]=?|[!=]=)\\s*");

    @Override
    public String apply(final PolicyCondition condition) {
        if (condition.getValue() == null) {
            return null;
        }

        final JSONObject def = new JSONObject(condition.getValue());
        final String group = Optional.ofNullable(def.optString("group", null)).orElse("");
        final String name = Optional.ofNullable(def.optString("name", null)).orElse("");
        final String version = Optional.ofNullable(def.optString("version")).orElse("");

        final var scriptSrc = evaluateScript(group, name, version);
        if (condition.getOperator() == PolicyCondition.Operator.MATCHES) {
            return scriptSrc;
        } else if (condition.getOperator() == PolicyCondition.Operator.NO_MATCH) {
            return "!(%s)".formatted(scriptSrc);
        }

        return null;
    }

    private static String evaluateScript(final String conditionGroupPart, final String conditionNamePart, final String conditionVersionPart) {
        final String group = replace(conditionGroupPart);
        final String name = replace(conditionNamePart);

        final Matcher versionOperatorMatcher = VERSION_OPERATOR_PATTERN.matcher(conditionVersionPart);
        //Do an exact match if no operator found
        if (!versionOperatorMatcher.find()) {

            Vers conditionVers = Vers.builder(VersioningScheme.GENERIC)
                    .withConstraint(Comparator.EQUAL, conditionVersionPart)
                    .build();
            return """
                component.group.matches("%s") && component.name.matches("%s") && component.matches_range("%s")
                """.formatted(escapeQuotes(group), escapeQuotes(name), conditionVers.toString());
        }

        io.github.nscuro.versatile.Comparator versionComparator = switch (versionOperatorMatcher.group(1)) {
            case "==" -> Comparator.EQUAL;
            case "!=" -> Comparator.NOT_EQUAL;
            case "<" -> Comparator.LESS_THAN;
            case "<=" -> Comparator.LESS_THAN_OR_EQUAL;
            case ">" -> Comparator.GREATER_THAN;
            case ">=" -> Comparator.GREATER_THAN_OR_EQUAL;
            default -> null;
        };
        if (versionComparator == null) {
            // Shouldn't ever happen because the regex won't match anything else
            LOGGER.error("Failed to infer version operator from " + versionOperatorMatcher.group(1));
            return null;
        }
        String condition = VERSION_OPERATOR_PATTERN.split(conditionVersionPart)[1];
        Vers conditionVers = Vers.builder(VersioningScheme.GENERIC)
                .withConstraint(versionComparator, condition)
                .build();

        return """
                component.group.matches("%s") && component.name.matches("%s") && component.matches_range("%s")
                """.formatted(escapeQuotes(group), escapeQuotes(name), conditionVers.toString());
    }

    private static String replace(String conditionString) {
        conditionString = conditionString.replace("*", ".*").replace("..*", ".*");
        if (!conditionString.startsWith("^") && !conditionString.startsWith(".*")) {
            conditionString = ".*" + conditionString;
        }
        if (!conditionString.endsWith("$") && !conditionString.endsWith(".*")) {
            conditionString += ".*";
        }
        return conditionString;
    }
}
