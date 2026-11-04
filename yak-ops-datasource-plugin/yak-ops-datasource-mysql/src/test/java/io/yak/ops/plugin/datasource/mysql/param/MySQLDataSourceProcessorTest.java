package io.yak.ops.plugin.datasource.mysql.param;

import io.yak.ops.plugin.datasource.api.constants.DataSourceConstants;
import io.yak.ops.plugin.datasource.api.utils.PasswordUtils;
import io.yak.ops.spi.enums.DbType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.collect.Lists;

@ExtendWith(MockitoExtension.class)
public class MySQLDataSourceProcessorTest {

    private MySQLDataSourceProcessor mysqlDatasourceProcessor = new MySQLDataSourceProcessor();

    @Test
    public void testCreateConnectionParams() {
        Map<String, String> props = new HashMap<>();
        props.put("serverTimezone", "utc");
        MySQLDataSourceParamDTO mysqlDatasourceParamDTO = new MySQLDataSourceParamDTO();
        mysqlDatasourceParamDTO.setUserName("root");
        mysqlDatasourceParamDTO.setPassword("123456");
        mysqlDatasourceParamDTO.setHost("localhost");
        mysqlDatasourceParamDTO.setPort(3306);
        mysqlDatasourceParamDTO.setDatabase("default");
        mysqlDatasourceParamDTO.setOther(props);
        try (MockedStatic<PasswordUtils> mockedPasswordUtils = Mockito.mockStatic(PasswordUtils.class)) {
            Mockito.when(PasswordUtils.encodePassword(Mockito.anyString())).thenReturn("test");
            MySQLConnectionParam connectionParams = (MySQLConnectionParam) mysqlDatasourceProcessor
                    .createConnectionParams(mysqlDatasourceParamDTO);
            Assertions.assertEquals("jdbc:mysql://localhost:3306", connectionParams.getAddress());
            Assertions.assertEquals("jdbc:mysql://localhost:3306/default", connectionParams.getJdbcUrl());
        }
    }

    @Test
    public void testCreateConnectionParams2() {
        String connectionJson = "{\"user\":\"root\",\"password\":\"123456\",\"address\":\"jdbc:mysql://localhost:3306\""
                + ",\"database\":\"default\",\"jdbcUrl\":\"jdbc:mysql://localhost:3306/default\"}";
        MySQLConnectionParam connectionParams = (MySQLConnectionParam) mysqlDatasourceProcessor
                .createConnectionParams(connectionJson);
        Assertions.assertNotNull(connectionJson);
        Assertions.assertEquals("root", connectionParams.getUser());
    }

    @Test
    public void testGetDatasourceDriver() {
        Assertions.assertEquals(DataSourceConstants.COM_MYSQL_CJ_JDBC_DRIVER,
                mysqlDatasourceProcessor.getDatasourceDriver());
    }

    @Test
    public void testGetJdbcUrl() {
        MySQLConnectionParam mysqlConnectionParam = new MySQLConnectionParam();
        mysqlConnectionParam.setJdbcUrl("jdbc:mysql://localhost:3306/default");
        Assertions.assertEquals(
                "jdbc:mysql://localhost:3306/default",
                mysqlDatasourceProcessor.getJdbcUrl(mysqlConnectionParam));
    }

    @Test
    public void testGetDbType() {
        Assertions.assertEquals(DbType.MYSQL, mysqlDatasourceProcessor.getDbType());
    }

    @Test
    public void testGetValidationQuery() {
        Assertions.assertEquals(DataSourceConstants.MYSQL_VALIDATION_QUERY,
                mysqlDatasourceProcessor.getValidationQuery());
    }

    @Test
    public void testGetDatasourceUniqueId() {
        MySQLConnectionParam mysqlConnectionParam = new MySQLConnectionParam();
        mysqlConnectionParam.setJdbcUrl("jdbc:mysql://localhost:3306/default");
        mysqlConnectionParam.setUser("root");
        mysqlConnectionParam.setPassword("123456");
        try (MockedStatic<PasswordUtils> mockedPasswordUtils = Mockito.mockStatic(PasswordUtils.class)) {
            Mockito.when(PasswordUtils.encodePassword(Mockito.anyString())).thenReturn("123456");
            Assertions.assertEquals("mysql@root@123456@jdbc:mysql://localhost:3306/default",
                    mysqlDatasourceProcessor.getDatasourceUniqueId(mysqlConnectionParam, DbType.MYSQL));
        }
    }

    @Test
    public void testSplitAndRemoveComment() {
        String sql = "select * from table1;\nselect * from table2;\nselect * from table3;\r\n";
        List<String> expect = Lists.newArrayList(
                "select * from table1",
                "select * from table2",
                "select * from table3");
        Assertions.assertEquals(expect, mysqlDatasourceProcessor.splitAndRemoveComment(sql));

        // Variable
        sql = "select * from ${table1};";
        expect = Lists.newArrayList("select * from ${table1}");
        Assertions.assertEquals(expect, mysqlDatasourceProcessor.splitAndRemoveComment(sql));
    }

}
