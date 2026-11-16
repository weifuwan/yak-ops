import type { IndicatorDefinition } from "../workbench/types";

export const COMPOSITE_INDICATOR_DEFINITION: IndicatorDefinition = {
    key: "composeIndicator",
    title: "复合指标",
    description: "基于派生指标和计算表达式构建复合指标，支持计算表达式及同环比模式。",
    versioned: true,
    fields: [
      { key: "deriveId", label: "关联派生指标", section: "指标定义", optionsSource: "derivativeIndicator" },
      { key: "computeMode", label: "计算模式", section: "指标定义", required: true, options: [
        { value: "0", label: "计算表达式" }, { value: "1", label: "同环比" },
      ] },
      { key: "computeExpr", label: "计算表达式", section: "指标定义", multiline: true, placeholder: "例如：当前值 / 同期值 - 1" },
      { key: "compareType", label: "对比类型", section: "指标定义", options: [
        { value: "DAY_ON_LAST_DAY", label: "日环比" }, { value: "WEEK_ON_LAST_WEEK", label: "周环比" },
        { value: "WEEK_ON_WEEK_LAST_YEAR", label: "周同比" }, { value: "MONTH_ON_LAST_MONTH", label: "月环比" },
        { value: "MONTH_ON_MONTH_LAST_YEAR", label: "月同比" }, { value: "QUARTER_ON_LAST_QUARTER", label: "季度环比" },
        { value: "YEAR_ON_LAST_YEAR", label: "年环比" }, { value: "YEAR_ON_YEAR", label: "年同比" },
      ] },
      { key: "enShortName", label: "英文缩写", section: "基本信息", required: true },
      { key: "enName", label: "英文名称", section: "基本信息" },
      { key: "cnName", label: "中文名称", section: "基本信息", required: true },
      { key: "director", label: "负责人", section: "基本信息" },
      { key: "remark", label: "描述", section: "基本信息", multiline: true },
      { key: "dwhId", label: "数仓分层", section: "业务归属", optionsSource: "dwLayer" },
      { key: "martId", label: "数据集市", section: "业务归属", optionsSource: "dataMart", optionLabelField: "martName" },
      { key: "subjectId", label: "主题域", section: "业务归属", optionsSource: "subjectDomain", optionLabelField: "domainName" },
      { key: "busTypeId", label: "业务分类", section: "业务归属", optionsSource: "businessType", optionLabelField: "businessName" },
      { key: "domainId", label: "数据域", section: "业务归属", optionsSource: "dataDomain" },
      { key: "busProcessId", label: "业务过程", section: "业务归属", optionsSource: "businessProcess" },
    ],
    columns: [
      { key: "enShortName", title: "英文缩写", width: 145 },
      { key: "cnName", title: "指标名称", width: 220 },
      { key: "computeMode", title: "计算模式", width: 130 },
      { key: "deriveId", title: "关联派生指标", width: 190 },
      { key: "director", title: "负责人", width: 120 },
      { key: "submit", title: "状态", width: 90 },
    ],
  };
