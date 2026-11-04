package io.yak.ops.plugin.datasource.dameng.param;

import io.yak.ops.plugin.datasource.api.constants.DataSourceConstants;
import io.yak.ops.plugin.datasource.api.utils.PasswordUtils;
import io.yak.ops.spi.enums.DbType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DamengDataSourceProcessorTest {

    private DamengDataSourceProcessor damengDatasourceProcessor = new DamengDataSourceProcessor();

    @Test
    public void testCreateConnectionParams() {
        DamengDataSourceParamDTO damengDatasourceParamDTO = new DamengDataSourceParamDTO();
        damengDatasourceParamDTO.setUserName("SYSDBA");
        damengDatasourceParamDTO.setPassword("SYSDBA");
        damengDatasourceParamDTO.setHost("localhost");
        damengDatasourceParamDTO.setPort(5236);
        damengDatasourceParamDTO.setDatabase("PERSON");

        try (MockedStatic<PasswordUtils> mockedPasswordUtils = Mockito.mockStatic(PasswordUtils.class)) {
            Mockito.when(PasswordUtils.encodePassword(Mockito.anyString())).thenReturn("test");
            DamengConnectionParam connectionParams = (DamengConnectionParam) damengDatasourceProcessor
                    .createConnectionParams(damengDatasourceParamDTO);
            Assertions.assertEquals("jdbc:dm://localhost:5236", connectionParams.getAddress());
            Assertions.assertEquals("jdbc:dm://localhost:5236/PERSON", connectionParams.getJdbcUrl());
        }
    }

    @Test
    public void testCreateConnectionParams2() {
        String connectionJson = "{\"user\":\"SYSDBA\",\"password\":\"SYSDBA\","
                + "\"address\":\"jdbc:dm://localhost:5236\""
                + ",\"database\":\"PERSON\",\"jdbcUrl\":\"jdbc:dm://localhost:5236/PERSON\"}";
        DamengConnectionParam connectionParams = (DamengConnectionParam) damengDatasourceProcessor
                .createConnectionParams(connectionJson);
        Assertions.assertNotNull(connectionJson);
        Assertions.assertEquals("SYSDBA", connectionParams.getUser());
    }

    @Test
    public void testGetDatasourceDriver() {
        Assertions.assertEquals(DataSourceConstants.COM_DAMENG_JDBC_DRIVER,
                damengDatasourceProcessor.getDatasourceDriver());
    }

    @Test
    public void testGetJdbcUrl() {
        DamengConnectionParam damengConnectionParam = new DamengConnectionParam();
        damengConnectionParam.setJdbcUrl("jdbc:dm://localhost:5236/PERSON");
        Assertions.assertEquals(
                "jdbc:dm://localhost:5236/PERSON",
                damengDatasourceProcessor.getJdbcUrl(damengConnectionParam));
    }

    @Test
    public void testGetDbType() {
        Assertions.assertEquals(DbType.DAMENG, damengDatasourceProcessor.getDbType());
    }

    @Test
    public void testGetValidationQuery() {
        Assertions.assertEquals(DataSourceConstants.DAMENG_VALIDATION_QUERY,
                damengDatasourceProcessor.getValidationQuery());
    }

}
