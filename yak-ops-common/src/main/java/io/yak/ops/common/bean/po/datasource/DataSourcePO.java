package io.yak.ops.common.bean.po.datasource;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/** 数据源持久化对象。 */
@Data
@TableName("yak_ops_data_source")
public class DataSourcePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private DataSourceDbType dbType;
    private String jdbcUrl;
    private DataSourceEnvironment environment;
    private DataSourceConnStatus connStatus;
    private String remark;

    @ToString.Exclude
    private String connectionParams;

    @ToString.Exclude
    private String originalJson;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
