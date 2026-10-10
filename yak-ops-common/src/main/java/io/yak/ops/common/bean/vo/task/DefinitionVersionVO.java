package io.yak.ops.common.bean.vo.task;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 通用任务定义已持久化版本的只读响应。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Data
public class DefinitionVersionVO {

    /** 版本所属的稳定任务定义ID。 */
    private String definitionId;

    /** 插件的 canonical task type。 */
    private String taskType;

    /** 版本产生时的任务名称。 */
    private String name;

    /** 实际留存的可执行配置版本号。 */
    private Integer version;

    /** 已脱敏的插件配置快照。 */
    private String parametersSnapshot;

    /** 版本首次被记录的时间。 */
    private LocalDateTime createTime;
}
