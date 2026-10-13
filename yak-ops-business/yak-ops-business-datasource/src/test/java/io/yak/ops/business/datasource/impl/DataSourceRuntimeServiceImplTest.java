package io.yak.ops.business.datasource.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.runtime.DataSourceRuntimeService;
import io.yak.ops.business.datasource.runtime.impl.DataSourceRuntimeServiceImpl;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import java.lang.reflect.Proxy;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Checks trusted Workspace ownership and datasource revision propagation for runtime bindings. */
class DataSourceRuntimeServiceImplTest {

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
            context.registerBean(DataSourceRuntimeServiceImpl.class);
            context.registerBean(DataSourceEntityRepository.class, () -> repository);
            context.registerBean(DataSourceProperties.class);
            context.refresh();

            DataSourceRuntimeService service = context.getBean(DataSourceRuntimeService.class);
            var resolved = service.connection("workspace-1", "h2-source");
            assertEquals("TEST_H2", resolved.datasourceType());
            assertEquals(7, resolved.revision());
            assertEquals(H2TestDataSourcePlugin.JDBC_URL, resolved.jdbcUrl());
            assertTrue(resolved.toString().contains("credentials=redacted"));

            DataSourceException unavailable = assertThrows(
                    DataSourceException.class, () -> service.connection("other-workspace", "h2-source"));
            assertEquals(DataSourceErrorCode.NOT_FOUND, unavailable.getErrorCode());
        }
    }
}
