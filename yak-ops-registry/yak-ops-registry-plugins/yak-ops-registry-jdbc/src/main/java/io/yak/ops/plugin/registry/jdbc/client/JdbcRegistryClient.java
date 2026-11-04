package io.yak.ops.plugin.registry.jdbc.client;

import io.yak.ops.common.utils.CodeGenerateUtils;
import io.yak.ops.common.utils.NetUtils;
import io.yak.ops.common.utils.OSUtils;
import io.yak.ops.plugin.registry.jdbc.model.DTO.DataType;
import io.yak.ops.plugin.registry.jdbc.model.DTO.JdbcRegistryDataDTO;
import io.yak.ops.plugin.registry.jdbc.server.ConnectionStateListener;
import io.yak.ops.plugin.registry.jdbc.server.IJdbcRegistryServer;
import io.yak.ops.plugin.registry.jdbc.server.JdbcRegistryDataChangeListener;
import io.yak.ops.plugin.registry.jdbc.server.JdbcRegistryServerState;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * The client of jdbc registry, used to interact with the {@link io.yak.ops.plugin.registry.jdbc.server.JdbcRegistryServer}.
 */
@Slf4j
public class JdbcRegistryClient implements IJdbcRegistryClient {

    private static final String DEFAULT_CLIENT_NAME = NetUtils.getHost() + "_" + OSUtils.getProcessID();

    private final JdbcRegistryClientIdentify jdbcRegistryClientIdentify;

    private final IJdbcRegistryServer jdbcRegistryServer;

    public JdbcRegistryClient(IJdbcRegistryServer jdbcRegistryServer) {
        this.jdbcRegistryServer = jdbcRegistryServer;
        this.jdbcRegistryClientIdentify =
                new JdbcRegistryClientIdentify(CodeGenerateUtils.genCode(), DEFAULT_CLIENT_NAME);
    }

    @Override
    public void start() {
        jdbcRegistryServer.registerClient(this);
    }

    @Override
    public JdbcRegistryClientIdentify getJdbcRegistryClientIdentify() {
        return jdbcRegistryClientIdentify;
    }

    @Override
    public void subscribeConnectionStateChange(ConnectionStateListener connectionStateListener) {
        jdbcRegistryServer.subscribeConnectionStateChange(connectionStateListener);
    }

    @Override
    public void subscribeJdbcRegistryDataChange(JdbcRegistryDataChangeListener jdbcRegistryDataChangeListener) {
        jdbcRegistryServer.subscribeJdbcRegistryDataChange(jdbcRegistryDataChangeListener);
    }

    @Override
    public Optional<JdbcRegistryDataDTO> getJdbcRegistryDataByKey(String key) {
        return jdbcRegistryServer.getJdbcRegistryDataByKey(key);
    }

    @Override
    public void putJdbcRegistryData(String key, String value, DataType dataType) {
        jdbcRegistryServer.putJdbcRegistryData(jdbcRegistryClientIdentify.getClientId(), key, value, dataType);
    }

    @Override
    public void deleteJdbcRegistryDataByKey(String key) {
        jdbcRegistryServer.deleteJdbcRegistryDataByKey(key);
    }

    @Override
    public List<JdbcRegistryDataDTO> listJdbcRegistryDataChildren(String key) {
        return jdbcRegistryServer.listJdbcRegistryDataChildren(key);
    }

    @Override
    public boolean existJdbcRegistryDataKey(String key) {
        return jdbcRegistryServer.existJdbcRegistryDataKey(key);
    }

    @Override
    public void acquireJdbcRegistryLock(String key) {
        jdbcRegistryServer.acquireJdbcRegistryLock(jdbcRegistryClientIdentify.getClientId(), key);
    }

    @Override
    public boolean acquireJdbcRegistryLock(String key, long timeout) {
        return jdbcRegistryServer.acquireJdbcRegistryLock(jdbcRegistryClientIdentify.getClientId(), key, timeout);
    }

    @Override
    public void releaseJdbcRegistryLock(String key) {
        jdbcRegistryServer.releaseJdbcRegistryLock(jdbcRegistryClientIdentify.getClientId(), key);
    }

    @Override
    public void close() {
        jdbcRegistryServer.deregisterClient(this);
        log.info("Closed JdbcRegistryClient: {}", jdbcRegistryClientIdentify);
    }

    @Override
    public boolean isConnectivity() {
        return jdbcRegistryServer.getServerState() == JdbcRegistryServerState.STARTED;
    }

}
