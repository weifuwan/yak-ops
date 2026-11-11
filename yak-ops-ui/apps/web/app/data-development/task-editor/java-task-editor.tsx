import { useEffect, useMemo, useState } from "react";
import {
  Braces,
  Cpu,
  FileCode2,
  Layers3,
  Plus,
  RefreshCw,
  Save,
  Trash2,
} from "lucide-react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";

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
  name?: string;
  fullName?: string;
  isDirctory?: boolean;
  dirctory?: boolean;
  isDirectory?: boolean;
  children?: ResourceNode[];
};

const inputClassName =
  "h-9 w-full rounded-md border border-[#dce1e8] bg-white px-3 text-xs text-[#303846] outline-none transition-colors placeholder:text-[#a0a8b4] focus:border-[#9bb5ff] focus:ring-2 focus:ring-[#315efb]/10";

const textareaClassName =
  "min-h-[76px] w-full resize-y rounded-md border border-[#dce1e8] bg-white px-3 py-2 text-xs leading-5 text-[#303846] outline-none transition-colors placeholder:text-[#a0a8b4] focus:border-[#9bb5ff] focus:ring-2 focus:ring-[#315efb]/10";

const sectionClassName =
  "rounded-lg border border-[#e5e8ed] bg-white p-4";

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

function Field({
  label,
  description,
  children,
}: {
  label: string;
  description?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="min-w-0">
      <label className="mb-1.5 block text-xs font-medium text-[#475467]">
        {label}
      </label>
      {children}
      {description ? (
        <p className="mt-1.5 text-[11px] leading-4 text-[#98a1ad]">
          {description}
        </p>
      ) : null}
    </div>
  );
}

export function JavaTaskEditor({
  workflowCode,
  task,
}: {
  workflowCode: number;
  task?: WorkflowTaskRecord;
}) {
  const queryClient = useQueryClient();
  const initialForm = useMemo(
    () => parseJavaTaskForm(task?.taskParams),
    [task?.taskParams],
  );
  const [form, setForm] = useState<JavaTaskForm>(initialForm);
  const [activeView, setActiveView] = useState<"form" | "json">("form");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setForm(initialForm);
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

  const updateField = <K extends keyof JavaTaskForm>(
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

  const isDirty = JSON.stringify(form) !== JSON.stringify(initialForm);

  const handleSave = async () => {
    if (!task) return;
    if (!form.mainJar.trim()) {
      toast.warning("请选择主程序 JAR", {
        description: "Java 节点必须配置 mainJar 才能通过后端参数校验。",
      });
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
      toast.success("Java 节点配置已保存");
    } catch {
      toast.error("保存 Java 节点配置失败，请稍后重试");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex h-10 shrink-0 items-center justify-between border-b border-[#e8ebef] bg-white px-4">
        <div className="flex items-center gap-1 rounded-md bg-[#f3f5f8] p-0.5">
          <button
            type="button"
            aria-pressed={activeView === "form"}
            onClick={() => setActiveView("form")}
            className={[
              "flex h-7 cursor-pointer items-center gap-1.5 rounded px-3 text-xs transition-colors",
              activeView === "form"
                ? "bg-white font-medium text-[#315efb] shadow-sm"
                : "text-[#7b8492] hover:text-[#303846]",
            ].join(" ")}
          >
            <FileCode2 size={13} />
            配置
          </button>
          <button
            type="button"
            aria-pressed={activeView === "json"}
            onClick={() => setActiveView("json")}
            className={[
              "flex h-7 cursor-pointer items-center gap-1.5 rounded px-3 text-xs transition-colors",
              activeView === "json"
                ? "bg-white font-medium text-[#315efb] shadow-sm"
                : "text-[#7b8492] hover:text-[#303846]",
            ].join(" ")}
          >
            <Braces size={13} />
            JSON
          </button>
        </div>

        <div className="flex items-center gap-2 text-[11px] text-[#98a1ad]">
          {resourceQuery.isFetching ? (
            <RefreshCw size={12} className="animate-spin" />
          ) : null}
          {task ? (
            <span>{isDirty ? "有未保存的修改" : "配置已加载"}</span>
          ) : (
            <span className="rounded bg-[#fff7ed] px-2 py-1 text-[#c2410c]">
              草稿节点 · 仅预览
            </span>
          )}
        </div>
      </div>

      {activeView === "json" ? (
        <div className="min-h-0 flex-1 overflow-auto bg-[#f7f8fa] p-4">
          <pre className="mx-auto min-w-max max-w-[1160px] rounded-md border border-[#e5e8ed] bg-white p-4 font-mono text-xs leading-5 text-[#303846]">
            {jsonText}
          </pre>
        </div>
      ) : (
        <div className="min-h-0 flex-1 overflow-y-auto bg-[#f7f8fa] p-4">
          <div className="mx-auto max-w-[1160px] space-y-3">
            <div className="flex flex-wrap items-start justify-between gap-2 px-1 pb-1">
              <div>
                <h3 className="text-sm font-semibold text-[#303846]">
                  Java 运行配置
                </h3>
                <p className="mt-1 text-xs text-[#8b95a3]">
                  配置 JAR 入口、JVM 启动参数以及程序运行时依赖。
                </p>
              </div>
              <span className="rounded bg-[#eef3ff] px-2 py-1 text-[11px] text-[#315efb]">
                JAVA TASK
              </span>
            </div>

            <section className={sectionClassName}>
              <div className="mb-3 flex items-center gap-2">
                <Cpu size={15} className="text-[#64748b]" />
                <h4 className="text-xs font-semibold text-[#303846]">
                  运行方式
                </h4>
              </div>
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                <button
                  type="button"
                  aria-pressed={form.runType === "FAT_JAR"}
                  onClick={() => updateField("runType", "FAT_JAR")}
                  className={[
                    "rounded-md border p-3 text-left transition-colors",
                    form.runType === "FAT_JAR"
                      ? "border-[#9bb5ff] bg-[#f5f8ff]"
                      : "border-[#e5e8ed] bg-white hover:border-[#cbd5e1]",
                  ].join(" ")}
                >
                  <span className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-[#303846]">
                      FAT_JAR
                    </span>
                    <span
                      className={[
                        "size-3 rounded-full border",
                        form.runType === "FAT_JAR"
                          ? "border-[4px] border-[#315efb]"
                          : "border-[#cbd2dc]",
                      ].join(" ")}
                    />
                  </span>
                  <span className="mt-1 block text-[11px] leading-4 text-[#8b95a3]">
                    可执行 JAR，通常已包含程序运行所需依赖。
                  </span>
                </button>

                <button
                  type="button"
                  aria-pressed={form.runType === "NORMAL_JAR"}
                  onClick={() => updateField("runType", "NORMAL_JAR")}
                  className={[
                    "rounded-md border p-3 text-left transition-colors",
                    form.runType === "NORMAL_JAR"
                      ? "border-[#9bb5ff] bg-[#f5f8ff]"
                      : "border-[#e5e8ed] bg-white hover:border-[#cbd5e1]",
                  ].join(" ")}
                >
                  <span className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-[#303846]">
                      NORMAL_JAR
                    </span>
                    <span
                      className={[
                        "size-3 rounded-full border",
                        form.runType === "NORMAL_JAR"
                          ? "border-[4px] border-[#315efb]"
                          : "border-[#cbd2dc]",
                      ].join(" ")}
                    />
                  </span>
                  <span className="mt-1 block text-[11px] leading-4 text-[#8b95a3]">
                    普通 JAR，通过主类和外部依赖构建运行入口。
                  </span>
                </button>
              </div>
            </section>

            <section className={sectionClassName}>
              <div className="mb-3 flex items-center gap-2">
                <FileCode2 size={15} className="text-[#64748b]" />
                <h4 className="text-xs font-semibold text-[#303846]">
                  程序入口
                </h4>
              </div>
              <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
                <Field
                  label="主程序 JAR"
                  description="必填。优先从资源中心选择已上传的 JAR 文件，也可以输入资源完整路径。"
                >
                  <input
                    list="java-task-jar-options"
                    value={form.mainJar}
                    onChange={(event) =>
                      updateField("mainJar", event.target.value)
                    }
                    placeholder="选择或输入 JAR 资源路径"
                    className={inputClassName}
                  />
                  <datalist id="java-task-jar-options">
                    {jarFiles.map((path) => (
                      <option key={path} value={path} />
                    ))}
                  </datalist>
                  {resourceQuery.isError ? (
                    <p className="mt-1 text-[11px] text-[#c2410c]">
                      资源目录加载失败，可以先手动填写资源路径。
                    </p>
                  ) : resourceQuery.isSuccess && jarFiles.length === 0 ? (
                    <p className="mt-1 text-[11px] text-[#98a1ad]">
                      资源中心暂无 JAR 文件，请先上传程序包。
                    </p>
                  ) : null}
                </Field>

                {form.runType === "NORMAL_JAR" ? (
                  <Field
                    label="主类名称"
                    description="填写包含 package 的完整类名；留空时尝试从 JAR 中提取主类。"
                  >
                    <input
                      value={form.mainClass}
                      onChange={(event) =>
                        updateField("mainClass", event.target.value)
                      }
                      placeholder="例如：com.example.DemoApplication"
                      className={inputClassName}
                    />
                  </Field>
                ) : (
                  <div className="flex min-h-[72px] items-center rounded-md bg-[#f8fafc] px-3 text-[11px] leading-5 text-[#8792a2]">
                    FAT_JAR 模式通过 java -jar 启动，不需要单独指定主类。
                  </div>
                )}
              </div>
            </section>

            <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
              <section className={sectionClassName}>
                <div className="mb-3 flex items-center gap-2">
                  <Braces size={15} className="text-[#64748b]" />
                  <h4 className="text-xs font-semibold text-[#303846]">
                    主程序参数
                  </h4>
                </div>
                <Field
                  label="Main 参数"
                  description="传递给 Java main(String[] args) 的参数。"
                >
                  <textarea
                    rows={3}
                    value={form.mainArgs}
                    onChange={(event) =>
                      updateField("mainArgs", event.target.value)
                    }
                    placeholder="例如：--date 2026-11-11 --mode batch"
                    className={textareaClassName}
                  />
                </Field>
              </section>

              <section className={sectionClassName}>
                <div className="mb-3 flex items-center gap-2">
                  <Cpu size={15} className="text-[#64748b]" />
                  <h4 className="text-xs font-semibold text-[#303846]">
                    JVM 参数
                  </h4>
                </div>
                <Field
                  label="JVM 启动参数"
                  description="设置堆内存、系统属性等 JVM 参数。"
                >
                  <textarea
                    rows={3}
                    value={form.jvmArgs}
                    onChange={(event) =>
                      updateField("jvmArgs", event.target.value)
                    }
                    placeholder="-Xms256m -Xmx512m -Dfile.encoding=UTF-8"
                    className={textareaClassName}
                  />
                </Field>
              </section>
            </div>

            <section className={sectionClassName}>
              <div className="mb-3 flex items-center gap-2">
                <Layers3 size={15} className="text-[#64748b]" />
                <h4 className="text-xs font-semibold text-[#303846]">
                  依赖资源
                </h4>
                <span className="text-[11px] text-[#98a1ad]">
                  {form.resourceList.filter((item) => item.trim()).length} 项
                </span>
              </div>
              <div className="space-y-2">
                {form.resourceList.map((resource, index) => (
                  <div key={index} className="flex items-center gap-2">
                    <input
                      list="java-task-resource-options"
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
                      placeholder="依赖资源完整路径"
                      className={inputClassName}
                    />
                    <button
                      type="button"
                      title="移除依赖资源"
                      aria-label="移除依赖资源"
                      onClick={() =>
                        updateField(
                          "resourceList",
                          form.resourceList.filter(
                            (_item, itemIndex) => itemIndex !== index,
                          ),
                        )
                      }
                      className="flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-md border border-[#e5e8ed] text-[#98a1ad] transition-colors hover:border-[#f2c5c5] hover:bg-[#fff7f7] hover:text-[#d14343]"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                ))}
                <datalist id="java-task-resource-options">
                  {resourceFiles.map((path) => (
                    <option key={path} value={path} />
                  ))}
                </datalist>
                <button
                  type="button"
                  onClick={() =>
                    updateField("resourceList", [...form.resourceList, ""])
                  }
                  className="inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-md border border-dashed border-[#cfd6e0] px-3 text-xs text-[#64748b] transition-colors hover:border-[#9bb5ff] hover:bg-[#f7f9ff] hover:text-[#315efb]"
                >
                  <Plus size={13} />
                  添加依赖资源
                </button>
              </div>
              <div className="mt-3 flex items-center gap-2 border-t border-[#eef0f3] pt-3">
                <input
                  id="java-module-path"
                  type="checkbox"
                  checked={form.isModulePath}
                  onChange={(event) =>
                    updateField("isModulePath", event.target.checked)
                  }
                  className="size-3.5 cursor-pointer accent-[#315efb]"
                />
                <label
                  htmlFor="java-module-path"
                  className="cursor-pointer text-xs text-[#475467]"
                >
                  使用 Java Module Path
                </label>
                <span className="text-[11px] text-[#98a1ad]">
                  默认使用 Classpath
                </span>
              </div>
            </section>

            <section className={sectionClassName}>
              <div className="mb-3 flex items-center justify-between gap-2">
                <div>
                  <h4 className="text-xs font-semibold text-[#303846]">
                    本地参数
                  </h4>
                  <p className="mt-1 text-[11px] text-[#98a1ad]">
                    定义当前节点可使用的输入、输出变量。
                  </p>
                </div>
                <button
                  type="button"
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
                  className="inline-flex h-7 cursor-pointer items-center gap-1 rounded-md border border-[#e5e8ed] px-2 text-[11px] text-[#64748b] transition-colors hover:border-[#9bb5ff] hover:text-[#315efb]"
                >
                  <Plus size={12} />
                  添加参数
                </button>
              </div>

              {form.localParams.length === 0 ? (
                <div className="rounded-md border border-dashed border-[#e2e7ee] bg-[#fafbfc] px-3 py-5 text-center text-xs text-[#98a1ad]">
                  暂无本地参数
                </div>
              ) : (
                <div className="space-y-2">
                  {form.localParams.map((item, index) => (
                    <div
                      key={index}
                      className="grid grid-cols-1 items-center gap-2 sm:grid-cols-[minmax(0,1fr)_100px_minmax(0,1fr)_36px]"
                    >
                      <input
                        value={item.prop}
                        onChange={(event) =>
                          updateLocalParam(index, {
                            prop: event.target.value,
                          })
                        }
                        placeholder="参数名称"
                        aria-label="参数名称"
                        className={inputClassName}
                      />
                      <select
                        value={item.direct}
                        onChange={(event) =>
                          updateLocalParam(index, {
                            direct: event.target.value as "IN" | "OUT",
                          })
                        }
                        aria-label="参数方向"
                        className={inputClassName}
                      >
                        <option value="IN">输入 IN</option>
                        <option value="OUT">输出 OUT</option>
                      </select>
                      <input
                        value={item.value}
                        onChange={(event) =>
                          updateLocalParam(index, {
                            value: event.target.value,
                          })
                        }
                        placeholder="参数值"
                        aria-label="参数值"
                        className={inputClassName}
                      />
                      <button
                        type="button"
                        title="移除本地参数"
                        aria-label="移除本地参数"
                        onClick={() =>
                          updateField(
                            "localParams",
                            form.localParams.filter(
                              (_param, itemIndex) => itemIndex !== index,
                            ),
                          )
                        }
                        className="flex size-9 cursor-pointer items-center justify-center rounded-md border border-[#e5e8ed] text-[#98a1ad] transition-colors hover:border-[#f2c5c5] hover:bg-[#fff7f7] hover:text-[#d14343]"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        </div>
      )}

      <footer className="flex min-h-12 shrink-0 items-center justify-between gap-3 border-t border-[#e5e8ed] bg-white px-4 py-2">
        <div className="min-w-0 text-[11px] text-[#98a1ad]">
          {task
            ? "保存后将更新当前节点的 taskParams。"
            : "草稿节点尚未创建任务定义，当前配置仅用于界面预览。"}
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <button
            type="button"
            onClick={() => setForm(initialForm)}
            disabled={!isDirty || saving}
            className="h-8 cursor-pointer rounded-md border border-[#e1e5eb] px-3 text-xs text-[#667085] transition-colors hover:bg-[#f8fafc] disabled:cursor-not-allowed disabled:opacity-40"
          >
            重置
          </button>
          {task ? (
            <button
              type="button"
              onClick={() => void handleSave()}
              disabled={!isDirty || saving}
              className="inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-md bg-[#315efb] px-3 text-xs font-medium text-white transition-colors hover:bg-[#244fe0] disabled:cursor-not-allowed disabled:opacity-40"
            >
              <Save size={13} />
              {saving ? "保存中…" : "保存配置"}
            </button>
          ) : null}
        </div>
      </footer>
    </div>
  );
}
