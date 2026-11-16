import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
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
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import {
  Download,
  History,
  Plus,
  RefreshCw,
  Save,
  Send,
  Trash2,
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
import { businessTypeApi } from "@/service/data-modeling/planning/business-type";
import { dataDomainApi } from "@/service/data-modeling/planning/common-layer/data-domain";
import { businessProcessApi } from "@/service/data-modeling/planning/common-layer/business-process";
import { dataMartApi } from "@/service/data-modeling/planning/application-layer/data-mart";
import { subjectDomainApi } from "@/service/data-modeling/planning/application-layer/subject-domain";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";

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

const TABLE_TYPES_BY_MODEL: Record<string, string[]> = {
  SOURCE: ["SOURCE"],
  DIMENSION: ["DIMENSION"],
  DIM: ["DIM"],
  DIMENSION_DIM: ["DIMENSION", "DIM"],
  DETAIL: ["DETAIL"],
  SUMMARY: ["SUMMARY"],
  APPLICATION: ["APPLICATION"],
};

const DATA_TYPE_OPTIONS: Option[] = [
  "INT", "BIGINT", "BOOLEAN", "STRING", "TEXT", "DECIMAL", "DATE",
  "DATETIME", "TIMESTAMP", "JSON", "ARRAY", "DOUBLE", "MAP", "STRUCT",
].map((value) => ({ value, label: value }));

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
}: {
  value: string;
  options: Option[];
  placeholder: string;
  onChange: (value: string) => void;
  disabled?: boolean;
}) {
  return (
    <Select
      size="small"
      value={value || undefined}
      disabled={disabled}
      onValueChange={(nextValue) => onChange(String(nextValue || ""))}
    >
      <SelectTrigger variant="outlined" className="w-full">
        <SelectValue placeholder={placeholder}>
          {(currentValue) => currentValue
            ? options.find((option) => option.value === currentValue)?.label || "未知选项"
            : placeholder}
        </SelectValue>
      </SelectTrigger>
      <SelectContent emptyContent="暂无可选项">
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
  dwType,
  tab,
  onSaved,
  onDirtyChange,
}: DimensionEditorProps) {
  const queryClient = useQueryClient();
  const recordId = String(tab.record?.id || "");
  const [values, setValues] = useState<Record<string, string>>(() => initialValues(tab.record));
  const [fields, setFields] = useState<DimensionFieldRecord[]>([]);
  const [dirty, setDirty] = useState(Boolean(tab.dirty));
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
  const businessTypesQuery = useQuery<ModelingRecord[]>({
    queryKey: ["modeling", "dimension-editor-business-types", projectCode],
    queryFn: () => businessTypeApi.all<ModelingRecord>(projectCode),
    enabled: Boolean(projectCode),
  });
  const domainsQuery = useQuery<ModelingRecord[]>({
    queryKey: ["modeling", "dimension-editor-domains", projectCode],
    queryFn: () => dataDomainApi.all<ModelingRecord>(projectCode),
    enabled: Boolean(projectCode),
  });
  const processesQuery = useQuery<ModelingRecord[]>({
    queryKey: ["modeling", "dimension-editor-processes", projectCode],
    queryFn: () => businessProcessApi.all<ModelingRecord>(projectCode),
    enabled: Boolean(projectCode),
  });
  const martsQuery = useQuery<ModelingRecord[]>({
    queryKey: ["modeling", "dimension-editor-marts", projectCode],
    queryFn: () => dataMartApi.all<ModelingRecord>(projectCode),
    enabled: Boolean(projectCode),
  });
  const subjectsQuery = useQuery<ModelingRecord[]>({
    queryKey: ["modeling", "dimension-editor-subjects", projectCode],
    queryFn: () => subjectDomainApi.all<ModelingRecord>(projectCode),
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
  const layerOptions = useMemo(() => layers
    .filter((layer) => String(layer.dwType || "") === dwType)
    .map((layer) => ({
      value: String(layer.id || ""),
      label: optionLabel(layer, ["cnName", "enShortName", "enName"]),
    }))
    .filter((option) => option.value), [layers, dwType]);

  const activeLayer = layers.find((layer) => String(layer.id || "") === values.dwhId);
  const isUnassigned = dwType === "NONE_LAYER";
  const tableTypeOptions = useMemo(() => {
    if (isUnassigned) return [];
    const modelType = String(activeLayer?.dwModelType || "");
    const allowed = TABLE_TYPES_BY_MODEL[modelType] || [];
    return TABLE_TYPE_OPTIONS.filter((option) => allowed.includes(option.value));
  }, [activeLayer, isUnassigned]);

  const businessTypeOptions = (businessTypesQuery.data || []).map((record) => ({
    value: String(record.id || ""),
    label: optionLabel(record, ["businessName", "cnName", "enName"]),
  })).filter((option) => option.value);
  const domainOptions = (domainsQuery.data || []).map((record) => ({
    value: String(record.id || ""),
    label: optionLabel(record, ["cnName", "domainName", "enName"]),
  })).filter((option) => option.value);
  const processOptions = (processesQuery.data || [])
    .filter((record) => !values.domainId || String(record.dataDomainId || record.domainId || "") === values.domainId)
    .map((record) => ({
      value: String(record.id || ""),
      label: optionLabel(record, ["cnName", "processName", "enName"]),
    })).filter((option) => option.value);
  const martSubjectOptions = [
    ...(martsQuery.data || []).map((record) => ({
      value: String(record.id || "") + "-MART",
      label: "数据集市 / " + optionLabel(record, ["martName", "cnName", "enName"]),
    })),
    ...(subjectsQuery.data || []).map((record) => ({
      value: String(record.id || "") + "-SUBJECT",
      label: "主题域 / " + optionLabel(record, ["domainName", "cnName", "enName"]),
    })),
  ].filter((option) => option.value);

  useEffect(() => {
    if (tab.mode !== "create" || values.dwhId || !layerOptions.length) return;
    const firstLayer = layerOptions[0];
    const layer = layers.find((item) => String(item.id || "") === firstLayer.value);
    const modelType = String(layer?.dwModelType || "");
    const firstTableType = (TABLE_TYPES_BY_MODEL[modelType] || [])[0] || "";
    setValues((current) => ({ ...current, dwhId: firstLayer.value, tableType: firstTableType }));
  }, [tab.mode, values.dwhId, layerOptions, layers]);

  useEffect(() => {
    if (fieldsQuery.data) {
      setFields(fieldsQuery.data.map((field) => ({ ...field, tempId: field.id || field.tempId })));
    }
  }, [fieldsQuery.data]);

  const changeValue = (key: string, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setDirty(true);
    onDirtyChange(true);
    setNoticeDismissed(false);
    setError("");
  };

  const changeField = (field: DimensionFieldRecord, updates: Partial<DimensionFieldRecord>) => {
    const identity = field.id || field.tempId;
    setFields((current) => current.map((item) =>
      (item.id || item.tempId) === identity ? { ...item, ...updates } : item,
    ));
    setDirty(true);
    onDirtyChange(true);
    setNoticeDismissed(false);
  };

  const addField = () => {
    const tempId = "draft-" + String(Date.now()) + "-" + String(fields.length);
    setFields((current) => [...current, {
      tempId,
      fieldName: "",
      cnName: "",
      dataType: "STRING",
      primaryFlag: false,
      notNull: false,
    }]);
    setDirty(true);
    onDirtyChange(true);
  };

  const removeField = (field: DimensionFieldRecord) => {
    const identity = field.id || field.tempId;
    setFields((current) => current.filter((item) => (item.id || item.tempId) !== identity));
    setDirty(true);
    onDirtyChange(true);
  };

  const save = async () => {
    if (saving) return;
    if (!values.dwhId) {
      setError("请选择数仓分层");
      return;
    }
    if (!values.tableName.trim()) {
      setError("请输入英文表名");
      return;
    }
    if (!values.cnName.trim()) {
      setError("请输入表中文名");
      return;
    }
    if (!isUnassigned && !values.tableType) {
      setError("请选择模型类型");
      return;
    }

    const payload: Record<string, unknown> = {
      id: recordId || undefined,
      dwhId: values.dwhId,
      busTypeId: values.busTypeId || null,
      domainId: values.domainId || null,
      busProcessId: values.busProcessId || null,
      martOrSubject: values.martOrSubject || null,
      tableName: values.tableName.trim(),
      cnName: values.cnName.trim(),
      tableType: isUnassigned ? null : values.tableType || null,
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

      if (!saved.id) throw new Error("保存成功但未返回维度表ID");

      try {
        const normalizedFields = fields.map(({ tempId, ...field }) => ({
          ...field,
          tableId: String(saved.id),
        }));
        const savedFields = await dimensionModelingApi.replaceFields(
          projectCode,
          String(saved.id),
          normalizedFields,
        );
        setFields(savedFields);
      } catch {
        onSaved(saved);
        toast.warning("基本信息已保存，但字段管理保存失败，请重试");
        return;
      }

      onSaved(saved);
      setDirty(false);
      onDirtyChange(false);
      setNoticeDismissed(false);
      toast.success("维度表已保存");
      await queryClient.invalidateQueries({ queryKey: ["modeling", "dimension-tree", projectCode] });
      await queryClient.invalidateQueries({ queryKey: ["modeling", "dimension-fields", projectCode, String(saved.id)] });
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "保存失败，请检查填写内容后重试");
      toast.warning("维度表保存失败");
    } finally {
      setSaving(false);
    }
  };

  const submit = async () => {
    if (!recordId || dirty || submitting) return;
    setSubmitting(true);
    try {
      const submitted = await dimensionModelingApi.submit(projectCode, recordId);
      onSaved(submitted);
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
      setValues(initialValues());
      setFields([]);
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
      setFields(nextFields);
      onSaved(record);
      setDirty(false);
      onDirtyChange(false);
      toast.success("维度表已刷新");
    } catch {
      toast.warning("刷新失败，请稍后重试");
    }
  };

  const rollback = async (version: DimensionVersionRecord) => {
    if (!recordId) return;
    try {
      const restored = await dimensionModelingApi.rollback(projectCode, recordId, version.versionNumber);
      setValues(initialValues(restored));
      onSaved(restored);
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

  const fieldColumns: TableColumns<DimensionFieldRecord> = [
    {
      key: "sequence",
      title: "序号",
      width: 58,
      align: "center",
      render: (_value, _record, index) => index + 1,
    },
    {
      key: "fieldName",
      title: "字段名称",
      width: 190,
      render: (_value, field) => (
        <Input
          size="small"
          variant="outlined"
          value={field.fieldName || ""}
          placeholder="请输入字段名称"
          onChange={(event) => changeField(field, { fieldName: event.target.value })}
        />
      ),
    },
    {
      key: "dataType",
      title: "类型",
      width: 150,
      render: (_value, field) => (
        <OptionSelect
          value={field.dataType || "STRING"}
          options={DATA_TYPE_OPTIONS}
          placeholder="请选择类型"
          onChange={(value) => changeField(field, { dataType: value })}
        />
      ),
    },
    {
      key: "cnName",
      title: "字段显示名",
      width: 190,
      render: (_value, field) => (
        <Input
          size="small"
          variant="outlined"
          value={field.cnName || ""}
          placeholder="请输入字段显示名"
          onChange={(event) => changeField(field, { cnName: event.target.value })}
        />
      ),
    },
    {
      key: "primaryFlag",
      title: "主键",
      width: 72,
      align: "center",
      render: (_value, field) => (
        <input
          aria-label="主键"
          type="checkbox"
          checked={Boolean(field.primaryFlag)}
          onChange={(event) => changeField(field, { primaryFlag: event.target.checked })}
          className="size-3.5 cursor-pointer accent-[#315efb]"
        />
      ),
    },
    {
      key: "notNull",
      title: "非空",
      width: 72,
      align: "center",
      render: (_value, field) => (
        <input
          aria-label="非空"
          type="checkbox"
          checked={Boolean(field.notNull)}
          onChange={(event) => changeField(field, { notNull: event.target.checked })}
          className="size-3.5 cursor-pointer accent-[#315efb]"
        />
      ),
    },
    {
      key: "actions",
      title: "操作",
      width: 72,
      align: "center",
      render: (_value, field) => (
        <Button size="sm" variant="ghost" className="!h-6 !px-2 text-xs text-[var(--yak-components-danger-text)]" onClick={() => removeField(field)}>
          <Trash2 size={12} />删除
        </Button>
      ),
    },
  ];

  return (
    <div className="flex h-full min-h-0 w-full min-w-0 flex-1 bg-white">
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex min-h-10 shrink-0 flex-wrap items-center justify-between gap-2 border-b border-[var(--yak-components-panel-border)] px-4 py-1.5">
          <div className="flex flex-wrap items-center gap-1.5">
            <Button size="sm" variant="primary" isDisabled={!dirty || saving || (Boolean(recordId) && (fieldsQuery.isFetching || fieldsQuery.isError))} isPending={saving} onClick={() => void save()}>
              <Save size={13} />保存
            </Button>
            <Button size="sm" variant="secondary" isDisabled={!recordId || dirty || isSubmitted || submitting} isPending={submitting} onClick={() => void submit()}>
              <Send size={13} />提交
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
          <div className="flex shrink-0 items-center gap-2 border-b border-[#dce6fb] bg-[#eef4ff] px-4 py-1.5 text-xs text-[#63748f]">
            <span>{dirty ? "当前存在未保存的变更，请及时保存。" : "当前存在未提交的内容，保存后可点击上方“提交”生成最新版本。"}</span>
            <button type="button" aria-label="关闭提示" className="ml-auto flex size-5 items-center justify-center rounded hover:bg-black/5" onClick={() => setNoticeDismissed(true)}>
              <X size={12} />
            </button>
          </div>
        ) : null}

        <div className="min-h-0 flex-1 overflow-y-auto bg-[var(--yak-color-hover)] p-3.5">
          <div className="space-y-3.5">
            <section className="bg-white p-4">
              <FormSectionHeader title="基本信息" />
              <div className="grid grid-cols-1 gap-x-8 gap-y-3 xl:grid-cols-2">
                <FormField label="数仓分层" required>
                  <OptionSelect
                    value={values.dwhId}
                    options={layerOptions}
                    placeholder={layersQuery.isFetching ? "正在加载数仓分层..." : "请选择数仓分层"}
                    onChange={(value) => {
                      const layer = layers.find((item) => String(item.id || "") === value);
                      const modelType = String(layer?.dwModelType || "");
                      const firstType = (TABLE_TYPES_BY_MODEL[modelType] || [])[0] || "";
                      setValues((current) => ({ ...current, dwhId: value, tableType: firstType }));
                      setDirty(true);
                      onDirtyChange(true);
                    }}
                  />
                </FormField>

                {!isUnassigned ? (
                  <FormField label="模型类型" required>
                    <OptionSelect
                      value={values.tableType}
                      options={tableTypeOptions}
                      placeholder="请选择模型类型"
                      onChange={(value) => changeValue("tableType", value)}
                    />
                  </FormField>
                ) : (
                  <FormField label="模型类型">
                    <Input size="small" variant="outlined" value="未分层不指定模型类型" disabled />
                  </FormField>
                )}

                {dwType === "APPLICATION_LAYER" ? (
                  <FormField label="应用层归属">
                    <OptionSelect
                      value={values.martOrSubject}
                      options={martSubjectOptions}
                      placeholder="请选择数据集市或主题域"
                      onChange={(value) => changeValue("martOrSubject", value)}
                    />
                  </FormField>
                ) : (
                  <>
                    <FormField label="业务分类">
                      <OptionSelect
                        value={values.busTypeId}
                        options={businessTypeOptions}
                        placeholder={businessTypesQuery.isFetching ? "正在加载业务分类..." : "请选择业务分类"}
                        onChange={(value) => changeValue("busTypeId", value)}
                      />
                    </FormField>
                    <FormField label="数据域">
                      <OptionSelect
                        value={values.domainId}
                        options={domainOptions}
                        placeholder={domainsQuery.isFetching ? "正在加载数据域..." : "请选择数据域"}
                        onChange={(value) => {
                          setValues((current) => ({ ...current, domainId: value, busProcessId: "" }));
                          setDirty(true);
                          onDirtyChange(true);
                          setNoticeDismissed(false);
                        }}
                      />
                    </FormField>
                    <FormField label="业务过程">
                      <OptionSelect
                        value={values.busProcessId}
                        options={processOptions}
                        placeholder={processesQuery.isFetching ? "正在加载业务过程..." : "请选择业务过程"}
                        onChange={(value) => changeValue("busProcessId", value)}
                      />
                    </FormField>
                  </>
                )}

                <FormField label="表名" required>
                  <Input size="small" variant="outlined" value={values.tableName} placeholder="请输入英文表名" onChange={(event) => changeValue("tableName", event.target.value)} />
                </FormField>
                <FormField label="表中文名" required>
                  <Input size="small" variant="outlined" value={values.cnName} placeholder="请输入表中文名" onChange={(event) => changeValue("cnName", event.target.value)} />
                </FormField>
                <FormField label="存储策略">
                  <OptionSelect
                    value={values.storagePolicy}
                    options={STORAGE_POLICY_OPTIONS}
                    placeholder="请选择存储策略"
                    onChange={(value) => changeValue("storagePolicy", value)}
                  />
                </FormField>
                <FormField label="生命周期">
                  <div className="flex items-center gap-2">
                    <Input size="small" variant="outlined" type="number" min={0} value={values.lifeCycle} placeholder="请输入" className="max-w-[150px]" onChange={(event) => changeValue("lifeCycle", event.target.value)} />
                    <span className="text-xs text-[var(--yak-components-muted-text)]">天</span>
                  </div>
                </FormField>
                <FormField label="负责人">
                  <Input size="small" variant="outlined" value={values.director} placeholder="请选择或输入负责人" onChange={(event) => changeValue("director", event.target.value)} />
                </FormField>
                <FormField label="描述" className="xl:col-span-2">
                  <Textarea size="small" variant="outlined" rows={3} value={values.remark} placeholder="请输入描述" onValueChange={(value) => changeValue("remark", value)} />
                </FormField>
              </div>
            </section>

            <section className="bg-white p-4">
              <div className="mb-3 flex items-center justify-between gap-3">
                <FormSectionHeader title="字段管理" />
                <Button size="sm" variant="secondary" className="shrink-0" onClick={addField}>
                  <Plus size={13} />添加字段
                </Button>
              </div>
              <div className="min-w-0 overflow-x-auto">
                <Table<DimensionFieldRecord>
                  columns={fieldColumns}
                  dataSource={fields}
                  rowKey={(field) => field.id || field.tempId || field.fieldName}
                  loading={Boolean(recordId && fieldsQuery.isFetching)}
                  emptyText={fieldsQuery.isError ? "字段加载失败，请刷新后重试" : "暂无字段，点击右上角添加字段"}
                  pagination={false}
                  size="small"
                  scroll={{ x: 820 }}
                />
              </div>
            </section>

            <section className="bg-white p-4">
              <FormSectionHeader title="分区字段管理" />
              <div className="flex min-h-16 items-center justify-center rounded border border-dashed border-[var(--yak-components-panel-border)] text-xs text-[var(--yak-components-muted-text)]">
                暂无分区字段配置，分区字段管理将在后续阶段接入
              </div>
            </section>

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
    { key: "sequence", title: "序号", width: 64, align: "center", render: (_value, _record, index) => (pageNo - 1) * pageSize + index + 1 },
    { key: "cnName", title: "表中文名", dataIndex: "cnName", width: 180, ellipsis: true },
    { key: "tableName", title: "表名", dataIndex: "tableName", width: 180, ellipsis: true },
    { key: "tableType", title: "模型类型", width: 110, render: (_value, record) => TABLE_TYPE_LABELS[String(record.tableType || "")] || "-" },
    { key: "dwhName", title: "数仓分层", dataIndex: "dwhName", width: 140, ellipsis: true },
    { key: "submit", title: "状态", width: 88, render: (_value, record) => record.submit === true || record.submit === "true" ? "已提交" : "草稿" },
    { key: "updateTime", title: "最近修改时间", dataIndex: "updateTime", width: 170 },
    { key: "actions", title: "操作", width: 90, align: "center", render: (_value, record) => <Button size="sm" variant="ghost" className="px-1 text-xs" onClick={() => onEdit(record)}>编辑</Button> },
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
            placeholder="搜索表中文名或英文表名"
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
          emptyText="暂无维度表"
          pagination={false}
          size="small"
          scroll={{ x: 980, y: "calc(100vh - 250px)" }}
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
