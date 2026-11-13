import { useState } from "react";
import { BookOpen } from "lucide-react";
import { Button, PageHeader } from "@yak-ops/yak-ui";

import { useWorkspace } from "@/hooks/use-workspace";

import {
  ModelingEntityPage,
  type ModelingEntityDefinition,
} from "../components/modeling-entity-page";

const FIELD_DIRECTORY_TYPES = [
  { value: "FOLDER", label: "文件夹" },
  { value: "STANDARD_SET", label: "标准集" },
  { value: "STANDARD", label: "字段标准" },
];
const CODE_DIRECTORY_TYPES = [
  { value: "FOLDER", label: "文件夹" },
  { value: "CODE", label: "代码标准" },
];
const UNIT_DIRECTORY_TYPES = [
  { value: "FOLDER", label: "文件夹" },
  { value: "UNIT", label: "单位" },
];

const DEFINITIONS: ModelingEntityDefinition[] = [
  {
    key: "standardDirectory",
    title: "字段标准目录",
    description: "维护字段标准的目录层级，目录名称在同一标准模块内唯一。",
    searchField: "dirName",
    searchPlaceholder: "搜索目录名称",
    filters: { dirModule: "FIELD" },
    defaults: { dirModule: "FIELD" },
    fields: [
      { key: "dirName", label: "目录名称", required: true },
      { key: "dirType", label: "目录类型", required: true, options: FIELD_DIRECTORY_TYPES },
      { key: "dirModule", label: "归属模块", required: true, options: [{ value: "FIELD", label: "字段标准" }] },
      { key: "parentId", label: "上级目录", optionsSource: "standardDirectory", optionsParams: { dirModule: "FIELD" }, optionLabelField: "dirName", placeholder: "顶级目录留空" },
    ],
    columns: [
      { key: "dirName", title: "目录名称", width: 240 },
      { key: "dirType", title: "目录类型", width: 140, render: (value) => value === "FOLDER" ? "文件夹" : value === "STANDARD_SET" ? "标准集" : "字段标准" },
      { key: "parentId", title: "上级目录", width: 200 },
    ],
  },
  {
    key: "fieldStandard",
    title: "字段标准",
    description: "维护字段标准编码、命名、数据类型、长度精度、空值约束和业务定义。",
    searchField: "cnName",
    searchPlaceholder: "搜索字段标准",
    fields: [
      { key: "stdCode", label: "标准编码", required: true },
      { key: "enShortName", label: "英文缩写", required: true },
      { key: "enName", label: "英文名称", required: true },
      { key: "cnName", label: "中文名称", required: true },
      { key: "fieldDataType", label: "数据类型", required: true, options: [
        { value: "INT", label: "INT" }, { value: "BIGINT", label: "BIGINT" },
        { value: "BOOLEAN", label: "BOOLEAN" }, { value: "STRING", label: "STRING" },
        { value: "TEXT", label: "TEXT" }, { value: "DECIMAL", label: "DECIMAL" },
        { value: "DATE(", label: "DATE" }, { value: "DATETIME", label: "DATETIME" },
        { value: "TIMESTAMP", label: "TIMESTAMP" }, { value: "JSON", label: "JSON" },
        { value: "ARRAY", label: "ARRAY" }, { value: "DOUBLE", label: "DOUBLE" },
        { value: "MAP", label: "MAP" }, { value: "STRUCT", label: "STRUCT" },
      ] },
      { key: "fieldLength", label: "字段长度", placeholder: "例如 64" },
      { key: "fieldPrecision", label: "字段精度", placeholder: "例如 2" },
      { key: "fieldCanNull", label: "允许为空", options: [{ value: "true", label: "是" }, { value: "false", label: "否" }] },
      { key: "fieldDefaultValue", label: "默认值" },
      { key: "businessDefinition", label: "业务定义", multiline: true },
      { key: "parentId", label: "所属目录", optionsSource: "standardDirectory", optionsParams: { dirModule: "FIELD" }, optionLabelField: "dirName" },
      { key: "parentStd", label: "父级标准" },
      { key: "reference", label: "引用代码", placeholder: "请输入引用代码" },
    ],
    columns: [
      { key: "stdCode", title: "标准编码", width: 150 },
      { key: "cnName", title: "中文名称", width: 220 },
      { key: "enShortName", title: "英文缩写", width: 150 },
      { key: "fieldDataType", title: "数据类型", width: 120 },
      { key: "fieldLength", title: "长度", width: 90 },
      { key: "fieldPrecision", title: "精度", width: 90 },
      { key: "fieldCanNull", title: "允许为空", width: 110, render: (value) => value === true || value === "true" ? "是" : value === false || value === "false" ? "否" : "-" },
    ],
  },
  {
    key: "standardDirectory",
    title: "标准代码目录",
    description: "维护标准代码的目录层级，供代码集和指标修饰词引用。",
    searchField: "dirName",
    searchPlaceholder: "搜索目录名称",
    filters: { dirModule: "CODE" },
    defaults: { dirModule: "CODE" },
    fields: [
      { key: "dirName", label: "目录名称", required: true },
      { key: "dirType", label: "目录类型", required: true, options: CODE_DIRECTORY_TYPES },
      { key: "dirModule", label: "归属模块", required: true, options: [{ value: "CODE", label: "标准代码" }] },
      { key: "parentId", label: "上级目录", optionsSource: "standardDirectory", optionsParams: { dirModule: "CODE" }, optionLabelField: "dirName", placeholder: "顶级目录留空" },
    ],
    columns: [
      { key: "dirName", title: "目录名称", width: 240 },
      { key: "dirType", title: "目录类型", width: 140, render: (value) => value === "FOLDER" ? "文件夹" : "代码标准" },
      { key: "parentId", title: "上级目录", width: 200 },
    ],
  },
  {
    key: "codeStandard",
    title: "标准代码",
    description: "维护标准代码集及其编码含义，支持指标修饰词引用。",
    searchField: "cnName",
    searchPlaceholder: "搜索标准代码",
    fields: [
      { key: "parentId", label: "所属目录", optionsSource: "standardDirectory", optionsParams: { dirModule: "CODE" }, optionLabelField: "dirName" },
      { key: "enShortName", label: "英文缩写", required: true },
      { key: "enName", label: "英文名称", required: true },
      { key: "cnName", label: "中文名称", required: true },
      { key: "remark", label: "描述", multiline: true },
    ],
    columns: [
      { key: "cnName", title: "代码集名称", width: 240 },
      { key: "enShortName", title: "英文缩写", width: 150 },
      { key: "enName", title: "英文名称", width: 220 },
      { key: "parentId", title: "所属目录", width: 200 },
    ],
  },
  {
    key: "codeDetail",
    title: "代码明细",
    description: "维护标准代码集下的枚举值、英文值和中文含义。",
    searchField: "cnName",
    searchPlaceholder: "搜索代码明细",
    fields: [
      { key: "codeId", label: "所属标准代码", required: true, optionsSource: "codeStandard" },
      { key: "enShortName", label: "编码取值", required: true },
      { key: "enName", label: "英文名称", required: true },
      { key: "cnName", label: "中文名称", required: true },
      { key: "remark", label: "编码含义", multiline: true },
    ],
    columns: [
      { key: "cnName", title: "代码名称", width: 220 },
      { key: "enShortName", title: "编码取值", width: 160 },
      { key: "enName", title: "英文名称", width: 220 },
      { key: "codeId", title: "所属标准代码", width: 200 },
    ],
  },
  {
    key: "standardDirectory",
    title: "度量单位目录",
    description: "维护度量单位分类，便于原子指标选择统一单位。",
    searchField: "dirName",
    searchPlaceholder: "搜索目录名称",
    filters: { dirModule: "UNIT" },
    defaults: { dirModule: "UNIT" },
    fields: [
      { key: "dirName", label: "目录名称", required: true },
      { key: "dirType", label: "目录类型", required: true, options: UNIT_DIRECTORY_TYPES },
      { key: "dirModule", label: "归属模块", required: true, options: [{ value: "UNIT", label: "度量单位" }] },
      { key: "parentId", label: "上级目录", optionsSource: "standardDirectory", optionsParams: { dirModule: "UNIT" }, optionLabelField: "dirName", placeholder: "顶级目录留空" },
    ],
    columns: [
      { key: "dirName", title: "目录名称", width: 240 },
      { key: "dirType", title: "目录类型", width: 140, render: (value) => value === "FOLDER" ? "文件夹" : "单位" },
      { key: "parentId", title: "上级目录", width: 200 },
    ],
  },
  {
    key: "unitStandard",
    title: "度量单位",
    description: "统一维护货币、数量、比例、排名和时间等指标度量单位。",
    searchField: "cnName",
    searchPlaceholder: "搜索度量单位",
    fields: [
      { key: "parentId", label: "所属目录", optionsSource: "standardDirectory", optionsParams: { dirModule: "UNIT" }, optionLabelField: "dirName" },
      { key: "enShortName", label: "英文缩写", required: true },
      { key: "enName", label: "英文名称", required: true },
      { key: "cnName", label: "中文名称", required: true },
    ],
    columns: [
      { key: "cnName", title: "单位名称", width: 220 },
      { key: "enShortName", title: "英文缩写", width: 160 },
      { key: "enName", title: "英文名称", width: 240 },
      { key: "parentId", title: "所属目录", width: 200 },
    ],
  },
  {
    key: "dictionaryStandard",
    title: "命名词典",
    description: "维护标准英文缩写、英文名称和中文名称，减少数据模型命名歧义。",
    searchField: "cnName",
    searchPlaceholder: "搜索命名词典",
    fields: [
      { key: "enShortName", label: "英文缩写", required: true },
      { key: "enName", label: "英文名称", required: true },
      { key: "cnName", label: "中文名称", required: true },
    ],
    columns: [
      { key: "cnName", title: "中文名称", width: 240 },
      { key: "enShortName", title: "英文缩写", width: 180 },
      { key: "enName", title: "英文名称", width: 260 },
    ],
  },
];

export function DataModelingStandardsPage() {
  const { currentWorkspace } = useWorkspace();
  const [activeKey, setActiveKey] = useState(0);
  const activeDefinition = DEFINITIONS[activeKey] || DEFINITIONS[0];

  return (
    <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <PageHeader
        title="数据标准"
        bordered
        className="bg-white px-6 max-md:px-4"
      />
      <div className="flex min-h-0 flex-1 flex-col gap-3 px-6 pb-4 pt-5 max-md:px-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <BookOpen size={16} className="text-[var(--yak-color-primary)]" />
            <p className="text-xs text-[var(--yak-components-muted-text)]">
              统一管理字段标准、标准代码、度量单位和命名词典。
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
              key={`${item.title}-${index}`}
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
            key={`${activeDefinition.key}-${JSON.stringify(activeDefinition.filters || {})}-${activeKey}`}
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

export default DataModelingStandardsPage;
