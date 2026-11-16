import type { IndicatorDefinition } from "../workbench/types";
import { DW_TYPE_OPTIONS } from "../workbench/config/indicator-options";

export const CALCULATE_FUNCTION_OPTIONS = [
  { value: "COUNT", label: "计算（COUNT）" },
  { value: "SUM", label: "累加（SUM）" },
  { value: "AVG", label: "平均值（AVG）" },
  { value: "MAX", label: "最大值（MAX）" },
  { value: "MIN", label: "最小值（MIN）" },
  { value: "RATE", label: "率" },
  { value: "RANK", label: "排名" },
  { value: "OTHER", label: "其他" },
];

export const ATOM_INDICATOR_DEFINITION: IndicatorDefinition = {
  key: "atomIndicator",
  title: "原子指标",
  description: "原子指标是最小、不可再拆分的业务度量，负责沉淀统一的业务口径与计算逻辑。",
  listFilters: [
    { key: "businessTypeId", paramKey: "busTypeId", label: "全部业务分类", optionsSource: "businessType", optionLabelField: "businessName" },
    { key: "domainId", paramKey: "domainId", label: "全部数据域", optionsSource: "dataDomain" },
  ],
  filterByDwType: true,
  columnFormats: { cnName: "indicatorName", submit: "status", dwType: "dwType" },
  versioned: true,
  fields: [
    { key: "enShortName", label: "英文缩写", section: "基本信息", required: true, readOnly: true, placeholder: "创建后作为指标唯一标识" },
    { key: "enName", label: "英文名称", section: "基本信息" },
    { key: "cnName", label: "中文名称", section: "基本信息", required: true },
    { key: "businessCaliber", label: "业务口径", section: "基本信息", required: true, multiline: true },
    { key: "director", label: "负责人", section: "基本信息", required: true },
    { key: "remark", label: "描述", section: "基本信息", multiline: true },
    { key: "dwType", label: "数仓分层", section: "业务归属", required: true, options: DW_TYPE_OPTIONS },
    { key: "busTypeId", label: "业务分类", section: "业务归属", optionsSource: "businessType", optionLabelField: "businessName" },
    { key: "domainId", label: "数据域", section: "业务归属", required: true },
    { key: "busProcessId", label: "业务过程", section: "业务归属", required: true },
    { key: "calculateFunction", label: "计算函数", section: "计算逻辑", options: CALCULATE_FUNCTION_OPTIONS },
    { key: "decimalNumber", label: "小数位数", section: "计算逻辑", placeholder: "例如 0、2、4" },
    { key: "unitId", label: "度量单位", section: "计算逻辑", optionsSource: "unitStandard" },
    { key: "removeDuplicates", label: "是否去重", section: "计算逻辑", options: [
      { value: "true", label: "是" }, { value: "false", label: "否" },
    ] },
  ],
  columns: [
    { key: "enShortName", title: "英文缩写", width: 145 },
    { key: "cnName", title: "指标名称", width: 220 },
    { key: "dwType", title: "数仓分层", width: 110 },
    { key: "calculateFunction", title: "计算函数", width: 130 },
    { key: "director", title: "负责人", width: 120 },
    { key: "submit", title: "状态", width: 90 },
  ],
};
