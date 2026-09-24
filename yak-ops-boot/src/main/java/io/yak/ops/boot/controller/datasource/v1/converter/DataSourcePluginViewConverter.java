package io.yak.ops.boot.controller.datasource.v1.converter;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.FormFieldVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.FormSectionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.JdbcUrlLinkageVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.OptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.RuleVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.VisibilityConditionVO;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor;
import org.springframework.stereotype.Component;

/**
 * 将稳定的 Datasource Plugin SPI 描述转换为前端动态表单结构。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Component
@ConditionalOnDataSourceEnabled
public class DataSourcePluginViewConverter {

    public DataSourcePluginConfigVO config(DataSourcePluginDescriptor source) {
        if (source == null) return null;
        return DataSourcePluginConfigVO.builder()
                .pluginType(source.dbType().name())
                .sections(source.connectionForm().sections().stream().map(this::section).toList())
                .formFields(source.connectionForm().legacyFields().stream().map(this::field).toList())
                .installRequired(source.installRequired())
                .installHint(source.installHint())
                .build();
    }

    private FormSectionVO section(DataSourcePluginDescriptor.FormSection source) {
        return FormSectionVO.builder()
                .key(source.key())
                .title(source.title())
                .description(source.description())
                .collapsible(source.collapsible())
                .defaultExpanded(source.defaultExpanded())
                .fields(source.fields().stream().map(this::field).toList())
                .build();
    }

    private FormFieldVO field(DataSourcePluginDescriptor.FormField source) {
        return FormFieldVO.builder()
                .key(source.key())
                .label(source.label())
                .type(source.type().name())
                .placeholder(source.placeholder())
                .defaultValue(source.defaultValue())
                .options(source.options().stream()
                        .map(value -> new OptionVO(value.label(), value.value()))
                        .toList())
                .rules(source.rules().stream()
                        .map(value -> new RuleVO(
                                value.required(), value.pattern(), value.min(), value.max(), value.message()))
                        .toList())
                .dependsOn(source.dependsOn())
                .visibleWhen(source.visibleWhen().stream()
                        .map(value -> new VisibilityConditionVO(
                                value.field(), value.operator().name(), value.value(), value.values()))
                        .toList())
                .urlLinkage(linkage(source.jdbcUrlLinkage()))
                .build();
    }

    private JdbcUrlLinkageVO linkage(DataSourcePluginDescriptor.JdbcUrlLinkage source) {
        if (source == null) return null;
        return JdbcUrlLinkageVO.builder()
                .template(source.template())
                .hostField(source.hostField())
                .portField(source.portField())
                .databaseField(source.databaseField())
                .preserveSuffix(source.preserveSuffix())
                .build();
    }
}
