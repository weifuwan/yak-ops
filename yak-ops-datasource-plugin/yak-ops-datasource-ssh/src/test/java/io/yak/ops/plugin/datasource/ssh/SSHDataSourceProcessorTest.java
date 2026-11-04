package io.yak.ops.plugin.datasource.ssh;

import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.plugin.datasource.ssh.param.SSHConnectionParam;
import io.yak.ops.plugin.datasource.ssh.param.SSHDataSourceParamDTO;
import io.yak.ops.plugin.datasource.ssh.param.SSHDataSourceProcessor;
import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.enums.DbType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SSHDataSourceProcessorTest {

    private SSHDataSourceProcessor sshDataSourceProcessor;

    private String connectJson =
            "{\"user\":\"lucky\",\"password\":\"123456\",\"host\":\"yak-ops.com\",\"port\":22, \"privateKey\":\"ssh-rsa AAAAB\"}";

    @BeforeEach
    public void init() {
        sshDataSourceProcessor = new SSHDataSourceProcessor();
    }

    @Test
    void testCheckDatasourceParam() {
        BaseDataSourceParamDTO baseDataSourceParamDTO = new SSHDataSourceParamDTO();
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> sshDataSourceProcessor.checkDatasourceParam(baseDataSourceParamDTO));
        baseDataSourceParamDTO.setHost("localhost");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> sshDataSourceProcessor.checkDatasourceParam(baseDataSourceParamDTO));
        baseDataSourceParamDTO.setUserName("root");
        Assertions.assertDoesNotThrow(() -> sshDataSourceProcessor.checkDatasourceParam(baseDataSourceParamDTO));

    }

    @Test
    void testGetDatasourceUniqueId() {
        SSHConnectionParam sshConnectionParam = new SSHConnectionParam();
        sshConnectionParam.setHost("localhost");
        sshConnectionParam.setUser("root");
        sshConnectionParam.setPassword("123456");
        Assertions.assertEquals("ssh@localhost@root@123456",
                sshDataSourceProcessor.getDatasourceUniqueId(sshConnectionParam, DbType.SSH));

    }

    @Test
    void testCreateDatasourceParamDTO() {
        SSHDataSourceParamDTO sshDataSourceParamDTO =
                (SSHDataSourceParamDTO) sshDataSourceProcessor.createDatasourceParamDTO(connectJson);
        Assertions.assertEquals("lucky", sshDataSourceParamDTO.getUserName());
        Assertions.assertEquals("123456", sshDataSourceParamDTO.getPassword());
        Assertions.assertEquals("yak-ops.com", sshDataSourceParamDTO.getHost());
        Assertions.assertEquals(22, sshDataSourceParamDTO.getPort());
        Assertions.assertEquals("ssh-rsa AAAAB", sshDataSourceParamDTO.getPrivateKey());
    }

    @Test
    void testCreateConnectionParams() {
        SSHDataSourceParamDTO sshDataSourceParamDTO =
                (SSHDataSourceParamDTO) sshDataSourceProcessor.createDatasourceParamDTO(connectJson);
        SSHConnectionParam sshConnectionParam = sshDataSourceProcessor.createConnectionParams(sshDataSourceParamDTO);
        Assertions.assertEquals("lucky", sshConnectionParam.getUser());
        Assertions.assertEquals("123456", sshConnectionParam.getPassword());
        Assertions.assertEquals("yak-ops.com", sshConnectionParam.getHost());
        Assertions.assertEquals(22, sshConnectionParam.getPort());
        Assertions.assertEquals("ssh-rsa AAAAB", sshConnectionParam.getPrivateKey());
    }

    @Test
    void testTestConnection() {
        SSHDataSourceParamDTO sshDataSourceParamDTO =
                (SSHDataSourceParamDTO) sshDataSourceProcessor.createDatasourceParamDTO(connectJson);
        ConnectionParam connectionParam = sshDataSourceProcessor.createConnectionParams(sshDataSourceParamDTO);
        Assertions.assertFalse(sshDataSourceProcessor.checkDataSourceConnectivity(connectionParam));

        try (
                MockedConstruction<SshClientWrapper> sshClientWrapperMockedConstruction =
                        Mockito.mockConstruction(SshClientWrapper.class, (mock, context) -> {
                            Mockito.when(mock.isAuth()).thenReturn(true);
                        })) {
            Assertions.assertTrue(sshDataSourceProcessor.checkDataSourceConnectivity(connectionParam));
        }
    }

}
