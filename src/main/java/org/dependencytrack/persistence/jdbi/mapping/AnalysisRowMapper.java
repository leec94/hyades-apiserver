package org.dependencytrack.persistence.jdbi.mapping;

import org.dependencytrack.model.Analysis;
import org.dependencytrack.model.AnalysisJustification;
import org.dependencytrack.model.AnalysisResponse;
import org.dependencytrack.model.AnalysisState;
import org.dependencytrack.model.Component;
import org.dependencytrack.model.Vulnerability;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.dependencytrack.persistence.jdbi.mapping.RowMapperUtil.maybeSet;

public class AnalysisRowMapper implements RowMapper<Analysis> {

    @Override
    public Analysis map(final ResultSet rs, final StatementContext ctx) throws SQLException {
        final var analysis = new Analysis();
        maybeSet(rs, "ID", ResultSet::getLong, analysis::setId);
        maybeSet(rs, "COMPONENT_ID", ResultSet::getLong, value -> {
            final var component = new Component();
            component.setId(value);
            analysis.setComponent(component);
        });
        maybeSet(rs, "VULNERABILITY_ID", ResultSet::getLong, value -> {
            final var vuln = new Vulnerability();
            vuln.setId(value);
            analysis.setVulnerability(vuln);
        });
        maybeSet(rs, "STATE", ResultSet::getString, value -> analysis.setAnalysisState(AnalysisState.valueOf(value)));
        maybeSet(rs, "JUSTIFICATION", ResultSet::getString, value -> analysis.setAnalysisJustification(AnalysisJustification.valueOf(value)));
        maybeSet(rs, "RESPONSE", ResultSet::getString, value -> analysis.setAnalysisResponse(AnalysisResponse.valueOf(value)));
        maybeSet(rs, "DETAILS", ResultSet::getString, analysis::setAnalysisDetails);
        maybeSet(rs, "SUPPRESSED", ResultSet::getBoolean, analysis::setSuppressed);
        return analysis;
    }

}
