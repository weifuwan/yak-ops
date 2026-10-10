package io.yak.ops.common.enums.task;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通用任务定义的发布状态，与任务执行状态和同步类型相互独立。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Getter
@RequiredArgsConstructor
public enum DefinitionStatus {

    /** 定义已下线，不允许创建新的执行实例。 */
    UNPUBLISHED(0),

    /** 定义已上线，可按任务类型规则执行。 */
    PUBLISHED(1);

    @EnumValue
    private final Integer value;
}
