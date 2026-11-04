package io.yak.ops.plugin.datasource.clickhouse.param;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.datasource.api.constants.DataSourceConstants;
import io.yak.ops.plugin.datasource.api.datasource.AbstractDataSourceProcessor;
import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.plugin.datasource.api.datasource.DataSourceProcessor;
import io.yak.ops.plugin.datasource.api.datasource.JdbcDriverConnectionProvider;
import io.yak.ops.plugin.datasource.api.utils.PasswordUtils;
import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.enums.DbType;

import org.apache.commons.collections4.MapUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.alibaba.druid.sql.parser.SQLParserUtils;
import com.google.auto.service.AutoService;

@AutoService(DataSourceProcessor.class)
public class ClickHouseDataSourceProcessor extends AbstractDataSourceProcessor {

    @Override
    public BaseDataSourceParamDTO castDatasourceParamDTO(String paramJson) {
        return JSONUtils.parseObject(paramJson, ClickHouseDataSourceParamDTO.class);
    }

    @Override
    public BaseDataSourceParamDTO createDatasourceParamDTO(String connectionJson) {
        ClickHouseConnectionParam connectionParams = (ClickHouseConnectionParam) createConnectionParams(connectionJson);

        ClickHouseDataSourceParamDTO clickHouseDatasourceParamDTO = new ClickHouseDataSourceParamDTO();
        clickHouseDatasourceParamDTO.setDatabase(connectionParams.getDatabase());
        clickHouseDatasourceParamDTO.setUserName(connectionParams.getUser());
        clickHouseDatasourceParamDTO.setOther(connectionParams.getOther());

        String[] hostSeperator = connectionParams.getAddress().split(Constants.DOUBLE_SLASH);
        String[] hostPortArray = hostSeperator[hostSeperator.length - 1].split(Constants.COMMA);
        clickHouseDatasourceParamDTO.setPort(Integer.parseInt(hostPortArray[0].split(Constants.COLON)[1]));
        clickHouseDatasourceParamDTO.setHost(hostPortArray[0].split(Constants.COLON)[0]);

        return clickHouseDatasourceParamDTO;
    }

    @Override
    public ConnectionParam createConnectionParams(BaseDataSourceParamDTO datasourceParam) {
        ClickHouseDataSourceParamDTO clickHouseParam = (ClickHouseDataSourceParamDTO) datasourceParam;
        String address = String.format("%s%s:%s", DataSourceConstants.JDBC_CLICKHOUSE, clickHouseParam.getHost(),
                clickHouseParam.getPort());
        String jdbcUrl = address + "/" + clickHouseParam.getDatabase();

        ClickHouseConnectionParam clickhouseConnectionParam = new ClickHouseConnectionParam();
        clickhouseConnectionParam.setDatabase(clickHouseParam.getDatabase());
        clickhouseConnectionParam.setAddress(address);
        clickhouseConnectionParam.setJdbcUrl(jdbcUrl);
        clickhouseConnectionParam.setUser(clickHouseParam.getUserName());
        clickhouseConnectionParam.setPassword(PasswordUtils.encodePassword(clickHouseParam.getPassword()));
        clickhouseConnectionParam.setDriverClassName(getDatasourceDriver());
        clickhouseConnectionParam.setValidationQuery(getValidationQuery());
        clickhouseConnectionParam.setOther(clickHouseParam.getOther());
        return clickhouseConnectionParam;
    }

    @Override
    public ConnectionParam createConnectionParams(String connectionJson) {
        return JSONUtils.parseObject(connectionJson, ClickHouseConnectionParam.class);
    }

    @Override
    public String getDatasourceDriver() {
        return DataSourceConstants.COM_CLICKHOUSE_JDBC_DRIVER;
    }

    @Override
    public String getValidationQuery() {
        return DataSourceConstants.CLICKHOUSE_VALIDATION_QUERY;
    }

    @Override
    public String getJdbcUrl(ConnectionParam connectionParam) {
        ClickHouseConnectionParam clickhouseConnectionParam = (ClickHouseConnectionParam) connectionParam;
        String jdbcUrl = clickhouseConnectionParam.getJdbcUrl();
        if (MapUtils.isNotEmpty(clickhouseConnectionParam.getOther())) {
            jdbcUrl = String.format("%s?%s", jdbcUrl, transformOther(clickhouseConnectionParam.getOther()));
        }
        return jdbcUrl;
    }

    @Override
    public Connection getConnection(ConnectionParam connectionParam) throws SQLException {
        ClickHouseConnectionParam clickhouseConnectionParam = (ClickHouseConnectionParam) connectionParam;
        return JdbcDriverConnectionProvider.builder()
                .jdbcDriverClassName(getDatasourceDriver())
                .jdbcUrl(getJdbcUrl(clickhouseConnectionParam))
                .username(clickhouseConnectionParam.getUser())
                .password(PasswordUtils.decodePassword(clickhouseConnectionParam.getPassword()))
                .build()
                .getConnection();
    }

    @Override
    public DbType getDbType() {
        return DbType.CLICKHOUSE;
    }

    @Override
    public DataSourceProcessor create() {
        return new ClickHouseDataSourceProcessor();
    }

    @Override
    public List<String> splitAndRemoveComment(String sql) {
        String cleanSQL = SQLParserUtils.removeComment(sql, com.alibaba.druid.DbType.clickhouse);
        return SQLParserUtils.split(cleanSQL, com.alibaba.druid.DbType.clickhouse);
    }

    private String transformOther(Map<String, String> otherMap) {
        if (MapUtils.isEmpty(otherMap)) {
            return null;
        }
        List<String> otherList = new ArrayList<>();
        otherMap.forEach((key, value) -> otherList.add(String.format("%s=%s", key, value)));
        return String.join("&", otherList);
    }

}
