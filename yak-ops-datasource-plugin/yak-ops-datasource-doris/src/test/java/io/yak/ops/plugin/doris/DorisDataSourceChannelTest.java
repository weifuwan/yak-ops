package io.yak.ops.plugin.doris;

import io.yak.ops.plugin.doris.param.DorisConnectionParam;
import io.yak.ops.spi.enums.DbType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DorisDataSourceChannelTest {

    @Test
    public void testCreateDataSourceClient() {
        DorisDataSourceChannel sourceChannel = Mockito.mock(DorisDataSourceChannel.class);
        DorisPooledDataSourceClient dataSourceClient = Mockito.mock(DorisPooledDataSourceClient.class);
        Mockito.when(sourceChannel.createPooledDataSourceClient(Mockito.any(), Mockito.any()))
                .thenReturn(dataSourceClient);
        Assertions.assertNotNull(sourceChannel.createPooledDataSourceClient(new DorisConnectionParam(), DbType.DORIS));
    }
}
