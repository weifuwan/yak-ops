package io.yak.ops.dao.repository.impl.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.common.context.ProjectCodeContext;
import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.DimensionDorisPublishPO;
import io.yak.ops.dao.repository.modeling.DimensionDorisPublishDao;
import java.sql.Timestamp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DimensionDorisPublishDaoImplTest extends BaseDaoTest {

    private static final String TABLE_ID = "ddl-preview-tenant-test";

    @Autowired
    private DimensionDorisPublishDao dimensionDorisPublishDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void preparePublishTable() {
        ProjectCodeContext.clear();
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS t_yak_ops_dimension_table_publish ("
                        + "id varchar(32) NOT NULL PRIMARY KEY, "
                        + "project_code bigint NOT NULL, "
                        + "table_id varchar(32) NOT NULL, "
                        + "datasource_id integer NOT NULL, "
                        + "target_database varchar(255) NOT NULL, "
                        + "target_table varchar(255) NOT NULL, "
                        + "status varchar(32) NOT NULL, "
                        + "create_time timestamp NULL)");
        jdbcTemplate.update(
                "DELETE FROM t_yak_ops_dimension_table_publish WHERE table_id = ?",
                TABLE_ID);
    }

    @AfterEach
    void clearProjectContext() {
        ProjectCodeContext.clear();
    }

    @Test
    void shouldReturnLatestSuccessfulPublishOnlyForActiveProject() {
        insertPublish("project-1001-old", 1001L, "SUCCESS", Timestamp.valueOf("2026-01-01 10:00:00"));
        insertPublish("project-1001-latest", 1001L, "SUCCESS", Timestamp.valueOf("2026-01-02 10:00:00"));
        insertPublish("project-1001-failed", 1001L, "FAILED", Timestamp.valueOf("2026-01-04 10:00:00"));
        insertPublish("project-1002-latest", 1002L, "SUCCESS", Timestamp.valueOf("2026-01-03 10:00:00"));

        try {
            ProjectCodeContext.setProjectCode(1001L);
            DimensionDorisPublishPO project1001 = dimensionDorisPublishDao.queryLatestSuccessful(
                    TABLE_ID, 100, "warehouse", "dwd_order_detail");
            assertNotNull(project1001);
            assertEquals("project-1001-latest", project1001.getId());
            assertEquals(1001L, project1001.getProjectCode());

            ProjectCodeContext.setProjectCode(1002L);
            DimensionDorisPublishPO project1002 = dimensionDorisPublishDao.queryLatestSuccessful(
                    TABLE_ID, 100, "warehouse", "dwd_order_detail");
            assertNotNull(project1002);
            assertEquals("project-1002-latest", project1002.getId());
            assertEquals(1002L, project1002.getProjectCode());
        } finally {
            ProjectCodeContext.clear();
        }
    }

    @Test
    void shouldFailClosedWhenProjectContextIsMissing() {
        ProjectCodeContext.clear();

        assertThrows(
                IllegalStateException.class,
                () -> dimensionDorisPublishDao.queryLatestSuccessful(
                        TABLE_ID, 100, "warehouse", "dwd_order_detail"));
    }

    private void insertPublish(String id, Long projectCode, String status, Timestamp createTime) {
        jdbcTemplate.update(
                "INSERT INTO t_yak_ops_dimension_table_publish "
                        + "(id, project_code, table_id, datasource_id, target_database, "
                        + "target_table, status, create_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                projectCode,
                TABLE_ID,
                100,
                "warehouse",
                "dwd_order_detail",
                status,
                createTime);
    }
}
