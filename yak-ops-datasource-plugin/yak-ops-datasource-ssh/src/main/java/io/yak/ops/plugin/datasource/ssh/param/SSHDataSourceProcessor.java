package io.yak.ops.plugin.datasource.ssh.param;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.datasource.api.datasource.AbstractDataSourceProcessor;
import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.plugin.datasource.api.datasource.DataSourceProcessor;
import io.yak.ops.plugin.datasource.api.utils.PasswordUtils;
import io.yak.ops.plugin.datasource.ssh.SshClientWrapper;
import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.enums.DbType;

import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.text.MessageFormat;

import com.google.auto.service.AutoService;

import lombok.extern.slf4j.Slf4j;

@AutoService(DataSourceProcessor.class)
@Slf4j
public class SSHDataSourceProcessor extends AbstractDataSourceProcessor {

    @Override
    public BaseDataSourceParamDTO castDatasourceParamDTO(String paramJson) {
        return JSONUtils.parseObject(paramJson, SSHDataSourceParamDTO.class);
    }

    @Override
    public void checkDatasourceParam(BaseDataSourceParamDTO datasourceParamDTO) {
        if (StringUtils.isEmpty(datasourceParamDTO.getHost())
                || StringUtils.isEmpty(datasourceParamDTO.getUserName())) {
            throw new IllegalArgumentException("ssh datasource param is not valid");
        }
    }

    @Override
    public String getDatasourceUniqueId(ConnectionParam connectionParam, DbType dbType) {
        SSHConnectionParam baseConnectionParam = (SSHConnectionParam) connectionParam;
        return MessageFormat.format("{0}@{1}@{2}@{3}", dbType.getName(), baseConnectionParam.getHost(),
                baseConnectionParam.getUser(),
                PasswordUtils.encodePassword(baseConnectionParam.getPassword()));
    }

    @Override
    public BaseDataSourceParamDTO createDatasourceParamDTO(String connectionJson) {
        SSHConnectionParam connectionParams = (SSHConnectionParam) createConnectionParams(connectionJson);
        SSHDataSourceParamDTO sshDataSourceParamDTO = new SSHDataSourceParamDTO();

        sshDataSourceParamDTO.setUserName(connectionParams.getUser());
        sshDataSourceParamDTO.setPassword(connectionParams.getPassword());
        sshDataSourceParamDTO.setHost(connectionParams.getHost());
        sshDataSourceParamDTO.setPort(connectionParams.getPort());
        sshDataSourceParamDTO.setPrivateKey(connectionParams.getPrivateKey());

        return sshDataSourceParamDTO;
    }

    @Override
    public SSHConnectionParam createConnectionParams(BaseDataSourceParamDTO dataSourceParam) {
        SSHDataSourceParamDTO sshDataSourceParam = (SSHDataSourceParamDTO) dataSourceParam;
        SSHConnectionParam sshConnectionParam = new SSHConnectionParam();
        sshConnectionParam.setUser(sshDataSourceParam.getUserName());
        sshConnectionParam.setPassword(sshDataSourceParam.getPassword());
        sshConnectionParam.setHost(sshDataSourceParam.getHost());
        sshConnectionParam.setPort(sshDataSourceParam.getPort());
        sshConnectionParam.setPrivateKey(sshDataSourceParam.getPrivateKey());

        return sshConnectionParam;
    }

    @Override
    public ConnectionParam createConnectionParams(String connectionJson) {
        return JSONUtils.parseObject(connectionJson, SSHConnectionParam.class);
    }

    @Override
    public String getDatasourceDriver() {
        return "";
    }

    @Override
    public String getValidationQuery() {
        return "";
    }

    @Override
    public String getJdbcUrl(ConnectionParam connectionParam) {
        return "";
    }

    @Override
    public Connection getConnection(ConnectionParam connectionParam) {
        return null;
    }

    @Override
    public boolean checkDataSourceConnectivity(ConnectionParam connectionParam) {
        SSHConnectionParam baseConnectionParam = (SSHConnectionParam) connectionParam;
        try (
                SshClientWrapper sshClientWrapper = new SshClientWrapper(
                        baseConnectionParam.getHost(),
                        baseConnectionParam.getPort(),
                        baseConnectionParam.getUser(),
                        baseConnectionParam.getPassword(),
                        baseConnectionParam.getPrivateKey())) {

            return sshClientWrapper.isAuth();
        } catch (Exception e) {
            log.error("ssh test connection failed", e);
            return false;

        }
    }

    @Override
    public DbType getDbType() {
        return DbType.SSH;
    }

    @Override
    public DataSourceProcessor create() {
        return new SSHDataSourceProcessor();
    }

}
