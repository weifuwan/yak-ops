package io.yak.ops.common.bean.vo.datasource.plugin;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源插件动态表单的分区定义。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourcePluginFormSectionVO {

    /** 分区唯一标识。 */
    private String key;

    /** 分区标题。 */
    private String title;

    /** 分区说明。 */
    private String description;

    /** 是否允许折叠。 */
    @Builder.Default
    private Boolean collapsible = false;

    /** 是否默认展开。 */
    @Builder.Default
    private Boolean defaultExpanded = true;

    /** 分区内的动态表单字段。 */
    @Builder.Default
    private List<DataSourcePluginFormFieldVO> fields = new ArrayList<>();
}
