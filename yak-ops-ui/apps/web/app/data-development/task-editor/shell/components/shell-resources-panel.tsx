import {
  Button,
  TreeSelect,
  type TreeSelectOption,
} from "@yak-ops/yak-ui";
import { FolderOpen, Plus, RefreshCw, Trash2 } from "lucide-react";

import type {
  ShellTaskForm,
  ShellTaskUpdateField,
} from "../types";

export function ShellResourcesPanel({
  form,
  readOnly,
  updateField,
  resourceTree,
  resourceLoading,
  resourceError,
  onRetryResources,
}: {
  form: ShellTaskForm;
  readOnly: boolean;
  updateField: ShellTaskUpdateField;
  resourceTree: TreeSelectOption[];
  resourceLoading: boolean;
  resourceError: boolean;
  onRetryResources: () => void;
}) {
  return (
    <>
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
    </>
  );
}
