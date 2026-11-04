package io.yak.ops.plugin.registry.jdbc.model.DTO;

import io.yak.ops.plugin.registry.jdbc.model.DO.JdbcRegistryLock;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JdbcRegistryLockDTO {

    private Long id;
    private String lockKey;
    private String lockOwner;
    private Long clientId;
    private Date createTime;

    public static JdbcRegistryLockDTO fromJdbcRegistryLock(JdbcRegistryLock jdbcRegistryLock) {
        return JdbcRegistryLockDTO.builder()
                .id(jdbcRegistryLock.getId())
                .lockKey(jdbcRegistryLock.getLockKey())
                .lockOwner(jdbcRegistryLock.getLockOwner())
                .clientId(jdbcRegistryLock.getClientId())
                .createTime(jdbcRegistryLock.getCreateTime())
                .build();
    }

    public static JdbcRegistryLock toJdbcRegistryLock(JdbcRegistryLockDTO jdbcRegistryLock) {
        return JdbcRegistryLock.builder()
                .id(jdbcRegistryLock.getId())
                .lockKey(jdbcRegistryLock.getLockKey())
                .lockOwner(jdbcRegistryLock.getLockOwner())
                .clientId(jdbcRegistryLock.getClientId())
                .createTime(jdbcRegistryLock.getCreateTime())
                .build();
    }

}
