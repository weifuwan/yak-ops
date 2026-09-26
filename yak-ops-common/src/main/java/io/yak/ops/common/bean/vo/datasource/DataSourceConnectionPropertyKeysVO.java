package io.yak.ops.common.bean.vo.datasource;

import java.util.List;
import lombok.Data;

/**
 * 数据源高级连接参数候选项。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Data
public class DataSourceConnectionPropertyKeysVO {

    /** 当前 Provider 可推荐给高级参数编辑器的属性名。 */
    private List<String> acceptedPropertyKeys;
}
