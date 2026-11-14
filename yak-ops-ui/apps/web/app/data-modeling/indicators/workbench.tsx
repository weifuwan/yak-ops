import { useEffect, useMemo, useState, type MouseEvent as ReactMouseEvent } from "react";
import { createPortal } from "react-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  Field,
  FieldLabel,
  Input,
  Modal,
  Segmented,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  Textarea,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import {
  ArrowLeftToLine,
  ArrowRightToLine,
  CalendarDays,
  ChartNoAxesCombined,
  ChevronDown,
  ChevronRight,
  CircleDot,
  FileText,
  Folder,
  History,
  List,
  Ellipsis,
  Plus,
  RefreshCw,
  Save,
  Search,
  Send,
  Trash2,
  X,
  XCircle,
} from "lucide-react";

import {
  modelingBusinessTypeApi,
  modelingEntityApi,
  type ModelingRecord,
  type ModelingResourceKey,
  type ModelingVersionRecord,
} from "../service";
import { PlanningIcon } from "../planning/icons";

export type IndicatorKind = "atomIndicator" | "derivativeIndicator" | "composeIndicator" | "modifier" | "timePeriod";
type IndicatorMode = "create" | "edit" | "list";

type IndicatorFieldOption = { value: string; label: string };
type IndicatorField = {
  key: string;
  label: string;
  section?: string;
  placeholder?: string;
  required?: boolean;
  multiline?: boolean;
  readOnly?: boolean;
  options?: IndicatorFieldOption[];
  optionsSource?: ModelingResourceKey;
  optionLabelField?: string;
};
type IndicatorColumn = { key: string; title: string; width?: number };
type IndicatorDefinition = {
  key: IndicatorKind;
  title: string;
  description: string;
  fields: IndicatorField[];
  columns: IndicatorColumn[];
  versioned: boolean;
};

type IndicatorTab = {
  key: string;
  kind: IndicatorKind;
  mode: IndicatorMode;
  label: string;
  recordId?: string;
  record?: ModelingRecord;
  draftValues?: Record<string, string>;
  dirty?: boolean;
};

type IndicatorTreeNodeType = "root" | "businessType" | "dataDomain" | "businessProcess" | "asset" | "group";

type IndicatorTreeNode = {
  key: string;
  id: string;
  title: string;
  kind: "group" | "asset";
  nodeType?: IndicatorTreeNodeType;
  count?: number;
  record?: ModelingRecord;
  children: IndicatorTreeNode[];
};

const DW_TYPE_OPTIONS: IndicatorFieldOption[] = [
  { value: "SOURCE_LAYER", label: "贴源层" },
  { value: "PUBLIC_LAYER", label: "公共层" },
  { value: "APPLICATION_LAYER", label: "应用层" },
  { value: "NONE_LAYER", label: "未分层" },
];

const INDICATOR_DEFINITIONS: IndicatorDefinition[] = [
  {
    key: "atomIndicator",
    title: "原子指标",
    description: "原子指标是最小、不可再拆分的业务度量，负责沉淀统一的业务口径与计算逻辑。",
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
      { key: "domainId", label: "数据域", section: "业务归属", optionsSource: "dataDomain" },
      { key: "busProcessId", label: "业务过程", section: "业务归属", optionsSource: "businessProcess" },
      { key: "calculateFunction", label: "计算函数", section: "计算逻辑", options: [
        { value: "COUNT", label: "计算（COUNT）" }, { value: "SUM", label: "累加（SUM）" },
        { value: "AVG", label: "平均值（AVG）" }, { value: "MAX", label: "最大值（MAX）" },
        { value: "MIN", label: "最小值（MIN）" }, { value: "RATE", label: "率" },
        { value: "RANK", label: "排名" }, { value: "OTHER", label: "其他" },
      ] },
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
  },
  {
    key: "derivativeIndicator",
    title: "派生指标",
    description: "基于原子指标、时间周期和业务修饰词组合形成派生指标。",
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
  },
  {
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
  },
  {
    key: "modifier",
    title: "修饰词",
    description: "维护普通业务修饰词和维度枚举修饰词，支持引用标准代码及枚举值。",
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
      { key: "dwhId", label: "数仓分层", section: "业务归属", optionsSource: "dwLayer" },
      { key: "martId", label: "数据集市", section: "业务归属", optionsSource: "dataMart", optionLabelField: "martName" },
      { key: "subjectId", label: "主题域", section: "业务归属", optionsSource: "subjectDomain", optionLabelField: "domainName" },
      { key: "busTypeId", label: "业务分类", section: "业务归属", optionsSource: "businessType", optionLabelField: "businessName" },
      { key: "domainId", label: "数据域", section: "业务归属", optionsSource: "dataDomain" },
      { key: "dimensionTbId", label: "维度表 ID", section: "维度枚举" },
      { key: "dimensionFdId", label: "维度字段 ID", section: "维度枚举" },
      { key: "codeId", label: "标准代码", section: "维度枚举", optionsSource: "codeStandard" },
      { key: "codeDetailId", label: "代码明细", section: "维度枚举", optionsSource: "codeDetail" },
    ],
    columns: [
      { key: "enShortName", title: "英文缩写", width: 145 },
      { key: "cnName", title: "修饰词名称", width: 220 },
      { key: "modifierType", title: "类型", width: 160 },
      { key: "director", title: "负责人", width: 120 },
      { key: "submit", title: "状态", width: 90 },
    ],
  },
  {
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
  },
];

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
const RELATION_FIELDS: Record<string, ModelingResourceKey> = {
  atomicId: "atomIndicator",
  timePeriodId: "timePeriod",
  deriveId: "derivativeIndicator",
  categoryId: "timePeriodCategory",
  unitId: "unitStandard",
  busTypeId: "businessType",
  domainId: "dataDomain",
  busProcessId: "businessProcess",
  dwhId: "dwLayer",
  martId: "dataMart",
  subjectId: "subjectDomain",
  codeId: "codeStandard",
  codeDetailId: "codeDetail",
};

function getDefinition(kind: IndicatorKind): IndicatorDefinition {
  return INDICATOR_DEFINITIONS.find((item) => item.key === kind) || INDICATOR_DEFINITIONS[0];
}

function recordName(record: ModelingRecord): string {
  const candidates = [record.businessName, record.domainName, record.martName, record.processName, record.cnName, record.name, record.enShortName];
  const value = candidates.find((item) => item !== undefined && item !== null && String(item).trim() !== "");
  return value === undefined ? String(record.id || "未命名") : String(value);
}

function readFormValue(record: ModelingRecord | undefined, key: string): string {
  const value = record?.[key];
  if (value === undefined || value === null) return "";
  return typeof value === "boolean" ? String(value) : String(value);
}

function initialFormValues(
  kind: IndicatorKind,
  record: ModelingRecord | undefined,
  fields: IndicatorField[],
): Record<string, string> {
  const values = Object.fromEntries(fields.map((field) => [field.key, readFormValue(record, field.key)]));
  if (!record && kind === "atomIndicator") {
    values.dwType = "PUBLIC_LAYER";
    values.removeDuplicates = "true";
    values.decimalNumber = "0";
  }
  if (!record && kind === "composeIndicator") values.computeMode = "0";
  if (!record && kind === "modifier") values.modifierType = "SIMPLE";
  return values;
}

function fieldOptions(
  field: IndicatorField,
  optionsByResource: Partial<Record<ModelingResourceKey, ModelingRecord[]>>,
): IndicatorFieldOption[] {
  if (field.options) return field.options;
  if (!field.optionsSource) return [];
  const labelField = field.optionLabelField || "cnName";
  return (optionsByResource[field.optionsSource] || []).map((record) => {
    const label = record[labelField] ?? record.cnName ?? record.businessName ?? record.id;
    const shortName = record.enShortName ? " (" + String(record.enShortName) + ")" : "";
    return { value: String(record.id || ""), label: String(label || "未命名") + shortName };
  }).filter((item) => item.value);
}

function buildGroupedTree(
  groups: ModelingRecord[],
  records: ModelingRecord[],
  groupBy: "BUSINESS_TYPE" | "DATA_DOMAIN",
): IndicatorTreeNode[] {
  const groupMap = new Map<string, IndicatorTreeNode>();
  groups.forEach((record) => {
    if (!record.id) return;
    const id = String(record.id);
    groupMap.set(id, {
      key: "group:" + id,
      id,
      title: recordName(record),
      kind: "group",
      children: [],
    });
  });

  const roots: IndicatorTreeNode[] = [];
  groups.forEach((record) => {
    if (!record.id) return;
    const id = String(record.id);
    const node = groupMap.get(id);
    if (!node) return;
    const parentId = record.parentId ? String(record.parentId) : "";
    const parent = parentId ? groupMap.get(parentId) : undefined;
    if (parent && parent !== node) parent.children.push(node);
    else roots.push(node);
  });

  const relationKey = groupBy === "BUSINESS_TYPE" ? "busTypeId" : "domainId";
  const ungrouped: IndicatorTreeNode[] = [];
  records.forEach((record) => {
    if (!record.id) return;
    const id = String(record.id);
    const leaf: IndicatorTreeNode = {
      key: "asset:" + id,
      id,
      title: recordName(record),
      kind: "asset",
      record,
      children: [],
    };
    const parentId = record[relationKey] ? String(record[relationKey]) : "";
    const parent = parentId ? groupMap.get(parentId) : undefined;
    if (parent) parent.children.push(leaf);
    else ungrouped.push(leaf);
  });

  if (ungrouped.length) {
    roots.push({
      key: "group:uncategorized",
      id: "uncategorized",
      title: "未分类",
      kind: "group",
      children: ungrouped,
    });
  }
  if (!roots.length) {
    return [{
      key: "group:all",
      id: "all",
      title: "全部指标",
      kind: "group",
      children: records.filter((record) => Boolean(record.id)).map((record) => ({
        key: "asset:" + String(record.id),
        id: String(record.id),
        title: recordName(record),
        kind: "asset" as const,
        record,
        children: [],
      })),
    }];
  }
  return roots;
}

function buildAtomIndicatorTree(
  businessTypes: ModelingRecord[],
  dataDomains: ModelingRecord[],
  businessProcesses: ModelingRecord[],
  records: ModelingRecord[],
  groupBy: "BUSINESS_TYPE" | "DATA_DOMAIN",
  relatedDomainIdsByBusinessType: Map<string, string[]>,
): IndicatorTreeNode[] {
  const uniqueRecordMap = new Map<string, ModelingRecord>();
  records.forEach((record) => {
    if (record.id) uniqueRecordMap.set(String(record.id), record);
  });
  const uniqueRecords = Array.from(uniqueRecordMap.values());

  const domainMap = new Map<string, ModelingRecord>();
  dataDomains.forEach((record) => {
    if (record.id) domainMap.set(String(record.id), record);
  });
  const businessTypeMap = new Map<string, ModelingRecord>();
  businessTypes.forEach((record) => {
    if (record.id) businessTypeMap.set(String(record.id), record);
  });
  const processMap = new Map<string, ModelingRecord>();
  businessProcesses.forEach((record) => {
    if (record.id) processMap.set(String(record.id), record);
  });

  const assetNode = (record: ModelingRecord): IndicatorTreeNode => ({
    key: "asset:" + String(record.id),
    id: String(record.id),
    title: recordName(record),
    kind: "asset",
    nodeType: "asset",
    record,
    children: [],
  });

  const buildDomainNode = (
    domainId: string,
    scopedRecords: ModelingRecord[],
    keyPrefix: string,
  ): IndicatorTreeNode => {
    const domain = domainMap.get(domainId);
    const domainProcesses = domain
      ? businessProcesses.filter((process) => String(process.dataDomainId || "") === domainId)
      : [];
    const processIds = new Set(domainProcesses.map((process) => String(process.id)));
    const children: IndicatorTreeNode[] = domainProcesses.map((process) => {
      const processId = String(process.id);
      const processRecords = scopedRecords.filter((record) => String(record.busProcessId || "") === processId);
      return {
        key: keyPrefix + ":process:" + processId,
        id: processId,
        title: recordName(process),
        kind: "group",
        nodeType: "businessProcess",
        record: process,
        count: processRecords.length,
        children: processRecords.map(assetNode),
      };
    });

    const unassignedProcessRecords = scopedRecords.filter((record) => {
      const processId = String(record.busProcessId || "");
      const process = processMap.get(processId);
      return !process || String(process.dataDomainId || "") !== domainId || !processIds.has(processId);
    });
    if (unassignedProcessRecords.length) {
      children.push({
        key: keyPrefix + ":domain:" + domainId + ":unassigned-process",
        id: "unassigned-process",
        title: "未关联业务过程",
        kind: "group",
        nodeType: "group",
        count: unassignedProcessRecords.length,
        children: unassignedProcessRecords.map(assetNode),
      });
    }

    return {
      key: keyPrefix + ":domain:" + domainId,
      id: domainId,
      title: domain ? recordName(domain) : "未关联数据域",
      kind: "group",
      nodeType: domain ? "dataDomain" : "group",
      record: domain,
      count: scopedRecords.length,
      children,
    };
  };

  const buildUnassignedDomainNode = (
    scopedRecords: ModelingRecord[],
    key: string,
  ): IndicatorTreeNode => ({
    key,
    id: "unassigned-domain",
    title: "未关联数据域",
    kind: "group",
    nodeType: "group",
    count: scopedRecords.length,
    children: scopedRecords.map(assetNode),
  });

  const root: IndicatorTreeNode = {
    key: "root:atom-indicator",
    id: "root",
    title: "原子指标",
    kind: "group",
    nodeType: "root",
    count: uniqueRecords.length,
    children: [],
  };

  if (groupBy === "DATA_DOMAIN") {
    root.children = dataDomains.filter((domain) => domain.id).map((domain) => {
      const domainId = String(domain.id);
      const domainRecords = uniqueRecords.filter((record) => String(record.domainId || "") === domainId);
      return buildDomainNode(domainId, domainRecords, "data-domain:" + domainId);
    });

    const unassignedDomainRecords = uniqueRecords.filter((record) => {
      const domainId = String(record.domainId || "");
      return !domainId || !domainMap.has(domainId);
    });
    if (unassignedDomainRecords.length) {
      root.children.push(buildUnassignedDomainNode(unassignedDomainRecords, "data-domain:unassigned"));
    }
    return [root];
  }

  const childrenByParent = new Map<string, ModelingRecord[]>();
  businessTypes.forEach((businessType) => {
    const id = String(businessType.id || "");
    const parentId = String(businessType.parentId || "");
    if (!id || !parentId || !businessTypeMap.has(parentId)) return;
    const children = childrenByParent.get(parentId) || [];
    children.push(businessType);
    childrenByParent.set(parentId, children);
  });

  const visitedBusinessTypeIds = new Set<string>();
  const buildBusinessTypeNode = (businessType: ModelingRecord): IndicatorTreeNode | null => {
    const id = String(businessType.id || "");
    if (!id || visitedBusinessTypeIds.has(id)) return null;
    visitedBusinessTypeIds.add(id);

    const directRecords = uniqueRecords.filter((record) => String(record.busTypeId || "") === id);
    const childNodes = (childrenByParent.get(id) || [])
      .map(buildBusinessTypeNode)
      .filter((node): node is IndicatorTreeNode => Boolean(node));

    const relatedDomainIds = relatedDomainIdsByBusinessType.get(id) || [];
    const directDomainIds = directRecords
      .map((record) => String(record.domainId || ""))
      .filter((domainId) => domainId && domainMap.has(domainId));
    const domainIds = Array.from(new Set([...relatedDomainIds, ...directDomainIds]))
      .filter((domainId) => domainMap.has(domainId));
    const domainNodes = domainIds.map((domainId) => buildDomainNode(
      domainId,
      directRecords.filter((record) => String(record.domainId || "") === domainId),
      "business-type:" + id,
    ));

    const unassignedDomainRecords = directRecords.filter((record) => {
      const domainId = String(record.domainId || "");
      return !domainId || !domainMap.has(domainId);
    });
    if (unassignedDomainRecords.length) {
      domainNodes.push(buildUnassignedDomainNode(
        unassignedDomainRecords,
        "business-type:" + id + ":unassigned-domain",
      ));
    }

    return {
      key: "business-type:" + id,
      id,
      title: recordName(businessType),
      kind: "group",
      nodeType: "businessType",
      record: businessType,
      count: directRecords.length + childNodes.reduce((total, child) => total + (child.count || 0), 0),
      children: [...childNodes, ...domainNodes],
    };
  };

  const businessTypeRoots = businessTypes.filter((businessType) => {
    const parentId = String(businessType.parentId || "");
    return !parentId || !businessTypeMap.has(parentId);
  }).map(buildBusinessTypeNode).filter((node): node is IndicatorTreeNode => Boolean(node));

  businessTypes.forEach((businessType) => {
    const id = String(businessType.id || "");
    if (id && !visitedBusinessTypeIds.has(id)) {
      const node = buildBusinessTypeNode(businessType);
      if (node) businessTypeRoots.push(node);
    }
  });

  root.children = businessTypeRoots;

  const unassignedBusinessTypeRecords = uniqueRecords.filter((record) => {
    const businessTypeId = String(record.busTypeId || "");
    return !businessTypeId || !businessTypeMap.has(businessTypeId);
  });
  if (unassignedBusinessTypeRecords.length) {
    const domainIds = Array.from(new Set(unassignedBusinessTypeRecords
      .map((record) => String(record.domainId || ""))
      .filter((domainId) => domainId && domainMap.has(domainId))));
    const domainNodes = domainIds.map((domainId) => buildDomainNode(
      domainId,
      unassignedBusinessTypeRecords.filter((record) => String(record.domainId || "") === domainId),
      "business-type:unassigned",
    ));
    const unassignedDomainRecords = unassignedBusinessTypeRecords.filter((record) => {
      const domainId = String(record.domainId || "");
      return !domainId || !domainMap.has(domainId);
    });
    if (unassignedDomainRecords.length) {
      domainNodes.push(buildUnassignedDomainNode(
        unassignedDomainRecords,
        "business-type:unassigned:unassigned-domain",
      ));
    }
    root.children.push({
      key: "business-type:unassigned",
      id: "unassigned-business-type",
      title: "未关联业务分类",
      kind: "group",
      nodeType: "group",
      count: unassignedBusinessTypeRecords.length,
      children: domainNodes,
    });
  }

  return [root];
}

function buildTimePeriodTree(categories: ModelingRecord[], records: ModelingRecord[]): IndicatorTreeNode[] {
  const nodeMap = new Map<string, IndicatorTreeNode>();
  categories.forEach((record) => {
    if (!record.id) return;
    const id = String(record.id);
    nodeMap.set(id, {
      key: "category:" + id,
      id,
      title: recordName(record),
      kind: "group",
      children: [],
    });
  });
  const roots: IndicatorTreeNode[] = [];
  categories.forEach((record) => {
    if (!record.id) return;
    const node = nodeMap.get(String(record.id));
    if (!node) return;
    const parent = record.parentId ? nodeMap.get(String(record.parentId)) : undefined;
    if (parent && parent !== node) parent.children.push(node);
    else roots.push(node);
  });
  const ungrouped: IndicatorTreeNode[] = [];
  records.forEach((record) => {
    if (!record.id) return;
    const leaf: IndicatorTreeNode = {
      key: "asset:" + String(record.id),
      id: String(record.id),
      title: recordName(record),
      kind: "asset",
      record,
      children: [],
    };
    const parent = record.categoryId ? nodeMap.get(String(record.categoryId)) : undefined;
    if (parent) parent.children.push(leaf);
    else ungrouped.push(leaf);
  });
  if (ungrouped.length) {
    roots.push({
      key: "group:uncategorized",
      id: "uncategorized",
      title: "未归类周期",
      kind: "group",
      children: ungrouped,
    });
  }
  return roots;
}

function filterTree(nodes: IndicatorTreeNode[], keyword: string): IndicatorTreeNode[] {
  const normalized = keyword.trim().toLowerCase();
  if (!normalized) return nodes;
  return nodes.reduce<IndicatorTreeNode[]>((result, node) => {
    const children = filterTree(node.children, normalized);
    const searchableText = [
      node.title,
      node.record?.cnName,
      node.record?.enName,
      node.record?.enShortName,
    ].filter(Boolean).join(" ").toLowerCase();
    if (searchableText.includes(normalized) || children.length) {
      result.push({ ...node, children });
    }
    return result;
  }, []);
}

function collectExpandedKeys(nodes: IndicatorTreeNode[]): string[] {
  const result: string[] = [];
  nodes.forEach((node) => {
    if (node.children.length) {
      result.push(node.key);
      result.push(...collectExpandedKeys(node.children));
    }
  });
  return result;
}

type TreeRowsProps = {
  nodes: IndicatorTreeNode[];
  depth: number;
  expandedKeys: Set<string>;
  selectedKey: string;
  onToggle: (key: string) => void;
  onSelect: (record: ModelingRecord) => void;
  onContextMenu: (event: ReactMouseEvent<HTMLButtonElement>, node: IndicatorTreeNode) => void;
};

function IndicatorTreeRows({
  nodes,
  depth,
  expandedKeys,
  selectedKey,
  onToggle,
  onSelect,
  onContextMenu,
}: TreeRowsProps) {
  return (
    <div className="space-y-0.5">
      {nodes.map((node) => {
        const hasChildren = node.children.length > 0;
        const expanded = expandedKeys.has(node.key);
        const selected = selectedKey === node.key;
        return (
          <div key={node.key}>
            <button
              type="button"
              title={node.title}
              onClick={() => {
                if (node.kind === "asset" && node.record) onSelect(node.record);
                else onToggle(node.key);
              }}
              onContextMenu={(event) => {
                if (node.kind === "asset") onContextMenu(event, node);
              }}
              className={[
                "group flex min-h-8 w-full items-center gap-1.5 rounded-md px-2 text-left text-xs transition-colors",
                selected
                  ? "bg-[var(--yak-color-primary-soft)] text-[var(--yak-color-primary)]"
                  : "text-[var(--yak-components-panel-text)] hover:bg-[var(--yak-color-hover)]",
              ].join(" ")}
              style={{ paddingLeft: 8 + depth * 14 }}
            >
              <span className="flex size-4 shrink-0 items-center justify-center text-[var(--yak-components-muted-text)]">
                {node.kind === "group" && hasChildren
                  ? expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />
                  : null}
              </span>
              {node.nodeType === "root" ? (
                <ChartNoAxesCombined size={14} className="shrink-0 text-[var(--yak-color-primary)]" />
              ) : node.nodeType === "businessType" ? (
                <PlanningIcon name="business-type" size={14} className={selected ? "text-[var(--yak-color-primary)]" : "text-[var(--yak-components-muted-text)]"} />
              ) : node.nodeType === "dataDomain" ? (
                <PlanningIcon name="data-domain" size={14} className={selected ? "text-[var(--yak-color-primary)]" : "text-[var(--yak-components-muted-text)]"} />
              ) : node.nodeType === "businessProcess" ? (
                <PlanningIcon name="business-process" size={14} className={selected ? "text-[var(--yak-color-primary)]" : "text-[var(--yak-components-muted-text)]"} />
              ) : node.kind === "group" ? (
                <Folder size={14} className="shrink-0 text-[var(--yak-components-muted-text)]" />
              ) : (
                <FileText size={14} className="shrink-0 text-[var(--yak-color-primary)]" />
              )}
              <span className="min-w-0 flex-1 truncate">{node.title}</span>
              {typeof node.count === "number" ? (
                <span className="shrink-0 text-[11px] tabular-nums text-[var(--yak-components-muted-text)]">({node.count})</span>
              ) : null}
              {node.kind === "asset" && node.record?.submit === true ? (
                <span className="shrink-0 rounded bg-[var(--yak-color-hover)] px-1.5 py-0.5 text-[10px] text-[var(--yak-components-muted-text)]">已提交</span>
              ) : null}
            </button>
            {hasChildren && expanded ? (
              <div className="mt-0.5">
                <IndicatorTreeRows
                  nodes={node.children}
                  depth={depth + 1}
                  expandedKeys={expandedKeys}
                  selectedKey={selectedKey}
                  onToggle={onToggle}
                  onSelect={onSelect}
                  onContextMenu={onContextMenu}
                />
              </div>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}

type IndicatorListTabProps = {
  projectCode: string;
  definition: IndicatorDefinition;
  dwType: string;
  optionsByResource: Partial<Record<ModelingResourceKey, ModelingRecord[]>>;
  onEdit: (record: ModelingRecord) => void;
  onDelete: (record: ModelingRecord) => void;
};

function IndicatorListTab({
  projectCode,
  definition,
  dwType,
  optionsByResource,
  onEdit,
  onDelete,
}: IndicatorListTabProps) {
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState("all");
  const [businessTypeId, setBusinessTypeId] = useState("");
  const [domainId, setDomainId] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [applied, setApplied] = useState({
    keyword: "",
    status: "all",
    businessTypeId: "",
    domainId: "",
    categoryId: "",
  });
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const pageQuery = useQuery({
    queryKey: [
      "modeling",
      "indicator-page",
      projectCode,
      definition.key,
      pageNo,
      pageSize,
      JSON.stringify(applied),
      definition.key === "atomIndicator" ? dwType : "all",
    ],
    queryFn: () => {
      const params: Record<string, unknown> = {
        pageNo,
        pageSize,
        keyword: applied.keyword || undefined,
      };
      if (definition.key === "atomIndicator") params.dwType = dwType;
      if (applied.status !== "all") params.submit = applied.status;
      if (applied.businessTypeId) params.busTypeId = applied.businessTypeId;
      if (applied.domainId) params.domainId = applied.domainId;
      if (applied.categoryId) params.categoryId = applied.categoryId;
      return modelingEntityApi.page<ModelingRecord>(projectCode, definition.key, params);
    },
    enabled: Boolean(projectCode),
  });

  const relationLabel = (key: string, value: unknown): string => {
    if (value === undefined || value === null || value === "") return "-";
    const source = RELATION_FIELDS[key];
    if (!source) return String(value);
    const option = (optionsByResource[source] || []).find((item) => String(item.id) === String(value));
    return option ? recordName(option) : String(value);
  };

  const columns: TableColumns<ModelingRecord> = [
    { key: "sequence", title: "序号", width: 64, align: "center", render: (_value, _record, index) => String((pageNo - 1) * pageSize + index + 1).padStart(2, "0") },
    ...definition.columns.map((column) => ({
      key: column.key,
      title: column.title,
      dataIndex: column.key,
      width: column.width,
      ellipsis: true,
      render: (value: unknown, record: ModelingRecord) => {
        if (column.key === "cnName") {
          return (
            <div className="min-w-0 leading-5">
              <div className="truncate text-xs text-[var(--yak-components-panel-text)]">{String(record.enName || "-")}</div>
              <div className="truncate text-xs font-medium text-[var(--yak-components-panel-text)]">{String(record.cnName || "-")}</div>
            </div>
          );
        }
        if (column.key === "submit") {
          const submitted = record.submit === true || record.submit === "true";
          return (
            <span className={submitted
              ? "inline-flex rounded bg-[var(--yak-color-hover)] px-2 py-1 text-[11px] text-[var(--yak-components-panel-text)]"
              : "inline-flex rounded bg-[var(--yak-color-hover)] px-2 py-1 text-[11px] text-[var(--yak-components-muted-text)]"}>
              {submitted ? "已提交" : "草稿"}
            </span>
          );
        }
        if (column.key === "dwType") return DW_TYPE_OPTIONS.find((option) => option.value === String(value))?.label || "-";
        if (column.key === "computeMode") return String(value) === "1" ? "同环比" : "计算表达式";
        if (column.key === "modifierType") return String(value) === "DIMENSION" ? "维度枚举" : "普通业务";
        return relationLabel(column.key, value);
      },
    })),
    { key: "updateTime", title: "最近修改时间", width: 170, render: (_value, record) => String(record.updateTime || "-") },
    {
      key: "actions",
      title: "操作",
      width: 132,
      align: "center",
      render: (_value, record) => (
        <div className="flex items-center justify-center gap-1">
          <Button size="sm" variant="ghost" className="px-1 text-xs" onClick={() => onEdit(record)}>编辑</Button>
          <span className="h-3 w-px bg-[var(--yak-components-control-border)]" />
          <Button size="sm" variant="ghost" className="px-1 text-xs text-[var(--yak-components-danger-text)]" onClick={() => onDelete(record)}>删除</Button>
        </div>
      ),
    },
  ];

  const applySearch = () => {
    setPageNo(1);
    setApplied({ keyword, status, businessTypeId, domainId, categoryId });
  };

  const resetSearch = () => {
    setKeyword("");
    setStatus("all");
    setBusinessTypeId("");
    setDomainId("");
    setCategoryId("");
    setPageNo(1);
    setApplied({ keyword: "", status: "all", businessTypeId: "", domainId: "", categoryId: "" });
  };

  const filterOptions = (resource: ModelingResourceKey, labelField?: string): IndicatorFieldOption[] =>
    (optionsByResource[resource] || []).map((record) => ({
      value: String(record.id || ""),
      label: String(record[labelField || "cnName"] || recordName(record)),
    })).filter((option) => option.value);

  return (
    <div className="flex h-full min-h-0 w-full min-w-0 flex-1 flex-col bg-white p-4">
      <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-[var(--yak-components-panel-border)] pb-3">
        <div className="relative min-w-[220px] flex-1">
          <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
          <Input
            size="small"
            variant="outlined"
            value={keyword}
            className="pl-8"
            placeholder={"搜索" + definition.title + "名称"}
            onChange={(event) => setKeyword(event.target.value)}
            onKeyDown={(event) => { if (event.key === "Enter") applySearch(); }}
          />
        </div>
        <div className="w-32">
          <Select size="small" value={status} onValueChange={(value) => setStatus(value ?? "all")}>
            <SelectTrigger variant="outlined"><SelectValue placeholder="状态" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all"><SelectItemText>全部状态</SelectItemText><SelectItemIndicator /></SelectItem>
              <SelectItem value="true"><SelectItemText>已提交</SelectItemText><SelectItemIndicator /></SelectItem>
              <SelectItem value="false"><SelectItemText>草稿</SelectItemText><SelectItemIndicator /></SelectItem>
            </SelectContent>
          </Select>
        </div>
        {definition.key === "timePeriod" ? (
          <div className="w-40">
            <Select value={categoryId || "all"} size="small" onValueChange={(value) => setCategoryId(value === "all" ? "" : String(value))}>
              <SelectTrigger variant="outlined"><SelectValue placeholder="周期粒度" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all"><SelectItemText>全部周期</SelectItemText></SelectItem>
                {filterOptions("timePeriodCategory").map((option) => (
                  <SelectItem key={option.value} value={option.value}><SelectItemText>{option.label}</SelectItemText></SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        ) : (
          <>
            <div className="w-36">
              <Select value={businessTypeId || "all"} size="small" onValueChange={(value) => setBusinessTypeId(value === "all" ? "" : String(value))}>
                <SelectTrigger variant="outlined"><SelectValue placeholder="业务分类" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="all"><SelectItemText>全部业务分类</SelectItemText></SelectItem>
                  {filterOptions("businessType", "businessName").map((option) => (
                    <SelectItem key={option.value} value={option.value}><SelectItemText>{option.label}</SelectItemText></SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="w-36">
              <Select value={domainId || "all"} size="small" onValueChange={(value) => setDomainId(value === "all" ? "" : String(value))}>
                <SelectTrigger variant="outlined"><SelectValue placeholder="数据域" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="all"><SelectItemText>全部数据域</SelectItemText></SelectItem>
                  {filterOptions("dataDomain").map((option) => (
                    <SelectItem key={option.value} value={option.value}><SelectItemText>{option.label}</SelectItemText></SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </>
        )}
        <Button size="sm" variant="primary" onClick={applySearch}><Search size={13} />查询</Button>
        <Button size="sm" variant="secondary" onClick={resetSearch}>重置</Button>
        <Button size="sm" variant="secondary" isDisabled={pageQuery.isFetching} onClick={() => void pageQuery.refetch()}>
          <RefreshCw size={13} className={pageQuery.isFetching ? "animate-spin" : ""} />刷新
        </Button>
      </div>

      {pageQuery.isError ? (
        <div className="mt-3 flex items-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
          列表加载失败，请检查建模服务和当前工作空间权限。
          <Button size="sm" variant="secondary" onClick={() => void pageQuery.refetch()}>重试</Button>
        </div>
      ) : null}

      <div className="mt-3 min-h-0 flex-1">
        <Table<ModelingRecord>
          columns={columns}
          dataSource={pageQuery.data?.totalList || []}
          rowKey={(record) => record.id || JSON.stringify(record)}
          loading={pageQuery.isPending || pageQuery.isFetching}
          bordered
          size="small"
          scroll={{ x: 1100, y: "calc(100vh - 300px)" }}
          emptyText="暂无数据"
          pagination={pageQuery.data?.total ? {
            current: pageNo,
            pageSize,
            total: pageQuery.data.total,
            pageSizeOptions: PAGE_SIZE_OPTIONS,
            pageSizeLabel: "每页显示：",
            showSizeChanger: true,
            showQuickJumper: true,
            disabled: pageQuery.isFetching,
            onChange: (page, size) => { setPageNo(page); setPageSize(size); },
          } : false}
        />
      </div>
      <div className="mt-2 shrink-0 text-[11px] text-[var(--yak-components-muted-text)]">{definition.description}</div>
    </div>
  );
}

type IndicatorEditorProps = {
  projectCode: string;
  definition: IndicatorDefinition;
  tab: IndicatorTab;
  optionsByResource: Partial<Record<ModelingResourceKey, ModelingRecord[]>>;
  optionsLoading: boolean;
  onSaved: (kind: IndicatorKind, tabKey: string, record: ModelingRecord) => void;
  onDraftChange: (kind: IndicatorKind, tabKey: string, values: Record<string, string>) => void;
};

function IndicatorEditor({
  projectCode,
  definition,
  tab,
  optionsByResource,
  optionsLoading,
  onSaved,
  onDraftChange,
}: IndicatorEditorProps) {
  const queryClient = useQueryClient();
  const [values, setValues] = useState<Record<string, string>>(() =>
    tab.draftValues || initialFormValues(definition.key, tab.record, definition.fields),
  );
  const [dirty, setDirty] = useState(Boolean(tab.dirty));
  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [versionOpen, setVersionOpen] = useState(false);
  const recordId = tab.recordId || (tab.record?.id ? String(tab.record.id) : "");

  useEffect(() => {
    setValues(tab.draftValues || initialFormValues(definition.key, tab.record, definition.fields));
    setDirty(Boolean(tab.dirty));
    setError("");
  }, [tab.key, tab.record, definition]);

  const versionQuery = useQuery<ModelingVersionRecord[]>({
    queryKey: ["modeling", "indicator-versions", projectCode, definition.key, recordId],
    queryFn: () => recordId ? modelingEntityApi.versions(projectCode, definition.key, recordId) : Promise.resolve([]),
    enabled: Boolean(projectCode && recordId && versionOpen),
  });

  const changeValue = (key: string, value: string) => {
    const nextValues = { ...values, [key]: value };
    setValues(nextValues);
    setDirty(true);
    onDraftChange(tab.kind, tab.key, nextValues);
    setError("");
  };

  const save = async () => {
    if (saving) return;
    const missing = definition.fields.find((field) => field.required && !String(values[field.key] || "").trim());
    if (missing) {
      setError("请填写" + missing.label);
      return;
    }
    const payload: Record<string, unknown> = {};
    definition.fields.forEach((field) => {
      const value = String(values[field.key] ?? "").trim();
      if (field.key === "decimalNumber" && value !== "") payload[field.key] = Number(value);
      else if (field.key === "removeDuplicates" && value !== "") payload[field.key] = value === "true";
      else payload[field.key] = value;
    });
    if (recordId) payload.id = recordId;

    setSaving(true);
    try {
      let saved: ModelingRecord;
      if (recordId) {
        saved = await modelingEntityApi.update(projectCode, definition.key, payload);
      } else {
        const createdId = await modelingEntityApi.create(projectCode, definition.key, payload);
        saved = await modelingEntityApi.getById<ModelingRecord>(projectCode, definition.key, String(createdId));
      }
      onSaved(tab.kind, tab.key, saved);
      setDirty(false);
      setError("");
      toast.success(definition.title + "已保存");
      await queryClient.invalidateQueries({ queryKey: ["modeling", "indicator-versions", projectCode, definition.key] });
    } catch {
      toast.warning("保存失败，请检查填写内容后重试");
    } finally {
      setSaving(false);
    }
  };

  const submit = async () => {
    if (!recordId || dirty || submitting) return;
    setSubmitting(true);
    try {
      const submitted = await modelingEntityApi.submit(projectCode, definition.key, recordId);
      onSaved(tab.kind, tab.key, submitted);
      toast.success("已提交，版本快照已保存");
      await queryClient.invalidateQueries({ queryKey: ["modeling", "indicator-versions", projectCode, definition.key, recordId] });
    } catch {
      toast.warning("提交失败，请检查指标信息是否完整");
    } finally {
      setSubmitting(false);
    }
  };

  const rollback = async (version: ModelingVersionRecord) => {
    if (!recordId) return;
    try {
      const restored = await modelingEntityApi.rollback(projectCode, definition.key, recordId, version.versionNumber);
      onSaved(tab.kind, tab.key, restored);
      setValues(Object.fromEntries(definition.fields.map((field) => [field.key, readFormValue(restored, field.key)])));
      setDirty(false);
      setVersionOpen(false);
      toast.success("已回滚到 V" + version.versionNumber);
    } catch {
      toast.warning("版本回滚失败，请刷新后重试");
    }
  };

  const sectionNames = Array.from(new Set(definition.fields.map((field) => field.section || "基本信息")));
  const currentVersion = tab.record?.currentVersion ? "V" + String(tab.record.currentVersion) : "暂无";
  const isSubmitted = tab.record?.submit === true || tab.record?.submit === "true";

  return (
    <div className="flex h-full min-h-0 w-full min-w-0 flex-1 bg-white">
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex shrink-0 items-center justify-between gap-3 border-b border-[var(--yak-components-panel-border)] px-4 py-2.5">
          <div className="flex items-center gap-2">
            <Button size="sm" variant="primary" isDisabled={!dirty || saving} isPending={saving} onClick={() => void save()}>
              <Save size={13} />保存
            </Button>
            <Button size="sm" variant="secondary" isDisabled={!recordId || dirty || isSubmitted || submitting} isPending={submitting} onClick={() => void submit()}>
              <Send size={13} />提交
            </Button>
          </div>
          <div className="flex items-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
            <span className={isSubmitted ? "size-1.5 rounded-full bg-[var(--yak-color-success)]" : "size-1.5 rounded-full bg-[var(--yak-components-muted-text)]"} />
            <span>当前版本：{currentVersion}</span>
            <span className="mx-1 h-3 w-px bg-[var(--yak-components-control-border)]" />
            <span>{isSubmitted ? "已提交" : recordId ? "草稿 / 待提交" : "新建草稿"}</span>
          </div>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto bg-[var(--yak-color-hover)] p-4">
          <div className="mx-auto w-full max-w-[1120px] space-y-4">
            {dirty ? (
              <div className="flex items-center gap-2 rounded-md border border-[var(--yak-components-panel-border)] bg-white px-3 py-2 text-xs text-[var(--yak-components-muted-text)]">
                <CircleDot size={13} />当前存在未保存的变更，请及时保存。
              </div>
            ) : recordId && !isSubmitted ? (
              <div className="flex items-center gap-2 rounded-md border border-[var(--yak-components-panel-border)] bg-white px-3 py-2 text-xs text-[var(--yak-components-muted-text)]">
                <CircleDot size={13} />当前存在未提交的内容，保存后可点击上方“提交”生成最新版本。
              </div>
            ) : null}
            {sectionNames.map((section) => (
              <section key={section} className="rounded-lg border border-[var(--yak-components-panel-border)] bg-white p-4">
                <div className="mb-4 flex items-center gap-2 border-b border-[var(--yak-components-panel-border)] pb-2">
                  <span className="size-1.5 rounded-full bg-[var(--yak-color-primary)]" />
                  <h3 className="text-xs font-semibold text-[var(--yak-components-panel-text)]">{section}</h3>
                </div>
                <div className="grid grid-cols-1 gap-x-8 gap-y-4 lg:grid-cols-2">
                  {definition.fields.filter((field) => (field.section || "基本信息") === section).map((field) => {
                    const options = fieldOptions(field, optionsByResource);
                    const isSelect = Boolean(field.options || field.optionsSource);
                    const disabled = Boolean(field.readOnly && recordId);
                    return (
                      <Field key={field.key} className={field.multiline ? "grid grid-cols-[112px_minmax(0,1fr)] items-start gap-3 lg:col-span-2" : "grid grid-cols-[112px_minmax(0,1fr)] items-start gap-3"}>
                        <FieldLabel required={field.required} className="pt-1.5 text-xs leading-4">{field.label}</FieldLabel>
                        <div className="min-w-0">
                          {isSelect ? (
                            <Select
                              size="small"
                              value={values[field.key] || undefined}
                              disabled={disabled}
                              onValueChange={(value) => changeValue(field.key, String(value || ""))}
                            >
                              <SelectTrigger variant="outlined">
                                <SelectValue placeholder={optionsLoading ? "正在加载选项..." : field.placeholder || ("请选择" + field.label)} />
                              </SelectTrigger>
                              <SelectContent>
                                {options.map((option) => (
                                  <SelectItem key={option.value} value={option.value}>
                                    <SelectItemText>{option.label}</SelectItemText><SelectItemIndicator />
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          ) : field.multiline ? (
                            <Textarea
                              size="small"
                              variant="outlined"
                              rows={3}
                              value={values[field.key] || ""}
                              disabled={disabled}
                              placeholder={field.placeholder || ("请输入" + field.label)}
                              onValueChange={(value) => changeValue(field.key, String(value || ""))}
                            />
                          ) : (
                            <Input
                              size="small"
                              variant="outlined"
                              type={field.key === "decimalNumber" ? "number" : "text"}
                              min={field.key === "decimalNumber" ? 0 : undefined}
                              value={values[field.key] || ""}
                              disabled={disabled}
                              placeholder={field.placeholder || ("请输入" + field.label)}
                              onChange={(event) => changeValue(field.key, event.target.value)}
                            />
                          )}
                        </div>
                      </Field>
                    );
                  })}
                </div>
              </section>
            ))}
            {error ? <p role="alert" className="text-xs text-[var(--yak-components-danger-text)]">{error}</p> : null}
          </div>
        </div>
      </div>

      <div className="flex shrink-0">
        <div className="flex w-9 flex-col items-center border-l border-[var(--yak-components-panel-border)] bg-white pt-3">
          <button
            type="button"
            title="版本管理"
            disabled={!recordId}
            onClick={() => setVersionOpen((open) => !open)}
            className={[
              "flex min-h-24 cursor-pointer items-center justify-center rounded-md px-2 py-3 text-xs [writing-mode:vertical-rl]",
              versionOpen ? "bg-[var(--yak-color-hover)] text-[var(--yak-color-primary)]" : "text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)]",
              !recordId ? "cursor-not-allowed opacity-40" : "",
            ].join(" ")}
          >
            <History size={14} className="mb-2" />版本管理
          </button>
        </div>
        {versionOpen ? (
          <aside className="flex w-[330px] shrink-0 flex-col border-l border-[var(--yak-components-panel-border)] bg-white">
            <div className="flex h-10 shrink-0 items-center justify-between border-b border-[var(--yak-components-panel-border)] px-3">
              <span className="text-xs font-semibold">版本管理</span>
              <Button size="sm" variant="ghost" className="!h-6 !w-6 !p-0" onClick={() => setVersionOpen(false)}><X size={13} /></Button>
            </div>
            <div className="min-h-0 flex-1 p-3">
              <Table<ModelingVersionRecord>
                columns={[
                  { key: "versionNumber", title: "版本", width: 70, render: (_value, record) => "V" + record.versionNumber },
                  { key: "updateTime", title: "提交时间", width: 150, render: (_value, record) => record.updateTime || "-" },
                  { key: "updateBy", title: "提交人", render: (_value, record) => record.updateBy || "-" },
                  { key: "actions", title: "操作", width: 72, align: "center", render: (_value, record) => (
                    <Button size="sm" variant="ghost" className="px-1 text-xs" onClick={() => void rollback(record)}>回滚</Button>
                  ) },
                ]}
                dataSource={versionQuery.data || []}
                rowKey={(record) => record.id}
                loading={versionQuery.isFetching}
                emptyText="暂无已提交版本"
                pagination={false}
                size="small"
                scroll={{ y: "calc(100vh - 220px)" }}
              />
            </div>
          </aside>
        ) : null}
      </div>
    </div>
  );
}


type IndicatorTabContentProps = {
  projectCode: string;
  tab: IndicatorTab;
  dwType: string;
  onEdit: (kind: IndicatorKind, record: ModelingRecord) => void;
  onDelete: (kind: IndicatorKind, record: ModelingRecord) => void;
  onSaved: (kind: IndicatorKind, tabKey: string, record: ModelingRecord) => void;
  onDraftChange: (kind: IndicatorKind, tabKey: string, values: Record<string, string>) => void;
};

function IndicatorTabContent({
  projectCode,
  tab,
  dwType,
  onEdit,
  onDelete,
  onSaved,
  onDraftChange,
}: IndicatorTabContentProps) {
  const definition = getDefinition(tab.kind);
  const optionResources = useMemo(() => {
    const resources = new Set<ModelingResourceKey>(["businessType", "dataDomain"]);
    definition.fields.forEach((field) => {
      if (field.optionsSource) resources.add(field.optionsSource);
    });
    return Array.from(resources);
  }, [definition]);

  const optionsQuery = useQuery<Partial<Record<ModelingResourceKey, ModelingRecord[]>>>({
    queryKey: ["modeling", "indicator-options", projectCode, tab.kind],
    queryFn: async () => {
      const entries = await Promise.all(optionResources.map(async (resource) => [
        resource,
        await modelingEntityApi.all<ModelingRecord>(projectCode, resource),
      ] as const));
      return Object.fromEntries(entries) as Partial<Record<ModelingResourceKey, ModelingRecord[]>>;
    },
    enabled: Boolean(projectCode),
  });
  const optionsByResource = optionsQuery.data || {};

  if (tab.mode === "list") {
    return (
      <IndicatorListTab
        projectCode={projectCode}
        definition={definition}
        dwType={dwType}
        optionsByResource={optionsByResource}
        onEdit={(record) => onEdit(tab.kind, record)}
        onDelete={(record) => onDelete(tab.kind, record)}
      />
    );
  }

  return (
    <IndicatorEditor
      projectCode={projectCode}
      definition={definition}
      tab={tab}
      optionsByResource={optionsByResource}
      optionsLoading={optionsQuery.isFetching}
      onSaved={onSaved}
      onDraftChange={onDraftChange}
    />
  );
}

type DeleteTarget = { kind: IndicatorKind; record: ModelingRecord };
type ContextMenuState = { x: number; y: number; record: ModelingRecord } | null;
type TabContextMenuState = { tabKey: string; x: number; y: number };
type PendingTabClose = { keys: string[]; dirtyTitles: string[] };

type DataIndicatorsWorkbenchProps = { projectCode: string; activeKind: IndicatorKind };

export function DataIndicatorsWorkbench({ projectCode, activeKind }: DataIndicatorsWorkbenchProps) {
  const queryClient = useQueryClient();
  const [groupBy, setGroupBy] = useState<"BUSINESS_TYPE" | "DATA_DOMAIN">("BUSINESS_TYPE");
  const [dwType, setDwType] = useState("PUBLIC_LAYER");
  const [treeKeyword, setTreeKeyword] = useState("");
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
  const [selectedTreeKey, setSelectedTreeKey] = useState("");
  const [tabs, setTabs] = useState<IndicatorTab[]>([]);
  const [activeTabKey, setActiveTabKey] = useState("");
  const [contextMenu, setContextMenu] = useState<ContextMenuState>(null);
  const [tabContextMenu, setTabContextMenu] = useState<TabContextMenuState | null>(null);
  const [contextMenuPortalRoot, setContextMenuPortalRoot] = useState<HTMLElement | null>(null);
  const [pendingClose, setPendingClose] = useState<PendingTabClose | null>(null);
  const [pendingDelete, setPendingDelete] = useState<DeleteTarget | null>(null);
  const [openingId, setOpeningId] = useState("");

  const definition = getDefinition(activeKind);
  const activeTab = tabs.find((tab) => tab.key === activeTabKey) || null;
  const contextTab = tabs.find((tab) => tab.key === tabContextMenu?.tabKey) || null;
  const contextTabIndex = contextTab ? tabs.findIndex((tab) => tab.key === contextTab.key) : -1;

  useEffect(() => {
    setContextMenuPortalRoot(document.body);
  }, []);

  const treeQuery = useQuery<IndicatorTreeNode[]>({
    queryKey: ["modeling", "indicator-tree", projectCode, activeKind, groupBy, activeKind === "atomIndicator" ? dwType : "all"],
    queryFn: async () => {
      if (activeKind === "timePeriod") {
        const results = await Promise.all([
          modelingEntityApi.all<ModelingRecord>(projectCode, "timePeriodCategory"),
          modelingEntityApi.all<ModelingRecord>(projectCode, "timePeriod"),
        ]);
        return buildTimePeriodTree(results[0], results[1]);
      }
      if (activeKind === "atomIndicator") {
        const [businessTypes, dataDomains, businessProcesses, atomIndicators] = await Promise.all([
          modelingEntityApi.all<ModelingRecord>(projectCode, "businessType"),
          modelingEntityApi.all<ModelingRecord>(projectCode, "dataDomain"),
          modelingEntityApi.all<ModelingRecord>(projectCode, "businessProcess"),
          modelingEntityApi.all<ModelingRecord>(projectCode, "atomIndicator", { dwType }),
        ]);
        const relatedDomainMap = groupBy === "BUSINESS_TYPE"
          ? await modelingBusinessTypeApi.allRelatedDomainIds(projectCode)
          : {};
        return buildAtomIndicatorTree(
          businessTypes,
          dataDomains,
          businessProcesses,
          atomIndicators,
          groupBy,
          new Map(Object.entries(relatedDomainMap)),
        );
      }
      const groupResource: ModelingResourceKey = groupBy === "BUSINESS_TYPE" ? "businessType" : "dataDomain";
      const results = await Promise.all([
        modelingEntityApi.all<ModelingRecord>(projectCode, groupResource),
        modelingEntityApi.all<ModelingRecord>(projectCode, activeKind),
      ]);
      return buildGroupedTree(results[0], results[1], groupBy);
    },
    enabled: Boolean(projectCode),
  });

  const visibleTree = useMemo(
    () => filterTree(treeQuery.data || [], treeKeyword),
    [treeQuery.data, treeKeyword],
  );
  const visibleExpandedKeys = useMemo(() => {
    const next = new Set(expandedKeys);
    if (treeKeyword.trim()) {
      collectExpandedKeys(visibleTree).forEach((key) => next.add(key));
    }
    return next;
  }, [expandedKeys, treeKeyword, visibleTree]);

  useEffect(() => {
    if (activeKind === "atomIndicator") {
      const root = (treeQuery.data || []).find((node) => node.nodeType === "root");
      setExpandedKeys(new Set(root ? [root.key] : []));
      return;
    }
    setExpandedKeys(new Set(collectExpandedKeys(treeQuery.data || [])));
  }, [treeQuery.data, activeKind]);

  useEffect(() => {
    setTreeKeyword("");
    setSelectedTreeKey("");
    setContextMenu(null);
  }, [activeKind, groupBy, dwType]);

  const openTab = (kind: IndicatorKind, mode: IndicatorMode, record?: ModelingRecord) => {
    const existing = tabs.find((tab) => (
      tab.kind === kind && (
        mode === "list"
          ? tab.mode === "list"
          : Boolean(record?.id) && tab.recordId === String(record?.id)
      )
    ));
    if (existing) {
      setActiveTabKey(existing.key);
      return;
    }

    const key = kind + "-" + mode + "-" + String(Date.now());
    const label = mode === "create"
      ? "新建" + getDefinition(kind).title
      : mode === "list"
        ? getDefinition(kind).title + "列表"
        : String(record?.cnName || record?.enShortName || getDefinition(kind).title);
    const tab: IndicatorTab = {
      key,
      kind,
      mode,
      label,
      recordId: record?.id ? String(record.id) : undefined,
      record,
      draftValues: mode === "create" && kind === "atomIndicator"
        ? { ...initialFormValues(kind, undefined, getDefinition(kind).fields), dwType }
        : undefined,
    };
    setTabs((current) => [...current, tab]);
    setActiveTabKey(key);
  };

  const openEdit = async (kind: IndicatorKind, record: ModelingRecord) => {
    if (!record.id) return;
    const id = String(record.id);
    const existing = tabs.find((tab) => tab.kind === kind && tab.recordId === id);
    if (existing) {
      setActiveTabKey(existing.key);
      if (kind === activeKind) setSelectedTreeKey("asset:" + id);
      return;
    }
    const openingKey = kind + ":" + id;
    if (openingId === openingKey) return;
    setOpeningId(openingKey);
    try {
      const detail = await modelingEntityApi.getById<ModelingRecord>(projectCode, kind, id);
      openTab(kind, "edit", detail);
      if (kind === activeKind) setSelectedTreeKey("asset:" + id);
    } catch {
      toast.warning("获取详情失败，请刷新列表后重试");
    } finally {
      setOpeningId("");
    }
  };

  const closeTabKeys = (keys: string[]) => {
    const closingKeys = new Set(keys);
    if (!closingKeys.size) return;
    const closingIndex = tabs.findIndex((tab) => closingKeys.has(tab.key));
    if (closingIndex < 0) return;
    const nextTabs = tabs.filter((tab) => !closingKeys.has(tab.key));
    setTabs(nextTabs);
    if (activeTabKey && closingKeys.has(activeTabKey)) {
      setActiveTabKey(nextTabs[Math.min(closingIndex, nextTabs.length - 1)]?.key || "");
    }
  };

  const requestCloseTabs = (keys: string[]) => {
    const closingKeys = Array.from(new Set(keys));
    if (!closingKeys.length) return;
    const dirtyTitles = tabs
      .filter((tab) => closingKeys.includes(tab.key) && tab.dirty)
      .map((tab) => tab.label);
    if (dirtyTitles.length) {
      setPendingClose({ keys: closingKeys, dirtyTitles });
      return;
    }
    closeTabKeys(closingKeys);
  };

  const getTabKeysToClose = (
    anchorKey: string,
    action: "current" | "other" | "left" | "right" | "all",
  ) => {
    const index = tabs.findIndex((tab) => tab.key === anchorKey);
    if (action === "all") return tabs.map((tab) => tab.key);
    if (index < 0) return [];
    if (action === "current") return [anchorKey];
    if (action === "other") return tabs.filter((tab) => tab.key !== anchorKey).map((tab) => tab.key);
    if (action === "left") return tabs.slice(0, index).map((tab) => tab.key);
    return tabs.slice(index + 1).map((tab) => tab.key);
  };

  const confirmPendingClose = () => {
    if (!pendingClose) return;
    closeTabKeys(pendingClose.keys);
    setPendingClose(null);
  };

  const onDraftChange = (kind: IndicatorKind, tabKey: string, values: Record<string, string>) => {
    setTabs((current) => current.map((tab) => (
      tab.kind === kind && tab.key === tabKey
        ? { ...tab, draftValues: values, dirty: true }
        : tab
    )));
  };

  const onSaved = (kind: IndicatorKind, tabKey: string, record: ModelingRecord) => {
    setTabs((current) => current.map((tab) => (
      tab.kind === kind && tab.key === tabKey
        ? {
            ...tab,
            mode: "edit",
            recordId: record.id ? String(record.id) : tab.recordId,
            record,
            draftValues: undefined,
            dirty: false,
            label: String(record.cnName || record.enShortName || tab.label),
          }
        : tab
    )));
    void queryClient.invalidateQueries({ queryKey: ["modeling", "indicator-tree", projectCode] });
    void queryClient.invalidateQueries({ queryKey: ["modeling", "indicator-page", projectCode, kind] });
  };

  const deleteRecord = async () => {
    if (!pendingDelete?.record.id) return;
    try {
      await modelingEntityApi.remove(projectCode, pendingDelete.kind, String(pendingDelete.record.id));
      toast.success(getDefinition(pendingDelete.kind).title + "已删除");
      const deletedId = String(pendingDelete.record.id);
      const removedKeys = tabs
        .filter((tab) => tab.kind === pendingDelete.kind && tab.recordId === deletedId)
        .map((tab) => tab.key);
      const nextTabs = tabs.filter((tab) => !removedKeys.includes(tab.key));
      setTabs(nextTabs);
      if (removedKeys.includes(activeTabKey)) {
        const activeIndex = tabs.findIndex((tab) => tab.key === activeTabKey);
        setActiveTabKey(nextTabs[Math.max(0, activeIndex - 1)]?.key || nextTabs[0]?.key || "");
      }
      setPendingDelete(null);
      void queryClient.invalidateQueries({ queryKey: ["modeling", "indicator-tree", projectCode] });
      void queryClient.invalidateQueries({ queryKey: ["modeling", "indicator-page", projectCode, pendingDelete.kind] });
    } catch {
      toast.warning("删除失败，请确认该指标没有被其他建模对象引用");
    }
  };

  const onTreeContextMenu = (event: ReactMouseEvent<HTMLButtonElement>, node: IndicatorTreeNode) => {
    if (!node.record) return;
    event.preventDefault();
    setContextMenu({
      x: Math.min(event.clientX, window.innerWidth - 180),
      y: Math.min(event.clientY, window.innerHeight - 90),
      record: node.record,
    });
  };

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <div className="flex min-h-0 flex-1 overflow-hidden bg-white">
        <aside className="flex w-[292px] min-w-[260px] max-w-[360px] shrink-0 flex-col border-r border-[var(--yak-components-panel-border)] bg-white">
          <div className="flex shrink-0 items-center justify-between px-3 pb-2 pt-3">
            <div className="flex items-center gap-2">
              {activeKind === "timePeriod" ? <CalendarDays size={15} className="text-[var(--yak-components-muted-text)]" /> : activeKind === "atomIndicator" ? <ChartNoAxesCombined size={15} className="text-[var(--yak-components-muted-text)]" /> : <Folder size={15} className="text-[var(--yak-components-muted-text)]" />}
              <span className="text-sm font-semibold">{definition.title}</span>
            </div>
            <div className="flex items-center gap-0.5">
              <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title={"新建" + definition.title} onClick={() => openTab(activeKind, "create")}>
                <Plus size={14} />
              </Button>
              <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title="列表模式" onClick={() => openTab(activeKind, "list")}>
                <List size={14} />
              </Button>
              <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title="刷新" isDisabled={treeQuery.isFetching} onClick={() => void treeQuery.refetch()}>
                <RefreshCw size={13} className={treeQuery.isFetching ? "animate-spin" : ""} />
              </Button>
            </div>
          </div>

          {activeKind === "atomIndicator" ? (
            <div className="px-3 pb-2">
              <div className="flex items-center gap-2">
                <span className="shrink-0 text-[11px] text-[var(--yak-components-muted-text)]">数仓分层</span>
                <Select
                  size="small"
                  value={dwType}
                  onValueChange={(value) => setDwType(String(value || "PUBLIC_LAYER"))}
                >
                  <SelectTrigger variant="outlined" className="min-w-0 flex-1">
                    <SelectValue placeholder="请选择数仓分层" />
                  </SelectTrigger>
                  <SelectContent>
                    {DW_TYPE_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        <SelectItemText>{option.label}</SelectItemText><SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          ) : null}

          {activeKind !== "timePeriod" ? (
            <div className="px-3 pb-2">
              <Segmented
                className="w-full"
                value={groupBy}
                options={[
                  { value: "BUSINESS_TYPE", label: "业务分类" },
                  { value: "DATA_DOMAIN", label: "数据域" },
                ]}
                onValueChange={(value) => setGroupBy(value as "BUSINESS_TYPE" | "DATA_DOMAIN")}
              />
            </div>
          ) : null}

          <div className="shrink-0 px-3 pb-2">
            <div className="relative">
              <Search size={13} className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
              <Input
                size="small"
                variant="outlined"
                className="pl-8"
                value={treeKeyword}
                placeholder={activeKind === "atomIndicator" ? "搜索业务分类、数据域、业务过程或指标" : "搜索目录或指标"}
                onChange={(event) => setTreeKeyword(event.target.value)}
              />
            </div>
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
            {treeQuery.isPending || treeQuery.isFetching ? (
              <div className="flex items-center gap-2 px-2 py-4 text-xs text-[var(--yak-components-muted-text)]">
                <RefreshCw size={13} className="animate-spin" />正在加载目录...
              </div>
            ) : treeQuery.isError ? (
              <div className="px-2 py-4 text-xs text-[var(--yak-components-muted-text)]">
                目录加载失败。
                <Button size="sm" variant="secondary" className="ml-2" onClick={() => void treeQuery.refetch()}>重试</Button>
              </div>
            ) : visibleTree.length ? (
              <IndicatorTreeRows
                nodes={visibleTree}
                depth={0}
                expandedKeys={visibleExpandedKeys}
                selectedKey={selectedTreeKey}
                onToggle={(key) => {
                  setSelectedTreeKey(key);
                  setExpandedKeys((current) => {
                    const next = new Set(current);
                    if (next.has(key)) next.delete(key);
                    else next.add(key);
                    return next;
                  });
                }}
                onSelect={(record) => void openEdit(activeKind, record)}
                onContextMenu={onTreeContextMenu}
              />
            ) : (
              <div className="px-2 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
                {treeKeyword ? "没有匹配的指标" : "暂无指标，点击上方 + 新建"}
              </div>
            )}
          </div>
        </aside>

        <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[var(--yak-color-hover)]">
          {tabs.length ? (
            <div className="flex h-[35px] min-w-0 shrink-0 items-center gap-1 overflow-hidden border-b border-[#e2e2e2] bg-[#f2f2f2] px-2">
              <div
                role="tablist"
                aria-label="指标工作区选项卡"
                className="flex h-full min-w-0 flex-1 items-center gap-1 overflow-x-auto overflow-y-hidden [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden"
              >
                {tabs.map((tab) => {
                  const active = tab.key === activeTabKey;
                  return (
                    <div
                      key={tab.key}
                      onContextMenu={(event) => {
                        event.preventDefault();
                        event.stopPropagation();
                        setTabContextMenu({
                          tabKey: tab.key,
                          x: event.clientX,
                          y: event.clientY,
                        });
                      }}
                      className={[
                        "group relative flex h-[27px] w-fit min-w-[120px] max-w-[240px] shrink-0 items-center rounded-[3px]",
                        active
                          ? "bg-[#cedaff] text-[#26282c]"
                          : "bg-transparent text-[#616161] hover:bg-[#e8e8e8]",
                      ].join(" ")}
                    >
                      <button
                        type="button"
                        role="tab"
                        aria-selected={active}
                        tabIndex={active ? 0 : -1}
                        title={tab.dirty ? tab.label + "（有未保存修改）" : tab.label}
                        onClick={() => setActiveTabKey(tab.key)}
                        className="flex h-full min-w-0 flex-1 cursor-pointer items-center gap-2 pl-2 pr-0 text-left text-[13px] leading-none outline-none"
                      >
                        {tab.kind === "timePeriod" ? (
                          <CalendarDays size={16} />
                        ) : tab.kind === "atomIndicator" ? (
                          <ChartNoAxesCombined size={16} />
                        ) : tab.kind === "modifier" ? (
                          <FileText size={16} />
                        ) : (
                          <Folder size={16} />
                        )}
                        <span className="min-w-0 flex-1 truncate whitespace-nowrap">{tab.label}</span>
                        {tab.dirty ? (
                          <span
                            aria-label="有未保存修改"
                            title="有未保存修改"
                            className="mr-1.5 size-1.5 shrink-0 rounded-full bg-[#e6a23c]"
                          />
                        ) : null}
                      </button>
                      <button
                        type="button"
                        aria-label={"关闭 " + tab.label}
                        title={"关闭 " + tab.label}
                        onClick={() => requestCloseTabs([tab.key])}
                        className={[
                          "mr-1 flex size-5 shrink-0 cursor-pointer items-center justify-center rounded-sm text-[#343434] transition-opacity hover:bg-black/5 focus-visible:opacity-100 focus-visible:outline-none",
                          active ? "opacity-100" : "opacity-0 group-hover:opacity-100",
                        ].join(" ")}
                      >
                        <X size={15} strokeWidth={1.8} />
                      </button>
                    </div>
                  );
                })}
              </div>
              <DropdownMenu>
                <DropdownMenuTrigger
                  aria-label="标签页更多操作"
                  title="标签页更多操作"
                  disabled={!activeTab}
                  className="flex h-[27px] w-8 shrink-0 cursor-pointer items-center justify-center rounded-[3px] border-0 bg-transparent p-0 text-[#616161] outline-none transition-colors hover:bg-[#e8e8e8] hover:text-[#26282c] focus-visible:ring-1 focus-visible:ring-[#9bb6ff] disabled:cursor-not-allowed disabled:opacity-40"
                >
                  <Ellipsis size={18} strokeWidth={1.8} />
                </DropdownMenuTrigger>
                <DropdownMenuContent side="bottom" align="end" sideOffset={4} className="w-48">
                  <DropdownMenuItem
                    disabled={!activeTab}
                    onClick={() => requestCloseTabs(getTabKeysToClose(activeTabKey, "current"))}
                  >
                    <X size={14} />关闭当前标签页
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    disabled={!activeTab || tabs.length < 2}
                    onClick={() => requestCloseTabs(getTabKeysToClose(activeTabKey, "other"))}
                  >
                    <XCircle size={14} />关闭其他标签页
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    disabled={!activeTab || getTabKeysToClose(activeTabKey, "left").length === 0}
                    onClick={() => requestCloseTabs(getTabKeysToClose(activeTabKey, "left"))}
                  >
                    <ArrowLeftToLine size={14} />关闭左侧标签页
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    disabled={!activeTab || getTabKeysToClose(activeTabKey, "right").length === 0}
                    onClick={() => requestCloseTabs(getTabKeysToClose(activeTabKey, "right"))}
                  >
                    <ArrowRightToLine size={14} />关闭右侧标签页
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    tone="danger"
                    disabled={tabs.length === 0}
                    onClick={() => requestCloseTabs(getTabKeysToClose(activeTabKey, "all"))}
                  >
                    <XCircle size={14} />关闭全部标签页
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          ) : null}

          <div className="relative min-h-0 flex-1 overflow-hidden">
            {tabs.map((tab) => {
              const isActive = tab.key === activeTabKey;
              return (
                <div
                  key={tab.key}
                  aria-hidden={!isActive}
                  className={["absolute inset-0 min-h-0", isActive ? "flex" : "hidden"].join(" ")}
                >
                  <IndicatorTabContent
                    projectCode={projectCode}
                    tab={tab}
                    dwType={dwType}
                    onEdit={openEdit}
                    onDelete={(kind, record) => setPendingDelete({ kind, record })}
                    onSaved={onSaved}
                    onDraftChange={onDraftChange}
                  />
                </div>
              );
            })}

            {!activeTab ? (
              <div className="absolute inset-0 flex min-h-0 flex-col items-center justify-center bg-white text-center">
                <div className="mb-3 flex size-12 items-center justify-center rounded-xl bg-[var(--yak-color-hover)] text-[var(--yak-color-primary)]">
                  {activeKind === "timePeriod" ? <CalendarDays size={22} /> : <ChartNoAxesCombined size={22} />}
                </div>
                <div className="text-sm font-medium text-[var(--yak-components-panel-text)]">请选择或创建{definition.title}</div>
                <div className="mt-1 text-xs text-[var(--yak-components-muted-text)]">从左侧目录打开指标，或新建一条记录开始维护</div>
                <Button size="sm" variant="primary" className="mt-4" onClick={() => openTab(activeKind, "create")}>
                  <Plus size={14} />新建{definition.title}
                </Button>
              </div>
            ) : null}
          </div>
        </main>
      </div>

      {contextMenu ? (
        <>
          <button type="button" aria-label="关闭右键菜单" className="fixed inset-0 z-40 cursor-default" onClick={() => setContextMenu(null)} />
          <div className="fixed z-50 min-w-32 rounded-lg border border-[var(--yak-components-panel-border)] bg-white p-1 shadow-[var(--yak-components-panel-shadow)]" style={{ left: contextMenu.x, top: contextMenu.y }}>
            <button
              type="button"
              className="flex w-full items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[var(--yak-components-danger-text)] hover:bg-[var(--yak-color-hover)]"
              onClick={() => {
                setPendingDelete({ kind: activeKind, record: contextMenu.record });
                setContextMenu(null);
              }}
            >
              <Trash2 size={13} />删除
            </button>
          </div>
        </>
      ) : null}

      {tabContextMenu && contextMenuPortalRoot && contextTab ? createPortal(
        <DropdownMenu
          open
          onOpenChange={(open) => {
            if (!open) setTabContextMenu(null);
          }}
        >
          <DropdownMenuTrigger
            aria-hidden="true"
            tabIndex={-1}
            className="pointer-events-none fixed size-px opacity-0"
            style={{ left: tabContextMenu.x, top: tabContextMenu.y }}
          />
          <DropdownMenuContent side="bottom" align="start" sideOffset={0} className="w-[208px] rounded-md p-1">
            <DropdownMenuItem onClick={() => requestCloseTabs([contextTab.key])}>
              <X size={14} />关闭
            </DropdownMenuItem>
            <DropdownMenuItem
              disabled={tabs.length < 2}
              onClick={() => requestCloseTabs(getTabKeysToClose(contextTab.key, "other"))}
            >
              <XCircle size={14} />关闭其他标签页
            </DropdownMenuItem>
            <DropdownMenuItem
              disabled={contextTabIndex < 1}
              onClick={() => requestCloseTabs(getTabKeysToClose(contextTab.key, "left"))}
            >
              <ArrowLeftToLine size={14} />关闭左侧标签页
            </DropdownMenuItem>
            <DropdownMenuItem
              disabled={contextTabIndex < 0 || contextTabIndex >= tabs.length - 1}
              onClick={() => requestCloseTabs(getTabKeysToClose(contextTab.key, "right"))}
            >
              <ArrowRightToLine size={14} />关闭右侧标签页
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem tone="danger" onClick={() => requestCloseTabs(getTabKeysToClose(contextTab.key, "all"))}>
              <XCircle size={14} />关闭全部标签页
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>,
        contextMenuPortalRoot,
      ) : null}

      <Dialog
        open={Boolean(pendingClose)}
        onOpenChange={(open) => {
          if (!open) setPendingClose(null);
        }}
      >
        {pendingClose ? (
          <DialogContent className="w-[420px]">
            <DialogTitle className="text-base font-semibold">关闭未保存的标签页</DialogTitle>
            <DialogDescription className="mt-2 text-sm leading-6 text-[var(--yak-components-muted-text)]">
              {pendingClose.dirtyTitles.join("、")}存在未保存的修改。关闭后，这些修改将丢失，确定继续吗？
            </DialogDescription>
            <div className="mt-6 flex justify-end gap-2">
              <Button size="sm" variant="secondary" onClick={() => setPendingClose(null)}>取消</Button>
              <Button size="sm" variant="danger" onClick={confirmPendingClose}>仍然关闭</Button>
            </div>
          </DialogContent>
        ) : null}
      </Dialog>

      <Modal
        open={Boolean(pendingDelete)}
        centered
        width={420}
        title={"删除" + (pendingDelete ? getDefinition(pendingDelete.kind).title : "指标")}
        onClose={() => setPendingDelete(null)}
        footer={
          <>
            <Button size="sm" variant="secondary" onClick={() => setPendingDelete(null)}>取消</Button>
            <Button size="sm" variant="danger" onClick={() => void deleteRecord()}>删除</Button>
          </>
        }
      >
        <p className="text-sm leading-6 text-[var(--yak-components-muted-text)]">
          确定删除“{pendingDelete ? recordName(pendingDelete.record) : ""}”吗？如果该指标已被其他建模对象引用，删除可能会失败。
        </p>
      </Modal>
    </div>
  );
}

export default DataIndicatorsWorkbench;
