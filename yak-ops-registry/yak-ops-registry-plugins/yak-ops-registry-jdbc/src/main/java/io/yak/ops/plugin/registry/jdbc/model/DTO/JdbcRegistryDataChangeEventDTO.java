package io.yak.ops.plugin.registry.jdbc.model.DTO;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.registry.jdbc.model.DO.JdbcRegistryData;
import io.yak.ops.plugin.registry.jdbc.model.DO.JdbcRegistryDataChangeEvent;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JdbcRegistryDataChangeEventDTO {

    private Long id;

    private EventType eventType;

    private JdbcRegistryDataDTO jdbcRegistryData;

    private Date createTime;

    public enum EventType {
        ADD,
        UPDATE,
        DELETE;

    }

    public static JdbcRegistryDataChangeEventDTO fromJdbcRegistryDataChangeEvent(JdbcRegistryDataChangeEvent jdbcRegistryDataChangeEvent) {
        JdbcRegistryData jdbcRegistryData =
                JSONUtils.parseObject(jdbcRegistryDataChangeEvent.getJdbcRegistryData(), JdbcRegistryData.class);
        if (jdbcRegistryData == null) {
            throw new IllegalArgumentException(
                    "jdbcRegistryData: " + jdbcRegistryDataChangeEvent.getJdbcRegistryData() + " is invalidated");
        }
        return JdbcRegistryDataChangeEventDTO.builder()
                .id(jdbcRegistryDataChangeEvent.getId())
                .jdbcRegistryData(JdbcRegistryDataDTO.fromJdbcRegistryData(jdbcRegistryData))
                .eventType(EventType.valueOf(jdbcRegistryDataChangeEvent.getEventType()))
                .createTime(jdbcRegistryDataChangeEvent.getCreateTime())
                .build();
    }

    public static JdbcRegistryDataChangeEvent toJdbcRegistryDataChangeEvent(JdbcRegistryDataChangeEventDTO jdbcRegistryDataChangeEvent) {
        return JdbcRegistryDataChangeEvent.builder()
                .id(jdbcRegistryDataChangeEvent.getId())
                .jdbcRegistryData(JSONUtils.toJsonString(jdbcRegistryDataChangeEvent.getJdbcRegistryData()))
                .eventType(jdbcRegistryDataChangeEvent.getEventType().name())
                .createTime(jdbcRegistryDataChangeEvent.getCreateTime())
                .build();
    }

}
