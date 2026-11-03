package io.yak.ops.business.task.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.dao.mapper.task.DefinitionMapper;
import io.yak.ops.dao.repository.task.DefinitionPageQuery;
import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.testutils.TestNames;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Real MySQL Task Definition pagination, Workspace isolation and immutable current-version matching. */
@Tag("business")
@Timeout(120)
class DefinitionPageQueryIT {
    @RegisterExtension
    static final MySqlDatabase MYSQL = new MySqlDatabase();

    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 1, 1, 0, 0);
    private final String database = TestNames.unique("definition_page");
    private JdbcTemplate jdbc;
    private SqlSession session;
    private DefinitionMapper mapper;
    private boolean created;

    @BeforeEach
    void createDatabaseAndMapper() throws Exception {
        DatabaseMetadata metadata = MYSQL.metadata();
        metadata.execute("CREATE DATABASE " + database);
        created = true;
        DataSource source = new DriverManagerDataSource(
                "jdbc:mysql://" + metadata.host() + ":" + metadata.port() + "/" + database
                        + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=30000",
                metadata.username(), metadata.password());
        Flyway.configure().dataSource(source).locations("classpath:db/migration/yak-ops").load().migrate();
        jdbc = new JdbcTemplate(source);

        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false);
        factory.setConfiguration(configuration);
        MybatisPlusInterceptor pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor());
        factory.setPlugins(pagination);
        SqlSessionFactory sessions = factory.getObject();
        sessions.getConfiguration().addMapper(DefinitionMapper.class);
        session = sessions.openSession(true);
        mapper = session.getMapper(DefinitionMapper.class);
    }

    @AfterEach
    void dropDatabase() throws Exception {
        if (session != null) session.close();
        if (created) MYSQL.metadata().execute("DROP DATABASE " + database);
    }

    @Test
    void filtersByWorkspaceStatusAndKeywordBeforePagination() {
        insert("orders-a", "ws", 1, 1, false, "{\"marker\":\"alpha\"}");
        insert("orders-b", "ws", 1, 1, false, "{\"marker\":\"beta\"}");
        insert("orders-unpublished", "ws", 0, 1, false, "{}");
        insert("orders-deleted", "ws", 1, 1, true, "{}");
        insert("orders-foreign", "other-workspace", 1, 1, false, "{}");

        DefinitionPageQuery first = new DefinitionPageQuery("ws", 1, 1, "orders", "EXAMPLE_TASK", 1);
        IPage<DefinitionEntity> firstPage = select(first);
        assertEquals(2L, firstPage.getTotal());
        assertEquals(2L, firstPage.getPages());
        assertEquals(List.of("orders-b"), ids(firstPage));
        DefinitionPageQuery second = new DefinitionPageQuery("ws", 2, 1, "orders", "EXAMPLE_TASK", 1);
        assertEquals(List.of("orders-a"), ids(select(second)));
    }

    @Test
    void searchesCurrentVersionWithoutMatchingHistoricalParameterPayloads() {
        insert("definition-a", "ws", 1, 2, false, "{\"marker\":\"current-only\"}");
        insertVersion("definition-a", "ws", 1, "{\"marker\":\"historical-only\"}");
        insert("definition-b", "ws", 1, 1, false, "{\"marker\":\"historical-only\"}");

        IPage<DefinitionEntity> historical = select(
                new DefinitionPageQuery("ws", 1, 20, "historical-only", "EXAMPLE_TASK", null));
        assertEquals(1L, historical.getTotal());
        assertEquals(List.of("definition-b"), ids(historical));
        IPage<DefinitionEntity> current = select(
                new DefinitionPageQuery("ws", 1, 20, "current-only", "EXAMPLE_TASK", null));
        assertEquals(List.of("definition-a"), ids(current));
        assertEquals(2, current.getRecords().getFirst().getCurrentVersion());
    }

    @Test
    void taskTypeFilteringIsExactAndKeepsDefaultListingGeneric() {
        insert("example-a", "{}");
        insert("example-b", "{}");
        insert("example-c", "{}");
        jdbc.update("UPDATE yak_ops_task_definition SET task_type=? WHERE id=?", "OTHER_TASK", "example-a");

        IPage<DefinitionEntity> typed = select(
                new DefinitionPageQuery("ws", 1, 20, null, "EXAMPLE_TASK", null));
        assertEquals(2L, typed.getTotal());
        assertEquals(List.of("example-c", "example-b"), ids(typed));
        assertEquals(1L, select(
                new DefinitionPageQuery("ws", 1, 20, null, "OTHER_TASK", null)).getTotal());
        assertEquals(3L, select(
                new DefinitionPageQuery("ws", 1, 20, null, null, null)).getTotal());
    }

    @Test
    void clearingPublicationPersistsNullsWithoutChangingSavedParameters() {
        insert("published", "{\"marker\":\"published\"}");
        DefinitionEntity definition = mapper.selectById("published");
        assertEquals(1, definition.getPublishedVersion());
        definition.setStatus(DefinitionStatus.UNPUBLISHED);
        definition.setPublishedVersion(null);
        definition.setPublishedDebug(null);
        definition.setRevision(2);
        assertEquals(1, mapper.updateById(definition));
        DefinitionEntity reloaded = mapper.selectById("published");
        assertNull(reloaded.getPublishedVersion());
        assertNull(reloaded.getPublishedDebug());
        assertEquals(1, reloaded.getCurrentVersion());
        assertEquals(2, reloaded.getRevision());
    }

    @Test
    void upgradeBackfillsOnlyCurrentPublicationWithoutInventingHistory() throws Exception {
        DatabaseMetadata metadata = MYSQL.metadata();
        String upgradeDatabase = TestNames.unique("pub_upgrade");
        metadata.execute("CREATE DATABASE " + upgradeDatabase);
        try {
            DataSource source = new DriverManagerDataSource(
                    "jdbc:mysql://" + metadata.host() + ":" + metadata.port() + "/" + upgradeDatabase
                            + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=30000",
                    metadata.username(), metadata.password());
            Flyway.configure().dataSource(source).locations("classpath:db/migration/yak-ops")
                    .target("1").load().migrate();
            JdbcTemplate upgrade = new JdbcTemplate(source);
            for (int status = 0; status <= 1; status++) {
                String id = "task-" + status;
                upgrade.update("""
                        INSERT INTO yak_ops_task_definition
                          (id,workspace_id,name,task_type,status,current_version,debug,
                           create_time,update_time,create_by,update_by)
                        VALUES (?,'ws',?,'EXAMPLE_TASK',?,3,1,?,?,'original-author','original-author')
                        """, id, id, status, UPDATED, UPDATED);
                upgrade.update("""
                        INSERT INTO yak_ops_task_definition_version
                          (id,workspace_id,definition_id,version,parameters,parameter_schema_version,
                           create_time,update_time,create_by,update_by)
                        VALUES (?,'ws',?,3,?,7,?,?,'original-author','original-author')
                        """, id + "-v3", id, "{\"sourceTable\":\"orders\"}", UPDATED, UPDATED);
            }
            Flyway.configure().dataSource(source).locations("classpath:db/migration/yak-ops").load().migrate();

            assertEquals(3, upgrade.queryForObject(
                    "SELECT published_version FROM yak_ops_task_definition WHERE id='task-1'", Integer.class));
            assertEquals(true, upgrade.queryForObject(
                    "SELECT published_debug FROM yak_ops_task_definition WHERE id='task-1'", Boolean.class));
            assertNull(upgrade.queryForObject(
                    "SELECT published_version FROM yak_ops_task_definition WHERE id='task-0'", Integer.class));
            assertNull(upgrade.queryForObject(
                    "SELECT published_debug FROM yak_ops_task_definition WHERE id='task-0'", Boolean.class));
            assertEquals(2L, upgrade.queryForObject(
                    "SELECT COUNT(*) FROM yak_ops_task_definition WHERE revision=1 AND current_version=3 AND debug=1", Long.class));
            assertEquals(0L, upgrade.queryForObject("SELECT COUNT(*) FROM yak_ops_task_publication", Long.class));
            assertEquals(2L, upgrade.queryForObject("""
                    SELECT COUNT(*) FROM yak_ops_task_definition_version
                    WHERE parameters=? AND version=3 AND create_by='original-author' AND create_time=?
                    """, Long.class, "{\"sourceTable\":\"orders\"}", UPDATED));
        } finally {
            metadata.execute("DROP DATABASE " + upgradeDatabase);
        }
    }

    private IPage<DefinitionEntity> select(DefinitionPageQuery query) {
        return mapper.selectDefinitionPage(new Page<>(query.pageNo(), query.pageSize()), query);
    }

    private static List<String> ids(IPage<DefinitionEntity> page) {
        return page.getRecords().stream().map(DefinitionEntity::getId).toList();
    }

    private void insert(String id, String parameters) {
        insert(id, "ws", 1, 1, false, parameters);
    }

    private void insert(String id, String workspace, int status, int version, boolean deleted, String parameters) {
        jdbc.update("""
                INSERT INTO yak_ops_task_definition
                    (id, workspace_id, name, task_type, status, current_version, published_version, published_debug, deleted_at,
                     create_time, update_time, create_by, update_by)
                VALUES (?, ?, ?, 'EXAMPLE_TASK', ?, ?, ?, ?, ?, ?, ?, 'test', 'test')
                """, id, workspace, id, status, version, status == 1 ? version : null, status == 1 ? false : null,
                deleted ? UPDATED : null, UPDATED, UPDATED);
        insertVersion(id, workspace, version, parameters);
    }

    private void insertVersion(String id, String workspace, int version, String parameters) {
        jdbc.update("""
                INSERT INTO yak_ops_task_definition_version
                    (id, workspace_id, definition_id, version, parameters, parameter_schema_version,
                     create_time, update_time, create_by, update_by)
                VALUES (?, ?, ?, ?, ?, 7, ?, ?, 'test', 'test')
                """, id + "-v" + version, workspace, id, version, parameters, UPDATED, UPDATED);
    }

}
