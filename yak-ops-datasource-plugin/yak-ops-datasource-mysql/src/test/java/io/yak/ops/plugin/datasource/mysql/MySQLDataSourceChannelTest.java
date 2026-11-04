package io.yak.ops.plugin.datasource.mysql;

import io.yak.ops.plugin.datasource.mysql.param.MySQLConnectionParam;
import io.yak.ops.spi.enums.DbType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MySQLDataSourceChannelTest {

    @Test
    public void testCreateDataSourceClient() {
        MySQLDataSourceChannel sourceChannel = Mockito.mock(MySQLDataSourceChannel.class);
        MySQLPooledDataSourceClient dataSourceClient = Mockito.mock(MySQLPooledDataSourceClient.class);
        Mockito.when(sourceChannel.createPooledDataSourceClient(Mockito.any(), Mockito.any()))
                .thenReturn(dataSourceClient);
        Assertions.assertNotNull(sourceChannel.createPooledDataSourceClient(new MySQLConnectionParam(), DbType.MYSQL));
    }
}
