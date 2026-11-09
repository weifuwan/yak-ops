import {
  useEffect,
  useState,
  type ReactNode,
} from "react";
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
  Switch,
  Tabs,
  TabsList,
  TabsPanel,
  TabsTab,
} from "@yak-ops/yak-ui";
import { CircleHelp } from "lucide-react";
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

type MainTab = "parameters" | "ddl" | "engine";
type EndpointTab = "source" | "sink";
type Endpoint = "source" | "sink";

interface BaseAdvancedOption {
  key: string;
  label: string;
  description: string;
}

interface NumberAdvancedOption extends BaseAdvancedOption {
  type: "number";
  defaultValue: number;
  min?: number;
  max?: number;
  suffix?: string;
}

interface StringAdvancedOption extends BaseAdvancedOption {
  type: "string";
  defaultValue: string;
  placeholder?: string;
}

interface BooleanAdvancedOption extends BaseAdvancedOption {
  type: "boolean";
  defaultValue: boolean;
}

type AdvancedOption =
  | NumberAdvancedOption
  | StringAdvancedOption
  | BooleanAdvancedOption;

const BEHAVIOR_LABELS: Record<
  DataSyncSchemaChangeBehavior,
  string
> = {
  evolve: "严格同步",
  try_evolve: "尽力同步",
  lenient: "安全演进",
  ignore: "忽略变更",
  exception: "遇变更失败",
};

const EVENT_LABELS: Record<
  DataSyncSchemaChangeEvent,
  string
> = {
  "create.table": "新建表",
  "drop.table": "删除表",
  "add.column": "新增列",
  "drop.column": "删除列",
  "rename.column": "重命名列",
  "alter.column.type": "修改列类型",
  "truncate.table": "清空表",
  "alter.table.comment": "修改表注释",
};

const MYSQL_SOURCE_ADVANCED_OPTIONS: readonly AdvancedOption[] = [
  {
    key: "scan.incremental.snapshot.chunk.size",
    label: "快照分片大小",
    type: "number",
    defaultValue: 8096,
    min: 2,
    description: "每个快照分片读取的记录数。",
  },
  {
    key: "scan.snapshot.fetch.size",
    label: "单次读取条数",
    type: "number",
    defaultValue: 1024,
    min: 2,
    description: "每次从数据库读取的记录数。",
  },
  {
    key: "connect.timeout",
    label: "连接超时时间",
    type: "string",
    defaultValue: "30s",
    placeholder: "例如 30s",
    description: "连接数据库的最大等待时间。",
  },
  {
    key: "connect.max-retries",
    label: "连接重试次数",
    type: "number",
    defaultValue: 3,
    min: 1,
    description: "数据库连接失败后的重试次数。",
  },
  {
    key: "connection.pool.size",
    label: "连接池大小",
    type: "number",
    defaultValue: 20,
    min: 2,
    description: "读取数据使用的数据库连接数。",
  },
  {
    key: "heartbeat.interval",
    label: "心跳间隔",
    type: "string",
    defaultValue: "30s",
    placeholder: "例如 30s",
    description: "Binlog 心跳发送间隔。",
  },
  {
    key: "include-comments.enabled",
    label: "同步注释",
    type: "boolean",
    defaultValue: false,
    description: "同步表和字段注释。",
  },
  {
    key: "treat-tinyint1-as-boolean.enabled",
    label: "TINYINT(1) 转布尔值",
    type: "boolean",
    defaultValue: true,
    description: "将 TINYINT(1) 按布尔值处理。",
  },
];

const JDBC_SINK_ADVANCED_OPTIONS: readonly AdvancedOption[] = [
  {
    key: "connect.timeout",
    label: "连接超时时间",
    type: "string",
    defaultValue: "30s",
    placeholder: "例如 30s",
    description: "连接目标数据库的最大等待时间。",
  },
  {
    key: "connect.max-retries",
    label: "连接重试次数",
    type: "number",
    defaultValue: 3,
    min: 0,
    description: "数据库连接失败后的重试次数。",
  },
  {
    key: "connection.pool.size",
    label: "连接池大小",
    type: "number",
    defaultValue: 20,
    min: 1,
    description: "写入数据使用的数据库连接数。",
  },
  {
    key: "write.batch.interval.ms",
    label: "批量写入间隔",
    type: "number",
    defaultValue: 0,
    min: 0,
    suffix: "ms",
    description: "定时提交一批数据的间隔。",
  },
  {
    key: "write.batch.size",
    label: "单批写入条数",
    type: "number",
    defaultValue: 512,
    min: 1,
    description: "每批最多写入的记录数。",
  },
  {
    key: "write.max-open-tables",
    label: "最大打开表数",
    type: "number",
    defaultValue: 32,
    min: 1,
    description: "同时保持打开的最大表数量。",
  },
  {
    key: "write.max.retries",
    label: "写入重试次数",
    type: "number",
    defaultValue: 3,
    min: 0,
    description: "数据写入失败后的重试次数。",
  },
];

const MAIN_TAB_CLASS = [
  "relative -mb-px -ml-px first:!ml-0",
  "!inline-flex !h-8 !items-center !justify-center",
  "!rounded-t-[2px]",
  "!border !border-[var(--yak-components-control-border)]",
  "!bg-white !px-4 !py-0",
  "!text-xs !font-normal",
  "!text-[var(--yak-components-tab-text)]",
  "hover:!text-[var(--yak-components-tab-text-hover)]",
  "data-active:!z-10",
  "data-active:!border-b-white",
  "data-active:!font-medium",
  "data-active:!text-[var(--yak-color-primary)]",
  "after:!hidden",
  "before:absolute before:inset-x-0 before:-top-px",
  "before:h-0.5 before:bg-transparent before:content-['']",
  "data-active:before:bg-[var(--yak-color-primary)]",
].join(" ");

function effectiveExcluded(
  definition: PipelineDefinition,
): DataSyncSchemaChangeEvent[] {
  const configured =
    definition.sink["exclude.schema.changes"];

  if (Array.isArray(configured)) {
    return configured;
  }

  return (
    definition.pipeline["schema.change.behavior"] ??
    "lenient"
  ) === "lenient"
    ? ["truncate.table", "drop.table"]
    : [];
}

function hasOwn(
  object: Record<string, unknown>,
  key: string,
): boolean {
  return Object.prototype.hasOwnProperty.call(
    object,
    key,
  );
}

function effectiveNumberValue(
  object: Record<string, unknown>,
  option: NumberAdvancedOption,
): number {
  const value = object[option.key];

  if (
    typeof value === "number" &&
    Number.isFinite(value)
  ) {
    return value;
  }

  if (
    typeof value === "string" &&
    value.trim() !== "" &&
    Number.isFinite(Number(value))
  ) {
    return Number(value);
  }

  return option.defaultValue;
}

function effectiveStringValue(
  object: Record<string, unknown>,
  option: StringAdvancedOption,
): string {
  const value = object[option.key];

  return typeof value === "string"
    ? value
    : option.defaultValue;
}

function effectiveBooleanValue(
  object: Record<string, unknown>,
  option: BooleanAdvancedOption,
): boolean {
  const value = object[option.key];

  if (typeof value === "boolean") {
    return value;
  }

  if (value === "true") {
    return true;
  }

  if (value === "false") {
    return false;
  }

  return option.defaultValue;
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
  const [form, setForm] =
    useState<PipelineDefinition>(() =>
      structuredClone(value),
    );

  const [mainTab, setMainTab] =
    useState<MainTab>("parameters");

  const [endpointTab, setEndpointTab] =
    useState<EndpointTab>("source");

  useEffect(() => {
    if (!open) {
      return;
    }

    setForm(structuredClone(value));
    setMainTab("parameters");
    setEndpointTab("source");
    onDirtyChange(false);
  }, [open]);

  const change = (
    mutate: (
      definition: PipelineDefinition,
    ) => void,
  ) => {
    const next = structuredClone(form);

    mutate(next);

    setForm(next);

    onDirtyChange(
      JSON.stringify(next) !==
        JSON.stringify(value),
    );
  };

  const setEndpointOption = (
    endpoint: Endpoint,
    key: string,
    optionValue: unknown,
    defaultValue: unknown,
  ) => {
    change((definition) => {
      const target = definition[endpoint];

      if (optionValue === defaultValue) {
        delete target[key];
        return;
      }

      target[key] = optionValue;
    });
  };

  const resetEndpointOption = (
    endpoint: Endpoint,
    key: string,
  ) => {
    change((definition) => {
      delete definition[endpoint][key];
    });
  };

  const changeBehavior = (
    nextBehavior: DataSyncSchemaChangeBehavior,
  ) => {
    change((definition) => {
      definition.pipeline[
        "schema.change.behavior"
      ] = nextBehavior;
    });
  };

  const changeEvent = (
    event: DataSyncSchemaChangeEvent,
    ignored: boolean,
  ) => {
    change((definition) => {
      const values = new Set(
        effectiveExcluded(definition),
      );

      if (ignored) {
        values.add(event);
      } else {
        values.delete(event);
      }

      definition.sink[
        "exclude.schema.changes"
      ] = SCHEMA_CHANGE_EVENTS.filter(
        (item) => values.has(item),
      );
    });
  };

  const behavior =
    form.pipeline[
      "schema.change.behavior"
    ] ?? "lenient";

  const excluded = effectiveExcluded(form);

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
            size="sm"
            variant="primary"
            onClick={() =>
              onApply(structuredClone(form))
            }
          >
            确认
          </Button>

          <Button variant="secondary"
            size="sm"
            onClick={onClose}
          >
            取消
          </Button>
        </>
      }
    >
      <div className="pt-4">
        <Tabs
          value={mainTab}
          onValueChange={(next) =>
            setMainTab(next as MainTab)
          }
        >
          <TabsList
            className="
              !h-[33px]
              !items-end
              !gap-0
              !border-b
              !border-[var(--yak-components-control-border)]
            "
          >
            <TabsTab
              value="parameters"
              className={MAIN_TAB_CLASS}
            >
              高级参数
            </TabsTab>

            <TabsTab
              value="ddl"
              className={MAIN_TAB_CLASS}
            >
              DDL能力配置
            </TabsTab>

            <TabsTab
              value="engine"
              className={MAIN_TAB_CLASS}
            >
              引擎参数配置
            </TabsTab>
          </TabsList>

          <TabsPanel value="parameters">
            <AdvancedParameters
              definition={form}
              endpointTab={endpointTab}
              onEndpointTabChange={
                setEndpointTab
              }
              onChange={
                setEndpointOption
              }
              onReset={
                resetEndpointOption
              }
            />
          </TabsPanel>

          <TabsPanel value="ddl">
            <SchemaChangeConfig
              behavior={behavior}
              excluded={excluded}
              onBehaviorChange={
                changeBehavior
              }
              onEventChange={
                changeEvent
              }
            />
          </TabsPanel>

          <TabsPanel value="engine">
            <EngineConfig
              definition={form}
              onChange={change}
            />
          </TabsPanel>
        </Tabs>
      </div>
    </EditorDrawer>
  );
}

function AdvancedParameters({
  definition,
  endpointTab,
  onEndpointTabChange,
  onChange,
  onReset,
}: {
  definition: PipelineDefinition;
  endpointTab: EndpointTab;
  onEndpointTabChange: (
    value: EndpointTab,
  ) => void;
  onChange: (
    endpoint: Endpoint,
    key: string,
    value: unknown,
    defaultValue: unknown,
  ) => void;
  onReset: (
    endpoint: Endpoint,
    key: string,
  ) => void;
}) {
  return (
    <div className="pt-4">
      <Tabs
        value={endpointTab}
        onValueChange={(next) =>
          onEndpointTabChange(
            next as EndpointTab,
          )
        }
      >
        <TabsList>
          <TabsTab value="source">
            来源参数
          </TabsTab>

          <TabsTab value="sink">
            目标参数
          </TabsTab>
        </TabsList>

        <TabsPanel
          value="source"
          className="pt-3"
        >
          <AdvancedOptionTable
            endpoint="source"
            values={definition.source}
            options={
              MYSQL_SOURCE_ADVANCED_OPTIONS
            }
            onChange={onChange}
            onReset={onReset}
          />
        </TabsPanel>

        <TabsPanel
          value="sink"
          className="pt-3"
        >
          <AdvancedOptionTable
            endpoint="sink"
            values={definition.sink}
            options={
              JDBC_SINK_ADVANCED_OPTIONS
            }
            onChange={onChange}
            onReset={onReset}
          />
        </TabsPanel>
      </Tabs>
    </div>
  );
}

function AdvancedOptionTable({
  endpoint,
  values,
  options,
  onChange,
  onReset,
}: {
  endpoint: Endpoint;
  values: Record<string, unknown>;
  options: readonly AdvancedOption[];
  onChange: (
    endpoint: Endpoint,
    key: string,
    value: unknown,
    defaultValue: unknown,
  ) => void;
  onReset: (
    endpoint: Endpoint,
    key: string,
  ) => void;
}) {
  return (
    <div className="overflow-hidden border border-[var(--yak-components-panel-border)]">
      <div className="grid grid-cols-[200px_220px_minmax(0,1fr)] bg-[var(--yak-color-hover)] text-xs font-medium text-[var(--yak-components-field-label)]">
        <div className="px-3 py-2">
          参数名称
        </div>

        <div className="border-l border-[var(--yak-components-panel-border)] px-3 py-2">
          参数值
        </div>

        <div className="border-l border-[var(--yak-components-panel-border)] px-3 py-2">
          参数说明
        </div>
      </div>

      {options.map((option) => {
        const configured = hasOwn(
          values,
          option.key,
        );

        return (
          <div
            key={option.key}
            className="grid min-h-[48px] grid-cols-[200px_220px_minmax(0,1fr)] border-t border-[var(--yak-components-panel-border)] text-xs"
          >
            <div className="flex items-center px-3">
              {option.label}
            </div>

            <div className="flex items-center border-l border-[var(--yak-components-panel-border)] px-3 py-2">
              <OptionEditor
                values={values}
                option={option}
                onChange={(next) =>
                  onChange(
                    endpoint,
                    option.key,
                    next,
                    option.defaultValue,
                  )
                }
                onReset={() =>
                  onReset(
                    endpoint,
                    option.key,
                  )
                }
              />
            </div>

            <div className="border-l border-[var(--yak-components-panel-border)] px-3 py-2 leading-5 text-[var(--yak-components-muted-text)]">
              <div>
                {option.description}
              </div>

              <div className="flex items-center gap-2">
                <span>
                  默认值：
                  <DefaultValue
                    option={option}
                  />
                </span>

                {configured ? (
                  <Button
                    size="sm"
                    variant="ghost"
                    className="!h-auto !min-h-0 !px-0 !py-0 text-xs font-normal text-[var(--yak-color-primary)]"
                    onClick={() =>
                      onReset(
                        endpoint,
                        option.key,
                      )
                    }
                  >
                    恢复默认
                  </Button>
                ) : null}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function OptionEditor({
  values,
  option,
  onChange,
  onReset,
}: {
  values: Record<string, unknown>;
  option: AdvancedOption;
  onChange: (value: unknown) => void;
  onReset: () => void;
}) {
  if (option.type === "boolean") {
    const value =
      effectiveBooleanValue(
        values,
        option,
      );

    return (
      <div className="flex items-center gap-2">
        <Switch
          size="small"
          checked={value}
          aria-label={option.label}
          onCheckedChange={(checked) =>
            onChange(checked)
          }
        />

        <span className="text-[var(--yak-components-muted-text)]">
          {value ? "开启" : "关闭"}
        </span>
      </div>
    );
  }

  if (option.type === "number") {
    return (
      <NumberOptionInput
        value={effectiveNumberValue(
          values,
          option,
        )}
        option={option}
        onChange={onChange}
        onReset={onReset}
      />
    );
  }

  return (
    <StringOptionInput
      value={effectiveStringValue(
        values,
        option,
      )}
      option={option}
      onChange={onChange}
      onReset={onReset}
    />
  );
}

function NumberOptionInput({
  value,
  option,
  onChange,
  onReset,
}: {
  value: number;
  option: NumberAdvancedOption;
  onChange: (value: number) => void;
  onReset: () => void;
}) {
  const [draft, setDraft] =
    useState(String(value));

  const [error, setError] =
    useState<string>();

  useEffect(() => {
    setDraft(String(value));
    setError(undefined);
  }, [value]);

  const commit = () => {
    const text = draft.trim();

    if (!text) {
      onReset();
      return;
    }

    const next = Number(text);

    if (
      !Number.isFinite(next) ||
      !Number.isInteger(next)
    ) {
      setError("请输入整数");
      return;
    }

    if (
      option.min !== undefined &&
      next < option.min
    ) {
      setError(
        `不能小于 ${option.min}`,
      );
      return;
    }

    if (
      option.max !== undefined &&
      next > option.max
    ) {
      setError(
        `不能大于 ${option.max}`,
      );
      return;
    }

    setError(undefined);
    onChange(next);
  };

  return (
    <div className="w-full">
      <div className="flex items-center gap-2">
        <Input
          type="number"
          size="small"
          variant="outlined"
          value={draft}
          min={option.min}
          max={option.max}
          step={1}
          aria-label={option.label}
          onChange={(event) => {
            setError(undefined);
            setDraft(
              event.target.value,
            );
          }}
          onBlur={commit}
          onKeyDown={(event) => {
            if (
              event.key === "Enter"
            ) {
              event.currentTarget.blur();
            }

            if (
              event.key === "Escape"
            ) {
              setDraft(
                String(value),
              );
              setError(undefined);
              event.currentTarget.blur();
            }
          }}
        />

        {option.suffix ? (
          <span className="shrink-0 text-[var(--yak-components-muted-text)]">
            {option.suffix}
          </span>
        ) : null}
      </div>

      {error ? (
        <div className="mt-1 text-[11px] text-[var(--yak-color-danger)]">
          {error}
        </div>
      ) : null}
    </div>
  );
}

function StringOptionInput({
  value,
  option,
  onChange,
  onReset,
}: {
  value: string;
  option: StringAdvancedOption;
  onChange: (value: string) => void;
  onReset: () => void;
}) {
  const [draft, setDraft] =
    useState(value);

  useEffect(() => {
    setDraft(value);
  }, [value]);

  const commit = () => {
    const next = draft.trim();

    if (!next) {
      onReset();
      return;
    }

    onChange(next);
  };

  return (
    <Input
      size="small"
      variant="outlined"
      value={draft}
      placeholder={option.placeholder}
      aria-label={option.label}
      onChange={(event) =>
        setDraft(event.target.value)
      }
      onBlur={commit}
      onKeyDown={(event) => {
        if (event.key === "Enter") {
          event.currentTarget.blur();
        }

        if (event.key === "Escape") {
          setDraft(value);
          event.currentTarget.blur();
        }
      }}
    />
  );
}

function DefaultValue({
  option,
}: {
  option: AdvancedOption;
}) {
  if (option.type === "boolean") {
    return (
      <>
        {option.defaultValue
          ? "开启"
          : "关闭"}
      </>
    );
  }

  return (
    <>
      {option.defaultValue}
      {option.type === "number" &&
      option.suffix
        ? ` ${option.suffix}`
        : ""}
    </>
  );
}

function EngineConfig({
  definition,
  onChange,
}: {
  definition: PipelineDefinition;
  onChange: (
    mutate: (
      definition: PipelineDefinition,
    ) => void,
  ) => void;
}) {
  const parallelismValue =
    definition.pipeline.parallelism;

  const parallelism =
    typeof parallelismValue === "number"
      ? parallelismValue
      : Number(parallelismValue ?? 1);

  const timeZoneValue =
    definition.pipeline[
      "local-time-zone"
    ];

  const timeZone =
    typeof timeZoneValue === "string"
      ? timeZoneValue
      : "";

  return (
    <div className="pt-4">
      <div className="overflow-hidden border border-[var(--yak-components-panel-border)]">
        <div className="grid grid-cols-[200px_220px_minmax(0,1fr)] bg-[var(--yak-color-hover)] text-xs font-medium text-[var(--yak-components-field-label)]">
          <div className="px-3 py-2">
            参数名称
          </div>

          <div className="border-l border-[var(--yak-components-panel-border)] px-3 py-2">
            参数值
          </div>

          <div className="border-l border-[var(--yak-components-panel-border)] px-3 py-2">
            参数说明
          </div>
        </div>

        <EngineParameterRow
          label="并行度"
          description="任务运行的默认并行度。"
          defaultValue="1"
        >
          <Input
            type="number"
            size="small"
            variant="outlined"
            min={1}
            max={128}
            step={1}
            value={
              Number.isFinite(
                parallelism,
              )
                ? parallelism
                : 1
            }
            onChange={(event) => {
              const next = Number(
                event.target.value,
              );

              if (
                !Number.isInteger(
                  next,
                ) ||
                next < 1 ||
                next > 128
              ) {
                return;
              }

              onChange(
                (
                  nextDefinition,
                ) => {
                  if (next === 1) {
                    delete nextDefinition
                      .pipeline
                      .parallelism;
                  } else {
                    nextDefinition
                      .pipeline
                      .parallelism =
                      next;
                  }
                },
              );
            }}
          />
        </EngineParameterRow>

        <EngineParameterRow
          label="运行时区"
          description="时间字段使用的运行时区。"
          defaultValue="系统默认"
        >
          <Input
            size="small"
            variant="outlined"
            value={timeZone}
            placeholder="例如 Asia/Shanghai"
            onChange={(event) => {
              const next =
                event.target.value;

              onChange(
                (
                  nextDefinition,
                ) => {
                  if (next.trim()) {
                    nextDefinition
                      .pipeline[
                      "local-time-zone"
                    ] = next;
                  } else {
                    delete nextDefinition
                      .pipeline[
                      "local-time-zone"
                    ];
                  }
                },
              );
            }}
          />
        </EngineParameterRow>
      </div>
    </div>
  );
}

function EngineParameterRow({
  label,
  description,
  defaultValue,
  children,
}: {
  label: string;
  description: string;
  defaultValue: string;
  children: ReactNode;
}) {
  return (
    <div className="grid min-h-[48px] grid-cols-[200px_220px_minmax(0,1fr)] border-t border-[var(--yak-components-panel-border)] text-xs">
      <div className="flex items-center px-3">
        {label}
      </div>

      <div className="flex items-center border-l border-[var(--yak-components-panel-border)] px-3 py-2">
        {children}
      </div>

      <div className="border-l border-[var(--yak-components-panel-border)] px-3 py-2 leading-5 text-[var(--yak-components-muted-text)]">
        <div>
          {description}
        </div>

        <div>
          默认值：{defaultValue}
        </div>
      </div>
    </div>
  );
}

function SchemaChangeConfig({
  behavior,
  excluded,
  onBehaviorChange,
  onEventChange,
}: {
  behavior: DataSyncSchemaChangeBehavior;
  excluded: DataSyncSchemaChangeEvent[];
  onBehaviorChange: (
    behavior: DataSyncSchemaChangeBehavior,
  ) => void;
  onEventChange: (
    event: DataSyncSchemaChangeEvent,
    ignored: boolean,
  ) => void;
}) {
  return (
    <div className="space-y-3 pt-4">
      <div className="flex justify-end">
        <Popover>
          <PopoverTrigger
            type="button"
            openOnHover
            delay={120}
            closeDelay={120}
            className="flex items-center gap-1 border-0 bg-transparent p-0 text-xs text-[var(--yak-color-primary)] outline-none"
          >
            <span>
              处理策略说明
            </span>

            <CircleHelp
              size={14}
              aria-hidden="true"
            />
          </PopoverTrigger>

          <PopoverContent
            side="bottom"
            align="end"
            className="w-[340px] max-w-[calc(100vw-2rem)] rounded-md p-4 text-xs leading-5"
          >
            <div className="space-y-1">
              <p>
                <strong>
                  严格同步：
                </strong>
                同步结构变更，失败时任务失败。
              </p>

              <p>
                <strong>
                  尽力同步：
                </strong>
                尽量同步目标端支持的结构变更。
              </p>

              <p>
                <strong>
                  安全演进：
                </strong>
                优先以安全方式处理结构变化。
              </p>

              <p>
                <strong>
                  忽略变更：
                </strong>
                不向目标端同步结构变更。
              </p>

              <p>
                <strong>
                  遇变更失败：
                </strong>
                检测到结构变更后任务失败。
              </p>
            </div>
          </PopoverContent>
        </Popover>
      </div>

      <ConfigRow label="处理策略">
        <Select<DataSyncSchemaChangeBehavior>
          size="small"
          items={Object.fromEntries(
            SCHEMA_CHANGE_BEHAVIORS.map(
              (item) => [
                item,
                BEHAVIOR_LABELS[item],
              ],
            ),
          )}
          value={behavior}
          onValueChange={(next) => {
            if (next) {
              onBehaviorChange(next);
            }
          }}
        >
          <SelectTrigger variant="outlined">
            <SelectValue />
          </SelectTrigger>

          <SelectContent>
            {SCHEMA_CHANGE_BEHAVIORS.map(
              (item) => (
                <SelectItem
                  key={item}
                  value={item}
                >
                  <SelectItemText>
                    {
                      BEHAVIOR_LABELS[
                        item
                      ]
                    }
                  </SelectItemText>

                  <SelectItemIndicator />
                </SelectItem>
              ),
            )}
          </SelectContent>
        </Select>
      </ConfigRow>

      <div className="h-px bg-[var(--yak-components-panel-border)]" />

      {SCHEMA_CHANGE_EVENTS.map(
        (event) => {
          const ignored =
            excluded.includes(event);

          return (
            <ConfigRow
              key={event}
              label={
                EVENT_LABELS[event]
              }
              required
            >
              <Select<
                "normal" | "ignore"
              >
                size="small"
                items={{
                  normal: "正常处理",
                  ignore: "忽略",
                }}
                value={
                  ignored
                    ? "ignore"
                    : "normal"
                }
                onValueChange={(
                  next,
                ) => {
                  if (next) {
                    onEventChange(
                      event,
                      next ===
                        "ignore",
                    );
                  }
                }}
              >
                <SelectTrigger variant="outlined">
                  <SelectValue />
                </SelectTrigger>

                <SelectContent>
                  <SelectItem value="normal">
                    <SelectItemText>
                      正常处理
                    </SelectItemText>

                    <SelectItemIndicator />
                  </SelectItem>

                  <SelectItem value="ignore">
                    <SelectItemText>
                      忽略
                    </SelectItemText>

                    <SelectItemIndicator />
                  </SelectItem>
                </SelectContent>
              </Select>
            </ConfigRow>
          );
        },
      )}
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
          <span className="mr-1 text-[var(--yak-color-danger)]">
            *
          </span>
        ) : null}

        <span>
          {label}：
        </span>
      </div>

      <div className="min-w-0">
        {children}
      </div>
    </div>
  );
}