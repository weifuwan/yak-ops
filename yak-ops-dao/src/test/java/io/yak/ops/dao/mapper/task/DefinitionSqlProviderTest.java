package io.yak.ops.dao.mapper.task;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.dao.repository.task.DefinitionPageQuery;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Task Definition queries must bind all caller-controlled filter values. */
class DefinitionSqlProviderTest {
    @Test
    void bindsWorkspaceTypeStatusAndKeyword() {
        DefinitionPageQuery filter = new DefinitionPageQuery(
                "ws", 1, 20, "orders", "EXAMPLE_TASK", 1);
        String sql = DefinitionSqlProvider.selectDefinitionPage(Map.of("filter", filter));
        assertTrue(sql.contains("d.workspace_id = #{filter.workspaceId}"));
        assertTrue(sql.contains("d.task_type = #{filter.taskType}"));
        assertTrue(sql.contains("d.status = #{filter.statusValue}"));
        assertTrue(sql.contains("d.name LIKE CONCAT('%', #{filter.keyword}, '%')"));
        assertTrue(sql.contains("v.parameters LIKE CONCAT('%', #{filter.keyword}, '%')"));
        assertTrue(sql.contains("d.deleted_at IS NULL"));
        assertTrue(sql.contains("ORDER BY d.update_time DESC, d.id DESC"));
    }

    @Test
    void doesNotInterpolateUserKeywordIntoSql() {
        String keyword = "orders' OR 1=1 --";
        DefinitionPageQuery filter = new DefinitionPageQuery(
                "ws", 1, 20, keyword, "EXAMPLE_TASK", null);
        String sql = DefinitionSqlProvider.selectDefinitionPage(Map.of("filter", filter));
        assertFalse(sql.contains(keyword));
        assertTrue(sql.contains("#{filter.keyword}"));
    }

    @Test
    void retiredProductIndexedPredicatesAreNotCompiled() {
        DefinitionPageQuery filter = new DefinitionPageQuery(
                "ws", 1, 20, null, null, null);
        String sql = DefinitionSqlProvider.selectDefinitionPage(Map.of("filter", filter));
        assertFalse(sql.contains("dataSyncProfile"));
        assertFalse(sql.contains("JSON_EXTRACT"));
        assertFalse(sql.contains("parameter_schema_version"));
        assertFalse(sql.contains("#{filter.parameterFilters."));
    }
}
