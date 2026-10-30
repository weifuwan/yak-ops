package io.yak.ops.dao.mapper.task;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.dao.repository.task.DefinitionPageQuery;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Stable V6 indexed metadata filter paths must never interpolate user-controlled values into SQL. */
class DefinitionSqlProviderTest {
    @Test
    void bindsOnlyValidatedV6YamlSearchProjection() {
        DefinitionPageQuery filter = new DefinitionPageQuery(
                "ws", 1, 20, "orders", "DATA_SYNC", 1,
                Map.of("sourceType", "MYSQL_CDC", "sourceDataSourceId", "source-id",
                        "targetDataSourceId", "target-id"));
        String sql = DefinitionSqlProvider.selectDefinitionPage(Map.of("filter", filter));
        assertTrue(sql.contains("$.index.sourceType"));
        assertTrue(DefinitionSqlProvider.SOLUTION_TYPE.contains("$.index.solutionType"));
        assertTrue(sql.contains("v.parameter_schema_version = 6"));
        assertFalse(sql.contains("v.parameter_schema_version = 2"));
        assertTrue(sql.contains("$.index.sourceDataSourceId"));
        assertTrue(sql.contains("$.index.targetDataSourceId"));
        assertTrue(sql.contains("#{filter.parameterFilters.sourceType}"));
        assertFalse(sql.contains("source-id"));
    }

    @Test
    void rejectsArbitraryJsonPathsInsteadOfBuildingSqlFromThem() {
        DefinitionPageQuery filter = new DefinitionPageQuery(
                "ws", 1, 20, null, "DATA_SYNC", null, Map.of("credentials.password", "anything"));
        assertThrows(IllegalArgumentException.class,
                () -> DefinitionSqlProvider.selectDefinitionPage(Map.of("filter", filter)));
    }
}
