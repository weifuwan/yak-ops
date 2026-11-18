import {
  Button, CollapseSection, Input, PasswordInput, Select, SelectContent, SelectItem,
  SelectItemIndicator, SelectItemText, SelectTrigger, SelectValue, Switch, TreeSelect,
  type TreeSelectOption,
} from "@yak-ops/yak-ui";
import { FolderOpen, Plus, RefreshCw, Trash2 } from "lucide-react";
import type { ReactNode } from "react";

import { NODE_LOCAL_PARAM_TYPE_OPTIONS } from "./constants";
import type { NodeLocalParam } from "./types";

function LocalParamValueInput({
  item, index, disabled, onChange,
}: {
  item: NodeLocalParam;
  index: number;
  disabled: boolean;
  onChange: (patch: Partial<NodeLocalParam>) => void;
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
          <SelectItem value="true"><SelectItemText>true</SelectItemText><SelectItemIndicator /></SelectItem>
          <SelectItem value="false"><SelectItemText>false</SelectItemText><SelectItemIndicator /></SelectItem>
        </SelectContent>
      </Select>
    );
  }
  const inputType = ["INTEGER", "LONG", "FLOAT", "DOUBLE"].includes(item.type)
    ? "number" : item.type === "DATE" ? "date" : item.type === "TIME" ? "time" : "text";
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
        size="small" variant="outlined" value={item.value} disabled={disabled}
        onChange={(event) => onChange({ value: event.target.value })}
        placeholder={placeholder} aria-label={"敏感参数值 " + (index + 1)}
      />
    );
  }
  return (
    <Input
      size="small" variant="outlined" type={inputType}
      step={["INTEGER", "LONG", "TIME"].includes(item.type) ? 1 : "any"}
      value={item.value} disabled={disabled}
      onChange={(event) => onChange({ value: event.target.value })}
      placeholder={placeholder} aria-label={"参数值 " + (index + 1)}
    />
  );
}

export function NodeResourceParamsPanel({
  idPrefix = "node",
  resourceList,
  onResourceListChange,
  localParams,
  onLocalParamsChange,
  resourceTree = [],
  resourceLoading = false,
  resourceError = false,
  onRetryResources,
  disabled = false,
  resourceExtra,
}: {
  idPrefix?: string;
  resourceList?: string[];
  onResourceListChange?: (resourceList: string[]) => void;
  localParams: NodeLocalParam[];
  onLocalParamsChange: (localParams: NodeLocalParam[]) => void;
  resourceTree?: TreeSelectOption[];
  resourceLoading?: boolean;
  resourceError?: boolean;
  onRetryResources?: () => void;
  disabled?: boolean;
  resourceExtra?: ReactNode;
}) {
  const resources = resourceList ?? [];
  const hasResources = resourceList !== undefined && Boolean(onResourceListChange);
  const updateLocalParam = (index: number, patch: Partial<NodeLocalParam>) => {
    onLocalParamsChange(localParams.map((item, i) => i === index ? { ...item, ...patch } : item));
  };

  return (
    <div className="space-y-3">
      {hasResources ? (
        <CollapseSection
          id={idPrefix + "-resource-list"}
          title="依赖资源"
          extra={<span className="text-xs font-normal text-[var(--yak-components-muted-text)]">{resources.filter((item) => item.trim()).length} 项</span>}
          triggerClassName="!h-8 rounded-sm px-3"
          contentClassName="space-y-3 pt-3"
        >
          <div className="space-y-3">
            {resourceError ? (
              <div className="flex items-center justify-between rounded-md border border-[#f2d6d6] bg-[#fffafa] px-3 py-2">
                <span className="text-xs text-[#b54747]">资源列表加载失败</span>
                {onRetryResources ? <Button type="button" size="sm" variant="ghost" onClick={onRetryResources}><RefreshCw size={12} />重试</Button> : null}
              </div>
            ) : null}
            {resources.length ? (
              <div className="space-y-2">
                {resources.map((resource, index) => (
                  <div key={index} className="flex items-center gap-2">
                    <TreeSelect
                      items={resourceTree}
                      value={resource || null}
                      onValueChange={(value) => onResourceListChange?.(resources.map((item, i) => i === index ? value ?? "" : item))}
                      size="small" variant="outlined" placeholder="请选择依赖资源"
                      searchPlaceholder="搜索文件名称或路径"
                      emptyText={resourceLoading ? "正在加载资源..." : "暂无匹配的资源"}
                      ariaLabel={"依赖资源 " + (index + 1)} clearable disabled={disabled}
                      className="min-w-0 flex-1"
                    />
                    <Button type="button" size="sm" variant="ghost" isIconOnly isDisabled={disabled}
                      aria-label="移除依赖资源"
                      onClick={() => onResourceListChange?.(resources.filter((_item, i) => i !== index))}
                      className="shrink-0 text-[var(--yak-components-muted-text)] hover:text-[var(--yak-components-field-error)]">
                      <Trash2 size={14} />
                    </Button>
                  </div>
                ))}
              </div>
            ) : (
              <div className="flex min-h-[88px] flex-col items-center justify-center gap-1 rounded-md border border-dashed border-[#dfe4eb] bg-[#fcfdff] text-center">
                <FolderOpen size={18} className="text-[#a4adba]" />
                <span className="text-xs text-[#687385]">暂无依赖资源</span>
              </div>
            )}
            <div className="flex flex-wrap items-center justify-between gap-2 border-t border-[var(--yak-components-panel-border)] pt-3">
              {resourceExtra ?? <span />}
              <Button type="button" size="sm" variant="secondary" isDisabled={disabled}
                onClick={() => onResourceListChange?.([...resources, ""])}>
                <Plus size={13} />添加依赖资源
              </Button>
            </div>
          </div>
        </CollapseSection>
      ) : null}
      <CollapseSection
        id={idPrefix + "-local-params"}
        title="本地参数"
        extra={<span className="text-xs font-normal text-[var(--yak-components-muted-text)]">{localParams.length} 项</span>}
        triggerClassName="!h-8 rounded-sm px-3"
        contentClassName="space-y-3 pt-3"
      >
        <div className="space-y-3">
          {!localParams.length ? (
            <div className="flex min-h-[88px] flex-col items-center justify-center gap-1 rounded-md border border-dashed border-[#dfe4eb] bg-[#fcfdff] text-center">
              <span className="text-xs text-[#687385]">暂无本地参数</span>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <div className="min-w-[760px]">
                <div className="grid grid-cols-[minmax(0,1.2fr)_112px_140px_minmax(0,1.5fr)_64px_40px] items-center gap-2 border-b border-[#edf0f3] bg-[#f8f9fb] px-2 py-2 text-[11px] text-[#7c8796]">
                  <span>参数名称</span><span>方向</span><span>类型</span><span>参数值</span><span>敏感</span><span />
                </div>
                <div className="space-y-2 pt-2">
                  {localParams.map((item, index) => (
                    <div key={index} className="grid grid-cols-[minmax(0,1.2fr)_112px_140px_minmax(0,1.5fr)_64px_40px] items-center gap-2">
                      <Input size="small" variant="outlined" value={item.prop} disabled={disabled}
                        onChange={(event) => updateLocalParam(index, { prop: event.target.value })}
                        placeholder="参数名称" aria-label={"参数名称 " + (index + 1)} />
                      <Select<string> size="small" items={{ IN: "输入 IN", OUT: "输出 OUT" }} value={item.direct}
                        disabled={disabled} onValueChange={(value) => {
                          if (value === "IN" || value === "OUT") updateLocalParam(index, { direct: value });
                        }}>
                        <SelectTrigger variant="outlined" className="w-full" aria-label={"参数方向 " + (index + 1)}><SelectValue placeholder="选择方向" /></SelectTrigger>
                        <SelectContent>
                          <SelectItem value="IN"><SelectItemText>输入 IN</SelectItemText><SelectItemIndicator /></SelectItem>
                          <SelectItem value="OUT"><SelectItemText>输出 OUT</SelectItemText><SelectItemIndicator /></SelectItem>
                        </SelectContent>
                      </Select>
                      <Select<string> size="small" items={Object.fromEntries(NODE_LOCAL_PARAM_TYPE_OPTIONS.map((item) => [item.value, item.label]))}
                        value={item.type} disabled={disabled} onValueChange={(value) => { if (value) updateLocalParam(index, { type: value }); }}>
                        <SelectTrigger variant="outlined" className="w-full" aria-label={"参数类型 " + (index + 1)}><SelectValue placeholder="选择类型" /></SelectTrigger>
                        <SelectContent>{NODE_LOCAL_PARAM_TYPE_OPTIONS.map((option) => (
                          <SelectItem key={option.value} value={option.value}><SelectItemText>{option.label}</SelectItemText><SelectItemIndicator /></SelectItem>
                        ))}</SelectContent>
                      </Select>
                      <LocalParamValueInput item={item} index={index} disabled={disabled}
                        onChange={(patch) => updateLocalParam(index, patch)} />
                      <div className="flex items-center justify-center">
                        <Switch size="small" checked={item.sensitive} disabled={disabled}
                          aria-label={"敏感参数 " + (item.prop || index + 1)}
                          onCheckedChange={(checked) => updateLocalParam(index, { sensitive: checked })} />
                      </div>
                      <Button type="button" size="sm" variant="ghost" isIconOnly isDisabled={disabled}
                        aria-label="移除本地参数"
                        onClick={() => onLocalParamsChange(localParams.filter((_item, i) => i !== index))}
                        className="text-[var(--yak-components-muted-text)] hover:text-[var(--yak-components-field-error)]">
                        <Trash2 size={14} />
                      </Button>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
          <div className="flex justify-end border-t border-[var(--yak-components-panel-border)] pt-3">
            <Button type="button" size="sm" variant="secondary" isDisabled={disabled}
              onClick={() => onLocalParamsChange([...localParams, { prop: "", direct: "IN", type: "VARCHAR", value: "", sensitive: false }])}>
              <Plus size={13} />添加参数
            </Button>
          </div>
        </div>
      </CollapseSection>
    </div>
  );
}
