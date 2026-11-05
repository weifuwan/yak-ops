package io.yak.ops.plugin.datasource.api.catalog;

import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;

import java.sql.DriverPropertyInfo;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JdbcConnectionPropertyCatalogTest {

    @Test
    public void testFiltersSensitiveNamesAndDeduplicates() {
        DriverPropertyInfo timeout = new DriverPropertyInfo("connectTimeout", "10000");
        timeout.description = "Timeout in milliseconds";
        timeout.choices = new String[]{"1000", "10000"};
        List<ConnectionProperty> properties = JdbcConnectionPropertyCatalog.toProperties(
                new DriverPropertyInfo[]{
                        new DriverPropertyInfo("password", "not-exposed"),
                        new DriverPropertyInfo("user", "admin"),
                        new DriverPropertyInfo("allowLoadLocalInfile", "true"),
                        new DriverPropertyInfo("autoDeserialize", "true"),
                        timeout,
                        new DriverPropertyInfo("CONNECTTIMEOUT", "duplicate"),
                        new DriverPropertyInfo("bad key", "no")
                });
        Assertions.assertEquals(1, properties.size());
        Assertions.assertEquals("connectTimeout", properties.get(0).getName());
        Assertions.assertEquals("10000", properties.get(0).getDefaultValue());
        Assertions.assertEquals("Timeout in milliseconds", properties.get(0).getDescription());
        Assertions.assertEquals(Arrays.asList("1000", "10000"), properties.get(0).getChoices());
    }

    @Test
    public void testEmptyPropertyMetadata() {
        Assertions.assertTrue(JdbcConnectionPropertyCatalog.toProperties(null).isEmpty());
        Assertions.assertTrue(JdbcConnectionPropertyCatalog.toProperties(new DriverPropertyInfo[0]).isEmpty());
    }
}
