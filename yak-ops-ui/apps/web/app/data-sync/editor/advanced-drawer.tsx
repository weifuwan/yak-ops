import { useState } from "react";
import {
  Alert, Button, Field, FieldLabel, Input, Switch, Tabs, TabsList,
  TabsPanel, TabsTab, Textarea,
} from "@yak-ops/yak-ui";
import {
  setPipelineOption, setSchemaChangeBehavior,
  type PipelineDefinition,
} from "@/service/data-sync/native-pipeline";
import { EditorDrawer } from "./editor-drawer";

type AdvancedTab = "parameters" | "ddl" | "engine";
type ParameterTab = "read" | "write" | "runtime";
const EDITABLE_KEYS = [
  "parallelism", "local-time-zone", "schema.operator.uid", "route-mode",
  "transform.decimal.precision.mode",
];

export function AdvancedDrawer({
  value, open, onApply, onClose, onClosed, onDirtyChange,
}: {
  value: PipelineDefinition;
  open: boolean;
  onApply: (next: PipelineDefinition) => void;
  onClose: () => void;
  onClosed: () => void;
  onDirtyChange: (dirty: boolean) => void;
}) {
  const [definition, setDefinition] = useState<PipelineDefinition>(() => structuredClone(value));
  const [tab, setTab] = useState<AdvancedTab>("parameters");
  const [parameterTab, setParameterTab] = useState<ParameterTab>("read");
  const [error, setError] = useState("");
  const [engineText, setEngineText] = useState(() => JSON.stringify(
    Object.fromEntries(Object.entries(value.pipeline).filter(([key]) => EDITABLE_KEYS.includes(key))),
    null, 2,
  ));

  const update = (next: PipelineDefinition) => {
    setDefinition(next);
    setError("");
    onDirtyChange(JSON.stringify(next) !== JSON.stringify(value));
  };
  const changeEngineInput = (key: "parallelism" | "local-time-zone", raw: string) => {
    try {
      const options: unknown = JSON.parse(engineText || "{}");
      if (!options || typeof options !== "object" || Array.isArray(options)) {
        throw new Error("请先修正引擎参数 JSON");
      }
      const values = { ...options } as Record<string, unknown>;
      if (raw) values[key] = raw;
      else delete values[key];
      update(setPipelineOption(definition, key, raw || undefined));
      setEngineText(JSON.stringify(values, null, 2));
    } catch {
      setTab("engine");
      setError("请先修正引擎参数 JSON，再修改运行时配置");
    }
  };
  const parseEngine = () => {
    const options: unknown = JSON.parse(engineText);
    if (!options || typeof options !== "object" || Array.isArray(options))
      throw new Error("引擎参数必须是 JSON 对象");
    for (const key of Object.keys(options)) {
      if (!EDITABLE_KEYS.includes(key)) throw new Error("不支持修改此参数：" + key);
      if (key !== "parallelism" && typeof (options as Record<string, unknown>)[key] !== "string") {
        throw new Error(key + " 必须是字符串");
      }
    }
    const next = structuredClone(definition);
    for (const key of EDITABLE_KEYS) delete next.pipeline[key];
    Object.assign(next.pipeline, options);
    const count = next.pipeline.parallelism;
    if (count !== undefined && (!/^\d+$/.test(String(count)) ||
        Number(count) < 1 || Number(count) > 128)) {
      throw new Error("并行度必须在 1 到 128 之间");
    }
    if (next.pipeline["local-time-zone"] !== undefined &&
        typeof next.pipeline["local-time-zone"] !== "string") {
      throw new Error("运行时区必须是字符串");
    }
    return next;
  };
  const apply = () => {
    try {
      const next = parseEngine();
      onApply(next);
    } catch (failure) {
      setTab("engine");
      setError(failure instanceof Error ? failure.message : "引擎参数无效");
    }
  };

  return (
    <EditorDrawer open={open} onClosed={onClosed} title="高级配置" width={800}
      onClose={onClose} footer={
        <>
          <Button size="small" variant="primary" onClick={apply}>确认</Button>
          <Button size="small" onClick={onClose}>取消</Button>
        </>
      }>
      <Tabs value={tab} onValueChange={(next) => setTab(next as AdvancedTab)}>
        <TabsList>
          <TabsTab value="parameters">高级参数</TabsTab>
          <TabsTab value="ddl">表结构同步</TabsTab>
          <TabsTab value="engine">引擎参数配置</TabsTab>
        </TabsList>
        {error ? <Alert className="mt-4">{error}</Alert> : null}
        <TabsPanel value="parameters" className="pt-5">
          <Tabs value={parameterTab} onValueChange={(next) => setParameterTab(next as ParameterTab)}>
            <TabsList>
              <TabsTab value="read">读端配置</TabsTab>
              <TabsTab value="write">写端配置</TabsTab>
              <TabsTab value="runtime">运行时配置</TabsTab>
            </TabsList>
            <TabsPanel value="read" className="space-y-5 pt-4">
              <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel>读取策略</FieldLabel>
                <span className="text-sm">全量后持续增量</span>
              </Field>
              <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel>启动位置</FieldLabel>
                <span className="text-sm">INITIAL</span>
              </Field>
            </TabsPanel>
            <TabsPanel value="write" className="pt-5">
              <p className="text-sm text-[var(--yak-components-muted-text)]">
                当前 JDBC Sink 按来源主键执行 UPSERT；不支持自定义目标主键和 SQL Hooks。
              </p>
            </TabsPanel>
            <TabsPanel value="runtime" className="space-y-5 pt-4">
              <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel>并行度</FieldLabel>
                <Input size="small" variant="outlined" aria-label="并行度" inputMode="numeric"
                  value={definition.pipeline.parallelism === undefined ? "" : String(definition.pipeline.parallelism)}
                  onChange={(event) => changeEngineInput("parallelism", event.target.value)} />
              </Field>
              <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel>时区</FieldLabel>
                <Input size="small" variant="outlined" aria-label="运行时区"
                  value={String(definition.pipeline["local-time-zone"] || "")}
                  onChange={(event) => changeEngineInput("local-time-zone", event.target.value)} />
              </Field>
              <p className="text-xs text-[var(--yak-components-muted-text)]">
                当前实时同步无需 Cron 调度，启动与停止由工作流执行接口管理。
              </p>
            </TabsPanel>
          </Tabs>
        </TabsPanel>
        <TabsPanel value="ddl" className="space-y-5 pt-5">
          <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel>目标表初始化</FieldLabel>
            <span className="text-sm">创建或调整目标表</span>
          </Field>
          <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel>同步结构变更</FieldLabel>
            <Switch size="small" aria-label="同步结构变更"
              checked={definition.pipeline["schema.change.behavior"] === "evolve"}
              onCheckedChange={(enabled) => update(setSchemaChangeBehavior(definition, enabled))} />
          </Field>
          <p className="text-xs text-[var(--yak-components-muted-text)]">
            当前 Runtime 始终禁止删除目标字段，不能更改此安全策略。
          </p>
        </TabsPanel>
        <TabsPanel value="engine" className="space-y-3 pt-5">
          <p className="text-xs text-[var(--yak-components-muted-text)]">
            直接编辑原生 Pipeline 运行参数；固定运行模式及只读 Connector 配置不会被重写。
          </p>
          <Textarea aria-label="Pipeline 引擎参数" variant="outlined" rows={12}
            value={engineText}
            onValueChange={(next) => { setEngineText(next); setError(""); onDirtyChange(true); }} />
        </TabsPanel>
      </Tabs>
    </EditorDrawer>
  );
}
