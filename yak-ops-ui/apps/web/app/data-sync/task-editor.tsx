import {
  Alert,
  Badge,
  Button,
  CollapseSection,
  CronSchedulerPicker,
  Field,
  FieldLabel,
  Input,
  PageHeader,
  Popover,
  PopoverContent,
  PopoverTitle,
  PopoverTrigger,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Switch,
  Textarea,
  toast,
} from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";

import { EditorAnchorStepper, type EditorAnchorItem } from "@/app/data-sync/editor-anchor-stepper";
import { MultiTableRouteEditor } from "@/app/data-sync/multi-table-route-editor";
import { SchemaMappingEditor } from "@/app/data-sync/schema-mapping-editor";
import { DataSyncSearchableSelect } from "@/app/data-sync/searchable-select";
import { getDataSourceTypeLabel } from "@/app/datasource/constants";
import DatabaseIcons from "@/app/datasource/icons/DatabaseIcons";
import {
  listDataSourceColumns,
  listDataSources,
  listDataSourceSchemas,
  listDataSourceTables,
  type DataSourceCatalogColumn,
  type DataSourceCatalogTable,
  type DataSourceRecord,
} from "@/service/datasource";
import {
  createDataSyncTask,
  getDataSyncSchedule,
  getDataSyncTask,
  previewDataSyncMapping,
  previewDataSyncSchedule,
  publishDataSyncTask,
  saveDataSyncSchedule,
  updateDataSyncTask,
  type DataSyncMappingConfig,
  type DataSyncMappingPreview,
  type DataSyncScheduleSavePayload,
  type DataSyncTaskSavePayload,
  type DataSyncTableRoute,
  type DataSyncTaskStatus,
  type DataSyncType,
  type DataSyncWriteMode,
} from "@/service/data-sync";

interface EditorForm {
  name: string;
  remark: string;
  writeMode: DataSyncWriteMode;
  sourceDataSourceId: string;
  sourceDatabase: string;
  sourceSchema: string;
  sourceTable: string;
  targetDataSourceId: string;
  targetDatabase: string;
  targetSchema: string;
  targetTable: string;
  autoCreateTable: boolean;
  mapping?: DataSyncMappingConfig;
}

interface ScheduleForm {
  cronExpression: string;
  timeZone: string;
}

interface CatalogOptions {
  schemas: string[];
  tables: DataSourceCatalogTable[];
  loading: boolean;
  refresh: () => void;
}

interface ColumnOptions {
  columns: DataSourceCatalogColumn[];
  loading: boolean;
}

const EMPTY_SCHEDULE: ScheduleForm = {
  cronExpression: "",
  timeZone: "Asia/Shanghai",
};

const COMMON_TIME_ZONE_ITEMS: Record<string, string> = {
  "Asia/Shanghai": "Asia/Shanghai",
  "Asia/Hong_Kong": "Asia/Hong_Kong",
  "Asia/Taipei": "Asia/Taipei",
  "Asia/Tokyo": "Asia/Tokyo",
  "Asia/Seoul": "Asia/Seoul",
  "Asia/Singapore": "Asia/Singapore",
  UTC: "UTC",
  "Europe/London": "Europe/London",
  "America/New_York": "America/New_York",
  "America/Los_Angeles": "America/Los_Angeles",
};

function formatFireTime(value: string) {
  return value.replace("T", " ").replace(/\.\d+$/, "");
}

function ScheduleFireTimePreview({
  cronExpression,
  timeZone,
}: {
  cronExpression: string;
  timeZone: string;
}) {
  const [loading, setLoading] = useState(false);
  const [times, setTimes] = useState<string[]>([]);
  const [error, setError] = useState(false);

  useEffect(() => {
    const cron = cronExpression.trim();
    const zone = timeZone.trim();
    if (!cron || !zone) {
      setLoading(false);
      setTimes([]);
      setError(false);
      return;
    }

    let active = true;
    const timer = window.setTimeout(() => {
      setLoading(true);
      setError(false);
      void previewDataSyncSchedule({ cronExpression: cron, timeZone: zone })
        .then((result) => {
          if (!active) return;
          setTimes(result.nextFireTimes || []);
        })
        .catch(() => {
          if (!active) return;
          setTimes([]);
          setError(true);
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, 250);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [cronExpression, timeZone]);

  return (
    <div>
      <div className="flex items-center justify-between gap-3">
        <div className="text-xs font-medium text-[#344054]">未来 5 次执行时间</div>
        <div className="text-[11px] text-[#98a2b3]">{timeZone}</div>
      </div>

      {loading ? (
        <div className="mt-2 text-xs text-[#98a2b3]">正在计算...</div>
      ) : error ? (
        <div className="mt-2 text-xs text-[#d92d20]">当前 Cron 或时区无法预览</div>
      ) : times.length > 0 ? (
        <div className="mt-2 grid grid-cols-2 gap-x-5 gap-y-1.5 max-sm:grid-cols-1">
          {times.map((time, index) => (
            <div key={time} className="font-mono text-xs text-[#667085]">
              {index + 1}. {formatFireTime(time)}
            </div>
          ))}
        </div>
      ) : (
        <div className="mt-2 text-xs text-[#98a2b3]">暂无未来触发时间</div>
      )}
    </div>
  );
}

const EMPTY_FORM: EditorForm = {
  name: "",
  remark: "",
  writeMode: "APPEND",
  sourceDataSourceId: "",
  sourceDatabase: "",
  sourceSchema: "",
  sourceTable: "",
  targetDataSourceId: "",
  targetDatabase: "",
  targetSchema: "",
  targetTable: "",
  autoCreateTable: false,
  mapping: undefined,
};

const tableKey = (table: DataSourceCatalogTable) =>
  [table.database || "", table.schema || "", table.name].join("|");

const tableLabel = (table: DataSourceCatalogTable) =>
  [table.schema, table.name].filter(Boolean).join(".") || table.name;

const selectedTableKey = (
  tables: DataSourceCatalogTable[],
  database: string,
  schema: string,
  tableName: string,
) =>
  tables.find(
    (table) =>
      table.name === tableName &&
      (table.database || "") === database &&
      (table.schema || "") === schema,
  )
    ? [database, schema, tableName].join("|")
    : null;

function useCatalogOptions(dataSourceId: string, database: string, schema: string): CatalogOptions {
  const [schemas, setSchemas] = useState<string[]>([]);
  const [tables, setTables] = useState<DataSourceCatalogTable[]>([]);
  const [loading, setLoading] = useState(false);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const refresh = useCallback(() => setRefreshVersion((value) => value + 1), []);

  useEffect(() => {
    if (!dataSourceId) {
      setSchemas([]);
      setTables([]);
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    void Promise.all([
      listDataSourceSchemas(dataSourceId, database || undefined),
      listDataSourceTables(dataSourceId, {
        database: database || undefined,
        schema: schema || undefined,
        limit: 500,
      }),
    ])
      .then(([schemaOptions, tableOptions]) => {
        if (!active) return;
        setSchemas(schemaOptions || []);
        setTables(tableOptions || []);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [dataSourceId, database, schema, refreshVersion]);

  return { schemas, tables, loading, refresh };
}

function useTableColumns(
  dataSourceId: string,
  database: string,
  schema: string,
  table: string,
  enabled = true,
): ColumnOptions {
  const [columns, setColumns] = useState<DataSourceCatalogColumn[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!enabled || !dataSourceId || !table) {
      setColumns([]);
      setLoading(false);
      return;
    }

    let active = true;
    setLoading(true);
    void listDataSourceColumns(dataSourceId, {
      database: database || undefined,
      schema: schema || undefined,
      table,
    })
      .then((result) => {
        if (active) setColumns(result || []);
      })
      .catch(() => {
        if (active) setColumns([]);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [dataSourceId, database, enabled, schema, table]);

  return { columns, loading };
}

interface DataSourceEndpointCardProps {
  title: string;
  dataSources: DataSourceRecord[];
  dataSourceId: string;
  refreshing: boolean;
  onRefresh: () => void | Promise<void>;
  onCreateDataSource: () => void;
  onDataSourceChange: (value: string) => void;
}

function DataSourceEndpointCard({
  title,
  dataSources,
  dataSourceId,
  refreshing,
  onRefresh,
  onCreateDataSource,
  onDataSourceChange,
}: DataSourceEndpointCardProps) {
  const selectedDataSource = dataSources.find((item) => item.id === dataSourceId);
  const dataSourceOptions = useMemo(
    () =>
      dataSources.flatMap((item) =>
        item.id
          ? [
              {
                value: item.id,
                label: item.name || item.id,
                searchText: [item.dbType, item.database, item.schema].filter(Boolean).join(" "),
              },
            ]
          : [],
      ),
    [dataSources],
  );
  return (
    <div className="rounded-lg border border-[#e6e8eb] bg-white p-4">
      <div className="text-sm font-semibold text-[#344054]">{title}</div>
      <div className="mt-4 space-y-3">
        <Field className="grid grid-cols-[90px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel>类型</FieldLabel>
          {selectedDataSource ? (
            <div className="flex items-center gap-2 text-[13px] text-[#344054]">
              <DatabaseIcons dbType={selectedDataSource.dbType} width="16px" height="16px" />
              <span>{getDataSourceTypeLabel(selectedDataSource.dbType)}</span>
            </div>
          ) : (
            <div className="text-[13px] text-[#98a2b3]">-</div>
          )}
        </Field>

        <Field className="grid grid-cols-[90px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>数据源</FieldLabel>
          <DataSyncSearchableSelect
            value={dataSourceId || null}
            options={dataSourceOptions}
            placeholder="请选择数据源"
            searchPlaceholder="搜索数据源"
            emptyText="暂无数据源"
            refreshing={refreshing}
            onRefresh={onRefresh}
            footer={
              <Button
                size="small"
                variant="ghost"
                className="px-1 text-xs font-normal text-[var(--yak-color-primary)]"
                onClick={onCreateDataSource}
              >
                <Plus size={14} />
                新增数据源
              </Button>
            }
            onValueChange={onDataSourceChange}
          />
        </Field>
      </div>
    </div>
  );
}

interface TableSectionProps {
  children?: ReactNode;
  dataSourceId: string;
  boundSchema?: string;
  database: string;
  schema: string;
  table: string;
  catalog: CatalogOptions;
  allowCustomTable?: boolean;
  tableFieldLabel?: string;
  tableAction?: ReactNode;
  onSchemaChange: (value: string) => void;
  onTableChange: (table: DataSourceCatalogTable) => void;
  onTableNameChange?: (value: string) => void;
}

function TableSection({
  children,
  dataSourceId,
  boundSchema,
  database,
  schema,
  table,
  catalog,
  allowCustomTable = false,
  tableFieldLabel = "表",
  tableAction,
  onSchemaChange,
  onTableChange,
  onTableNameChange,
}: TableSectionProps) {
  const tableValue = selectedTableKey(catalog.tables, database, schema, table);
  const schemaOptions = useMemo(
    () => catalog.schemas.map((item) => ({ value: item, label: item })),
    [catalog.schemas],
  );
  const tableOptions = useMemo(
    () =>
      catalog.tables.map((item) => ({
        value: tableKey(item),
        label: tableLabel(item),
        searchText: [item.database, item.schema, item.name, item.type, item.remarks]
          .filter(Boolean)
          .join(" "),
      })),
    [catalog.tables],
  );
  const requiresSchema = !boundSchema && catalog.schemas.length > 0;
  const tableDisabled = !dataSourceId || (requiresSchema && !schema);

  return (
    <div className="rounded-lg border border-[#e6e8eb] bg-white p-4">
      <div className="space-y-3">
        {requiresSchema ? (
          <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel>Schema</FieldLabel>
            <DataSyncSearchableSelect
              value={schema || null}
              options={schemaOptions}
              placeholder="请选择 Schema"
              searchPlaceholder="搜索 Schema"
              emptyText="暂无 Schema"
              refreshing={catalog.loading}
              onRefresh={catalog.refresh}
              onValueChange={onSchemaChange}
            />
          </Field>
        ) : null}

        <Field
          className={`grid grid-cols-[112px_minmax(0,1fr)] ${
            allowCustomTable ? "items-start" : "items-center"
          } !gap-3`}
        >
          <FieldLabel required className={allowCustomTable ? "pt-1.5" : undefined}>
            {tableFieldLabel}
          </FieldLabel>
          <div className="flex items-center gap-2">
            <div className="w-1/2 min-w-0 max-lg:w-auto max-lg:flex-1">
              {allowCustomTable ? (
                <Input
                  size="small"
                  variant="outlined"
                  value={table}
                  disabled={tableDisabled}
                  placeholder="请输入目标表名"
                  onChange={(event) => onTableNameChange?.(event.target.value)}
                />
              ) : (
                <DataSyncSearchableSelect
                  value={tableValue}
                  options={tableOptions}
                  disabled={tableDisabled}
                  placeholder={
                    !dataSourceId
                      ? "请先选择数据源"
                      : requiresSchema && !schema
                        ? "请先选择 Schema"
                        : catalog.loading
                          ? "正在读取 Catalog..."
                          : "请选择表"
                  }
                  searchPlaceholder="搜索表"
                  emptyText="暂无表"
                  refreshing={catalog.loading}
                  onRefresh={catalog.refresh}
                  onValueChange={(value) => {
                    const selected = catalog.tables.find((item) => tableKey(item) === value);
                    if (selected) onTableChange(selected);
                  }}
                />
              )}
            </div>
            {allowCustomTable ? tableAction : null}
          </div>
        </Field>
        {children}
      </div>
    </div>
  );
}

const WRITE_MODE_ITEMS: Record<DataSyncWriteMode, string> = {
  APPEND: "追加写入",
  OVERWRITE: "覆盖写入",
  UPSERT: "更新写入",
};

const SQL_KEYWORDS = new Set([
  "CREATE",
  "TABLE",
  "COMMENT",
  "ON",
  "IS",
  "PRIMARY",
  "KEY",
  "NOT",
  "NULL",
  "WITH",
  "ZONE",
]);

const SQL_TYPES = new Set([
  "BOOLEAN",
  "TINYINT",
  "SMALLINT",
  "INTEGER",
  "INT",
  "BIGINT",
  "FLOAT",
  "DOUBLE",
  "PRECISION",
  "DECIMAL",
  "NUMERIC",
  "NUMBER",
  "VARCHAR",
  "VARCHAR2",
  "TEXT",
  "LONGTEXT",
  "BINARY",
  "VARBINARY",
  "BYTEA",
  "RAW",
  "BLOB",
  "LONGBLOB",
  "CLOB",
  "DATE",
  "TIME",
  "TIMESTAMP",
]);

const SQL_TOKEN_PATTERN =
  /'(?:''|[^'])*'|`(?:``|[^`])*`|"(?:""|[^"])*"|\b\d+(?:\.\d+)?\b|\b[A-Za-z_][A-Za-z0-9_]*\b|\s+|./g;

function previewDdlStatements(preview?: DataSyncMappingPreview) {
  if (!preview) return [];
  if (preview.ddlStatements && preview.ddlStatements.length > 0) {
    return preview.ddlStatements;
  }
  return preview.createTableSql ? [preview.createTableSql] : [];
}

function findMatchingParenthesis(sql: string, openIndex: number) {
  let depth = 0;
  let quote: "'" | '"' | "`" | undefined;

  for (let index = openIndex; index < sql.length; index += 1) {
    const character = sql[index];
    if (quote) {
      if (character === quote) {
        if (sql[index + 1] === quote) {
          index += 1;
        } else {
          quote = undefined;
        }
      }
      continue;
    }

    if (character === "'" || character === '"' || character === "`") {
      quote = character;
    } else if (character === "(") {
      depth += 1;
    } else if (character === ")") {
      depth -= 1;
      if (depth === 0) return index;
    }
  }

  return -1;
}

function splitTopLevelDefinitions(body: string) {
  const definitions: string[] = [];
  let start = 0;
  let depth = 0;
  let quote: "'" | '"' | "`" | undefined;

  for (let index = 0; index < body.length; index += 1) {
    const character = body[index];
    if (quote) {
      if (character === quote) {
        if (body[index + 1] === quote) {
          index += 1;
        } else {
          quote = undefined;
        }
      }
      continue;
    }

    if (character === "'" || character === '"' || character === "`") {
      quote = character;
    } else if (character === "(") {
      depth += 1;
    } else if (character === ")") {
      depth -= 1;
    } else if (character === "," && depth === 0) {
      definitions.push(body.slice(start, index).trim());
      start = index + 1;
    }
  }

  const tail = body.slice(start).trim();
  if (tail) definitions.push(tail);
  return definitions;
}

function normalizeNestedCommaSpacing(value: string) {
  let result = "";
  let depth = 0;
  let quote: "'" | '"' | "`" | undefined;

  for (let index = 0; index < value.length; index += 1) {
    const character = value[index];
    if (quote) {
      result += character;
      if (character === quote) {
        if (value[index + 1] === quote) {
          result += value[index + 1];
          index += 1;
        } else {
          quote = undefined;
        }
      }
      continue;
    }

    if (character === "'" || character === '"' || character === "`") {
      quote = character;
      result += character;
    } else if (character === "(") {
      depth += 1;
      result += character;
    } else if (character === ")") {
      depth -= 1;
      result += character;
    } else if (character === "," && depth > 0) {
      result += ", ";
      while (/\s/.test(value[index + 1] || "")) index += 1;
    } else {
      result += character;
    }
  }

  return result;
}

function formatDdlStatement(statement: string) {
  const sql = statement.trim().replace(/;\s*$/, "");
  if (!/^CREATE\s+TABLE\b/i.test(sql)) return `${sql};`;

  const openIndex = sql.indexOf("(");
  if (openIndex < 0) return `${sql};`;

  const closeIndex = findMatchingParenthesis(sql, openIndex);
  if (closeIndex < 0) return `${sql};`;

  const prefix = sql.slice(0, openIndex).trimEnd();
  const body = sql.slice(openIndex + 1, closeIndex);
  const suffix = sql.slice(closeIndex + 1).trim();
  const definitions = splitTopLevelDefinitions(body).map(normalizeNestedCommaSpacing);

  if (definitions.length === 0) return `${sql};`;

  return [
    `${prefix} (`,
    ...definitions.map(
      (definition, index) => `  ${definition}${index < definitions.length - 1 ? "," : ""}`,
    ),
    `)${suffix ? ` ${suffix}` : ""};`,
  ].join("\n");
}

function SqlCodePreview({ statements }: { statements: string[] }) {
  const sql = statements.map(formatDdlStatement).join("\n\n");
  const tokens = sql.match(SQL_TOKEN_PATTERN) || [sql];

  return (
    <pre className="max-h-[360px] overflow-auto whitespace-pre bg-[#f5f5f5] px-4 py-3 font-mono text-xs leading-5 text-[#262626]">
      {tokens.map((token, index) => {
        const normalized = token.toUpperCase();
        let className = "text-[#262626]";
        if (token.startsWith("'")) {
          className = "text-[#2f8f46]";
        } else if (token.startsWith("`") || token.startsWith('"')) {
          className = "text-[#262626]";
        } else if (/^\d/.test(token)) {
          className = "text-[#d04a63]";
        } else if (SQL_TYPES.has(normalized)) {
          className = "text-[#7a5af8]";
        } else if (SQL_KEYWORDS.has(normalized)) {
          className = "text-[#c2416c]";
        } else if (/^[(),.;=]+$/.test(token)) {
          className = "text-[#525252]";
        }

        return (
          <span key={`${index}-${token}`} className={className}>
            {token}
          </span>
        );
      })}
    </pre>
  );
}

function DdlPreviewPopover({
  preview,
  loading,
}: {
  preview?: DataSyncMappingPreview;
  loading: boolean;
}) {
  const statements = previewDdlStatements(preview);
  const disabled = loading || statements.length === 0;

  return (
    <Popover>
      <PopoverTrigger
        disabled={disabled}
        render={
          <Button size="small" disabled={disabled}>
            DDL
          </Button>
        }
      />
      <PopoverContent
        side="right"
        align="start"
        sideOffset={8}
        className="w-[720px] max-w-[calc(100vw-2rem)] overflow-hidden p-0"
      >
        <div className="flex items-center justify-between border-b border-[#eef0f3] px-4 py-2.5">
          <PopoverTitle className="text-xs font-medium text-[#344054]">DDL 预览</PopoverTitle>
          <Badge tone="info">只读</Badge>
        </div>
        <SqlCodePreview statements={statements} />
      </PopoverContent>
    </Popover>
  );
}

function SchemaPreviewDiagnostics({ preview }: { preview?: DataSyncMappingPreview }) {
  if (!preview) return null;

  const warnings = preview.warnings || [];
  const unsupportedReasons = preview.unsupportedReasons || [];
  const hasDiagnostics =
    (!preview.targetTableExists && !preview.autoCreateTable) ||
    warnings.length > 0 ||
    unsupportedReasons.length > 0;

  if (!hasDiagnostics) return null;

  return (
    <div className="space-y-3">
      {!preview.targetTableExists && !preview.autoCreateTable ? (
        <div
          role="alert"
          className="rounded-lg border border-[#fecdca] bg-[#fff6f5] px-4 py-3 text-xs leading-5 text-[#b42318]"
        >
          目标表不存在。请选择已有目标表，或开启“自动建表”后输入待创建的目标表名。
        </div>
      ) : null}

      {warnings.length > 0 ? (
        <Alert>
          <div className="space-y-1">
            <div className="font-medium">Schema 规划提示</div>
            {warnings.map((warning, index) => (
              <div key={`${index}-${warning}`}>• {warning}</div>
            ))}
          </div>
        </Alert>
      ) : null}

      {unsupportedReasons.length > 0 ? (
        <div
          role="alert"
          className="rounded-lg border border-[#fecdca] bg-[#fff6f5] px-4 py-3 text-xs leading-5 text-[#b42318]"
        >
          <div className="font-medium">当前 Schema 无法直接同步</div>
          <div className="mt-1 space-y-1">
            {unsupportedReasons.map((reason, index) => (
              <div key={`${index}-${reason}`}>• {reason}</div>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}

interface DataSyncTaskEditorPageProps {
  syncType: DataSyncType;
}

export function DataSyncTaskEditorPage({ syncType }: DataSyncTaskEditorPageProps) {
  const realtime = syncType === "REALTIME";
  const localScroll = !realtime;
  const basePath = realtime ? "/realtime-sync" : "/offline-sync";
  const { id } = useParams<{ id: string }>();
  const editing = Boolean(id);
  const navigate = useNavigate();
  const location = useLocation();
  const draft = (
    location.state as {
      draft?: {
        name?: string;
        sourceDataSourceId?: string;
        targetDataSourceId?: string;
      };
    } | null
  )?.draft;

  const [form, setForm] = useState<EditorForm>(() => ({
    ...EMPTY_FORM,
    name: draft?.name || "",
    sourceDataSourceId: draft?.sourceDataSourceId || "",
    targetDataSourceId: draft?.targetDataSourceId || "",
  }));
  const [tableRoutes, setTableRoutes] = useState<DataSyncTableRoute[]>([]);
  const [routesReady, setRoutesReady] = useState(false);
  const onRoutesChange = useCallback((routes: DataSyncTableRoute[]) => setTableRoutes(routes), []);
  const onRoutesReadyChange = useCallback((ready: boolean) => setRoutesReady(ready), []);

  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);
  const [dataSourcesLoading, setDataSourcesLoading] = useState(false);
  const [taskStatus, setTaskStatus] = useState<DataSyncTaskStatus>("UNPUBLISHED");
  const [loading, setLoading] = useState(editing);
  const [saving, setSaving] = useState(false);
  const [mappingLoading, setMappingLoading] = useState(false);
  const [mapping, setMapping] = useState<DataSyncMappingPreview>();
  const mappingScopeRef = useRef("");
  const editorScrollRef = useRef<HTMLElement | null>(null);
  const [scheduleForm, setScheduleForm] = useState<ScheduleForm>({ ...EMPTY_SCHEDULE });
  const [scheduleExists, setScheduleExists] = useState(false);

  const timeZoneItems = useMemo(
    () =>
      scheduleForm.timeZone && !COMMON_TIME_ZONE_ITEMS[scheduleForm.timeZone]
        ? { ...COMMON_TIME_ZONE_ITEMS, [scheduleForm.timeZone]: scheduleForm.timeZone }
        : COMMON_TIME_ZONE_ITEMS,
    [scheduleForm.timeZone],
  );

  const anchorItems = useMemo<EditorAnchorItem[]>(
    () => [
      { id: "basic", label: "基本信息" },
      { id: "datasource", label: "数据源" },
      { id: "source", label: "数据来源" },
      { id: "target", label: "数据去向" },
      { id: "mapping", label: "去向字段映射" },
      ...(realtime ? [] : [{ id: "schedule", label: "调度配置" }]),
    ],
    [realtime],
  );

  const sourceCatalog = useCatalogOptions(
    form.sourceDataSourceId,
    form.sourceDatabase,
    form.sourceSchema,
  );
  const targetCatalog = useCatalogOptions(
    form.targetDataSourceId,
    form.targetDatabase,
    form.targetSchema,
  );

  const sourceDataSources = useMemo(() => {
    if (!realtime) return dataSources;
    return dataSources.filter((item) => item.dbType === "MYSQL");
  }, [dataSources, realtime]);
  const targetDataSources = useMemo(() => {
    if (!realtime) return dataSources;
    return dataSources.filter((item) =>
      ["MYSQL", "POSTGRE_SQL", "ORACLE"].includes(item.dbType || ""),
    );
  }, [dataSources, realtime]);
  const selectedSourceDataSource = dataSources.find((item) => item.id === form.sourceDataSourceId);
  const selectedTargetDataSource = dataSources.find((item) => item.id === form.targetDataSourceId);
  const sourceReady = Boolean(realtime && form.sourceDataSourceId && form.sourceTable);
  const targetReady = Boolean(realtime && form.targetDataSourceId && form.targetTable);
  const targetTableExistsInCatalog =
    selectedTableKey(
      targetCatalog.tables,
      form.targetDatabase,
      form.targetSchema,
      form.targetTable,
    ) !== null;
  const targetDerived = form.autoCreateTable && !targetTableExistsInCatalog;
  const sourceColumns = useTableColumns(
    form.sourceDataSourceId,
    form.sourceDatabase,
    form.sourceSchema,
    form.sourceTable,
    sourceReady,
  );
  const targetColumns = useTableColumns(
    form.targetDataSourceId,
    form.targetDatabase,
    form.targetSchema,
    form.targetTable,
    targetReady && !targetDerived && !targetCatalog.loading,
  );

  const loadDataSources = useCallback(async () => {
    setDataSourcesLoading(true);
    try {
      const result = await listDataSources({ pageNo: 1, pageSize: 200 });
      setDataSources(result?.bizData || []);
    } finally {
      setDataSourcesLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDataSources();
  }, [loadDataSources]);

  useEffect(() => {
    if (dataSources.length === 0) return;
    setForm((current) => {
      const source = dataSources.find((item) => item.id === current.sourceDataSourceId);
      const target = dataSources.find((item) => item.id === current.targetDataSourceId);
      return {
        ...current,
        sourceDatabase: source?.database || current.sourceDatabase,
        sourceSchema: source?.schema || current.sourceSchema,
        targetDatabase: target?.database || current.targetDatabase,
        targetSchema: target?.schema || current.targetSchema,
      };
    });
  }, [dataSources]);

  useEffect(() => {
    if (!id) return;
    let active = true;
    setLoading(true);
    void Promise.all([
      getDataSyncTask(id),
      realtime ? Promise.resolve(undefined) : getDataSyncSchedule(id),
    ])
      .then(([task, schedule]) => {
        if (!active) return;
        if (task.syncType !== syncType) {
          toast.error("任务类型与当前页面不匹配");
          navigate(basePath, { replace: true });
          return;
        }
        setTaskStatus(task.status === "PUBLISHED" ? "PUBLISHED" : "UNPUBLISHED");
        setForm({
          name: task.name,
          remark: task.remark || "",
          writeMode:
            task.writeMode === "OVERWRITE" || task.writeMode === "UPSERT"
              ? task.writeMode
              : "APPEND",
          sourceDataSourceId: task.sourceDataSourceId,
          sourceDatabase: task.sourceDatabase || "",
          sourceSchema: task.sourceSchema || "",
          sourceTable: task.sourceTable,
          targetDataSourceId: task.targetDataSourceId,
          targetDatabase: task.targetDatabase || "",
          targetSchema: task.targetSchema || "",
          targetTable: task.targetTable,
          autoCreateTable: Boolean(task.autoCreateTable),
          mapping: task.mapping,
        });
        if (!realtime) {
          setTableRoutes(task.tableRoutes?.length ? task.tableRoutes : [{
            sourceDatabase: task.sourceDatabase,
            sourceSchema: task.sourceSchema,
            sourceTable: task.sourceTable,
            targetDatabase: task.targetDatabase,
            targetSchema: task.targetSchema,
            targetTable: task.targetTable,
            autoCreateTable: Boolean(task.autoCreateTable),
            mapping: task.mapping,
          }]);
          setScheduleExists(Boolean(schedule));
          setScheduleForm(
            schedule
              ? {
                  cronExpression: schedule.cronExpression,
                  timeZone: schedule.timeZone,
                }
              : { ...EMPTY_SCHEDULE },
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [basePath, id, navigate, realtime, syncType]);

  const mappingScopeKey = useMemo(
    () =>
      [
        form.sourceDataSourceId,
        form.sourceDatabase,
        form.sourceSchema,
        form.sourceTable,
        form.targetDataSourceId,
        form.targetDatabase,
        form.targetSchema,
        form.targetTable,
        String(form.autoCreateTable),
      ].join("::"),
    [
      form.sourceDataSourceId,
      form.sourceDatabase,
      form.sourceSchema,
      form.sourceTable,
      form.targetDataSourceId,
      form.targetDatabase,
      form.targetSchema,
      form.targetTable,
      form.autoCreateTable,
    ],
  );

  const mappingPayload = useMemo(
    () =>
      realtime &&
      form.sourceDataSourceId &&
      form.sourceTable &&
      form.targetDataSourceId &&
      form.targetTable &&
      (form.mapping === undefined || form.mapping.columns.length > 0)
        ? {
            sourceDataSourceId: form.sourceDataSourceId,
            sourceDatabase: form.sourceDatabase || undefined,
            sourceSchema: form.sourceSchema || undefined,
            sourceTable: form.sourceTable,
            targetDataSourceId: form.targetDataSourceId,
            targetDatabase: form.targetDatabase || undefined,
            targetSchema: form.targetSchema || undefined,
            targetTable: form.targetTable,
            autoCreateTable: form.autoCreateTable,
            mapping: form.mapping,
          }
        : undefined,
    [
      realtime,
      form.sourceDataSourceId,
      form.sourceDatabase,
      form.sourceSchema,
      form.sourceTable,
      form.targetDataSourceId,
      form.targetDatabase,
      form.targetSchema,
      form.targetTable,
      form.autoCreateTable,
      form.mapping,
    ],
  );

  useEffect(() => {
    if (!mappingPayload) {
      setMapping(undefined);
      setMappingLoading(false);
      return;
    }

    const previousScope = mappingScopeRef.current;
    const scopeChanged = Boolean(previousScope && previousScope !== mappingScopeKey);
    mappingScopeRef.current = mappingScopeKey;

    if (scopeChanged) {
      setMapping(undefined);
    }

    let active = true;
    setMappingLoading(true);
    const timer = window.setTimeout(() => {
      void previewDataSyncMapping(mappingPayload)
        .then((result) => {
          if (active) setMapping(result);
        })
        .finally(() => {
          if (active) setMappingLoading(false);
        });
    }, 200);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [mappingPayload, mappingScopeKey]);

  const patch = <K extends keyof EditorForm>(key: K, value: EditorForm[K]) =>
    setForm((current) => {
      const next = { ...current, [key]: value };
      if (
        key === "sourceDataSourceId" ||
        key === "sourceDatabase" ||
        key === "sourceSchema" ||
        key === "sourceTable" ||
        key === "targetDataSourceId" ||
        key === "targetDatabase" ||
        key === "targetSchema" ||
        key === "targetTable"
      ) {
        next.mapping = undefined;
      }
      return next;
    });

  const patchSchedule = (key: keyof ScheduleForm, value: string) =>
    setScheduleForm((current) => ({ ...current, [key]: value }));

  const payload = (): DataSyncTaskSavePayload => {
    const firstRoute = !realtime ? tableRoutes[0] : undefined;
    const common = {
      name: form.name.trim(),
      sourceDataSourceId: form.sourceDataSourceId,
      sourceDatabase: firstRoute?.sourceDatabase || form.sourceDatabase || undefined,
      sourceSchema: firstRoute?.sourceSchema || form.sourceSchema || undefined,
      sourceTable: firstRoute?.sourceTable || form.sourceTable,
      targetDataSourceId: form.targetDataSourceId,
      targetDatabase: firstRoute?.targetDatabase || form.targetDatabase || undefined,
      targetSchema: firstRoute?.targetSchema || form.targetSchema || undefined,
      targetTable: firstRoute?.targetTable || form.targetTable,
      autoCreateTable: firstRoute ? Boolean(firstRoute.autoCreateTable) : form.autoCreateTable,
      mapping: firstRoute ? firstRoute.mapping : form.mapping,
      remark: form.remark.trim() || undefined,
    };
    if (realtime) {
      return {
        ...common,
        writeMode: "APPEND",
        syncType: "REALTIME",
      };
    }
    return {
      ...common,
      writeMode: form.writeMode,
      syncType: "OFFLINE",
      tableRoutes: tableRoutes.map((route) => ({
        id: route.id,
        sourceDatabase: route.sourceDatabase,
        sourceSchema: route.sourceSchema,
        sourceTable: route.sourceTable,
        targetDatabase: route.targetDatabase,
        targetSchema: route.targetSchema,
        targetTable: route.targetTable.trim(),
        autoCreateTable: Boolean(route.autoCreateTable),
        mapping: route.mapping,
      })),
    };
  };

  const schedulePayload = (): DataSyncScheduleSavePayload => ({
    cronExpression: scheduleForm.cronExpression.trim(),
    timeZone: scheduleForm.timeZone.trim(),
  });

  const published = editing && taskStatus === "PUBLISHED";
  const scheduleConfigured = Boolean(scheduleForm.cronExpression.trim());
  const scheduleRequired = scheduleExists || scheduleConfigured;
  const scheduleValid =
    realtime || !scheduleRequired || (scheduleConfigured && Boolean(scheduleForm.timeZone.trim()));
  const canSave =
    !published &&
    scheduleValid &&
    form.name.trim() &&
    (realtime ? Boolean(mapping?.compatible && !mappingLoading) : routesReady && tableRoutes.length > 0) &&
    !sourceCatalog.loading &&
    !targetCatalog.loading;

  const save = async (publishAfterSave = false) => {
    if (!canSave || saving) return;
    setSaving(true);
    try {
      const saved =
        editing && id
          ? await updateDataSyncTask(id, payload())
          : await createDataSyncTask(payload());

      if (!realtime && scheduleRequired) {
        try {
          await saveDataSyncSchedule(saved.id, schedulePayload());
          setScheduleExists(true);
        } catch {
          toast.warning("任务已保存，但调度配置保存失败");
          navigate(`${basePath}/${saved.id}`, { replace: true });
          return;
        }
      }

      if (publishAfterSave) {
        try {
          await publishDataSyncTask(saved.id);
        } catch {
          toast.warning("任务已保存，但上线失败，当前保持已下线");
          navigate(`${basePath}/${saved.id}`, { replace: true });
          return;
        }
        toast.success(realtime ? "实时同步任务已保存并上线" : "同步任务已保存并上线");
        navigate(basePath, { replace: true });
        return;
      }
      toast.success(editing ? "同步任务已保存，当前仍为已下线" : "同步任务已创建，当前为已下线");
      navigate(`${basePath}/${saved.id}`, { replace: true });
    } finally {
      setSaving(false);
    }
  };

  const pageTitle = editing
    ? form.name || (realtime ? "编辑实时同步任务" : "编辑离线同步任务")
    : realtime
      ? "新建实时同步任务"
      : "新建离线同步任务";
  const pageDescription = realtime
    ? "MySQL CDC 单表实时同步 · 首次全量后持续消费 Binlog"
    : undefined;

  if (loading) {
    return <div className="p-8 text-sm text-[#667085]">正在加载同步任务...</div>;
  }

  if (published) {
    return (
      <div className="min-h-full bg-[#f6f6f6] text-[#242731]">
        <PageHeader
          title={pageTitle}
          description={pageDescription}
          bordered
          className="bg-white px-6 max-md:px-4"
          extra={
            <Button size="small" onClick={() => navigate(basePath)}>
              返回任务列表
            </Button>
          }
        />
        <div className="px-6 pt-5 max-md:px-4">
          <Alert>任务已上线，当前不可编辑。请先在任务列表下线，再修改任务定义。</Alert>
        </div>
      </div>
    );
  }

  return (
    <div
      className={
        localScroll
          ? "flex h-full min-h-0 flex-col overflow-hidden bg-[#f6f6f6] text-[#242731]"
          : "min-h-full bg-[#f6f6f6] text-[#242731]"
      }
    >
      <PageHeader
        title={pageTitle}
        description={pageDescription}
        bordered
        className={localScroll ? "shrink-0 bg-white px-6 max-md:px-4" : "bg-white px-6 max-md:px-4"}
        extra={
          <>
            <Button size="small" disabled={saving} onClick={() => navigate(basePath)}>
              取消
            </Button>
            <Button size="small" loading={saving} disabled={!canSave} onClick={() => void save()}>
              保存
            </Button>
            <Button
              size="small"
              variant="primary"
              loading={saving}
              disabled={!canSave}
              onClick={() => void save(true)}
            >
              保存并上线
            </Button>
          </>
        }
      />

      <div
        className={
          localScroll
            ? "flex min-h-0 flex-1 gap-5 px-6 max-md:px-4"
            : "flex gap-5 px-6 pb-8 pt-5 max-md:px-4"
        }
      >
        <main
          ref={editorScrollRef}
          className={
            localScroll
              ? "min-h-0 min-w-0 flex-1 space-y-4 overflow-y-auto pb-8 pt-5"
              : "min-w-0 flex-1 space-y-4"
          }
        >
          {editing ? (
            <Alert>
              {realtime
                ? "当前任务已下线，可修改任务定义。执行配置变化会生成新版本，新版本上线后首次启动会重新全量同步；仅修改名称或备注不会增加版本。"
                : "当前任务已下线，可修改任务定义。保存后仍需上线，任务才可以运行。"}
            </Alert>
          ) : (
            <Alert>新建任务保存后默认处于已下线状态，需要上线后才可以运行或启动。</Alert>
          )}

          {realtime ? (
            <div className="rounded-lg border border-[#b2ccff] bg-[#f5f8ff] px-4 py-3 text-xs leading-5 text-[#344054]">
              首次启动会先同步来源表当前全量数据，随后持续消费 MySQL
              Binlog；停止后再次启动同一任务版本会从已保存的 CDC 状态继续。
            </div>
          ) : null}

          <CollapseSection id="basic" title="基本信息">
            <div className="space-y-3 rounded-lg border border-[#e6e8eb] bg-white p-4">
              <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel required>任务名称</FieldLabel>
                <Input
                  size="small"
                  variant="outlined"
                  maxLength={128}
                  value={form.name}
                  placeholder="请输入任务名称"
                  onChange={(event) => patch("name", event.target.value)}
                />
              </Field>
              <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-start !gap-3">
                <FieldLabel className="pt-1.5">备注</FieldLabel>
                <Textarea
                  size="small"
                  rows={2}
                  maxLength={500}
                  value={form.remark}
                  className="min-h-[56px] resize-none"
                  placeholder="可选"
                  onValueChange={(value) => patch("remark", value)}
                />
              </Field>
            </div>
          </CollapseSection>

          <CollapseSection id="datasource" title="数据源">
            <div className="grid grid-cols-2 gap-3 max-lg:grid-cols-1">
              <DataSourceEndpointCard
                title="来源"
                dataSources={sourceDataSources}
                dataSourceId={form.sourceDataSourceId}
                refreshing={dataSourcesLoading}
                onRefresh={loadDataSources}
                onCreateDataSource={() => navigate("/data-source?create=1")}
                onDataSourceChange={(value) => {
                  const selected = dataSources.find((item) => item.id === value);
                  setForm((current) => ({
                    ...current,
                    sourceDataSourceId: value,
                    sourceDatabase: selected?.database || "",
                    sourceSchema: selected?.schema || "",
                    sourceTable: "",
                    mapping: undefined,
                  }));
                  if (!realtime) {
                    setTableRoutes([]);
                    setRoutesReady(false);
                  }
                }}
              />
              <DataSourceEndpointCard
                title="去向"
                dataSources={targetDataSources}
                dataSourceId={form.targetDataSourceId}
                refreshing={dataSourcesLoading}
                onRefresh={loadDataSources}
                onCreateDataSource={() => navigate("/data-source?create=1")}
                onDataSourceChange={(value) => {
                  const selected = dataSources.find((item) => item.id === value);
                  setForm((current) => ({
                    ...current,
                    targetDataSourceId: value,
                    targetDatabase: selected?.database || "",
                    targetSchema: selected?.schema || "",
                    targetTable: "",
                    mapping: undefined,
                  }));
                  if (!realtime) {
                    setTableRoutes((current) => current.map((route) => ({
                      ...route,
                      targetDatabase: selected?.database || undefined,
                      targetSchema: selected?.schema || undefined,
                      targetTable: route.sourceTable,
                      autoCreateTable: false,
                      mapping: undefined,
                    })));
                    setRoutesReady(false);
                  }
                }}
              />
            </div>
          </CollapseSection>

          {realtime ? (
            <>
          <CollapseSection id="source" title="数据来源">
            <TableSection
              dataSourceId={form.sourceDataSourceId}
              boundSchema={selectedSourceDataSource?.schema}
              database={form.sourceDatabase}
              schema={form.sourceSchema}
              table={form.sourceTable}
              catalog={sourceCatalog}
              onSchemaChange={(value) =>
                setForm((current) => ({
                  ...current,
                  sourceSchema: value,
                  sourceTable: "",
                  mapping: undefined,
                }))
              }
              onTableChange={(table) =>
                setForm((current) => ({
                  ...current,
                  sourceDatabase: current.sourceDatabase || table.database || "",
                  sourceSchema: current.sourceSchema || table.schema || "",
                  sourceTable: table.name,
                  mapping: undefined,
                }))
              }
            >
              {realtime ? (
                <Alert>实时同步依赖 ROW Binlog 和 CDC 权限；连接测试通过不代表 CDC 可用。</Alert>
              ) : null}
            </TableSection>
          </CollapseSection>

          <CollapseSection id="target" title="数据去向">
            <TableSection
              dataSourceId={form.targetDataSourceId}
              boundSchema={selectedTargetDataSource?.schema}
              database={form.targetDatabase}
              schema={form.targetSchema}
              table={form.targetTable}
              catalog={targetCatalog}
              allowCustomTable={form.autoCreateTable}
              tableFieldLabel="目标表"
              tableAction={
                targetDerived ? (
                  <DdlPreviewPopover preview={mapping} loading={mappingLoading} />
                ) : undefined
              }
              onTableNameChange={(value) => patch("targetTable", value)}
              onSchemaChange={(value) =>
                setForm((current) => ({
                  ...current,
                  targetSchema: value,
                  targetTable: "",
                  mapping: undefined,
                }))
              }
              onTableChange={(table) =>
                setForm((current) => ({
                  ...current,
                  targetDatabase: current.targetDatabase || table.database || "",
                  targetSchema: current.targetSchema || table.schema || "",
                  targetTable: table.name,
                  mapping: undefined,
                }))
              }
            >
              <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel>自动建表</FieldLabel>
                <Switch
                  size="small"
                  checked={form.autoCreateTable}
                  onCheckedChange={(checked) =>
                    setForm((current) => {
                      const autoCreateTable = Boolean(checked);
                      if (autoCreateTable) {
                        return { ...current, autoCreateTable, mapping: undefined };
                      }
                      const currentTableExists =
                        selectedTableKey(
                          targetCatalog.tables,
                          current.targetDatabase,
                          current.targetSchema,
                          current.targetTable,
                        ) !== null;
                      return {
                        ...current,
                        autoCreateTable,
                        targetTable: currentTableExists ? current.targetTable : "",
                        mapping: undefined,
                      };
                    })
                  }
                />
              </Field>

              {!realtime ? (
                <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-start !gap-3">
                  <FieldLabel required className="pt-1.5">
                    写入方式
                  </FieldLabel>
                  <div className="space-y-2">
                    <Select
                      size="small"
                      items={WRITE_MODE_ITEMS}
                      value={form.writeMode}
                      onValueChange={(value) =>
                        patch("writeMode", String(value || "APPEND") as DataSyncWriteMode)
                      }
                    >
                      <SelectTrigger variant="outlined">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {(Object.entries(WRITE_MODE_ITEMS) as [DataSyncWriteMode, string][]).map(
                          ([value, label]) => (
                            <SelectItem key={value} value={value}>
                              <SelectItemText>{label}</SelectItemText>
                              <SelectItemIndicator />
                            </SelectItem>
                          ),
                        )}
                      </SelectContent>
                    </Select>
                    {form.writeMode === "OVERWRITE" ? (
                      <Alert>覆盖写入会先清空目标表，同步失败时原数据不会自动恢复。</Alert>
                    ) : null}
                  </div>
                </Field>
              ) : null}
            </TableSection>
          </CollapseSection>

          <CollapseSection
            id="mapping"
            title="Schema 映射"
            extra={
              mapping ? (
                <div className="flex items-center gap-2">
                  {mapping.targetTableExists ? (
                    <Badge tone="success">目标表已存在</Badge>
                  ) : mapping.autoCreateTable ? (
                    <Badge tone="warning">将自动建表</Badge>
                  ) : (
                    <Badge tone="danger">目标表不存在</Badge>
                  )}
                  <Badge tone={mapping.compatible ? "success" : "danger"}>
                    {mapping.compatible ? "兼容" : "不兼容"}
                  </Badge>
                </div>
              ) : undefined
            }
          >
            <div className="space-y-3">
              <SchemaMappingEditor
                value={form.mapping}
                onChange={(value) =>
                  setForm((current) => ({
                    ...current,
                    mapping: value,
                  }))
                }
                sourceColumns={sourceColumns.columns}
                targetColumns={targetColumns.columns}
                sourceLoading={sourceColumns.loading}
                targetLoading={targetColumns.loading}
                sourceReady={sourceReady}
                targetReady={targetReady}
                targetDerived={targetDerived}
                preview={mapping}
              />

              {form.mapping?.columns.length === 0 ? (
                <Alert>至少保留一个字段映射后才能保存任务。</Alert>
              ) : null}

              <SchemaPreviewDiagnostics preview={mapping} />
            </div>
          </CollapseSection>


            </>
          ) : (
            <MultiTableRouteEditor
              routes={tableRoutes}
              onChange={onRoutesChange}
              onReadyChange={onRoutesReadyChange}
              sourceDataSourceId={form.sourceDataSourceId}
              targetDataSourceId={form.targetDataSourceId}
              sourceDatabase={form.sourceDatabase}
              sourceSchema={form.sourceSchema}
              targetDatabase={form.targetDatabase}
              targetSchema={form.targetSchema}
              sourceBoundSchema={selectedSourceDataSource?.schema}
              targetBoundSchema={selectedTargetDataSource?.schema}
              sourceCatalog={sourceCatalog}
              targetCatalog={targetCatalog}
              onSourceSchemaChange={(value) =>
                setForm((current) => ({ ...current, sourceSchema: value }))
              }
              onTargetSchemaChange={(value) => {
                setForm((current) => ({ ...current, targetSchema: value }));
                setTableRoutes((current) => current.map((route) => ({
                  ...route,
                  targetSchema: value,
                  mapping: undefined,
                })));
                setRoutesReady(false);
              }}
            />
          )}

          {!realtime ? (
            <CollapseSection id="schedule" title="调度配置">
              <div className="space-y-3 rounded-lg border border-[#e6e8eb] bg-white p-4">
                <Field className="grid grid-cols-[140px_minmax(0,1fr)] items-center !gap-3">
                  <FieldLabel required={scheduleRequired}>Cron 表达式</FieldLabel>
                  <CronSchedulerPicker
                    value={scheduleForm.cronExpression}
                    allowClear={!scheduleExists}
                    placeholder="点击配置 Cron"
                    onValueChange={(value) => patchSchedule("cronExpression", value)}
                    renderPanelExtra={(draftCronExpression) => (
                      <ScheduleFireTimePreview
                        cronExpression={draftCronExpression}
                        timeZone={scheduleForm.timeZone}
                      />
                    )}
                  />
                </Field>

                <Field className="grid grid-cols-[140px_minmax(0,1fr)] items-center !gap-3">
                  <FieldLabel required={scheduleRequired}>时区</FieldLabel>
                  <Select
                    size="small"
                    items={timeZoneItems}
                    value={scheduleForm.timeZone}
                    onValueChange={(value) =>
                      patchSchedule("timeZone", String(value || "Asia/Shanghai"))
                    }
                  >
                    <SelectTrigger variant="outlined">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {Object.entries(timeZoneItems).map(([value, label]) => (
                        <SelectItem key={value} value={value}>
                          <SelectItemText>{label}</SelectItemText>
                          <SelectItemIndicator />
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </Field>
              </div>
            </CollapseSection>
          ) : null}
        </main>

        <aside
          className={
            localScroll
              ? "hidden h-fit w-44 shrink-0 self-start pt-5 lg:block"
              : "sticky top-4 hidden h-fit w-44 shrink-0 self-start lg:block"
          }
        >
          <EditorAnchorStepper
            items={anchorItems}
            scrollRootRef={localScroll ? editorScrollRef : undefined}
          />
        </aside>
      </div>
    </div>
  );
}

export default DataSyncTaskEditorPage;
