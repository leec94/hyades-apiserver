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
package org.dependencytrack.util;

import org.dependencytrack.model.Analysis;
import org.dependencytrack.model.AnalysisJustification;
import org.dependencytrack.model.AnalysisResponse;
import org.dependencytrack.model.AnalysisState;
import org.dependencytrack.persistence.QueryManager;
import org.dependencytrack.util.AnalysisCommentFormatter.AnalysisCommentField;

import static org.dependencytrack.util.AnalysisCommentFormatter.formatComment;

public final class AnalysisCommentUtil {

    private AnalysisCommentUtil() { }

    public static boolean makeStateComment(final QueryManager qm, final Analysis analysis, final AnalysisState analysisState, final String commenter) {
        boolean analysisStateChange = false;
        if (analysisState != null && analysisState != analysis.getAnalysisState()) {
            analysisStateChange = true;
            qm.makeAnalysisComment(analysis, formatComment(AnalysisCommentField.STATE, analysis.getAnalysisState(), analysisState), commenter);
        }
        return analysisStateChange;
    }

    public static void makeJustificationComment(final QueryManager qm, final Analysis analysis, final AnalysisJustification analysisJustification, final String commenter) {
        if (analysisJustification != null) {
            if (analysis.getAnalysisJustification() == null && AnalysisJustification.NOT_SET != analysisJustification) {
                qm.makeAnalysisComment(analysis, formatComment(AnalysisCommentField.JUSTIFICATION, AnalysisJustification.NOT_SET, analysisJustification), commenter);
            } else if (analysis.getAnalysisJustification() != null && analysisJustification != analysis.getAnalysisJustification()) {
                qm.makeAnalysisComment(analysis, formatComment(AnalysisCommentField.JUSTIFICATION, analysis.getAnalysisJustification(), analysisJustification), commenter);
            }
        }
    }

    public static void makeAnalysisResponseComment(final QueryManager qm, final Analysis analysis, final AnalysisResponse analysisResponse, final String commenter) {
        if (analysisResponse != null) {
            if (analysis.getAnalysisResponse() == null && analysis.getAnalysisResponse() != analysisResponse) {
                qm.makeAnalysisComment(analysis, formatComment(AnalysisCommentField.RESPONSE, AnalysisResponse.NOT_SET, analysisResponse), commenter);
            } else if (analysis.getAnalysisResponse() != null && analysis.getAnalysisResponse() != analysisResponse) {
                qm.makeAnalysisComment(analysis, formatComment(AnalysisCommentField.RESPONSE, analysis.getAnalysisResponse(), analysisResponse), commenter);
            }
        }
    }

    public static void makeAnalysisDetailsComment(final QueryManager qm, final Analysis analysis, final String analysisDetails, final String commenter) {
        if (analysisDetails != null && !analysisDetails.equals(analysis.getAnalysisDetails())) {
            qm.makeAnalysisComment(analysis, formatComment(AnalysisCommentField.DETAILS, analysis.getAnalysisDetails(), analysisDetails), commenter);
        }
    }

    public static boolean makeAnalysisSuppressionComment(final QueryManager qm, final Analysis analysis, final Boolean suppressed, final String commenter) {
        boolean suppressionChange = false;
        if (suppressed != null && analysis.isSuppressed() != suppressed) {
            suppressionChange = true;
            qm.makeAnalysisComment(analysis, formatComment(AnalysisCommentField.SUPPRESSED, analysis.isSuppressed(), suppressed), commenter);
        }
        return suppressionChange;
    }
}
