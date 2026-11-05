package io.yak.ops.plugin.datasource.api.datasource;

import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.enums.DbType;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public interface DataSourceProcessor {

    /**
     * cast JSON to relate DTO
     *
     * @param paramJson
     * @return {@link BaseDataSourceParamDTO}
     */
    BaseDataSourceParamDTO castDatasourceParamDTO(String paramJson);

    /**
     * check datasource param is valid.
     * @throws IllegalArgumentException if invalid
     */
    void checkDatasourceParam(BaseDataSourceParamDTO datasourceParam);

    /**
     * get Datasource Client UniqueId
     *
     * @return UniqueId
     */
    String getDatasourceUniqueId(ConnectionParam connectionParam, DbType dbType);

    /**
     * create BaseDataSourceParamDTO by connectionJson
     *
     * @param connectionJson see{@link io.yak.ops.dao.entity.DataSource}
     * @return {@link BaseDataSourceParamDTO}
     */
    BaseDataSourceParamDTO createDatasourceParamDTO(String connectionJson);

    /**
     * create datasource connection parameter which will be stored at DataSource
     * <p>
     * see {@code io.yak.ops.dao.entity.DataSource.connectionParams}
     */
    ConnectionParam createConnectionParams(BaseDataSourceParamDTO datasourceParam);

    /**
     * deserialize json to datasource connection param
     *
     * @param connectionJson {@code io.yak.ops.dao.entity.DataSource.connectionParams}
     * @return {@link ConnectionParam}
     */
    ConnectionParam createConnectionParams(String connectionJson);

    /**
     * get datasource Driver
     */
    String getDatasourceDriver();

    /**
     * get validation Query
     */
    String getValidationQuery();

    /**
     * get jdbcUrl by connection param, the jdbcUrl is different with ConnectionParam.jdbcUrl, this method will inject
     * other to jdbcUrl
     *
     * @param connectionParam connection param
     */
    String getJdbcUrl(ConnectionParam connectionParam);

    /**
     * get connection by connectionParam
     *
     * @param connectionParam connectionParam
     * @return {@link Connection}
     */
    // todo: Change to return a ConnectionWrapper
    Connection getConnection(ConnectionParam connectionParam) throws SQLException, IOException;

    /**
     * test connection
     *
     * @param connectionParam connectionParam
     * @return true if connection is valid
     */
    boolean checkDataSourceConnectivity(ConnectionParam connectionParam);

    /**
     * @return {@link DbType}
     */
    DbType getDbType();

    /**
     * get datasource processor
     */
    DataSourceProcessor create();

    List<String> splitAndRemoveComment(String sql);

    /**
     * Optional datasource connection properties for the UI.
     * Plugins without this capability return no suggested properties.
     */
    default List<ConnectionProperty> getConnectionProperties() {
        return Collections.emptyList();
    }
}
