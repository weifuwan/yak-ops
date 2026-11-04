package io.yak.ops.plugin.datasource.dameng;

import io.yak.ops.plugin.datasource.dameng.param.DamengConnectionParam;
import io.yak.ops.spi.enums.DbType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DamengDataSourceChannelTest {

    @Test
    public void testCreateDataSourceClient() {
        DamengDataSourceChannel sourceChannel = Mockito.mock(DamengDataSourceChannel.class);
        DamengPooledDataSourceClient dataSourceClient = Mockito.mock(DamengPooledDataSourceClient.class);
        Mockito.when(sourceChannel.createPooledDataSourceClient(Mockito.any(), Mockito.any()))
                .thenReturn(dataSourceClient);
        Assertions
                .assertNotNull(sourceChannel.createPooledDataSourceClient(new DamengConnectionParam(), DbType.DAMENG));
    }

}
