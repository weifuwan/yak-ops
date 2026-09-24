package io.yak.ops.common.bean.vo.datasource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据预览前端列定义。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourcePreviewColumnVO {

    private String title;
    private String dataIndex;
    private String key;
    private Boolean ellipsis;
}
