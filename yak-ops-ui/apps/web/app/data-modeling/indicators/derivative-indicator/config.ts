import type { IndicatorDefinition } from "../workbench/types";

export const DERIVATIVE_INDICATOR_DEFINITION: IndicatorDefinition = {
    key: "derivativeIndicator",
    title: "派生指标",
    description: "基于原子指标、时间周期和业务修饰词组合形成派生指标。",
    listFilters: [
      { key: "businessTypeId", paramKey: "busTypeId", label: "全部业务分类", optionsSource: "businessType", optionLabelField: "businessName" },
      { key: "domainId", paramKey: "domainId", label: "全部数据域", optionsSource: "dataDomain" },
    ],
    columnFormats: { cnName: "indicatorName", submit: "status" },
    versioned: true,
    fields: [
      { key: "atomicId", label: "原子指标", section: "指标定义", required: true, optionsSource: "atomIndicator" },
      { key: "timePeriodId", label: "时间周期", section: "指标定义", required: true, optionsSource: "timePeriod" },
      { key: "modifierId", label: "修饰词", section: "指标定义", placeholder: "多个修饰词可用英文逗号分隔" },
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
      { key: "atomicId", title: "原子指标", width: 180 },
      { key: "timePeriodId", title: "时间周期", width: 150 },
      { key: "director", title: "负责人", width: 120 },
      { key: "submit", title: "状态", width: 90 },
    ],
  };
