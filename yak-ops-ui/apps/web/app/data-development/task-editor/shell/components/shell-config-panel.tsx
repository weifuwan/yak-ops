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
  Switch,
  TreeSelect,
  type TreeSelectOption,
} from "@yak-ops/yak-ui";
import { FolderOpen, Plus, RefreshCw, Trash2 } from "lucide-react";

import { createShellLocalParam, SHELL_PARAM_TYPE_OPTIONS } from "../constants";
import type {
  ShellConfigTab,
  ShellLocalParam,
  ShellTaskForm,
  ShellTaskUpdateField,
} from "../types";
import { LocalParamValueInput } from "./local-param-value-input";

export function ShellConfigPanel({
  form,
  readOnly,
  activeTab,
  onActiveTabChange,
  updateField,
  updateLocalParam,
  resourceTree,
  resourceLoading,
  resourceError,
  onRetryResources,
}: {
  form: ShellTaskForm;
  readOnly: boolean;
  activeTab: ShellConfigTab;
  onActiveTabChange: (tab: ShellConfigTab) => void;
  updateField: ShellTaskUpdateField;
  updateLocalParam: (index: number, patch: Partial<ShellLocalParam>) => void;
  resourceTree: TreeSelectOption[];
  resourceLoading: boolean;
  resourceError: boolean;
  onRetryResources: () => void;
}) {
  return (
    <>
<section className="flex h-[286px] max-h-[38vh] min-h-[220px] shrink-0 flex-col border-t border-[#e5e7eb] bg-white">
            <nav className="flex h-9 shrink-0 items-center gap-1 border-b border-[#edf0f3] bg-[#fbfcfe] px-3">
              <button
                type="button"
                aria-pressed={activeTab === "parameters"}
                onClick={() => onActiveTabChange("parameters")}
                className={[
                  "flex h-full cursor-pointer items-center gap-1.5 border-b-2 px-2 text-xs transition-colors",
                  activeTab === "parameters"
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
                aria-pressed={activeTab === "resources"}
                onClick={() => onActiveTabChange("resources")}
                className={[
                  "flex h-full cursor-pointer items-center gap-1.5 border-b-2 px-2 text-xs transition-colors",
                  activeTab === "resources"
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
              {activeTab === "parameters" ? (
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
                      onClick={() => updateField("localParams", [...form.localParams, createShellLocalParam()])}
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
                                  SHELL_PARAM_TYPE_OPTIONS.map((option) => [
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
                                  {SHELL_PARAM_TYPE_OPTIONS.map((option) => (
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

                  {resourceError ? (
                    <div className="flex items-center justify-between rounded-md border border-[#f2d6d6] bg-[#fffafa] px-3 py-2">
                      <span className="text-xs text-[#b54747]">资源列表加载失败</span>
                      <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        onClick={() => void onRetryResources()}
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
                              resourceLoading
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
    </>
  );
}
