import { useState, type ComponentProps } from "react";
import {
  Alert,
  Button,
  Field,
  FieldLabel,
  Input,
  Tabs,
  TabsList,
  TabsPanel,
  TabsTab,
  Textarea,
} from "@yak-ops/yak-ui";
import type { DataSyncSolution, DataSyncWriteMode } from "@/service/data-sync";
import type { ScheduleTimeRule } from "@/service/schedule";
import { parameterFingerprint } from "@/service/data-sync/versions";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { findStepCombination } from "../solution/presentation";
import { EditorDrawer } from "./editor-drawer";
import {
  changeReadStrategy,
  editableEngineOptions,
  parseEngineOptions,
  type EditorConfiguration,
} from "./configuration";
import { StrategySection } from "./strategy-section";
import { RunConfigurationSection } from "./run-configuration-section";
import { DdlConfiguration } from "./ddl-configuration";
import { StartupFields, startupConfigurationError } from "./startup-fields";
import type { EditorScheduleSection } from "./schedule-section";

export type AdvancedTab = "parameters" | "ddl" | "engine";
export type AdvancedParameterTab = "read" | "write" | "runtime";

export function AdvancedDrawer({
  value,
  solution,
  strategyDisabled,
  initialTab = "parameters",
  initialParameterTab = "read",
  schedule,
  onApply,
  onClose,
  onDirtyChange,
}: {
  value: EditorConfiguration;
  solution?: DataSyncSolution;
  strategyDisabled: boolean;
  initialTab?: AdvancedTab;
  initialParameterTab?: AdvancedParameterTab;
  schedule?: ComponentProps<typeof EditorScheduleSection>;
  onApply: (value: EditorConfiguration, scheduleForm?: ScheduleTimeRule) => void;
  onClose: () => void;
  onDirtyChange: (dirty: boolean) => void;
}) {
  const [configuration, setConfiguration] = useState(() => structuredClone(value));
  const [scheduleForm, setScheduleForm] = useState(schedule?.scheduleForm);
  const [tab, setTab] = useState<AdvancedTab>(initialTab);
  const [parameterTab, setParameterTab] = useState<AdvancedParameterTab>(initialParameterTab);
  const [engineText, setEngineText] = useState(() =>
    JSON.stringify(editableEngineOptions(value.form.pipelineOptions), null, 2),
  );
  const [error, setError] = useState("");
  const { draft, form, mappings } = configuration;
  const replication = draft.productType === "DATABASE_REPLICATION";
  const continuous = replication && draft.steps.includes("INCREMENTAL");
  const combination = findStepCombination(solution, draft.steps);
  const update = (next: EditorConfiguration) => {
    if (next.form.pipelineOptions?.["schema.change.behavior"] !== undefined)
      next = {
        ...next,
        form: {
          ...next.form,
          pipelineOptions: {
            ...next.form.pipelineOptions,
            "schema.change.behavior": next.form.sink.schemaChangesEnabled ? "evolve" : "exception",
          },
        },
      };
    setConfiguration(next);
    setEngineText(JSON.stringify(editableEngineOptions(next.form.pipelineOptions), null, 2));
    setError("");
    onDirtyChange(
      parameterFingerprint(next) !== parameterFingerprint(value) ||
        parameterFingerprint(scheduleForm) !== parameterFingerprint(schedule?.scheduleForm),
    );
  };
  const parseEngine = () => {
    try {
      return parseEngineOptions(engineText, form.sink.schemaChangesEnabled);
    } catch (failure) {
      setError(
        failure instanceof SyntaxError
          ? "请输入有效的 JSON 引擎参数"
          : failure instanceof Error
            ? failure.message
            : "引擎参数无效",
      );
      return undefined;
    }
  };
  const apply = () => {
    const pipelineOptions = parseEngine();
    if (!pipelineOptions) {
      setTab("engine");
      return;
    }
    const startupError = startupConfigurationError(form.startup);
    if (startupError) {
      setTab("parameters");
      setParameterTab("read");
      setError(startupError);
      return;
    }
    onApply({ ...configuration, form: { ...form, pipelineOptions } }, scheduleForm);
  };
  return (
    <EditorDrawer
      title="高级配置"
      width={800}
      onClose={onClose}
      footer={
        <>
          <Button size="small" variant="primary" onClick={apply}>
            确认
          </Button>
          <Button size="small" onClick={onClose}>
            取消
          </Button>
        </>
      }
    >
      <Tabs
        value={tab}
        onValueChange={(next) => {
          if (tab === "engine") {
            const parsed = parseEngine();
            if (!parsed) return;
            update({ ...configuration, form: { ...form, pipelineOptions: parsed } });
          }
          setTab(next as AdvancedTab);
        }}
      >
        <TabsList>
          <TabsTab value="parameters">高级参数</TabsTab>
          <TabsTab value="ddl">DDL 能力配置</TabsTab>
          <TabsTab value="engine">引擎参数配置</TabsTab>
        </TabsList>
        {error ? <Alert className="mt-4">{error}</Alert> : null}
        <TabsPanel value="parameters" className="pt-5">
          <Tabs
            value={parameterTab}
            onValueChange={(next) => setParameterTab(next as AdvancedParameterTab)}
          >
            <TabsList>
              <TabsTab value="read">读端配置</TabsTab>
              <TabsTab value="write">写端配置</TabsTab>
              <TabsTab value="runtime">运行时配置</TabsTab>
            </TabsList>
            <TabsPanel value="read" className="space-y-5 pt-4">
              <StrategySection
                draft={draft}
                solution={solution}
                disabled={
                  strategyDisabled || mappings.some((mapping) => mapping.writeMode === "RETRACT")
                }
                startupMode={form.startup?.mode}
                onChange={(steps) => update(changeReadStrategy(configuration, steps, solution))}
              />
              {continuous ? (
                <StartupFields
                  value={form.startup}
                  initialLoad={draft.steps.includes("FULL")}
                  onChange={(startup) => update({ ...configuration, form: { ...form, startup } })}
                />
              ) : null}
            </TabsPanel>
            <TabsPanel value="write" className="pt-5">
              {replication ? (
                <p className="text-sm text-[var(--yak-components-muted-text)]">
                  写入方式和主键按表配置，请在目标表映射中设置。
                </p>
              ) : (
                <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
                  <FieldLabel>写入方式</FieldLabel>
                  <DataSyncSearchableSelect
                    ariaLabel="写入方式"
                    placeholder="请选择写入方式"
                    value={form.sink.writeMode}
                    options={(combination?.writeModes || []).map((mode) => ({
                      value: mode,
                      label: mode === "UPSERT" ? "按主键更新" : "追加写入",
                    }))}
                    disabled={(combination?.writeModes.length || 0) < 2}
                    onValueChange={(writeMode) =>
                      update({
                        ...configuration,
                        form: {
                          ...form,
                          sink: { ...form.sink, writeMode: writeMode as DataSyncWriteMode },
                        },
                      })
                    }
                  />
                </Field>
              )}
            </TabsPanel>
            <TabsPanel value="runtime" className="space-y-5 pt-4">
              <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel>并行度</FieldLabel>
                <Input
                  aria-label="并行度"
                  size="small"
                  variant="outlined"
                  inputMode="numeric"
                  value={form.pipelineOptions?.parallelism ?? ""}
                  placeholder="默认"
                  onChange={(event) =>
                    update({
                      ...configuration,
                      form: {
                        ...form,
                        pipelineOptions: {
                          ...editableEngineOptions(form.pipelineOptions),
                          parallelism: event.target.value || undefined,
                        },
                      },
                    })
                  }
                />
              </Field>
              <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel>时区</FieldLabel>
                <Input
                  aria-label="运行时区"
                  size="small"
                  variant="outlined"
                  value={form.pipelineOptions?.["local-time-zone"] || ""}
                  placeholder="默认"
                  onChange={(event) =>
                    update({
                      ...configuration,
                      form: {
                        ...form,
                        pipelineOptions: {
                          ...editableEngineOptions(form.pipelineOptions),
                          "local-time-zone": event.target.value || undefined,
                        },
                      },
                    })
                  }
                />
              </Field>
              <RunConfigurationSection
                replication={replication}
                continuous={continuous}
                debug={form.debug}
                onDebugChange={(debug) => update({ ...configuration, form: { ...form, debug } })}
                schedule={
                  schedule && scheduleForm
                    ? {
                        ...schedule,
                        scheduleForm,
                        applicable: Boolean(combination?.supportsScheduling),
                        onChange: (next) => {
                          setScheduleForm(next);
                          onDirtyChange(
                            parameterFingerprint(configuration) !== parameterFingerprint(value) ||
                              parameterFingerprint(next) !==
                                parameterFingerprint(schedule.scheduleForm),
                          );
                        },
                      }
                    : undefined
                }
              />
            </TabsPanel>
          </Tabs>
        </TabsPanel>
        <TabsPanel value="ddl" className="pt-5">
          <DdlConfiguration value={configuration} solution={solution} onChange={update} />
        </TabsPanel>
        <TabsPanel value="engine" className="pt-5">
          <Field>
            <FieldLabel>引擎参数 JSON</FieldLabel>
            <Textarea
              aria-label="引擎参数 JSON"
              variant="outlined"
              className="min-h-[420px] font-mono text-xs"
              spellCheck={false}
              value={engineText}
              onValueChange={(content) => {
                setEngineText(content);
                setError("");
                onDirtyChange(true);
              }}
            />
          </Field>
        </TabsPanel>
      </Tabs>
    </EditorDrawer>
  );
}
