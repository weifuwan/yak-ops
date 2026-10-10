package io.yak.ops.common.bean.dto.task;

import io.yak.ops.common.enums.task.InstanceStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 当前Workspace的通用Task Instance分页请求，不包含插件凭证或执行策略。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Data
public class InstanceQueryDTO {

    /** 当前页码，从1开始。 */
    @Min(1)
    private Integer pageNo = 1;

    /** 单次查询最大100条，避免一次加载无限历史实例。 */
    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

    /** 可选的通用Task Definition ID。 */
    private String taskId;

    /** 可选的插件canonical type。 */
    private String taskType;

    /** 可选的根实例生命周期状态。 */
    private InstanceStatus status;
}
