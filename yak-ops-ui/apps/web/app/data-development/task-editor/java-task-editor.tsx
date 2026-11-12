import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import {
  Autocomplete,
  AutocompleteContent,
  AutocompleteInput,
  AutocompleteItem,
  Button,
  Checkbox,
  CollapseSection,
  Field,
  FieldLabel,
  Input,
  PasswordInput,
  Segmented,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Switch,
  Textarea,
  TreeSelect,
  type TreeSelectOption,
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
  toast,
} from "@yak-ops/yak-ui";
import { Info, Plus, Trash2 } from "lucide-react";

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
  getTaskGroupValidationError, normalizeTaskCommonSettings, parseTaskCommonSettings,
  TaskCommonSettingsSection, useTaskGroups, type TaskCommonSettings,
} from "./task-common-settings-section";

type JavaRunType = "FAT_JAR" | "NORMAL_JAR";

type JavaLocalParam = {
  prop: string;
  direct: "IN" | "OUT";
  type: string;
  value: string;
  sensitive: boolean;
};

type JavaTaskForm = {
  runType: JavaRunType;
  mainJar: string;
  mainClass: string;
  mainArgs: string;
  jvmArgs: string;
  isModulePath: boolean;
  resourceList: string[];
  localParams: JavaLocalParam[];
};

type ResourceInfo = {
  resourceName: string;
};

const runTypeOptions = [
  { value: "FAT_JAR", label: "依赖内置 JAR" },
  { value: "NORMAL_JAR", label: "普通 JAR" },
] as const;

const javaLocalParamTypeOptions = [
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

const jvmArgTemplates = [
  {
    key: "light",
    label: "轻量型",
    value: "-Xms256m -Xmx512m -Dfile.encoding=UTF-8",
  },
  {
    key: "general",
    label: "通用型",
    value: "-Xms512m -Xmx1024m -Dfile.encoding=UTF-8",
  },
  {
    key: "large",
    label: "大内存型",
    value: "-Xms1024m -Xmx2048m -Dfile.encoding=UTF-8",
  },
] as const;

function readObject(value: unknown): Record<string, unknown> {
  if (typeof value === "string") {
    try {
      const parsed: unknown = JSON.parse(value);
      return parsed && typeof parsed === "object" && !Array.isArray(parsed)
        ? (parsed as Record<string, unknown>)
        : {};
    } catch {
      return {};
    }
  }

  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function readString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function readResourceName(value: unknown): string {
  if (typeof value === "string") return value;
  if (!value || typeof value !== "object") return "";
  return readString((value as Record<string, unknown>).resourceName);
}

function parseJavaTaskForm(value: unknown): JavaTaskForm {
  const raw = readObject(value);
  const resourceList = Array.isArray(raw.resourceList)
    ? raw.resourceList.map(readResourceName).filter(Boolean)
    : [];
  const localParams = Array.isArray(raw.localParams)
    ? raw.localParams.map((item) => {
        const param = readObject(item);
        return {
          prop: readString(param.prop),
          direct: param.direct === "OUT" ? "OUT" : "IN",
          type: readString(param.type) || "VARCHAR",
          value: readString(param.value),
          sensitive: param.sensitive === true,
        } satisfies JavaLocalParam;
      })
    : [];

  return {
    runType: raw.runType === "NORMAL_JAR" ? "NORMAL_JAR" : "FAT_JAR",
    mainJar: readResourceName(raw.mainJar),
    mainClass: readString(raw.mainClass),
    mainArgs: readString(raw.mainArgs),
    jvmArgs: readString(raw.jvmArgs),
    isModulePath: raw.isModulePath === true || raw.modulePath === true,
    resourceList,
    localParams,
  };
}

function buildJavaTaskParams(form: JavaTaskForm) {
  const mainJar: ResourceInfo | null = form.mainJar.trim()
    ? { resourceName: form.mainJar.trim() }
    : null;

  return {
    runType: form.runType,
    mainJar,
    mainClass: form.mainClass.trim(),
    mainArgs: form.mainArgs,
    jvmArgs: form.jvmArgs,
    isModulePath: form.isModulePath,
    resourceList: form.resourceList
      .map((resourceName) => resourceName.trim())
      .filter(Boolean)
      .map((resourceName) => ({ resourceName })),
    localParams: form.localParams
      .filter((item) => item.prop.trim())
      .map((item) => ({
        ...item,
        prop: item.prop.trim(),
        value: item.value ?? "",
      })),
  };
}

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

function flattenResourceFiles(nodes: WorkflowResourceNode[]): string[] {
  const paths: string[] = [];

  const visit = (node: WorkflowResourceNode) => {
    const children = node.children ?? [];
    if (children.length > 0 || isResourceDirectory(node)) {
      children.forEach(visit);
      return;
    }

    if (node.fullName) paths.push(node.fullName);
  };

  nodes.forEach(visit);
  return Array.from(new Set(paths)).sort((a, b) => a.localeCompare(b));
}

function JavaField({
  label,
  labelAddon,
  children,
  required = false,
  align = "center",
}: {
  label: string;
  labelAddon?: ReactNode;
  children: ReactNode;
  required?: boolean;
  align?: "center" | "start";
}) {
  return (
    <Field
      className={[
        "grid grid-cols-[148px_minmax(0,1fr)] !gap-3 max-md:grid-cols-1",
        align === "center" ? "items-center" : "items-start",
      ].join(" ")}
    >
      <div className="flex min-w-0 items-center gap-1.5">
        <FieldLabel required={required} className={align === "start" ? "pt-1.5" : ""}>
          {label}
        </FieldLabel>
        {labelAddon}
      </div>
      <div className="min-w-0 max-w-[760px]">{children}</div>
    </Field>
  );
}

const inputClassName = "w-full max-w-[760px]";
const textareaClassName = "w-full max-w-[760px]";

export function JavaTaskEditor({
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
    () => parseJavaTaskForm(task?.taskParams),
    [task?.taskParams],
  );
  const initialCommonSettings = useMemo(() => parseTaskCommonSettings(task), [task]);
  const [form, setForm] = useState<JavaTaskForm>(initialForm);
  const [baselineForm, setBaselineForm] = useState<JavaTaskForm>(initialForm);
  const [commonSettings, setCommonSettings] = useState<TaskCommonSettings>(initialCommonSettings);
  const [baselineCommonSettings, setBaselineCommonSettings] = useState<TaskCommonSettings>(initialCommonSettings);
  const [currentTask, setCurrentTask] = useState(task);
  const [activeView, setActiveView] = useState<"form" | "json">("form");
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
    queryKey: ["data-development", "java-task-resources"],
    queryFn: ({ signal }) => listWorkflowResources(signal),
    staleTime: 60_000,
  });

  const resourceTree = useMemo(
    () => buildResourceTreeOptions(resourceQuery.data ?? []),
    [resourceQuery.data],
  );
  const resourceFiles = useMemo(
    () => flattenResourceFiles(resourceQuery.data ?? []),
    [resourceQuery.data],
  );
  const jarFiles = useMemo(
    () => resourceFiles.filter((path) => /\.jar$/i.test(path)),
    [resourceFiles],
  );
  const updateField = <K extends keyof JavaTaskForm,>(
    field: K,
    value: JavaTaskForm[K],
  ) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const updateLocalParam = (
    index: number,
    patch: Partial<JavaLocalParam>,
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
      taskType: "JAVA",
      taskParams: buildJavaTaskParams(form),
      ...commonFields,
    }, null, 2);
  }, [form, commonSettings]);

  const isDirty =
    JSON.stringify(form) !== JSON.stringify(baselineForm) ||
    JSON.stringify(commonSettings) !== JSON.stringify(baselineCommonSettings);

  useEffect(() => {
    onDirtyChange(isDirty);
  }, [isDirty, onDirtyChange]);

  useEffect(() => () => onDirtyChange(false), [onDirtyChange]);

  const handleSave = async (): Promise<boolean> => {
    if (!currentTask || readOnly) return false;

    let commonPatch;
    try {
      commonPatch = normalizeTaskCommonSettings(commonSettings);
    } catch (error) {
      toast.warning(error instanceof Error ? error.message : "请检查节点通用配置");
      return false;
    }
    const taskGroupError = getTaskGroupValidationError(commonSettings, {
      projectCode, taskGroups, isPending: taskGroupsQuery.isPending, isError: taskGroupsQuery.isError,
    });
    if (taskGroupError) {
      toast.warning(taskGroupError);
      return false;
    }

    setSaving(true);
    try {
      const taskParams = buildJavaTaskParams(form);
      const nextTask = { ...currentTask, taskParams, ...commonPatch };
      if (!onApplyTask(nextTask)) return false;
      setCurrentTask(nextTask);
      setBaselineForm(form);
      setBaselineCommonSettings(commonSettings);
      toast.success("Java 节点配置已应用，请保存工作流");
      return true;
    } catch {
      toast.error("保存 Java 节点配置失败，请稍后重试");
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
    if (!form.mainJar.trim()) {
      toast.warning("请先选择主程序 JAR");
      return;
    }

    if (isDirty && !(await handleSave())) return;

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
      toast.error("Java 节点运行失败", {
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
      const refreshTasks: Array<Promise<unknown>> = [
        resourceQuery.refetch(),
      ];
      if (currentTask) {
        refreshTasks.push(
          getWorkflowDefinition(workflowCode).then((dag) => {
            const latestTask = dag.taskDefinitionList.find(
              (item) => item.code === currentTask.code,
            );
            if (!latestTask) throw new Error("节点不存在或已被删除");
            const nextForm = parseJavaTaskForm(latestTask.taskParams);
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
      toast.success(currentTask ? "节点配置已刷新" : "资源列表已刷新");
    } catch (error) {
      toast.error("刷新失败", {
        description: error instanceof Error ? error.message : "请稍后重试。",
      });
    } finally {
      setRefreshing(false);
    }
  };

  return (
    <NodeEditorFrame
      task={currentTask}
      taskName={taskName}
      taskType="JAVA"
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
          id="java-run-config"
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
        <div className="min-h-0 flex-1 overflow-y-auto bg-[var(--yak-components-panel-bg)] p-4">
          <div className="mx-auto max-w-[1120px] space-y-3">
            <CollapseSection
              id="java-run-type"
              title="运行方式"
              triggerClassName="!h-8 rounded-sm px-3"
              contentClassName="space-y-3 pt-3"
            >
              <JavaField
                label="运行类型"
                labelAddon={
                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger
                        render={
                          <Button
                            type="button"
                            size="sm"
                            variant="ghost"
                            isIconOnly
                            aria-label="查看运行方式说明"
                            className="!size-4 !min-h-4 !p-0 text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-primary)]"
                          >
                            <Info size={13} aria-hidden="true" />
                          </Button>
                        }
                      />
                      <TooltipContent side="top" align="start" className="border-white/10 shadow-xl">
                        <div className="space-y-2">
                          <div>
                            <div className="font-medium">依赖内置 JAR</div>
                            <div>主程序和运行依赖已打包在同一个 JAR 中，指定 JAR 即可运行。</div>
                          </div>
                          <div>
                            <div className="font-medium">普通 JAR</div>
                            <div>需要指定主类；如果还有外部依赖，可在「依赖资源」中添加。</div>
                          </div>
                        </div>
                      </TooltipContent>
                    </Tooltip>
                  </TooltipProvider>
                }
              >
                <Segmented
                  aria-label="运行类型"
                  value={form.runType}
                  options={runTypeOptions}
                  onValueChange={(value) => updateField("runType", value)}
                />
              </JavaField>
            </CollapseSection>

            <CollapseSection
              id="java-entry"
              title="程序入口"
              triggerClassName="!h-8 rounded-sm px-3"
              contentClassName="space-y-3 pt-3"
            >
              <JavaField label="主程序 JAR" required>
                <Autocomplete<string>
                  items={jarFiles}
                  value={form.mainJar}
                  onValueChange={(value) => updateField("mainJar", value)}
                  openOnInputClick
                >
                  <AutocompleteInput
                    size="small"
                    variant="outlined"
                    aria-label="主程序 JAR"
                    placeholder="请选择主程序 JAR"
                    className={inputClassName}
                  />
                  <AutocompleteContent
                    emptyContent={
                      resourceQuery.isPending ? "正在加载资源..." : "暂无匹配的 JAR"
                    }
                  >
                    {(path: string) => (
                      <AutocompleteItem key={path} value={path}>
                        <span className="min-w-0 truncate">{path}</span>
                      </AutocompleteItem>
                    )}
                  </AutocompleteContent>
                </Autocomplete>
              </JavaField>

              {form.runType === "NORMAL_JAR" ? (
                <JavaField label="主类名称">
                  <Input
                    size="small"
                    variant="outlined"
                    value={form.mainClass}
                    onChange={(event) =>
                      updateField("mainClass", event.target.value)
                    }
                    placeholder="请输入完整类名"
                    className={inputClassName}
                  />
                </JavaField>
              ) : null}
            </CollapseSection>

            <CollapseSection
              id="java-runtime-args"
              title="运行参数"
              triggerClassName="!h-8 rounded-sm px-3"
              contentClassName="space-y-3 pt-3"
            >
              <JavaField label="Main 参数" align="start">
                <Textarea
                  size="small"
                  variant="outlined"
                  rows={2}
                  value={form.mainArgs}
                  onValueChange={(value) =>
                    updateField("mainArgs", String(value ?? ""))
                  }
                  placeholder="请输入 Main 参数"
                  className={textareaClassName}
                />
              </JavaField>
              <JavaField
                label="JVM 参数"
                align="start"
                labelAddon={
                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger
                        render={
                          <Button
                            type="button"
                            size="sm"
                            variant="ghost"
                            isIconOnly
                            aria-label="查看 JVM 参数说明"
                            className="!size-4 !min-h-4 !p-0 text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-primary)]"
                          >
                            <Info size={13} aria-hidden="true" />
                          </Button>
                        }
                      />
                      <TooltipContent
                        side="top"
                        align="start"
                        className="max-w-[280px] shadow-xl"
                      >
                        <div className="space-y-1.5 text-xs leading-5">
                          <div>
                            <code className="font-semibold">-Xms</code>
                            <span>：Java 初始堆内存。</span>
                          </div>
                          <div>
                            <code className="font-semibold">-Xmx</code>
                            <span>：Java 最大堆内存。</span>
                          </div>
                          <div>
                            <code className="font-semibold">-D</code>
                            <span>：设置 Java 系统属性，例如文件编码。</span>
                          </div>
                          <p className="pt-1 text-[var(--yak-components-muted-text)]">
                            最大堆内存不应超过 Worker 可用内存，模板可按实际运行环境调整。
                          </p>
                        </div>
                      </TooltipContent>
                    </Tooltip>
                  </TooltipProvider>
                }
              >
                <div className="space-y-2">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className="mr-1 text-[11px] text-[var(--yak-components-muted-text)]">
                      快捷模板
                    </span>
                    {jvmArgTemplates.map((template) => {
                      const active = form.jvmArgs.trim() === template.value;
                      return (
                        <Button
                          key={template.key}
                          type="button"
                          size="sm"
                          variant={active ? "outline" : "ghost"}
                          onClick={() => updateField("jvmArgs", template.value)}
                          aria-pressed={active}
                          className={[
                            "!h-6 !px-2 !text-[11px]",
                            active
                              ? "border-[var(--yak-components-input-border-focus)] bg-[var(--yak-components-input-bg-focus)] text-[var(--yak-color-primary)]"
                              : "text-[var(--yak-components-field-label)]",
                          ].join(" ")}
                        >
                          {template.label}
                        </Button>
                      );
                    })}
                  </div>
                  <Textarea
                    size="small"
                    variant="outlined"
                    rows={2}
                    value={form.jvmArgs}
                    onValueChange={(value) =>
                      updateField("jvmArgs", String(value ?? ""))
                    }
                    placeholder="例如：-Xms512m -Xmx1024m"
                    className={textareaClassName}
                  />
                </div>
              </JavaField>
            </CollapseSection>

            <CollapseSection
              id="java-resources"
              title="依赖资源"
              extra={
                <span className="text-xs font-normal text-[var(--yak-components-muted-text)]">
                  {form.resourceList.filter((item) => item.trim()).length} 项
                </span>
              }
              triggerClassName="!h-8 rounded-sm px-3"
              contentClassName="space-y-3 pt-3"
            >
              {form.resourceList.length > 0 ? (
                <div className="space-y-2">
                  {form.resourceList.map((resource, index) => (
                    <JavaField
                      key={index}
                      label={"依赖资源 " + (index + 1)}
                    >
                      <div className="flex items-center gap-2">
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
                          className="min-w-0 flex-1"
                        />
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          isIconOnly
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
                    </JavaField>
                  ))}
                </div>
              ) : (
                <div className="py-2 text-center text-xs text-[var(--yak-components-muted-text)]">
                  暂无依赖资源
                </div>
              )}

              <div className="flex items-center justify-between border-t border-[var(--yak-components-panel-border)] pt-3">
                <label className="flex cursor-pointer items-center gap-2 text-xs text-[var(--yak-components-field-label)]">
                  <Checkbox
                    checked={form.isModulePath}
                    onCheckedChange={(checked) =>
                      updateField("isModulePath", checked === true)
                    }
                    aria-label="使用 Java Module Path"
                  />
                  <span>使用 Java Module Path</span>
                </label>
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  onClick={() =>
                    updateField("resourceList", [...form.resourceList, ""])
                  }
                >
                  <Plus size={13} />
                  添加依赖资源
                </Button>
              </div>
            </CollapseSection>

            <CollapseSection
              id="java-local-params"
              title="本地参数"
              extra={
                <span className="text-xs font-normal text-[var(--yak-components-muted-text)]">
                  {form.localParams.length} 项
                </span>
              }
              triggerClassName="!h-8 rounded-sm px-3"
              contentClassName="space-y-3 pt-3"
            >
              {form.localParams.length === 0 ? (
                <div className="py-2 text-center text-xs text-[var(--yak-components-muted-text)]">
                  暂无本地参数
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <div className="min-w-[760px]">
                    <div className="grid grid-cols-[minmax(0,1.2fr)_112px_140px_minmax(0,1.5fr)_64px_40px] items-center gap-2 border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)] px-2 py-2 text-xs text-[var(--yak-components-muted-text)]">
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
                            onChange={(event) =>
                              updateLocalParam(index, {
                                prop: event.target.value,
                              })
                            }
                            placeholder="参数名称"
                            aria-label="参数名称"
                          />
                          <Select<string>
                            size="small"
                            items={{ IN: "输入 IN", OUT: "输出 OUT" }}
                            value={item.direct}
                            onValueChange={(value) => {
                              if (value === "IN" || value === "OUT") {
                                updateLocalParam(index, { direct: value });
                              }
                            }}
                          >
                            <SelectTrigger
                              variant="outlined"
                              className="w-full"
                              aria-label="参数方向"
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
                              javaLocalParamTypeOptions.map((option) => [
                                option.value,
                                option.label,
                              ]),
                            )}
                            value={item.type}
                            onValueChange={(value) => {
                              if (value) updateLocalParam(index, { type: value });
                            }}
                          >
                            <SelectTrigger
                              variant="outlined"
                              className="w-full"
                              aria-label="参数类型"
                            >
                              <SelectValue placeholder="选择类型" />
                            </SelectTrigger>
                            <SelectContent>
                              {javaLocalParamTypeOptions.map((option) => (
                                <SelectItem key={option.value} value={option.value}>
                                  <SelectItemText>{option.label}</SelectItemText>
                                  <SelectItemIndicator />
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                          {item.type === "BOOLEAN" ? (
                            <Select<string>
                              size="small"
                              items={{ true: "true", false: "false" }}
                              value={
                                item.value === "true" || item.value === "false"
                                  ? item.value
                                  : null
                              }
                              onValueChange={(value) => {
                                if (value === "true" || value === "false") {
                                  updateLocalParam(index, { value });
                                }
                              }}
                            >
                              <SelectTrigger
                                variant="outlined"
                                className="w-full"
                                aria-label="布尔参数值"
                              >
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
                          ) : item.sensitive ? (
                            <PasswordInput
                              size="small"
                              variant="outlined"
                              value={item.value}
                              onChange={(event) =>
                                updateLocalParam(index, { value: event.target.value })
                              }
                              placeholder={
                                item.type === "DATE" ? "YYYY-MM-DD"
                                  : item.type === "TIME" ? "HH:mm:ss"
                                  : item.type === "TIMESTAMP" ? "YYYY-MM-DD HH:mm:ss"
                                  : item.type === "LIST" ? 'JSON 数组，例如 ["a","b"]'
                                  : "参数值"
                              }
                              aria-label="参数值"
                            />
                          ) : (
                            <Input
                              size="small"
                              variant="outlined"
                              type={
                                ["INTEGER", "LONG", "FLOAT", "DOUBLE"].includes(item.type)
                                  ? "number"
                                  : item.type === "DATE"
                                    ? "date"
                                    : item.type === "TIME"
                                      ? "time"
                                      : "text"
                              }
                              step={
                                item.type === "INTEGER" || item.type === "LONG" || item.type === "TIME"
                                  ? 1
                                  : "any"
                              }
                              value={item.value}
                              onChange={(event) =>
                                updateLocalParam(index, { value: event.target.value })
                              }
                              placeholder={
                                item.type === "INTEGER" ? "请输入整数"
                                  : item.type === "LONG" ? "请输入长整数"
                                  : item.type === "FLOAT" || item.type === "DOUBLE" ? "请输入数字"
                                  : item.type === "DATE" ? "YYYY-MM-DD"
                                  : item.type === "TIME" ? "HH:mm:ss"
                                  : item.type === "TIMESTAMP" ? "YYYY-MM-DD HH:mm:ss"
                                  : item.type === "LIST" ? 'JSON 数组，例如 ["a","b"]'
                                  : item.type === "FILE" ? "请输入文件路径"
                                  : "参数值"
                              }
                              aria-label="参数值"
                            />
                          )}
                          <div className="flex items-center justify-center">
                            <Switch
                              size="small"
                              checked={item.sensitive}
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
                            aria-label="移除本地参数"
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

              <div className="flex justify-end border-t border-[var(--yak-components-panel-border)] pt-3">
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  onClick={() =>
                    updateField("localParams", [
                      ...form.localParams,
                      {
                        prop: "",
                        direct: "IN",
                        type: "VARCHAR",
                        value: "",
                        sensitive: false,
                      },
                    ])
                  }
                >
                  <Plus size={13} />
                  添加参数
                </Button>
              </div>
            </CollapseSection>

          </div>
        </div>
      )}

    </NodeEditorFrame>
  );
}
