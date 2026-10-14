from apply_model import ROOT, edit, write
import re
S='yak-ops-ui/apps/web/service/data-sync/'
A='yak-ops-ui/apps/web/app/data-sync/'

path=S+'types.ts'
edit(path,'export type DataSyncSolutionType = "SINGLE_TABLE_OFFLINE" | "SINGLE_TABLE_REALTIME";', 'export type DataSyncSolutionType = "SINGLE_TABLE_OFFLINE" | "SINGLE_TABLE_REALTIME" | "DATABASE_OFFLINE";')
edit(path,'export interface DataSyncParameters {','''export interface DataSyncTableMapping {
  source: DataSyncTablePath;
  target: DataSyncTablePath;
  writeMode: DataSyncWriteMode;
}

export interface DataSyncTableResult {
  mappingIndex: number;
  step: "FULL";
  status: "WAITING" | "RUNNING" | "FINISHED" | "FAILED" | "CANCELED";
  progressPercent: number | null;
  progressUnit: "SPLITS";
  readRows: number;
  committedRows: number;
  plannedSplits: number | null;
  completedSplits: number;
  failureCode: string | null;
}

export interface DataSyncExecutionReport {
  version: 1;
  observedAt: string;
  tables: DataSyncTableResult[];
}

export interface DataSyncParameters {''')
edit(path,'''  tableMappings: Array<{
    source: DataSyncTablePath;
    target: DataSyncTablePath;
    writeMode: DataSyncWriteMode;
  }>;''', '  tableMappings: DataSyncTableMapping[];')
edit(path,'export interface DataSyncTaskRecord {\n  id: string;', 'export interface DataSyncTaskRecord {\n  id: string;\n  tableMappings: DataSyncTableMapping[];')
edit(path,'export interface DataSyncTaskSavePayload extends DataSyncTaskSaveBase {', 'export interface DataSyncTaskSavePayload extends DataSyncTaskSaveBase {\n  tableMappings?: DataSyncTableMapping[];')
edit(path,'export interface DataSyncDefinitionSnapshot {', 'export interface DataSyncDefinitionSnapshot {\n  tableMappings?: DataSyncTableMapping[];')
edit(path,'export interface DataSyncInstanceRecord {', 'export interface DataSyncInstanceRecord {\n  executionReport?: DataSyncExecutionReport;')
path=S+'draft.ts';edit(path,'["SINGLE_TABLE_OFFLINE", "SINGLE_TABLE_REALTIME"].includes(draft.solutionType)', '["SINGLE_TABLE_OFFLINE", "SINGLE_TABLE_REALTIME", "DATABASE_OFFLINE"].includes(draft.solutionType)')
path=S+'parameters.ts'
edit(path,'new Set(["SINGLE_TABLE_OFFLINE", "SINGLE_TABLE_REALTIME"])','new Set(["SINGLE_TABLE_OFFLINE", "SINGLE_TABLE_REALTIME", "DATABASE_OFFLINE"])')
edit(path,'    value.tableMappings.length !== 1 ||','    value.tableMappings.length < 1 ||\n    value.tableMappings.length > 100 ||\n    (value.solutionType !== "DATABASE_OFFLINE" && value.tableMappings.length !== 1) ||')
edit(path,'  const mapping = value.tableMappings[0];','  const sources = new Set<string>();\n  const targets = new Set<string>();\n  for (const mapping of value.tableMappings) {')
edit(path,'  return value;','''    const sourceKey = JSON.stringify([mapping.source.database || "", mapping.source.schema || "", mapping.source.table]);
    const targetKey = JSON.stringify([mapping.target.database || "", mapping.target.schema || "", mapping.target.table]);
    if (sources.has(sourceKey) || targets.has(targetKey)) throw new Error("来源表和去向表不能重复");
    sources.add(sourceKey);
    targets.add(targetKey);
  }
  return value;''')
path=S+'definition.ts'
edit(path,'  DataSyncParameters,', '  DataSyncParameters,\n  DataSyncTableMapping,')
edit(path,'''  return {
    solutionType: task.solutionType,''', '''  let tableMappings: DataSyncTableMapping[] = [{
    source: { database: task.sourceDatabase, schema: task.sourceSchema, table: task.sourceTable },
    target: { database: task.targetDatabase, schema: task.targetSchema, table: task.targetTable },
    writeMode: task.writeMode,
  }];
  if (task.solutionType === "DATABASE_OFFLINE") {
    if (!task.tableMappings?.length) throw new Error("请选择同步表");
    tableMappings = task.tableMappings.map((mapping) => ({
      source: { ...mapping.source }, target: { ...mapping.target }, writeMode: mapping.writeMode,
    }));
  }
  return {
    solutionType: task.solutionType,''')
p=ROOT/path; text=p.read_text(); start=text.index('    tableMappings: ['); end=text.index('\n  };',start); text=text[:start]+'    tableMappings,'+text[end:];p.write_text(text)
edit(path,'''    id: definition.id,
    name: definition.name,''', '''    id: definition.id,
    tableMappings: parameters.tableMappings.map((table) => ({
      source: { database: table.source.database, schema: table.source.schema, table: table.source.table },
      target: { database: table.target.database, schema: table.target.schema, table: table.target.table },
      writeMode: table.writeMode,
    })),
    name: definition.name,''')
path=A+'solution-presentation.ts';edit(path,'  SINGLE_TABLE_OFFLINE: "单表离线",', '  SINGLE_TABLE_OFFLINE: "单表离线",\n  DATABASE_OFFLINE: "整库离线",')
path=A+'index.tsx';edit(path,'if (initial === "SINGLE_TABLE_OFFLINE" || initial === "SINGLE_TABLE_REALTIME") return initial;', 'if (initial === "SINGLE_TABLE_OFFLINE" || initial === "SINGLE_TABLE_REALTIME" || initial === "DATABASE_OFFLINE") return initial;')
# Rows are mapped from the exact parameter version used by each Instance.
path=S+'execution.ts'
edit(path,'import { readSyncParameters, solutionSyncType } from "./parameters.ts";', 'import { readSyncParameters, solutionSyncType } from "./parameters.ts";\nimport { readExecutionReport } from "./report.ts";')
edit(path,'    id: instance.id,', '    id: instance.id,\n    executionReport: readExecutionReport(instance.executionReport, parameters.tableMappings.length),')
edit(path,'    definitionSnapshot: {', '    definitionSnapshot: {\n      tableMappings: parameters.tableMappings,')
path='yak-ops-ui/apps/web/service/task-execution/types.ts'
edit(path,'  progressPercent?: number | null;', '  progressPercent?: number | null;\n  executionReport?: string | null;')
write(S+'report.ts','''
import type { DataSyncExecutionReport } from "./types";

/** Invalid or absent observations never become a successful table or an invented zero. */
export function readExecutionReport(content: string | null | undefined, tableCount: number): DataSyncExecutionReport | undefined {
  if (!content) return undefined;
  try {
    const report = JSON.parse(content) as DataSyncExecutionReport;
    if (report.version !== 1 || !Number.isFinite(Date.parse(report.observedAt)) ||
        !Array.isArray(report.tables) || report.tables.length !== tableCount) return undefined;
    const indexes = new Set<number>();
    for (const table of report.tables) {
      if (!Number.isInteger(table.mappingIndex) || table.mappingIndex < 0 || table.mappingIndex >= tableCount ||
          indexes.has(table.mappingIndex) || table.step !== "FULL" || table.progressUnit !== "SPLITS" ||
          !["WAITING", "RUNNING", "FINISHED", "FAILED", "CANCELED"].includes(table.status) ||
          ![table.readRows, table.committedRows, table.completedSplits].every((value) => Number.isSafeInteger(value) && value >= 0) ||
          (table.progressPercent !== null && (!Number.isInteger(table.progressPercent) || table.progressPercent < 0 || table.progressPercent > 100)) ||
          (table.plannedSplits !== null && (!Number.isInteger(table.plannedSplits) || table.plannedSplits < 0))) return undefined;
      indexes.add(table.mappingIndex);
    }
    return report;
  } catch {
    return undefined;
  }
}
''')
write(A+'table-mapping-editor.tsx','''
import { Alert, Button, Checkbox, Input, Table, type TableColumns } from "@yak-ops/yak-ui";
import { useMemo, useState } from "react";
import type { DataSourceCatalogTable } from "@/service/datasource";
import type { DataSyncTableMapping, DataSyncTablePath, DataSyncWriteMode } from "@/service/data-sync";
import { DataSyncSearchableSelect } from "./searchable-select";

export const tableKey = (table: DataSyncTablePath) => JSON.stringify([table.database || "", table.schema || "", table.table]);
const pathText = (table: DataSyncTablePath) => [table.database, table.schema, table.table].filter(Boolean).join(".");
const catalogPath = (table: DataSourceCatalogTable): DataSyncTablePath => ({ database: table.database, schema: table.schema, table: table.name });

interface Props {
  sourceTables: DataSourceCatalogTable[];
  targetTables: DataSourceCatalogTable[];
  mappings: DataSyncTableMapping[];
  loading: boolean;
  error: string;
  refresh: () => void;
  onChange: (mappings: DataSyncTableMapping[]) => void;
}

export function ExplicitTableSelection({ sourceTables, targetTables, mappings, loading, error, refresh, onChange }: Props) {
  const [keyword, setKeyword] = useState("");
  const selected = new Set(mappings.map((mapping) => tableKey(mapping.source)));
  const available = useMemo(() => sourceTables.map((table) => ({ ...catalogPath(table), id: tableKey(catalogPath(table)) })), [sourceTables]);
  const visible = available.filter((table) => pathText(table).toLowerCase().includes(keyword.trim().toLowerCase()));
  const add = (tables: DataSyncTablePath[]) => {
    const missing = tables.filter((table) => !selected.has(tableKey(table)));
    if (mappings.length + missing.length > 100) return;
    onChange([...mappings, ...missing.map((source) => {
      const matches = targetTables.filter((target) => target.name === source.table);
      let target: DataSyncTablePath = { table: "" };
      if (matches.length === 1) target = catalogPath(matches[0]);
      return { source: { database: source.database, schema: source.schema, table: source.table }, target, writeMode: "APPEND" as const };
    })]);
  };
  const columns: TableColumns<(typeof available)[number]> = [
    { key: "selected", title: "", width: 44, render: (_value, row) => (
      <Checkbox aria-label={`选择 ${pathText(row)}`} checked={selected.has(row.id)}
        disabled={!selected.has(row.id) && mappings.length >= 100}
        onCheckedChange={(checked) => checked ? add([row]) : onChange(mappings.filter((mapping) => tableKey(mapping.source) !== row.id))} />
    ) },
    { key: "name", title: "来源表", render: (_value, row) => <span className="break-all">{pathText(row)}</span> },
  ];
  return <div className="space-y-3 rounded-lg border border-[#e6e8eb] bg-white p-4">
    <div className="flex flex-wrap items-center gap-2">
      <Input size="small" variant="outlined" placeholder="搜索来源表" value={keyword} onChange={(event) => setKeyword(event.target.value)} />
      <Button size="small" onClick={() => add(visible)} disabled={loading || mappings.length + visible.filter((table) => !selected.has(table.id)).length > 100}>选择当前结果</Button>
      <Button size="small" onClick={refresh}>刷新</Button>
      <span className="text-xs text-[#667085]">已选 {mappings.length} / 100</span>
    </div>
    {error ? <Alert>{error}</Alert> : null}
    <div className="max-h-72 overflow-auto">
      <Table columns={columns} dataSource={visible} rowKey="id" size="small" loading={loading} pagination={false} emptyText="暂无来源表" />
    </div>
  </div>;
}

interface MappingProps {
  mappings: DataSyncTableMapping[];
  targetTables: DataSourceCatalogTable[];
  loading: boolean;
  error: string;
  refresh: () => void;
  onChange: (mappings: DataSyncTableMapping[]) => void;
}

export function TableMappingEditor({ mappings, targetTables, loading, error, refresh, onChange }: MappingProps) {
  const targetOptions = targetTables.map((table) => ({ value: tableKey(catalogPath(table)), label: pathText(catalogPath(table)) }));
  const patch = (index: number, value: Partial<DataSyncTableMapping>) => onChange(mappings.map((mapping, i) => i === index ? { ...mapping, ...value } : mapping));
  const rows = mappings.map((mapping, index) => ({ ...mapping, id: tableKey(mapping.source), index }));
  const columns: TableColumns<(typeof rows)[number]> = [
    { key: "source", title: "来源表", minWidth: 180, render: (_value, row) => <span className="break-all">{pathText(row.source)}</span> },
    { key: "target", title: "去向表", minWidth: 240, render: (_value, row) => {
      const duplicate = row.target.table && mappings.some((mapping, i) => i !== row.index && tableKey(mapping.target) === tableKey(row.target));
      return <div className="space-y-1"><DataSyncSearchableSelect aria-label={`去向表 ${pathText(row.source)}`}
        value={row.target.table ? tableKey(row.target) : null} options={targetOptions} disabled={loading}
        placeholder="请选择已有目标表" onValueChange={(value) => {
          const table = targetTables.find((entry) => tableKey(catalogPath(entry)) === value);
          if (table) patch(row.index, { target: catalogPath(table) });
        }} />
        {duplicate ? <span role="alert" className="text-xs text-[#d92d20]">去向表不能重复</span> : null}
      </div>;
    } },
    { key: "writeMode", title: "写入方式", width: 160, render: (_value, row) => <DataSyncSearchableSelect
      value={row.writeMode} options={[{ value: "APPEND", label: "追加写入" }, { value: "UPSERT", label: "按主键更新" }]}
      onValueChange={(value) => patch(row.index, { writeMode: value as DataSyncWriteMode })} /> },
    { key: "actions", title: "", width: 64, render: (_value, row) => <Button size="small" variant="ghost" onClick={() => onChange(mappings.filter((_mapping, i) => i !== row.index))}>移除</Button> },
  ];
  return <div className="space-y-3 rounded-lg border border-[#e6e8eb] bg-white p-4">
    <div className="flex items-center gap-2">
      <div className="w-44"><DataSyncSearchableSelect value={null} placeholder="批量设置写入方式"
        options={[{ value: "APPEND", label: "追加写入" }, { value: "UPSERT", label: "按主键更新" }]}
        onValueChange={(value) => onChange(mappings.map((mapping) => ({ ...mapping, writeMode: value as DataSyncWriteMode })))} /></div>
      <Button size="small" onClick={refresh}>刷新目标表</Button>
    </div>
    {error ? <Alert>{error}</Alert> : null}
    <Table columns={columns} dataSource={rows} rowKey="id" size="small" scroll={{ x: 750 }} pagination={false} emptyText="请选择来源表" />
  </div>;
}

export function validTableMappings(mappings: DataSyncTableMapping[], targetTables: DataSourceCatalogTable[]) {
  if (!mappings.length || mappings.length > 100) return false;
  const sources = new Set(mappings.map((mapping) => tableKey(mapping.source)));
  const targets = new Set(mappings.map((mapping) => tableKey(mapping.target)));
  const available = new Set(targetTables.map((table) => tableKey(catalogPath(table))));
  return sources.size === mappings.length && targets.size === mappings.length &&
    mappings.every((mapping) => Boolean(mapping.source.table) && Boolean(mapping.target.table) && available.has(tableKey(mapping.target)));
}
''')
# Compose the existing editor; retain single-table and scheduling behavior.
path=A+'task-editor.tsx'
edit(path,'  type DataSyncDraft,','  type DataSyncDraft,\n  type DataSyncTableMapping,')
edit(path,'import { useSolutions } from "./use-solutions";', 'import { useSolutions } from "./use-solutions";\nimport { ExplicitTableSelection, TableMappingEditor, validTableMappings } from "./table-mapping-editor";\nimport { Modal } from "@yak-ops/yak-ui";')
edit(path,'  const localScroll = true;','''  const batch = draft.solutionType === "DATABASE_OFFLINE";
  const [mappings, setMappings] = useState<DataSyncTableMapping[]>(initialTask?.tableMappings || draft.form?.tableMappings || []);
  const [pendingConnection, setPendingConnection] = useState<{ side: "source" | "target"; value: string }>();
  const localScroll = true;''')
edit(path,'      form: { ...form, solutionType: draft.solutionType, steps: draft.steps },', '      form: { ...form, solutionType: draft.solutionType, steps: draft.steps, tableMappings: batch ? mappings : undefined },')
edit(path,'[id, draftKey, draft.sourceType, draft.targetType, draft.solutionType, draft.steps, form]', '[id, draftKey, draft.sourceType, draft.targetType, draft.solutionType, draft.steps, form, batch, mappings]')
edit(path,'      writeMode: form.writeMode,\n    };','      writeMode: form.writeMode,\n      tableMappings: batch ? mappings : undefined,\n    };')
edit(path,'''    Boolean(
      form.sourceDataSourceId &&
      form.sourceTable &&
      form.targetDataSourceId &&
      selectedTableKey(
        targetCatalog.tables,
        form.targetDatabase,
        form.targetSchema,
        form.targetTable,
      ),
    )''', '''    Boolean(form.sourceDataSourceId && form.targetDataSourceId) &&
    (batch ? validTableMappings(mappings, targetCatalog.tables) : Boolean(form.sourceTable && selectedTableKey(
      targetCatalog.tables, form.targetDatabase, form.targetSchema, form.targetTable,
    )))''')
# Move connection changes into an explicit confirmation flow when a batch mapping would be discarded.
p=ROOT/path; text=p.read_text()
pattern=r'onDataSourceChange=\{\(value\) => \{(.*?)\n                \}\}'
found=list(re.finditer(pattern,text,re.S)); assert len(found)==2,len(found)
bodies=[m.group(1) for m in found]
for match,side in reversed(list(zip(found,['source','target']))):
    text=text[:match.start()]+f'onDataSourceChange={{(value) => requestConnectionChange("{side}", value)}}'+text[match.end():]
helper='''  const updateMappings = (value: DataSyncTableMapping[]) => {
    setMappings(value);
    setTaskDirty(true);
    setParametersDirty(true);
  };
  const changeConnection = (side: "source" | "target", value: string) => {
    setMappings([]);
    if (side === "source") {'''+bodies[0]+'''
    } else {'''+bodies[1]+'''
    }
  };
  const requestConnectionChange = (side: "source" | "target", value: string) => {
    if (batch && mappings.length) setPendingConnection({ side, value });
    else changeConnection(side, value);
  };

'''
pos=text.index('  const payload =');text=text[:pos]+helper+text[pos:]
for section,component in [('source','ExplicitTableSelection'),('target','TableMappingEditor')]:
    a=text.index(f'<CollapseSection id="{section}"'); st=text.index('<TableSection',a); en=text.index('</TableSection>',st)+len('</TableSection>')
    original=text[st:en]
    if section=='source':
        replacement='''{batch ? <ExplicitTableSelection sourceTables={sourceCatalog.tables} targetTables={targetCatalog.tables}
                mappings={mappings} loading={sourceCatalog.loading} error={sourceCatalog.error}
                refresh={sourceCatalog.refresh} onChange={updateMappings} /> : ('''+original+''')}'''
    else:
        replacement='''{batch ? <TableMappingEditor mappings={mappings} targetTables={targetCatalog.tables}
                loading={targetCatalog.loading} error={targetCatalog.error} refresh={targetCatalog.refresh}
                onChange={updateMappings} /> : ('''+original+''')}'''
    text=text[:st]+replacement+text[en:]
end=text.rfind('    </div>\n  );'); assert end>=0
text=text[:end]+'''      {pendingConnection ? <Modal open centered width={420} title="更换数据源" onClose={() => setPendingConnection(undefined)}
        footer={<><Button size="small" onClick={() => setPendingConnection(undefined)}>取消</Button>
          <Button size="small" variant="primary" onClick={() => { changeConnection(pendingConnection.side, pendingConnection.value); setPendingConnection(undefined); }}>确认</Button></>}>
        <p className="text-sm">更换数据源将清除已选择的表映射。</p>
      </Modal> : null}
''' +text[end:];p.write_text(text)
# A dedicated result table uses pinned mappings and never labels an unobserved table successful.
write(A+'table-execution-results.tsx','''
import { Table, type TableColumns } from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";
import type { DataSyncInstanceRecord, DataSyncInstanceStatus } from "@/service/data-sync";
import { ExecutionProgressBar } from "./execution-progress-bar";

const STATE_LABELS = { WAITING: "等待", RUNNING: "运行中", FINISHED: "成功", FAILED: "失败", CANCELED: "已停止", UNKNOWN: "未取得状态" };
const INSTANCE_STATES: Record<string, DataSyncInstanceStatus> = { WAITING: "SUBMITTED", RUNNING: "RUNNING", FINISHED: "FINISHED", FAILED: "FAILED", CANCELED: "CANCELED" };
const pathText = (table: { database?: string; schema?: string; table?: string }) => [table.database, table.schema, table.table].filter(Boolean).join(".");

export function TableExecutionResults({ record }: { record: DataSyncInstanceRecord }) {
  const [now, setNow] = useState(Date.now());
  const active = ["RUNNING", "LAUNCHING", "CANCELLING", "RECONCILING"].includes(record.status);
  useEffect(() => {
    if (!active) return;
    const timer = window.setInterval(() => setNow(Date.now()), 2000);
    return () => window.clearInterval(timer);
  }, [active]);
  const report = record.executionReport;
  const stale = active && (!report || now - Date.parse(report.observedAt) > 10000 || record.status === "RECONCILING");
  const mappings = record.definitionSnapshot?.tableMappings || [];
  const rows = mappings.map((mapping, index) => {
    const result = report?.tables.find((table) => table.mappingIndex === index);
    let status: keyof typeof STATE_LABELS = result?.status || "UNKNOWN";
    if (stale) status = "UNKNOWN";
    return { id: String(index), source: pathText(mapping.source), target: pathText(mapping.target), status, result };
  });
  const columns: TableColumns<(typeof rows)[number]> = [
    { key: "source", title: "来源表", minWidth: 190, render: (_value, row) => <span className="break-all">{row.source}</span> },
    { key: "target", title: "去向表", minWidth: 190, render: (_value, row) => <span className="break-all">{row.target}</span> },
    { key: "progress", title: "全量进度", width: 170, render: (_value, row) => row.status === "UNKNOWN" ? <span title={stale ? "观测已过期" : "未取得表级观测"}>—</span> :
      <ExecutionProgressBar status={INSTANCE_STATES[row.status]} percent={row.result?.progressPercent} realtime={false} ariaLabel={`${row.source} 全量进度`} /> },
    { key: "status", title: "状态", width: 90, render: (_value, row) => STATE_LABELS[row.status] },
    { key: "read", title: "读取条数", width: 100, align: "right", render: (_value, row) => row.result?.readRows.toLocaleString("zh-CN") ?? "—" },
    { key: "written", title: "已提交条数", width: 110, align: "right", render: (_value, row) => row.result?.committedRows.toLocaleString("zh-CN") ?? "—" },
    { key: "failure", title: "失败原因", minWidth: 120, render: (_value, row) => row.result?.failureCode === "READ_FAILED" ? "来源读取失败" : row.result?.failureCode === "WRITE_FAILED" ? "目标写入失败" : "—" },
  ];
  return <Table columns={columns} dataSource={rows} rowKey="id" size="small" scroll={{ x: 1000 }} pagination={false} emptyText="暂无表映射" />;
}
''')
path=A+'execution-progress-bar.tsx'
edit(path,'  realtime,\n}: {','  realtime,\n  ariaLabel,\n}: {')
edit(path,'  realtime: boolean;','  realtime: boolean;\n  ariaLabel?: string;')
edit(path,'  const label = realtime ? "实时同步" : "离线同步";', '  const label = ariaLabel || (realtime ? "实时同步" : "离线同步");')
path=A+'execution-detail.tsx'
edit(path,'import { ExecutionProgressBar } from "./execution-progress-bar";', 'import { ExecutionProgressBar } from "./execution-progress-bar";\nimport { TableExecutionResults } from "./table-execution-results";')
# Keep the existing single-table view for old observations and CDC; use measured FULL table reports for batches.
p=ROOT/path;text=p.read_text();a=text.index('        <Table<TableExecutionRow>');b=text.index('\n      </div>',a);old=text[a:b]
text=text[:a]+'''        {record.solutionType === "DATABASE_OFFLINE" || record.executionReport ? <TableExecutionResults record={record} /> : (
'''+old+'''
        )}'''+text[b:];p.write_text(text)
print('Explicit multi-table UI, pinned mappings and measured results applied')
