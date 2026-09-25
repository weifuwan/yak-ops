package io.yak.ops.common.bean.vo.datasource.plugin;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源插件向前端暴露的动态连接表单配置。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourcePluginConfigVO {

    /** 插件稳定类型标识，由 Provider 自己声明。 */
    private String pluginType;

    /** 新版分区表单配置；存在有效 sections 时前端优先按分区渲染。 */
    @Builder.Default
    private List<DataSourcePluginFormSectionVO> sections = new ArrayList<>();

    /** 旧版扁平动态表单字段，保留兼容能力。 */
    @Builder.Default
    private List<DataSourcePluginFormFieldVO> formFields = new ArrayList<>();
}
