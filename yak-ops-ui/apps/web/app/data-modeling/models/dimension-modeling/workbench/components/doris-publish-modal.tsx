import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Field,
  FieldLabel,
  Modal,
  Radio,
  RadioGroup,
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
import { Copy, Send } from "lucide-react";

import {
  dimensionModelingApi,
  type DimensionFieldRecord,
  type DimensionTableRecord,
  type DorisPublishMode,
  type DorisPublishRecord,
  type DorisPublishRequest,
  type DorisTableModel,
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

const HORIZONTAL_FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";

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
  onChange: (value: string) => void;
}) {
  const items = Object.fromEntries(options.map((option) => [option.value, option.label]));
  return (
    <Select<string>
      size="small"
      items={items}
      value={value || null}
      disabled={disabled}
      onValueChange={(nextValue) => onChange(String(nextValue || ""))}
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

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "DDL 预览失败，请检查 Doris 数据源连接";
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
  const [tableModel, setTableModel] = useState<DorisTableModel | "">("");
  const [publishMode, setPublishMode] = useState<DorisPublishMode | "">("");
  const [publishRemark, setPublishRemark] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirmDropOpen, setConfirmDropOpen] = useState(false);

  useEffect(() => {
    if (!open) {
      setConfirmDropOpen(false);
      return;
    }
    setDatasourceId("");
    setTableModel("");
    setPublishMode("");
    setPublishRemark("");
    setConfirmDropOpen(false);
  }, [open, tableId]);

  const physicalFields = useMemo(
    () => fields.filter((field) => field.id),
    [fields],
  );
  const fieldSignature = useMemo(
    () => physicalFields
      .map((field) => [
        field.id,
        field.fieldName,
        field.dataType,
        field.length,
        field.fieldPrecision,
        field.fieldScale,
        field.datetimePrecision,
        field.primaryFlag,
        field.notNull,
      ].join(":"))
      .join("|"),
    [physicalFields],
  );

  const dorisSources = useMemo(
    () => (datasourceQuery.data?.totalList || []).filter((source) => source.dbType === "DORIS"),
    [datasourceQuery.data?.totalList],
  );
  const datasourceOptions = useMemo<SelectOption[]>(
    () => dorisSources.map((source) => ({
      value: String(source.id),
      label: String(source.name || source.id),
    })),
    [dorisSources],
  );

  const previewQuery = useQuery({
    queryKey: [
      "modeling",
      "doris-publish-preview",
      projectCode,
      tableId,
      datasourceId,
      tableModel,
      publishMode,
      fieldSignature,
    ],
    queryFn: () => dimensionModelingApi.previewDorisDdl(projectCode, tableId, {
      datasourceId: Number(datasourceId),
      tableModel: tableModel as DorisTableModel,
      publishMode: publishMode as DorisPublishMode,
      publishRemark: "",
    }),
    enabled: enabled
      && Boolean(datasourceId && tableModel && publishMode)
      && physicalFields.length > 0,
    retry: false,
  });

  const preview = previewQuery.data as DorisPublishRecord | undefined;
  const previewError = previewQuery.isError ? errorMessage(previewQuery.error) : "";

  const copyDdl = async () => {
    if (!preview?.ddl) return;
    try {
      await navigator.clipboard.writeText(preview.ddl);
      toast.success("DDL 已复制");
    } catch {
      toast.warning("复制失败，请手动选择 DDL");
    }
  };

  const publish = async (confirmedDrop = false) => {
    if (busy) return;
    if (!datasourceId) {
      toast.warning("请选择 Doris 数据源");
      return;
    }
    if (!tableModel) {
      toast.warning("请选择 Doris 表类型");
      return;
    }
    if (!publishMode) {
      toast.warning("请选择发布模式");
      return;
    }
    if (!publishRemark.trim()) {
      toast.warning("请输入发布备注");
      return;
    }
    if (!physicalFields.length) {
      toast.warning("请先配置模型字段");
      return;
    }
    if (publishMode === "DROP_RECREATE" && !confirmedDrop) {
      setConfirmDropOpen(true);
      return;
    }
    if (previewQuery.isFetching || previewQuery.isError || !preview?.ddl) {
      toast.warning(previewError || "请等待 DDL 预览生成完成");
      return;
    }

    const payload: DorisPublishRequest = {
      datasourceId: Number(datasourceId),
      tableModel,
      publishMode,
      publishRemark: publishRemark.trim(),
      confirmDrop: publishMode === "DROP_RECREATE" && confirmedDrop,
    };

    setBusy(true);
    try {
      const result = await dimensionModelingApi.publishDoris(projectCode, tableId, payload);
      await client.invalidateQueries({
        queryKey: ["modeling", "doris-publish-history", projectCode, tableId],
      });
      if (result.status === "SUCCESS") {
        try {
          onModelUpdated(await dimensionModelingApi.getById(projectCode, tableId));
        } catch {
          // The publish record is already persisted; refreshing the editor is secondary.
        }
        toast.success(result.noChanges ? "目标表结构已是最新，无需变更" : "Doris 物理表发布成功");
        setConfirmDropOpen(false);
        onOpenChange(false);
      } else {
        toast.warning(result.errorMessage || "发布未成功，请查看发布记录");
      }
    } catch (error) {
      toast.warning(error instanceof Error ? error.message : "Doris 发布失败");
      await client.invalidateQueries({
        queryKey: ["modeling", "doris-publish-history", projectCode, tableId],
      });
    } finally {
      setBusy(false);
    }
  };

  const canPublish = enabled
    && !busy
    && !datasourceQuery.isPending
    && !datasourceQuery.isError
    && Boolean(datasourceId && tableModel && publishMode && publishRemark.trim())
    && physicalFields.length > 0
    && !previewQuery.isFetching
    && !previewQuery.isError
    && Boolean(preview?.ddl);

  const previewPlaceholder = !datasourceId || !tableModel || !publishMode
    ? "请选择 Doris 表类型、数据源和发布模式，系统将自动生成 DDL"
    : previewQuery.isFetching
      ? "正在根据当前配置生成 DDL..."
      : previewError || "暂无 DDL";

  return (
    <>
      <Modal
        open={open}
        centered
        width={960}
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
              variant="primary"
              isDisabled={!canPublish}
              isPending={busy}
              onClick={() => void publish()}
            >
              <Send size={14} />
              发布
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <section className="space-y-3">
            <Field className={HORIZONTAL_FIELD_CLASS}>
              <FieldLabel required className="pt-1 text-xs">Doris 表类型</FieldLabel>
              <RadioGroup
                aria-label="Doris 表类型"
                value={tableModel}
                onValueChange={(value) => setTableModel(String(value) as DorisTableModel)}
                className="flex flex-wrap items-center gap-5 pt-1"
              >
                <label htmlFor="doris-table-model-unique" className="inline-flex cursor-pointer items-center gap-2 text-xs">
                  <Radio id="doris-table-model-unique" value="UNIQUE" />
                  <span>主键表（UNIQUE KEY）</span>
                </label>
                <label htmlFor="doris-table-model-duplicate" className="inline-flex cursor-pointer items-center gap-2 text-xs">
                  <Radio id="doris-table-model-duplicate" value="DUPLICATE" />
                  <span>明细表（DUPLICATE KEY）</span>
                </label>
              </RadioGroup>
            </Field>

            <Field className={HORIZONTAL_FIELD_CLASS}>
              <FieldLabel required className="pt-1.5 text-xs">Doris 数据源</FieldLabel>
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
                  onChange={setDatasourceId}
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
                    暂无可用 Doris 数据源，请先在数据源管理中配置。
                  </p>
                ) : null}
              </div>
            </Field>

            <Field className={HORIZONTAL_FIELD_CLASS}>
              <FieldLabel required className="pt-1 text-xs">发布模式</FieldLabel>
              <RadioGroup
                aria-label="Doris 发布模式"
                value={publishMode}
                onValueChange={(value) => setPublishMode(String(value) as DorisPublishMode)}
                className="flex flex-wrap items-center gap-5 pt-1"
              >
                <label htmlFor="doris-publish-incremental" className="inline-flex cursor-pointer items-center gap-2 text-xs">
                  <Radio id="doris-publish-incremental" value="INCREMENTAL" />
                  <span>增量发布</span>
                </label>
                <label htmlFor="doris-publish-drop-recreate" className="inline-flex cursor-pointer items-center gap-2 text-xs">
                  <Radio id="doris-publish-drop-recreate" value="DROP_RECREATE" />
                  <span>删除重建</span>
                </label>
              </RadioGroup>
            </Field>

            <Field className={HORIZONTAL_FIELD_CLASS}>
              <FieldLabel required className="pt-1.5 text-xs">发布备注</FieldLabel>
              <Textarea
                size="small"
                variant="outlined"
                rows={2}
                maxLength={2048}
                value={publishRemark}
                placeholder="请输入本次发布的原因或变更说明"
                disabled={busy}
                onValueChange={(value) => setPublishRemark(String(value || ""))}
              />
            </Field>
          </section>

          <section className="border-t border-[var(--yak-components-panel-border)] pt-4">
            <div className="mb-2 flex items-center justify-between gap-2">
              <div className="flex min-w-0 items-center gap-1.5">
                <h3 className="m-0 text-xs font-medium text-[var(--yak-components-field-label)]">DDL 预览</h3>
                {tableModel ? (
                  <span className="text-[11px] text-[var(--yak-components-muted-text)]">
                    {tableModel === "UNIQUE" ? "UNIQUE KEY · 主键表" : "DUPLICATE KEY · 明细表"}
                  </span>
                ) : null}
                <span className="text-[11px] text-[var(--yak-components-muted-text)]">只读 · 自动生成</span>
              </div>
              <Button
                size="sm"
                variant="ghost"
                isDisabled={!preview?.ddl}
                onClick={() => void copyDdl()}
              >
                <Copy size={13} />
                复制
              </Button>
            </div>
            <Textarea
              size="small"
              variant="outlined"
              rows={12}
              readOnly
              value={preview?.ddl || ""}
              placeholder={previewPlaceholder}
              className="min-h-[260px] resize-y font-mono text-xs leading-5"
            />
            {previewError ? (
              <p className="mb-0 mt-2 text-xs text-[var(--yak-components-danger-text)]">{previewError}</p>
            ) : preview?.noChanges ? (
              <p className="mb-0 mt-2 text-xs text-[var(--yak-components-muted-text)]">
                当前目标表结构与模型一致，本次增量发布不会执行变更。
              </p>
            ) : publishMode === "DROP_RECREATE" && preview?.ddl ? (
              <p className="mb-0 mt-2 text-xs text-[var(--yak-components-danger-text)]">
                删除重建会清空目标表现有数据，发布前需要再次确认。
              </p>
            ) : null}
            {tableType === "DIM" ? (
              <p className="mb-0 mt-2 text-xs text-[var(--yak-components-danger-text)]">
                DIM 是逻辑维度定义，不支持发布为 Doris 物理表。
              </p>
            ) : !physicalFields.length ? (
              <p className="mb-0 mt-2 text-xs text-[var(--yak-components-danger-text)]">
                当前模型还没有字段，请先完成字段管理并提交模型。
              </p>
            ) : null}
          </section>
        </div>
      </Modal>

      <Dialog
        open={confirmDropOpen}
        onOpenChange={(nextOpen) => {
          if (!busy) setConfirmDropOpen(nextOpen);
        }}
      >
        <DialogContent className="w-[440px]">
          <DialogTitle className="text-base font-semibold">确认删除重建？</DialogTitle>
          <DialogDescription className="mt-3 text-sm leading-6 text-[var(--yak-components-muted-text)]">
            即将删除目标表并重新创建，目标表中的现有数据将全部清空。请确认数据已备份，且当前数据允许重建。
          </DialogDescription>
          <div className="mt-6 flex justify-end gap-2">
            <Button
              size="sm"
              variant="secondary"
              isDisabled={busy}
              onClick={() => setConfirmDropOpen(false)}
            >
              返回修改
            </Button>
            <Button
              size="sm"
              variant="danger"
              isPending={busy}
              onClick={() => void publish(true)}
            >
              确认删除并重建
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
