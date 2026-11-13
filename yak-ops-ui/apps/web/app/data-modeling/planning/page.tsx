import { useState } from "react";
import { PageHeader, Button } from "@yak-ops/yak-ui";

import { useWorkspace } from "@/hooks/use-workspace";

import { ModelingEntityPage, type ModelingEntityDefinition } from "../components/modeling-entity-page";

const DEFINITIONS: ModelingEntityDefinition[] = [
  {
    key: "businessType", title: "业务分类",
    description: "按业务视角组织数仓建设范围，支持层级分类及业务负责人维护。",
    searchField: "businessName", searchPlaceholder: "搜索业务名称",
    fields: [
      { key: "businessName", label: "业务名称", required: true },
      { key: "enShortName", label: "英文缩写" },
      { key: "reviewer", label: "模型评审人", placeholder: "多个评审人使用逗号分隔" },
      { key: "director", label: "负责人" },
      { key: "parentId", label: "上级分类", optionsSource: "businessType", optionLabelField: "businessName", placeholder: "顶级分类留空" },
      { key: "remark", label: "备注", multiline: true },
    ],
    columns: [
      { key: "businessName", title: "业务名称", width: 220 },
      { key: "enShortName", title: "英文缩写", width: 150 },
      { key: "director", title: "负责人", width: 140 },
      { key: "reviewer", title: "模型评审人", width: 200 },
      { key: "parentId", title: "上级分类", width: 150 },
    ],
  },
  {
    key: "dwLayer", title: "数仓分层",
    description: "维护数仓分层类型与模型类型，为后续模型设计提供统一的分层定义。",
    searchField: "cnName", searchPlaceholder: "搜索分层名称",
    fields: [
      { key: "cnName", label: "中文名称", required: true },
      { key: "enShortName", label: "英文缩写" },
      { key: "enName", label: "英文名称" },
      { key: "dwType", label: "分层类型", required: true, options: [
        { value: "SOURCE_LAYER", label: "贴源层" },
        { value: "PUBLIC_LAYER", label: "公共层" },
        { value: "APPLICATION_LAYER", label: "应用层" },
        { value: "NONE_LAYER", label: "未分层" },
      ]},
      { key: "dwModelType", label: "模型类型", options: [
        { value: "SOURCE", label: "贴源表" },
        { value: "DIMENSION", label: "维度表" },
        { value: "DIM", label: "维度" },
        { value: "DIMENSION_DIM", label: "维度表 / 维度" },
        { value: "DETAIL", label: "明细表" },
        { value: "SUMMARY", label: "汇总表" },
        { value: "APPLICATION", label: "应用表" },
      ]},
      { key: "director", label: "负责人" },
      { key: "remark", label: "备注", multiline: true },
    ],
    columns: [
      { key: "cnName", title: "分层名称", width: 200 },
      { key: "enShortName", title: "英文缩写", width: 140 },
      { key: "dwType", title: "分层类型", width: 160 },
      { key: "dwModelType", title: "模型类型", width: 160 },
      { key: "director", title: "负责人", width: 140 },
    ],
  },
  {
    key: "dataDomain", title: "数据域",
    description: "公共层的数据域定义，沉淀可复用的业务分类和数据组织边界。",
    searchField: "cnName", searchPlaceholder: "搜索数据域名称",
    fields: [
      { key: "cnName", label: "中文名称", required: true },
      { key: "enShortName", label: "英文缩写" },
      { key: "enName", label: "英文名称" },
      { key: "reviewer", label: "模型评审人", placeholder: "多个评审人使用逗号分隔" },
      { key: "director", label: "负责人" },
      { key: "remark", label: "备注", multiline: true },
    ],
    columns: [
      { key: "cnName", title: "数据域名称", width: 220 },
      { key: "enShortName", title: "英文缩写", width: 150 },
      { key: "enName", title: "英文名称", width: 220 },
      { key: "director", title: "负责人", width: 140 },
      { key: "flag", title: "默认域", width: 100, render: (value) => Number(value) === 1 ? "是" : "否" },
    ],
  },
  {
    key: "businessProcess", title: "业务过程",
    description: "维护数据域下的业务过程，支撑原子指标及后续维度模型的业务定义。",
    searchField: "cnName", searchPlaceholder: "搜索业务过程",
    fields: [
      { key: "cnName", label: "中文名称", required: true },
      { key: "enShortName", label: "英文缩写" },
      { key: "enName", label: "英文名称" },
      { key: "dataDomainId", label: "所属数据域", optionsSource: "dataDomain", optionLabelField: "cnName", placeholder: "请选择数据域" },
      { key: "director", label: "负责人" },
      { key: "remark", label: "备注", multiline: true },
    ],
    columns: [
      { key: "cnName", title: "业务过程", width: 220 },
      { key: "enShortName", title: "英文缩写", width: 150 },
      { key: "dataDomainCnName", title: "所属数据域", width: 200 },
      { key: "director", title: "负责人", width: 140 },
    ],
  },
  {
    key: "dataMart", title: "数据集市",
    description: "应用层的数据集市组织，按业务分类维护集市及其层级关系。",
    searchField: "martName", searchPlaceholder: "搜索数据集市",
    fields: [
      { key: "martName", label: "集市名称", required: true },
      { key: "enShortName", label: "英文缩写" },
      { key: "martType", label: "集市类型", options: [
        { value: "PUBLIC", label: "公共集市" },
        { value: "BUSINESS", label: "业务集市" },
        { value: "APPLICATION", label: "数据应用集市" },
      ]},
      { key: "businessTypeId", label: "业务分类", optionsSource: "businessType", optionLabelField: "businessName", placeholder: "请选择业务分类" },
      { key: "parentId", label: "上级集市", optionsSource: "dataMart", optionLabelField: "martName", placeholder: "顶级集市留空" },
      { key: "director", label: "负责人" },
      { key: "remark", label: "备注", multiline: true },
    ],
    columns: [
      { key: "martName", title: "集市名称", width: 220 },
      { key: "enShortName", title: "英文缩写", width: 150 },
      { key: "martType", title: "集市类型", width: 160 },
      { key: "businessTypeName", title: "业务分类", width: 180 },
      { key: "director", title: "负责人", width: 140 },
    ],
  },
  {
    key: "subjectDomain", title: "主题域",
    description: "数据集市下的主题域定义，用于组织主题模型和后续指标资产。",
    searchField: "domainName", searchPlaceholder: "搜索主题域",
    fields: [
      { key: "domainName", label: "主题域名称", required: true },
      { key: "enShortName", label: "英文缩写" },
      { key: "dataMartId", label: "所属集市", optionsSource: "dataMart", optionLabelField: "martName", placeholder: "请选择数据集市" },
      { key: "parentId", label: "上级主题域", optionsSource: "subjectDomain", optionLabelField: "domainName", placeholder: "顶级主题域留空" },
      { key: "director", label: "负责人" },
      { key: "remark", label: "备注", multiline: true },
    ],
    columns: [
      { key: "domainName", title: "主题域名称", width: 220 },
      { key: "enShortName", title: "英文缩写", width: 150 },
      { key: "dataMartName", title: "所属集市", width: 200 },
      { key: "director", title: "负责人", width: 140 },
      { key: "parentId", title: "上级主题域", width: 150 },
    ],
  },
];

export function DataModelingPlanningPage() {
  const { currentWorkspace } = useWorkspace();
  const [activeKey, setActiveKey] = useState<ModelingEntityDefinition["key"]>("businessType");
  const activeDefinition = DEFINITIONS.find((item) => item.key === activeKey) ?? DEFINITIONS[0];

  return (
    <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <PageHeader title="数仓规划" bordered className="bg-white px-6 max-md:px-4" />
      <div className="flex min-h-0 flex-1 flex-col gap-3 px-6 pb-4 pt-5 max-md:px-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-xs text-[var(--yak-components-muted-text)]">
              统一管理业务分类、数仓分层、数据域、业务过程、数据集市和主题域。
            </p>
          </div>
          {currentWorkspace ? (
            <div className="rounded-md border border-[var(--yak-components-panel-border)] bg-white px-3 py-1.5 text-xs text-[var(--yak-components-muted-text)]">
              当前工作空间：{currentWorkspace.name}
            </div>
          ) : null}
        </div>
        <div className="flex shrink-0 flex-wrap gap-1 rounded-md border border-[var(--yak-components-panel-border)] bg-white p-1">
          {DEFINITIONS.map((item) => (
            <Button key={item.key} size="sm" variant={activeKey === item.key ? "primary" : "ghost"}
              onClick={() => setActiveKey(item.key)}>
              {item.title}
            </Button>
          ))}
        </div>
        {currentWorkspace ? (
          <ModelingEntityPage key={activeDefinition.key} projectCode={currentWorkspace.id} definition={activeDefinition} />
        ) : (
          <div className="flex min-h-[240px] flex-1 items-center justify-center rounded-md border border-dashed border-[var(--yak-components-panel-border)] bg-white text-sm text-[var(--yak-components-muted-text)]">
            请先选择工作空间
          </div>
        )}
      </div>
    </div>
  );
}

export default DataModelingPlanningPage;
