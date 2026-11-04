package io.yak.ops.plugin.datasource.api.client;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BasePooledDataSourceClientTest {

    @Mock
    private BasePooledDataSourceClient basePooledDataSourceClient;

    @Test
    public void testGetConnection() throws SQLException {
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(basePooledDataSourceClient.getConnection()).thenReturn(connection);
        Assertions.assertNotNull(basePooledDataSourceClient.getConnection());
    }
}
