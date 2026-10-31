package io.yak.ops.business.task.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.mapper.task.DefinitionMapper;
import io.yak.ops.dao.repository.task.DefinitionPageQuery;
import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.testutils.TestNames;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

/** Real MySQL product filtering and MyBatis pagination over current Task Definition versions. */
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
    void replicationIncludesSnapshotsAndOmittedStartupBeforeCountingAndPaging() {
        insert("01-snapshot-single", parameters("mysql", "BATCH", "SINGLE", "SNAPSHOT", 1));
        insert("02-snapshot-database", parameters("mysql", "BATCH", "DATABASE", "SNAPSHOT", 2));
        insert("03-initial", parameters("mysql", "STREAMING", "DATABASE", "INITIAL", 2));
        insert("04-incremental", parameters("mysql", "STREAMING", "SINGLE", "LATEST_OFFSET", 1));
        insert("05-default-initial", parameters("mysql", "STREAMING", null, null, 1));
        insert("06-jdbc", parameters("jdbc-mysql", "BATCH", "SINGLE", null, 1));

        Map<String, String> replication = Map.of("dataSyncProfile", "DATABASE_REPLICATION");
        IPage<DefinitionEntity> first = page(1, 2, replication);
        assertEquals(5L, first.getTotal());
        assertEquals(3L, first.getPages());
        assertEquals(List.of("05-default-initial", "04-incremental"), ids(first));
        IPage<DefinitionEntity> second = page(2, 2, replication);
        assertEquals(5L, second.getTotal());
        assertEquals(List.of("03-initial", "02-snapshot-database"), ids(second));
        assertEquals(List.of("01-snapshot-single"), ids(page(3, 2, replication)));

        IPage<DefinitionEntity> snapshots = page(1, 20,
                Map.of("dataSyncProfile", "DATABASE_REPLICATION", "runtimeMode", "BATCH"));
        assertEquals(2L, snapshots.getTotal());
        assertEquals(List.of("02-snapshot-database", "01-snapshot-single"), ids(snapshots));
        assertEquals(3L, page(1, 20, Map.of("runtimeMode", "BATCH")).getTotal());
        assertEquals(List.of("01-snapshot-single"), ids(page(1, 20, Map.of(
                "dataSyncProfile", "DATABASE_REPLICATION", "runtimeMode", "BATCH", "tableScope", "SINGLE"))));
    }

    @Test
    void singleBatchUsesEffectiveScopeWithoutConvertingExplicitDatabaseSelections() {
        insert("01-explicit-single", parameters("jdbc-mysql", "BATCH", "SINGLE", null, 1));
        insert("02-default-single", parameters("jdbc-postgres", "BATCH", null, null, 1));
        insert("03-database-one-table", parameters("jdbc-mysql", "BATCH", "DATABASE", null, 1));
        insert("04-database-two-tables", parameters("jdbc-mysql", "BATCH", "DATABASE", null, 2));
        insert("05-default-multiple", parameters("jdbc-mysql", "BATCH", null, null, 2));

        IPage<DefinitionEntity> single = page(1, 20, Map.of("dataSyncProfile", "SINGLE_TABLE_BATCH"));
        assertEquals(2L, single.getTotal());
        assertEquals(List.of("02-default-single", "01-explicit-single"), ids(single));
        assertEquals(0L, page(1, 20, Map.of("dataSyncProfile", "DATABASE_REPLICATION")).getTotal());
        assertEquals(5L, page(1, 20, Map.of()).getTotal());
    }

    @Test
    void countsOnlyCurrentVersionsWithinWorkspaceAndExistingDefinitionFilters() {
        String snapshot = parameters("mysql", "BATCH", "DATABASE", "SNAPSHOT", 1);
        String initial = parameters("mysql", "STREAMING", "DATABASE", "INITIAL", 1);
        insert("orders-current-snapshot", "ws", 1, 2, false, snapshot);
        insertVersion("orders-current-snapshot", "ws", 1, initial);
        insert("orders-current-streaming", "ws", 1, 2, false, initial);
        insertVersion("orders-current-streaming", "ws", 1, snapshot);
        insert("orders-other-workspace", "another-workspace", 1, 1, false, snapshot);
        insert("orders-deleted", "ws", 1, 1, true, snapshot);
        insert("orders-unpublished", "ws", 0, 1, false, snapshot);
        insert("unrelated-name", "ws", 1, 1, false, snapshot);

        DefinitionPageQuery query = new DefinitionPageQuery(
                "ws", 1, 1, "orders", "DATA_SYNC", 1,
                Map.of("dataSyncProfile", "DATABASE_REPLICATION", "runtimeMode", "BATCH",
                        "sourceType", "mysql", "sourceDataSourceId", "source"));
        IPage<DefinitionEntity> result = select(query);
        assertEquals(1L, result.getTotal());
        assertEquals(1L, result.getPages());
        assertEquals(List.of("orders-current-snapshot"), ids(result));
        assertEquals(2, result.getRecords().getFirst().getCurrentVersion());
    }

    private IPage<DefinitionEntity> page(int pageNo, int pageSize, Map<String, String> filters) {
        return select(new DefinitionPageQuery("ws", pageNo, pageSize, null, "DATA_SYNC", null, filters));
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
                    (id, workspace_id, name, task_type, status, current_version, deleted_at,
                     create_time, update_time, create_by, update_by)
                VALUES (?, ?, ?, 'DATA_SYNC', ?, ?, ?, ?, ?, 'test', 'test')
                """, id, workspace, id, status, version, deleted ? UPDATED : null, UPDATED, UPDATED);
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

    private static String parameters(String connector, String runtime, String scope, String startup, int tables) {
        List<Map<String, Object>> sourceTables = new ArrayList<>();
        List<Map<String, Object>> sinkTables = new ArrayList<>();
        List<Map<String, String>> routes = new ArrayList<>();
        for (int i = 0; i < tables; i++) {
            String table = "table_" + i;
            sourceTables.add(Map.of("database", "inventory", "table", table));
            sinkTables.add(Map.of("database", "replica", "table", table, "write", Map.of("mode", "UPSERT")));
            routes.add(Map.of("source-table", "inventory." + table, "sink-table", "replica." + table));
        }
        Map<String, Object> source = new LinkedHashMap<>(Map.of(
                "type", connector, "datasourceId", "source", "tables", sourceTables));
        if (startup != null) source.put("startup", Map.of("mode", startup));
        else if ("BATCH".equals(runtime)) source.put("syncMode", "FULL");
        Map<String, Object> pipeline = new LinkedHashMap<>(Map.of("execution.runtime-mode", runtime));
        if (scope != null) pipeline.put("pipeline.table-scope", scope);
        String effectiveScope = scope == null ? (tables == 1 ? "SINGLE" : "DATABASE") : scope;
        return JSONUtils.toJson(Map.of(
                "source", source,
                "sink", Map.of("type", "jdbc-mysql", "datasourceId", "target", "tables", sinkTables,
                        "schemaPolicy", Map.of("initial", "VERIFY", "evolution", Map.of("enabled", false, "allowDropColumns", false))),
                "route", routes,
                "transform", List.of(),
                "pipeline", pipeline,
                "index", Map.of("sourceType", connector, "sinkType", "jdbc-mysql", "runtimeMode", runtime,
                        "tableScope", effectiveScope, "sourceDataSourceId", "source", "targetDataSourceId", "target")));
    }
}
