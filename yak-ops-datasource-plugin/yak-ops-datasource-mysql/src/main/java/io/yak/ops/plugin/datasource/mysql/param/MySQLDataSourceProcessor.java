package io.yak.ops.plugin.datasource.mysql.param;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.datasource.api.constants.DataSourceConstants;
import io.yak.ops.plugin.datasource.api.datasource.AbstractDataSourceProcessor;
import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;
import io.yak.ops.plugin.datasource.api.datasource.DataSourceProcessor;
import io.yak.ops.plugin.datasource.api.datasource.JdbcDriverConnectionProvider;
import io.yak.ops.plugin.datasource.api.utils.PasswordUtils;
import io.yak.ops.plugin.datasource.mysql.catalog.MySQLDataSourceCatalog;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.enums.DbType;

import org.apache.commons.collections4.MapUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.alibaba.druid.sql.parser.SQLParserUtils;
import com.google.auto.service.AutoService;

import lombok.extern.slf4j.Slf4j;

@AutoService(DataSourceProcessor.class)
@Slf4j
public class MySQLDataSourceProcessor extends AbstractDataSourceProcessor {

    private final MySQLDataSourceCatalog catalog = new MySQLDataSourceCatalog();

    @Override
    public List<ConnectionProperty> getConnectionProperties() {
        return catalog.getConnectionProperties();
    }

    @Override
    public MySQLDataSourceCatalog getCatalog() {
        return catalog;
    }

    private static final String ALLOW_LOAD_LOCAL_IN_FILE_NAME = "allowLoadLocalInfile";

    private static final String AUTO_DESERIALIZE = "autoDeserialize";

    private static final String ALLOW_LOCAL_IN_FILE_NAME = "allowLocalInfile";

    private static final String ALLOW_URL_IN_LOCAL_IN_FILE_NAME = "allowUrlInLocalInfile";

    @Override
    public BaseDataSourceParamDTO castDatasourceParamDTO(String paramJson) {
        return JSONUtils.parseObject(paramJson, MySQLDataSourceParamDTO.class);
    }

    @Override
    public BaseDataSourceParamDTO createDatasourceParamDTO(String connectionJson) {
        MySQLConnectionParam connectionParams = (MySQLConnectionParam) createConnectionParams(connectionJson);
        MySQLDataSourceParamDTO mysqlDatasourceParamDTO = new MySQLDataSourceParamDTO();

        mysqlDatasourceParamDTO.setUserName(connectionParams.getUser());
        mysqlDatasourceParamDTO.setDatabase(connectionParams.getDatabase());
        mysqlDatasourceParamDTO.setOther(connectionParams.getOther());

        String address = connectionParams.getAddress();
        String[] hostSeperator = address.split(Constants.DOUBLE_SLASH);
        String[] hostPortArray = hostSeperator[hostSeperator.length - 1].split(Constants.COMMA);
        mysqlDatasourceParamDTO.setPort(Integer.parseInt(hostPortArray[0].split(Constants.COLON)[1]));
        mysqlDatasourceParamDTO.setHost(hostPortArray[0].split(Constants.COLON)[0]);

        return mysqlDatasourceParamDTO;
    }

    @Override
    public BaseConnectionParam createConnectionParams(BaseDataSourceParamDTO dataSourceParam) {
        MySQLDataSourceParamDTO mysqlDatasourceParam = (MySQLDataSourceParamDTO) dataSourceParam;
        String address = String.format("%s%s:%s", DataSourceConstants.JDBC_MYSQL, mysqlDatasourceParam.getHost(),
                mysqlDatasourceParam.getPort());
        String jdbcUrl = String.format("%s/%s", address, mysqlDatasourceParam.getDatabase());

        MySQLConnectionParam mysqlConnectionParam = new MySQLConnectionParam();
        mysqlConnectionParam.setJdbcUrl(jdbcUrl);
        mysqlConnectionParam.setDatabase(mysqlDatasourceParam.getDatabase());
        mysqlConnectionParam.setAddress(address);
        mysqlConnectionParam.setUser(mysqlDatasourceParam.getUserName());
        mysqlConnectionParam.setPassword(PasswordUtils.encodePassword(mysqlDatasourceParam.getPassword()));
        mysqlConnectionParam.setDriverClassName(getDatasourceDriver());
        mysqlConnectionParam.setValidationQuery(getValidationQuery());
        mysqlConnectionParam.setOther(mysqlDatasourceParam.getOther());

        return mysqlConnectionParam;
    }

    @Override
    public ConnectionParam createConnectionParams(String connectionJson) {
        return JSONUtils.parseObject(connectionJson, MySQLConnectionParam.class);
    }

    @Override
    public String getDatasourceDriver() {
        return DataSourceConstants.COM_MYSQL_CJ_JDBC_DRIVER;
    }

    @Override
    public String getValidationQuery() {
        return DataSourceConstants.MYSQL_VALIDATION_QUERY;
    }

    @Override
    public String getJdbcUrl(ConnectionParam connectionParam) {
        MySQLConnectionParam mysqlConnectionParam = (MySQLConnectionParam) connectionParam;
        if (MapUtils.isNotEmpty(mysqlConnectionParam.getOther())) {
            return String.format("%s?%s", mysqlConnectionParam.getJdbcUrl(),
                    transformOther(mysqlConnectionParam.getOther()));
        }
        return mysqlConnectionParam.getJdbcUrl();
    }

    @Override
    public Connection getConnection(ConnectionParam connectionParam) throws SQLException {
        MySQLConnectionParam mysqlConnectionParam = (MySQLConnectionParam) connectionParam;
        String user = mysqlConnectionParam.getUser();
        if (user.contains(AUTO_DESERIALIZE)) {
            log.warn("sensitive param : {} in username field is filtered", AUTO_DESERIALIZE);
            user = user.replace(AUTO_DESERIALIZE, "");
        }
        String password = PasswordUtils.decodePassword(mysqlConnectionParam.getPassword());
        if (password.contains(AUTO_DESERIALIZE)) {
            log.warn("sensitive param : {} in password field is filtered", AUTO_DESERIALIZE);
            password = password.replace(AUTO_DESERIALIZE, "");
        }
        return JdbcDriverConnectionProvider.builder()
                .jdbcDriverClassName(getDatasourceDriver())
                .jdbcUrl(getJdbcUrl(mysqlConnectionParam))
                .username(user)
                .password(PasswordUtils.decodePassword(password))
                .properties(buildJdbcProperties(mysqlConnectionParam))
                .build()
                .getConnection();
    }

    private Properties buildJdbcProperties(MySQLConnectionParam mysqlConnectionParam) {
        Properties connectionProperties = new Properties();
        Map<String, String> paramMap = mysqlConnectionParam.getOther();
        if (MapUtils.isNotEmpty(paramMap)) {
            paramMap.forEach((k, v) -> {
                if (!checkKeyIsLegitimate(k)) {
                    log.info("Key `{}` is not legitimate for security reason", k);
                    return;
                }
                connectionProperties.put(k, v);
            });
        }
        connectionProperties.put(AUTO_DESERIALIZE, "false");
        connectionProperties.put(ALLOW_LOAD_LOCAL_IN_FILE_NAME, "false");
        connectionProperties.put(ALLOW_LOCAL_IN_FILE_NAME, "false");
        connectionProperties.put(ALLOW_URL_IN_LOCAL_IN_FILE_NAME, "false");
        return connectionProperties;
    }

    @Override
    public DbType getDbType() {
        return DbType.MYSQL;
    }

    @Override
    public DataSourceProcessor create() {
        return new MySQLDataSourceProcessor();
    }

    @Override
    public List<String> splitAndRemoveComment(String sql) {
        String cleanSQL = SQLParserUtils.removeComment(sql, com.alibaba.druid.DbType.mysql);
        return SQLParserUtils.split(cleanSQL, com.alibaba.druid.DbType.mysql);
    }

    private static boolean checkKeyIsLegitimate(String key) {
        return !key.contains(ALLOW_LOAD_LOCAL_IN_FILE_NAME)
                && !key.contains(AUTO_DESERIALIZE)
                && !key.contains(ALLOW_LOCAL_IN_FILE_NAME)
                && !key.contains(ALLOW_URL_IN_LOCAL_IN_FILE_NAME);
    }

    private String transformOther(Map<String, String> otherMap) {
        if (MapUtils.isNotEmpty(otherMap)) {
            List<String> list = new ArrayList<>(otherMap.size());
            otherMap.forEach((key, value) -> list.add(String.format("%s=%s", key, value)));
            return String.join("&", list);
        }
        return null;
    }

}
