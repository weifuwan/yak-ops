package io.yak.ops.business.datasource.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.datasource.DataSourceCatalogService;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.impl.DataSourceLookupServiceImpl;
import io.yak.ops.common.bean.dto.datasource.DataSourceCatalogQueryDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePageDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourcePreviewDTO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.core.data.TableId;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Verifies that Datasource delegates its JDBC metadata queries to one Connector Catalog. */
class DataSourceCatalogServiceImplTest {

    @Test
    void sharedJdbcProviderReadsPhysicalMetadataAndEnforcesWorkspace() throws Exception {
        try (Connection connection = DriverManager.getConnection(H2TestDataSourcePlugin.JDBC_URL, "sa", "");
                Statement ddl = connection.createStatement()) {
            ddl.execute("CREATE SCHEMA APP");
            ddl.execute("CREATE TABLE APP.PRODUCTS (TENANT INT, ID BIGINT, NAME VARCHAR(64),"
                    + " PRIMARY KEY(ID, TENANT))");
            ddl.execute("INSERT INTO APP.PRODUCTS VALUES (1, 9007199254740993, 'visible')");
        }
        H2TestDataSourcePlugin.CONNECTION_OPENS.set(0);

        DataSourceEntity datasource = new DataSourceEntity();
        datasource.setId("h2-source");
        datasource.setWorkspaceId("workspace-1");
        datasource.setDbType("TEST_H2");
        datasource.setConnectionParams("{}");
        datasource.setRevision(1L);

        DataSourceEntityRepository repository = (DataSourceEntityRepository) Proxy.newProxyInstance(
                DataSourceEntityRepository.class.getClassLoader(),
                new Class<?>[] {DataSourceEntityRepository.class},
                (ignored, method, arguments) -> {
                    if ("queryById".equals(method.getName()) && arguments != null && arguments.length == 2) {
                        if ("workspace-1".equals(arguments[0]) && "h2-source".equals(arguments[1])) {
                            return Optional.of(datasource);
                        }
                        return Optional.empty();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DataSourceCatalogServiceImpl.class);
            context.registerBean(DataSourceLookupServiceImpl.class);
            context.registerBean(DataSourceEntityRepository.class, () -> repository);
            context.registerBean(DataSourceProperties.class);
            context.refresh();

            DataSourceCatalogService catalog = context.getBean(DataSourceCatalogService.class);
            WorkspaceContext.bind("workspace-1");
            try {
                DataSourceCatalogQueryDTO query = new DataSourceCatalogQueryDTO();
                query.setSchema("APP");
                query.setKeyword("prod");
                query.setLimit(10);
                assertEquals("PRODUCTS", catalog.listTables("h2-source", query).getFirst().getName());

                DataSourceTablePathDTO path = new DataSourceTablePathDTO();
                path.setSchema("APP");
                path.setTable("PRODUCTS");
                var columns = catalog.listColumns("h2-source", path);
                assertEquals(3, columns.size());
                assertEquals(2, columns.getFirst().getPrimaryKeyPosition());
                assertEquals(1, columns.get(1).getPrimaryKeyPosition());
                assertFalse(columns.get(2).getPrimaryKey());

                TableId table = new TableId(null, "APP", "PRODUCTS");
                var schema = catalog.getTableSchema("h2-source", table);
                assertEquals(3, schema.columnCount());
                assertEquals(List.of("ID", "TENANT"), schema.primaryKeys());
                assertEquals(0, H2TestDataSourcePlugin.CONNECTION_OPENS.get(),
                        "Catalog connections are opened by the shared JDBC implementation");

                DataSourceTablePageDTO page = new DataSourceTablePageDTO();
                page.setSchema("APP");
                page.setTablePattern("PROD.*");
                assertEquals("PRODUCTS", catalog.listTablePage("h2-source", page).getItems().getFirst().getName());
                DataSourcePreviewDTO preview = new DataSourcePreviewDTO();
                preview.setSchema("APP");
                preview.setTable("PRODUCTS");
                preview.setWhere("TENANT = 1");
                assertEquals("9007199254740993", catalog.preview("h2-source", preview).getRows().getFirst().get(1));

                page.setSchema(null);
                assertEquals("APP", catalog.listTablePage("h2-source", page).getItems().getFirst().getSchema());
                preview.setSchema(null);
                assertEquals("9007199254740993", catalog.preview("h2-source", preview).getRows().getFirst().get(1));
                page.setSchema("OTHER");
                assertEquals(DataSourceErrorCode.INVALID_CATALOG_FILTER,
                        assertThrows(DataSourceException.class, () -> catalog.listTablePage("h2-source", page)).getErrorCode());
                preview.setSchema("OTHER");
                assertEquals(DataSourceErrorCode.INVALID_PREVIEW_CONFIGURATION,
                        assertThrows(DataSourceException.class, () -> catalog.preview("h2-source", preview)).getErrorCode());
                page.setSchema("APP");
                preview.setSchema("APP");

                WorkspaceContext.bind("another-workspace");
                DataSourceException exception = assertThrows(
                        DataSourceException.class, () -> catalog.getTableSchema("h2-source", table));
                assertEquals(DataSourceErrorCode.NOT_FOUND, exception.getErrorCode());
                assertEquals(DataSourceErrorCode.NOT_FOUND,
                        assertThrows(DataSourceException.class, () -> catalog.listTablePage("h2-source", page))
                                .getErrorCode());
                assertEquals(DataSourceErrorCode.NOT_FOUND,
                        assertThrows(DataSourceException.class, () -> catalog.preview("h2-source", preview))
                                .getErrorCode());
            } finally {
                WorkspaceContext.clear();
            }
        }
    }
}
