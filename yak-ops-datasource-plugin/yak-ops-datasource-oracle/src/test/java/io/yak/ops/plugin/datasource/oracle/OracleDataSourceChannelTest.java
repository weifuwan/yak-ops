package io.yak.ops.plugin.datasource.oracle;

import io.yak.ops.plugin.datasource.oracle.param.OracleConnectionParam;
import io.yak.ops.spi.enums.DbType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OracleDataSourceChannelTest {

    @Test
    public void testCreateDataSourceClient() {
        OracleDataSourceChannel sourceChannel = Mockito.mock(OracleDataSourceChannel.class);
        OraclePooledDataSourceClient dataSourceClient = Mockito.mock(OraclePooledDataSourceClient.class);
        Mockito.when(sourceChannel.createPooledDataSourceClient(Mockito.any(), Mockito.any()))
                .thenReturn(dataSourceClient);
        Assertions
                .assertNotNull(sourceChannel.createPooledDataSourceClient(new OracleConnectionParam(), DbType.ORACLE));
    }
}
