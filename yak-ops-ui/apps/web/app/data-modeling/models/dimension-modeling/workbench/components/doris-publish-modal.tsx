import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Input, Modal, toast } from "@yak-ops/yak-ui";
import { ChevronDown, ChevronUp, Code2, Copy, Save, Send } from "lucide-react";

import { dimensionModelingApi, type DimensionFieldRecord, type DimensionTableRecord, type DorisConfig, type DorisPublishRecord } from "@/service/data-modeling/models/dimension-modeling";
import { listDataSources } from "@/service/datasource";

type Props = {
  projectCode: string;
  tableId: string;
  tableType: string;
  fields: DimensionFieldRecord[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onModelUpdated: (record: DimensionTableRecord) => void;
};

const DEFAULT_CONFIG: DorisConfig = {
  dataModel: "DUPLICATE",
  keyFieldIds: [],
  distributionType: "HASH",
  distributionFieldIds: [],
  bucketMode: "AUTO",
  partitionStrategy: "NONE",
  tableProperties: {},
};

const fieldLabel = (field: DimensionFieldRecord) =>
  [field.cnName, field.fieldName].filter(Boolean).join(" / ");

export function DorisPublishModal({
  projectCode,
  tableId,
  tableType,
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
  const typeQuery = useQuery({
    queryKey: ["modeling", "doris-field-types", projectCode],
    queryFn: () => dimensionModelingApi.dorisFieldTypes(projectCode),
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
  const [expanded, setExpanded] = useState(false);
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
  const sources = datasourceQuery.data?.totalList || [];
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
        ? [...(config[key] || []), id]
        : (config[key] || []).filter((value) => value !== id),
    );
  };

  const saveConfig = async () => {
    if (!tableId || busy || !physicalFields.length) return;
    setBusy(true);
    try {
      const parsedProperties = JSON.parse(propertiesText || "{}") as unknown;
      if (!parsedProperties || typeof parsedProperties !== "object" || Array.isArray(parsedProperties)) {
        throw new Error("Doris 表属性必须是 JSON 对象");
      }
      const payload: DorisConfig = {
        ...config,
        tableProperties: parsedProperties as Record<string, string>,
      };
      const saved = await dimensionModelingApi.saveDorisConfig(projectCode, tableId, payload);
      setConfig({ ...DEFAULT_CONFIG, ...saved, tableProperties: saved.tableProperties || {} });
      setPropertiesText(JSON.stringify(saved.tableProperties || {}, null, 2));
      setConfigDirty(false);
      setHasSavedConfig(true);
      setPreview(null);
      await client.invalidateQueries({ queryKey: ["modeling", "doris-config", projectCode, tableId] });
      try {
        onModelUpdated(await dimensionModelingApi.getById(projectCode, tableId));
      } catch {
        // The physical configuration is already saved; a metadata refresh must not undo that result.
      }
      toast.success("Doris 物理配置已保存");
    } catch (error) {
      toast.warning(error instanceof Error ? error.message : "保存 Doris 配置失败，请检查表属性 JSON");
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
      setExpanded(true);
      return null;
    }
    return { datasourceId: Number(datasourceId), targetDatabase: targetDatabase.trim() };
  };

  const previewDdl = async () => {
    const payload = request();
    if (!payload || busy) return;
    setBusy(true);
    try {
      setPreview(await dimensionModelingApi.previewDorisDdl(projectCode, tableId, payload));
    } catch (error) {
      toast.warning(error instanceof Error ? error.message : "DDL 预览失败");
    } finally {
      setBusy(false);
    }
  };

  const publish = async () => {
    const payload = request();
    if (!payload || busy) return;
    if (!window.confirm("发布将创建 Doris 物理表，且不会覆盖已存在的目标表。确认继续？")) return;
    setBusy(true);
    try {
      const result = await dimensionModelingApi.publishDoris(projectCode, tableId, payload);
      setPreview(result);
      await Promise.all([
        client.invalidateQueries({ queryKey: ["modeling", "doris-publish-history", projectCode, tableId] }),
        client.invalidateQueries({ queryKey: ["modeling", "dimension-tree", projectCode] }),
        client.invalidateQueries({ queryKey: ["modeling", "dimension-versions", projectCode, tableId] }),
      ]);
      try {
        onModelUpdated(await dimensionModelingApi.getById(projectCode, tableId));
      } catch {
        // Publish succeeded; a failed follow-up read must not report the publish as failed.
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

  const canSaveConfig = !busy && !configQuery.isPending && !configQuery.isError && physicalFields.length > 0;
  const canPreview = !busy && hasSavedConfig && !configDirty && Boolean(datasourceId && targetDatabase.trim());

  return (
    <Modal
      open={open}
      onClose={() => {
        if (!busy) onOpenChange(false);
      }}
      title={
        <div className="flex min-w-0 items-center gap-2">
          <span>发布到 Doris</span>
          <span className="truncate text-xs font-normal text-[var(--yak-components-muted-text)]">
            {tableId}
          </span>
        </div>
      }
      width={1080}
      centered
      maskClosable={false}
      className="!max-h-[calc(100dvh-48px)]"
      bodyClassName="space-y-4 bg-[var(--yak-color-hover)]"
      footer={
        <>
          <Button size="sm" variant="secondary" isDisabled={busy} onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button size="sm" variant="primary" isPending={busy} isDisabled={!canPreview} onClick={() => void publish()}>
            <Send size={14} />
            发布建表
          </Button>
        </>
      }
    >
      <section className="rounded-lg border border-[var(--yak-components-panel-border)] bg-white p-4">
        <div className="mb-3 flex items-center gap-2">
          <span className="flex size-6 items-center justify-center rounded-md bg-[var(--yak-color-hover)] text-xs font-semibold text-[var(--yak-color-primary)]">01</span>
          <h3 className="m-0 text-sm font-semibold">发布目标</h3>
        </div>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <label className="min-w-0 text-xs text-[var(--yak-components-muted-text)]">
            Doris 数据源
            <select
              className="mt-1 h-9 w-full rounded-md border border-[var(--yak-components-control-border)] bg-white px-3 text-sm text-[var(--yak-components-panel-text)] outline-none focus:border-[var(--yak-color-primary)]"
              value={datasourceId}
              disabled={busy || datasourceQuery.isPending}
              onChange={(event) => {
                setDatasourceId(event.target.value);
                setPreview(null);
              }}
            >
              <option value="">请选择 Doris 数据源</option>
              {sources.filter((source) => source.dbType === "DORIS").map((source) => (
                <option key={source.id} value={source.id}>{source.name}</option>
              ))}
            </select>
            {datasourceQuery.isError ? (
              <button type="button" className="mt-1 cursor-pointer text-xs text-[var(--yak-color-primary)] hover:underline" onClick={() => void datasourceQuery.refetch()}>
                数据源加载失败，重新加载
              </button>
            ) : null}
            {!datasourceQuery.isPending && !datasourceQuery.isError && !sources.some((source) => source.dbType === "DORIS") ? (
              <span className="mt-1 block text-xs">暂无可用 Doris 数据源</span>
            ) : null}
          </label>
          <label className="min-w-0 text-xs text-[var(--yak-components-muted-text)]">
            目标数据库
            <Input
              size="small"
              className="mt-1 w-full"
              value={targetDatabase}
              placeholder="请输入目标数据库"
              disabled={busy}
              onChange={(event) => {
                setTargetDatabase(event.target.value);
                setPreview(null);
              }}
            />
          </label>
        </div>
      </section>

      <section className="rounded-lg border border-[var(--yak-components-panel-border)] bg-white p-4">
        <div className="flex items-center justify-between gap-3">
          <button
            type="button"
            className="flex min-w-0 cursor-pointer items-center gap-2 text-left"
            onClick={() => setExpanded((value) => !value)}
            aria-expanded={expanded}
          >
            <span className="flex size-6 items-center justify-center rounded-md bg-[var(--yak-color-hover)] text-xs font-semibold text-[var(--yak-color-primary)]">02</span>
            <span className="text-sm font-semibold">Doris 物理表配置</span>
            {typeQuery.data?.length ? (
              <span className="text-xs text-[var(--yak-components-muted-text)]">支持 {typeQuery.data.length} 种字段类型</span>
            ) : null}
            {expanded ? <ChevronUp size={15} className="text-[var(--yak-components-muted-text)]" /> : <ChevronDown size={15} className="text-[var(--yak-components-muted-text)]" />}
          </button>
          <div className="flex shrink-0 items-center gap-2">
            {configDirty ? <span className="text-xs text-[var(--yak-color-warning)]">有未保存修改</span> : null}
            <Button size="sm" variant="primary" isDisabled={!canSaveConfig} isPending={busy} onClick={() => void saveConfig()}>
              <Save size={13} />
              保存物理配置
            </Button>
          </div>
        </div>

        {expanded ? (
          <div className="mt-4 space-y-4 border-t border-[var(--yak-components-panel-border)] pt-4">
            {configQuery.isPending ? (
              <p className="text-xs text-[var(--yak-components-muted-text)]">正在加载 Doris 物理配置...</p>
            ) : configQuery.isError ? (
              <div className="flex items-center gap-2 text-xs text-[var(--yak-components-danger-text)]">
                <span>Doris 物理配置加载失败</span>
                <Button size="sm" variant="secondary" onClick={() => void configQuery.refetch()}>重新加载</Button>
              </div>
            ) : (
              <>
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                  <label className="text-xs text-[var(--yak-components-muted-text)]">
                    数据模型
                    <select
                      className="mt-1 h-9 w-full rounded-md border border-[var(--yak-components-control-border)] bg-white px-3 text-sm text-[var(--yak-components-panel-text)]"
                      value={config.dataModel || "DUPLICATE"}
                      disabled={busy}
                      onChange={(event) => change("dataModel", event.target.value as DorisConfig["dataModel"])}
                    >
                      <option value="DUPLICATE">DUPLICATE KEY</option>
                      <option value="UNIQUE">UNIQUE KEY</option>
                    </select>
                  </label>
                  <label className="text-xs text-[var(--yak-components-muted-text)]">
                    分布方式
                    <select disabled className="mt-1 h-9 w-full rounded-md border border-[var(--yak-components-control-border)] bg-[var(--yak-color-hover)] px-3 text-sm" value="HASH">
                      <option value="HASH">HASH</option>
                    </select>
                  </label>
                </div>

                <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                  <div className="rounded-md border border-[var(--yak-components-panel-border)] p-3">
                    <div className="mb-2 text-xs font-medium">Key 字段（按选择顺序）</div>
                    <div className="max-h-36 space-y-2 overflow-auto">
                      {physicalFields.map((field) => (
                        <label key={field.id} className="flex cursor-pointer items-center gap-2 text-xs">
                          <input type="checkbox" checked={(config.keyFieldIds || []).includes(String(field.id))} disabled={busy} onChange={(event) => toggleField("keyFieldIds", String(field.id), event.target.checked)} />
                          {fieldLabel(field)}
                        </label>
                      ))}
                      {!physicalFields.length ? <p className="text-xs text-[var(--yak-components-muted-text)]">请先保存字段信息</p> : null}
                    </div>
                  </div>
                  <div className="rounded-md border border-[var(--yak-components-panel-border)] p-3">
                    <div className="mb-2 text-xs font-medium">HASH 分桶字段</div>
                    <div className="max-h-36 space-y-2 overflow-auto">
                      {physicalFields.map((field) => (
                        <label key={field.id} className="flex cursor-pointer items-center gap-2 text-xs">
                          <input type="checkbox" checked={(config.distributionFieldIds || []).includes(String(field.id))} disabled={busy} onChange={(event) => toggleField("distributionFieldIds", String(field.id), event.target.checked)} />
                          {fieldLabel(field)}
                        </label>
                      ))}
                    </div>
                    <div className="mt-3 grid grid-cols-2 gap-2">
                      <select className="h-9 rounded-md border border-[var(--yak-components-control-border)] px-2 text-xs" value={config.bucketMode || "AUTO"} disabled={busy} onChange={(event) => change("bucketMode", event.target.value as DorisConfig["bucketMode"])}>
                        <option value="AUTO">自动分桶</option>
                        <option value="FIXED">固定分桶</option>
                      </select>
                      {config.bucketMode === "FIXED" ? (
                        <Input size="small" type="number" min={1} value={String(config.bucketCount || "")} placeholder="分桶数" disabled={busy} onChange={(event) => change("bucketCount", Number(event.target.value) || undefined)} />
                      ) : null}
                    </div>
                  </div>
                </div>

                <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                  <label className="text-xs text-[var(--yak-components-muted-text)]">
                    分区策略
                    <select className="mt-1 h-9 w-full rounded-md border border-[var(--yak-components-control-border)] px-2 text-xs" value={config.partitionStrategy || "NONE"} disabled={busy} onChange={(event) => change("partitionStrategy", event.target.value as DorisConfig["partitionStrategy"])}>
                      <option value="NONE">不分区</option>
                      <option value="AUTO_RANGE">自动范围分区</option>
                    </select>
                  </label>
                  {config.partitionStrategy === "AUTO_RANGE" ? (
                    <>
                      <label className="text-xs text-[var(--yak-components-muted-text)]">
                        分区字段
                        <select className="mt-1 h-9 w-full rounded-md border border-[var(--yak-components-control-border)] px-2 text-xs" value={config.partitionFieldId || ""} disabled={busy} onChange={(event) => change("partitionFieldId", event.target.value)}>
                          <option value="">请选择字段</option>
                          {physicalFields.map((field) => <option key={field.id} value={field.id}>{fieldLabel(field)}</option>)}
                        </select>
                      </label>
                      <label className="text-xs text-[var(--yak-components-muted-text)]">
                        分区粒度
                        <select className="mt-1 h-9 w-full rounded-md border border-[var(--yak-components-control-border)] px-2 text-xs" value={config.partitionUnit || "DAY"} disabled={busy} onChange={(event) => change("partitionUnit", event.target.value as DorisConfig["partitionUnit"])}>
                          {["DAY", "WEEK", "MONTH", "QUARTER", "YEAR"].map((unit) => (
                            <option key={unit} value={unit}>{({ DAY: "日", WEEK: "周", MONTH: "月", QUARTER: "季度", YEAR: "年" } as Record<string, string>)[unit]}</option>
                          ))}
                        </select>
                      </label>
                    </>
                  ) : null}
                </div>

                <label className="block text-xs text-[var(--yak-components-muted-text)]">
                  Doris 表属性（JSON）
                  <textarea
                    className="mt-1 min-h-20 w-full rounded-md border border-[var(--yak-components-control-border)] p-3 font-mono text-xs text-[var(--yak-components-panel-text)]"
                    value={propertiesText}
                    disabled={busy}
                    onChange={(event) => {
                      setPropertiesText(event.target.value);
                      setConfigDirty(true);
                      setPreview(null);
                    }}
                    spellCheck={false}
                  />
                </label>
              </>
            )}
          </div>
        ) : null}
      </section>

      <section className="rounded-lg border border-[var(--yak-components-panel-border)] bg-white p-4">
        <div className="mb-3 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <span className="flex size-6 items-center justify-center rounded-md bg-[var(--yak-color-hover)] text-xs font-semibold text-[var(--yak-color-primary)]">03</span>
            <h3 className="m-0 text-sm font-semibold">DDL 预览</h3>
          </div>
          <Button size="sm" variant="secondary" isDisabled={!canPreview} isPending={busy} onClick={() => void previewDdl()}>
            <Code2 size={13} />
            预览 DDL
          </Button>
        </div>
        {preview?.ddl ? (
          <div className="overflow-hidden rounded-md border border-[var(--yak-components-panel-border)]">
            <div className="flex items-center justify-between border-b border-[var(--yak-components-panel-border)] px-3 py-2">
              <span className="min-w-0 truncate text-xs font-medium">
                {preview.targetDatabase}.{preview.targetTable}
              </span>
              <Button size="sm" variant="ghost" onClick={() => void copyDdl()}>
                <Copy size={13} />
                复制
              </Button>
            </div>
            <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-all bg-[var(--yak-color-hover)] p-3 text-xs leading-5">{preview.ddl}</pre>
          </div>
        ) : (
          <div className="flex min-h-24 items-center justify-center rounded-md border border-dashed border-[var(--yak-components-panel-border)] text-xs text-[var(--yak-components-muted-text)]">
            {configDirty ? "物理配置有修改，请先保存后预览" : "选择发布目标并保存物理配置后，可预览建表 DDL"}
          </div>
        )}
      </section>
    </Modal>
  );
}
