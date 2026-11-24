import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Checkbox,
  Field,
  FieldLabel,
  Input,
  Modal,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
  toast,
} from "@yak-ops/yak-ui";
import { Code2, Copy, Save, Send } from "lucide-react";

import {
  dimensionModelingApi,
  type DimensionFieldRecord,
  type DimensionTableRecord,
  type DorisConfig,
  type DorisPublishRecord,
} from "@/service/data-modeling/models/dimension-modeling";
import { listDataSources } from "@/service/datasource";

type Props = {
  projectCode: string;
  tableId: string;
  tableType: string;
  modelName?: string;
  fields: DimensionFieldRecord[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onModelUpdated: (record: DimensionTableRecord) => void;
};

type SelectOption = {
  value: string;
  label: string;
};

const HORIZONTAL_FIELD_CLASS =
  "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";

const DEFAULT_CONFIG: DorisConfig = {
  dataModel: "DUPLICATE",
  keyFieldIds: [],
  distributionType: "HASH",
  distributionFieldIds: [],
  bucketMode: "AUTO",
  partitionStrategy: "NONE",
  tableProperties: {},
};

const DATA_MODEL_OPTIONS: SelectOption[] = [
  { value: "DUPLICATE", label: "DUPLICATE KEY" },
  { value: "UNIQUE", label: "UNIQUE KEY" },
];

const DISTRIBUTION_OPTIONS: SelectOption[] = [{ value: "HASH", label: "HASH" }];

const BUCKET_MODE_OPTIONS: SelectOption[] = [
  { value: "AUTO", label: "自动分桶" },
  { value: "FIXED", label: "固定分桶" },
];

const PARTITION_STRATEGY_OPTIONS: SelectOption[] = [
  { value: "NONE", label: "不分区" },
  { value: "AUTO_RANGE", label: "自动范围分区" },
];

const PARTITION_UNIT_OPTIONS: SelectOption[] = [
  { value: "DAY", label: "日" },
  { value: "WEEK", label: "周" },
  { value: "MONTH", label: "月" },
  { value: "QUARTER", label: "季度" },
  { value: "YEAR", label: "年" },
];

const fieldLabel = (field: DimensionFieldRecord) =>
  [field.cnName, field.fieldName].filter(Boolean).join(" / ");

function FormSelect({
  value,
  options,
  placeholder,
  disabled,
  onChange,
}: {
  value: string;
  options: SelectOption[];
  placeholder?: string;
  disabled?: boolean;
  onChange?: (value: string) => void;
}) {
  const items = Object.fromEntries(options.map((option) => [option.value, option.label]));
  return (
    <Select<string>
      size="small"
      items={items}
      value={value || null}
      disabled={disabled}
      onValueChange={(nextValue) => onChange?.(String(nextValue || ""))}
    >
      <SelectTrigger variant="outlined" className="w-full">
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent emptyContent={options.length ? undefined : "暂无可选项"}>
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

function FieldCheckboxList({
  fields,
  checkedIds,
  disabled,
  emptyText,
  onToggle,
}: {
  fields: DimensionFieldRecord[];
  checkedIds: string[];
  disabled?: boolean;
  emptyText: string;
  onToggle: (fieldId: string, checked: boolean) => void;
}) {
  if (!fields.length) {
    return (
      <div className="rounded-md border border-[var(--yak-components-control-border)] px-3 py-4 text-xs text-[var(--yak-components-muted-text)]">
        {emptyText}
      </div>
    );
  }

  return (
    <div className="grid max-h-36 grid-cols-2 gap-x-4 gap-y-1 overflow-y-auto rounded-md border border-[var(--yak-components-control-border)] px-3 py-2">
      {fields.map((field) => {
        const id = String(field.id);
        return (
          <label
            key={id}
            className="flex min-w-0 cursor-pointer items-center gap-2 rounded px-1 py-1.5 text-xs hover:bg-[var(--yak-color-hover)]"
          >
            <Checkbox
              checked={checkedIds.includes(id)}
              disabled={disabled}
              aria-label={fieldLabel(field)}
              onCheckedChange={(checked) => onToggle(id, checked === true)}
            />
            <span className="truncate">{fieldLabel(field)}</span>
          </label>
        );
      })}
    </div>
  );
}

export function DorisPublishModal({
  projectCode,
  tableId,
  tableType,
  modelName,
  fields,
  open,
  onOpenChange,
  onModelUpdated,
}: Props) {
  const client = useQueryClient();
  const enabled = open && Boolean(projectCode && tableId && tableType !== "DIM");

  const configQuery = useQuery({
    queryKey: ["modeling", "doris-config", projectCode, tableId],
    queryFn: () => dimensionModelingApi.dorisConfig(projectCode, tableId),
    enabled,
  });

  const datasourceQuery = useQuery({
    queryKey: ["datasources", "doris-publish", projectCode],
    queryFn: () => listDataSources({ pageNo: 1, pageSize: 100, dbType: "DORIS" }),
    enabled,
  });

  const [config, setConfig] = useState<DorisConfig>(DEFAULT_CONFIG);
  const [propertiesText, setPropertiesText] = useState("{}");
  const [datasourceId, setDatasourceId] = useState("");
  const [targetDatabase, setTargetDatabase] = useState("");
  const [preview, setPreview] = useState<DorisPublishRecord | null>(null);
  const [configDirty, setConfigDirty] = useState(false);
  const [hasSavedConfig, setHasSavedConfig] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!configQuery.data) return;
    const nextConfig = {
      ...DEFAULT_CONFIG,
      ...configQuery.data,
      tableProperties: configQuery.data.tableProperties || {},
    };
    setConfig(nextConfig);
    setPropertiesText(JSON.stringify(nextConfig.tableProperties, null, 2));
    setConfigDirty(false);
    setHasSavedConfig(true);
  }, [configQuery.data]);

  useEffect(() => {
    if (!open) return;
    setPreview(null);
  }, [open, tableId]);

  const physicalFields = useMemo(() => fields.filter((field) => field.id), [fields]);
  const dorisSources = useMemo(
    () => (datasourceQuery.data?.totalList || []).filter((source) => source.dbType === "DORIS"),
    [datasourceQuery.data?.totalList],
  );
  const datasourceOptions = useMemo<SelectOption[]>(
    () =>
      dorisSources.map((source) => ({
        value: String(source.id),
        label: String(source.name || source.id),
      })),
    [dorisSources],
  );
  const partitionFieldOptions = useMemo<SelectOption[]>(
    () =>
      physicalFields.map((field) => ({
        value: String(field.id),
        label: fieldLabel(field),
      })),
    [physicalFields],
  );

  const change = <K extends keyof DorisConfig>(key: K, value: DorisConfig[K]) => {
    setConfig((current) => ({ ...current, [key]: value }));
    setConfigDirty(true);
    setPreview(null);
  };

  const toggleField = (
    key: "keyFieldIds" | "distributionFieldIds",
    id: string,
    checked: boolean,
  ) => {
    change(
      key,
      checked
        ? Array.from(new Set([...(config[key] || []), id]))
        : (config[key] || []).filter((value) => value !== id),
    );
  };

  const saveConfig = async () => {
    if (!tableId || busy || !physicalFields.length) return;
    setBusy(true);
    try {
      const parsedProperties = JSON.parse(propertiesText || "{}") as unknown;
      if (
        !parsedProperties ||
        typeof parsedProperties !== "object" ||
        Array.isArray(parsedProperties)
      ) {
        throw new Error("Doris 表属性必须是 JSON 对象");
      }

      const payload: DorisConfig = {
        ...config,
        tableProperties: parsedProperties as Record<string, string>,
      };
      const saved = await dimensionModelingApi.saveDorisConfig(
        projectCode,
        tableId,
        payload,
      );
      setConfig({
        ...DEFAULT_CONFIG,
        ...saved,
        tableProperties: saved.tableProperties || {},
      });
      setPropertiesText(JSON.stringify(saved.tableProperties || {}, null, 2));
      setConfigDirty(false);
      setHasSavedConfig(true);
      setPreview(null);
      await client.invalidateQueries({
        queryKey: ["modeling", "doris-config", projectCode, tableId],
      });
      try {
        onModelUpdated(await dimensionModelingApi.getById(projectCode, tableId));
      } catch {
        // The physical configuration is already saved; metadata refresh is secondary.
      }
      toast.success("Doris 物理配置已保存");
    } catch (error) {
      toast.warning(
        error instanceof Error
          ? error.message
          : "保存 Doris 配置失败，请检查表属性 JSON",
      );
    } finally {
      setBusy(false);
    }
  };

  const request = () => {
    if (!datasourceId || !targetDatabase.trim()) {
      toast.warning("请选择 Doris 数据源并填写目标数据库");
      return null;
    }
    if (!hasSavedConfig || configDirty) {
      toast.warning("请先保存 Doris 物理配置");
      return null;
    }
    return {
      datasourceId: Number(datasourceId),
      targetDatabase: targetDatabase.trim(),
    };
  };

  const previewDdl = async () => {
    const payload = request();
    if (!payload || busy) return;
    setBusy(true);
    try {
      setPreview(
        await dimensionModelingApi.previewDorisDdl(projectCode, tableId, payload),
      );
    } catch (error) {
      toast.warning(error instanceof Error ? error.message : "DDL 预览失败");
    } finally {
      setBusy(false);
    }
  };

  const publish = async () => {
    const payload = request();
    if (!payload || busy) return;
    if (
      !window.confirm(
        "发布将创建 Doris 物理表，且不会覆盖已存在的目标表。确认继续？",
      )
    ) {
      return;
    }

    setBusy(true);
    try {
      const result = await dimensionModelingApi.publishDoris(
        projectCode,
        tableId,
        payload,
      );
      setPreview(result);
      await Promise.all([
        client.invalidateQueries({
          queryKey: ["modeling", "doris-publish-history", projectCode, tableId],
        }),
        client.invalidateQueries({
          queryKey: ["modeling", "dimension-tree", projectCode],
        }),
        client.invalidateQueries({
          queryKey: ["modeling", "dimension-versions", projectCode, tableId],
        }),
      ]);
      try {
        onModelUpdated(await dimensionModelingApi.getById(projectCode, tableId));
      } catch {
        // Publish already succeeded; metadata refresh is secondary.
      }
      toast.success("Doris 物理表发布成功");
      onOpenChange(false);
    } catch (error) {
      toast.warning(error instanceof Error ? error.message : "发布失败");
    } finally {
      setBusy(false);
    }
  };

  const copyDdl = async () => {
    if (!preview?.ddl) return;
    try {
      await navigator.clipboard.writeText(preview.ddl);
      toast.success("DDL 已复制");
    } catch {
      toast.warning("复制失败，请手动选择 DDL");
    }
  };

  const canSaveConfig =
    !busy &&
    !configQuery.isPending &&
    !configQuery.isError &&
    physicalFields.length > 0;
  const canPreview =
    !busy &&
    hasSavedConfig &&
    !configDirty &&
    Boolean(datasourceId && targetDatabase.trim());

  return (
    <Modal
      open={open}
      centered
      width={920}
      maskClosable={false}
      title={
        <div className="flex min-w-0 items-center gap-2">
          <span>发布</span>
          <span className="truncate text-xs font-normal text-[var(--yak-components-muted-text)]">
            {modelName || tableId}
          </span>
        </div>
      }
      bodyClassName="py-4"
      onClose={() => {
        if (!busy) onOpenChange(false);
      }}
      footer={
        <>
          <Button
            size="sm"
            variant="secondary"
            isDisabled={busy}
            onClick={() => onOpenChange(false)}
          >
            取消
          </Button>
          <Button
            size="sm"
            variant="primary"
            isPending={busy}
            isDisabled={!canPreview}
            onClick={() => void publish()}
          >
            <Send size={14} />
            发布建表
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <section>
          <h3 className="mb-3 text-xs font-medium text-[var(--yak-components-field-label)]">
            发布目标
          </h3>
          <div className="space-y-2.5">
            <Field className={HORIZONTAL_FIELD_CLASS}>
              <FieldLabel required className="pt-1.5 text-xs">
                Doris 数据源
              </FieldLabel>
              <div className="min-w-0">
                <FormSelect
                  value={datasourceId}
                  options={datasourceOptions}
                  placeholder={
                    datasourceQuery.isPending
                      ? "正在加载 Doris 数据源..."
                      : "请选择 Doris 数据源"
                  }
                  disabled={busy || datasourceQuery.isPending || datasourceQuery.isError}
                  onChange={(value) => {
                    setDatasourceId(value);
                    setPreview(null);
                  }}
                />
                {datasourceQuery.isError ? (
                  <Button
                    size="sm"
                    variant="ghost"
                    className="mt-1 !h-5 !px-0 text-xs"
                    onClick={() => void datasourceQuery.refetch()}
                  >
                    数据源加载失败，重新加载
                  </Button>
                ) : !datasourceQuery.isPending && !datasourceOptions.length ? (
                  <p className="mb-0 mt-1 text-xs text-[var(--yak-components-muted-text)]">
                    暂无可用 Doris 数据源
                  </p>
                ) : null}
              </div>
            </Field>

            <Field className={HORIZONTAL_FIELD_CLASS}>
              <FieldLabel required htmlFor="doris-target-database" className="pt-1.5 text-xs">
                目标数据库
              </FieldLabel>
              <Input
                id="doris-target-database"
                size="small"
                variant="outlined"
                value={targetDatabase}
                placeholder="请输入目标数据库"
                disabled={busy}
                onChange={(event) => {
                  setTargetDatabase(event.target.value);
                  setPreview(null);
                }}
              />
            </Field>
          </div>
        </section>

        <section className="border-t border-[var(--yak-components-panel-border)] pt-4">
          <div className="mb-3 flex items-center justify-between gap-3">
            <h3 className="text-xs font-medium text-[var(--yak-components-field-label)]">
              Doris 物理表配置
            </h3>
            {configDirty ? (
              <span className="text-xs text-[var(--yak-color-warning)]">
                有未保存修改
              </span>
            ) : null}
          </div>

          {configQuery.isPending ? (
            <div className="py-6 text-center text-xs text-[var(--yak-components-muted-text)]">
              正在加载 Doris 物理配置...
            </div>
          ) : configQuery.isError ? (
            <div className="flex items-center justify-center gap-2 py-6">
              <span className="text-xs text-[var(--yak-components-danger-text)]">
                Doris 物理配置加载失败
              </span>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => void configQuery.refetch()}
              >
                重新加载
              </Button>
            </div>
          ) : (
            <div className="space-y-2.5">
              <Field className={HORIZONTAL_FIELD_CLASS}>
                <FieldLabel className="pt-1.5 text-xs">数据模型</FieldLabel>
                <FormSelect
                  value={config.dataModel || "DUPLICATE"}
                  options={DATA_MODEL_OPTIONS}
                  disabled={busy}
                  onChange={(value) =>
                    change("dataModel", value as DorisConfig["dataModel"])
                  }
                />
              </Field>

              <Field className={HORIZONTAL_FIELD_CLASS}>
                <FieldLabel className="pt-1.5 text-xs">Key 字段</FieldLabel>
                <FieldCheckboxList
                  fields={physicalFields}
                  checkedIds={config.keyFieldIds || []}
                  disabled={busy}
                  emptyText="请先保存字段信息"
                  onToggle={(id, checked) =>
                    toggleField("keyFieldIds", id, checked)
                  }
                />
              </Field>

              <Field className={HORIZONTAL_FIELD_CLASS}>
                <FieldLabel className="pt-1.5 text-xs">分布方式</FieldLabel>
                <div className="grid grid-cols-[180px_minmax(0,1fr)] gap-2">
                  <FormSelect
                    value="HASH"
                    options={DISTRIBUTION_OPTIONS}
                    disabled
                  />
                  <FieldCheckboxList
                    fields={physicalFields}
                    checkedIds={config.distributionFieldIds || []}
                    disabled={busy}
                    emptyText="暂无可选字段"
                    onToggle={(id, checked) =>
                      toggleField("distributionFieldIds", id, checked)
                    }
                  />
                </div>
              </Field>

              <Field className={HORIZONTAL_FIELD_CLASS}>
                <FieldLabel className="pt-1.5 text-xs">分桶方式</FieldLabel>
                <div className="grid grid-cols-[180px_minmax(0,1fr)] gap-2">
                  <FormSelect
                    value={config.bucketMode || "AUTO"}
                    options={BUCKET_MODE_OPTIONS}
                    disabled={busy}
                    onChange={(value) =>
                      change("bucketMode", value as DorisConfig["bucketMode"])
                    }
                  />
                  {config.bucketMode === "FIXED" ? (
                    <Input
                      size="small"
                      variant="outlined"
                      type="number"
                      min={1}
                      value={String(config.bucketCount || "")}
                      placeholder="请输入分桶数"
                      disabled={busy}
                      onChange={(event) =>
                        change(
                          "bucketCount",
                          Number(event.target.value) || undefined,
                        )
                      }
                    />
                  ) : (
                    <div />
                  )}
                </div>
              </Field>

              <Field className={HORIZONTAL_FIELD_CLASS}>
                <FieldLabel className="pt-1.5 text-xs">分区策略</FieldLabel>
                <div className="grid grid-cols-3 gap-2">
                  <FormSelect
                    value={config.partitionStrategy || "NONE"}
                    options={PARTITION_STRATEGY_OPTIONS}
                    disabled={busy}
                    onChange={(value) =>
                      change(
                        "partitionStrategy",
                        value as DorisConfig["partitionStrategy"],
                      )
                    }
                  />
                  {config.partitionStrategy === "AUTO_RANGE" ? (
                    <>
                      <FormSelect
                        value={config.partitionFieldId || ""}
                        options={partitionFieldOptions}
                        placeholder="请选择分区字段"
                        disabled={busy}
                        onChange={(value) => change("partitionFieldId", value)}
                      />
                      <FormSelect
                        value={config.partitionUnit || "DAY"}
                        options={PARTITION_UNIT_OPTIONS}
                        disabled={busy}
                        onChange={(value) =>
                          change(
                            "partitionUnit",
                            value as DorisConfig["partitionUnit"],
                          )
                        }
                      />
                    </>
                  ) : null}
                </div>
              </Field>

              <Field className={HORIZONTAL_FIELD_CLASS}>
                <FieldLabel className="pt-1.5 text-xs">表属性</FieldLabel>
                <Textarea
                  size="small"
                  variant="outlined"
                  rows={3}
                  value={propertiesText}
                  disabled={busy}
                  className="min-h-[72px] font-mono text-xs"
                  onValueChange={(value) => {
                    setPropertiesText(String(value || ""));
                    setConfigDirty(true);
                    setPreview(null);
                  }}
                />
              </Field>

              <div className="flex justify-end pl-[116px]">
                <Button
                  size="sm"
                  variant="secondary"
                  isDisabled={!canSaveConfig}
                  isPending={busy}
                  onClick={() => void saveConfig()}
                >
                  <Save size={13} />
                  保存物理配置
                </Button>
              </div>
            </div>
          )}
        </section>

        <section className="border-t border-[var(--yak-components-panel-border)] pt-4">
          <h3 className="mb-3 text-xs font-medium text-[var(--yak-components-field-label)]">
            DDL
          </h3>
          <Field className={HORIZONTAL_FIELD_CLASS}>
            <FieldLabel className="pt-1.5 text-xs">DDL</FieldLabel>
            <div className="min-w-0">
              <div className="mb-2 flex items-center justify-end gap-2">
                {preview?.ddl ? (
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => void copyDdl()}
                  >
                    <Copy size={13} />
                    复制
                  </Button>
                ) : null}
                <Button
                  size="sm"
                  variant="secondary"
                  isDisabled={!canPreview}
                  isPending={busy}
                  onClick={() => void previewDdl()}
                >
                  <Code2 size={13} />
                  预览 DDL
                </Button>
              </div>
              <Textarea
                size="small"
                variant="outlined"
                rows={8}
                readOnly
                value={preview?.ddl || ""}
                className="min-h-[180px] resize-none font-mono text-xs leading-5"
                placeholder={
                  configDirty
                    ? "物理配置有修改，请先保存后预览"
                    : "选择发布目标并保存物理配置后，可预览建表 DDL"
                }
              />
            </div>
          </Field>
        </section>
      </div>
    </Modal>
  );
}
