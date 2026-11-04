package io.yak.ops.plugin.datasource.postgresql.param;

import static io.yak.ops.plugin.datasource.api.constants.DataSourceConstants.ORG_POSTGRESQL_DRIVER;
import static io.yak.ops.plugin.datasource.api.constants.DataSourceConstants.POSTGRESQL_VALIDATION_QUERY;

import io.yak.ops.plugin.datasource.api.utils.PasswordUtils;
import io.yak.ops.spi.enums.DbType;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.collect.ImmutableMap;

@ExtendWith(MockitoExtension.class)
public class PostgreSQLDataSourceProcessorTest {

    private PostgreSQLDataSourceProcessor postgreSqlDatasourceProcessor = new PostgreSQLDataSourceProcessor();

    @Test
    public void testCreateConnectionParams() {
        Map<String, String> props = new HashMap<>();
        props.put("serverTimezone", "utc");
        PostgreSQLDataSourceParamDTO postgreSqlDatasourceParamDTO = new PostgreSQLDataSourceParamDTO();
        postgreSqlDatasourceParamDTO.setUserName("root");
        postgreSqlDatasourceParamDTO.setPassword("123456");
        postgreSqlDatasourceParamDTO.setHost("localhost");
        postgreSqlDatasourceParamDTO.setPort(3308);
        postgreSqlDatasourceParamDTO.setDatabase("default");
        postgreSqlDatasourceParamDTO.setOther(props);
        try (MockedStatic<PasswordUtils> mockedStaticPasswordUtils = Mockito.mockStatic(PasswordUtils.class)) {
            mockedStaticPasswordUtils.when(() -> PasswordUtils.encodePassword(Mockito.anyString())).thenReturn("test");
            PostgreSQLConnectionParam connectionParams = (PostgreSQLConnectionParam) postgreSqlDatasourceProcessor
                    .createConnectionParams(postgreSqlDatasourceParamDTO);
            Assertions.assertEquals("jdbc:postgresql://localhost:3308", connectionParams.getAddress());
            Assertions.assertEquals("jdbc:postgresql://localhost:3308/default", connectionParams.getJdbcUrl());
            Assertions.assertEquals("root", connectionParams.getUser());
        }
    }

    @Test
    public void testCreateConnectionParams2() {
        String connectionJson =
                "{\"user\":\"root\",\"password\":\"123456\",\"address\":\"jdbc:postgresql://localhost:3308\""
                        + ",\"database\":\"default\",\"jdbcUrl\":\"jdbc:postgresql://localhost:3308/default\"}";
        PostgreSQLConnectionParam connectionParams = (PostgreSQLConnectionParam) postgreSqlDatasourceProcessor
                .createConnectionParams(connectionJson);
        Assertions.assertNotNull(connectionParams);
        Assertions.assertEquals("root", connectionParams.getUser());
    }

    @Test
    public void testGetDatasourceDriver() {
        Assertions.assertEquals(ORG_POSTGRESQL_DRIVER, postgreSqlDatasourceProcessor.getDatasourceDriver());
    }

    @Test
    public void testGetJdbcUrl() {
        PostgreSQLConnectionParam postgreSqlConnectionParam = new PostgreSQLConnectionParam();
        postgreSqlConnectionParam.setJdbcUrl("jdbc:postgresql://localhost:3308/default");
        ImmutableMap<String, String> map = new ImmutableMap.Builder<String, String>()
                .put("other", "other")
                .build();
        postgreSqlConnectionParam.setOther(map);

        String jdbcUrl = postgreSqlDatasourceProcessor.getJdbcUrl(postgreSqlConnectionParam);
        Assertions.assertEquals("jdbc:postgresql://localhost:3308/default?other=other", jdbcUrl);

    }

    @Test
    public void testGetDbType() {
        Assertions.assertEquals(DbType.POSTGRESQL, postgreSqlDatasourceProcessor.getDbType());
    }

    @Test
    public void testGetValidationQuery() {
        Assertions.assertEquals(POSTGRESQL_VALIDATION_QUERY,
                postgreSqlDatasourceProcessor.getValidationQuery());
    }
}
