import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Cascader,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  Textarea,
  TreeSelect,
  toast,
  type TableColumns,
  type TreeSelectOption,
} from "@yak-ops/yak-ui";
import {
  Download,
  History,
  RefreshCw,
  Save,
  Upload,
  X,
} from "lucide-react";

import { dimensionModelingApi } from "@/service/data-modeling/models/dimension-modeling";
import type {
  DimensionFieldRecord,
  DimensionTableRecord,
  DimensionVersionRecord,
} from "@/service/data-modeling/models/dimension-modeling";
import type { DimensionTab } from "../types";
import { dwLayerApi } from "@/service/data-modeling/planning/dw-layer";
import { businessTypeApi, type BusinessTypeTreeNode } from "@/service/data-modeling/planning/business-type";
import { dataDomainApi } from "@/service/data-modeling/planning/common-layer/data-domain";
import { useBusinessProcessCascader } from "@/app/data-modeling/planning/common-layer/business-process/use-business-process-cascader";
import { dataMartApi } from "@/service/data-modeling/planning/application-layer/data-mart";
import { subjectDomainApi, type SubjectDomainDirectoryNode } from "@/service/data-modeling/planning/application-layer/subject-domain";
import { timePeriodApi } from "@/service/data-modeling/indicators/time-period";
import { modifierApi } from "@/service/data-modeling/indicators/modifier";
import { dimensionReferenceApi } from "@/service/data-modeling/indicators/dimension-reference";
import { fieldStandardApi } from "@/service/data-modeling/standards/field-standard";
import { codeStandardApi } from "@/service/data-modeling/standards/code-standard";
import { unitStandardApi } from "@/service/data-modeling/standards/unit-standard";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import { warehouseEnumCode, warehouseLayerOptions } from "../utils/warehouse-layers";
import { availableDataDomains, defaultDataDomainId } from "../utils/default-data-domain";
import { mapBusinessTypeTreeOption } from "../utils/business-type-tree-options";
import { planningQueryKeys } from "../../../../planning/query-keys";
import { ModelTypeIcon, isModelTypeIconType } from "../../../../components/model-type-icon";
import { PlanningIcon } from "../../../../planning/components/icons";
import { buildApplicationScopeTree } from "../utils/application-scope-tree";
import FieldSpreadsheet from "./field-spreadsheet/field-spreadsheet";
import SourceFieldImportCard from "./source-field-import-card";
import {
  fieldsForSave,
  validateDimensionFields,
} from "./field-spreadsheet/field-grid-utils";

type Option = { value: string; label: string };

type DimensionEditorProps = {
  projectCode: string;
  dwType: string;
  tab: DimensionTab;
  onSaved: (record: DimensionTableRecord) => void;
  onDirtyChange: (dirty: boolean) => void;
};

const TABLE_TYPE_OPTIONS: Option[] = [
  { value: "SOURCE", label: "贴源表" },
  { value: "DIMENSION", label: "维度表" },
  { value: "DIM", label: "维度" },
  { value: "DETAIL", label: "明细表" },
  { value: "SUMMARY", label: "汇总表" },
  { value: "APPLICATION", label: "应用表" },
];

const STORAGE_POLICY_OPTIONS: Option[] = [
  { value: "REAL_TIME_INCREMENTAL", label: "实时增量" },
  { value: "REAL_TIME_FULL", label: "实时全量" },
  { value: "QUARTER_HOURLY_INCREMENTAL", label: "15分钟增量" },
  { value: "QUARTER_HOURLY_FULL", label: "15分钟全量" },
  { value: "HOURLY_INCREMENTAL", label: "小时增量" },
  { value: "HOURLY_FULL_CUMULATIVE", label: "小时全量累计" },
  { value: "DAILY_INCREMENTAL", label: "每日增量" },
  { value: "DAILY_FULL", label: "每日全量" },
  { value: "WEEKLY_INCREMENTAL", label: "每周增量" },
  { value: "WEEKLY_FULL", label: "每周全量" },
  { value: "MONTHLY_INCREMENTAL", label: "每月增量" },
  { value: "MONTHLY_FULL", label: "每月全量" },
  { value: "QUARTERLY_INCREMENTAL", label: "每季度增量" },
  { value: "QUARTERLY_FULL", label: "每季度全量" },
  { value: "YEARLY_INCREMENTAL", label: "每年增量" },
  { value: "YEARLY_FULL", label: "每年全量" },
  { value: "HOURLY_MONTHLY_INCREMENTAL", label: "小时月增量" },
  { value: "HOURLY_DAILY_CUMULATIVE", label: "小时当天累计" },
];

function initialValues(record?: DimensionTableRecord): Record<string, string> {
  return {
    dwhId: String(record?.dwhId || ""),
    enShortName: String(record?.enShortName || (record?.tableType === "DIM" ? record?.tableName : "") || ""),
    timePeriodId: String(record?.timePeriodId || ""),
    dimId: String(record?.dimId || ""),
    modifier: String(record?.modifier || ""),
    nameRuleId: String(record?.nameRuleId || ""),
    busTypeId: String(record?.busTypeId || ""),
    domainId: String(record?.domainId || ""),
    busProcessId: String(record?.busProcessId || ""),
    martOrSubject: String(record?.martOrSubject || ""),
    tableType: String(record?.tableType || ""),
    storagePolicy: String(record?.storagePolicy || ""),
    tableName: String(record?.tableName || ""),
    cnName: String(record?.cnName || ""),
    lifeCycle: record?.lifeCycle === undefined || record?.lifeCycle === null ? "" : String(record.lifeCycle),
    director: String(record?.director || ""),
    remark: String(record?.remark || ""),
  };
}

function optionLabel(record: ModelingRecord, fields: string[]) {
  for (const field of fields) {
    const value = record[field];
    if (value !== undefined && value !== null && String(value).trim()) return String(value);
  }
  return String(record.id || "未命名");
}

function FormSectionHeader({ title }: { title: string }) {
  return (
    <div className="mb-3 flex min-w-0 items-center gap-2">
      <span aria-hidden="true" className="h-4 w-1 shrink-0 bg-[#5664ff]" />
      <h3 className="m-0 shrink-0 text-sm font-semibold leading-5 text-[var(--yak-components-panel-text)]">
        {title}
      </h3>
      <span aria-hidden="true" className="h-px min-w-0 flex-1 bg-[var(--yak-components-panel-border)]" />
    </div>
  );
}

function FormField({
  label,
  required,
  children,
  className = "",
}: {
  label: string;
  required?: boolean;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={["grid min-w-0 grid-cols-1 items-start gap-1.5 sm:grid-cols-[104px_minmax(0,1fr)] sm:gap-3", className].join(" ")}>
      <label className="pt-1.5 text-xs leading-5 text-[var(--yak-components-muted-text)]">
        {required ? <span className="mr-1 text-[#e5484d]">*</span> : null}
        {label}
      </label>
      <div className="min-w-0">{children}</div>
    </div>
  );
}

function OptionSelect({
  value,
  options,
  placeholder,
  onChange,
  disabled,
  emptyContent = "暂无可选项",
}: {
  value: string;
  options: Option[];
  placeholder: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  emptyContent?: string;
}) {
  return (
    <Select<string>
      size="small"
      items={Object.fromEntries(options.map((option) => [option.value, option.label]))}
      value={value || null}
      disabled={disabled}
      onValueChange={(nextValue) => onChange(nextValue ?? "")}
    >
      <SelectTrigger variant="outlined" className="w-full">
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent emptyContent={options.length === 0 ? emptyContent : undefined}>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            <SelectItemText>{option.label}</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

export function DimensionEditor({
  projectCode,
  tab,
  onSaved,
  onDirtyChange,
}: DimensionEditorProps) {
  const queryClient = useQueryClient();
  const recordId = String(tab.record?.id || "");
  const [values, setValues] = useState<Record<string, string>>(() => ({
    ...initialValues(tab.record),
    tableType: String(tab.createType || tab.record?.tableType || ""),
  }));
  const [fields, setFields] = useState<DimensionFieldRecord[]>([]);
  const [dirty, setDirty] = useState(Boolean(tab.dirty));
  const [basicDirty, setBasicDirty] = useState(Boolean(tab.dirty));
  const [fieldsDirty, setFieldsDirty] = useState(false);
  const [savingFields, setSavingFields] = useState(false);
  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [versionOpen, setVersionOpen] = useState(false);
  const [noticeDismissed, setNoticeDismissed] = useState(false);
  const [error, setError] = useState("");

  const layersQuery = useQuery<ModelingRecord[]>({
    queryKey: ["modeling", "dimension-editor-layers", projectCode],
    queryFn: () => dwLayerApi.all<ModelingRecord>(projectCode),
    enabled: Boolean(projectCode),
  });
  const businessTypeTreeQuery = useQuery<BusinessTypeTreeNode[]>({
    queryKey: planningQueryKeys.businessTypeTree(projectCode),
    queryFn: () => businessTypeApi.tree(projectCode),
    enabled: Boolean(projectCode),
  });
  const domainsQuery = useQuery<ModelingRecord[]>({
    queryKey: planningQueryKeys.dataDomainAll(projectCode),
    queryFn: () => dataDomainApi.all<ModelingRecord>(projectCode),
    enabled: Boolean(projectCode),
  });
  // Query the selected category's linked domains; an empty successful response means
  // this business category has no restrictions and all data domains are available.
  const relatedDomainsQuery = useQuery<ModelingRecord[]>({
    queryKey: planningQueryKeys.businessTypeRelatedDomains(projectCode, values.busTypeId),
    queryFn: () => businessTypeApi.selectRelatedDomains<ModelingRecord>(projectCode, values.busTypeId),
    enabled: Boolean(projectCode && values.busTypeId),
  });
  const martsQuery = useQuery<ModelingRecord[]>({
    queryKey: planningQueryKeys.dataMartAll(projectCode),
    queryFn: () => dataMartApi.all<ModelingRecord>(projectCode),
    enabled: Boolean(projectCode && (values.tableType === "APPLICATION" || tab.createType === "APPLICATION" || tab.record?.tableType === "APPLICATION")),
  });
  const applicationScopeQuery = useQuery<SubjectDomainDirectoryNode[]>({
    queryKey: planningQueryKeys.subjectDomainTree(projectCode),
    queryFn: () => subjectDomainApi.tree(projectCode),
    enabled: Boolean(projectCode && (values.tableType === "APPLICATION" || tab.createType === "APPLICATION" || tab.record?.tableType === "APPLICATION")),
  });
  const timePeriodsQuery = useQuery<ModelingRecord[]>({
    queryKey: ["modeling", "dimension-editor-time-periods", projectCode],
    queryFn: () => timePeriodApi.all<ModelingRecord>(projectCode),
    enabled: Boolean(projectCode && (values.tableType === "SUMMARY" || values.tableType === "APPLICATION"
      || tab.createType === "SUMMARY" || tab.createType === "APPLICATION"
      || tab.record?.tableType === "SUMMARY" || tab.record?.tableType === "APPLICATION")),
  });
  const modifiersQuery = useQuery<ModelingRecord[]>({
    queryKey: ["modeling", "dimension-editor-modifiers", projectCode],
    queryFn: () => modifierApi.all<ModelingRecord>(projectCode),
    enabled: Boolean(projectCode && (values.tableType === "SUMMARY" || values.tableType === "APPLICATION"
      || tab.createType === "SUMMARY" || tab.createType === "APPLICATION"
      || tab.record?.tableType === "SUMMARY" || tab.record?.tableType === "APPLICATION")),
  });
  const dimensionsQuery = useQuery({
    queryKey: ["modeling", "dimension-editor-dimensions", projectCode],
    queryFn: () => dimensionReferenceApi.dimensionOptions(projectCode),
    enabled: Boolean(projectCode && (values.tableType === "SUMMARY" || values.tableType === "APPLICATION"
      || tab.createType === "SUMMARY" || tab.createType === "APPLICATION"
      || tab.record?.tableType === "SUMMARY" || tab.record?.tableType === "APPLICATION")),
  });
  const fieldStandardsQuery = useQuery<ModelingRecord[]>({
    queryKey: ["modeling", "dimension-editor-field-standards", projectCode],
    queryFn: () => fieldStandardApi.all<ModelingRecord>(projectCode),
    enabled: Boolean(projectCode),
  });
  const codeStandardsQuery = useQuery<ModelingRecord[]>({
    queryKey: ["modeling", "dimension-editor-code-standards", projectCode],
    queryFn: () => codeStandardApi.all<ModelingRecord>(projectCode),
    enabled: Boolean(projectCode),
  });
  const unitsQuery = useQuery<ModelingRecord[]>({
    queryKey: ["modeling", "dimension-editor-units", projectCode],
    queryFn: () => unitStandardApi.all<ModelingRecord>(projectCode),
    enabled: Boolean(projectCode),
  });
  const fieldsQuery = useQuery<DimensionFieldRecord[]>({
    queryKey: ["modeling", "dimension-fields", projectCode, recordId],
    queryFn: () => dimensionModelingApi.fields(projectCode, recordId),
    enabled: Boolean(projectCode && recordId),
  });
  const versionQuery = useQuery<DimensionVersionRecord[]>({
    queryKey: ["modeling", "dimension-versions", projectCode, recordId],
    queryFn: () => dimensionModelingApi.versionHistory(projectCode, recordId),
    enabled: Boolean(projectCode && recordId && versionOpen),
  });

  const layers = layersQuery.data || [];
  const selectedTableType = String(values.tableType || tab.createType || tab.record?.tableType || "");
  const processQuery = useBusinessProcessCascader(projectCode, values.busTypeId, selectedTableType === "DETAIL");
  const layerOptions = useMemo(() => warehouseLayerOptions(
    layers,
    selectedTableType,
    tab.mode === "edit" ? String(tab.record?.dwhId || "") : "",
  ), [layers, selectedTableType, tab.mode, tab.record?.dwhId]);
  const displayedLayerOptions = useMemo(() => {
    if (!values.dwhId || layerOptions.some((option) => option.value === values.dwhId)) {
      return layerOptions;
    }
    // During a failed refresh, preserve the saved ID and a readable edit label.
    if (tab.mode === "edit" && tab.record?.dwhName) {
      return [{ value: values.dwhId, label: String(tab.record.dwhName) }, ...layerOptions];
    }
    return layerOptions;
  }, [layerOptions, values.dwhId, tab.mode, tab.record?.dwhName]);
  const activeLayer = layers.find((layer) => String(layer.id || "") === values.dwhId);
  const isUnassigned = warehouseEnumCode(activeLayer?.dwType) === "NONE_LAYER";
  const tableTypeOptions = useMemo(() => {
    const modelType = warehouseEnumCode(activeLayer?.dwModelType);
    const allowed = ({
      SOURCE: ["SOURCE"],
      DIMENSION: ["DIMENSION"],
      DIM: ["DIM"],
      DIMENSION_DIM: ["DIMENSION", "DIM"],
      DETAIL: ["DETAIL"],
      SUMMARY: ["SUMMARY"],
      APPLICATION: ["APPLICATION"],
    } as Record<string, string[]>)[modelType] || [];
    return TABLE_TYPE_OPTIONS.filter((option) => allowed.includes(option.value));
  }, [activeLayer]);
  const layerEmptyContent = layersQuery.isError
    ? "数仓分层加载失败，请重试"
    : layersQuery.isPending
      ? "正在加载数仓分层..."
      : layers.length === 0
        ? "暂无数仓分层，请先在数仓规划中创建"
        : "当前模型类型没有匹配的数仓分层";
  const isLegacyUntyped = tab.mode === "edit" && !selectedTableType && isUnassigned;
  const canChooseLegacyType = tab.mode === "edit" && !selectedTableType && !isUnassigned;

  const businessTypeTreeOptions = useMemo<TreeSelectOption[]>(() => (
    (businessTypeTreeQuery.data || [])
      .map(mapBusinessTypeTreeOption)
      .filter((option): option is TreeSelectOption => option !== null)
  ), [businessTypeTreeQuery.data]);
  const domainOptionsReady = domainsQuery.isSuccess && (!values.busTypeId || relatedDomainsQuery.isSuccess);
  const availableDomains = useMemo(() => availableDataDomains(
    domainsQuery.data || [],
    values.busTypeId,
    !values.busTypeId || relatedDomainsQuery.isSuccess ? relatedDomainsQuery.data || [] : null,
  ), [domainsQuery.data, values.busTypeId, relatedDomainsQuery.isSuccess, relatedDomainsQuery.data]);
  const domainOptions = availableDomains.map((record) => ({
    value: String(record.id || ""),
    label: optionLabel(record, ["cnName", "domainName", "enName"]),
  })).filter((option) => option.value);
  const domainEmptyContent = domainsQuery.isError
    ? "数据域加载失败，请重试"
    : relatedDomainsQuery.isError && values.busTypeId
      ? "关联数据域加载失败，请重试"
      : !domainOptionsReady
        ? "正在加载可用数据域..."
        : "暂无可用数据域";
  const applicationScopeLoading = martsQuery.isPending || applicationScopeQuery.isPending || businessTypeTreeQuery.isPending;
  const applicationScopeError = martsQuery.isError || applicationScopeQuery.isError || businessTypeTreeQuery.isError;
  const martSubjectOptions = useMemo<TreeSelectOption[]>(() => {
    if (!martsQuery.isSuccess || !applicationScopeQuery.isSuccess || !businessTypeTreeQuery.isSuccess) return [];
    const addIcons = (nodes: readonly TreeSelectOption[]): TreeSelectOption[] => nodes.map((node) => ({
      ...node,
      icon: <PlanningIcon
        name={node.value.endsWith("-SUBJECT") ? "subject-domain" : node.value.endsWith("-MART") ? "data-mart" : "business-type"}
        size={13}
        className="text-[#3a3d51]"
      />,
      children: node.children ? addIcons(node.children) : undefined,
    }));
    return addIcons(buildApplicationScopeTree(
      businessTypeTreeQuery.data || [],
      martsQuery.data || [],
      applicationScopeQuery.data || [],
    ));
  }, [businessTypeTreeQuery.data, businessTypeTreeQuery.isSuccess, martsQuery.data, martsQuery.isSuccess, applicationScopeQuery.data, applicationScopeQuery.isSuccess]);
  const timePeriodOptions = (timePeriodsQuery.data || []).map((record) => ({
    value: String(record.id || ""),
    label: optionLabel(record, ["cnName", "enName", "enShortName"]),
  })).filter((option) => option.value);
  const modifierOptions = (modifiersQuery.data || []).map((record) => ({
    value: String(record.id || ""),
    label: optionLabel(record, ["cnName", "enName", "enShortName"]),
  })).filter((option) => option.value);
  const dimensionOptions = (dimensionsQuery.data || []).map((option) => ({
    value: String(option.value || ""),
    label: String(option.label || option.value || ""),
  })).filter((option) => option.value);
  const fieldStandardOptions = (fieldStandardsQuery.data || []).map((record) => ({
    value: String(record.id || ""),
    label: optionLabel(record, ["cnName", "enName", "enShortName"]),
  })).filter((option) => option.value);
  const codeStandardOptions = (codeStandardsQuery.data || []).map((record) => ({
    value: String(record.id || ""),
    label: optionLabel(record, ["cnName", "enName", "enShortName"]),
  })).filter((option) => option.value);
  const unitOptions = (unitsQuery.data || []).map((record) => ({
    value: String(record.id || ""),
    label: optionLabel(record, ["cnName", "enName", "enShortName", "unitName"]),
  })).filter((option) => option.value);
  const isDim = selectedTableType === "DIM";
  const isDetail = selectedTableType === "DETAIL";
  const isSummary = selectedTableType === "SUMMARY";
  const isApplication = selectedTableType === "APPLICATION";

  // Treat asynchronously loaded defaults as effective form values during the
  // first render after queries resolve, even before the hydration effects run.
  // This is intentionally create-only: saved records never receive defaults.
  const isNewDraft = tab.mode === "create" && !recordId;
  const resolvedDwhId = isNewDraft ? values.dwhId || layerOptions[0]?.value || "" : values.dwhId;
  const resolvedDomainId = isNewDraft && (isDim || isSummary)
    ? domainOptions.some((option) => option.value === values.domainId)
      ? values.domainId
      : domainOptionsReady ? defaultDataDomainId(availableDomains) : ""
    : values.domainId;

  // New models start with the first warehouse layer compatible with their creation type.
  // Selection is by record ID; never reset a saved or manually selected value.
  useEffect(() => {
    if (tab.mode !== "create" || recordId || !layerOptions.length) return;
    const firstLayerId = layerOptions[0]?.value;
    if (!firstLayerId) return;
    setValues((current) => current.dwhId
      ? current
      : { ...current, dwhId: firstLayerId });
  }, [tab.mode, recordId, layerOptions]);

  // Hydrate only from the active category's completed domain query.
  // Preserve any valid manual selection, but never keep a stale relation.
  useEffect(() => {
    if (tab.mode !== "create" || recordId || (!isDim && !isSummary) || !domainOptionsReady) return;
    const nextDomainId = defaultDataDomainId(availableDomains);
    setValues((current) => current.domainId
      && availableDomains.some((domain) => String(domain.id || "") === current.domainId)
        ? current
        : { ...current, domainId: nextDomainId });
  }, [tab.mode, recordId, isDim, isSummary, domainOptionsReady, availableDomains]);

  useEffect(() => {
    if (fieldsQuery.data && !fieldsDirty) {
      setFields(fieldsQuery.data.map((field) => ({
        ...field,
        fieldType: field.fieldType || "PROPERTY",
        tempId: field.id || field.tempId,
      })));
    }
  }, [fieldsQuery.data, fieldsDirty]);

  const changeValue = (key: string, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setBasicDirty(true);
    setDirty(true);
    onDirtyChange(true);
    setNoticeDismissed(false);
    setError("");
  };

  const changeBusinessType = (businessTypeId: string) => {
    if (businessTypeId === values.busTypeId) return;
    // A previously selected domain (and its business process) may not belong to
    // the new category; let the linked-domain query choose a fresh default.
    setValues((current) => ({ ...current, busTypeId: businessTypeId, domainId: "", busProcessId: "" }));
    setBasicDirty(true);
    setDirty(true);
    onDirtyChange(true);
    setNoticeDismissed(false);
    setError("");
  };

  const updateFieldGroup = (
    fieldType: "PROPERTY" | "PARTITION",
    nextFields: DimensionFieldRecord[],
  ) => {
    setFields((current) => {
      const otherFields =
        fieldType === "PARTITION"
          ? current.filter((field) => field.fieldType !== "PARTITION")
          : current.filter((field) => field.fieldType === "PARTITION");
      const normalized = nextFields.map((field) => ({
        ...field,
        fieldType: fieldType === "PARTITION"
          ? "PARTITION"
          : field.fieldType && field.fieldType !== "PARTITION"
            ? field.fieldType
            : "PROPERTY",
      }));
      return fieldType === "PARTITION"
        ? [...otherFields, ...normalized]
        : [...normalized, ...otherFields];
    });
    setFieldsDirty(true);
    setDirty(true);
    onDirtyChange(true);
    setNoticeDismissed(false);
    setError("");
  };

  const saveBasicInfo = async (showSuccess = true) => {
    if (saving) return false;
    if (!resolvedDwhId) {
      setError("请选择有效数仓分层");
      return false;
    }
    if (!selectedTableType && !isLegacyUntyped) {
      setError("请选择模型类型");
      return false;
    }
    if (!values.cnName.trim()) {
      setError("请输入中文名称");
      return false;
    }
    if (isDim) {
      if (!values.enShortName.trim()) {
        setError("请输入维度英文缩写");
        return false;
      }
      if (!resolvedDomainId) {
        setError("请选择数据域");
        return false;
      }
    } else {
      if (!values.tableName.trim()) {
        setError("请输入英文表名");
        return false;
      }
      if (!isLegacyUntyped && !values.director.trim()) {
        setError("请选择负责人");
        return false;
      }
    }
    if (isDetail && !values.busProcessId) {
      setError("请选择业务过程");
      return false;
    }
    if (isApplication && !values.martOrSubject) {
      setError("请选择数据集市或主题域");
      return false;
    }

    const payload: Record<string, unknown> = {
      id: recordId || undefined,
      dwhId: resolvedDwhId,
      tableType: selectedTableType || null,
      enShortName: isDim ? values.enShortName.trim() : null,
      busTypeId: values.busTypeId || null,
      domainId: resolvedDomainId || null,
      busProcessId: values.busProcessId || null,
      martOrSubject: values.martOrSubject || null,
      timePeriodId: values.timePeriodId || null,
      dimId: values.dimId || null,
      modifier: values.modifier || null,
      nameRuleId: values.nameRuleId || null,
      tableName: isDim ? null : values.tableName.trim(),
      cnName: values.cnName.trim(),
      storagePolicy: values.storagePolicy || null,
      lifeCycle: values.lifeCycle ? Number(values.lifeCycle) : null,
      director: values.director || null,
      remark: values.remark || null,
    };

    setSaving(true);
    setError("");
    try {
      const saved = recordId
        ? await dimensionModelingApi.update(projectCode, payload)
        : await dimensionModelingApi.create(projectCode, payload);

      if (!saved.id) throw new Error("保存成功但未返回模型ID");
      // Persist the displayed defaults before the tab transitions to edit mode.
      setValues((current) => ({ ...current, dwhId: resolvedDwhId, domainId: resolvedDomainId }));
      onSaved(saved);
      setBasicDirty(false);
      setDirty(fieldsDirty);
      onDirtyChange(fieldsDirty);
      setNoticeDismissed(false);
      if (showSuccess) toast.success("基本信息已保存为草稿");
      await queryClient.invalidateQueries({ queryKey: ["modeling", "dimension-tree", projectCode] });
      return saved;
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "保存失败，请检查填写内容后重试");
      toast.warning("基本信息保存失败");
      return false;
    } finally {
      setSaving(false);
    }
  };

  const saveFields = async (showSuccess = true, targetRecordId = recordId) => {
    if (!targetRecordId || !fieldsDirty || savingFields || Boolean(recordId && fieldsQuery.isFetching)) return false;
    const persistedFields = fieldsForSave(fields);
    const validationIssues = validateDimensionFields(persistedFields);
    if (validationIssues.length) {
      setError("字段配置存在 " + validationIssues.length + " 个问题，请先处理");
      toast.warning("字段配置校验未通过");
      return false;
    }
    setSavingFields(true);
    try {
      const normalizedFields = persistedFields.map(({ tempId, ...field }) => ({
        ...field,
        tableId: targetRecordId,
      }));
      const savedFields = await dimensionModelingApi.replaceFields(
        projectCode,
        targetRecordId,
        normalizedFields,
      );
      setFields(savedFields.map((field) => ({
        ...field,
        fieldType: field.fieldType || "PROPERTY",
        tempId: field.id || field.tempId,
      })));
      setFieldsDirty(false);
      try {
        const updatedRecord = await dimensionModelingApi.getById(projectCode, targetRecordId);
        onSaved(updatedRecord);
      } catch {
        // The field save already succeeded; a failed metadata refresh must not report a failed save.
      }
      setDirty(basicDirty);
      onDirtyChange(basicDirty);
      if (showSuccess) toast.success("字段信息已保存");
      await queryClient.invalidateQueries({ queryKey: ["modeling", "dimension-tree", projectCode] });
      await queryClient.invalidateQueries({ queryKey: ["modeling", "dimension-fields", projectCode, targetRecordId] });
      return true;
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "字段保存失败，请重试");
      toast.warning("字段保存失败");
      return false;
    } finally {
      setSavingFields(false);
    }
  };

  const saveChanges = async () => {
    if (saving || savingFields || (!basicDirty && !fieldsDirty)) return;

    const hadBasicChanges = basicDirty;
    const hadFieldChanges = fieldsDirty;
    let targetRecordId = recordId;

    if (hadBasicChanges || !targetRecordId) {
      const basicSaved = await saveBasicInfo(false);
      if (!basicSaved) return;
      targetRecordId = String(basicSaved.id || "");
      if (!targetRecordId) {
        setError("保存成功但未返回模型ID");
        return;
      }
    }
    if (hadFieldChanges) {
      const fieldsSaved = await saveFields(false, targetRecordId);
      if (!fieldsSaved) return;
    }

    setDirty(false);
    onDirtyChange(false);
    toast.success("模型信息已保存");
  };

  const submit = async () => {
    if (!recordId || dirty || submitting) return;
    setSubmitting(true);
    try {
      const submitted = await dimensionModelingApi.submit(projectCode, recordId);
      onSaved(submitted);
      setBasicDirty(false);
      setFieldsDirty(false);
      setDirty(false);
      onDirtyChange(false);
      toast.success("维度表已提交，版本快照已生成");
      await queryClient.invalidateQueries({ queryKey: ["modeling", "dimension-versions", projectCode, recordId] });
      await queryClient.invalidateQueries({ queryKey: ["modeling", "dimension-tree", projectCode] });
    } catch {
      toast.warning("提交失败，请检查维度表信息");
    } finally {
      setSubmitting(false);
    }
  };

  const refresh = async () => {
    if (!recordId) {
      setValues({
        ...initialValues(),
        tableType: String(tab.createType || ""),
      });
      setFields([]);
      setBasicDirty(false);
      setFieldsDirty(false);
      setDirty(false);
      onDirtyChange(false);
      return;
    }
    try {
      const [record, nextFields] = await Promise.all([
        dimensionModelingApi.getById(projectCode, recordId),
        dimensionModelingApi.fields(projectCode, recordId),
      ]);
      setValues(initialValues(record));
      setFields(nextFields.map((field) => ({
        ...field,
        fieldType: field.fieldType || "PROPERTY",
        tempId: field.id || field.tempId,
      })));
      onSaved(record);
      setBasicDirty(false);
      setFieldsDirty(false);
      setDirty(false);
      onDirtyChange(false);
      toast.success("模型已刷新");
    } catch {
      toast.warning("刷新失败，请稍后重试");
    }
  };

  const rollback = async (version: DimensionVersionRecord) => {
    if (!recordId) return;
    try {
      const restored = await dimensionModelingApi.rollback(projectCode, recordId, version.versionNumber);
      const restoredFields = await dimensionModelingApi.fields(projectCode, recordId);
      setValues(initialValues(restored));
      setFields(restoredFields.map((field) => ({
        ...field,
        fieldType: field.fieldType || "PROPERTY",
        tempId: field.id || field.tempId,
      })));
      onSaved(restored);
      setBasicDirty(false);
      setFieldsDirty(false);
      setDirty(false);
      onDirtyChange(false);
      setVersionOpen(false);
      toast.success("已回滚到 V" + version.versionNumber);
      await queryClient.invalidateQueries({ queryKey: ["modeling", "dimension-versions", projectCode, recordId] });
      await queryClient.invalidateQueries({ queryKey: ["modeling", "dimension-tree", projectCode] });
    } catch {
      toast.warning("版本回滚失败，请刷新后重试");
    }
  };

  const isSubmitted = tab.record?.submit === true || tab.record?.submit === "true";
  const currentVersion = Number(tab.record?.currentVersion || 0);
  const showNotice = dirty || Boolean(recordId && !isSubmitted);
  const noticeMessage = dirty
    ? "当前存在未保存的修改，请先保存相关内容，再提交生成版本。"
    : "当前草稿尚未提交，确认内容无误后，可点击「提交」生成版本。";

  const propertyFields = fields.filter((field) => field.fieldType !== "PARTITION");
  const partitionFields = fields.filter((field) => field.fieldType === "PARTITION");

  return (
    <div className="flex h-full min-h-0 w-full min-w-0 flex-1 bg-white">
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex min-h-10 shrink-0 flex-wrap items-center justify-between gap-2 border-b border-[var(--yak-components-panel-border)] px-4 py-1.5">
          <div className="flex flex-wrap items-center gap-1.5">
            <Button
              size="sm"
              variant="ghost"
              isDisabled={(!basicDirty && !fieldsDirty) || saving || savingFields || Boolean(recordId && fieldsDirty && (fieldsQuery.isFetching || fieldsQuery.isError))}
              isPending={saving || savingFields}
              onClick={() => void saveChanges()}
            >
              <Save size={13} />保存
            </Button>
            <Button
              size="sm"
              variant="ghost"
              isDisabled={!recordId || dirty || isSubmitted || submitting || fieldsQuery.isFetching || fieldsQuery.isError}
              isPending={submitting}
              onClick={() => void submit()}
            >
              <Upload size={13} />提交
            </Button>
            <Button size="sm" variant="ghost" onClick={() => void refresh()}>
              <RefreshCw size={13} />刷新
            </Button>
            <span className="mx-1 h-4 w-px bg-[var(--yak-components-control-border)]" />
            <Button size="sm" variant="ghost" isDisabled title="发布能力将在后续阶段接入">发布</Button>
            <Button size="sm" variant="ghost" isDisabled title="日志能力将在后续阶段接入">日志</Button>
            <Button size="sm" variant="ghost" isDisabled title="质量规则将在后续阶段接入">质量规则</Button>
            <Button size="sm" variant="ghost" isDisabled title="模型开发将在后续阶段接入">模型开发</Button>
            <Button size="sm" variant="ghost" isDisabled title="导出将在后续阶段接入"><Download size={13} />导出</Button>
          </div>
          <div className="flex items-center gap-2 whitespace-nowrap text-xs text-[var(--yak-components-muted-text)]">
            <span className={isSubmitted ? "size-1.5 rounded-full bg-[var(--yak-color-success)]" : "size-1.5 rounded-full bg-[var(--yak-components-muted-text)]"} />
            <span>当前版本：{currentVersion > 0 ? "V" + currentVersion : "暂无"}</span>
            <span className="mx-1 h-3 w-px bg-[var(--yak-components-control-border)]" />
            <span>{isSubmitted ? "已提交" : recordId ? "草稿 / 待提交" : "新建草稿"}</span>
          </div>
        </div>

        {showNotice && !noticeDismissed ? (
          <Alert className="shrink-0 rounded-none border-x-0 border-t-0 px-4 py-1.5">
            <span>{noticeMessage}</span>
            <button
              type="button"
              aria-label="关闭提示"
              className="ml-auto inline-flex size-5 shrink-0 items-center justify-center rounded-sm transition-colors hover:bg-black/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--yak-color-primary)]"
              onClick={() => setNoticeDismissed(true)}
            >
              <X size={12} />
            </button>
          </Alert>
        ) : null}

        <div className="min-h-0 flex-1 overflow-y-auto bg-[var(--yak-color-hover)] p-3.5">
          <div className="space-y-3.5">
            <section className="bg-white p-4">
              <FormSectionHeader title="基本信息" />
              <div className="grid grid-cols-1 gap-x-8 gap-y-3 xl:grid-cols-2">
                <FormField label="数仓分层" required>
                  <OptionSelect
                    value={resolvedDwhId}
                    options={displayedLayerOptions}
                    placeholder={layersQuery.isPending ? "正在加载可用数仓分层..." : "请选择数仓分层"}
                    emptyContent={layerEmptyContent}
                    onChange={(value) => {
                      setValues((current) => ({ ...current, dwhId: value }));
                      setBasicDirty(true);
                      setDirty(true);
                      onDirtyChange(true);
                    }}
                  />
                  {layersQuery.isError ? (
                    <div className="mt-1 flex items-center gap-2 text-xs text-[#b45309]">
                      <span>数仓分层加载失败</span>
                      <button
                        type="button"
                        className="cursor-pointer text-[var(--yak-color-primary)] hover:underline"
                        onClick={() => void layersQuery.refetch()}
                      >
                        重新加载
                      </button>
                    </div>
                  ) : layersQuery.isSuccess && layerOptions.length === 0 ? (
                    <p className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
                      当前模型类型没有可用分层，请先在数仓规划中配置分层归属与模型类型。
                    </p>
                  ) : null}
                </FormField>

                {canChooseLegacyType ? (
                  <FormField label="模型类型" required>
                    <OptionSelect
                      value={values.tableType}
                      options={tableTypeOptions}
                      placeholder="请选择迁移后的模型类型"
                      onChange={(value) => changeValue("tableType", value)}
                    />
                  </FormField>
                ) : null}

                {isApplication ? (
                  <FormField label="集市/主题" required>
                    <TreeSelect
                      ariaLabel="选择数据集市或主题域"
                      items={martSubjectOptions}
                      value={values.martOrSubject || null}
                      placeholder={applicationScopeLoading ? "正在加载集市与主题..." : "请选择数据集市或主题域"}
                      searchPlaceholder="搜索数据集市或主题域"
                      emptyText={applicationScopeError ? "集市或主题域加载失败，请重试" : "暂无可选数据集市或主题域"}
                      disabled={applicationScopeLoading || applicationScopeError}
                      size="small"
                      variant="outlined"
                      className="w-full"
                      popupClassName="!min-w-[300px]"
                      onValueChange={(value) => changeValue("martOrSubject", value || "")}
                    />
                    {applicationScopeError ? (
                      <button
                        type="button"
                        className="mt-1 cursor-pointer text-xs text-[var(--yak-color-primary)] hover:underline"
                        onClick={() => void Promise.all([
                          martsQuery.refetch(),
                          applicationScopeQuery.refetch(),
                          businessTypeTreeQuery.refetch(),
                        ])}
                      >
                        重新加载集市与主题
                      </button>
                    ) : null}
                  </FormField>
                ) : (
                  <FormField label="业务分类">
                    <TreeSelect
                      ariaLabel="选择业务分类"
                      items={businessTypeTreeOptions}
                      value={values.busTypeId || null}
                      placeholder={businessTypeTreeQuery.isPending ? "正在加载业务分类..." : "请选择业务分类"}
                      searchPlaceholder="搜索业务分类"
                      emptyText={businessTypeTreeQuery.isError ? "业务分类加载失败，请重试" : "暂无业务分类"}
                      disabled={businessTypeTreeQuery.isPending && !businessTypeTreeQuery.data}
                      size="small"
                      variant="outlined"
                      className="w-full"
                      popupClassName="!min-w-[280px]"
                      onValueChange={(value) => changeBusinessType(value || "")}
                    />
                    {businessTypeTreeQuery.isError ? (
                      <button
                        type="button"
                        className="mt-1 cursor-pointer text-xs text-[var(--yak-color-primary)] hover:underline"
                        onClick={() => void businessTypeTreeQuery.refetch()}
                      >
                        重新加载业务分类
                      </button>
                    ) : null}
                  </FormField>
                )}

                {isDim ? (
                  <FormField label="数据域" required>
                    <OptionSelect
                      value={resolvedDomainId}
                      options={domainOptions}
                      placeholder={!domainOptionsReady ? "正在加载可用数据域..." : "请选择数据域"}
                      emptyContent={domainEmptyContent}
                      onChange={(value) => changeValue("domainId", value)}
                    />
                    {values.busTypeId && relatedDomainsQuery.isError ? (
                      <button
                        type="button"
                        className="mt-1 cursor-pointer text-xs text-[var(--yak-color-primary)] hover:underline"
                        onClick={() => void relatedDomainsQuery.refetch()}
                      >
                        重新加载关联数据域
                      </button>
                    ) : null}
                  </FormField>
                ) : null}

                {isDetail ? (
                  <FormField label="业务过程" required>
                    <Cascader
                      ariaLabel="选择业务过程"
                      items={processQuery.options}
                      value={values.domainId && values.busProcessId ? [values.domainId, values.busProcessId] : null}
                      columnLabels={["数据域", "业务过程"]}
                      placeholder={processQuery.isFetching ? "正在加载业务过程..." : "请选择数据域及业务过程"}
                      emptyText={processQuery.isError ? "业务过程加载失败，请重试" : "暂无可选业务过程"}
                      disabled={processQuery.isFetching}
                      size="small"
                      variant="outlined"
                      onValueChange={(path) => {
                        setValues((current) => ({
                          ...current,
                          domainId: path?.[0] || "",
                          busProcessId: path?.[path.length - 1] || "",
                        }));
                        setBasicDirty(true);
                        setDirty(true);
                        onDirtyChange(true);
                      }}
                    />
                    {processQuery.isError ? (
                      <button
                        type="button"
                        className="mt-1 cursor-pointer text-xs text-[var(--yak-color-primary)] hover:underline"
                        onClick={() => void processQuery.refetch()}
                      >
                        重新加载业务过程
                      </button>
                    ) : null}
                  </FormField>
                ) : null}

                {isSummary ? (
                  <FormField label="数据域">
                    <OptionSelect
                      value={resolvedDomainId}
                      options={domainOptions}
                      placeholder={!domainOptionsReady ? "正在加载可用数据域..." : "请选择数据域"}
                      emptyContent={domainEmptyContent}
                      onChange={(value) => changeValue("domainId", value)}
                    />
                  </FormField>
                ) : null}

                {isDim ? (
                  <FormField label="英文缩写" required>
                    <Input
                      size="small"
                      variant="outlined"
                      value={values.enShortName}
                      placeholder="请输入维度英文缩写，例如 customer"
                      onChange={(event) => changeValue("enShortName", event.target.value)}
                    />
                  </FormField>
                ) : (
                  <FormField label={isLegacyUntyped ? "英文名称" : "表名"} required>
                    <Input
                      size="small"
                      variant="outlined"
                      value={values.tableName}
                      placeholder={isLegacyUntyped ? "请输入历史英文名称" : "请输入英文表名"}
                      onChange={(event) => changeValue("tableName", event.target.value)}
                    />
                  </FormField>
                )}

                <FormField label={isDim ? "中文名称" : "表中文名"} required>
                  <Input
                    size="small"
                    variant="outlined"
                    value={values.cnName}
                    placeholder={isDim ? "请输入维度中文名称" : "请输入表中文名"}
                    onChange={(event) => changeValue("cnName", event.target.value)}
                  />
                </FormField>

                {isSummary || isApplication ? (
                  <>
                    <FormField label="时间周期">
                      <OptionSelect
                        value={values.timePeriodId}
                        options={timePeriodOptions}
                        placeholder={timePeriodsQuery.isFetching ? "正在加载时间周期..." : "请选择时间周期"}
                        onChange={(value) => changeValue("timePeriodId", value)}
                      />
                    </FormField>
                    <FormField label="粒度">
                      <OptionSelect
                        value={values.dimId}
                        options={dimensionOptions}
                        placeholder={dimensionsQuery.isFetching ? "正在加载维度..." : "请选择维度粒度"}
                        onChange={(value) => changeValue("dimId", value)}
                      />
                    </FormField>
                    <FormField label="修饰词">
                      <OptionSelect
                        value={values.modifier}
                        options={modifierOptions}
                        placeholder={modifiersQuery.isFetching ? "正在加载修饰词..." : "请选择修饰词"}
                        onChange={(value) => changeValue("modifier", value)}
                      />
                    </FormField>
                  </>
                ) : null}

                {["SOURCE", "DIMENSION", "DETAIL"].includes(selectedTableType) ? (
                  <FormField label="存储策略">
                    <OptionSelect
                      value={values.storagePolicy}
                      options={STORAGE_POLICY_OPTIONS}
                      placeholder="请选择存储策略"
                      onChange={(value) => changeValue("storagePolicy", value)}
                    />
                  </FormField>
                ) : null}

                {selectedTableType && selectedTableType !== "DIM" ? (
                  <>
                    <FormField label="表名规则">
                      <Input
                        size="small"
                        variant="outlined"
                        value={values.nameRuleId}
                        placeholder="可选，填写已配置的规则标识"
                        onChange={(event) => changeValue("nameRuleId", event.target.value)}
                      />
                    </FormField>
                    <FormField label="生命周期">
                      <div className="flex items-center gap-2">
                        <Input
                          size="small"
                          variant="outlined"
                          type="number"
                          min={0}
                          value={values.lifeCycle}
                          placeholder="请输入"
                          className="max-w-[150px]"
                          onChange={(event) => changeValue("lifeCycle", event.target.value)}
                        />
                        <span className="text-xs text-[var(--yak-components-muted-text)]">天</span>
                      </div>
                    </FormField>
                    <FormField label="负责人" required>
                      <Input
                        size="small"
                        variant="outlined"
                        value={values.director}
                        placeholder="请输入负责人"
                        onChange={(event) => changeValue("director", event.target.value)}
                      />
                    </FormField>
                  </>
                ) : null}

                {isLegacyUntyped ? (
                  <FormField label="负责人">
                    <Input
                      size="small"
                      variant="outlined"
                      value={values.director}
                      placeholder="可选，填写负责人"
                      onChange={(event) => changeValue("director", event.target.value)}
                    />
                  </FormField>
                ) : null}

                <FormField label="描述" className="xl:col-span-2">
                  <Textarea
                    size="small"
                    variant="outlined"
                    rows={3}
                    value={values.remark}
                    placeholder="请输入描述"
                    onValueChange={(value) => changeValue("remark", value)}
                  />
                </FormField>
              </div>
            </section>

            {!isDim ? (
              <>
                <section className="bg-white p-4">
                  <FormSectionHeader title="字段管理" />
                  <SourceFieldImportCard
                    projectCode={projectCode}
                    fields={propertyFields}
                    onChange={(nextFields) => updateFieldGroup("PROPERTY", nextFields)}
                  />
                  <FieldSpreadsheet
                    modelType={selectedTableType}
                    fieldType="PROPERTY"
                    fields={propertyFields}
                    fieldStandardOptions={fieldStandardOptions}
                    codeStandardOptions={codeStandardOptions}
                    unitOptions={unitOptions}
                    loading={Boolean(recordId && fieldsQuery.isFetching)}
                    error={Boolean(recordId && fieldsQuery.isError)}
                    onChange={(nextFields) => updateFieldGroup("PROPERTY", nextFields)}
                  />
                </section>

                <section className="bg-white p-4">
                  <FormSectionHeader title="分区字段管理" />
                  <FieldSpreadsheet
                    modelType={selectedTableType}
                    fieldType="PARTITION"
                    fields={partitionFields}
                    fieldStandardOptions={fieldStandardOptions}
                    codeStandardOptions={codeStandardOptions}
                    unitOptions={unitOptions}
                    loading={Boolean(recordId && fieldsQuery.isFetching)}
                    error={Boolean(recordId && fieldsQuery.isError)}
                    onChange={(nextFields) => updateFieldGroup("PARTITION", nextFields)}
                  />
                </section>
              </>
            ) : null}

            {error ? <p role="alert" className="px-1 text-xs text-[var(--yak-components-danger-text)]">{error}</p> : null}
          </div>
        </div>
      </div>

      <aside className="flex h-full min-h-0 shrink-0 bg-white">
        {versionOpen ? (
          <section className="flex h-full min-h-0 w-[330px] flex-col border-l border-[#e5e7eb] bg-white">
            <header className="flex h-11 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-3">
              <span className="text-sm font-semibold text-[#303846]">版本管理</span>
              <button type="button" title="收起面板" aria-label="收起版本管理面板" onClick={() => setVersionOpen(false)} className="flex size-6 cursor-pointer items-center justify-center rounded text-[#8b93a1] hover:bg-[#f0f2f5] hover:text-[#303846]">
                <X size={14} />
              </button>
            </header>
            <div className="min-h-0 flex-1 overflow-y-auto p-2">
              <Table<DimensionVersionRecord>
                columns={[
                  { key: "versionNumber", title: "版本", width: 56, render: (_value, version) => "V" + version.versionNumber },
                  { key: "updateTime", title: "提交时间", width: 128, render: (_value, version) => version.updateTime || "-" },
                  { key: "updateBy", title: "提交人", width: 76, render: (_value, version) => version.updateBy || "-" },
                  { key: "actions", title: "操作", width: 64, align: "center", render: (_value, version) => <Button size="sm" variant="ghost" className="!h-6 !px-1 text-xs" onClick={() => void rollback(version)}>回滚</Button> },
                ]}
                dataSource={versionQuery.data || []}
                rowKey={(version) => version.key}
                loading={versionQuery.isFetching}
                emptyText="暂无已提交版本"
                pagination={false}
                size="small"
              />
            </div>
          </section>
        ) : null}
        <nav aria-label="维度表辅助面板" className="flex h-full w-9 shrink-0 flex-col border-l border-[#e5e7eb] bg-white">
          <button
            type="button"
            title="版本管理"
            aria-label="版本管理"
            aria-pressed={versionOpen}
            disabled={!recordId}
            onClick={() => setVersionOpen((open) => !open)}
            className={[
              "relative flex w-full shrink-0 cursor-pointer flex-col items-center justify-center border-b border-[#e5e7eb] px-0 py-2 text-xs leading-[18px] transition-colors hover:bg-[#f7f9fc] hover:text-[#315efb] disabled:cursor-not-allowed disabled:opacity-40",
              versionOpen ? "bg-white text-[#315efb]" : "bg-white text-[#7c8796]",
            ].join(" ")}
          >
            {versionOpen ? <span aria-hidden="true" className="absolute inset-y-0 left-0 z-[1] w-0.5 bg-[#315efb]" /> : null}
            <History size={14} className="mb-1" />
            <span className="[writing-mode:vertical-rl] [text-orientation:upright] [word-break:keep-all] whitespace-nowrap tracking-[2px]">版本管理</span>
          </button>
          <button
            type="button"
            title="发布记录（后续接入）"
            aria-label="发布记录"
            disabled
            className="flex w-full shrink-0 cursor-not-allowed flex-col items-center justify-center border-b border-[#e5e7eb] bg-white px-0 py-2 text-xs leading-[18px] text-[#a0a7b3] opacity-60"
          >
            <span className="[writing-mode:vertical-rl] [text-orientation:upright] [word-break:keep-all] whitespace-nowrap tracking-[2px]">发布记录</span>
          </button>
        </nav>
      </aside>
    </div>
  );
}

type DimensionListProps = {
  projectCode: string;
  dwType: string;
  onEdit: (record: DimensionTableRecord) => void;
};

const TABLE_TYPE_LABELS: Record<string, string> = {
  SOURCE: "贴源表",
  DIMENSION: "维度表",
  DIM: "维度",
  DETAIL: "明细表",
  SUMMARY: "汇总表",
  APPLICATION: "应用表",
};

export function DimensionList({ projectCode, dwType, onEdit }: DimensionListProps) {
  const [keyword, setKeyword] = useState("");
  const [appliedKeyword, setAppliedKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const pageSize = 10;

  const pageQuery = useQuery({
    queryKey: ["modeling", "dimension-page", projectCode, dwType, appliedKeyword, pageNo],
    queryFn: () => dimensionModelingApi.page(projectCode, {
      dwhType: dwType,
      keyword: appliedKeyword || undefined,
      pageNo,
      pageSize,
    }),
    enabled: Boolean(projectCode),
  });

  const page = pageQuery.data;
  const records = page?.totalList || [];
  const totalPage = Math.max(1, page?.totalPage || 1);
  const columns: TableColumns<DimensionTableRecord> = [
    {
      key: "sequence",
      title: "序号",
      width: 64,
      align: "center",
      render: (_value, _record, index) => (pageNo - 1) * pageSize + index + 1,
    },
    {
      key: "cnName",
      title: "模型中文名",
      dataIndex: "cnName",
      minWidth: 200,
      ellipsis: true,
    },
    {
      key: "tableName",
      title: "英文标识",
      minWidth: 220,
      ellipsis: true,
      render: (_value, record) =>
        record.tableType === "DIM"
          ? record.enShortName || record.tableName || "-"
          : record.tableName || "-",
    },
    {
      key: "tableType",
      title: "模型类型",
      width: 132,
      render: (_value, record) => {
        const type = String(record.tableType || "");
        const label = TABLE_TYPE_LABELS[type] || "-";
        return isModelTypeIconType(type) ? (
          <span className="inline-flex items-center gap-1.5">
            <ModelTypeIcon type={type} size={14} />
            <span>{label}</span>
          </span>
        ) : label;
      },
    },
    {
      key: "dwhName",
      title: "数仓分层",
      dataIndex: "dwhName",
      minWidth: 160,
      ellipsis: true,
    },
    {
      key: "submit",
      title: "状态",
      width: 88,
      render: (_value, record) =>
        record.submit === true || record.submit === "true" ? "已提交" : "草稿",
    },
    {
      key: "updateTime",
      title: "最近修改时间",
      dataIndex: "updateTime",
      minWidth: 180,
      ellipsis: true,
    },
    {
      key: "actions",
      title: "操作",
      width: 80,
      align: "center",
      render: (_value, record) => (
        <Button
          size="sm"
          variant="ghost"
          className="px-1 text-xs"
          onClick={() => onEdit(record)}
        >
          编辑
        </Button>
      ),
    },
  ];

  const applySearch = () => {
    setPageNo(1);
    setAppliedKeyword(keyword.trim());
  };

  return (
    <div className="flex h-full min-h-0 w-full min-w-0 flex-1 flex-col bg-white p-4">
      <div className="mb-3 flex shrink-0 items-center gap-2 border-b border-[var(--yak-components-panel-border)] pb-3">
        <div className="min-w-[220px] flex-1">
          <Input
            size="small"
            variant="outlined"
            value={keyword}
            placeholder="搜索模型中文名、英文标识或维度缩写"
            onChange={(event) => setKeyword(event.target.value)}
            onKeyDown={(event) => { if (event.key === "Enter") applySearch(); }}
          />
        </div>
        <Button size="sm" variant="secondary" onClick={applySearch}>搜索</Button>
      </div>
      <div className="min-h-0 flex-1 overflow-auto">
        <Table<DimensionTableRecord>
          columns={columns}
          dataSource={records}
          rowKey={(record) => String(record.id || "")}
          loading={pageQuery.isFetching}
          emptyText="暂无模型"
          pagination={false}
          size="small"
          scroll={{ x: 1120, y: "calc(100vh - 250px)" }}
        />
      </div>
      <div className="flex shrink-0 items-center justify-end gap-3 border-t border-[var(--yak-components-panel-border)] pt-3 text-xs text-[var(--yak-components-muted-text)]">
        <span>共 {page?.total || 0} 条</span>
        <span>第 {pageNo} / {totalPage} 页</span>
        <Button size="sm" variant="secondary" isDisabled={pageNo <= 1} onClick={() => setPageNo((current) => Math.max(1, current - 1))}>上一页</Button>
        <Button size="sm" variant="secondary" isDisabled={pageNo >= totalPage} onClick={() => setPageNo((current) => Math.min(totalPage, current + 1))}>下一页</Button>
      </div>
    </div>
  );
}

export default DimensionEditor;
