package io.yak.ops.plugin.registry.jdbc.model.DO;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName(value = "t_yak_ops_jdbc_registry_data_change_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JdbcRegistryDataChangeEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String eventType;

    private String jdbcRegistryData;

    private Date createTime;

}
