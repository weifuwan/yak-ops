package io.yak.ops.modeling.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Storage and refresh policy for a modeled table. */
@Getter
@AllArgsConstructor
public enum DimensionStoragePolicyEnum {
    REAL_TIME_INCREMENTAL("REAL_TIME_INCREMENTAL", "实时增量"),
    REAL_TIME_FULL("REAL_TIME_FULL", "实时全量"),
    QUARTER_HOURLY_INCREMENTAL("QUARTER_HOURLY_INCREMENTAL", "15分钟增量"),
    QUARTER_HOURLY_FULL("QUARTER_HOURLY_FULL", "15分钟全量"),
    HOURLY_INCREMENTAL("HOURLY_INCREMENTAL", "小时增量"),
    HOURLY_FULL_CUMULATIVE("HOURLY_FULL_CUMULATIVE", "小时全量累计"),
    DAILY_INCREMENTAL("DAILY_INCREMENTAL", "每日增量"),
    DAILY_FULL("DAILY_FULL", "每日全量"),
    WEEKLY_INCREMENTAL("WEEKLY_INCREMENTAL", "每周增量"),
    WEEKLY_FULL("WEEKLY_FULL", "每周全量"),
    MONTHLY_INCREMENTAL("MONTHLY_INCREMENTAL", "每月增量"),
    MONTHLY_FULL("MONTHLY_FULL", "每月全量"),
    QUARTERLY_INCREMENTAL("QUARTERLY_INCREMENTAL", "每季度增量"),
    QUARTERLY_FULL("QUARTERLY_FULL", "每季度全量"),
    YEARLY_INCREMENTAL("YEARLY_INCREMENTAL", "每年增量"),
    YEARLY_FULL("YEARLY_FULL", "每年全量"),
    HOURLY_MONTHLY_INCREMENTAL("HOURLY_MONTHLY_INCREMENTAL", "小时月增量"),
    HOURLY_DAILY_CUMULATIVE("HOURLY_DAILY_CUMULATIVE", "小时当天累计");

    @EnumValue
    private final String code;
    private final String name;
}