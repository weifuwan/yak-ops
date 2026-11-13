import { useState } from "react";
import { ChartNoAxesCombined } from "lucide-react";
import { Button, PageHeader } from "@yak-ops/yak-ui";

import { useWorkspace } from "@/hooks/use-workspace";

import {
  ModelingEntityPage,
  type ModelingEntityDefinition,
} from "../components/modeling-entity-page";

const CALCULATE_FUNCTIONS = [
  { value: "COUNT", label: "计算（COUNT）" },
  { value: "SUM", label: "累加（SUM）" },
  { value: "AVG", label: "平均值（AVG）" },
  { value: "MAX", label: "最大值（MAX）" },
  { value: "MIN", label: "最小值（MIN）" },
  { value: "RATE", label: "率" },
  { value: "RANK", label: "排名" },
  { value: "OTHER", label: "其他" },
];

const DEFINITIONS: ModelingEntityDefinition[] = [
  {
    key: "atomIndicator",
    title: "原子指标",
    description: "原子指标是最小、不可再拆分的业务度量，提交后生成可追溯版本。",
    searchField: "cnName",
    searchPlaceholder: "搜索原子指标",
    versioned: true,
    defaults: { removeDuplicates: "true", decimalNumber: "0" },
    fields: [
      { key: "enShortName", label: "英文缩写", required: true, readOnly: true, placeholder: "创建后作为指标唯一标识，建议保持稳定" },
      { key: "enName", label: "英文名称" },
      { key: "cnName", label: "中文名称", required: true },
      { key: "businessCaliber", label: "业务口径", required: true, multiline: true },
      { key: "busTypeId", label: "业务分类", optionsSource: "businessType", optionLabelField: "businessName" },
      { key: "domainId", label: "数据域", optionsSource: "dataDomain" },
      { key: "busProcessId", label: "业务过程", optionsSource: "businessProcess" },
      { key: "director", label: "负责人", required: true },
      { key: "calculateFunction", label: "计算函数", options: CALCULATE_FUNCTIONS },
      { key: "decimalNumber", label: "小数位数", placeholder: "例如 0、2、4" },
      { key: "unitId", label: "度量单位", optionsSource: "unitStandard" },
      { key: "removeDuplicates", label: "是否去重", options: [{ value: "true", label: "是" }, { value: "false", label: "否" }] },
      { key: "remark", label: "描述", multiline: true },
    ],
    columns: [
      { key: "cnName", title: "指标名称", width: 220 },
      { key: "enShortName", title: "英文缩写", width: 170 },
      { key: "calculateFunction", title: "计算函数", width: 130 },
      { key: "director", title: "负责人", width: 120 },
      { key: "submit", title: "状态", width: 90, render: (value) => value === true || value === "true" ? "已提交" : "草稿" },
    ],
  },
  {
    key: "derivativeIndicator",
    title: "派生指标",
    description: "基于原子指标、时间周期和业务修饰词组合形成派生指标。",
    searchField: "cnName",
    searchPlaceholder: "搜索派生指标",
    versioned: true,
    fields: [
      { key: "atomicId", label: "原子指标", required: true, optionsSource: "atomIndicator" },
      { key: "timePeriodId", label: "时间周期", required: true, optionsSource: "timePeriod" },
      { key: "modifierId", label: "修饰词", placeholder: "多个修饰词使用英文逗号分隔" },
      { key: "enShortName", label: "英文缩写", required: true },
      { key: "enName", label: "英文名称" },
      { key: "cnName", label: "中文名称", required: true },
      { key: "dwhId", label: "数仓分层", optionsSource: "dwLayer" },
      { key: "martId", label: "数据集市", optionsSource: "dataMart", optionLabelField: "martName" },
      { key: "subjectId", label: "主题域", optionsSource: "subjectDomain", optionLabelField: "domainName" },
      { key: "busTypeId", label: "业务分类", optionsSource: "businessType", optionLabelField: "businessName" },
      { key: "domainId", label: "数据域", optionsSource: "dataDomain" },
      { key: "busProcessId", label: "业务过程", optionsSource: "businessProcess" },
      { key: "director", label: "负责人" },
      { key: "remark", label: "描述", multiline: true },
    ],
    columns: [
      { key: "cnName", title: "指标名称", width: 220 },
      { key: "enShortName", title: "英文缩写", width: 170 },
      { key: "atomicId", title: "原子指标", width: 180 },
      { key: "timePeriodId", title: "时间周期", width: 150 },
      { key: "director", title: "负责人", width: 120 },
      { key: "submit", title: "状态", width: 90, render: (value) => value === true || value === "true" ? "已提交" : "草稿" },
    ],
  },
  {
    key: "composeIndicator",
    title: "复合指标",
    description: "基于派生指标和计算表达式构建复合指标，保留计算模式和对比类型。",
    searchField: "cnName",
    searchPlaceholder: "搜索复合指标",
    versioned: true,
    fields: [
      { key: "deriveId", label: "关联派生指标", optionsSource: "derivativeIndicator" },
      { key: "computeMode", label: "计算模式", options: [
        { value: "FORMULA", label: "计算公式" },
        { value: "COMPARE", label: "对比计算" },
        { value: "CUSTOM", label: "自定义" },
      ] },
      { key: "computeExpr", label: "计算表达式", multiline: true, placeholder: "例如：当前值 / 同期值 - 1" },
      { key: "compareType", label: "对比类型", options: [
        { value: "YOY", label: "同比" },
        { value: "MOM", label: "环比" },
        { value: "CUSTOM", label: "自定义" },
      ] },
      { key: "enShortName", label: "英文缩写", required: true },
      { key: "enName", label: "英文名称" },
      { key: "cnName", label: "中文名称", required: true },
      { key: "dwhId", label: "数仓分层", optionsSource: "dwLayer" },
      { key: "martId", label: "数据集市", optionsSource: "dataMart", optionLabelField: "martName" },
      { key: "subjectId", label: "主题域", optionsSource: "subjectDomain", optionLabelField: "domainName" },
      { key: "busTypeId", label: "业务分类", optionsSource: "businessType", optionLabelField: "businessName" },
      { key: "domainId", label: "数据域", optionsSource: "dataDomain" },
      { key: "busProcessId", label: "业务过程", optionsSource: "businessProcess" },
      { key: "director", label: "负责人" },
      { key: "remark", label: "描述", multiline: true },
    ],
    columns: [
      { key: "cnName", title: "指标名称", width: 220 },
      { key: "enShortName", title: "英文缩写", width: 170 },
      { key: "computeMode", title: "计算模式", width: 130 },
      { key: "deriveId", title: "关联派生指标", width: 190 },
      { key: "director", title: "负责人", width: 120 },
      { key: "submit", title: "状态", width: 90, render: (value) => value === true || value === "true" ? "已提交" : "草稿" },
    ],
  },
  {
    key: "modifier",
    title: "修饰词",
    description: "维护普通业务修饰词和维度枚举修饰词，支持引用标准代码及枚举值。",
    searchField: "cnName",
    searchPlaceholder: "搜索修饰词",
    versioned: true,
    fields: [
      { key: "modifierType", label: "修饰词类型", required: true, options: [
        { value: "SIMPLE", label: "普通业务修饰词" },
        { value: "DIMENSION", label: "维度枚举修饰词" },
      ] },
      { key: "enShortName", label: "英文缩写", required: true },
      { key: "enName", label: "英文名称" },
      { key: "cnName", label: "中文名称", required: true },
      { key: "businessCaliber", label: "业务口径", multiline: true },
      { key: "director", label: "负责人" },
      { key: "dwhId", label: "数仓分层", optionsSource: "dwLayer" },
      { key: "martId", label: "数据集市", optionsSource: "dataMart", optionLabelField: "martName" },
      { key: "subjectId", label: "主题域", optionsSource: "subjectDomain", optionLabelField: "domainName" },
      { key: "busTypeId", label: "业务分类", optionsSource: "businessType", optionLabelField: "businessName" },
      { key: "domainId", label: "数据域", optionsSource: "dataDomain" },
      { key: "dimensionTbId", label: "维度表 ID" },
      { key: "dimensionFdId", label: "维度字段 ID" },
      { key: "codeId", label: "标准代码", optionsSource: "codeStandard" },
      { key: "codeDetailId", label: "代码明细", optionsSource: "codeDetail" },
      { key: "remark", label: "描述", multiline: true },
    ],
    columns: [
      { key: "cnName", title: "修饰词名称", width: 220 },
      { key: "enShortName", title: "英文缩写", width: 170 },
      { key: "modifierType", title: "类型", width: 160, render: (value) => value === "DIMENSION" ? "维度枚举" : "普通业务" },
      { key: "director", title: "负责人", width: 120 },
      { key: "submit", title: "状态", width: 90, render: (value) => value === true || value === "true" ? "已提交" : "草稿" },
    ],
  },
  {
    key: "timePeriodCategory",
    title: "时间周期目录",
    description: "维护时间周期分类树，例如天、周、月、季度、年和历史全量。",
    searchField: "cnName",
    searchPlaceholder: "搜索周期目录",
    fields: [
      { key: "cnName", label: "中文名称", required: true },
      { key: "enName", label: "英文名称" },
      { key: "parentId", label: "上级目录", optionsSource: "timePeriodCategory", optionLabelField: "cnName", placeholder: "顶级目录留空" },
      { key: "leaf", label: "叶子节点", options: [{ value: "true", label: "是" }, { value: "false", label: "否" }] },
    ],
    columns: [
      { key: "cnName", title: "目录名称", width: 220 },
      { key: "enName", title: "英文名称", width: 180 },
      { key: "parentId", title: "上级目录", width: 200 },
      { key: "leaf", title: "叶子节点", width: 110, render: (value) => value === true || value === "true" ? "是" : "否" },
    ],
  },
  {
    key: "timePeriod",
    title: "时间周期",
    description: "维护时间周期定义，派生指标可直接引用周期，并支持提交、历史版本和回滚。",
    searchField: "cnName",
    searchPlaceholder: "搜索时间周期",
    versioned: true,
    fields: [
      { key: "categoryId", label: "周期粒度", required: true, optionsSource: "timePeriodCategory", optionLabelField: "cnName" },
      { key: "enShortName", label: "英文缩写", required: true, readOnly: true },
      { key: "enName", label: "英文名称" },
      { key: "cnName", label: "中文名称", required: true },
      { key: "director", label: "负责人", required: true },
      { key: "remark", label: "描述", multiline: true },
    ],
    columns: [
      { key: "cnName", title: "周期名称", width: 220 },
      { key: "enShortName", title: "英文缩写", width: 150 },
      { key: "enName", title: "英文名称", width: 200 },
      { key: "categoryId", title: "周期粒度", width: 180 },
      { key: "submit", title: "状态", width: 90, render: (value) => value === true || value === "true" ? "已提交" : "草稿" },
    ],
  },
];

export function DataModelingIndicatorsPage() {
  const { currentWorkspace } = useWorkspace();
  const [activeKey, setActiveKey] = useState(0);
  const activeDefinition = DEFINITIONS[activeKey] || DEFINITIONS[0];

  return (
    <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <PageHeader
        title="数据指标"
        bordered
        className="bg-white px-6 max-md:px-4"
      />
      <div className="flex min-h-0 flex-1 flex-col gap-3 px-6 pb-4 pt-5 max-md:px-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <ChartNoAxesCombined size={16} className="text-[var(--yak-color-primary)]" />
            <p className="text-xs text-[var(--yak-components-muted-text)]">
              从原子指标到派生指标、复合指标，统一沉淀业务口径和可复用计算逻辑。
            </p>
          </div>
          {currentWorkspace ? (
            <div className="rounded-md border border-[var(--yak-components-panel-border)] bg-white px-3 py-1.5 text-xs text-[var(--yak-components-muted-text)]">
              当前工作空间：{currentWorkspace.name}
            </div>
          ) : null}
        </div>
        <div className="flex shrink-0 flex-wrap gap-1 rounded-md border border-[var(--yak-components-panel-border)] bg-white p-1">
          {DEFINITIONS.map((item, index) => (
            <Button
              key={item.key}
              size="sm"
              variant={activeKey === index ? "primary" : "ghost"}
              onClick={() => setActiveKey(index)}
            >
              {item.title}
            </Button>
          ))}
        </div>
        {currentWorkspace ? (
          <ModelingEntityPage
            key={activeDefinition.key}
            projectCode={currentWorkspace.id}
            definition={activeDefinition}
          />
        ) : (
          <div className="flex min-h-[240px] flex-1 items-center justify-center rounded-md border border-dashed border-[var(--yak-components-panel-border)] bg-white text-sm text-[var(--yak-components-muted-text)]">
            请先选择工作空间
          </div>
        )}
      </div>
    </div>
  );
}

export default DataModelingIndicatorsPage;
