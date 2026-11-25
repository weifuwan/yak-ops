package io.yak.ops.plugin.doris.param;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.datasource.api.catalog.CatalogPath;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.constants.DataSourceConstants;
import io.yak.ops.plugin.datasource.api.datasource.AbstractDataSourceProcessor;
import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.plugin.datasource.api.datasource.DataSourceProcessor;
import io.yak.ops.plugin.datasource.api.datasource.JdbcDriverConnectionProvider;
import io.yak.ops.plugin.datasource.api.utils.PasswordUtils;
import io.yak.ops.plugin.doris.catalog.DorisDataSourceCatalog;
import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.enums.DbType;
import com.alibaba.druid.sql.parser.SQLParserUtils;
import com.google.auto.service.AutoService;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.MapUtils;

/** Doris datasource plugin. Connection and catalog behavior stay within this plugin. */
@AutoService(DataSourceProcessor.class)
public class DorisDataSourceProcessor extends AbstractDataSourceProcessor {

    private final DorisDataSourceCatalog catalog = new DorisDataSourceCatalog();

    @Override
    public DataSourceCatalog getCatalog() {
        return catalog;
    }

    @Override
    public BaseDataSourceParamDTO castDatasourceParamDTO(String paramJson) {
        return JSONUtils.parseObject(paramJson, DorisDataSourceParamDTO.class);
    }

    @Override
    public BaseDataSourceParamDTO createDatasourceParamDTO(String connectionJson) {
        DorisConnectionParam connectionParams =
                (DorisConnectionParam) createConnectionParams(connectionJson);
        DorisDataSourceParamDTO datasourceParam = new DorisDataSourceParamDTO();
        datasourceParam.setUserName(connectionParams.getUser());
        datasourceParam.setDatabase(connectionParams.getDatabase());
        datasourceParam.setOther(connectionParams.getOther());

        String address = connectionParams.getAddress();
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Doris datasource address is required");
        }
        String[] hosts = address.split(Constants.DOUBLE_SLASH);
        String[] hostPorts = hosts[hosts.length - 1].split(Constants.COMMA);
        List<String> hostNames = new ArrayList<>();
        Integer port = null;
        for (String hostPort : hostPorts) {
            String[] parts = hostPort.split(Constants.COLON);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Doris datasource address is invalid");
            }
            hostNames.add(parts[0]);
            if (port == null) {
                port = Integer.valueOf(parts[1]);
            } else if (!port.equals(Integer.valueOf(parts[1]))) {
                throw new IllegalArgumentException("All Doris frontend addresses must use the same port");
            }
        }
        datasourceParam.setHost(String.join(Constants.COMMA, hostNames));
        datasourceParam.setPort(port);
        return datasourceParam;
    }

    @Override
    public ConnectionParam createConnectionParams(BaseDataSourceParamDTO datasourceParam) {
        DorisDataSourceParamDTO dorisParam = (DorisDataSourceParamDTO) datasourceParam;
        String[] hosts = dorisParam.getHost().split(Constants.COMMA);
        for (int i = 0; i < hosts.length; i++) {
            hosts[i] = String.format(Constants.FORMAT_S_S_COLON, hosts[i], dorisParam.getPort());
        }

        String address = String.format(
                "%s%s", DataSourceConstants.JDBC_MYSQL_LOADBALANCE, String.join(",", hosts));
        String jdbcUrl = String.format(Constants.FORMAT_S_S, address, dorisParam.getDatabase());

        DorisConnectionParam connectionParam = new DorisConnectionParam();
        connectionParam.setJdbcUrl(jdbcUrl);
        connectionParam.setDatabase(dorisParam.getDatabase());
        connectionParam.setAddress(address);
        connectionParam.setUser(dorisParam.getUserName());
        connectionParam.setPassword(PasswordUtils.encodePassword(dorisParam.getPassword()));
        connectionParam.setDriverClassName(getDatasourceDriver());
        connectionParam.setValidationQuery(getValidationQuery());
        connectionParam.setOther(dorisParam.getOther());
        return connectionParam;
    }

    @Override
    public ConnectionParam createConnectionParams(String connectionJson) {
        return JSONUtils.parseObject(connectionJson, DorisConnectionParam.class);
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
        DorisConnectionParam dorisParam = (DorisConnectionParam) connectionParam;
        String jdbcUrl = dorisParam.getJdbcUrl();
        if (MapUtils.isEmpty(dorisParam.getOther())) {
            return jdbcUrl;
        }
        return jdbcUrl + "?" + transformOther(dorisParam.getOther());
    }

    @Override
    public Connection getConnection(ConnectionParam connectionParam) throws SQLException {
        DorisConnectionParam dorisParam = (DorisConnectionParam) connectionParam;
        return JdbcDriverConnectionProvider.builder()
                .jdbcDriverClassName(getDatasourceDriver())
                .jdbcUrl(getJdbcUrl(dorisParam))
                .username(dorisParam.getUser())
                .password(PasswordUtils.decodePassword(dorisParam.getPassword()))
                .build()
                .getConnection();
    }

    @Override
    public Connection getConnection(ConnectionParam connectionParam, CatalogPath path) throws SQLException {
        if (path == null || path.getDatabase() == null
                || !(connectionParam instanceof DorisConnectionParam)) {
            return getConnection(connectionParam);
        }
        DorisConnectionParam source = (DorisConnectionParam) connectionParam;
        if (source.getDatabase() == null || !path.getDatabase().equals(source.getDatabase())) {
            throw new IllegalArgumentException(
                    "Catalog database '" + path.getDatabase()
                            + "' is outside the configured Doris datasource database '"
                            + source.getDatabase() + "'");
        }
        return getConnection(connectionParam);
    }

    @Override
    public DbType getDbType() {
        return DbType.DORIS;
    }

    @Override
    public DataSourceProcessor create() {
        return new DorisDataSourceProcessor();
    }

    @Override
    public List<String> splitAndRemoveComment(String sql) {
        String cleanSql = SQLParserUtils.removeComment(sql, com.alibaba.druid.DbType.mysql);
        return SQLParserUtils.split(cleanSql, com.alibaba.druid.DbType.mysql);
    }

    private String transformOther(Map<String, String> parameters) {
        List<String> values = new ArrayList<>(parameters.size());
        parameters.forEach((key, value) -> values.add(key + "=" + value));
        return String.join("&", values);
    }
}
