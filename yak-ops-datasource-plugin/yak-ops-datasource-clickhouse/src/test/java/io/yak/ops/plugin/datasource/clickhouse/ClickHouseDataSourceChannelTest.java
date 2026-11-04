package io.yak.ops.plugin.datasource.clickhouse;

import io.yak.ops.plugin.datasource.clickhouse.param.ClickHouseConnectionParam;
import io.yak.ops.spi.enums.DbType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ClickHouseDataSourceChannelTest {

    @Test
    public void testCreateDataSourceClient() {
        ClickHouseDataSourceChannel sourceChannel = Mockito.mock(ClickHouseDataSourceChannel.class);
        ClickHousePooledDataSourceClient dataSourceClient = Mockito.mock(ClickHousePooledDataSourceClient.class);
        Mockito.when(sourceChannel.createPooledDataSourceClient(Mockito.any(), Mockito.any()))
                .thenReturn(dataSourceClient);
        Assertions.assertNotNull(
                sourceChannel.createPooledDataSourceClient(new ClickHouseConnectionParam(), DbType.CLICKHOUSE));
    }
}
