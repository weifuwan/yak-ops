package io.yak.ops.common.bean.vo.datasource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据源下拉选项。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceOptionVO {

    private String label;
    private String value;
    private String dbType;
}
