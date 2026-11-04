package io.yak.ops.plugin.registry.jdbc.model.DTO;

import io.yak.ops.plugin.registry.jdbc.model.DO.JdbcRegistryData;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JdbcRegistryDataDTO {

    private Long id;
    private String dataKey;
    private String dataValue;
    private String dataType;
    private Long clientId;
    private Date createTime;
    private Date lastUpdateTime;

    public static JdbcRegistryDataDTO fromJdbcRegistryData(JdbcRegistryData jdbcRegistryData) {
        return JdbcRegistryDataDTO.builder()
                .id(jdbcRegistryData.getId())
                .dataKey(jdbcRegistryData.getDataKey())
                .dataValue(jdbcRegistryData.getDataValue())
                .dataType(jdbcRegistryData.getDataType())
                .clientId(jdbcRegistryData.getClientId())
                .createTime(jdbcRegistryData.getCreateTime())
                .lastUpdateTime(jdbcRegistryData.getLastUpdateTime())
                .build();
    }

    public static JdbcRegistryData toJdbcRegistryData(JdbcRegistryDataDTO jdbcRegistryData) {
        return JdbcRegistryData.builder()
                .id(jdbcRegistryData.getId())
                .dataKey(jdbcRegistryData.getDataKey())
                .dataValue(jdbcRegistryData.getDataValue())
                .dataType(jdbcRegistryData.getDataType())
                .clientId(jdbcRegistryData.getClientId())
                .createTime(jdbcRegistryData.getCreateTime())
                .lastUpdateTime(jdbcRegistryData.getLastUpdateTime())
                .build();
    }

}
