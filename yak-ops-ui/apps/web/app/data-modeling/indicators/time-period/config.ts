import type { IndicatorDefinition } from "../workbench/types";

export const TIME_PERIOD_DEFINITION: IndicatorDefinition = {
    key: "timePeriod",
    title: "时间周期",
    description: "维护时间周期定义，按周期目录组织，并支持提交、历史版本和回滚。",
    versioned: true,
    fields: [
      { key: "categoryId", label: "周期粒度", section: "基本信息", required: true, optionsSource: "timePeriodCategory" },
      { key: "enShortName", label: "英文缩写", section: "基本信息", required: true, readOnly: true },
      { key: "enName", label: "英文名称", section: "基本信息" },
      { key: "cnName", label: "中文名称", section: "基本信息", required: true },
      { key: "director", label: "负责人", section: "基本信息", required: true },
      { key: "remark", label: "描述", section: "基本信息", multiline: true },
    ],
    columns: [
      { key: "enShortName", title: "英文缩写", width: 145 },
      { key: "cnName", title: "周期名称", width: 220 },
      { key: "enName", title: "英文名称", width: 200 },
      { key: "categoryId", title: "周期粒度", width: 160 },
      { key: "submit", title: "状态", width: 90 },
    ],
  };
