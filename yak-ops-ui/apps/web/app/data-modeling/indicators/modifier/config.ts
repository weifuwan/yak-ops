import type { IndicatorDefinition } from "../workbench/types";

export const MODIFIER_DEFINITION: IndicatorDefinition = {
    key: "modifier",
    title: "修饰词",
    description: "维护普通业务修饰词和维度枚举修饰词，支持关联数仓分层、业务分类、数据域及维度标准。",
    versioned: true,
    fields: [
      { key: "modifierType", label: "修饰词类型", section: "基本信息", required: true, options: [
        { value: "SIMPLE", label: "普通业务修饰词" }, { value: "DIMENSION", label: "维度枚举修饰词" },
      ] },
      { key: "enShortName", label: "英文缩写", section: "基本信息", required: true, readOnly: true },
      { key: "enName", label: "英文名称", section: "基本信息" },
      { key: "cnName", label: "中文名称", section: "基本信息", required: true },
      { key: "businessCaliber", label: "业务口径", section: "基本信息", required: true, multiline: true },
      { key: "director", label: "负责人", section: "基本信息", required: true },
      { key: "remark", label: "描述", section: "基本信息", multiline: true },
      { key: "dwhId", label: "数仓分层", section: "业务归属", optionsSource: "dwLayer", excludeOptionValues: ["SOURCE_LAYER"], optionLabelPrefixField: "dwTypeName" },
      { key: "busTypeId", label: "业务分类", section: "业务归属", optionsSource: "businessType", optionLabelField: "businessName" },
      { key: "domainId", label: "数据域", section: "业务归属", optionsSource: "dataDomain" },
      { key: "dimensionTbId", label: "关联维度表", section: "维度枚举" },
      { key: "dimensionFdId", label: "关联维度字段", section: "维度枚举" },
      { key: "codeId", label: "关联标准代码", section: "维度枚举", optionsSource: "codeStandard" },
      { key: "codeDetailId", label: "关联代码明细", section: "维度枚举" },
    ],
    columns: [
      { key: "enShortName", title: "英文缩写", width: 145 },
      { key: "cnName", title: "修饰词名称", width: 220 },
      { key: "modifierType", title: "类型", width: 160 },
      { key: "director", title: "负责人", width: 120 },
      { key: "submit", title: "状态", width: 90 },
    ],
  };
