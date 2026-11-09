package io.yak.ops.plugin.registry.jdbc.server;

import io.yak.ops.plugin.registry.jdbc.JdbcRegistryProperties;
import io.yak.ops.plugin.registry.jdbc.client.IJdbcRegistryClient;
import io.yak.ops.plugin.registry.jdbc.client.JdbcRegistryClientIdentify;
import io.yak.ops.plugin.registry.jdbc.model.DTO.JdbcRegistryClientHeartbeatDTO;
import io.yak.ops.plugin.registry.jdbc.repository.JdbcRegistryClientRepository;
import io.yak.ops.plugin.registry.jdbc.repository.JdbcRegistryDataChangeEventRepository;
import io.yak.ops.plugin.registry.jdbc.repository.JdbcRegistryDataRepository;
import io.yak.ops.plugin.registry.jdbc.repository.JdbcRegistryLockRepository;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class JdbcRegistryServerTest {

    private static final JdbcRegistryClientIdentify CLIENT_IDENTIFY =
            new JdbcRegistryClientIdentify(1L, "test-client");

    @Mock
    private JdbcRegistryDataRepository jdbcRegistryDataRepository;

    @Mock
    private JdbcRegistryLockRepository jdbcRegistryLockRepository;

    @Mock
    private JdbcRegistryClientRepository jdbcRegistryClientRepository;

    @Mock
    private JdbcRegistryDataChangeEventRepository jdbcRegistryDataChangeEventRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private IJdbcRegistryClient jdbcRegistryClient;

    @Mock
    private ConnectionStateListener connectionStateListener;

    private JdbcRegistryServer jdbcRegistryServer;

    @BeforeEach
    void setUp() {
        JdbcRegistryProperties jdbcRegistryProperties = new JdbcRegistryProperties();
        jdbcRegistryProperties.setSessionTimeout(Duration.ofSeconds(1));
        jdbcRegistryServer = new JdbcRegistryServer(
                jdbcRegistryDataRepository,
                jdbcRegistryLockRepository,
                jdbcRegistryClientRepository,
                jdbcRegistryDataChangeEventRepository,
                jdbcRegistryProperties,
                transactionTemplate);
        Mockito.when(jdbcRegistryClient.getJdbcRegistryClientIdentify()).thenReturn(CLIENT_IDENTIFY);
        jdbcRegistryServer.registerClient(jdbcRegistryClient);
        jdbcRegistryServer.subscribeConnectionStateChange(connectionStateListener);
    }

    @AfterEach
    void tearDown() {
        jdbcRegistryServer.close();
    }

    @Test
    void refreshClientsHeartbeatShouldPersistCurrentTimestamp() {
        ArgumentCaptor<JdbcRegistryClientHeartbeatDTO> registeredHeartbeat =
                ArgumentCaptor.forClass(JdbcRegistryClientHeartbeatDTO.class);
        Mockito.verify(jdbcRegistryClientRepository).insert(registeredHeartbeat.capture());
        registeredHeartbeat.getValue().setLastHeartbeatTime(0L);
        AtomicLong persistedHeartbeatTimestamp = new AtomicLong(-1L);
        Mockito.when(jdbcRegistryClientRepository.updateById(Mockito.any())).thenAnswer(invocation -> {
            JdbcRegistryClientHeartbeatDTO heartbeat = invocation.getArgument(0);
            persistedHeartbeatTimestamp.set(heartbeat.getLastHeartbeatTime());
            return true;
        });

        ReflectionTestUtils.invokeMethod(jdbcRegistryServer, "refreshClientsHeartbeat");

        Assertions.assertTrue(persistedHeartbeatTimestamp.get() > 0L);
    }

    @Test
    void refreshClientsHeartbeatShouldDisconnectWhenHeartbeatRecordWasPurgedAfterTimeout() {
        setServerState(JdbcRegistryServerState.STARTED);
        ReflectionTestUtils.setField(jdbcRegistryServer, "lastSuccessHeartbeat", 0L);
        Mockito.when(jdbcRegistryClientRepository.updateById(Mockito.any())).thenReturn(false);

        ReflectionTestUtils.invokeMethod(jdbcRegistryServer, "refreshClientsHeartbeat");

        Assertions.assertEquals(JdbcRegistryServerState.DISCONNECTED, jdbcRegistryServer.getServerState());
        Mockito.verify(connectionStateListener).onDisConnected();
    }

    @Test
    void refreshClientsHeartbeatShouldReconnectAfterTransientFailure() {
        setServerState(JdbcRegistryServerState.STARTED);
        ReflectionTestUtils.setField(jdbcRegistryServer, "lastSuccessHeartbeat", System.currentTimeMillis());
        Mockito.when(jdbcRegistryClientRepository.updateById(Mockito.any()))
                .thenThrow(new IllegalStateException("Database unavailable"))
                .thenReturn(true);

        ReflectionTestUtils.invokeMethod(jdbcRegistryServer, "refreshClientsHeartbeat");

        Assertions.assertEquals(JdbcRegistryServerState.SUSPENDED, jdbcRegistryServer.getServerState());
        Mockito.verifyNoInteractions(connectionStateListener);

        ReflectionTestUtils.invokeMethod(jdbcRegistryServer, "refreshClientsHeartbeat");

        Assertions.assertEquals(JdbcRegistryServerState.STARTED, jdbcRegistryServer.getServerState());
        Mockito.verify(connectionStateListener).onReconnected();
        Mockito.verify(connectionStateListener, Mockito.never()).onDisConnected();
    }

    @Test
    void purgeInvalidJdbcRegistryMetadataShouldKeepHeartbeatUpdatedAfterSnapshot() {
        JdbcRegistryClientHeartbeatDTO staleHeartbeat = JdbcRegistryClientHeartbeatDTO.builder()
                .id(CLIENT_IDENTIFY.getClientId())
                .clientName(CLIENT_IDENTIFY.getClientName())
                .lastHeartbeatTime(System.currentTimeMillis() - Duration.ofSeconds(2).toMillis())
                .clientConfig(new JdbcRegistryClientHeartbeatDTO.ClientConfig(Duration.ofSeconds(1).toMillis()))
                .build();
        Mockito.when(jdbcRegistryClientRepository.queryAll()).thenReturn(Collections.singletonList(staleHeartbeat));
        Mockito.when(jdbcRegistryClientRepository.deleteByIdAndLastHeartbeatTime(
                staleHeartbeat.getId(), staleHeartbeat.getLastHeartbeatTime())).thenReturn(false);
        Mockito.when(jdbcRegistryDataRepository.selectAll()).thenReturn(Collections.emptyList());
        Mockito.when(jdbcRegistryLockRepository.queryAll()).thenReturn(Collections.emptyList());

        ReflectionTestUtils.invokeMethod(jdbcRegistryServer, "purgeInvalidJdbcRegistryMetadata");

        Mockito.verify(jdbcRegistryClientRepository).deleteByIdAndLastHeartbeatTime(
                staleHeartbeat.getId(), staleHeartbeat.getLastHeartbeatTime());
        Mockito.verify(jdbcRegistryDataRepository, Mockito.never()).deleteByKey(Mockito.anyString());
        Mockito.verify(jdbcRegistryLockRepository, Mockito.never()).deleteById(Mockito.anyLong());
    }

    @SuppressWarnings("unchecked")
    private void setServerState(JdbcRegistryServerState state) {
        AtomicReference<JdbcRegistryServerState> serverState =
                (AtomicReference<JdbcRegistryServerState>) ReflectionTestUtils.getField(
                        jdbcRegistryServer, "serverState");
        Assertions.assertNotNull(serverState);
        serverState.set(state);
    }
}
