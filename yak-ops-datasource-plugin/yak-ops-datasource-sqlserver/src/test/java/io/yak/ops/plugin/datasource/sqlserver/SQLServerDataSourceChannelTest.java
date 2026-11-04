package io.yak.ops.plugin.datasource.sqlserver;

import io.yak.ops.plugin.datasource.sqlserver.param.SQLServerConnectionParam;
import io.yak.ops.spi.enums.DbType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SQLServerDataSourceChannelTest {

    @Test
    public void testCreateDataSourceClient() {
        SQLServerDataSourceChannel sourceChannel = Mockito.mock(SQLServerDataSourceChannel.class);
        SQLServerPooledDataSourceClient dataSourceClient = Mockito.mock(SQLServerPooledDataSourceClient.class);
        Mockito.when(sourceChannel.createPooledDataSourceClient(Mockito.any(), Mockito.any()))
                .thenReturn(dataSourceClient);
        Assertions
                .assertNotNull(
                        sourceChannel.createPooledDataSourceClient(new SQLServerConnectionParam(), DbType.SQLSERVER));
    }
}
