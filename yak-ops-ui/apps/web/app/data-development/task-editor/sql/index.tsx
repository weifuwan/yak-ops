import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
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
  Switch,
  Textarea,
  TreeSelect,
  toast,
  type TreeSelectOption,
} from "@yak-ops/yak-ui";
import { Plus, Trash2 } from "lucide-react";

import DatabaseIcons from "@/app/datasource/icons/DatabaseIcons";
import { getDataSourceTypeLabel } from "@/app/datasource/constants";
import { DataSyncSearchableSelect } from "@/app/data-sync/components/searchable-select";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { listAlertGroups, type AlertGroupRecord } from "@/service/alert";
import { listDataSources, type DataSourceRecord } from "@/service/datasource";
import { listWorkflowResources } from "@/service/resources";
import { getWorkflowDefinition } from "@/service/workflow/definition";
import {
  startWorkflowInstance,
  stopWorkflowInstance,
} from "@/service/workflow/execution";

import { NodeResourceParamsPanel } from "../common/node-resource-params";
import { buildResourceTreeOptions } from "../common/node-resource-params/resource-adapter";
import {
  getTaskGroupValidationError,
  normalizeTaskCommonSettings,
  parseTaskCommonSettings,
  TaskCommonSettingsSection,
  useTaskGroups,
  type TaskCommonSettings,
} from "../task-common-settings-section";
import { NodeEditorFrame } from "../node-editor-frame";
import {
  buildSqlTaskParams,
  parseSqlTaskForm,
  toSqlTaskDbType,
  validateSqlTaskForm,
} from "./adapter";
import { SqlCodeEditor } from "./sql-code-editor";
import type { SqlTaskEditorProps, SqlTaskForm, SqlTaskUpdateField } from "./types";

function SqlField({
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
      <div className="min-w-0 max-w-[820px]">{children}</div>
    </Field>
  );
}

function filterSqlResourceTree(options: readonly TreeSelectOption[]): TreeSelectOption[] {
  return options.flatMap((option) => {
    const children = filterSqlResourceTree(option.children ?? []);
    if (option.selectable === false) {
      return children.length ? [{ ...option, children }] : [];
    }
    return /\.sql$/i.test(String(option.value)) ? [option] : [];
  });
}

function hasResourceValue(options: readonly TreeSelectOption[], value: string): boolean {
  return options.some((option) =>
    option.value === value || hasResourceValue(option.children ?? [], value),
  );
}

function SqlSectionHeader({ title }: { title: string }) {
  return (
    <div className="mb-3 flex min-w-0 items-center gap-2">
      <span aria-hidden="true" className="h-4 w-1 shrink-0 bg-[#5664ff]" />
      <h3 className="m-0 shrink-0 text-sm font-semibold leading-5 text-[var(--yak-components-panel-text)]">
        {title}
      </h3>
      <span aria-hidden="true" className="h-px min-w-0 flex-1 bg-[var(--yak-components-panel-border)]" />
    </div>
  );
}

function SqlStatementsSection({
  title,
  statements,
  disabled,
  onChange,
}: {
  title: string;
  statements: string[];
  disabled: boolean;
  onChange: (statements: string[]) => void;
}) {
  const updateStatement = (index: number, value: string) => {
    onChange(statements.map((item, itemIndex) => itemIndex === index ? value : item));
  };

  return (
    <div className="min-w-0">
      <SqlSectionHeader title={title} />
      {statements.length ? (
        <div className="space-y-2">
          {statements.map((statement, index) => (
            <div key={index} className="flex min-w-0 items-start gap-2">
              <div className="min-w-0 flex-1">
                <Textarea
                  size="small"
                  variant="outlined"
                  rows={3}
                  value={statement}
                  disabled={disabled}
                  onValueChange={(value) => updateStatement(index, String(value ?? ""))}
                  placeholder={title + " SQL"}
                  aria-label={title + " " + (index + 1)}
                  className="w-full font-mono text-xs leading-5"
                />
              </div>
              <Button
                type="button"
                size="sm"
                variant="ghost"
                isIconOnly
                isDisabled={disabled}
                aria-label={"删除" + title}
                onClick={() => onChange(statements.filter((_item, itemIndex) => itemIndex !== index))}
                className="mt-1 shrink-0 text-[var(--yak-components-muted-text)] hover:text-[var(--yak-components-field-error)]"
              >
                <Trash2 size={14} />
              </Button>
            </div>
          ))}
        </div>
      ) : (
        <div className="flex min-h-[72px] items-center justify-center rounded-md border border-dashed border-[#dfe4eb] bg-[#fcfdff] text-xs text-[#687385]">
          暂无{title}
        </div>
      )}
      <div className="mt-3 flex justify-end">
        <Button
          type="button"
          size="sm"
          variant="secondary"
          isDisabled={disabled}
          onClick={() => onChange([...statements, ""])}
        >
          <Plus size={13} />
          添加{title}
        </Button>
      </div>
    </div>
  );
}

function SqlTaskWorkspace({
  form,
  readOnly,
  dataSources,
  dataSourcesLoading,
  dataSourcesError,
  onRefreshDataSources,
  resourceTree,
  resourceLoading,
  resourceError,
  onRefreshResources,
  alertGroups,
  alertGroupsLoading,
  alertGroupsError,
  onRefreshAlertGroups,
  updateField,
}: {
  form: SqlTaskForm;
  readOnly: boolean;
  dataSources: DataSourceRecord[];
  dataSourcesLoading: boolean;
  dataSourcesError: boolean;
  onRefreshDataSources: () => void;
  resourceTree: TreeSelectOption[];
  resourceLoading: boolean;
  resourceError: boolean;
  onRefreshResources: () => void;
  alertGroups: AlertGroupRecord[];
  alertGroupsLoading: boolean;
  alertGroupsError: boolean;
  onRefreshAlertGroups: () => void;
  updateField: SqlTaskUpdateField;
}) {
  const dataSourceOptions = useMemo(() => {
    const options = dataSources.flatMap((item) => item.id ? [{
      value: item.id,
      label: item.name || item.id,
      icon: <DatabaseIcons dbType={item.dbType} width="16px" height="16px" />,
      searchText: [item.dbType, item.database, item.schema].filter(Boolean).join(" "),
    }] : []);
    if (form.datasource && !options.some((item) => item.value === form.datasource)) {
      options.unshift({
        value: form.datasource,
        label: "数据源 #" + form.datasource,
        icon: <DatabaseIcons dbType={form.type} width="16px" height="16px" />,
        searchText: form.type,
      });
    }
    return options;
  }, [dataSources, form.datasource, form.type]);

  const currentSqlResourceTree = useMemo(() => {
    const filtered = filterSqlResourceTree(resourceTree);
    if (form.sqlResource && !hasResourceValue(filtered, form.sqlResource)) {
      const label = form.sqlResource.split(/[\\/]/).filter(Boolean).at(-1) || form.sqlResource;
      return [...filtered, { label, value: form.sqlResource }];
    }
    return filtered;
  }, [form.sqlResource, resourceTree]);

  return (
    <div className="min-h-0 flex-1 overflow-y-auto bg-[var(--yak-components-panel-bg)] p-4">
      <div className="mx-auto max-w-[1120px] space-y-3 pb-4">
        <CollapseSection
          id="sql-basic"
          title="基础配置"
          triggerClassName="!h-8 rounded-sm px-3"
          contentClassName="space-y-3 pt-3"
        >
          <SqlField label="数据源" required>
            <div className="space-y-1.5">
              <DataSyncSearchableSelect
                value={form.datasource || null}
                options={dataSourceOptions}
                placeholder="请选择数据源"
                searchPlaceholder="搜索数据源名称或类型"
                emptyText={dataSourcesLoading ? "正在加载数据源..." : "暂无可用数据源"}
                disabled={readOnly}
                refreshing={dataSourcesLoading}
                onRefresh={onRefreshDataSources}
                onValueChange={(value) => {
                  const source = dataSources.find((item) => item.id === value);
                  updateField("datasource", value);
                  updateField("type", source?.dbType ? toSqlTaskDbType(source.dbType) : "");
                }}
              />
              {dataSourcesError ? (
                <button
                  type="button"
                  onClick={onRefreshDataSources}
                  className="text-[11px] text-[#315efb] hover:underline"
                >
                  数据源列表加载失败，点击重试
                </button>
              ) : null}
            </div>
          </SqlField>

          <SqlField label="数据库类型">
            <div className="flex h-8 items-center gap-2 text-xs text-[#475467]">
              {form.type ? <DatabaseIcons dbType={form.type} width="16px" height="16px" /> : null}
              <span>{form.type ? getDataSourceTypeLabel(form.type) : "选择数据源后自动识别"}</span>
            </div>
          </SqlField>

          <SqlField label="SQL 来源" required>
            <Select<string>
              size="small"
              items={{ SCRIPT: "自定义 SQL", FILE: "资源文件" }}
              value={form.sqlSource}
              disabled={readOnly}
              onValueChange={(value) => {
                if (value === "SCRIPT" || value === "FILE") updateField("sqlSource", value);
              }}
            >
              <SelectTrigger variant="outlined" className="w-full max-w-[320px]" aria-label="SQL 来源">
                <SelectValue placeholder="选择 SQL 来源" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="SCRIPT"><SelectItemText>自定义 SQL</SelectItemText><SelectItemIndicator /></SelectItem>
                <SelectItem value="FILE"><SelectItemText>资源文件</SelectItemText><SelectItemIndicator /></SelectItem>
              </SelectContent>
            </Select>
          </SqlField>

          <SqlField label="执行类型" required>
            <Select<string>
              size="small"
              items={{ "0": "查询（QUERY）", "1": "非查询（NON_QUERY）" }}
              value={form.sqlType}
              disabled={readOnly}
              onValueChange={(value) => {
                if (value === "0" || value === "1") {
                  updateField("sqlType", value);
                  if (value === "1" && form.sendEmail) updateField("sendEmail", false);
                }
              }}
            >
              <SelectTrigger variant="outlined" className="w-full max-w-[320px]" aria-label="SQL 执行类型">
                <SelectValue placeholder="选择执行类型" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="0"><SelectItemText>查询（QUERY）</SelectItemText><SelectItemIndicator /></SelectItem>
                <SelectItem value="1"><SelectItemText>非查询（NON_QUERY）</SelectItemText><SelectItemIndicator /></SelectItem>
              </SelectContent>
            </Select>
          </SqlField>
        </CollapseSection>

        <CollapseSection
          id="sql-script"
          title="SQL 脚本"
          extra={<span className="text-[11px] text-[var(--yak-components-muted-text)]">{form.sqlSource === "SCRIPT" ? "SCRIPT" : "FILE"}</span>}
          triggerClassName="!h-8 rounded-sm px-3"
          contentClassName="space-y-3 pt-3"
        >
          {form.sqlSource === "SCRIPT" ? (
            <div className="overflow-hidden rounded-md border border-[var(--yak-components-panel-border)] bg-white">
              <div className="h-[420px] min-h-[280px]">
                <SqlCodeEditor
                  value={form.sql}
                  readOnly={readOnly}
                  onChange={(value) => updateField("sql", value)}
                />
              </div>
              <div className="flex h-6 items-center justify-between border-t border-[#f0f1f3] px-3 text-[10px] text-[#98a1ad]">
                <span>行数：{Math.max(1, form.sql.split("\n").length)}　字符：{form.sql.length}</span>
                <span>SQL · UTF-8 · LF</span>
              </div>
            </div>
          ) : (
            <div className="space-y-2">
              <SqlField label="SQL 资源文件" required align="start">
                <div className="space-y-2">
                  <TreeSelect
                    items={currentSqlResourceTree}
                    value={form.sqlResource || null}
                    onValueChange={(value) => updateField("sqlResource", value ?? "")}
                    size="small"
                    variant="outlined"
                    placeholder="请选择 SQL 资源文件"
                    searchPlaceholder="搜索 SQL 文件名称或路径"
                    emptyText={resourceLoading ? "正在加载资源..." : "没有找到 SQL 文件"}
                    ariaLabel="SQL 资源文件"
                    clearable
                    disabled={readOnly}
                    className="w-full"
                  />
                  {resourceError ? (
                    <button
                      type="button"
                      onClick={onRefreshResources}
                      className="text-[11px] text-[#315efb] hover:underline"
                    >
                      资源列表加载失败，点击重试
                    </button>
                  ) : null}
                </div>
              </SqlField>
              <p className="text-[11px] leading-5 text-[var(--yak-components-muted-text)]">
                文件模式只保存资源路径，任务执行时由后端读取 SQL 文件内容。
              </p>
            </div>
          )}
        </CollapseSection>

        <CollapseSection
          id="sql-execution"
          title="执行配置"
          triggerClassName="!h-8 rounded-sm px-3"
          contentClassName="space-y-3 pt-3"
        >
          <SqlStatementsSection
            title="前置 SQL"
            statements={form.preStatements}
            disabled={readOnly}
            onChange={(value) => updateField("preStatements", value)}
          />
          <SqlStatementsSection
            title="后置 SQL"
            statements={form.postStatements}
            disabled={readOnly}
            onChange={(value) => updateField("postStatements", value)}
          />
          <div className="grid grid-cols-2 gap-3 max-md:grid-cols-1">
            <SqlField label="最大返回行数">
              <Input
                size="small"
                variant="outlined"
                type="number"
                min={0}
                step={1}
                value={form.limit}
                disabled={readOnly}
                onChange={(event) => updateField("limit", event.target.value)}
                aria-label="最大返回行数"
                className="w-full"
              />
            </SqlField>
            <SqlField label="日志展示行数">
              <Input
                size="small"
                variant="outlined"
                type="number"
                min={1}
                step={1}
                value={form.displayRows}
                disabled={readOnly}
                onChange={(event) => updateField("displayRows", event.target.value)}
                aria-label="日志展示行数"
                className="w-full"
              />
            </SqlField>
          </div>
          <p className="text-[11px] leading-5 text-[var(--yak-components-muted-text)]">
            最大返回行数填写 0 表示使用后端默认上限 10000；日志展示行数只控制执行日志打印的结果行数。
          </p>
        </CollapseSection>

        <CollapseSection
          id="sql-result-notification"
          title="结果通知"
          triggerClassName="!h-8 rounded-sm px-3"
          contentClassName="space-y-3 pt-3"
        >
          <SqlField label="发送查询结果通知">
            <div className="flex min-h-8 items-center gap-2">
              <Switch
                size="small"
                checked={form.sendEmail}
                disabled={readOnly}
                onCheckedChange={(checked) => {
                  if (checked && form.sqlType !== "0") {
                    toast.warning("结果通知仅支持查询 SQL");
                    return;
                  }
                  updateField("sendEmail", checked);
                }}
              />
              <span className="text-xs text-[var(--yak-components-muted-text)]">
                将查询结果发送到告警组
              </span>
            </div>
          </SqlField>

          {form.sendEmail && form.sqlType === "0" ? (
            <>
              <SqlField label="告警组" required>
                <div className="space-y-1.5">
                  <Select<string>
                    size="small"
                    items={Object.fromEntries(alertGroups.map((group) => [String(group.id), group.groupName]))}
                    value={form.groupId !== "0" ? form.groupId : null}
                    disabled={readOnly || alertGroupsLoading || alertGroups.length === 0}
                    onValueChange={(value) => {
                      if (value) updateField("groupId", value);
                    }}
                  >
                    <SelectTrigger variant="outlined" className="w-full max-w-[480px]" aria-label="SQL 结果通知告警组">
                      <SelectValue placeholder={alertGroupsLoading ? "正在加载告警组..." : "请选择告警组"} />
                    </SelectTrigger>
                    <SelectContent emptyContent={alertGroups.length === 0 ? "暂无可用告警组" : undefined}>
                      {alertGroups.map((group) => (
                        <SelectItem key={group.id} value={String(group.id)}>
                          <SelectItemText>{group.groupName}</SelectItemText>
                          <SelectItemIndicator />
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {alertGroupsError ? (
                    <button
                      type="button"
                      onClick={onRefreshAlertGroups}
                      className="text-[11px] text-[#315efb] hover:underline"
                    >
                      告警组加载失败，点击重试
                    </button>
                  ) : null}
                  {!alertGroupsLoading && !alertGroupsError && alertGroups.length === 0 ? (
                    <a
                      href="/management/alerts?tab=groups"
                      target="_blank"
                      rel="noreferrer"
                      className="inline-block text-[11px] text-[var(--yak-color-primary)] hover:underline"
                    >
                      前往告警中心创建告警组
                    </a>
                  ) : null}
                </div>
              </SqlField>
              <SqlField label="通知标题">
                <Input
                  size="small"
                  variant="outlined"
                  value={form.title}
                  disabled={readOnly}
                  onChange={(event) => updateField("title", event.target.value)}
                  placeholder="留空时使用节点名称 + query result sets"
                  aria-label="SQL 结果通知标题"
                  className="w-full max-w-[480px]"
                />
              </SqlField>
            </>
          ) : form.sendEmail ? (
            <p className="text-[11px] text-[var(--yak-components-field-error)]">
              当前为非查询模式，结果通知不会生效，请切换为查询模式或关闭通知。
            </p>
          ) : null}
        </CollapseSection>
      </div>
    </div>
  );
}

export function SqlTaskEditor({
  workflowCode,
  task,
  taskName,
  isDraft,
  readOnly,
  canRun,
  onApplyTask,
  onDirtyChange,
  onBackToWorkflow,
}: SqlTaskEditorProps) {
  const queryClient = useQueryClient();
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const { projectCode, taskGroups, taskGroupsQuery } = useTaskGroups();

  const initialForm = useMemo(() => parseSqlTaskForm(task.taskParams), [task.taskParams]);
  const initialCommonSettings = useMemo(() => parseTaskCommonSettings(task), [task]);
  const [form, setForm] = useState<SqlTaskForm>(initialForm);
  const [baselineForm, setBaselineForm] = useState<SqlTaskForm>(initialForm);
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

  const dataSourcesQuery = useQuery({
    queryKey: ["data-development", "sql-task-data-sources", currentWorkspace?.id ?? ""],
    queryFn: ({ signal }) => listDataSources({ pageNo: 1, pageSize: 200 }, signal),
    enabled: Boolean(currentWorkspace?.id),
    staleTime: 30_000,
  });
  const dataSources = useMemo(
    () => (dataSourcesQuery.data?.totalList ?? []).filter((item) => item.id && item.dbType !== "SSH"),
    [dataSourcesQuery.data],
  );
  const selectedDataSource = dataSources.find((item) => item.id === form.datasource);
  const effectiveForm = useMemo(
    () => ({
      ...form,
      type: selectedDataSource?.dbType
        ? toSqlTaskDbType(selectedDataSource.dbType)
        : form.type,
    }),
    [form, selectedDataSource],
  );

  const resourcesQuery = useQuery({
    queryKey: ["data-development", "sql-task-resources"],
    queryFn: ({ signal }) => listWorkflowResources(signal),
    enabled: form.sqlSource === "FILE",
    staleTime: 60_000,
  });
  const resourceTree = useMemo(
    () => buildResourceTreeOptions(resourcesQuery.data ?? []),
    [resourcesQuery.data],
  );
  const sqlResourceTree = useMemo(
    () => filterSqlResourceTree(resourceTree),
    [resourceTree],
  );

  const alertGroupsQuery = useQuery({
    queryKey: ["data-development", "sql-task-alert-groups", currentUser?.id ?? ""],
    queryFn: listAlertGroups,
    enabled: Boolean(currentUser?.id && form.sendEmail),
    staleTime: 30_000,
  });
  const alertGroups = alertGroupsQuery.data ?? [];

  const updateField: SqlTaskUpdateField = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const isDirty =
    JSON.stringify(form) !== JSON.stringify(baselineForm) ||
    JSON.stringify(commonSettings) !== JSON.stringify(baselineCommonSettings);

  useEffect(() => {
    onDirtyChange(isDirty);
  }, [isDirty, onDirtyChange]);

  useEffect(() => () => onDirtyChange(false), [onDirtyChange]);

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
        taskType: "SQL",
        taskParams: buildSqlTaskParams(effectiveForm, currentTask.taskParams),
      },
    }, null, 2);
  }, [commonSettings, currentTask, effectiveForm, workflowCode]);

  const validateBeforeApply = (): boolean => {
    const validationError = validateSqlTaskForm(effectiveForm);
    if (validationError) {
      toast.warning(validationError);
      return false;
    }

    if (form.sendEmail && form.sqlType === "0") {
      if (alertGroupsQuery.isPending || alertGroupsQuery.isFetching) {
        toast.warning("告警组正在加载，请稍后再保存");
        return false;
      }
      if (alertGroupsQuery.isError) {
        toast.warning("告警组加载失败，请刷新后重试");
        return false;
      }
      if (!alertGroups.some((group) => group.id === Number(form.groupId))) {
        toast.warning("请选择有效的告警组");
        return false;
      }
    }

    return true;
  };

  const handleSave = async (): Promise<boolean> => {
    if (readOnly) return false;
    if (!validateBeforeApply()) return false;

    let commonPatch;
    try {
      commonPatch = normalizeTaskCommonSettings(commonSettings);
    } catch (error) {
      toast.warning(error instanceof Error ? error.message : "请检查 SQL 节点运行配置");
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
        taskType: "SQL",
        taskParams: buildSqlTaskParams(effectiveForm, currentTask.taskParams),
      };
      if (!onApplyTask(nextTask)) return false;
      setCurrentTask(nextTask);
      setForm(effectiveForm);
      setBaselineForm(effectiveForm);
      setBaselineCommonSettings(commonSettings);
      toast.success("SQL 节点配置已应用，请保存工作流");
      return true;
    } catch {
      toast.error("保存 SQL 节点配置失败，请稍后重试");
      return false;
    } finally {
      setSaving(false);
    }
  };

  const handleRun = async () => {
    if (isDraft || !canRun) {
      toast.warning("请先保存并发布工作流，再运行节点");
      return;
    }
    if (isDirty) {
      toast.warning("当前节点有未保存修改，请先保存工作流");
      return;
    }
    if (!validateBeforeApply()) return;

    setRunning(true);
    try {
      const result = await startWorkflowInstance(String(workflowCode), {
        startNodeList: currentTask.code,
        taskDependType: "TASK_ONLY",
      });
      setLastWorkflowInstanceId(result.workflowInstanceId);
      toast.success("SQL 节点单节点运行已提交", {
        description: "工作流实例 ID：" + result.workflowInstanceId,
      });
    } catch (error) {
      toast.error("SQL 节点运行失败", {
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
      toast.success("SQL 节点停止请求已提交");
      setLastWorkflowInstanceId(null);
    } catch (error) {
      toast.error("停止 SQL 节点失败", {
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
      const refreshTasks: Array<Promise<unknown>> = [dataSourcesQuery.refetch()];
      if (form.sqlSource === "FILE") refreshTasks.push(resourcesQuery.refetch());
      if (form.sendEmail) refreshTasks.push(alertGroupsQuery.refetch());

      await Promise.all(refreshTasks);
      const dag = await getWorkflowDefinition(workflowCode);
      const latestTask = dag.taskDefinitionList.find((item) => item.code === currentTask.code);
      if (!latestTask) throw new Error("节点不存在或已被删除");
      if (!onApplyTask(latestTask)) return;

      const nextForm = parseSqlTaskForm(latestTask.taskParams);
      const nextCommonSettings = parseTaskCommonSettings(latestTask);
      setCurrentTask(latestTask);
      setForm(nextForm);
      setBaselineForm(nextForm);
      setCommonSettings(nextCommonSettings);
      setBaselineCommonSettings(nextCommonSettings);
      await queryClient.invalidateQueries({ queryKey: ["data-development", "workflow-definition"] });
      toast.success("SQL 节点配置已刷新");
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
      taskType="SQL"
      workflowCode={workflowCode}
      activeView={activeView}
      onActiveViewChange={setActiveView}
      isDirty={isDirty}
      saving={saving}
      running={running}
      stopping={stopping}
      refreshing={
        refreshing ||
        dataSourcesQuery.isFetching ||
        resourcesQuery.isFetching ||
        alertGroupsQuery.isFetching
      }
      lastWorkflowInstanceId={lastWorkflowInstanceId}
      canRun={canRun}
      readOnly={readOnly}
      onRun={() => void handleRun()}
      onStop={() => void handleStop()}
      onSave={() => void handleSave()}
      onRefresh={() => void handleRefresh()}
      onBack={onBackToWorkflow}
      resourceParams={
        <NodeResourceParamsPanel
          idPrefix={"sql-" + currentTask.code}
          localParams={form.localParams}
          onLocalParamsChange={(value) => updateField("localParams", value)}
          disabled={readOnly}
        />
      }
      runConfig={
        <TaskCommonSettingsSection
          id="sql-run-config"
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
        <SqlTaskWorkspace
          form={effectiveForm}
          readOnly={readOnly}
          dataSources={dataSources}
          dataSourcesLoading={dataSourcesQuery.isPending || dataSourcesQuery.isFetching}
          dataSourcesError={dataSourcesQuery.isError}
          onRefreshDataSources={() => void dataSourcesQuery.refetch()}
          resourceTree={sqlResourceTree}
          resourceLoading={resourcesQuery.isPending || resourcesQuery.isFetching}
          resourceError={resourcesQuery.isError}
          onRefreshResources={() => void resourcesQuery.refetch()}
          alertGroups={alertGroups}
          alertGroupsLoading={alertGroupsQuery.isPending || alertGroupsQuery.isFetching}
          alertGroupsError={alertGroupsQuery.isError}
          onRefreshAlertGroups={() => void alertGroupsQuery.refetch()}
          updateField={updateField}
        />
      )}
    </NodeEditorFrame>
  );
}
