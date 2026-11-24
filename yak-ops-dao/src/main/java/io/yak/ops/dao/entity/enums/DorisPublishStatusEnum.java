package io.yak.ops.dao.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DorisPublishStatusEnum {
    PENDING("PENDING", "等待执行"),
    RUNNING("RUNNING", "执行中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "执行失败"),
    CONFLICT("CONFLICT", "目标表已存在");

    @EnumValue
    private final String code;
    private final String label;
}
