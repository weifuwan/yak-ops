package io.yak.ops.modeling.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Physical/modeling table categories retained from data_modal. */
@Getter
@AllArgsConstructor
public enum DimensionTableTypeEnum {
    DIM("DIM", "维度"),
    SOURCE("SOURCE", "贴源表"),
    DIMENSION("DIMENSION", "维度表"),
    DETAIL("DETAIL", "明细表"),
    SUMMARY("SUMMARY", "汇总表"),
    APPLICATION("APPLICATION", "应用表");

    @EnumValue
    private final String code;
    private final String name;
}