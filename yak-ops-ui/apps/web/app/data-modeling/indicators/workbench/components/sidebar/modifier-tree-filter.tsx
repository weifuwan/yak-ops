import { useMemo, useState, type KeyboardEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { Globe, Layers } from "lucide-react";
import {
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectSearch,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";

import { businessTypeApi } from "@/service/data-modeling/planning/business-type";
import { dataDomainApi } from "@/service/data-modeling/planning/common-layer/data-domain";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type { ModifierScopeType } from "../../types";

type FilterOption = { value: string; label: string };
type ModifierDwLayer = "PUBLIC_LAYER" | "APPLICATION_LAYER" | "NONE_LAYER";

const DW_LAYER_OPTIONS: Array<{ value: ModifierDwLayer; label: string }> = [
  { value: "PUBLIC_LAYER", label: "公共层" },
  { value: "APPLICATION_LAYER", label: "应用层" },
  { value: "NONE_LAYER", label: "未分层" },
];

const SCOPE_OPTIONS: Array<{ value: ModifierScopeType; label: string }> = [
  { value: "DATA_DOMAIN", label: "数据域" },
  { value: "BUSINESS_TYPE", label: "业务分类" },
];

function ScopeIcon({ scope }: { scope: ModifierScopeType }) {
  const className = "size-[15px] shrink-0 text-[#3a3d51]";
  return scope === "BUSINESS_TYPE" ? <Layers className={className} /> : <Globe className={className} />;
}

type ModifierTreeFilterProps = {
  projectCode: string;
  dwType: string;
  onDwTypeChange: (value: string) => void;
  scopeType: ModifierScopeType;
  onScopeTypeChange: (value: ModifierScopeType) => void;
  scopeValue: string;
  onScopeValueChange: (value: string) => void;
  keyword: string;
  onKeywordChange: (value: string) => void;
};

export function ModifierTreeFilter({
  projectCode,
  dwType,
  onDwTypeChange,
  scopeType,
  onScopeTypeChange,
  scopeValue,
  onScopeValueChange,
  keyword,
  onKeywordChange,
}: ModifierTreeFilterProps) {
  const [scopeKeyword, setScopeKeyword] = useState("");
  const scopeOptionsQuery = useQuery<ModelingRecord[]>({
    queryKey: ["modeling", "modifier-tree-scope-options", projectCode, scopeType],
    queryFn: () => scopeType === "BUSINESS_TYPE"
      ? businessTypeApi.all<ModelingRecord>(projectCode)
      : dataDomainApi.all<ModelingRecord>(projectCode),
    enabled: Boolean(projectCode),
  });

  const scopeOptions = useMemo<FilterOption[]>(() => (scopeOptionsQuery.data || [])
    .map((record) => ({
      value: String(record.id || ""),
      label: String(record.businessName || record.cnName || record.domainName || record.id || "未命名"),
    }))
    .filter((option) => option.value), [scopeOptionsQuery.data]);
  const visibleScopeOptions = scopeOptions.filter((option) =>
    option.label.toLocaleLowerCase().includes(scopeKeyword.trim().toLocaleLowerCase()),
  );
  const activeScope = SCOPE_OPTIONS.find((option) => option.value === scopeType) || SCOPE_OPTIONS[0];

  const handleLayerKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    event.preventDefault();
    const direction = event.key === "ArrowRight" ? 1 : -1;
    const nextIndex = (index + direction + DW_LAYER_OPTIONS.length) % DW_LAYER_OPTIONS.length;
    const nextOption = DW_LAYER_OPTIONS[nextIndex];
    if (!nextOption) return;
    onDwTypeChange(nextOption.value);
    event.currentTarget.parentElement
      ?.querySelectorAll<HTMLButtonElement>('[role="radio"]')[nextIndex]
      ?.focus();
  };

  return (
    <div className="flex w-full shrink-0 flex-col gap-2 px-2.5 pb-2">
      <div
        role="radiogroup"
        aria-label="数仓分层"
        className="flex h-6 w-full overflow-hidden rounded-[4px] border border-[#d7dce5] bg-white"
      >
        {DW_LAYER_OPTIONS.map((option, index) => {
          const checked = dwType === option.value;
          return (
            <button
              key={option.value}
              type="button"
              role="radio"
              aria-checked={checked}
              tabIndex={checked ? 0 : -1}
              onClick={() => onDwTypeChange(option.value)}
              onKeyDown={(event) => handleLayerKeyDown(event, index)}
              className={[
                "relative flex h-full min-w-0 flex-1 items-center justify-center px-1 text-[11px] leading-none transition-colors",
                index > 0 ? "border-l border-[#d7dce5]" : "",
                checked
                  ? "z-[1] bg-[#f4f7ff] font-medium text-[#4169df] shadow-[inset_0_0_0_1px_#cbd7ff]"
                  : "text-[#606875] hover:bg-[#f8f9fc]",
              ].join(" ")}
            >
              <span className="truncate">{option.label}</span>
            </button>
          );
        })}
      </div>

      <div className="flex h-6 w-full min-w-0">
        <Select
          size="small"
          value={scopeType}
          onValueChange={(value) => {
            const next = String(value || "DATA_DOMAIN") as ModifierScopeType;
            onScopeTypeChange(next);
            onScopeValueChange("");
            setScopeKeyword("");
          }}
        >
          <SelectTrigger
            aria-label="选择目录类型"
            variant="outlined"
            className="!h-6 !w-[60px] !min-w-[60px] !shrink-0 !rounded-r-none !px-2 [&>span:last-child>svg]:!size-3"
          >
            <SelectValue>{() => <ScopeIcon scope={scopeType} />}</SelectValue>
          </SelectTrigger>
          <SelectContent className="!min-w-[148px]">
            {SCOPE_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value} className="!min-h-7 !px-2">
                <ScopeIcon scope={option.value} />
                <SelectItemText>{option.label}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          size="small"
          value={scopeValue || undefined}
          onValueChange={(value) => onScopeValueChange(String(value || ""))}
        >
          <SelectTrigger
            aria-label={`选择${activeScope.label}`}
            variant="outlined"
            className="!h-6 !min-w-0 !flex-1 !rounded-l-none !border-l-0 !px-2 [&>span:last-child>svg]:!size-3"
          >
            <SelectValue placeholder={scopeOptionsQuery.isFetching ? "正在加载..." : `请选择${activeScope.label}`} />
          </SelectTrigger>
          <SelectContent
            header={
              <SelectSearch
                size="small"
                value={scopeKeyword}
                placeholder={`搜索${activeScope.label}`}
                onChange={(event) => setScopeKeyword(event.target.value)}
              />
            }
            emptyContent={scopeOptionsQuery.isError ? "加载失败，请刷新重试" : visibleScopeOptions.length === 0 ? `没有匹配的${activeScope.label}` : undefined}
          >
            {visibleScopeOptions.map((option) => (
              <SelectItem key={option.value} value={option.value} className="!min-h-7 !px-2">
                <SelectItemText>{option.label}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Input
        size="small"
        variant="outlined"
        value={keyword}
        placeholder="搜索修饰词"
        className="!h-6 !rounded-[4px] !px-2 text-[11px]"
        onChange={(event) => onKeywordChange(event.target.value)}
      />
    </div>
  );
}
