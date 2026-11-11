import { useEffect, useId, useMemo, useState, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  CollapseSection,
  Field,
  FieldLabel,
  Input,
  Segmented,
  Textarea,
  toast,
} from "@yak-ops/yak-ui";
import { Plus, RefreshCw, Save, Trash2 } from "lucide-react";

import {
  updateWorkflowTaskDefinition,
  type WorkflowTaskRecord,
} from "@/service/workflow/definition";
import { listWorkflowResources } from "@/service/resources";

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

type ResourceNode = {
  fullName?: string;
  isDirctory?: boolean;
  dirctory?: boolean;
  isDirectory?: boolean;
  children?: ResourceNode[];
};

const runTypeOptions = [
  { value: "FAT_JAR", label: "FAT_JAR" },
  { value: "NORMAL_JAR", label: "NORMAL_JAR" },
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

function flattenResourceFiles(nodes: ResourceNode[]): string[] {
  const paths: string[] = [];

  const visit = (node: ResourceNode) => {
    const children = Array.isArray(node.children) ? node.children : [];
    if (children.length > 0) {
      children.forEach(visit);
      return;
    }

    if (
      node.fullName &&
      !node.isDirctory &&
      !node.dirctory &&
      !node.isDirectory
    ) {
      paths.push(node.fullName);
    }
  };

  nodes.forEach(visit);
  return Array.from(new Set(paths)).sort((a, b) => a.localeCompare(b));
}

function JavaField({
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

const inputClassName = "w-full max-w-[760px]";
const textareaClassName = "w-full max-w-[760px]";

export function JavaTaskEditor({
  workflowCode,
  task,
}: {
  workflowCode: number;
  task?: WorkflowTaskRecord;
}) {
  const queryClient = useQueryClient();
  const resourceId = useId().replace(/:/g, "");
  const jarDatalistId = resourceId + "-jars";
  const resourceDatalistId = resourceId + "-resources";
  const modulePathId = resourceId + "-module-path";
  const initialForm = useMemo(
    () => parseJavaTaskForm(task?.taskParams),
    [task?.taskParams],
  );
  const [form, setForm] = useState<JavaTaskForm>(initialForm);
  const [baselineForm, setBaselineForm] = useState<JavaTaskForm>(initialForm);
  const [activeView, setActiveView] = useState<"form" | "json">("form");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setForm(initialForm);
    setBaselineForm(initialForm);
  }, [initialForm]);

  const resourceQuery = useQuery({
    queryKey: ["data-development", "java-task-resources"],
    queryFn: ({ signal }) => listWorkflowResources(signal),
    staleTime: 60_000,
  });

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

  const jsonText = useMemo(
    () =>
      JSON.stringify(
        {
          taskType: "JAVA",
          taskParams: buildJavaTaskParams(form),
        },
        null,
        2,
      ),
    [form],
  );

  const isDirty = JSON.stringify(form) !== JSON.stringify(baselineForm);

  const handleSave = async () => {
    if (!task) return;
    if (!form.mainJar.trim()) {
      toast.warning("请选择主程序 JAR");
      return;
    }

    setSaving(true);
    try {
      await updateWorkflowTaskDefinition(
        workflowCode,
        task.code,
        buildJavaTaskParams(form),
      );
      await queryClient.invalidateQueries({
        queryKey: ["data-development", "workflow-definition"],
      });
      setBaselineForm(form);
      toast.success("Java 节点配置已保存");
    } catch {
      toast.error("保存 Java 节点配置失败，请稍后重试");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex h-10 shrink-0 items-center justify-between border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-4">
        <div className="flex items-center gap-1 rounded-md bg-[var(--yak-color-hover)] p-0.5">
          <button
            type="button"
            aria-pressed={activeView === "form"}
            onClick={() => setActiveView("form")}
            className={[
              "flex h-7 cursor-pointer items-center gap-1.5 rounded px-3 text-xs transition-colors",
              activeView === "form"
                ? "bg-[var(--yak-components-panel-bg)] font-medium text-[var(--yak-color-primary)] shadow-sm"
                : "text-[var(--yak-components-muted-text)] hover:text-[var(--yak-components-panel-text)]",
            ].join(" ")}
          >
            配置
          </button>
          <button
            type="button"
            aria-pressed={activeView === "json"}
            onClick={() => setActiveView("json")}
            className={[
              "flex h-7 cursor-pointer items-center gap-1.5 rounded px-3 text-xs transition-colors",
              activeView === "json"
                ? "bg-[var(--yak-components-panel-bg)] font-medium text-[var(--yak-color-primary)] shadow-sm"
                : "text-[var(--yak-components-muted-text)] hover:text-[var(--yak-components-panel-text)]",
            ].join(" ")}
          >
            JSON
          </button>
        </div>

        <div className="flex items-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
          {resourceQuery.isFetching ? (
            <RefreshCw size={12} className="animate-spin" />
          ) : null}
          {task ? (isDirty ? "未保存" : "已保存") : "草稿节点"}
        </div>
      </div>

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
              <JavaField label="运行类型">
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
                <>
                  <Input
                    size="small"
                    variant="outlined"
                    list={jarDatalistId}
                    value={form.mainJar}
                    onChange={(event) =>
                      updateField("mainJar", event.target.value)
                    }
                    placeholder="请选择主程序 JAR"
                    className={inputClassName}
                  />
                  <datalist id={jarDatalistId}>
                    {jarFiles.map((path) => (
                      <option key={path} value={path} />
                    ))}
                  </datalist>
                </>
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
              <JavaField label="JVM 参数" align="start">
                <Textarea
                  size="small"
                  variant="outlined"
                  rows={2}
                  value={form.jvmArgs}
                  onValueChange={(value) =>
                    updateField("jvmArgs", String(value ?? ""))
                  }
                  placeholder="请输入 JVM 参数"
                  className={textareaClassName}
                />
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
                        <Input
                          size="small"
                          variant="outlined"
                          list={resourceDatalistId}
                          value={resource}
                          onChange={(event) =>
                            updateField(
                              "resourceList",
                              form.resourceList.map((item, itemIndex) =>
                                itemIndex === index
                                  ? event.target.value
                                  : item,
                              ),
                            )
                          }
                          placeholder="请选择依赖资源"
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

              <datalist id={resourceDatalistId}>
                {resourceFiles.map((path) => (
                  <option key={path} value={path} />
                ))}
              </datalist>

              <div className="flex items-center justify-between border-t border-[var(--yak-components-panel-border)] pt-3">
                <label
                  htmlFor={modulePathId}
                  className="flex cursor-pointer items-center gap-2 text-xs text-[var(--yak-components-field-label)]"
                >
                  <input
                    id={modulePathId}
                    type="checkbox"
                    checked={form.isModulePath}
                    onChange={(event) =>
                      updateField("isModulePath", event.target.checked)
                    }
                    className="size-3.5 cursor-pointer accent-[var(--yak-color-primary)]"
                  />
                  使用 Java Module Path
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
                    <div className="grid grid-cols-[minmax(0,1.2fr)_112px_140px_minmax(0,1.5fr)_40px] items-center gap-2 border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)] px-2 py-2 text-xs text-[var(--yak-components-muted-text)]">
                      <span>参数名称</span>
                      <span>方向</span>
                      <span>类型</span>
                      <span>参数值</span>
                      <span />
                    </div>
                    <div className="space-y-2 pt-2">
                      {form.localParams.map((item, index) => (
                        <div
                          key={index}
                          className="grid grid-cols-[minmax(0,1.2fr)_112px_140px_minmax(0,1.5fr)_40px] items-center gap-2"
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
                          <select
                            value={item.direct}
                            onChange={(event) =>
                              updateLocalParam(index, {
                                direct: event.target.value as "IN" | "OUT",
                              })
                            }
                            aria-label="参数方向"
                            className="h-7 w-full rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-input-border)] bg-[var(--yak-components-input-bg-focus)] px-2 text-xs text-[var(--yak-components-input-text)] outline-none focus:border-[var(--yak-components-input-border-focus)]"
                          >
                            <option value="IN">输入 IN</option>
                            <option value="OUT">输出 OUT</option>
                          </select>
                          <Input
                            size="small"
                            variant="outlined"
                            value={item.type}
                            onChange={(event) =>
                              updateLocalParam(index, {
                                type: event.target.value,
                              })
                            }
                            placeholder="VARCHAR"
                            aria-label="参数类型"
                          />
                          <Input
                            size="small"
                            variant="outlined"
                            value={item.value}
                            onChange={(event) =>
                              updateLocalParam(index, {
                                value: event.target.value,
                              })
                            }
                            placeholder="参数值"
                            aria-label="参数值"
                          />
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

      <footer className="flex min-h-12 shrink-0 items-center justify-end gap-2 border-t border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-4 py-2">
        <Button
          type="button"
          size="sm"
          variant="secondary"
          onClick={() => setForm(baselineForm)}
          isDisabled={!isDirty || saving}
        >
          重置
        </Button>
        {task ? (
          <Button
            type="button"
            size="sm"
            variant="primary"
            onClick={() => void handleSave()}
            isDisabled={!isDirty || saving}
            isPending={saving}
          >
            <Save size={13} />
            保存配置
          </Button>
        ) : null}
      </footer>
    </div>
  );
}
