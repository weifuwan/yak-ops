package io.yak.ops.plugin.datasource.db2.param;

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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.alibaba.druid.sql.parser.SQLParserUtils;
import com.google.auto.service.AutoService;

@AutoService(DataSourceProcessor.class)
public class Db2DataSourceProcessor extends AbstractDataSourceProcessor {

    @Override
    public BaseDataSourceParamDTO castDatasourceParamDTO(String paramJson) {
        return JSONUtils.parseObject(paramJson, Db2DataSourceParamDTO.class);
    }

    @Override
    public BaseDataSourceParamDTO createDatasourceParamDTO(String connectionJson) {
        Db2ConnectionParam connectionParams = (Db2ConnectionParam) createConnectionParams(connectionJson);

        Db2DataSourceParamDTO db2DatasourceParamDTO = new Db2DataSourceParamDTO();
        db2DatasourceParamDTO.setDatabase(connectionParams.getDatabase());
        db2DatasourceParamDTO.setOther(connectionParams.getOther());
        db2DatasourceParamDTO.setUserName(connectionParams.getUser());

        String[] hostSeperator = connectionParams.getAddress().split(Constants.DOUBLE_SLASH);
        String[] hostPortArray = hostSeperator[hostSeperator.length - 1].split(Constants.COMMA);
        db2DatasourceParamDTO.setHost(hostPortArray[0].split(Constants.COLON)[0]);
        db2DatasourceParamDTO.setPort(Integer.parseInt(hostPortArray[0].split(Constants.COLON)[1]));

        return db2DatasourceParamDTO;
    }

    @Override
    public BaseConnectionParam createConnectionParams(BaseDataSourceParamDTO datasourceParam) {
        Db2DataSourceParamDTO db2Param = (Db2DataSourceParamDTO) datasourceParam;
        String address = String.format("%s%s:%s", DataSourceConstants.JDBC_DB2, db2Param.getHost(), db2Param.getPort());
        String jdbcUrl = String.format("%s/%s", address, db2Param.getDatabase());

        Db2ConnectionParam db2ConnectionParam = new Db2ConnectionParam();
        db2ConnectionParam.setAddress(address);
        db2ConnectionParam.setDatabase(db2Param.getDatabase());
        db2ConnectionParam.setJdbcUrl(jdbcUrl);
        db2ConnectionParam.setUser(db2Param.getUserName());
        db2ConnectionParam.setPassword(PasswordUtils.encodePassword(db2Param.getPassword()));
        db2ConnectionParam.setDriverClassName(getDatasourceDriver());
        db2ConnectionParam.setValidationQuery(getValidationQuery());
        db2ConnectionParam.setOther(db2Param.getOther());

        return db2ConnectionParam;
    }

    @Override
    public ConnectionParam createConnectionParams(String connectionJson) {
        return JSONUtils.parseObject(connectionJson, Db2ConnectionParam.class);
    }

    @Override
    public String getDatasourceDriver() {
        return DataSourceConstants.COM_DB2_JDBC_DRIVER;
    }

    @Override
    public String getJdbcUrl(ConnectionParam connectionParam) {
        Db2ConnectionParam db2ConnectionParam = (Db2ConnectionParam) connectionParam;
        if (MapUtils.isNotEmpty(db2ConnectionParam.getOther())) {
            return String.format("%s:%s", db2ConnectionParam.getJdbcUrl(),
                    transformOther(db2ConnectionParam.getOther()));
        }
        return db2ConnectionParam.getJdbcUrl();
    }

    @Override
    public Connection getConnection(ConnectionParam connectionParam) throws SQLException {
        Db2ConnectionParam db2ConnectionParam = (Db2ConnectionParam) connectionParam;
        return JdbcDriverConnectionProvider.builder()
                .jdbcDriverClassName(getDatasourceDriver())
                .jdbcUrl(getJdbcUrl(db2ConnectionParam))
                .username(db2ConnectionParam.getUser())
                .password(PasswordUtils.decodePassword(db2ConnectionParam.getPassword()))
                .build()
                .getConnection();
    }

    @Override
    public DbType getDbType() {
        return DbType.DB2;
    }

    @Override
    public DataSourceProcessor create() {
        return new Db2DataSourceProcessor();
    }

    @Override
    public String getValidationQuery() {
        return DataSourceConstants.DB2_VALIDATION_QUERY;
    }

    @Override
    public List<String> splitAndRemoveComment(String sql) {
        String cleanSQL = SQLParserUtils.removeComment(sql, com.alibaba.druid.DbType.db2);
        return SQLParserUtils.split(cleanSQL, com.alibaba.druid.DbType.db2);
    }

    private String transformOther(Map<String, String> otherMap) {
        if (MapUtils.isEmpty(otherMap)) {
            return null;
        }
        List<String> otherList = new ArrayList<>();
        otherMap.forEach((key, value) -> otherList.add(String.format("%s=%s", key, value)));
        return String.join(";", otherList);
    }

}
