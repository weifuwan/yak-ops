package io.yak.ops.api.service.impl;

import static io.yak.ops.api.constants.ApiFuncIdentificationConstant.DATASOURCE_DELETE;
import static io.yak.ops.api.constants.ApiFuncIdentificationConstant.DATASOURCE_UPDATE;

import io.yak.ops.api.constants.ApiFuncIdentificationConstant;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.service.DataSourceService;
import io.yak.ops.api.service.ProjectService;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.vo.DataSourceSimpleInfoVO;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.DataSourceDao;
import io.yak.ops.dao.repository.DataSourceUserDao;
import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.plugin.datasource.api.datasource.DataSourceProcessor;
import io.yak.ops.plugin.datasource.api.utils.DataSourceUtils;
import io.yak.ops.plugin.datasource.ssh.param.SSHConnectionParam;
import io.yak.ops.plugin.datasource.ssh.param.SSHDataSourceParamDTO;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.enums.DbType;
import io.yak.ops.spi.params.base.ParamsOptions;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DataSourceServiceImpl extends BaseServiceImpl implements DataSourceService {

    @Autowired
    private DataSourceDao dataSourceDao;

    @Autowired
    private DataSourceUserDao datasourceUserDao;

    @Autowired
    private ProjectService projectService;

    private void checkProjectRead(User user, long projectCode) {
        projectService.checkProjectAndAuthThrowException(user, projectCode, null);
    }

    private DataSource checkProjectDataSource(User user, long projectCode, int id, boolean write) {
        DataSource source = dataSourceDao.queryByIdAndProjectCode(id, projectCode);
        if (source == null) {
            throw new ServiceException(Status.RESOURCE_NOT_EXIST);
        }
        if (write) {
            projectService.checkHasProjectWritePermissionThrowException(user, projectCode);
        } else {
            checkProjectRead(user, projectCode);
        }
        return source;
    }

    private void checkDataSourcePermission(User user, DataSource source, boolean write, String permission) {
        if (source.getProjectCode() > 0) {
            if (write) {
                projectService.checkHasProjectWritePermissionThrowException(user, source.getProjectCode());
            } else {
                checkProjectRead(user, source.getProjectCode());
            }
        } else if (!canOperatorPermissions(user, new Object[]{source.getId()},
                AuthorizationType.DATASOURCE, permission)) {
            throw new ServiceException(Status.USER_NO_OPERATION_PERM);
        }
    }

    private static final String TABLE = "TABLE";
    private static final String VIEW = "VIEW";
    private static final String[] TABLE_TYPES = new String[]{TABLE, VIEW};
    private static final String TABLE_NAME = "TABLE_NAME";
    private static final String COLUMN_NAME = "COLUMN_NAME";

    @Override
    public DataSource createDataSource(User loginUser, BaseDataSourceParamDTO datasourceParam) {
        return createDataSourceRecord(loginUser, 0L, datasourceParam);
    }

    @Override
    public DataSource createDataSource(User loginUser, long projectCode, BaseDataSourceParamDTO datasourceParam) {
        projectService.checkHasProjectWritePermissionThrowException(loginUser, projectCode);
        return createDataSourceRecord(loginUser, projectCode, datasourceParam);
    }

    private DataSource createDataSourceRecord(User loginUser, long projectCode,
                                              BaseDataSourceParamDTO datasourceParam) {
        DataSourceUtils.checkDatasourceParam(datasourceParam);
        if (!canOperatorPermissions(loginUser, null, AuthorizationType.DATASOURCE,
                ApiFuncIdentificationConstant.DATASOURCE_CREATE_DATASOURCE)) {
            throw new ServiceException(Status.USER_NO_OPERATION_PERM);
        }
        // check name can use or not
        if (projectCode > 0
                ? dataSourceDao.existsByProjectCodeAndName(projectCode, datasourceParam.getName().trim(), null)
                : checkName(datasourceParam.getName())) {
            throw new ServiceException(Status.DATASOURCE_EXIST);
        }
        if (checkDescriptionLength(datasourceParam.getNote())) {
            throw new ServiceException(Status.DESCRIPTION_TOO_LONG_ERROR);
        }
        ConnectionParam connectionParam = DataSourceUtils.buildConnectionParams(datasourceParam);

        // build datasource
        DataSource dataSource = new DataSource();
        Date now = new Date();

        dataSource.setName(datasourceParam.getName().trim());
        dataSource.setNote(datasourceParam.getNote());
        dataSource.setUserId(loginUser.getId());
        dataSource.setProjectCode(projectCode);
        dataSource.setUserName(loginUser.getUserName());
        dataSource.setType(datasourceParam.getType());
        dataSource.setConnectionParams(JSONUtils.toJsonString(connectionParam));
        dataSource.setCreateTime(now);
        dataSource.setUpdateTime(now);
        try {
            dataSourceDao.insert(dataSource);
            // Do not return plaintext or recoverable datasource credentials from create.
            handlePasswd(Collections.singletonList(dataSource));
            return dataSource;
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(Status.DATASOURCE_EXIST);
        }
    }

    @Override
    public DataSource updateDataSource(User loginUser, long projectCode,
                                       BaseDataSourceParamDTO dataSourceParam) {
        checkProjectDataSource(loginUser, projectCode, dataSourceParam.getId(), true);
        return updateDataSource(loginUser, dataSourceParam);
    }

    @Override
    public DataSource updateDataSource(User loginUser, BaseDataSourceParamDTO dataSourceParam) {
        DataSourceUtils.checkDatasourceParam(dataSourceParam);
        // determine whether the data source exists
        DataSource dataSource = dataSourceDao.queryById(dataSourceParam.getId());
        if (dataSource == null) {
            throw new ServiceException(Status.RESOURCE_NOT_EXIST);
        }

        checkDataSourcePermission(loginUser, dataSource, true, DATASOURCE_UPDATE);

        // check name can use or not
        if (!dataSourceParam.getName().trim().equals(dataSource.getName())
                && (dataSource.getProjectCode() > 0
                ? dataSourceDao.existsByProjectCodeAndName(dataSource.getProjectCode(),
                        dataSourceParam.getName().trim(), dataSource.getId())
                : checkName(dataSourceParam.getName()))) {
            throw new ServiceException(Status.DATASOURCE_EXIST);
        }
        if (checkDescriptionLength(dataSourceParam.getNote())) {
            throw new ServiceException(Status.DESCRIPTION_TOO_LONG_ERROR);
        }
        // check password，if the password is not updated, set to the old password.
        ConnectionParam connectionParam = DataSourceUtils.buildConnectionParams(dataSourceParam);

        // SSH private keys are write-only to the frontend: blank/masked means keep the existing key.
        if (connectionParam instanceof SSHConnectionParam) {
            SSHConnectionParam ssh = (SSHConnectionParam) connectionParam;
            if (StringUtils.isBlank(ssh.getPrivateKey()) || Constants.XXXXXX.equals(ssh.getPrivateKey())) {
                ObjectNode oldParams = JSONUtils.parseObject(dataSource.getConnectionParams());
                ssh.setPrivateKey(oldParams.path("privateKey").asText(""));
            }
        }

        String password = connectionParam.getPassword();

        if (StringUtils.isBlank(password)) {
            String oldConnectionParams = dataSource.getConnectionParams();
            ObjectNode oldParams = JSONUtils.parseObject(oldConnectionParams);
            connectionParam.setPassword(oldParams.path(Constants.PASSWORD).asText());
        }

        Date now = new Date();

        dataSource.setName(dataSourceParam.getName().trim());
        dataSource.setNote(dataSourceParam.getNote());
        dataSource.setUserName(loginUser.getUserName());
        dataSource.setType(dataSource.getType());
        dataSource.setConnectionParams(JSONUtils.toJsonString(connectionParam));
        dataSource.setUpdateTime(now);
        try {
            dataSourceDao.updateById(dataSource);
            // Update has already persisted the real params; mask only the response.
            handlePasswd(Collections.singletonList(dataSource));
            return dataSource;
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(Status.DATASOURCE_EXIST);
        }
    }

    private boolean checkName(String name) {
        List<DataSource> queryDataSource = dataSourceDao.queryDataSourceByName(name.trim());
        return queryDataSource != null && !queryDataSource.isEmpty();
    }

    @Override
    public BaseDataSourceParamDTO queryDataSource(User loginUser, long projectCode, int id) {
        checkProjectDataSource(loginUser, projectCode, id, false);
        return queryDataSource(id, loginUser);
    }

    @Override
    public BaseDataSourceParamDTO queryDataSource(int id, User loginUser) {
        DataSource dataSource = dataSourceDao.queryById(id);
        if (dataSource == null) {
            log.error("Datasource does not exist, id:{}.", id);
            throw new ServiceException(Status.RESOURCE_NOT_EXIST);
        }

        checkDataSourcePermission(loginUser, dataSource, false, ApiFuncIdentificationConstant.DATASOURCE);

        // type
        BaseDataSourceParamDTO baseDataSourceParamDTO = DataSourceUtils.buildDatasourceParamDTO(
                dataSource.getType(), dataSource.getConnectionParams());
        baseDataSourceParamDTO.setId(dataSource.getId());
        baseDataSourceParamDTO.setName(dataSource.getName());
        baseDataSourceParamDTO.setNote(dataSource.getNote());
        baseDataSourceParamDTO.setPassword(getHiddenPassword());
        if (baseDataSourceParamDTO instanceof SSHDataSourceParamDTO) {
            ((SSHDataSourceParamDTO) baseDataSourceParamDTO).setPrivateKey(getHiddenPassword());
        }

        return baseDataSourceParamDTO;
    }

    @Override
    public PageInfo<DataSource> queryDataSourceListPaging(User loginUser, long projectCode, String searchVal,
                                                          Integer pageNo, Integer pageSize) {
        checkProjectRead(loginUser, projectCode);
        Page<DataSource> page = new Page<>(pageNo, pageSize);
        IPage<DataSource> result = dataSourceDao.queryByProjectCode(page, projectCode, searchVal);
        List<DataSource> sources = result.getRecords();
        handlePasswd(sources);
        PageInfo<DataSource> info = new PageInfo<>(pageNo, pageSize);
        info.setTotal((int) result.getTotal());
        info.setTotalList(sources);
        return info;
    }

    @Override
    public PageInfo<DataSource> queryDataSourceListPaging(User loginUser, String searchVal, Integer pageNo,
                                                          Integer pageSize) {
        IPage<DataSource> dataSourceList;
        Page<DataSource> dataSourcePage = new Page<>(pageNo, pageSize);
        PageInfo<DataSource> pageInfo = new PageInfo<>(pageNo, pageSize);
        if (loginUser.getUserType().equals(UserType.ADMIN_USER)) {
            dataSourceList = dataSourceDao.queryDataSourcePaging(dataSourcePage, 0, searchVal);
        } else {
            Set<Integer> ids = resourcePermissionCheckService
                    .userOwnedResourceIdsAcquisition(AuthorizationType.DATASOURCE, loginUser.getId(), log);
            if (ids.isEmpty()) {
                return pageInfo;
            }
            dataSourceList = dataSourceDao.queryDataSourcePagingByIds(dataSourcePage, new ArrayList<>(ids), searchVal);
        }

        List<DataSource> dataSources = dataSourceList != null ? dataSourceList.getRecords() : new ArrayList<>();
        handlePasswd(dataSources);
        pageInfo.setTotal((int) (dataSourceList != null ? dataSourceList.getTotal() : 0L));
        pageInfo.setTotalList(dataSources);
        return pageInfo;
    }

    /**
     * handle datasource connection password for safety
     */
    private void handlePasswd(List<DataSource> dataSourceList) {
        if (CollectionUtils.isEmpty(dataSourceList)) {
            return;
        }
        for (DataSource dataSource : dataSourceList) {
            if (dataSource == null) {
                continue;
            }

            ObjectNode object = null;
            String connectionParams = dataSource.getConnectionParams();
            if (StringUtils.isNotBlank(connectionParams)) {
                try {
                    object = JSONUtils.parseObject(connectionParams);
                } catch (RuntimeException ex) {
                    // Never include malformed connection parameters in logs or API responses.
                    log.warn("Invalid connection parameters for datasource id: {}, masking response",
                            dataSource.getId());
                }
            }
            if (object == null) {
                object = JSONUtils.createObjectNode();
            }
            object.put(Constants.PASSWORD, getHiddenPassword());
            if (dataSource.getType() == DbType.SSH) {
                object.put("privateKey", getHiddenPassword());
            }
            dataSource.setConnectionParams(object.toString());
        }
    }

    /**
     * get hidden password (resolve the security hotspot)
     *
     * @return hidden password
     */
    private String getHiddenPassword() {
        return Constants.XXXXXX;
    }

    @Override
    public List<DataSource> queryDataSourceList(User loginUser, long projectCode, Integer type) {
        checkProjectRead(loginUser, projectCode);
        List<DataSource> sources = dataSourceDao.queryByProjectCodeAndType(projectCode, type);
        handlePasswd(sources);
        return sources;
    }

    @Override
    public List<DataSource> queryDataSourceList(User loginUser, Integer type) {

        List<DataSource> datasourceList;
        if (loginUser.getUserType().equals(UserType.ADMIN_USER)) {
            datasourceList = dataSourceDao.queryDataSourceByType(0, type);
        } else {
            Set<Integer> ids = resourcePermissionCheckService
                    .userOwnedResourceIdsAcquisition(AuthorizationType.DATASOURCE, loginUser.getId(), log);
            if (ids.isEmpty()) {
                return Collections.emptyList();
            }
            datasourceList = dataSourceDao.queryByIds(ids).stream()
                    .filter(dataSource -> dataSource.getType().getCode() == type).collect(Collectors.toList());
        }

        // /datasources/list is a public API response: never expose credentials.
        handlePasswd(datasourceList);
        return datasourceList;
    }

    @Override
    public void verifyDataSourceName(long projectCode, User loginUser, String name) {
        checkProjectRead(loginUser, projectCode);
        if (dataSourceDao.existsByProjectCodeAndName(projectCode, name.trim(), null)) {
            throw new ServiceException(Status.DATASOURCE_EXIST);
        }
    }

    @Override
    public void verifyDataSourceName(String name) {
        List<DataSource> dataSourceList = dataSourceDao.queryDataSourceByName(name);
        if (dataSourceList != null && !dataSourceList.isEmpty()) {
            throw new ServiceException(Status.DATASOURCE_EXIST);
        }
    }

    @Override
    public void checkConnection(DbType type, ConnectionParam connectionParam) {
        DataSourceProcessor sshDataSourceProcessor = DataSourceUtils.getDatasourceProcessor(type);
        boolean connectivity = sshDataSourceProcessor.checkDataSourceConnectivity(connectionParam);
        if (connectivity) {
            return;
        }
        throw new ServiceException(Status.CONNECTION_TEST_FAILURE);
    }

    @Override
    public void connectionTest(User loginUser, long projectCode, int id) {
        checkProjectDataSource(loginUser, projectCode, id, false);
        connectionTest(loginUser, id);
    }

    @Override
    public void connectionTest(User loginUser, int id) {
        DataSource dataSource = dataSourceDao.queryById(id);

        if (dataSource == null) {
            throw new ServiceException(Status.RESOURCE_NOT_EXIST);
        }

        if (!canOperatorPermissions(loginUser, new Object[]{id}, AuthorizationType.DATASOURCE,
                ApiFuncIdentificationConstant.DATASOURCE)) {
            throw new ServiceException(Status.USER_NO_OPERATION_PERM);
        }

        checkConnection(dataSource.getType(),
                DataSourceUtils.buildConnectionParams(dataSource.getType(), dataSource.getConnectionParams()));
    }

    @Override
    @Transactional
    public void delete(User loginUser, long projectCode, int datasourceId) {
        checkProjectDataSource(loginUser, projectCode, datasourceId, true);
        delete(loginUser, datasourceId);
    }

    @Override
    @Transactional
    public void delete(User loginUser, int datasourceId) {
        // query datasource by id
        DataSource dataSource = dataSourceDao.queryById(datasourceId);

        if (dataSource == null) {
            throw new ServiceException(Status.RESOURCE_NOT_EXIST);
        }

        checkDataSourcePermission(loginUser, dataSource, true, DATASOURCE_DELETE);

        dataSourceDao.deleteById(datasourceId);
        datasourceUserDao.deleteByDatasourceId(datasourceId);
    }

    @Override
    public List<DataSourceSimpleInfoVO> unAuthDatasource(User loginUser, Integer userId) {
        if (isNotAdmin(loginUser)) {
            throw new ServiceException(Status.USER_NO_OPERATION_PERM);
        }
        List<DataSource> datasourceList = dataSourceDao.queryDatasourceExceptUserId(userId);
        List<DataSource> resultList = new ArrayList<>();
        Set<DataSource> datasourceSet;
        if (datasourceList != null && !datasourceList.isEmpty()) {
            datasourceSet = new HashSet<>(datasourceList);

            List<DataSource> authedDataSourceList = dataSourceDao.queryAuthedDatasource(userId);

            Set<DataSource> authedDataSourceSet;
            if (authedDataSourceList != null && !authedDataSourceList.isEmpty()) {
                authedDataSourceSet = new HashSet<>(authedDataSourceList);
                datasourceSet.removeAll(authedDataSourceSet);
            }
            resultList = new ArrayList<>(datasourceSet);
        }
        return resultList.stream().map(DataSourceSimpleInfoVO::new).collect(Collectors.toList());
    }

    @Override
    public List<DataSourceSimpleInfoVO> authedDatasource(User loginUser, Integer userId) {
        if (isNotAdmin(loginUser)) {
            throw new ServiceException(Status.USER_NO_OPERATION_PERM);
        }
        List<DataSource> authedDatasourceList = dataSourceDao.queryAuthedDatasource(userId);
        return authedDatasourceList.stream().map(DataSourceSimpleInfoVO::new).collect(Collectors.toList());
    }

    @Override
    public List<ParamsOptions> getTables(User loginUser, long projectCode,
                                        Integer datasourceId, String database) {
        checkProjectDataSource(loginUser, projectCode, datasourceId, false);
        return getTables(loginUser, datasourceId, database);
    }

    @Override
    public List<ParamsOptions> getTables(User loginUser, Integer datasourceId, String database) {
        DataSource dataSource = dataSourceDao.queryById(datasourceId);

        if (dataSource == null) {
            throw new ServiceException(Status.QUERY_DATASOURCE_ERROR);
        }

        checkDataSourcePermission(loginUser, dataSource, false, ApiFuncIdentificationConstant.DATASOURCE);

        List<String> tableList;
        BaseConnectionParam connectionParam =
                (BaseConnectionParam) DataSourceUtils.buildConnectionParams(
                        dataSource.getType(),
                        dataSource.getConnectionParams());

        if (null == connectionParam) {
            throw new ServiceException(Status.DATASOURCE_CONNECT_FAILED);
        }

        Connection connection =
                DataSourceUtils.getConnection(dataSource.getType(), connectionParam);
        ResultSet tables = null;

        try {

            if (null == connection) {
                throw new ServiceException(Status.DATASOURCE_CONNECT_FAILED);
            }

            DatabaseMetaData metaData = connection.getMetaData();
            String schema = null;
            try {
                schema = metaData.getConnection().getSchema();
            } catch (SQLException e) {
                log.error("Can not get the schema, datasourceId:{}.", datasourceId, e);
                throw new ServiceException(Status.GET_DATASOURCE_TABLES_ERROR);
            }

            tables = metaData.getTables(
                    database,
                    getDbSchemaPattern(dataSource.getType(), schema, connectionParam),
                    "%", TABLE_TYPES);
            if (null == tables) {
                log.error("Get datasource tables error, datasourceId:{}.", datasourceId);
                throw new ServiceException(Status.GET_DATASOURCE_TABLES_ERROR);
            }

            tableList = new ArrayList<>();
            while (tables.next()) {
                String name = tables.getString(TABLE_NAME);
                tableList.add(name);
            }

        } catch (Exception e) {
            log.error("Get datasource tables error, datasourceId:{}.", datasourceId, e);
            throw new ServiceException(Status.GET_DATASOURCE_TABLES_ERROR);
        } finally {
            closeResult(tables);
            releaseConnection(connection);
        }

        List<ParamsOptions> options = getParamsOptions(tableList);
        return options;
    }

    @Override
    public List<ParamsOptions> getTableColumns(User loginUser, long projectCode, Integer datasourceId,
                                               String database, String table) {
        checkProjectDataSource(loginUser, projectCode, datasourceId, false);
        return getTableColumns(loginUser, datasourceId, database, table);
    }

    @Override
    public List<ParamsOptions> getTableColumns(User loginUser, Integer datasourceId, String database,
                                               String tableName) {
        DataSource dataSource = dataSourceDao.queryById(datasourceId);

        if (dataSource == null) {
            throw new ServiceException(Status.QUERY_DATASOURCE_ERROR);
        }

        checkDataSourcePermission(loginUser, dataSource, false, ApiFuncIdentificationConstant.DATASOURCE);

        BaseConnectionParam connectionParam =
                (BaseConnectionParam) DataSourceUtils.buildConnectionParams(
                        dataSource.getType(),
                        dataSource.getConnectionParams());

        if (null == connectionParam) {
            throw new ServiceException(Status.DATASOURCE_CONNECT_FAILED);
        }

        Connection connection =
                DataSourceUtils.getConnection(dataSource.getType(), connectionParam);
        List<String> columnList = new ArrayList<>();
        ResultSet rs = null;

        try {
            if (null == connection) {
                throw new ServiceException(Status.DATASOURCE_CONNECT_FAILED);
            }

            DatabaseMetaData metaData = connection.getMetaData();

            if (dataSource.getType() == DbType.ORACLE) {
                database = null;
            }
            rs = metaData.getColumns(database, null, tableName, "%");
            if (rs == null) {
                throw new ServiceException(Status.DATASOURCE_CONNECT_FAILED);
            }
            while (rs.next()) {
                columnList.add(rs.getString(COLUMN_NAME));
            }
        } catch (Exception e) {
            log.error("Get datasource table columns error, datasourceId:{}.", dataSource.getId(), e);
            throw new ServiceException(Status.DATASOURCE_CONNECT_FAILED);
        } finally {
            closeResult(rs);
            releaseConnection(connection);
        }

        List<ParamsOptions> options = getParamsOptions(columnList);
        return options;
    }

    @Override
    public List<ParamsOptions> getDatabases(User loginUser, long projectCode, Integer datasourceId) {
        checkProjectDataSource(loginUser, projectCode, datasourceId, false);
        return getDatabases(loginUser, datasourceId);
    }

    @Override
    public List<ParamsOptions> getDatabases(User loginUser, Integer datasourceId) {

        DataSource dataSource = dataSourceDao.queryById(datasourceId);

        if (dataSource == null) {
            throw new ServiceException(Status.QUERY_DATASOURCE_ERROR);
        }

        checkDataSourcePermission(loginUser, dataSource, false, ApiFuncIdentificationConstant.DATASOURCE);

        List<String> tableList;
        BaseConnectionParam connectionParam =
                (BaseConnectionParam) DataSourceUtils.buildConnectionParams(
                        dataSource.getType(),
                        dataSource.getConnectionParams());

        if (null == connectionParam) {
            throw new ServiceException(Status.DATASOURCE_CONNECT_FAILED);
        }

        Connection connection =
                DataSourceUtils.getConnection(dataSource.getType(), connectionParam);
        ResultSet rs = null;

        try {
            if (null == connection) {
                throw new ServiceException(Status.DATASOURCE_CONNECT_FAILED);
            }
            if (dataSource.getType() == DbType.POSTGRESQL) {
                rs = connection.createStatement().executeQuery(Constants.DATABASES_QUERY_PG);
            } else {
                rs = connection.createStatement().executeQuery(Constants.DATABASES_QUERY);
            }
            tableList = new ArrayList<>();
            while (rs.next()) {
                String name = rs.getString(1);
                tableList.add(name);
            }
        } catch (Exception e) {
            log.error("Get databases error, datasourceId:{}.", datasourceId, e);
            throw new ServiceException(Status.GET_DATASOURCE_TABLES_ERROR);
        } finally {
            closeResult(rs);
            releaseConnection(connection);
        }

        List<ParamsOptions> options = getParamsOptions(tableList);
        return options;
    }

    private List<ParamsOptions> getParamsOptions(List<String> columnList) {
        List<ParamsOptions> options = null;
        if (CollectionUtils.isNotEmpty(columnList)) {
            options = new ArrayList<>();

            for (String column : columnList) {
                ParamsOptions childrenOption =
                        new ParamsOptions(column, column, false);
                options.add(childrenOption);
            }
        }
        return options;
    }

    private String getDbSchemaPattern(DbType dbType, String schema, BaseConnectionParam connectionParam) {
        if (dbType == null) {
            return null;
        }
        String schemaPattern = null;
        switch (dbType) {
            case HIVE:
                schemaPattern = connectionParam.getDatabase();
                break;
            case ORACLE:
                schemaPattern = connectionParam.getUser();
                if (null != schemaPattern) {
                    schemaPattern = schemaPattern.toUpperCase();
                }
                break;
            case SQLSERVER:
                schemaPattern = "dbo";
                break;
            case CLICKHOUSE:
            case DATABEND:
            case PRESTO:
                if (!StringUtils.isEmpty(schema)) {
                    schemaPattern = schema;
                }
                break;
            default:
                break;
        }
        return schemaPattern;
    }

    private static void releaseConnection(Connection connection) {
        if (null != connection) {
            try {
                connection.close();
            } catch (Exception e) {
                log.error("Connection release error", e);
            }
        }
    }

    private static void closeResult(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Exception e) {
                log.error("ResultSet close error", e);
            }
        }
    }

}
