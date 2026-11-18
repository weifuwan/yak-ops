import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import * as monaco from "monaco-editor/editor";
import "monaco-editor/features/register.all";
import "monaco-editor/languages/definitions/shell/register";
import EditorWorker from "monaco-editor/editor/editor.worker?worker";

import {
  Button,
  Input,
  PasswordInput,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Switch,
  TreeSelect,
  type TreeSelectOption,
  toast,
} from "@yak-ops/yak-ui";
import { FileCode2, FolderOpen, Plus, RefreshCw, Search, Trash2 } from "lucide-react";

import {
  getWorkflowDefinition,
  type WorkflowTaskRecord,
} from "@/service/workflow/definition";
import {
  listWorkflowResources,
  type WorkflowResourceNode,
} from "@/service/resources";
import {
  startWorkflowInstance,
  stopWorkflowInstance,
} from "@/service/workflow/execution";

import { ResourceTreeIcon } from "../../resources/components/resource-file-icon";
import { NodeEditorFrame } from "./node-editor-frame";
import {
  buildShellTaskParams,
  parseShellTaskForm,
  type ShellLocalParam,
  type ShellTaskForm,
} from "./shell-task-form";
import {
  getTaskGroupValidationError,
  normalizeTaskCommonSettings,
  parseTaskCommonSettings,
  TaskCommonSettingsSection,
  useTaskGroups,
  type TaskCommonSettings,
} from "./task-common-settings-section";

type ShellEditorView = "form" | "json";
type ShellConfigTab = "parameters" | "resources";

const shellParamTypeOptions = [
  { value: "VARCHAR", label: "VARCHAR" },
  { value: "INTEGER", label: "INTEGER" },
  { value: "LONG", label: "LONG" },
  { value: "FLOAT", label: "FLOAT" },
  { value: "DOUBLE", label: "DOUBLE" },
  { value: "DATE", label: "DATE" },
  { value: "TIME", label: "TIME" },
  { value: "TIMESTAMP", label: "TIMESTAMP" },
  { value: "BOOLEAN", label: "BOOLEAN" },
  { value: "LIST", label: "LIST" },
  { value: "FILE", label: "FILE" },
] as const;

const workerGlobal = self as typeof self & {
  MonacoEnvironment?: {
    getWorker: (workerId: string, label: string) => Worker;
  };
};

workerGlobal.MonacoEnvironment = {
  getWorker: () => new EditorWorker(),
};

function getResourceNodeName(node: WorkflowResourceNode) {
  if (node.name) return node.name;

  const pathParts = (node.fullName || node.currentDir || "")
    .split(/[\\/]/)
    .filter(Boolean);

  return pathParts[pathParts.length - 1] || "未命名资源";
}

function isResourceDirectory(node: WorkflowResourceNode) {
  return Boolean(
    node.isDirctory ??
      node.dirctory ??
      node.isDirectory ??
      (node.children && node.children.length > 0),
  );
}

function buildResourceTreeOptions(
  nodes: WorkflowResourceNode[],
  parentPath = "",
): TreeSelectOption[] {
  return nodes.map((node, index) => {
    const label = getResourceNodeName(node);
    const value =
      node.fullName ||
      node.currentDir ||
      [parentPath, label, index].filter(Boolean).join("/");
    const directory = isResourceDirectory(node);
    const children = node.children ?? [];

    return {
      label,
      value,
      selectable: !directory,
      searchText: [node.fullName, node.currentDir].filter(Boolean).join(" "),
      icon: <ResourceTreeIcon name={label} directory={directory} size={16} />,
      children: buildResourceTreeOptions(children, value),
    };
  });
}

function ShellScriptEditor({
  value,
  readOnly,
  onChange,
}: {
  value: string;
  readOnly: boolean;
  onChange: (value: string) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor | null>(null);
  const initialValueRef = useRef(value);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  useEffect(() => {
    if (!containerRef.current) return;

    const editor = monaco.editor.create(containerRef.current, {
      value: initialValueRef.current,
      language: "shell",
      theme: "vs",
      readOnly,
      automaticLayout: true,
      minimap: { enabled: false },
      fontSize: 13,
      lineHeight: 22,
      fontFamily: "Consolas, 'Cascadia Code', 'Courier New', monospace",
      tabSize: 2,
      insertSpaces: true,
      wordWrap: "on",
      scrollBeyondLastLine: false,
      renderLineHighlight: "line",
      cursorBlinking: "smooth",
      smoothScrolling: true,
      contextmenu: true,
      padding: { top: 12, bottom: 12 },
      ariaLabel: "Shell 脚本编辑器",
      overviewRulerBorder: false,
      scrollbar: {
        verticalScrollbarSize: 9,
        horizontalScrollbarSize: 9,
        useShadows: false,
      },
    });

    editorRef.current = editor;
    const subscription = editor.onDidChangeModelContent(() => {
      onChangeRef.current(editor.getValue());
    });

    return () => {
      subscription.dispose();
      editor.dispose();
      editorRef.current = null;
    };
  }, []);

  useEffect(() => {
    const editor = editorRef.current;
    if (editor && editor.getValue() !== value) {
      editor.setValue(value);
    }
  }, [value]);

  useEffect(() => {
    editorRef.current?.updateOptions({ readOnly });
  }, [readOnly]);

  return <div ref={containerRef} className="h-full min-h-[240px] w-full" />;
}

function LocalParamValueInput({
  item,
  index,
  disabled,
  onChange,
}: {
  item: ShellLocalParam;
  index: number;
  disabled: boolean;
  onChange: (patch: Partial<ShellLocalParam>) => void;
}) {
  if (item.type === "BOOLEAN") {
    return (
      <Select<string>
        size="small"
        items={{ true: "true", false: "false" }}
        value={item.value === "true" || item.value === "false" ? item.value : null}
        disabled={disabled}
        onValueChange={(value) => {
          if (value === "true" || value === "false") onChange({ value });
        }}
      >
        <SelectTrigger variant="outlined" className="w-full" aria-label={"布尔参数值 " + (index + 1)}>
          <SelectValue placeholder="请选择 true / false" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="true">
            <SelectItemText>true</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
          <SelectItem value="false">
            <SelectItemText>false</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        </SelectContent>
      </Select>
    );
  }

  const inputType =
    ["INTEGER", "LONG", "FLOAT", "DOUBLE"].includes(item.type)
      ? "number"
      : item.type === "DATE"
        ? "date"
        : item.type === "TIME"
          ? "time"
          : "text";
  const placeholder =
    item.type === "INTEGER" ? "请输入整数"
      : item.type === "LONG" ? "请输入长整数"
      : item.type === "FLOAT" || item.type === "DOUBLE" ? "请输入数字"
      : item.type === "DATE" ? "YYYY-MM-DD"
      : item.type === "TIME" ? "HH:mm:ss"
      : item.type === "TIMESTAMP" ? "YYYY-MM-DD HH:mm:ss"
      : item.type === "LIST" ? 'JSON 数组，例如 ["a","b"]'
      : item.type === "FILE" ? "请输入文件路径"
      : "参数值";

  if (item.sensitive) {
    return (
      <PasswordInput
        size="small"
        variant="outlined"
        value={item.value}
        disabled={disabled}
        onChange={(event) => onChange({ value: event.target.value })}
        placeholder={placeholder}
        aria-label={"敏感参数值 " + (index + 1)}
      />
    );
  }

  return (
    <Input
      size="small"
      variant="outlined"
      type={inputType}
      step={["INTEGER", "LONG", "TIME"].includes(item.type) ? 1 : "any"}
      value={item.value}
      disabled={disabled}
      onChange={(event) => onChange({ value: event.target.value })}
      placeholder={placeholder}
      aria-label={"参数值 " + (index + 1)}
    />
  );
}

export function ShellTaskEditor({
  workflowCode,
  task,
  taskName,
  isDraft,
  readOnly,
  canRun,
  onApplyTask,
  onDirtyChange,
  onBackToWorkflow,
}: {
  workflowCode: number;
  task: WorkflowTaskRecord;
  taskName: string;
  isDraft: boolean;
  readOnly: boolean;
  canRun: boolean;
  onApplyTask: (task: WorkflowTaskRecord) => boolean;
  onDirtyChange: (dirty: boolean) => void;
  onBackToWorkflow: () => void;
}) {
  const queryClient = useQueryClient();
  const { projectCode, taskGroups, taskGroupsQuery } = useTaskGroups();
  const initialForm = useMemo(
    () => parseShellTaskForm(task?.taskParams),
    [task?.taskParams],
  );
  const initialCommonSettings = useMemo(() => parseTaskCommonSettings(task), [task]);
  const [form, setForm] = useState<ShellTaskForm>(initialForm);
  const [baselineForm, setBaselineForm] = useState<ShellTaskForm>(initialForm);
  const [commonSettings, setCommonSettings] = useState<TaskCommonSettings>(initialCommonSettings);
  const [baselineCommonSettings, setBaselineCommonSettings] = useState<TaskCommonSettings>(initialCommonSettings);
  const [currentTask, setCurrentTask] = useState(task);
  const [activeView, setActiveView] = useState<ShellEditorView>("form");
  const [activeConfigTab, setActiveConfigTab] = useState<ShellConfigTab>("parameters");
  const [saving, setSaving] = useState(false);
  const [running, setRunning] = useState(false);
  const [stopping, setStopping] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [lastWorkflowInstanceId, setLastWorkflowInstanceId] = useState<number | null>(null);

  useEffect(() => {
    setForm(initialForm);
    setBaselineForm(initialForm);
    setCommonSettings(initialCommonSettings);
    setBaselineCommonSettings(initialCommonSettings);
    setCurrentTask(task);
  }, [initialForm, initialCommonSettings, task]);

  const resourceQuery = useQuery({
    queryKey: ["data-development", "shell-task-resources"],
    queryFn: ({ signal }) => listWorkflowResources(signal),
    staleTime: 60_000,
  });

  const resourceTree = useMemo(
    () => buildResourceTreeOptions(resourceQuery.data ?? []),
    [resourceQuery.data],
  );

  const updateField = <K extends keyof ShellTaskForm>(
    field: K,
    value: ShellTaskForm[K],
  ) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const updateLocalParam = (
    index: number,
    patch: Partial<ShellLocalParam>,
  ) => {
    setForm((current) => ({
      ...current,
      localParams: current.localParams.map((item, itemIndex) =>
        itemIndex === index ? { ...item, ...patch } : item,
      ),
    }));
  };

  const jsonText = useMemo(() => {
    let commonFields: Record<string, unknown>;
    try {
      commonFields = normalizeTaskCommonSettings(commonSettings);
    } catch {
      commonFields = commonSettings;
    }

    return JSON.stringify({
      taskType: "SHELL",
      taskParams: buildShellTaskParams(form, currentTask.taskParams),
      ...commonFields,
    }, null, 2);
  }, [commonSettings, currentTask.taskParams, form]);

  const isDirty =
    JSON.stringify(form) !== JSON.stringify(baselineForm) ||
    JSON.stringify(commonSettings) !== JSON.stringify(baselineCommonSettings);

  useEffect(() => {
    onDirtyChange(isDirty);
  }, [isDirty, onDirtyChange]);

  useEffect(() => () => onDirtyChange(false), [onDirtyChange]);

  const handleSave = async (): Promise<boolean> => {
    if (!currentTask || readOnly) return false;

    if (!form.rawScript.trim()) {
      toast.warning("请先编写 Shell 脚本");
      return false;
    }

    const paramNames = form.localParams
      .map((item) => item.prop.trim())
      .filter(Boolean);
    if (new Set(paramNames).size !== paramNames.length) {
      toast.warning("自定义参数名称不能重复");
      return false;
    }

    let commonPatch;
    try {
      commonPatch = normalizeTaskCommonSettings(commonSettings);
    } catch (error) {
      toast.warning(error instanceof Error ? error.message : "请检查节点通用配置");
      return false;
    }

    const taskGroupError = getTaskGroupValidationError(commonSettings, {
      projectCode,
      taskGroups,
      isPending: taskGroupsQuery.isPending,
      isError: taskGroupsQuery.isError,
    });
    if (taskGroupError) {
      toast.warning(taskGroupError);
      return false;
    }

    setSaving(true);
    try {
      const taskParams = buildShellTaskParams(form, currentTask.taskParams);
      const nextTask = { ...currentTask, taskParams, ...commonPatch };
      if (!onApplyTask(nextTask)) return false;
      setCurrentTask(nextTask);
      setBaselineForm(form);
      setBaselineCommonSettings(commonSettings);
      toast.success("Shell 节点配置已应用，请保存工作流");
      return true;
    } catch {
      toast.error("保存 Shell 节点配置失败，请稍后重试");
      return false;
    } finally {
      setSaving(false);
    }
  };

  const handleRun = async () => {
    if (!currentTask) return;
    if (isDraft || !canRun) {
      toast.warning("请先保存并发布工作流，再运行节点");
      return;
    }
    if (isDirty) {
      toast.warning("当前节点有未保存修改，请先保存工作流");
      return;
    }
    if (!form.rawScript.trim()) {
      toast.warning("请先编写 Shell 脚本");
      return;
    }

    setRunning(true);
    try {
      const result = await startWorkflowInstance(String(workflowCode), {
        startNodeList: currentTask.code,
        taskDependType: "TASK_ONLY",
      });
      setLastWorkflowInstanceId(result.workflowInstanceId);
      toast.success("单节点运行已提交", {
        description: "工作流实例 ID：" + result.workflowInstanceId,
      });
    } catch (error) {
      toast.error("Shell 节点运行失败", {
        description: error instanceof Error ? error.message : "请稍后重试。",
      });
    } finally {
      setRunning(false);
    }
  };

  const handleStop = async () => {
    if (!lastWorkflowInstanceId) return;

    setStopping(true);
    try {
      await stopWorkflowInstance(lastWorkflowInstanceId);
      toast.success("节点停止请求已提交");
      setLastWorkflowInstanceId(null);
    } catch (error) {
      toast.error("停止节点失败", {
        description: error instanceof Error ? error.message : "请稍后重试。",
      });
    } finally {
      setStopping(false);
    }
  };

  const handleRefresh = async () => {
    if (isDirty) {
      toast.warning("当前配置有未保存修改，请先保存后再刷新");
      return;
    }
    if (isDraft) {
      toast.info("新建节点尚未保存到后端，暂无可刷新的服务端配置");
      return;
    }

    setRefreshing(true);
    try {
      const refreshTasks: Array<Promise<unknown>> = [resourceQuery.refetch()];
      if (currentTask) {
        refreshTasks.push(
          getWorkflowDefinition(workflowCode).then((dag) => {
            const latestTask = dag.taskDefinitionList.find(
              (item) => item.code === currentTask.code,
            );
            if (!latestTask) throw new Error("节点不存在或已被删除");

            const nextForm = parseShellTaskForm(latestTask.taskParams);
            const nextCommonSettings = parseTaskCommonSettings(latestTask);
            if (!onApplyTask(latestTask)) return;

            setCurrentTask(latestTask);
            setForm(nextForm);
            setBaselineForm(nextForm);
            setCommonSettings(nextCommonSettings);
            setBaselineCommonSettings(nextCommonSettings);
          }),
        );
      }

      await Promise.all(refreshTasks);
      await queryClient.invalidateQueries({
        queryKey: ["data-development", "workflow-definition"],
      });
      toast.success("Shell 节点配置已刷新");
    } catch (error) {
      toast.error("刷新失败", {
        description: error instanceof Error ? error.message : "请稍后重试。",
      });
    } finally {
      setRefreshing(false);
    }
  };

  const scriptLineCount = Math.max(1, form.rawScript.split("\n").length);
  const scriptCharacterCount = form.rawScript.length;

  return (
    <NodeEditorFrame
      task={currentTask}
      taskName={taskName}
      taskType="SHELL"
      workflowCode={workflowCode}
      activeView={activeView}
      onActiveViewChange={setActiveView}
      isDirty={isDirty}
      saving={saving}
      running={running}
      stopping={stopping}
      refreshing={refreshing || resourceQuery.isFetching}
      lastWorkflowInstanceId={lastWorkflowInstanceId}
      canRun={canRun}
      readOnly={readOnly}
      onRun={() => void handleRun()}
      onStop={() => void handleStop()}
      onSave={() => void handleSave()}
      onRefresh={() => void handleRefresh()}
      onBack={onBackToWorkflow}
      runConfig={
        <TaskCommonSettingsSection
          id="shell-run-config"
          settings={commonSettings}
          onChange={setCommonSettings}
          disabled={readOnly}
          compact
        />
      }
    >
      {activeView === "json" ? (
        <div className="min-h-0 flex-1 overflow-auto bg-[var(--yak-components-panel-bg)] p-4">
          <pre className="w-full overflow-auto rounded-sm border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4 font-mono text-xs leading-5 text-[var(--yak-components-panel-text)]">
            {jsonText}
          </pre>
        </div>
      ) : (
        <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-white">
          <div className="flex h-9 shrink-0 items-center justify-between border-b border-[#e8ebef] bg-[#fbfcfe] px-3">
            <div className="flex min-w-0 items-center gap-2">
              <FileCode2 size={14} className="shrink-0 text-[#5b6b82]" />
              <span className="text-xs font-semibold text-[#303846]">Shell 脚本</span>
              <span className="rounded bg-[#eef2f7] px-1.5 py-0.5 text-[10px] text-[#687385]">Shell</span>
            </div>
            <div className="flex items-center gap-2 text-[11px] text-[#8b93a1]">
              <span className="hidden sm:inline-flex items-center gap-1">
                <Search size={12} />
                Ctrl + F 查找
              </span>
              <Button
                type="button"
                size="sm"
                variant="ghost"
                isDisabled={readOnly || !form.rawScript}
                onClick={() => updateField("rawScript", "")}
                className="!h-6 !px-2 text-[11px]"
              >
                清空脚本
              </Button>
            </div>
          </div>

          <div className="flex min-h-[250px] flex-1 flex-col overflow-hidden">
            <div className="min-h-0 flex-1">
              <ShellScriptEditor
                value={form.rawScript}
                readOnly={readOnly}
                onChange={(value) => updateField("rawScript", value)}
              />
            </div>
            <div className="flex h-6 shrink-0 items-center justify-between border-t border-[#f0f1f3] bg-white px-3 text-[10px] text-[#98a1ad]">
              <span>行数：{scriptLineCount}　字符：{scriptCharacterCount}</span>
              <span>Shell · UTF-8 · LF</span>
            </div>
          </div>

          <section className="flex h-[286px] max-h-[38vh] min-h-[220px] shrink-0 flex-col border-t border-[#e5e7eb] bg-white">
            <nav className="flex h-9 shrink-0 items-center gap-1 border-b border-[#edf0f3] bg-[#fbfcfe] px-3">
              <button
                type="button"
                aria-pressed={activeConfigTab === "parameters"}
                onClick={() => setActiveConfigTab("parameters")}
                className={[
                  "flex h-full cursor-pointer items-center gap-1.5 border-b-2 px-2 text-xs transition-colors",
                  activeConfigTab === "parameters"
                    ? "border-[#315efb] font-semibold text-[#315efb]"
                    : "border-transparent text-[#687385] hover:text-[#303846]",
                ].join(" ")}
              >
                自定义参数
                <span className="rounded-full bg-[#edf1f7] px-1.5 py-0.5 text-[10px] text-[#687385]">
                  {form.localParams.length}
                </span>
              </button>
              <button
                type="button"
                aria-pressed={activeConfigTab === "resources"}
                onClick={() => setActiveConfigTab("resources")}
                className={[
                  "flex h-full cursor-pointer items-center gap-1.5 border-b-2 px-2 text-xs transition-colors",
                  activeConfigTab === "resources"
                    ? "border-[#315efb] font-semibold text-[#315efb]"
                    : "border-transparent text-[#687385] hover:text-[#303846]",
                ].join(" ")}
              >
                <FolderOpen size={13} />
                依赖资源
                <span className="rounded-full bg-[#edf1f7] px-1.5 py-0.5 text-[10px] text-[#687385]">
                  {form.resourceList.length}
                </span>
              </button>
              <span className="ml-auto hidden text-[10px] text-[#98a1ad] lg:inline">
                参数配置沿用 DolphinScheduler localParams 结构
              </span>
            </nav>

            <div className="min-h-0 flex-1 overflow-auto p-3">
              {activeConfigTab === "parameters" ? (
                <div className="space-y-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="text-[11px] leading-5 text-[#687385]">
                      输入参数在脚本中使用{" "}
                      <code className="rounded bg-[#f1f4f8] px-1.5 py-0.5 font-mono text-[#31506f]">
                        {"$" + "{paramName}"}
                      </code>
                      <span className="mx-1">；</span>
                      OUT 参数可使用{" "}
                      <code className="rounded bg-[#f1f4f8] px-1.5 py-0.5 font-mono text-[#31506f]">
                        {"$" + "{setValue(name=value)}"}
                      </code>
                      输出。
                    </div>
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      isDisabled={readOnly}
                      onClick={() =>
                        setForm((current) => ({
                          ...current,
                          localParams: [
                            ...current.localParams,
                            {
                              prop: "",
                              direct: "IN",
                              type: "VARCHAR",
                              value: "",
                              sensitive: false,
                            },
                          ],
                        }))
                      }
                    >
                      <Plus size={13} />
                      添加参数
                    </Button>
                  </div>

                  {form.localParams.length === 0 ? (
                    <div className="flex min-h-[112px] flex-col items-center justify-center gap-1 rounded-md border border-dashed border-[#dfe4eb] bg-[#fcfdff] text-center">
                      <span className="text-xs text-[#687385]">暂无自定义参数</span>
                      <span className="text-[11px] text-[#98a1ad]">
                        添加 IN 参数供当前脚本引用，或添加 OUT 参数向下游节点传递结果
                      </span>
                    </div>
                  ) : (
                    <div className="overflow-x-auto">
                      <div className="min-w-[760px]">
                        <div className="grid grid-cols-[minmax(0,1.2fr)_112px_140px_minmax(0,1.5fr)_64px_40px] items-center gap-2 border-b border-[#edf0f3] bg-[#f8f9fb] px-2 py-2 text-[11px] text-[#7c8796]">
                          <span>参数名称</span>
                          <span>方向</span>
                          <span>类型</span>
                          <span>参数值</span>
                          <span>敏感</span>
                          <span />
                        </div>
                        <div className="space-y-2 pt-2">
                          {form.localParams.map((item, index) => (
                            <div
                              key={index}
                              className="grid grid-cols-[minmax(0,1.2fr)_112px_140px_minmax(0,1.5fr)_64px_40px] items-center gap-2"
                            >
                              <Input
                                size="small"
                                variant="outlined"
                                value={item.prop}
                                disabled={readOnly}
                                onChange={(event) =>
                                  updateLocalParam(index, { prop: event.target.value })
                                }
                                placeholder="参数名称"
                                aria-label={"参数名称 " + (index + 1)}
                              />
                              <Select<string>
                                size="small"
                                items={{ IN: "输入 IN", OUT: "输出 OUT" }}
                                value={item.direct}
                                disabled={readOnly}
                                onValueChange={(value) => {
                                  if (value === "IN" || value === "OUT") {
                                    updateLocalParam(index, { direct: value });
                                  }
                                }}
                              >
                                <SelectTrigger
                                  variant="outlined"
                                  className="w-full"
                                  aria-label={"参数方向 " + (index + 1)}
                                >
                                  <SelectValue placeholder="选择方向" />
                                </SelectTrigger>
                                <SelectContent>
                                  <SelectItem value="IN">
                                    <SelectItemText>输入 IN</SelectItemText>
                                    <SelectItemIndicator />
                                  </SelectItem>
                                  <SelectItem value="OUT">
                                    <SelectItemText>输出 OUT</SelectItemText>
                                    <SelectItemIndicator />
                                  </SelectItem>
                                </SelectContent>
                              </Select>
                              <Select<string>
                                size="small"
                                items={Object.fromEntries(
                                  shellParamTypeOptions.map((option) => [
                                    option.value,
                                    option.label,
                                  ]),
                                )}
                                value={item.type}
                                disabled={readOnly}
                                onValueChange={(value) => {
                                  if (value) updateLocalParam(index, { type: value });
                                }}
                              >
                                <SelectTrigger
                                  variant="outlined"
                                  className="w-full"
                                  aria-label={"参数类型 " + (index + 1)}
                                >
                                  <SelectValue placeholder="选择类型" />
                                </SelectTrigger>
                                <SelectContent>
                                  {shellParamTypeOptions.map((option) => (
                                    <SelectItem key={option.value} value={option.value}>
                                      <SelectItemText>{option.label}</SelectItemText>
                                      <SelectItemIndicator />
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                              <LocalParamValueInput
                                item={item}
                                index={index}
                                disabled={readOnly}
                                onChange={(patch) => updateLocalParam(index, patch)}
                              />
                              <div className="flex items-center justify-center">
                                <Switch
                                  size="small"
                                  checked={item.sensitive}
                                  disabled={readOnly}
                                  aria-label={"敏感参数 " + (item.prop || index + 1)}
                                  onCheckedChange={(checked) =>
                                    updateLocalParam(index, { sensitive: checked })
                                  }
                                />
                              </div>
                              <Button
                                type="button"
                                size="sm"
                                variant="ghost"
                                isIconOnly
                                isDisabled={readOnly}
                                aria-label="移除自定义参数"
                                onClick={() =>
                                  updateField(
                                    "localParams",
                                    form.localParams.filter(
                                      (_param, itemIndex) => itemIndex !== index,
                                    ),
                                  )
                                }
                                className="text-[var(--yak-components-muted-text)] hover:text-[var(--yak-components-field-error)]"
                              >
                                <Trash2 size={14} />
                              </Button>
                            </div>
                          ))}
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-[11px] text-[#687385]">
                      选择资源管理中的文件作为当前 Shell 任务的依赖资源。
                    </p>
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      isDisabled={readOnly}
                      onClick={() => updateField("resourceList", [...form.resourceList, ""])}
                    >
                      <Plus size={13} />
                      添加依赖资源
                    </Button>
                  </div>

                  {resourceQuery.isError ? (
                    <div className="flex items-center justify-between rounded-md border border-[#f2d6d6] bg-[#fffafa] px-3 py-2">
                      <span className="text-xs text-[#b54747]">资源列表加载失败</span>
                      <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        onClick={() => void resourceQuery.refetch()}
                      >
                        <RefreshCw size={12} />
                        重试
                      </Button>
                    </div>
                  ) : null}

                  {form.resourceList.length > 0 ? (
                    <div className="space-y-2">
                      {form.resourceList.map((resource, index) => (
                        <div key={index} className="flex items-center gap-2">
                          <TreeSelect
                            items={resourceTree}
                            value={resource || null}
                            onValueChange={(value) =>
                              updateField(
                                "resourceList",
                                form.resourceList.map((item, itemIndex) =>
                                  itemIndex === index ? value ?? "" : item,
                                ),
                              )
                            }
                            size="small"
                            variant="outlined"
                            placeholder="请选择依赖资源"
                            searchPlaceholder="搜索文件名称或路径"
                            emptyText={
                              resourceQuery.isPending
                                ? "正在加载资源..."
                                : "暂无匹配的资源"
                            }
                            ariaLabel={"依赖资源 " + (index + 1)}
                            clearable
                            disabled={readOnly}
                            className="min-w-0 flex-1"
                          />
                          <Button
                            type="button"
                            size="sm"
                            variant="ghost"
                            isIconOnly
                            isDisabled={readOnly}
                            aria-label="移除依赖资源"
                            onClick={() =>
                              updateField(
                                "resourceList",
                                form.resourceList.filter(
                                  (_item, itemIndex) => itemIndex !== index,
                                ),
                              )
                            }
                            className="shrink-0 text-[var(--yak-components-muted-text)] hover:text-[var(--yak-components-field-error)]"
                          >
                            <Trash2 size={14} />
                          </Button>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="flex min-h-[112px] flex-col items-center justify-center gap-1 rounded-md border border-dashed border-[#dfe4eb] bg-[#fcfdff] text-center">
                      <FolderOpen size={18} className="text-[#a4adba]" />
                      <span className="text-xs text-[#687385]">暂无依赖资源</span>
                      <span className="text-[11px] text-[#98a1ad]">
                        可以添加 Shell、Python、配置文件等资源
                      </span>
                    </div>
                  )}
                </div>
              )}
            </div>
          </section>
        </div>
      )}
    </NodeEditorFrame>
  );
}
