import { useMemo, useState, type ReactNode } from "react";
import {
  Button,
  Input,
  Popover,
  PopoverContent,
  PopoverTrigger,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  Tabs,
  TabsList,
  TabsPanel,
  TabsTab,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { CircleHelp, Plus, Trash2 } from "lucide-react";
import {
  SCHEMA_CHANGE_BEHAVIORS,
  SCHEMA_CHANGE_EVENTS,
  type PipelineDefinition,
} from "@/service/data-sync/native-pipeline";
import type {
  DataSyncSchemaChangeBehavior,
  DataSyncSchemaChangeEvent,
} from "@/service/data-sync";
import { EditorDrawer } from "./editor-drawer";

type MainTab = "advanced" | "ddl";
type EndpointTab = "source" | "sink";

interface ParameterRow {
  id: number;
  name: string;
  value: string;
}

const SOURCE_PROTECTED_KEYS = new Set([
  "type",
  "datasource-id",
  "tables",
  "scan.startup.mode",
]);

const SINK_PROTECTED_KEYS = new Set([
  "type",
  "datasource-id",
  "exclude.schema.changes",
]);

const CREDENTIAL_KEYS = new Set([
  "password",
  "passwd",
  "token",
  "secret",
  "secretkey",
  "accesskey",
  "apikey",
  "privatekey",
  "credentials",
]);

const BEHAVIOR_LABELS: Record<DataSyncSchemaChangeBehavior, string> = {
  evolve: "严格同步",
  try_evolve: "尽力同步",
  lenient: "安全演进",
  ignore: "忽略变更",
  exception: "遇变更失败",
};

const EVENT_LABELS: Record<DataSyncSchemaChangeEvent, string> = {
  "create.table": "新建表",
  "drop.table": "删除表",
  "add.column": "新增列",
  "drop.column": "删除列",
  "rename.column": "重命名列",
  "alter.column.type": "修改列类型",
  "truncate.table": "清空表",
  "alter.table.comment": "修改表注释",
};

function effectiveExcluded(
  definition: PipelineDefinition,
): DataSyncSchemaChangeEvent[] {
  const configured = definition.sink["exclude.schema.changes"];

  if (Array.isArray(configured)) {
    return configured;
  }

  return (definition.pipeline["schema.change.behavior"] ?? "lenient") === "lenient"
    ? ["truncate.table", "drop.table"]
    : [];
}

function parameterValue(value: unknown): string {
  if (value === undefined || value === null) return "";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function endpointParameters(
  endpoint: Record<string, unknown>,
  protectedKeys: Set<string>,
): ParameterRow[] {
  let id = 0;
  return Object.entries(endpoint)
    .filter(([key]) => !protectedKeys.has(key))
    .map(([name, value]) => ({
      id: id++,
      name,
      value: parameterValue(value),
    }));
}

function applyParameters(
  endpoint: Record<string, unknown>,
  protectedKeys: Set<string>,
  rows: ParameterRow[],
) {
  for (const key of Object.keys(endpoint)) {
    if (!protectedKeys.has(key)) delete endpoint[key];
  }
  for (const row of rows) {
    const name = row.name.trim();
    if (name) endpoint[name] = row.value;
  }
}

function parameterNameBlocked(name: string, protectedKeys: Set<string>) {
  const compact = name.replace(/[_-]/g, "").toLowerCase();
  return protectedKeys.has(name) || CREDENTIAL_KEYS.has(compact);
}

function hasInvalidParameters(rows: ParameterRow[], protectedKeys: Set<string>) {
  const names = rows.map((row) => row.name.trim());
  return names.some((name) => !name || parameterNameBlocked(name, protectedKeys))
    || new Set(names).size !== names.length;
}

export function AdvancedConfigDrawer({
  value,
  open,
  onApply,
  onClose,
  onClosed,
  onDirtyChange,
}: {
  value: PipelineDefinition;
  open: boolean;
  onApply: (value: PipelineDefinition) => void;
  onClose: () => void;
  onClosed: () => void;
  onDirtyChange: (dirty: boolean) => void;
}) {
  const [form, setForm] = useState(() => structuredClone(value));
  const [mainTab, setMainTab] = useState<MainTab>("advanced");
  const [endpointTab, setEndpointTab] = useState<EndpointTab>("source");
  const [sourceParameters, setSourceParameters] = useState(() =>
    endpointParameters(value.source, SOURCE_PROTECTED_KEYS),
  );
  const [sinkParameters, setSinkParameters] = useState(() =>
    endpointParameters(value.sink, SINK_PROTECTED_KEYS),
  );
  const [nextRowId, setNextRowId] = useState(1000);

  const behavior =
    form.pipeline["schema.change.behavior"] ?? "lenient";

  const excluded = effectiveExcluded(form);
  const parametersInvalid =
    hasInvalidParameters(sourceParameters, SOURCE_PROTECTED_KEYS) ||
    hasInvalidParameters(sinkParameters, SINK_PROTECTED_KEYS);

  const changed = useMemo(() => {
    const next = structuredClone(form);
    applyParameters(next.source, SOURCE_PROTECTED_KEYS, sourceParameters);
    applyParameters(next.sink, SINK_PROTECTED_KEYS, sinkParameters);
    return JSON.stringify(next) !== JSON.stringify(value);
  }, [form, sinkParameters, sourceParameters, value]);

  const markChanged = () => {
    queueMicrotask(() => onDirtyChange(true));
  };

  const change = (next: PipelineDefinition) => {
    setForm(next);
    onDirtyChange(true);
  };

  const changeBehavior = (
    nextBehavior: DataSyncSchemaChangeBehavior,
  ) => {
    const next = structuredClone(form);
    next.pipeline["schema.change.behavior"] = nextBehavior;
    change(next);
  };

  const changeEvent = (
    event: DataSyncSchemaChangeEvent,
    ignored: boolean,
  ) => {
    const next = structuredClone(form);
    const values = new Set(effectiveExcluded(next));

    if (ignored) {
      values.add(event);
    } else {
      values.delete(event);
    }

    next.sink["exclude.schema.changes"] =
      SCHEMA_CHANGE_EVENTS.filter((item) => values.has(item));

    change(next);
  };

  const addParameter = (side: EndpointTab) => {
    const row = { id: nextRowId, name: "", value: "" };
    setNextRowId((current) => current + 1);
    if (side === "source") {
      setSourceParameters((current) => [...current, row]);
    } else {
      setSinkParameters((current) => [...current, row]);
    }
    markChanged();
  };

  const updateParameter = (
    side: EndpointTab,
    id: number,
    patch: Partial<Pick<ParameterRow, "name" | "value">>,
  ) => {
    const update = (rows: ParameterRow[]) =>
      rows.map((row) => row.id === id ? { ...row, ...patch } : row);
    if (side === "source") {
      setSourceParameters(update);
    } else {
      setSinkParameters(update);
    }
    markChanged();
  };

  const removeParameter = (side: EndpointTab, id: number) => {
    const remove = (rows: ParameterRow[]) => rows.filter((row) => row.id !== id);
    if (side === "source") {
      setSourceParameters(remove);
    } else {
      setSinkParameters(remove);
    }
    markChanged();
  };

  const apply = () => {
    if (parametersInvalid) return;
    const next = structuredClone(form);
    applyParameters(next.source, SOURCE_PROTECTED_KEYS, sourceParameters);
    applyParameters(next.sink, SINK_PROTECTED_KEYS, sinkParameters);
    onDirtyChange(false);
    onApply(next);
  };

  return (
    <EditorDrawer
      title="高级配置"
      width={800}
      open={open}
      onClose={onClose}
      onClosed={onClosed}
      footer={
        <>
          <Button
            size="small"
            variant="primary"
            disabled={parametersInvalid}
            onClick={apply}
          >
            确认
          </Button>

          <Button size="small" onClick={onClose}>
            取消
          </Button>
        </>
      }
    >
      <Tabs
        value={mainTab}
        onValueChange={(tab) => setMainTab(tab as MainTab)}
        className="pt-4"
      >
        <TabsList className="gap-0">
          <TabsTab
            value="advanced"
            className="border border-b-0 px-4 py-2 data-active:border-t-2 data-active:border-t-[var(--yak-color-primary)] data-active:bg-[var(--yak-components-panel-bg)]"
          >
            高级参数
          </TabsTab>
          <TabsTab
            value="ddl"
            className="-ml-px border border-b-0 px-4 py-2 data-active:border-t-2 data-active:border-t-[var(--yak-color-primary)] data-active:bg-[var(--yak-components-panel-bg)]"
          >
            DDL能力配置
          </TabsTab>
        </TabsList>

        <TabsPanel value="advanced" className="pt-4">
          <AdvancedParameters
            activeTab={endpointTab}
            parametersInvalid={parametersInvalid}
            onTabChange={setEndpointTab}
            sourceRows={sourceParameters}
            sinkRows={sinkParameters}
            onAdd={addParameter}
            onChange={updateParameter}
            onRemove={removeParameter}
          />
        </TabsPanel>

        <TabsPanel value="ddl" className="pt-4">
          <DdlConfig
            behavior={behavior}
            excluded={excluded}
            onBehaviorChange={changeBehavior}
            onEventChange={changeEvent}
          />
        </TabsPanel>
      </Tabs>

      {!changed ? null : <span className="sr-only">高级配置已修改</span>}
    </EditorDrawer>
  );
}

function AdvancedParameters({
  activeTab,
  parametersInvalid,
  onTabChange,
  sourceRows,
  sinkRows,
  onAdd,
  onChange,
  onRemove,
}: {
  activeTab: EndpointTab;
  parametersInvalid: boolean;
  onTabChange: (tab: EndpointTab) => void;
  sourceRows: ParameterRow[];
  sinkRows: ParameterRow[];
  onAdd: (side: EndpointTab) => void;
  onChange: (
    side: EndpointTab,
    id: number,
    patch: Partial<Pick<ParameterRow, "name" | "value">>,
  ) => void;
  onRemove: (side: EndpointTab, id: number) => void;
}) {
  const rows = activeTab === "source" ? sourceRows : sinkRows;

  const columns: TableColumns<ParameterRow> = [
    {
      key: "name",
      title: "参数名称",
      width: 240,
      render: (_value, row) => (
        <Input
          size="small"
          variant="outlined"
          value={row.name}
          placeholder="请输入参数名称"
          onChange={(event) =>
            onChange(activeTab, row.id, { name: event.target.value })
          }
        />
      ),
    },
    {
      key: "value",
      title: "参数值",
      render: (_value, row) => (
        <Input
          size="small"
          variant="outlined"
          value={row.value}
          placeholder="请输入参数值"
          onChange={(event) =>
            onChange(activeTab, row.id, { value: event.target.value })
          }
        />
      ),
    },
    {
      key: "action",
      title: "操作",
      width: 72,
      align: "center",
      render: (_value, row) => (
        <Button
          size="small"
          variant="ghost"
          aria-label="删除参数"
          onClick={() => onRemove(activeTab, row.id)}
        >
          <Trash2 size={14} aria-hidden="true" />
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-3">
      <div className="rounded-[var(--yak-radius-control-small)] bg-[var(--yak-color-hover)] px-3 py-2 text-xs leading-5 text-[var(--yak-components-muted-text)]">
        高级参数会直接写入对应的 Flink CDC Connector 配置，请确认参数含义后再修改。
      </div>
      {parametersInvalid ? (
        <div className="text-xs text-[var(--yak-color-danger)]">
          参数名称不能为空、不能重复，也不能覆盖基础配置或保存凭据。
        </div>
      ) : null}

      <Tabs
        value={activeTab}
        onValueChange={(tab) => onTabChange(tab as EndpointTab)}
      >
        <div className="flex items-end justify-between border-b border-[var(--yak-components-control-border)]">
          <TabsList className="border-b-0">
            <TabsTab value="source">来源参数</TabsTab>
            <TabsTab value="sink">目标参数</TabsTab>
          </TabsList>
          <Button
            size="small"
            variant="ghost"
            className="mb-1.5"
            onClick={() => onAdd(activeTab)}
          >
            <Plus size={14} aria-hidden="true" />
            添加参数
          </Button>
        </div>

        <TabsPanel value="source" className="pt-2">
          <ParameterTable rows={rows} columns={columns} />
        </TabsPanel>
        <TabsPanel value="sink" className="pt-2">
          <ParameterTable rows={rows} columns={columns} />
        </TabsPanel>
      </Tabs>
    </div>
  );
}

function ParameterTable({
  rows,
  columns,
}: {
  rows: ParameterRow[];
  columns: TableColumns<ParameterRow>;
}) {
  return (
    <Table<ParameterRow>
      columns={columns}
      dataSource={rows}
      rowKey="id"
      size="small"
      bordered
      pagination={false}
      rowHoverable={false}
      emptyText="暂无高级参数"
      emptyClassName="min-h-28"
    />
  );
}

function DdlConfig({
  behavior,
  excluded,
  onBehaviorChange,
  onEventChange,
}: {
  behavior: DataSyncSchemaChangeBehavior;
  excluded: DataSyncSchemaChangeEvent[];
  onBehaviorChange: (behavior: DataSyncSchemaChangeBehavior) => void;
  onEventChange: (event: DataSyncSchemaChangeEvent, ignored: boolean) => void;
}) {
  return (
    <div className="space-y-3">
      <div className="flex justify-end">
        <Popover>
          <PopoverTrigger
            type="button"
            openOnHover
            delay={120}
            closeDelay={120}
            className="flex items-center gap-1 border-0 bg-transparent p-0 text-xs text-[var(--yak-color-primary)] outline-none"
          >
            <span>处理策略说明</span>
            <CircleHelp size={14} aria-hidden="true" />
          </PopoverTrigger>
          <PopoverContent
            side="bottom"
            align="end"
            className="w-[340px] max-w-[calc(100vw-2rem)] rounded-md p-4 text-xs leading-5"
          >
            <div className="space-y-1">
              <p><strong>严格同步：</strong>同步上游表结构变更，执行失败时任务失败。</p>
              <p><strong>尽力同步：</strong>尝试同步表结构变更，目标端不支持时尽量继续运行。</p>
              <p><strong>安全演进：</strong>以更宽松的方式处理表结构变化，优先避免数据丢失。</p>
              <p><strong>忽略变更：</strong>忽略上游表结构变更，不向目标端执行。</p>
              <p><strong>遇变更失败：</strong>检测到上游表结构变更后直接让任务失败。</p>
              <p className="pt-1 text-[var(--yak-components-muted-text)]">
                单项配置中，“忽略”表示该类 DDL 不会下发到目标端。
              </p>
            </div>
          </PopoverContent>
        </Popover>
      </div>

      <ConfigRow label="处理策略">
        <Select<DataSyncSchemaChangeBehavior>
          size="small"
          items={Object.fromEntries(
            SCHEMA_CHANGE_BEHAVIORS.map((item) => [
              item,
              BEHAVIOR_LABELS[item],
            ]),
          )}
          value={behavior}
          onValueChange={(next) => {
            if (next) onBehaviorChange(next);
          }}
        >
          <SelectTrigger variant="outlined">
            <SelectValue />
          </SelectTrigger>

          <SelectContent>
            {SCHEMA_CHANGE_BEHAVIORS.map((item) => (
              <SelectItem key={item} value={item}>
                <SelectItemText>{BEHAVIOR_LABELS[item]}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </ConfigRow>

      <div className="h-px bg-[var(--yak-components-panel-border)]" />

      {SCHEMA_CHANGE_EVENTS.map((event) => {
        const ignored = excluded.includes(event);

        return (
          <ConfigRow
            key={event}
            label={EVENT_LABELS[event]}
            required
          >
            <Select<"normal" | "ignore">
              size="small"
              items={{
                normal: "正常处理",
                ignore: "忽略",
              }}
              value={ignored ? "ignore" : "normal"}
              onValueChange={(next) => {
                if (next) onEventChange(event, next === "ignore");
              }}
            >
              <SelectTrigger variant="outlined">
                <SelectValue />
              </SelectTrigger>

              <SelectContent>
                <SelectItem value="normal">
                  <SelectItemText>正常处理</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>

                <SelectItem value="ignore">
                  <SelectItemText>忽略</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              </SelectContent>
            </Select>
          </ConfigRow>
        );
      })}
    </div>
  );
}

function ConfigRow({
  label,
  required = false,
  children,
}: {
  label: string;
  required?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="grid grid-cols-[120px_minmax(0,1fr)] items-center gap-4">
      <div className="flex items-center justify-end text-sm">
        {required ? (
          <span className="mr-1 text-[var(--yak-color-danger)]">*</span>
        ) : null}

        <span>{label}：</span>
      </div>

      <div className="min-w-0">
        {children}
      </div>
    </div>
  );
}
