package io.yak.ops.plugin.datasource.dameng.param;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.datasource.api.constants.DataSourceConstants;
import io.yak.ops.plugin.datasource.api.datasource.AbstractDataSourceProcessor;
import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.plugin.datasource.api.datasource.DataSourceProcessor;
import io.yak.ops.plugin.datasource.api.datasource.JdbcDriverConnectionProvider;
import io.yak.ops.plugin.datasource.api.utils.PasswordUtils;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.enums.DbType;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.alibaba.druid.sql.parser.SQLParserUtils;
import com.google.auto.service.AutoService;

@AutoService(DataSourceProcessor.class)
public class DamengDataSourceProcessor extends AbstractDataSourceProcessor {

    @Override
    public BaseDataSourceParamDTO castDatasourceParamDTO(String paramJson) {
        return JSONUtils.parseObject(paramJson, DamengDataSourceParamDTO.class);
    }

    @Override
    public BaseDataSourceParamDTO createDatasourceParamDTO(String connectionJson) {
        DamengConnectionParam connectionParams = (DamengConnectionParam) createConnectionParams(connectionJson);
        DamengDataSourceParamDTO damengDatasourceParamDTO = new DamengDataSourceParamDTO();

        damengDatasourceParamDTO.setUserName(connectionParams.getUser());
        damengDatasourceParamDTO.setDatabase(connectionParams.getDatabase());
        damengDatasourceParamDTO.setOther(connectionParams.getOther());

        String address = connectionParams.getAddress();
        String[] hostSeperator = address.split(Constants.DOUBLE_SLASH);
        String[] hostPortArray = hostSeperator[hostSeperator.length - 1].split(Constants.COMMA);
        damengDatasourceParamDTO.setPort(Integer.parseInt(hostPortArray[0].split(Constants.COLON)[1]));
        damengDatasourceParamDTO.setHost(hostPortArray[0].split(Constants.COLON)[0]);

        return damengDatasourceParamDTO;
    }

    @Override
    public BaseConnectionParam createConnectionParams(BaseDataSourceParamDTO dataSourceParam) {
        DamengDataSourceParamDTO dmDatasourceParam = (DamengDataSourceParamDTO) dataSourceParam;
        String address = String
                .format("%s%s:%s", DataSourceConstants.JDBC_DAMENG, dmDatasourceParam.getHost(),
                        dmDatasourceParam.getPort());
        String jdbcUrl = StringUtils.isEmpty(dmDatasourceParam.getDatabase()) ? address
                : String.format("%s/%s", address,
                        dmDatasourceParam.getDatabase());

        DamengConnectionParam damengConnectionParam = new DamengConnectionParam();
        damengConnectionParam.setJdbcUrl(jdbcUrl);
        damengConnectionParam.setDatabase(damengConnectionParam.getDatabase());
        damengConnectionParam.setAddress(address);
        damengConnectionParam.setUser(dmDatasourceParam.getUserName());
        damengConnectionParam.setPassword(PasswordUtils.encodePassword(dmDatasourceParam.getPassword()));
        damengConnectionParam.setDriverClassName(getDatasourceDriver());
        damengConnectionParam.setValidationQuery(getValidationQuery());
        damengConnectionParam.setOther(dmDatasourceParam.getOther());

        return damengConnectionParam;
    }

    @Override
    public ConnectionParam createConnectionParams(String connectionJson) {
        return JSONUtils.parseObject(connectionJson, DamengConnectionParam.class);
    }

    @Override
    public String getDatasourceDriver() {
        return DataSourceConstants.COM_DAMENG_JDBC_DRIVER;
    }

    @Override
    public String getValidationQuery() {
        return DataSourceConstants.DAMENG_VALIDATION_QUERY;
    }

    @Override
    public String getJdbcUrl(ConnectionParam connectionParam) {
        DamengConnectionParam damengConnectionParam = (DamengConnectionParam) connectionParam;
        String jdbcUrl = damengConnectionParam.getJdbcUrl();
        if (MapUtils.isNotEmpty(damengConnectionParam.getOther())) {
            return String.format("%s?%s", jdbcUrl, transformOther(damengConnectionParam.getOther()));
        }
        return jdbcUrl;
    }

    @Override
    public Connection getConnection(ConnectionParam connectionParam) throws SQLException {
        DamengConnectionParam damengConnectionParam = (DamengConnectionParam) connectionParam;
        return JdbcDriverConnectionProvider.builder()
                .jdbcDriverClassName(getDatasourceDriver())
                .jdbcUrl(getJdbcUrl(damengConnectionParam))
                .username(damengConnectionParam.getUser())
                .password(PasswordUtils.decodePassword(damengConnectionParam.getPassword()))
                .build()
                .getConnection();
    }

    @Override
    public DbType getDbType() {
        return DbType.DAMENG;
    }

    @Override
    public DataSourceProcessor create() {
        return new DamengDataSourceProcessor();
    }

    @Override
    public List<String> splitAndRemoveComment(String sql) {
        String cleanSQL = SQLParserUtils.removeComment(sql, com.alibaba.druid.DbType.dm);
        return SQLParserUtils.split(cleanSQL, com.alibaba.druid.DbType.dm);
    }

    private String transformOther(Map<String, String> paramMap) {
        if (MapUtils.isEmpty(paramMap)) {
            return null;
        }
        List<String> otherList = new ArrayList<>();
        paramMap.forEach((key, value) -> otherList.add(String.format("%s=%s", key, value)));
        return String.join("&", otherList);
    }

}
