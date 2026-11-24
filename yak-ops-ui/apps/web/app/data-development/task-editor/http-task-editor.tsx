import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Autocomplete,
  AutocompleteContent,
  AutocompleteInput,
  AutocompleteItem,
  Button,
  CollapseSection,
  Field,
  FieldLabel,
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
import { Plus, Trash2 } from "lucide-react";

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

const HTTP_HEADER_SUGGESTIONS = [
  "Authorization",
  "Content-Type",
  "Accept",
  "User-Agent",
  "Cache-Control",
  "X-Api-Key",
  "X-Tenant-Id",
  "X-Request-ID",
  "Cookie",
  "Accept-Language",
  "Origin",
  "Referer",
  "If-None-Match",
  "If-Match",
] as const;

const checkConditionOptions: Array<{ value: HttpCheckCondition; label: string }> = [
  { value: "STATUS_CODE_DEFAULT", label: "默认状态码（200）" },
  { value: "STATUS_CODE_CUSTOM", label: "自定义状态码" },
  { value: "BODY_CONTAINS", label: "响应内容包含" },
  { value: "BODY_NOT_CONTAINS", label: "响应内容不包含" },
];

function HttpField({
  label,
  children,
  required = false,
  align = "center",
}: {
  label: string;
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
      <FieldLabel required={required} className={align === "start" ? "pt-1.5" : ""}>
        {label}
      </FieldLabel>
      <div className="min-w-0 max-w-[760px]">{children}</div>
    </Field>
  );
}

function HttpKeyValueSection({
  id,
  title,
  parameterType,
  rows,
  onChange,
  disabled,
}: {
  id: string;
  title: string;
  parameterType: HttpParametersType;
  rows: HttpProperty[];
  onChange: (rows: HttpProperty[]) => void;
  disabled: boolean;
}) {
  const updateRow = (index: number, patch: Partial<HttpProperty>) => {
    onChange(rows.map((row, rowIndex) => rowIndex === index ? { ...row, ...patch } : row));
  };

  return (
    <CollapseSection
      id={id}
      title={title}
      extra={<span className="text-[11px] tabular-nums text-[var(--yak-components-muted-text)]">{rows.length} 项</span>}
      triggerClassName="!h-8 rounded-sm px-3"
      contentClassName="space-y-3 pt-3"
    >
      {rows.length ? (
        <div className="overflow-x-auto">
          <div className="min-w-[540px]">
            <div className="grid grid-cols-[minmax(0,0.9fr)_minmax(0,1.4fr)_36px] items-center gap-2 border-b border-[var(--yak-components-panel-border)] bg-[#fafafa] px-2 py-2 text-[11px] text-[var(--yak-components-muted-text)]">
              <span>参数名</span>
              <span>参数值</span>
              <span />
            </div>
            <div className="space-y-2 pt-2">
              {rows.map((row, index) => (
                <div
                  key={parameterType + "-" + index}
                  className="grid grid-cols-[minmax(0,0.9fr)_minmax(0,1.4fr)_36px] items-center gap-2"
                >
                  {parameterType === "HEADERS" ? (
                    <Autocomplete<string>
                      items={HTTP_HEADER_SUGGESTIONS}
                      value={row.prop}
                      onValueChange={(value) => updateRow(index, { prop: value })}
                      openOnInputClick
                      disabled={disabled}
                    >
                      <AutocompleteInput
                        size="small"
                        variant="outlined"
                        placeholder="选择或输入请求头名称"
                        aria-label={title + "名称 " + (index + 1)}
                        className="w-full"
                      />
                      <AutocompleteContent emptyContent="没有匹配的请求头，可直接输入自定义名称">
                        {(header: string) => (
                          <AutocompleteItem key={header} value={header}>
                            <span className="min-w-0 truncate">{header}</span>
                          </AutocompleteItem>
                        )}
                      </AutocompleteContent>
                    </Autocomplete>
                  ) : (
                    <Input
                      size="small"
                      variant="outlined"
                      value={row.prop}
                      disabled={disabled}
                      onChange={(event) => updateRow(index, { prop: event.target.value })}
                      placeholder="参数名称"
                      aria-label={title + "名称 " + (index + 1)}
                    />
                  )}
                  <Input
                    size="small"
                    variant="outlined"
                    value={row.value}
                    disabled={disabled}
                    onChange={(event) => updateRow(index, { value: event.target.value })}
                    placeholder="参数值"
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
      ) : null}
      <div className="flex justify-end">
        <Button
          type="button"
          size="sm"
          variant="secondary"
          isDisabled={disabled}
          onClick={() => onChange([...rows, { prop: "", httpParametersType: parameterType, value: "" }])}
        >
          <Plus size={13} />
          添加{parameterType === "HEADERS" ? "请求头" : "参数"}
        </Button>
      </div>
    </CollapseSection>
  );
}

function HttpRequestWorkspace({
  form,
  readOnly,
  updateField,
  updateParamGroup,
}: {
  form: HttpTaskForm;
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
      <div className="mx-auto max-w-[1120px] space-y-3 pb-4">
        <CollapseSection
          id="http-request"
          title="请求配置"
          triggerClassName="!h-8 rounded-sm px-3"
          contentClassName="space-y-3 pt-3"
        >
          <HttpField label="请求方式" required>
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
              <SelectTrigger variant="outlined" className="w-full max-w-[240px]" aria-label="HTTP 请求方式">
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
          </HttpField>

          <HttpField label="请求地址" required>
            <Input
              size="small"
              variant="outlined"
              value={form.url}
              disabled={readOnly}
              onChange={(event) => updateField("url", event.target.value)}
              placeholder="https://api.example.com/v1/resource"
              aria-label="HTTP 请求 URL"
              className="w-full max-w-[760px]"
            />
          </HttpField>

          <HttpField label="请求超时">
            <div className="flex max-w-[320px] items-center gap-2">
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
              <span className="shrink-0 text-xs text-[var(--yak-components-muted-text)]">毫秒</span>
            </div>
          </HttpField>
        </CollapseSection>

        <HttpKeyValueSection
          id="http-headers"
          title="请求头"
          parameterType="HEADERS"
          rows={headers}
          onChange={(rows) => updateParamGroup("HEADERS", rows)}
          disabled={readOnly}
        />

        <HttpKeyValueSection
          id="http-query-params"
          title="查询参数"
          parameterType="PARAMETER"
          rows={queryParams}
          onChange={(rows) => updateParamGroup("PARAMETER", rows)}
          disabled={readOnly}
        />

        {showBody ? (
          <CollapseSection
            id="http-body"
            title="请求 Body"
            extra={<span className="text-[11px] text-[var(--yak-components-muted-text)]">JSON</span>}
            triggerClassName="!h-8 rounded-sm px-3"
            contentClassName="space-y-3 pt-3"
          >
            <HttpField label="请求内容" align="start">
              <Textarea
                size="small"
                variant="outlined"
                rows={8}
                value={form.httpBody}
                disabled={readOnly}
                onValueChange={(value) => updateField("httpBody", String(value ?? ""))}
                placeholder={'{\n  "name": "Yak"\n}'}
                aria-label="HTTP 请求 Body"
                className="w-full max-w-[760px] font-mono text-xs leading-5"
              />
            </HttpField>
          </CollapseSection>
        ) : null}

        <CollapseSection
          id="http-response-check"
          title="响应检查"
          triggerClassName="!h-8 rounded-sm px-3"
          contentClassName="space-y-3 pt-3"
        >
          <HttpField label="检查方式">
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
              <SelectTrigger variant="outlined" className="w-full max-w-[320px]" aria-label="响应检查方式">
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
          </HttpField>
          {needsCondition ? (
            <HttpField label={conditionLabel}>
              <Input
                size="small"
                variant="outlined"
                type={form.httpCheckCondition === "STATUS_CODE_CUSTOM" ? "number" : "text"}
                min={form.httpCheckCondition === "STATUS_CODE_CUSTOM" ? 1 : undefined}
                step={form.httpCheckCondition === "STATUS_CODE_CUSTOM" ? 1 : undefined}
                value={form.condition}
                disabled={readOnly}
                onChange={(event) => updateField("condition", event.target.value)}
                placeholder={form.httpCheckCondition === "STATUS_CODE_CUSTOM" ? "例如 201" : "输入匹配内容"}
                aria-label={conditionLabel}
                className="w-full max-w-[760px]"
              />
            </HttpField>
          ) : null}
        </CollapseSection>
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
          readOnly={readOnly}
          updateField={updateField}
          updateParamGroup={updateParamGroup}
        />
      )}
    </NodeEditorFrame>
  );
}
