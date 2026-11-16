import {
  Button, Input, Select, SelectContent, SelectItem, SelectItemText,
  SelectTrigger, SelectValue,
} from "@yak-ops/yak-ui";

import { withSelectedOption } from "../../select-options";
import type { BusinessProcessFilters, BusinessProcessSelectOption } from "./types";

interface Props {
  filters: BusinessProcessFilters;
  domainOptions: BusinessProcessSelectOption[];
  creatorOptions: string[];
  directorOptions: string[];
  domainsLoading: boolean;
  domainsError: boolean;
  creatorsLoading: boolean;
  creatorsError: boolean;
  directorsLoading: boolean;
  directorsError: boolean;
  onRetryDomains: () => void;
  onRetryCreators: () => void;
  onRetryDirectors: () => void;
  onChange: (key: keyof BusinessProcessFilters, value: string) => void;
  onSearch: () => void;
  onReset: () => void;
}

const FIELD_CLASS = "grid grid-cols-[64px_minmax(0,1fr)] items-center gap-2";

function FilterSelect({
  value,
  options,
  loading,
  error,
  fallbackLabel,
  onChange,
  onRetry,
}: {
  value: string;
  options: BusinessProcessSelectOption[];
  loading: boolean;
  error: boolean;
  fallbackLabel?: string;
  onChange: (value: string) => void;
  onRetry: () => void;
}) {
  const selectItems = [
    { value: "__all__", label: "全部" },
    ...withSelectedOption(options, value, fallbackLabel || "当前选项不可用"),
  ];
  const empty = options.length === 0 && !value;
  const disabled = loading || error || empty;
  const placeholder = loading
    ? "正在加载..."
    : error
      ? "加载失败"
      : empty
        ? "暂无可选数据"
        : "全部";

  return (
    <div className="flex min-w-0 flex-1 items-center gap-2">
      <div className="min-w-0 flex-1">
        <Select
          items={selectItems}
          value={value || undefined}
          disabled={disabled}
          onValueChange={(nextValue) => onChange(nextValue === "__all__" ? "" : String(nextValue || ""))}
        >
          <SelectTrigger size="small" variant="outlined" className="w-full">
            <SelectValue placeholder={placeholder} />
          </SelectTrigger>
          <SelectContent>
            {selectItems.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                <SelectItemText>{option.label}</SelectItemText>
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      {error ? (
        <Button size="sm" variant="ghost" className="shrink-0 px-1 text-xs" onClick={onRetry}>
          重试
        </Button>
      ) : null}
    </div>
  );
}

export function BusinessProcessFiltersPanel({
  filters,
  domainOptions,
  creatorOptions,
  directorOptions,
  domainsLoading,
  domainsError,
  creatorsLoading,
  creatorsError,
  directorsLoading,
  directorsError,
  onRetryDomains,
  onRetryCreators,
  onRetryDirectors,
  onChange,
  onSearch,
  onReset,
}: Props) {
  const creatorSelectOptions = creatorOptions.map((value) => ({ value, label: value }));
  const directorSelectOptions = directorOptions.map((value) => ({ value, label: value }));

  return (
    <section className="bg-white pb-4 pt-4">
      <div className="grid grid-cols-1 gap-x-8 gap-y-2 px-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] 2xl:grid-cols-[440px_440px_auto] 2xl:gap-x-[100px]">
        <div className="space-y-2">
          <div className={FIELD_CLASS}>
            <span className="text-right text-xs text-[var(--yak-components-muted-text)]">英文缩写：</span>
            <Input size="small" variant="outlined" value={filters.enShortName} onChange={(event) => onChange("enShortName", event.target.value)} />
          </div>
          <div className={FIELD_CLASS}>
            <span className="text-right text-xs text-[var(--yak-components-muted-text)]">英文名：</span>
            <Input size="small" variant="outlined" value={filters.enName} onChange={(event) => onChange("enName", event.target.value)} />
          </div>
          <div className={FIELD_CLASS}>
            <span className="text-right text-xs text-[var(--yak-components-muted-text)]">中文名：</span>
            <Input size="small" variant="outlined" value={filters.cnName} onChange={(event) => onChange("cnName", event.target.value)} />
          </div>
        </div>
        <div className="space-y-2">
          <div className={FIELD_CLASS}>
            <span className="text-right text-xs text-[var(--yak-components-muted-text)]">数据域：</span>
            <FilterSelect
              value={filters.dataDomainId}
              options={domainOptions}
              loading={domainsLoading}
              error={domainsError}
              fallbackLabel="已选数据域（名称暂不可用）"
              onChange={(value) => onChange("dataDomainId", value)}
              onRetry={onRetryDomains}
            />
          </div>
          <div className={FIELD_CLASS}>
            <span className="text-right text-xs text-[var(--yak-components-muted-text)]">创建人：</span>
            <FilterSelect
              value={filters.createBy}
              options={creatorSelectOptions}
              loading={creatorsLoading}
              error={creatorsError}
              fallbackLabel="已选创建人（用户不可用）"
              onChange={(value) => onChange("createBy", value)}
              onRetry={onRetryCreators}
            />
          </div>
          <div className={FIELD_CLASS}>
            <span className="text-right text-xs text-[var(--yak-components-muted-text)]">负责人：</span>
            <FilterSelect
              value={filters.director}
              options={directorSelectOptions}
              loading={directorsLoading}
              error={directorsError}
              fallbackLabel="已选负责人（名称暂不可用）"
              onChange={(value) => onChange("director", value)}
              onRetry={onRetryDirectors}
            />
          </div>
        </div>
        <div className="flex items-start gap-2 pt-0.5">
          <Button size="sm" variant="primary" onClick={onSearch}>搜索</Button>
          <Button size="sm" variant="secondary" onClick={onReset}>重置</Button>
        </div>
      </div>
    </section>
  );
}

export default BusinessProcessFiltersPanel;
