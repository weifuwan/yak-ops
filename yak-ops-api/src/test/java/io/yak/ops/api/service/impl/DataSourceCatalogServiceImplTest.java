package io.yak.ops.api.service.impl;

import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;
import io.yak.ops.spi.enums.DbType;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DataSourceCatalogServiceImplTest {

    private final DataSourceCatalogServiceImpl service = new DataSourceCatalogServiceImpl();

    @Test
    public void testMysqlAndPostgresqlUseTheirOwnCatalogs() {
        for (DbType type : new DbType[]{DbType.MYSQL, DbType.POSTGRESQL}) {
            List<ConnectionProperty> properties = service.getConnectionProperties(type);
            Assertions.assertFalse(properties.isEmpty());
            Assertions.assertTrue(properties.stream().anyMatch(p -> p.getName() != null));
            Assertions.assertFalse(properties.stream().anyMatch(p -> "password".equalsIgnoreCase(p.getName())));
        }
    }

    @Test
    public void testOtherPluginsReturnEmptyByDefault() {
        Assertions.assertTrue(service.getConnectionProperties(DbType.ORACLE).isEmpty());
    }

    @Test
    public void testMissingDatasourcePluginIsNotSilentlyEmpty() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.getConnectionProperties(DbType.HIVE));
    }
}
