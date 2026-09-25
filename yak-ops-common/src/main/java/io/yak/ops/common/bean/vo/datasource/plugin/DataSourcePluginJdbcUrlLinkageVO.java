package io.yak.ops.common.bean.vo.datasource.plugin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JDBC URL 与 Host、Port、Database 字段的双向联动配置。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourcePluginJdbcUrlLinkageVO {

    /** JDBC URL 模板，例如 jdbc:mysql://{host}:{port}/{database}。 */
    private String template;

    /** Host 字段 key。 */
    @Builder.Default
    private String hostField = "host";

    /** Port 字段 key。 */
    @Builder.Default
    private String portField = "port";

    /** Database 字段 key。 */
    @Builder.Default
    private String databaseField = "database";

    /** 是否在结构化字段变化时保留 query 或 properties 尾部参数。 */
    @Builder.Default
    private Boolean preserveSuffix = true;
}
