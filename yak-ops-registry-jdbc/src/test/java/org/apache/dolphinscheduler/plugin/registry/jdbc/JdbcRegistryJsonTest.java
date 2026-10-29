package org.apache.dolphinscheduler.plugin.registry.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.dolphinscheduler.plugin.registry.jdbc.model.DO.JdbcRegistryClientHeartbeat;
import org.apache.dolphinscheduler.plugin.registry.jdbc.model.DTO.JdbcRegistryClientHeartbeatDTO;
import org.junit.jupiter.api.Test;

class JdbcRegistryJsonTest {
    @Test
    void preservesUpstreamClientHeartbeatConfigAcrossDatabaseSerialization() {
        JdbcRegistryClientHeartbeatDTO.ClientConfig config =
                JdbcRegistryClientHeartbeatDTO.ClientConfig.builder().sessionTimeout(45000L).build();
        JdbcRegistryClientHeartbeatDTO source = JdbcRegistryClientHeartbeatDTO.builder()
                .id(21L)
                .clientName("registry-test")
                .lastHeartbeatTime(System.currentTimeMillis())
                .clientConfig(config)
                .build();
        JdbcRegistryClientHeartbeat row = JdbcRegistryClientHeartbeatDTO.toJdbcRegistryClientHeartbeat(source);
        assertNotNull(row.getConnectionConfig());
        JdbcRegistryClientHeartbeatDTO restored =
                JdbcRegistryClientHeartbeatDTO.fromJdbcRegistryClientHeartbeat(row);
        assertEquals(45000L, restored.getClientConfig().getSessionTimeout());
        assertEquals("registry-test", restored.getClientName());
        assertFalse(restored.isDead());
    }
}
