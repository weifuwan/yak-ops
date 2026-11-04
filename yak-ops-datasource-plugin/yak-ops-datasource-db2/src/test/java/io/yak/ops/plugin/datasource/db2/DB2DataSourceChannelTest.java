package io.yak.ops.plugin.datasource.db2;

import io.yak.ops.plugin.datasource.db2.param.Db2ConnectionParam;
import io.yak.ops.spi.enums.DbType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DB2DataSourceChannelTest {

    @Test
    public void testCreateDataSourceClient() {
        DB2DataSourceChannel sourceChannel = Mockito.mock(DB2DataSourceChannel.class);
        DB2PooledDataSourceClient dataSourceClient = Mockito.mock(DB2PooledDataSourceClient.class);
        Mockito.when(sourceChannel.createPooledDataSourceClient(Mockito.any(), Mockito.any()))
                .thenReturn(dataSourceClient);
        Assertions.assertNotNull(sourceChannel.createPooledDataSourceClient(new Db2ConnectionParam(), DbType.DB2));
    }
}
