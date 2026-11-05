package io.yak.ops.plugin.registry.jdbc.model.DO;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName(value = "t_yak_ops_jdbc_registry_data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JdbcRegistryData {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String dataKey;
    private String dataValue;
    private String dataType;
    private long clientId;
    private Date createTime;
    private Date lastUpdateTime;

}
