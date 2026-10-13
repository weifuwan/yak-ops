package io.yak.ops.business.datasource.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.business.datasource.impl.DataSourceLookupServiceImpl;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import java.lang.reflect.Proxy;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Checks Workspace ownership and revision-checked connection preparation. */
class DataSourceLookupServiceImplTest {

    @Test
    void resolvesRegisteredDatasourceAndRejectsAnotherWorkspace() {
        DataSourceEntity datasource = new DataSourceEntity();
        datasource.setId("h2-source");
        datasource.setWorkspaceId("workspace-1");
        datasource.setDbType("TEST_H2");
        datasource.setConnectionParams("{}");
        datasource.setRevision(7L);

        DataSourceEntityRepository repository = (DataSourceEntityRepository) Proxy.newProxyInstance(
                DataSourceEntityRepository.class.getClassLoader(),
                new Class<?>[] {DataSourceEntityRepository.class},
                (ignored, method, parameters) -> {
                    if ("queryById".equals(method.getName()) && parameters != null && parameters.length == 2) {
                        if ("workspace-1".equals(parameters[0]) && "h2-source".equals(parameters[1])) {
                            return Optional.of(datasource);
                        }
                        return Optional.empty();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DataSourceLookupServiceImpl.class);
            context.registerBean(DataSourceEntityRepository.class, () -> repository);
            context.registerBean(DataSourceProperties.class);
            context.refresh();

            DataSourceLookupService service = context.getBean(DataSourceLookupService.class);
            var resolved = service.connectionOptions("workspace-1", "h2-source", 7);
            assertEquals("TEST_H2", service.dataSource("workspace-1", "h2-source").type());
            assertThrows(IllegalStateException.class, () -> service.connectionOptions("workspace-1", "h2-source", 6));
            assertThrows(IllegalStateException.class, () -> service.connectionOptions("workspace-1", "h2-source", 0));
            assertEquals(H2TestDataSourcePlugin.JDBC_URL, resolved.get(io.yak.ops.connector.jdbc.JdbcConnectionOptions.URL));
            assertTrue(!resolved.toString().contains(H2TestDataSourcePlugin.JDBC_URL));

            DataSourceException unavailable = assertThrows(
                    DataSourceException.class, () -> service.connectionOptions("other-workspace", "h2-source", 7));
            assertEquals(DataSourceErrorCode.NOT_FOUND, unavailable.getErrorCode());
        }
    }
}
