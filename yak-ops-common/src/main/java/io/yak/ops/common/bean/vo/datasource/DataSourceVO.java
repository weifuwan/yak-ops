package io.yak.ops.common.bean.vo.datasource;

import java.time.LocalDateTime;
import lombok.Data;

/** 数据源展示对象。 */
@Data
public class DataSourceVO {

    private Long id;
    private String name;
    private String dbType;
    private String jdbcUrl;
    private String environment;
    private String environmentName;
    private String connStatus;
    private String remark;
    private String originalJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
