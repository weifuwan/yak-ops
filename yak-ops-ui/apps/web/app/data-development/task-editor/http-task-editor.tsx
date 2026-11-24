import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
  toast,
} from "@yak-ops/yak-ui";
import { Clock3, FileJson, Globe2, Plus, ShieldCheck, Trash2 } from "lucide-react";

import { listWorkflowResources } from "@/service/resources";
import { getWorkflowDefinition, type WorkflowTaskRecord } from "@/service/workflow/definition";

import { NodeResourceParamsPanel } from "./common/node-resource-params";
import { buildResourceTreeOptions } from "./common/node-resource-params/resource-adapter";
import { NodeEditorFrame } from "./node-editor-frame";
import {
  buildHttpTaskParams,
  HTTP_CHECK_CONDITIONS,
  HTTP_REQUEST_METHODS,
  parseHttpTaskForm,
  validateHttpTaskForm,
  type HttpCheckCondition,
  type HttpParametersType,
  type HttpProperty,
  type HttpRequestMethod,
  type HttpTaskForm,
} from "./http-task-editor-adapter";
import {
  getTaskGroupValidationError,
  normalizeTaskCommonSettings,
  parseTaskCommonSettings,
  TaskCommonSettingsSection,
  useTaskGroups,
  type TaskCommonSettings,
} from "./task-common-settings-section";

const methodLabels: Record<HttpRequestMethod, string> = {
  GET: "GET",
  POST: "POST",
  PUT: "PUT",
  DELETE: "DELETE",
};

const checkConditionOptions: Array<{ value: HttpCheckCondition; label: string }> = [
  { value: "STATUS_CODE_DEFAULT", label: "默认状态码（200）" },
  { value: "STATUS_CODE_CUSTOM", label: "自定义状态码" },
  { value: "BODY_CONTAINS", label: "响应内容包含" },
  { value: "BODY_NOT_CONTAINS", label: "响应内容不包含" },
];

function HttpSection({
  title,
  description,
  trailing,
  children,
}: {
  title: string;
  description?: string;
  trailing?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="overflow-hidden rounded-md border border-[var(--yak-components-panel-border)] bg-white">
      <header className="flex min-h-12 items-center justify-between gap-3 border-b border-[#edf0f3] px-4 py-2.5">
        <div className="min-w-0">
          <h3 className="text-[13px] font-semibold text-[var(--yak-components-panel-text)]">{title}</h3>
          {description ? (
            <p className="mt-0.5 text-[11px] leading-4 text-[var(--yak-components-muted-text)]">{description}</p>
          ) : null}
        </div>
        {trailing}
      </header>
      <div className="p-4">{children}</div>
    </section>
  );
}

function HttpKeyValueSection({
  title,
  description,
  parameterType,
  rows,
  onChange,
  disabled,
}: {
  title: string;
  description: string;
  parameterType: HttpParametersType;
  rows: HttpProperty[];
  onChange: (rows: HttpProperty[]) => void;
  disabled: boolean;
}) {
  const updateRow = (index: number, patch: Partial<HttpProperty>) => {
    onChange(rows.map((row, rowIndex) => rowIndex === index ? { ...row, ...patch } : row));
  };

  return (
    <HttpSection
      title={title}
      description={description}
      trailing={<span className="shrink-0 text-[11px] tabular-nums text-[var(--yak-components-muted-text)]">{rows.length} 项</span>}
    >
      {rows.length ? (
        <div className="overflow-x-auto">
          <div className="min-w-[540px]">
            <div className="grid grid-cols-[minmax(0,0.9fr)_minmax(0,1.4fr)_36px] items-center gap-2 border-b border-[#edf0f3] bg-[#f8f9fb] px-2 py-2 text-[11px] text-[#7c8796]">
              <span>KEY</span>
              <span>VALUE</span>
              <span />
            </div>
            <div className="space-y-2 pt-2">
              {rows.map((row, index) => (
                <div key={parameterType + "-" + index} className="grid grid-cols-[minmax(0,0.9fr)_minmax(0,1.4fr)_36px] items-center gap-2">
                  <Input
                    size="small"
                    variant="outlined"
                    value={row.prop}
                    disabled={disabled}
                    onChange={(event) => updateRow(index, { prop: event.target.value })}
                    placeholder={parameterType === "HEADERS" ? "例如 Authorization" : "例如 page"}
                    aria-label={title + "名称 " + (index + 1)}
                  />
                  <Input
                    size="small"
                    variant="outlined"
                    value={row.value}
                    disabled={disabled}
                    onChange={(event) => updateRow(index, { value: event.target.value })}
                    placeholder="输入值，支持变量占位符"
                    aria-label={title + "值 " + (index + 1)}
                  />
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    isIconOnly
                    isDisabled={disabled}
                    aria-label={"删除" + title}
                    onClick={() => onChange(rows.filter((_row, rowIndex) => rowIndex !== index))}
                    className="text-[var(--yak-components-muted-text)] hover:text-[var(--yak-components-field-error)]"
                  >
                    <Trash2 size={14} />
                  </Button>
                </div>
              ))}
            </div>
          </div>
        </div>
      ) : (
        <div className="flex min-h-[76px] items-center justify-center rounded-md border border-dashed border-[#dfe4eb] bg-[#fcfdff] text-xs text-[#8b93a1]">
          暂无{title}
        </div>
      )}
      <div className="mt-3 flex justify-end border-t border-[var(--yak-components-panel-border)] pt-3">
        <Button
          type="button"
          size="sm"
          variant="secondary"
          isDisabled={disabled}
          onClick={() => onChange([...rows, { prop: "", httpParametersType: parameterType, value: "" }])}
        >
          <Plus size={13} />
          添加{parameterType === "HEADERS" ? "请求头" : "查询参数"}
        </Button>
      </div>
    </HttpSection>
  );
}

function HttpRequestWorkspace({
  form,
  taskName,
  readOnly,
  updateField,
  updateParamGroup,
}: {
  form: HttpTaskForm;
  taskName: string;
  readOnly: boolean;
  updateField: <K extends keyof HttpTaskForm>(field: K, value: HttpTaskForm[K]) => void;
  updateParamGroup: (type: HttpParametersType, rows: HttpProperty[]) => void;
}) {
  const headers = form.httpParams.filter((item) => item.httpParametersType === "HEADERS");
  const queryParams = form.httpParams.filter((item) => item.httpParametersType === "PARAMETER");
  const showBody = form.httpMethod === "POST" || form.httpMethod === "PUT";
  const needsCondition = form.httpCheckCondition !== "STATUS_CODE_DEFAULT";
  const conditionLabel = form.httpCheckCondition === "STATUS_CODE_CUSTOM" ? "期望状态码" : "匹配内容";

  return (
    <div className="min-h-0 flex-1 overflow-y-auto bg-[var(--yak-components-panel-bg)] p-4">
      <div className="mx-auto max-w-[980px] space-y-4 pb-4">
        <div className="flex flex-wrap items-start justify-between gap-3 px-1">
          <div>
            <div className="flex items-center gap-2">
              <Globe2 size={17} className="text-[#315efb]" />
              <h2 className="text-sm font-semibold text-[var(--yak-components-panel-text)]">HTTP 请求配置</h2>
            </div>
            <p className="mt-1 text-xs leading-5 text-[var(--yak-components-muted-text)]">
              配置请求地址、请求头、查询参数和响应检查，执行结果会作为节点输出提供给下游节点。
            </p>
          </div>
          <span className="rounded border border-[#dce5ff] bg-[#f5f8ff] px-2 py-1 text-[10px] font-semibold tracking-wide text-[#315efb]">
            HTTP
          </span>
        </div>

        <HttpSection
          title="请求配置"
          description="选择请求方式并填写目标接口地址。"
        >
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-[#475467]">请求地址</label>
              <div className="flex min-w-0 gap-2">
                <Select<string>
                  size="small"
                  items={Object.fromEntries(HTTP_REQUEST_METHODS.map((method) => [method, methodLabels[method]]))}
                  value={form.httpMethod}
                  disabled={readOnly}
                  onValueChange={(value) => {
                    if (HTTP_REQUEST_METHODS.some((method) => method === value)) {
                      updateField("httpMethod", value as HttpRequestMethod);
                    }
                  }}
                >
                  <SelectTrigger variant="outlined" className="w-[124px] shrink-0" aria-label="HTTP 请求方式">
                    <SelectValue placeholder="请求方式" />
                  </SelectTrigger>
                  <SelectContent>
                    {HTTP_REQUEST_METHODS.map((method) => (
                      <SelectItem key={method} value={method}>
                        <SelectItemText>{methodLabels[method]}</SelectItemText>
                        <SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Input
                  size="small"
                  variant="outlined"
                  value={form.url}
                  disabled={readOnly}
                  onChange={(event) => updateField("url", event.target.value)}
                  placeholder="https://api.example.com/v1/resource"
                  aria-label="HTTP 请求 URL"
                  className="min-w-0 flex-1"
                />
              </div>
              <p className="text-[11px] leading-4 text-[var(--yak-components-muted-text)]">
                URL 当前按原值发送；请求头、查询参数和 Body 支持 {"${变量名}"} 及 $[yyyyMMdd] 时间占位符。Content-Type 不进行变量替换。
              </p>
            </div>

            <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-4 max-md:grid-cols-1">
              <div className="space-y-1.5">
                <label className="flex items-center gap-1.5 text-xs font-medium text-[#475467]">
                  <Clock3 size={13} />
                  请求超时
                </label>
                <div className="flex items-center gap-2">
                  <Input
                    size="small"
                    variant="outlined"
                    type="number"
                    min={1}
                    step={1}
                    value={form.connectTimeout}
                    disabled={readOnly}
                    onChange={(event) => updateField("connectTimeout", event.target.value)}
                    aria-label="请求超时毫秒"
                    className="min-w-0 flex-1"
                  />
                  <span className="shrink-0 text-xs text-[#7c8796]">毫秒</span>
                </div>
                <p className="text-[11px] leading-4 text-[var(--yak-components-muted-text)]">
                  后端使用同一数值作为连接、读取和写入超时。
                </p>
              </div>
              <div className="flex items-center rounded-md bg-[#f8f9fb] px-3 py-2.5">
                <p className="text-[11px] leading-5 text-[#687385]">
                  认证信息通过请求头配置，例如 <code className="rounded bg-white px-1 py-0.5">Authorization</code>。默认 Content-Type 为 <code className="rounded bg-white px-1 py-0.5">application/json</code>；请求体始终按 JSON 对象序列化。
                </p>
              </div>
            </div>
          </div>
        </HttpSection>

        <HttpKeyValueSection
          title="请求头（Headers）"
          description="设置 Authorization、Content-Type 等 HTTP 请求头。Content-Type 仅设置媒体类型，不会切换请求体编码方式。"
          parameterType="HEADERS"
          rows={headers}
          onChange={(rows) => updateParamGroup("HEADERS", rows)}
          disabled={readOnly}
        />

        <HttpKeyValueSection
          title="查询参数（Query Params）"
          description="后端当前仅 GET 请求会将这些参数追加到 URL 查询字符串中；POST、PUT、DELETE 暂不传递此列表。"
          parameterType="PARAMETER"
          rows={queryParams}
          onChange={(rows) => updateParamGroup("PARAMETER", rows)}
          disabled={readOnly}
        />

        {showBody ? (
          <HttpSection
            title="请求 Body"
            description="当前后端执行器要求请求体为 JSON 对象；留空时不发送请求体。"
            trailing={<span className="flex items-center gap-1 text-[11px] text-[var(--yak-components-muted-text)]"><FileJson size={13} />JSON</span>}
          >
            <Textarea
              size="small"
              variant="outlined"
              rows={11}
              value={form.httpBody}
              disabled={readOnly}
              onValueChange={(value) => updateField("httpBody", String(value ?? ""))}
              placeholder={'{\n  "name": "\${variableName}",\n  "date": "$[yyyyMMdd]"\n}'}
              aria-label="HTTP 请求 Body"
              className="w-full font-mono text-xs leading-5"
            />
          </HttpSection>
        ) : null}

        <HttpSection
          title="响应检查"
          description="根据 HTTP 状态码或响应内容判断当前节点是否执行成功。"
          trailing={<ShieldCheck size={16} className="text-[#7c8796]" />}
        >
          <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-4 max-md:grid-cols-1">
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-[#475467]">检查方式</label>
              <Select<string>
                size="small"
                items={Object.fromEntries(checkConditionOptions.map((item) => [item.value, item.label]))}
                value={form.httpCheckCondition}
                disabled={readOnly}
                onValueChange={(value) => {
                  if (HTTP_CHECK_CONDITIONS.some((condition) => condition === value)) {
                    updateField("httpCheckCondition", value as HttpCheckCondition);
                  }
                }}
              >
                <SelectTrigger variant="outlined" className="w-full" aria-label="响应检查方式">
                  <SelectValue placeholder="选择检查方式" />
                </SelectTrigger>
                <SelectContent>
                  {checkConditionOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      <SelectItemText>{option.label}</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {needsCondition ? (
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-[#475467]">{conditionLabel}</label>
                <Input
                  size="small"
                  variant="outlined"
                  type={form.httpCheckCondition === "STATUS_CODE_CUSTOM" ? "number" : "text"}
                  min={form.httpCheckCondition === "STATUS_CODE_CUSTOM" ? 1 : undefined}
                  step={form.httpCheckCondition === "STATUS_CODE_CUSTOM" ? 1 : undefined}
                  value={form.condition}
                  disabled={readOnly}
                  onChange={(event) => updateField("condition", event.target.value)}
                  placeholder={form.httpCheckCondition === "STATUS_CODE_CUSTOM" ? "例如 201" : "输入需要匹配的响应文本"}
                  aria-label={conditionLabel}
                />
              </div>
            ) : (
              <div className="flex items-center rounded-md bg-[#f8f9fb] px-3 py-2.5">
                <p className="text-xs leading-5 text-[#687385]">HTTP 状态码必须严格等于 200 才会判定成功。</p>
              </div>
            )}
          </div>
          <div className="mt-3 flex items-start gap-2 rounded-md bg-[#f8f9fb] px-3 py-2.5">
            <ShieldCheck size={14} className="mt-0.5 shrink-0 text-[#7c8796]" />
            <p className="text-[11px] leading-5 text-[#687385]">
              {form.httpCheckCondition === "STATUS_CODE_DEFAULT"
                ? "默认检查只接受 HTTP 200；其他状态码均视为失败。"
                : form.httpCheckCondition === "STATUS_CODE_CUSTOM"
                  ? "只有响应状态码与指定值完全一致时，节点才会执行成功。"
                  : form.httpCheckCondition === "BODY_CONTAINS"
                    ? "响应 Body 包含指定文本时，节点执行成功。"
                    : "响应 Body 不包含指定文本时，节点执行成功。"}
            </p>
          </div>
        </HttpSection>

        <div className="flex flex-wrap items-center justify-between gap-2 px-1 text-[11px] text-[var(--yak-components-muted-text)]">
          <span>响应结果输出变量：<code className="rounded bg-white px-1 py-0.5">{"${" + taskName + ".response}"}</code></span>
          <span>本节点参数结构与后端 HttpParameters 保持一致</span>
        </div>
      </div>
    </div>
  );
}

export function HttpTaskEditor({
  workflowCode,
  task,
  taskName,
  readOnly,
  onApplyTask,
  onDirtyChange,
  onBackToWorkflow,
}: {
  workflowCode: number;
  task: WorkflowTaskRecord;
  taskName: string;
  readOnly: boolean;
  onApplyTask: (task: WorkflowTaskRecord) => boolean;
  onDirtyChange: (dirty: boolean) => void;
  onBackToWorkflow: () => void;
}) {
  const queryClient = useQueryClient();
  const { projectCode, taskGroups, taskGroupsQuery } = useTaskGroups();
  const initialForm = useMemo(() => parseHttpTaskForm(task.taskParams), [task.taskParams]);
  const initialCommonSettings = useMemo(() => parseTaskCommonSettings(task), [task]);
  const [form, setForm] = useState<HttpTaskForm>(initialForm);
  const [baselineForm, setBaselineForm] = useState<HttpTaskForm>(initialForm);
  const [commonSettings, setCommonSettings] = useState<TaskCommonSettings>(initialCommonSettings);
  const [baselineCommonSettings, setBaselineCommonSettings] = useState<TaskCommonSettings>(initialCommonSettings);
  const [currentTask, setCurrentTask] = useState(task);
  const [activeView, setActiveView] = useState<"form" | "json">("form");
  const [saving, setSaving] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    setForm(initialForm);
    setBaselineForm(initialForm);
    setCommonSettings(initialCommonSettings);
    setBaselineCommonSettings(initialCommonSettings);
    setCurrentTask(task);
  }, [initialForm, initialCommonSettings, task]);

  const resourceQuery = useQuery({
    queryKey: ["data-development", "http-task-resources"],
    queryFn: ({ signal }) => listWorkflowResources(signal),
    staleTime: 60_000,
  });
  const resourceTree = useMemo(
    () => buildResourceTreeOptions(resourceQuery.data ?? []),
    [resourceQuery.data],
  );

  const updateField = <K extends keyof HttpTaskForm>(field: K, value: HttpTaskForm[K]) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const updateParamGroup = (type: HttpParametersType, rows: HttpProperty[]) => {
    setForm((current) => ({
      ...current,
      httpParams: [
        ...current.httpParams.filter((item) => item.httpParametersType !== type),
        ...rows,
      ],
    }));
  };

  const isDirty =
    JSON.stringify(form) !== JSON.stringify(baselineForm) ||
    JSON.stringify(commonSettings) !== JSON.stringify(baselineCommonSettings);

  const jsonText = useMemo(() => {
    let commonFields: Record<string, unknown>;
    try {
      commonFields = normalizeTaskCommonSettings(commonSettings);
    } catch {
      commonFields = commonSettings;
    }
    return JSON.stringify({
      workflowCode,
      task: {
        ...currentTask,
        ...commonFields,
        taskType: "HTTP",
        taskParams: buildHttpTaskParams(form, currentTask.taskParams),
      },
    }, null, 2);
  }, [commonSettings, currentTask, form, workflowCode]);

  useEffect(() => {
    onDirtyChange(isDirty);
  }, [isDirty, onDirtyChange]);

  useEffect(() => () => onDirtyChange(false), [onDirtyChange]);

  const handleSave = async () => {
    const validationError = validateHttpTaskForm(form);
    if (validationError) {
      toast.warning(validationError);
      return false;
    }

    let commonPatch;
    try {
      commonPatch = normalizeTaskCommonSettings(commonSettings);
    } catch (error) {
      toast.warning(error instanceof Error ? error.message : "请检查节点运行配置");
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
      const nextTask = {
        ...currentTask,
        ...commonPatch,
        taskType: "HTTP",
        taskParams: buildHttpTaskParams(form, currentTask.taskParams),
      };
      if (!onApplyTask(nextTask)) return false;
      setCurrentTask(nextTask);
      setBaselineForm(form);
      setBaselineCommonSettings(commonSettings);
      toast.success("HTTP 节点配置已应用，请保存工作流");
      return true;
    } finally {
      setSaving(false);
    }
  };

  const handleRefresh = async () => {
    if (isDirty) {
      toast.warning("当前配置有未保存修改，请先保存后再刷新");
      return;
    }
    if (currentTask.version <= 0 || currentTask.id <= 0) {
      toast.info("新建节点尚未保存到后端，暂无可刷新的服务端配置");
      return;
    }
    setRefreshing(true);
    try {
      const dag = await getWorkflowDefinition(workflowCode);
      const latestTask = dag.taskDefinitionList.find((item) => item.code === currentTask.code);
      if (!latestTask) throw new Error("节点不存在或已被删除");
      if (!onApplyTask(latestTask)) return;
      const nextForm = parseHttpTaskForm(latestTask.taskParams);
      const nextCommonSettings = parseTaskCommonSettings(latestTask);
      setCurrentTask(latestTask);
      setForm(nextForm);
      setBaselineForm(nextForm);
      setCommonSettings(nextCommonSettings);
      setBaselineCommonSettings(nextCommonSettings);
      await queryClient.invalidateQueries({ queryKey: ["data-development", "workflow-definition"] });
      toast.success("HTTP 节点配置已刷新");
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
      taskType="HTTP"
      workflowCode={workflowCode}
      activeView={activeView}
      onActiveViewChange={setActiveView}
      isDirty={isDirty}
      saving={saving}
      running={false}
      stopping={false}
      refreshing={refreshing || resourceQuery.isFetching}
      lastWorkflowInstanceId={null}
      canRun={false}
      readOnly={readOnly}
      onRun={() => undefined}
      onStop={() => undefined}
      onSave={() => void handleSave()}
      onRefresh={() => void handleRefresh()}
      onBack={onBackToWorkflow}
      resourceParams={
        <NodeResourceParamsPanel
          idPrefix={"http-" + currentTask.code}
          resourceList={form.resourceList}
          onResourceListChange={(resourceList) => updateField("resourceList", resourceList)}
          localParams={form.localParams}
          onLocalParamsChange={(localParams) => updateField("localParams", localParams)}
          resourceTree={resourceTree}
          resourceLoading={resourceQuery.isPending}
          resourceError={resourceQuery.isError}
          onRetryResources={() => void resourceQuery.refetch()}
          disabled={readOnly}
        />
      }
      runConfig={
        <TaskCommonSettingsSection
          id="http-run-config"
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
        <HttpRequestWorkspace
          form={form}
          taskName={taskName}
          readOnly={readOnly}
          updateField={updateField}
          updateParamGroup={updateParamGroup}
        />
      )}
    </NodeEditorFrame>
  );
}
