package io.yak.ops.api.service;

import io.yak.ops.spi.enums.DbType;

import java.sql.DriverPropertyInfo;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JdbcDriverPropertyServiceTest {

    @Test
    public void testFiltersCredentialAndUnsafeProperties() {
        DriverPropertyInfo timeout = new DriverPropertyInfo("connectTimeout", "10000");
        timeout.description = "Timeout in milliseconds";
        timeout.choices = new String[]{"1000", "10000"};
        List<JdbcDriverPropertyService.Property> properties = JdbcDriverPropertyService.toProperties(
                new DriverPropertyInfo[]{
                        new DriverPropertyInfo("password", "secret"),
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
    public void testEmptyResultsAndUnsupportedTypes() {
        Assertions.assertTrue(JdbcDriverPropertyService.toProperties(null).isEmpty());
        Assertions.assertTrue(JdbcDriverPropertyService.toProperties(new DriverPropertyInfo[0]).isEmpty());
        JdbcDriverPropertyService service = new JdbcDriverPropertyService();
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.getProperties(DbType.ORACLE));
    }

    @Test
    public void testInstalledDriverMetadataWithoutConnecting() {
        JdbcDriverPropertyService service = new JdbcDriverPropertyService();
        for (DbType type : new DbType[]{DbType.MYSQL, DbType.POSTGRESQL}) {
            JdbcDriverPropertyService.DriverProperties metadata = service.getProperties(type);
            Assertions.assertEquals(type.name(), metadata.getDbType());
            Assertions.assertNotNull(metadata.getDriverClassName());
            Assertions.assertFalse(metadata.getDriverClassName().isEmpty());
            Assertions.assertNotNull(metadata.getDriverVersion());
            Assertions.assertFalse(metadata.getAcceptedPropertyKeys().isEmpty());
            Assertions.assertEquals(metadata.getAcceptedPropertyKeys().size(), metadata.getProperties().size());
            Assertions.assertFalse(metadata.getAcceptedPropertyKeys().contains("password"));
        }
    }
}
