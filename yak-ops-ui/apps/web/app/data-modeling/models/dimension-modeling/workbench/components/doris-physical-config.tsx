import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Input, toast } from "@yak-ops/yak-ui";
import { ChevronDown, ChevronUp, Code2, History, Save, Send } from "lucide-react";
import { dimensionModelingApi, type DimensionFieldRecord, type DorisConfig, type DorisPublishRecord } from "@/service/data-modeling/models/dimension-modeling";
import { listDataSources } from "@/service/datasource";

type Props = { projectCode: string; tableId: string; tableType: string; fields: DimensionFieldRecord[] };

const fieldLabel = (field: DimensionFieldRecord) => [field.cnName, field.fieldName].filter(Boolean).join(" / ");

export function DorisPhysicalConfig({ projectCode, tableId, tableType, fields }: Props) {
  const client = useQueryClient();
  const enabled = Boolean(projectCode && tableId && tableType !== "DIM");
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
  const historyQuery = useQuery({
    queryKey: ["modeling", "doris-publish-history", projectCode, tableId],
    queryFn: () => dimensionModelingApi.dorisPublishHistory(projectCode, tableId),
    enabled,
  });
  const [config, setConfig] = useState<DorisConfig>({
    dataModel: "DUPLICATE", keyFieldIds: [], distributionType: "HASH",
    distributionFieldIds: [], bucketMode: "AUTO", partitionStrategy: "NONE",
    tableProperties: {},
  });
  const [datasourceId, setDatasourceId] = useState("");
  const [targetDatabase, setTargetDatabase] = useState("");
  const [preview, setPreview] = useState<DorisPublishRecord | null>(null);
  const [expanded, setExpanded] = useState(true);
  const [busy, setBusy] = useState(false);
  const [propertiesText, setPropertiesText] = useState("{}");

  useEffect(() => {
    if (!configQuery.data) return;
    setConfig({ ...configQuery.data, tableProperties: configQuery.data.tableProperties || {} });
    setPropertiesText(JSON.stringify(configQuery.data.tableProperties || {}, null, 2));
  }, [configQuery.data]);

  const physicalFields = useMemo(() => fields.filter((field) => field.id), [fields]);
  const sources = datasourceQuery.data?.totalList || [];
  const change = <K extends keyof DorisConfig>(key: K, value: DorisConfig[K]) =>
    setConfig((current) => ({ ...current, [key]: value }));
  const toggleField = (key: "keyFieldIds" | "distributionFieldIds", id: string, checked: boolean) => {
    change(key, checked
      ? [...(config[key] || []), id]
      : (config[key] || []).filter((value) => value !== id));
  };
  const saveConfig = async () => {
    if (!tableId) return;
    setBusy(true);
    try {
      const properties = JSON.parse(propertiesText || "{}") as Record<string, string>;
      const payload = { ...config, tableProperties: properties };
      const saved = await dimensionModelingApi.saveDorisConfig(projectCode, tableId, payload);
      setConfig(saved);
      setPropertiesText(JSON.stringify(saved.tableProperties || {}, null, 2));
      await client.invalidateQueries({ queryKey: ["modeling", "doris-config", projectCode, tableId] });
      toast.success("Doris 物理配置已保存");
    } catch (error) {
      toast.warning(error instanceof Error ? error.message : "保存 Doris 配置失败，请检查表属性 JSON");
    } finally { setBusy(false); }
  };
  const request = () => {
    if (!datasourceId || !targetDatabase.trim()) {
      toast.warning("请选择 Doris 数据源并填写目标数据库");
      return null;
    }
    return { datasourceId: Number(datasourceId), targetDatabase: targetDatabase.trim() };
  };
  const previewDdl = async () => {
    const payload = request(); if (!payload) return;
    setBusy(true);
    try { setPreview(await dimensionModelingApi.previewDorisDdl(projectCode, tableId, payload)); }
    catch (error) { toast.warning(error instanceof Error ? error.message : "DDL 预览失败"); }
    finally { setBusy(false); }
  };
  const publish = async () => {
    const payload = request(); if (!payload) return;
    if (!window.confirm("发布将创建 Doris 物理表，且不会覆盖已存在的目标表。确认继续？")) return;
    setBusy(true);
    try {
      const result = await dimensionModelingApi.publishDoris(projectCode, tableId, payload);
      setPreview(result);
      await Promise.all([
        client.invalidateQueries({ queryKey: ["modeling", "doris-publish-history", projectCode, tableId] }),
        client.invalidateQueries({ queryKey: ["modeling", "dimension-tree", projectCode] }),
      ]);
      toast.success("Doris 物理表发布请求已完成");
    } catch (error) { toast.warning(error instanceof Error ? error.message : "发布失败"); }
    finally { setBusy(false); }
  };

  if (!enabled) return null;
  return (
    <section className="bg-white p-4">
      <div className="mb-3 flex items-center justify-between">
        <div>
          <h3 className="m-0 text-sm font-semibold text-[var(--yak-components-panel-text)]">Doris 物理表配置</h3>
          <p className="mt-1 text-xs text-[var(--yak-components-muted-text)]">配置 Key、分桶与分区规则，保存后可预览并发布建表 DDL{typeQuery.data?.length ? ` · 后端支持 ${typeQuery.data.length} 种字段类型` : ""}</p>
        </div>
        <Button size="sm" variant="secondary" onClick={() => setExpanded((value) => !value)}>
          {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />} {expanded ? "收起" : "展开"}
        </Button>
      </div>
      {expanded ? (
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            <label className="text-xs text-[var(--yak-components-muted-text)]">数据模型
              <select className="mt-1 h-8 w-full rounded-md border border-[var(--yak-components-panel-border)] bg-white px-2 text-xs text-[var(--yak-components-panel-text)]" value={config.dataModel || "DUPLICATE"} onChange={(e) => change("dataModel", e.target.value as DorisConfig["dataModel"])}>
                <option value="DUPLICATE">DUPLICATE KEY</option><option value="UNIQUE">UNIQUE KEY</option>
              </select>
            </label>
            <label className="text-xs text-[var(--yak-components-muted-text)]">分布方式
              <select disabled className="mt-1 h-8 w-full rounded-md border border-[var(--yak-components-panel-border)] bg-[#f7f8fa] px-2 text-xs" value="HASH"><option value="HASH">HASH</option></select>
            </label>
          </div>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="rounded-md border border-[var(--yak-components-panel-border)] p-3">
              <div className="mb-2 text-xs font-medium">Key 字段（按选择顺序）</div>
              <div className="max-h-36 space-y-2 overflow-auto">
                {physicalFields.map((field) => <label key={field.id} className="flex cursor-pointer items-center gap-2 text-xs"><input type="checkbox" checked={(config.keyFieldIds || []).includes(String(field.id))} onChange={(e) => toggleField("keyFieldIds", String(field.id), e.target.checked)} />{fieldLabel(field)}</label>)}
                {!physicalFields.length ? <p className="text-xs text-[var(--yak-components-muted-text)]">请先保存字段信息</p> : null}
              </div>
            </div>
            <div className="rounded-md border border-[var(--yak-components-panel-border)] p-3">
              <div className="mb-2 text-xs font-medium">HASH 分桶字段</div>
              <div className="max-h-36 space-y-2 overflow-auto">
                {physicalFields.map((field) => <label key={field.id} className="flex cursor-pointer items-center gap-2 text-xs"><input type="checkbox" checked={(config.distributionFieldIds || []).includes(String(field.id))} onChange={(e) => toggleField("distributionFieldIds", String(field.id), e.target.checked)} />{fieldLabel(field)}</label>)}
              </div>
              <div className="mt-3 grid grid-cols-2 gap-2">
                <select className="h-8 rounded-md border border-[var(--yak-components-panel-border)] px-2 text-xs" value={config.bucketMode || "AUTO"} onChange={(e) => change("bucketMode", e.target.value as DorisConfig["bucketMode"])}><option value="AUTO">自动分桶</option><option value="FIXED">固定分桶</option></select>
                {config.bucketMode === "FIXED" ? <Input size="sm" type="number" min={1} value={String(config.bucketCount || "")} placeholder="分桶数" onChange={(e) => change("bucketCount", Number(e.target.value) || undefined)} /> : null}
              </div>
            </div>
          </div>
          <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
            <label className="text-xs text-[var(--yak-components-muted-text)]">分区策略<select className="mt-1 h-8 w-full rounded-md border border-[var(--yak-components-panel-border)] px-2 text-xs" value={config.partitionStrategy || "NONE"} onChange={(e) => change("partitionStrategy", e.target.value as DorisConfig["partitionStrategy"])}><option value="NONE">不分区</option><option value="AUTO_RANGE">自动范围分区</option></select></label>
            {config.partitionStrategy === "AUTO_RANGE" ? <>
              <label className="text-xs text-[var(--yak-components-muted-text)]">分区字段<select className="mt-1 h-8 w-full rounded-md border border-[var(--yak-components-panel-border)] px-2 text-xs" value={config.partitionFieldId || ""} onChange={(e) => change("partitionFieldId", e.target.value)}><option value="">请选择字段</option>{physicalFields.map((field) => <option key={field.id} value={field.id}>{fieldLabel(field)}</option>)}</select></label>
              <label className="text-xs text-[var(--yak-components-muted-text)]">分区粒度<select className="mt-1 h-8 w-full rounded-md border border-[var(--yak-components-panel-border)] px-2 text-xs" value={config.partitionUnit || "DAY"} onChange={(e) => change("partitionUnit", e.target.value as DorisConfig["partitionUnit"])}>{["DAY","WEEK","MONTH","QUARTER","YEAR"].map((unit) => <option key={unit} value={unit}>{({DAY:"日",WEEK:"周",MONTH:"月",QUARTER:"季度",YEAR:"年"} as Record<string,string>)[unit]}</option>)}</select></label>
            </> : null}
          </div>
          <label className="block text-xs text-[var(--yak-components-muted-text)]">Doris 表属性（JSON）
            <textarea className="mt-1 min-h-20 w-full rounded-md border border-[var(--yak-components-panel-border)] p-2 font-mono text-xs" value={propertiesText} onChange={(e) => setPropertiesText(e.target.value)} spellCheck={false} />
          </label>
          <div className="flex justify-end"><Button size="sm" variant="primary" isDisabled={busy || !physicalFields.length} onClick={() => void saveConfig()}><Save size={14} />保存物理配置</Button></div>
          <div className="border-t border-[var(--yak-components-panel-border)] pt-3">
            <div className="mb-2 text-xs font-semibold">发布到 Doris</div>
            <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_1fr_auto_auto]">
              <select className="h-8 min-w-0 rounded-md border border-[var(--yak-components-panel-border)] bg-white px-2 text-xs" value={datasourceId} onChange={(e) => setDatasourceId(e.target.value)}><option value="">选择 Doris 数据源</option>{sources.filter((source) => source.dbType === "DORIS").map((source) => <option key={source.id} value={source.id}>{source.name}</option>)}</select>
              <Input size="sm" value={targetDatabase} placeholder="目标数据库" onChange={(e) => setTargetDatabase(e.target.value)} />
              <Button size="sm" variant="secondary" isDisabled={busy} onClick={() => void previewDdl()}><Code2 size={14} />预览 DDL</Button>
              <Button size="sm" variant="primary" isDisabled={busy} onClick={() => void publish()}><Send size={14} />发布建表</Button>
            </div>
            {datasourceQuery.isError ? <p className="mt-2 text-xs text-red-600">Doris 数据源加载失败</p> : null}
            {preview?.ddl ? <div className="mt-3 rounded-md border border-[var(--yak-components-panel-border)]">
              <div className="border-b border-[var(--yak-components-panel-border)] px-3 py-2 text-xs font-medium">DDL 预览 · {preview.targetDatabase}.{preview.targetTable}</div>
              <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-all bg-[#f8fafc] p-3 text-xs leading-5">{preview.ddl}</pre>
            </div> : null}
          </div>
          <div className="border-t border-[var(--yak-components-panel-border)] pt-3">
            <div className="mb-2 flex items-center gap-2 text-xs font-semibold"><History size={14} />发布历史</div>
            <div className="space-y-2">
              {(historyQuery.data || []).map((item) => <div key={item.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-[#f8fafc] px-3 py-2 text-xs"><span>{item.targetDatabase}.{item.targetTable} · V{item.versionNumber ?? "-"}</span><span>{item.status || "-"} · {item.createTime || item.startedAt || "-"}</span>{item.errorMessage ? <span className="w-full text-red-600">{item.errorMessage}</span> : null}</div>)}
              {!historyQuery.isFetching && !historyQuery.data?.length ? <p className="text-xs text-[var(--yak-components-muted-text)]">暂无发布记录</p> : null}
            </div>
          </div>
          <p className="text-[11px] text-[var(--yak-components-muted-text)]">发布前请先保存基本信息、字段和物理配置。目标表已存在时，后端会拒绝覆盖。</p>
        </div>
      ) : null}
    </section>
  );
}
