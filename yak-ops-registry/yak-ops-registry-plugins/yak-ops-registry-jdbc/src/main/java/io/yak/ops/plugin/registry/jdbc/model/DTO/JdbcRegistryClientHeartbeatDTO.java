package io.yak.ops.plugin.registry.jdbc.model.DTO;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.registry.jdbc.model.DO.JdbcRegistryClientHeartbeat;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JdbcRegistryClientHeartbeatDTO {

    private Long id;

    private String clientName;

    private Long lastHeartbeatTime;

    private ClientConfig clientConfig;

    private Date createTime;

    public static JdbcRegistryClientHeartbeatDTO fromJdbcRegistryClientHeartbeat(JdbcRegistryClientHeartbeat jdbcRegistryClientHeartbeat) {
        return JdbcRegistryClientHeartbeatDTO.builder()
                .id(jdbcRegistryClientHeartbeat.getId())
                .clientName(jdbcRegistryClientHeartbeat.getClientName())
                .lastHeartbeatTime(jdbcRegistryClientHeartbeat.getLastHeartbeatTime())
                .clientConfig(
                        JSONUtils.parseObject(jdbcRegistryClientHeartbeat.getConnectionConfig(), ClientConfig.class))
                .createTime(jdbcRegistryClientHeartbeat.getCreateTime())
                .build();
    }

    public static JdbcRegistryClientHeartbeat toJdbcRegistryClientHeartbeat(JdbcRegistryClientHeartbeatDTO jdbcRegistryClientHeartbeatDTO) {
        return JdbcRegistryClientHeartbeat.builder()
                .id(jdbcRegistryClientHeartbeatDTO.getId())
                .clientName(jdbcRegistryClientHeartbeatDTO.getClientName())
                .lastHeartbeatTime(jdbcRegistryClientHeartbeatDTO.getLastHeartbeatTime())
                .connectionConfig(JSONUtils.toJsonString(jdbcRegistryClientHeartbeatDTO.getClientConfig()))
                .createTime(jdbcRegistryClientHeartbeatDTO.getCreateTime())
                .build();
    }

    public boolean isDead() {
        // check if the client connection is expired.
        return System.currentTimeMillis() - lastHeartbeatTime > clientConfig.getSessionTimeout();
    }

    @SneakyThrows
    @Override
    public JdbcRegistryClientHeartbeatDTO clone() {
        return JdbcRegistryClientHeartbeatDTO.builder()
                .id(id)
                .clientName(clientName)
                .lastHeartbeatTime(lastHeartbeatTime)
                .clientConfig(clientConfig)
                .createTime(createTime)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientConfig {

        @Builder.Default
        private long sessionTimeout = TimeUnit.SECONDS.toMillis(60);

    }

}
