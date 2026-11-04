package io.yak.ops.plugin.datasource.postgresql;

import io.yak.ops.plugin.datasource.postgresql.param.PostgreSQLConnectionParam;
import io.yak.ops.spi.enums.DbType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PostgreSQLDataSourceChannelTest {

    @Test
    public void testCreateDataSourceClient() {
        PostgreSQLDataSourceChannel sourceChannel = Mockito.mock(PostgreSQLDataSourceChannel.class);
        PostgrePooledSQLDataSourceClient dataSourceClient = Mockito.mock(PostgrePooledSQLDataSourceClient.class);
        Mockito.when(sourceChannel.createPooledDataSourceClient(Mockito.any(), Mockito.any()))
                .thenReturn(dataSourceClient);
        Assertions.assertNotNull(
                sourceChannel.createPooledDataSourceClient(new PostgreSQLConnectionParam(), DbType.POSTGRESQL));
    }
}
