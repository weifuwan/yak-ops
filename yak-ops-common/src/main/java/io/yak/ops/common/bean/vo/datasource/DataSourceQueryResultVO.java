package io.yak.ops.common.bean.vo.datasource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据源表或 SQL 的预览结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceQueryResultVO {

    private List<DataSourcePreviewColumnVO> columns = new ArrayList<>();
    private List<Map<String, Object>> data = new ArrayList<>();
    private Long total;
}
