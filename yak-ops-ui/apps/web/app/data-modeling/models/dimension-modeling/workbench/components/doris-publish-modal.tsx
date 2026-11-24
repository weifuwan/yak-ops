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
import { ChevronDown, ChevronUp, Code2, Copy, Send } from "lucide-react";

import {
  dimensionModelingApi,
  type DimensionFieldRecord,
  type DimensionTableRecord,
  type DorisPartitionUnit,
  type DorisPublishRecord,
  type DorisPublishRequest,
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

const PARTITION_UNIT_OPTIONS: SelectOption[] = [
  { value: "DAY", label: "按天" },
  { value: "WEEK", label: "按周" },
  { value: "MONTH", label: "按月" },
  { value: "QUARTER", label: "按季度" },
  { value: "YEAR", label: "按年" },
];

const DISTRIBUTION_FIELD_OPTIONS: SelectOption[] = [
  { value: "AUTO", label: "自动推荐" },
  { value: "CUSTOM", label: "手动选择" },
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
  onToggle,
}: {
  fields: DimensionFieldRecord[];
  checkedIds: string[];
  disabled?: boolean;
  onToggle: (fieldId: string, checked: boolean) => void;
}) {
  if (!fields.length) {
    return (
      <div className="rounded-md border border-[var(--yak-components-control-border)] px-3 py-3 text-xs text-[var(--yak-components-muted-text)]">
        暂无可选字段
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

  const datasourceQuery = useQuery({
    queryKey: ["datasources", "doris-publish", projectCode],
    queryFn: () => listDataSources({ pageNo: 1, pageSize: 100, dbType: "DORIS" }),
    enabled,
  });

  const [datasourceId, setDatasourceId] = useState("");
  const [targetDatabase, setTargetDatabase] = useState("");
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [customDistribution, setCustomDistribution] = useState(false);
  const [distributionFieldIds, setDistributionFieldIds] = useState<string[]>([]);
  const [bucketCount, setBucketCount] = useState("");
  const [partitionEnabled, setPartitionEnabled] = useState(false);
  const [partitionFieldId, setPartitionFieldId] = useState("");
  const [partitionUnit, setPartitionUnit] = useState<DorisPartitionUnit>("MONTH");
  const [preview, setPreview] = useState<DorisPublishRecord | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    setDatasourceId("");
    setTargetDatabase("");
    setAdvancedOpen(false);
    setCustomDistribution(false);
    setDistributionFieldIds([]);
    setBucketCount("");
    setPartitionEnabled(false);
    setPartitionFieldId("");
    setPartitionUnit("MONTH");
    setPreview(null);
  }, [open, tableId]);

  const physicalFields = useMemo(
    () => fields.filter((field) => field.id),
    [fields],
  );
  const primaryFields = useMemo(
    () => physicalFields.filter((field) => Boolean(field.primaryFlag)),
    [physicalFields],
  );
  const dateFields = useMemo(
    () =>
      physicalFields.filter(
        (field) =>
          field.notNull === true &&
          ["DATE", "DATETIME", "TIMESTAMP"].includes(String(field.dataType).toUpperCase()),
      ),
    [physicalFields],
  );
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
  const dateFieldOptions = useMemo<SelectOption[]>(
    () =>
      dateFields.map((field) => ({
        value: String(field.id),
        label: fieldLabel(field),
      })),
    [dateFields],
  );

  const toggleDistributionField = (id: string, checked: boolean) => {
    setDistributionFieldIds((current) =>
      checked
        ? Array.from(new Set([...current, id]))
        : current.filter((value) => value !== id),
    );
    setPreview(null);
  };

  const request = (): DorisPublishRequest | null => {
    if (!datasourceId) {
      toast.warning("请选择 Doris 数据源");
      return null;
    }
    if (!targetDatabase.trim()) {
      toast.warning("请输入目标数据库");
      return null;
    }
    if (!physicalFields.length) {
      toast.warning("请先配置模型字段");
      return null;
    }
    if (customDistribution && !distributionFieldIds.length) {
      toast.warning("手动分布至少选择一个字段");
      return null;
    }
    if (partitionEnabled && (!partitionFieldId || !partitionUnit)) {
      toast.warning("请选择分区字段和分区周期");
      return null;
    }

    const advanced: NonNullable<DorisPublishRequest["advanced"]> = {};
    if (customDistribution) {
      advanced.customDistribution = true;
      advanced.distributionFieldIds = distributionFieldIds;
    }
    if (bucketCount.trim()) {
      const count = Number(bucketCount);
      if (!Number.isInteger(count) || count <= 0) {
        toast.warning("固定分桶数量必须是大于 0 的整数");
        return null;
      }
      advanced.bucketCount = count;
    }
    if (partitionEnabled) {
      advanced.partition = {
        fieldId: partitionFieldId,
        unit: partitionUnit,
      };
    }

    return {
      datasourceId: Number(datasourceId),
      targetDatabase: targetDatabase.trim(),
      advanced: Object.keys(advanced).length ? advanced : undefined,
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
      if (result.status === "SUCCESS") {
        try {
          onModelUpdated(await dimensionModelingApi.getById(projectCode, tableId));
        } catch {
          // Publish history is already persisted; refreshing the editor is secondary.
        }
        toast.success("Doris 物理表发布成功");
        onOpenChange(false);
      } else {
        toast.warning(result.errorMessage || "发布未成功，请查看发布结果");
      }
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

  const canSubmit =
    !busy &&
    !datasourceQuery.isPending &&
    !datasourceQuery.isError &&
    Boolean(datasourceId && targetDatabase.trim()) &&
    physicalFields.length > 0 &&
    tableType !== "DIM";

  return (
    <Modal
      open={open}
      centered
      width={920}
      maskClosable={false}
      title={
        <div className="flex min-w-0 items-center gap-2">
          <span>发布到 Doris</span>
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
            variant="secondary"
            isDisabled={!canSubmit}
            isPending={busy}
            onClick={() => void previewDdl()}
          >
            <Code2 size={14} />
            预览 DDL
          </Button>
          <Button
            size="sm"
            variant="primary"
            isDisabled={!canSubmit}
            isPending={busy}
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
          <h3 className="mb-3 text-xs font-medium text-[var(--yak-components-field-label)]">
            系统识别结果
          </h3>
          <div className="grid grid-cols-2 gap-3 rounded-md bg-[var(--yak-components-panel-muted)] p-3 text-xs">
            <div className="space-y-1">
              <p className="m-0 text-[var(--yak-components-muted-text)]">建表方式</p>
              <p className="m-0 font-medium">
                {primaryFields.length ? "主键更新表（UNIQUE KEY）" : "普通明细表（DUPLICATE KEY）"}
              </p>
            </div>
            <div className="space-y-1">
              <p className="m-0 text-[var(--yak-components-muted-text)]">主键字段</p>
              <p className="m-0 font-medium">
                {primaryFields.length
                  ? primaryFields.map((field) => field.fieldName).join("、")
                  : "未设置，按明细表创建"}
              </p>
            </div>
            <div className="space-y-1">
              <p className="m-0 text-[var(--yak-components-muted-text)]">数据分布</p>
              <p className="m-0 font-medium">HASH · 自动推荐</p>
            </div>
            <div className="space-y-1">
              <p className="m-0 text-[var(--yak-components-muted-text)]">分桶</p>
              <p className="m-0 font-medium">{bucketCount.trim() ? `固定 ${bucketCount} 个` : "自动分桶"}</p>
            </div>
          </div>
          {!primaryFields.length ? (
            <p className="mb-0 mt-2 text-xs text-[var(--yak-components-muted-text)]">
              当前模型没有设置主键，系统会按普通明细表创建。需要数据更新合并时，请先在字段管理中设置主键。
            </p>
          ) : null}
        </section>

        <section className="border-t border-[var(--yak-components-panel-border)] pt-3">
          <Button
            size="sm"
            variant="ghost"
            className="!px-0"
            onClick={() => setAdvancedOpen((value) => !value)}
          >
            {advancedOpen ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
            高级配置
            <span className="ml-1 text-xs font-normal text-[var(--yak-components-muted-text)]">
              默认使用系统推荐值
            </span>
          </Button>

          {advancedOpen ? (
            <div className="mt-3 space-y-3">
              <Field className={HORIZONTAL_FIELD_CLASS}>
                <FieldLabel className="pt-1.5 text-xs">HASH 分布字段</FieldLabel>
                <div className="space-y-2">
                  <FormSelect
                    value={customDistribution ? "CUSTOM" : "AUTO"}
                    options={DISTRIBUTION_FIELD_OPTIONS}
                    disabled={busy}
                    onChange={(value) => {
                      setCustomDistribution(value === "CUSTOM");
                      setPreview(null);
                    }}
                  />
                  {customDistribution ? (
                    <FieldCheckboxList
                      fields={physicalFields}
                      checkedIds={distributionFieldIds}
                      disabled={busy}
                      onToggle={toggleDistributionField}
                    />
                  ) : (
                    <p className="m-0 text-xs text-[var(--yak-components-muted-text)]">
                      系统优先使用主键字段；没有主键时使用第一个字段。
                    </p>
                  )}
                </div>
              </Field>

              <Field className={HORIZONTAL_FIELD_CLASS}>
                <FieldLabel className="pt-1.5 text-xs">分桶数量</FieldLabel>
                <div>
                  <Input
                    size="small"
                    variant="outlined"
                    type="number"
                    min={1}
                    value={bucketCount}
                    placeholder="留空表示自动分桶"
                    disabled={busy}
                    onChange={(event) => {
                      setBucketCount(event.target.value);
                      setPreview(null);
                    }}
                  />
                  <p className="m-0 mt-1 text-xs text-[var(--yak-components-muted-text)]">
                    通常不需要手动指定，只有明确知道目标集群规划时才设置固定数量。
                  </p>
                </div>
              </Field>

              <Field className={HORIZONTAL_FIELD_CLASS}>
                <FieldLabel className="pt-1.5 text-xs">时间分区</FieldLabel>
                <div className="space-y-2">
                  <label className="flex cursor-pointer items-center gap-2 text-xs">
                    <Checkbox
                      checked={partitionEnabled}
                      disabled={busy || !dateFields.length}
                      onCheckedChange={(checked) => {
                        setPartitionEnabled(checked === true);
                        setPreview(null);
                      }}
                    />
                    启用时间分区
                  </label>
                  {partitionEnabled ? (
                    <div className="grid grid-cols-2 gap-2">
                      <FormSelect
                        value={partitionFieldId}
                        options={dateFieldOptions}
                        placeholder="选择非空时间字段"
                        disabled={busy}
                        onChange={(value) => {
                          setPartitionFieldId(value);
                          setPreview(null);
                        }}
                      />
                      <FormSelect
                        value={partitionUnit}
                        options={PARTITION_UNIT_OPTIONS}
                        disabled={busy}
                        onChange={(value) => {
                          setPartitionUnit(value as DorisPartitionUnit);
                          setPreview(null);
                        }}
                      />
                    </div>
                  ) : (
                    <p className="m-0 text-xs text-[var(--yak-components-muted-text)]">
                      默认不分区。启用后需要选择一个非空 DATE / DATETIME 字段。
                      {!dateFields.length ? " 当前模型没有符合条件的时间字段。" : ""}
                    </p>
                  )}
                </div>
              </Field>
            </div>
          ) : null}
        </section>

        {preview?.ddl ? (
          <section className="border-t border-[var(--yak-components-panel-border)] pt-4">
            <div className="mb-2 flex items-center justify-between gap-2">
              <h3 className="text-xs font-medium text-[var(--yak-components-field-label)]">
                DDL 预览
              </h3>
              <Button size="sm" variant="ghost" onClick={() => void copyDdl()}>
                <Copy size={13} />
                复制
              </Button>
            </div>
            <Textarea
              size="small"
              variant="outlined"
              rows={9}
              readOnly
              value={preview.ddl}
              className="min-h-[180px] resize-y font-mono text-xs leading-5"
            />
          </section>
        ) : null}
      </div>
    </Modal>
  );
}
