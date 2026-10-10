package io.yak.ops.common.bean.dto.datasync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * OFFLINE Task 内一条 Source Table 到 Target Table 的稳定映射定义。
 *
 * <p>来源/目标数据源由 Task 共享；更新时传入已有 Route ID 保留其身份，新 Route 不传 ID。</p>
 *
 * @author weifuwan
 * @since 2026-10-08
 */
@Data
public class DataSyncTableRouteDTO {

    /** 更新时的已有 Route ID；创建新 Route 时为空。 */
    private String id;

    /** 来源数据库；数据源绑定数据库时以后端绑定值为准。 */
    @Size(max = 128, message = "来源数据库名称不能超过 128 个字符")
    private String sourceDatabase;

    /** 来源 Schema。 */
    @Size(max = 128, message = "来源 Schema 不能超过 128 个字符")
    private String sourceSchema;

    /** 来源表。 */
    @NotBlank(message = "来源表不能为空")
    @Size(max = 128, message = "来源表名称不能超过 128 个字符")
    private String sourceTable;

    /** 目标数据库；数据源绑定数据库时以后端绑定值为准。 */
    @Size(max = 128, message = "目标数据库名称不能超过 128 个字符")
    private String targetDatabase;

    /** 目标 Schema。 */
    @Size(max = 128, message = "目标 Schema 不能超过 128 个字符")
    private String targetSchema;

    /** 目标表。 */
    @NotBlank(message = "目标表不能为空")
    @Size(max = 128, message = "目标表名称不能超过 128 个字符")
    private String targetTable;
}
